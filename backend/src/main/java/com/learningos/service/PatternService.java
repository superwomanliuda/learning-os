package com.learningos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * v2: 候选 Pattern 识别。
 * 6 种核心 pattern + 状态机（pending_review / confirmed / rejected / modified / observing）。
 * 旧字段 confirmed:Boolean 保留兼容旧驾驶舱。
 */
@Service
public class PatternService {

    private static final List<PatternRule> RULES = List.of(
            new PatternRule(
                    "dependency",
                    List.of("hint_dependency_signal"),
                    3,
                    List.of("modeling_entry_difficulty", "confirmation_dependency", "新题型首次接触", "焦虑导致快速求助"),
                    "学生是否在已经会的题上也快速求助？还是只在新题型上？"),
            new PatternRule(
                    "process_break",
                    List.of("step_error"),
                    2,
                    List.of("题目难度超出可处理范围", "高认知负荷", "新题型适应"),
                    "学生在不同步骤都出错，还是固定卡在某一步？"),
            new PatternRule(
                    "surface_understanding",
                    List.of("vague_understanding_signal", "transfer_signal"),
                    2,
                    List.of("表达困难（理解了但说不出来）", "紧张影响表达", "语言组织能力弱"),
                    "学生是真的不理解，还是只是表达不出来？"),
            new PatternRule(
                    "modeling_entry_difficulty",
                    List.of("start_difficulty_signal"),
                    1,
                    List.of("普通的提示依赖", "焦虑导致不敢开始", "题目读不懂"),
                    "学生是不知道怎么把文字条件转成式子，还是连读题都困难？"),
            new PatternRule(
                    "confirmation_dependency",
                    List.of("confirmation_dependency_signal"),
                    2,
                    List.of("谨慎性格", "对自己判断不自信但其实是对的", "表达困难"),
                    "学生求确认时，他自己的判断其实是对的吗？"),
            new PatternRule(
                    "high_cognitive_load",
                    List.of("hesitation", "high_load_signal", "consecutive_error"),
                    3,
                    List.of("具体知识点不会", "疲劳信号", "题量过多"),
                    "学生是任务复杂度超载，还是底层知识点没掌握？")
    );

    private static final long REGEN_DEDUP_HOURS = 6;

    private final ObservationRepository observationRepository;
    private final PatternRepository patternRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public PatternService(ObservationRepository observationRepository,
                          PatternRepository patternRepository,
                          AiService aiService,
                          ObjectMapper objectMapper) {
        this.observationRepository = observationRepository;
        this.patternRepository = patternRepository;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    /**
     * 基于 student 全部 observation 跑 6 种 pattern 规则。
     * 旧接口签名保持，内部完全用新规则。
     */
    @Transactional
    public List<Pattern> generatePattern(Long requestedStudentId) {
        Long studentId = resolveStudentId(requestedStudentId);
        List<Observation> all = observationRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        return runRulesForStudent(studentId, null, all);
    }

    /**
     * 基于某次 session 的 observation 跑规则（用于 pipeline 链路）
     */
    @Transactional
    public List<Pattern> generatePatternForSession(Long studentId, String sessionId) {
        List<Observation> all = sessionId == null || sessionId.isBlank()
                ? observationRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                : observationRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
        return runRulesForStudent(studentId, sessionId, all);
    }

    private List<Pattern> runRulesForStudent(Long studentId, String sessionId, List<Observation> observations) {
        if (observations == null || observations.isEmpty()) {
            return List.of();
        }
        // 限制最近 100 条以避免历史污染
        List<Observation> recent = observations.size() > 100 ? observations.subList(0, 100) : observations;

        Map<String, List<Observation>> byObsType = recent.stream()
                .filter(o -> o.getObservationType() != null && !o.getObservationType().isBlank())
                .collect(Collectors.groupingBy(Observation::getObservationType));

        List<Pattern> created = new ArrayList<>();
        for (PatternRule rule : RULES) {
            List<Observation> evidence = collectEvidence(byObsType, rule.signals());
            if (evidence.size() < rule.threshold()) {
                continue;
            }
            // 去重：同 student + type 在 6 小时内有未处理 pattern，跳过
            Pattern existing = patternRepository
                    .findTopByStudentIdAndTypeAndStatusOrderByCreatedAtDesc(studentId, rule.patternId(), "pending_review");
            if (existing == null) {
                existing = patternRepository.findTopByStudentIdAndTypeAndConfirmedOrderByCreatedAtDesc(
                        studentId, rule.patternId(), false);
            }
            if (existing != null && existing.getCreatedAt() != null
                    && existing.getCreatedAt().isAfter(LocalDateTime.now().minusHours(REGEN_DEDUP_HOURS))) {
                continue;
            }

            // 规则置信度 + AI 二次评估
            double ruleConfidence = computeRuleConfidence(evidence, rule);
            String evidenceSummary = buildEvidenceSummary(evidence, rule);
            List<String> samples = evidence.stream().map(Observation::getContent)
                    .filter(c -> c != null && !c.isBlank()).limit(10).toList();
            Map<String, Object> ai = aiService.evaluatePatternWithAi(rule.patternId(), samples, evidenceSummary);
            boolean stable = Boolean.TRUE.equals(ai.get("stable"));
            if (!stable) {
                continue;
            }
            double aiConfidence = ((Number) ai.getOrDefault("confidence", 0.7)).doubleValue();
            double finalConfidence = round2(Math.max(ruleConfidence, aiConfidence));

            Pattern p = new Pattern();
            p.setStudentId(studentId);
            p.setSessionId(sessionId);
            p.setType(rule.patternId());
            p.setConfidenceScore(finalConfidence);
            p.setSeverity(deriveSeverity(evidence.size(), rule.threshold()));
            p.setTrend(deriveTrend(evidence));
            p.setEvidenceSummary(evidenceSummary);
            p.setEvidenceIds(toJson(evidence.stream().map(Observation::getId).toList()));
            p.setFalsePositiveRisks(toJson(rule.falsePositiveRisks()));
            p.setStatus("pending_review");
            p.setConfirmed(false);
            created.add(patternRepository.save(p));
        }
        return created;
    }

    private List<Observation> collectEvidence(Map<String, List<Observation>> byObsType, List<String> signals) {
        Set<Long> seen = new HashSet<>();
        List<Observation> evidence = new ArrayList<>();
        for (String sig : signals) {
            for (Observation o : byObsType.getOrDefault(sig, List.of())) {
                if (seen.add(o.getId())) {
                    evidence.add(o);
                }
            }
        }
        return evidence;
    }

    private double computeRuleConfidence(List<Observation> items, PatternRule rule) {
        int count = items.size();
        long teacherSignals = items.stream()
                .filter(o -> "teacher_note".equalsIgnoreCase(o.getSource()) || "teacher".equalsIgnoreCase(o.getType()))
                .count();
        double base = 0.5;
        double volume = Math.min(0.3, (count - rule.threshold()) * 0.06 + 0.1);
        double teacherBoost = teacherSignals > 0 ? 0.1 : 0.0;
        return round2(Math.min(0.95, base + volume + teacherBoost));
    }

    private String deriveSeverity(int count, int threshold) {
        if (count < threshold + 2) return "low";
        if (count < threshold + 5) return "medium";
        return "high";
    }

    private String deriveTrend(List<Observation> evidence) {
        if (evidence.size() < 4) return "unknown";
        // 把 evidence 按 createdAt 分两半，前半数量 vs 后半数量
        List<Observation> sorted = evidence.stream()
                .sorted(Comparator.comparing(Observation::getCreatedAt))
                .toList();
        int mid = sorted.size() / 2;
        int oldCount = mid;
        int newCount = sorted.size() - mid;
        if (newCount > oldCount + 1) return "worsening";
        if (newCount < oldCount - 1) return "improving";
        return "stable";
    }

    private String buildEvidenceSummary(List<Observation> evidence, PatternRule rule) {
        Map<String, Long> bySignal = evidence.stream()
                .filter(o -> o.getObservationType() != null)
                .collect(Collectors.groupingBy(Observation::getObservationType, Collectors.counting()));
        String breakdown = bySignal.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> e.getKey() + " ×" + e.getValue())
                .collect(Collectors.joining("、"));
        return "近期共 " + evidence.size() + " 条相关观察（" + breakdown + "）";
    }

