package com.learningos.service;

import com.learningos.model.InsightCandidate;
import com.learningos.model.Observation;
import com.learningos.model.Pattern;
import com.learningos.model.TeachingDecision;
import com.learningos.repository.ObservationRepository;
import com.learningos.repository.PatternRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v2 编排器：把 Observation→Pattern→Insight→Decision 串成一个对外接口。
 *
 * 流程：
 *   stage 1 (runDiagnose)：observation → pattern + 每个 pattern 同步一个 pending insight
 *   stage 2 (runDecisionAfterReview)：confirmed pattern + insight → teaching decision
 */
@Service
public class TeachingPipelineService {

    private final ObservationRepository observationRepository;
    private final PatternRepository patternRepository;
    private final PatternService patternService;
    private final InsightService insightService;
    private final TeachingDecisionService teachingDecisionService;

    public TeachingPipelineService(ObservationRepository observationRepository,
                                   PatternRepository patternRepository,
                                   PatternService patternService,
                                   InsightService insightService,
                                   TeachingDecisionService teachingDecisionService) {
        this.observationRepository = observationRepository;
        this.patternRepository = patternRepository;
        this.patternService = patternService;
        this.insightService = insightService;
        this.teachingDecisionService = teachingDecisionService;
    }

    /**
     * Stage 1：诊断阶段。
     * 不要求老师先确认，给老师 review 卡片用。
     * 返回 patterns + 每个 pattern 同步生成的 pending insight。
     */
    @Transactional
    public Map<String, Object> runDiagnose(Long studentId, String sessionId) {
        ensureTeacherAccess(studentId);

        // 1. observation 已由 ObservationService 在事件入库时抽好。这里只读出来。
        List<Observation> observations = sessionId == null || sessionId.isBlank()
                ? observationRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                : observationRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);

        // 2. 跑 pattern 规则
        List<Pattern> newPatterns = patternService.generatePatternForSession(studentId, sessionId);

        // 3. 给每个新 pattern 同步生成 pending insight
        List<InsightCandidate> insights = new java.util.ArrayList<>();
        for (Pattern p : newPatterns) {
            InsightCandidate ic = insightService.generateForPattern(p);
            if (ic != null) insights.add(ic);
        }

        // 4. 也把当前所有 pending pattern 一并返回（包括之前生成、这次没新生成的）
        List<Pattern> allPending = patternRepository
                .findByStudentIdAndStatusOrderByCreatedAtDesc(studentId, "pending_review");

        Map<String, Object> result = new HashMap<>();
        result.put("observationsCount", observations.size());
        result.put("newPatterns", newPatterns);
        result.put("pendingPatterns", allPending);
        result.put("newInsights", insights);
        result.put("stage", "diagnose_complete");
        result.put("nextAction", "请老师审阅 pending pattern，然后调用 /api/pipeline/decision 生成教学方向");
        return result;
    }

    /**
     * Stage 2：老师 review 完后，基于 confirmed pattern + insight 生成 teaching decision。
     */
    @Transactional
    public Map<String, Object> runDecisionAfterReview(Long studentId, String sessionId) {
        ensureTeacherAccess(studentId);

        // 检查是否有 confirmed pattern
        List<Pattern> confirmed;
        if (sessionId != null && !sessionId.isBlank()) {
            confirmed = patternRepository.findBySessionIdOrderByCreatedAtDesc(sessionId).stream()
                    .filter(p -> "confirmed".equals(p.getStatus()) || "modified".equals(p.getStatus()))
                    .toList();
        } else {
            confirmed = patternRepository.findByStudentIdAndConfirmedOrderByCreatedAtDesc(studentId, true);
        }
        if (confirmed.isEmpty()) {
            Map<String, Object> r = new HashMap<>();
            r.put("stage", "no_confirmed_pattern");
            r.put("message", "暂无已确认的 Pattern，老师先在 review 卡片确认或修改至少一个 Pattern");
            return r;
        }

        // 给所有 confirmed pattern 补全 insight（如果之前没生成）
        List<InsightCandidate> insights = insightService.generateForConfirmedPatterns(studentId, sessionId);

        // 生成 teaching decision
        TeachingDecision decision = teachingDecisionService.generateForSession(studentId, sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("confirmedPatterns", confirmed);
        result.put("insights", insights);
        result.put("decision", decision);
        result.put("stage", "decision_complete");
        result.put("nextAction", decision == null
                ? "未能生成教学方向，请检查 confirmed pattern"
                : "请老师审阅 decision，然后调用 /api/lesson-kits/generate 生成完整课件");
        return result;
    }

    private void ensureTeacherAccess(Long studentId) {
        String role = SecurityUtils.currentRole();
        if (!"TEACHER".equalsIgnoreCase(role)) {
            throw new IllegalStateException("仅老师可触发诊断流水线");
        }
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 必填");
        }
    }
}
