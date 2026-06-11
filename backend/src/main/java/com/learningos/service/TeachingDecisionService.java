package com.learningos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningos.model.InsightCandidate;
import com.learningos.model.Pattern;
import com.learningos.model.TeacherFeedback;
import com.learningos.model.TeachingDecision;
import com.learningos.repository.InsightCandidateRepository;
import com.learningos.repository.PatternRepository;
import com.learningos.repository.TeacherFeedbackRepository;
import com.learningos.repository.TeachingDecisionRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v2: 把 Pattern + Insight + 老师反馈 转成下节课教学决策。
 */
@Service
public class TeachingDecisionService {

    private final TeachingDecisionRepository decisionRepository;
    private final PatternRepository patternRepository;
    private final InsightCandidateRepository insightRepository;
    private final TeacherFeedbackRepository feedbackRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public TeachingDecisionService(TeachingDecisionRepository decisionRepository,
                                   PatternRepository patternRepository,
                                   InsightCandidateRepository insightRepository,
                                   TeacherFeedbackRepository feedbackRepository,
                                   AiService aiService,
                                   ObjectMapper objectMapper) {
        this.decisionRepository = decisionRepository;
        this.patternRepository = patternRepository;
        this.insightRepository = insightRepository;
        this.feedbackRepository = feedbackRepository;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    /**
     * 基于某 session 的 confirmed pattern + insight 生成 teaching decision。
     * 一个 session 通常只产出 1 个 decision（含 5 个维度的方向）。
     */
    @Transactional
    public TeachingDecision generateForSession(Long studentId, String sessionId) {
        List<Pattern> confirmedPatterns;
        List<InsightCandidate> insights;
        if (sessionId != null && !sessionId.isBlank()) {
            confirmedPatterns = patternRepository.findBySessionIdOrderByCreatedAtDesc(sessionId).stream()
                    .filter(p -> "confirmed".equals(p.getStatus()) || "modified".equals(p.getStatus()))
                    .toList();
            insights = insightRepository.findBySessionIdOrderByCreatedAtDesc(sessionId).stream()
                    .filter(i -> "confirmed".equals(i.getStatus()) || "modified".equals(i.getStatus())
                            || "pending_review".equals(i.getStatus()))
                    .toList();
        } else {
            confirmedPatterns = patternRepository.findByStudentIdAndConfirmedOrderByCreatedAtDesc(studentId, true);
            insights = insightRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        }

        if (confirmedPatterns.isEmpty()) {
            // 没有任何 confirmed pattern，不强行生成 decision
            return null;
        }

        // 老师反馈作为 hint
        String feedbackHint = buildFeedbackHint(studentId);

        List<String> patternTypes = confirmedPatterns.stream().map(Pattern::getType).toList();
        List<String> insightTexts = insights.stream()
                .map(InsightCandidate::getInsightText)
                .filter(t -> t != null && !t.isBlank())
                .limit(5)
                .toList();

        Map<String, Object> ai = aiService.generateTeachingDecision(
                patternTypes,
                insightTexts,
                feedbackHint,
                null  // student profile 暂时不传
        );

        TeachingDecision td = new TeachingDecision();
        td.setStudentId(studentId);
        td.setSessionId(sessionId);
        td.setLessonFocus(String.valueOf(ai.getOrDefault("lesson_focus", "")));
        td.setPracticePolicy(String.valueOf(ai.getOrDefault("practice_policy", "")));
        td.setInteractionPolicy(String.valueOf(ai.getOrDefault("interaction_policy", "")));
        td.setMaterialPolicy(String.valueOf(ai.getOrDefault("material_policy", "")));
        td.setDifficultyPolicy(String.valueOf(ai.getOrDefault("difficulty_policy", "")));

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("patterns", confirmedPatterns.stream().map(Pattern::getId).toList());
        ctx.put("insights", insights.stream().map(InsightCandidate::getId).toList());
        ctx.put("ai_reason", ai.getOrDefault("reason", ""));
        td.setSourceContext(toJson(ctx));
        td.setStatus("pending_review");

        return decisionRepository.save(td);
    }

    public List<TeachingDecision> listByStudent(Long requestedStudentId) {
        Long studentId = resolveStudentId(requestedStudentId);
        return decisionRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    public List<TeachingDecision> listBySession(String sessionId) {
        return decisionRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    @Transactional
    public TeachingDecision confirm(Long decisionId, boolean confirmed) {
        if (!"TEACHER".equalsIgnoreCase(SecurityUtils.currentRole())) {
            throw new IllegalStateException("仅老师可确认教学决策");
        }
        TeachingDecision td = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new IllegalArgumentException("TeachingDecision 不存在"));
        td.setStatus(confirmed ? "confirmed" : "rejected");
        return decisionRepository.save(td);
    }

    private String buildFeedbackHint(Long studentId) {
        List<TeacherFeedback> feedbacks = feedbackRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        if (feedbacks.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        feedbacks.stream().limit(3).forEach(f -> {
            sb.append("- 老师对 ").append(f.getTargetType())
                    .append(" 的反馈：").append(f.getFeedbackType());
            if (f.getTeacherText() != null && !f.getTeacherText().isBlank()) {
                sb.append("（").append(f.getTeacherText()).append("）");
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    private Long resolveStudentId(Long requestedStudentId) {
        String role = SecurityUtils.currentRole();
        if ("TEACHER".equalsIgnoreCase(role)) {
            if (requestedStudentId == null) {
                throw new IllegalArgumentException("老师侧查询 TeachingDecision 需要 studentId");
            }
            return requestedStudentId;
        }
        return SecurityUtils.currentUserId();
    }

    private String toJson(Object value) {
        if (value == null) return "{}";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
