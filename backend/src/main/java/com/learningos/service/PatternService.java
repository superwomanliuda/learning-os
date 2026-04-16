package com.learningos.service;

import com.learningos.model.Observation;
import com.learningos.model.Pattern;
import com.learningos.repository.ObservationRepository;
import com.learningos.repository.PatternRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PatternService {

    private final ObservationRepository observationRepository;
    private final PatternRepository patternRepository;
    private final AiService aiService;

    public PatternService(ObservationRepository observationRepository,
                          PatternRepository patternRepository,
                          AiService aiService) {
        this.observationRepository = observationRepository;
        this.patternRepository = patternRepository;
        this.aiService = aiService;
    }

    /**
     * MVP 规则：
     * - 同类 observation 达到 3 条，生成 pattern
     * - pattern 初始必须为未确认（confirmed=false）
     */
    @Transactional
    public List<Pattern> generatePattern(Long requestedStudentId) {
        Long studentId = resolveStudentId(requestedStudentId);
        List<Observation> observations = observationRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        if (observations.isEmpty()) {
            return List.of();
        }

        Map<String, List<Observation>> grouped = observations.stream()
                .limit(100)
                .collect(Collectors.groupingBy(this::inferPatternType));

        List<Pattern> created = new ArrayList<>();
        for (Map.Entry<String, List<Observation>> entry : grouped.entrySet()) {
            String type = entry.getKey();
            List<Observation> items = entry.getValue();
            if (items.size() < 3) {
                continue;
            }
            Pattern latestOpen = patternRepository
                    .findTopByStudentIdAndTypeAndConfirmedOrderByCreatedAtDesc(studentId, type, false);
            if (latestOpen != null && latestOpen.getCreatedAt() != null &&
                    latestOpen.getCreatedAt().isAfter(LocalDateTime.now().minusHours(6))) {
                continue;
            }
            Pattern p = new Pattern();
            p.setStudentId(studentId);
            String ruleEvidence = buildEvidenceSummary(items);
            List<String> samples = items.stream().map(Observation::getContent).toList();
            Map<String, Object> ai = aiService.evaluatePatternWithAi(type, samples, ruleEvidence);
            boolean stable = Boolean.TRUE.equals(ai.get("stable"));
            if (!stable) {
                continue;
            }
            p.setType(String.valueOf(ai.getOrDefault("type", type)));
            double ruleScore = calculateConfidence(items);
            double aiScore = ((Number) ai.getOrDefault("confidence", 0.7)).doubleValue();
            p.setConfidenceScore(round2(Math.max(ruleScore, aiScore)));
            p.setEvidenceSummary(String.valueOf(ai.getOrDefault("evidence", ruleEvidence)));
            p.setConfirmed(false);
            created.add(patternRepository.save(p));
        }
        return created;
    }

    @Transactional
    public Pattern confirmPattern(Long patternId, Boolean confirmed) {
        if (!"TEACHER".equalsIgnoreCase(SecurityUtils.currentRole())) {
            throw new IllegalStateException("仅老师可确认或忽略模式");
        }
        Pattern p = patternRepository.findById(patternId)
                .orElseThrow(() -> new IllegalArgumentException("Pattern 不存在"));
        p.setConfirmed(Boolean.TRUE.equals(confirmed));
        return patternRepository.save(p);
    }

    public List<Pattern> listByStudent(Long studentId, Boolean confirmed) {
        Long target = resolveStudentId(studentId);
        if (confirmed == null) {
            return patternRepository.findByStudentIdOrderByCreatedAtDesc(target);
        }
        return patternRepository.findByStudentIdAndConfirmedOrderByCreatedAtDesc(target, confirmed);
    }

    private Long resolveStudentId(Long requestedStudentId) {
        String role = SecurityUtils.currentRole();
        if ("TEACHER".equalsIgnoreCase(role)) {
            if (requestedStudentId == null) {
                throw new IllegalArgumentException("老师侧生成 Pattern 需要 studentId");
            }
            return requestedStudentId;
        }
        return SecurityUtils.currentUserId();
    }

    private String inferPatternType(Observation o) {
        String category = o.getCategory() == null ? "" : o.getCategory().toLowerCase();
        String content = o.getContent() == null ? "" : o.getContent().toLowerCase();
        if ("step_error".equals(category) || content.contains("步骤") || content.contains("卡在")) {
            return "step_break";
        }
        if (content.contains("变式") || content.contains("迁移")) {
            return "transfer_issue";
        }
        if ("hesitation".equals(category) || content.contains("提示") || content.contains("停顿")) {
            return "dependency";
        }
        return "dependency";
    }

    private double calculateConfidence(List<Observation> items) {
        int count = items.size();
        long teacherSignals = items.stream().filter(o -> "teacher".equalsIgnoreCase(o.getType())).count();
        double base = 0.45;
        double volume = Math.min(0.35, (count - 3) * 0.08 + 0.15);
        double teacherBoost = teacherSignals > 0 ? 0.12 : 0.0;
        return Math.min(0.95, round2(base + volume + teacherBoost));
    }

    private String buildEvidenceSummary(List<Observation> items) {
        Map<String, Integer> freq = new HashMap<>();
        for (Observation o : items) {
            String key = (o.getContent() == null || o.getContent().isBlank()) ? "行为重复出现" : o.getContent();
            freq.put(key, freq.getOrDefault(key, 0) + 1);
        }
        String top = freq.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("行为重复出现");
        return "近期待观察共 " + items.size() + " 条，重复信号：" + top;
    }

    private double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}