    @Transactional
    public Pattern confirmPattern(Long patternId, Boolean confirmed) {
        if (!"TEACHER".equalsIgnoreCase(SecurityUtils.currentRole())) {
            throw new IllegalStateException("仅老师可确认或忽略模式");
        }
        Pattern p = patternRepository.findById(patternId)
                .orElseThrow(() -> new IllegalArgumentException("Pattern 不存在"));
        boolean ok = Boolean.TRUE.equals(confirmed);
        p.setConfirmed(ok);
        p.setStatus(ok ? "confirmed" : "rejected");
        p.setUpdatedAt(LocalDateTime.now());
        return patternRepository.save(p);
    }

    /**
     * 老师把 pattern 改成另一个类型（如把 dependency 改成 modeling_entry_difficulty）
     */
    @Transactional
    public Pattern modifyPatternType(Long patternId, String newType) {
        if (!"TEACHER".equalsIgnoreCase(SecurityUtils.currentRole())) {
            throw new IllegalStateException("仅老师可修改模式类型");
        }
        Pattern p = patternRepository.findById(patternId)
                .orElseThrow(() -> new IllegalArgumentException("Pattern 不存在"));
        p.setType(newType);
        p.setStatus("modified");
        p.setConfirmed(true); // modify 也算确认
        p.setUpdatedAt(LocalDateTime.now());
        return patternRepository.save(p);
    }

    public List<Pattern> listByStudent(Long studentId, Boolean confirmed) {
        Long target = resolveStudentId(studentId);
        if (confirmed == null) {
            return patternRepository.findByStudentIdOrderByCreatedAtDesc(target);
        }
        return patternRepository.findByStudentIdAndConfirmedOrderByCreatedAtDesc(target, confirmed);
    }

    public List<Pattern> listByStudentAndStatus(Long studentId, String status) {
        Long target = resolveStudentId(studentId);
        if (status == null || status.isBlank()) {
            return patternRepository.findByStudentIdOrderByCreatedAtDesc(target);
        }
        return patternRepository.findByStudentIdAndStatusOrderByCreatedAtDesc(target, status);
    }

    private Long resolveStudentId(Long requestedStudentId) {
        String role = SecurityUtils.currentRole();
        if ("TEACHER".equalsIgnoreCase(role)) {
            if (requestedStudentId == null) {
                throw new IllegalArgumentException("老师侧操作 Pattern 需要 studentId");
            }
            return requestedStudentId;
        }
        return SecurityUtils.currentUserId();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

    private record PatternRule(
            String patternId,
            List<String> signals,
            int threshold,
            List<String> falsePositiveRisks,
            String reviewQuestion
    ) {}
}
