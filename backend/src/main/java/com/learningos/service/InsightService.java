package com.learningos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningos.model.InsightCandidate;
import com.learningos.model.Observation;
import com.learningos.model.Pattern;
import com.learningos.repository.InsightCandidateRepository;
import com.learningos.repository.ObservationRepository;
import com.learningos.repository.PatternRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v2: 把 confirmed Pattern 转成教育解释（Insight）。
 * 关键约束：仅基于已确认或部分接受的 Pattern 生成，未确认的 Pattern 不强行出 Insight。
 */
@Service
public class InsightService {

    private final InsightCandidateRepository insightRepository;
    private final PatternRepository patternRepository;
    private final ObservationRepository observationRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public InsightService(InsightCandidateRepository insightRepository,
                          PatternRepository patternRepository,
                          ObservationRepository observationRepository,
                          AiService aiService,
                          ObjectMapper objectMapper) {
        this.insightRepository = insightRepository;
        this.patternRepository = patternRepository;
        this.observationRepository = observationRepository;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    /**
     * 为单个 pattern 生成 insight（如果同 pattern 已有 pending insight 则跳过）。
     */
    @Transactional
    public InsightCandidate generateForPattern(Pattern pattern) {
        if (pattern == null) return null;

        // 已有同 pattern 的待审核 insight 跳过
        List<InsightCandidate> existing = insightRepository.findByPatternIdOrderByCreatedAtDesc(pattern.getId());
        if (!existing.isEmpty() && "pending_review".equals(existing.get(0).getStatus())) {
            return existing.get(0);
        }

        // 找证据样本
        List<String> samples = sampleObservationsForPattern(pattern);

        Map<String, Object> ai = aiService.generateInsight(
                pattern.getType(),
                pattern.getEvidenceSummary(),
                samples
        );

        InsightCandidate ic = new InsightCandidate();
        ic.setStudentId(pattern.getStudentId());
        ic.setSessionId(pattern.getSessionId());
        ic.setPatternId(pattern.getId());
        ic.setInsightText(String.valueOf(ai.getOrDefault("insight_text", "")));
        ic.setPossibleCauses(toJson(ai.get("possible_causes")));
        ic.setTeachingImplications(toJson(ai.get("teaching_implications")));
        Object conf = ai.getOrDefault("confidence", 0.6);
        ic.setConfidence(conf instanceof Number n ? n.doubleValue() : 0.6);
        ic.setStatus("pending_review");
        return insightRepository.save(ic);
    }

    /**
     * 为某个学生所有 confirmed/modified 的 pattern 批量生成 insight。
     * 用于老师确认 pattern 后一键推进。
     */
    @Transactional
    public List<InsightCandidate> generateForConfirmedPatterns(Long studentId, String sessionId) {
        List<Pattern> patterns;
        if (sessionId != null && !sessionId.isBlank()) {
            patterns = patternRepository.findBySessionIdOrderByCreatedAtDesc(sessionId).stream()
                    .filter(p -> "confirmed".equals(p.getStatus()) || "modified".equals(p.getStatus()))
                    .toList();
        } else {
            patterns = patternRepository.findByStudentIdAndConfirmedOrderByCreatedAtDesc(studentId, true);
        }
        List<InsightCandidate> results = new ArrayList<>();
        for (Pattern p : patterns) {
            InsightCandidate ic = generateForPattern(p);
            if (ic != null) results.add(ic);
        }
        return results;
    }

    public List<InsightCandidate> listByStudent(Long requestedStudentId) {
        Long studentId = resolveStudentId(requestedStudentId);
        return insightRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    public List<InsightCandidate> listBySession(String sessionId) {
        return insightRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    @Transactional
    public InsightCandidate confirmInsight(Long insightId, boolean confirmed) {
        if (!"TEACHER".equalsIgnoreCase(SecurityUtils.currentRole())) {
            throw new IllegalStateException("仅老师可确认 Insight");
        }
        InsightCandidate ic = insightRepository.findById(insightId)
                .orElseThrow(() -> new IllegalArgumentException("Insight 不存在"));
        ic.setStatus(confirmed ? "confirmed" : "rejected");
        return insightRepository.save(ic);
    }

    @Transactional
    public InsightCandidate modifyInsightText(Long insightId, String newText) {
        if (!"TEACHER".equalsIgnoreCase(SecurityUtils.currentRole())) {
            throw new IllegalStateException("仅老师可修改 Insight");
        }
        InsightCandidate ic = insightRepository.findById(insightId)
                .orElseThrow(() -> new IllegalArgumentException("Insight 不存在"));
        ic.setInsightText(newText);
        ic.setStatus("modified");
        return insightRepository.save(ic);
    }

    private List<String> sampleObservationsForPattern(Pattern p) {
        List<Observation> all;
        if (p.getSessionId() != null && !p.getSessionId().isBlank()) {
            all = observationRepository.findBySessionIdOrderByCreatedAtDesc(p.getSessionId());
        } else {
            all = observationRepository.findByStudentIdOrderByCreatedAtDesc(p.getStudentId());
        }
        return all.stream()
                .map(Observation::getContent)
                .filter(c -> c != null && !c.isBlank())
                .limit(8)
                .toList();
    }

    private Long resolveStudentId(Long requestedStudentId) {
        String role = SecurityUtils.currentRole();
        if ("TEACHER".equalsIgnoreCase(role)) {
            if (requestedStudentId == null) {
                throw new IllegalArgumentException("老师侧查询 Insight 需要 studentId");
            }
            return requestedStudentId;
        }
        return SecurityUtils.currentUserId();
    }

    private String toJson(Object value) {
        if (value == null) return "[]";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
