package com.learningos.service;

import com.learningos.dto.TeacherFeedbackRequest;
import com.learningos.model.InsightCandidate;
import com.learningos.model.Pattern;
import com.learningos.model.TeacherFeedback;
import com.learningos.repository.InsightCandidateRepository;
import com.learningos.repository.PatternRepository;
import com.learningos.repository.TeacherFeedbackRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * v2: 老师纠错最简版。
 * - 4 个反馈类型：confirm / reject / modify / uncertain
 * - 修改 pattern：直接改 type
 * - 修改 insight：直接改文本
 * - teacher_text 纯文本存档，不做 AI 结构化（demo 阶段）
 * - 同步把对应 pattern/insight 状态机推到 confirmed/rejected/modified
 */
@Service
public class TeacherReviewService {

    private final TeacherFeedbackRepository feedbackRepository;
    private final PatternRepository patternRepository;
    private final InsightCandidateRepository insightRepository;

    public TeacherReviewService(TeacherFeedbackRepository feedbackRepository,
                                PatternRepository patternRepository,
                                InsightCandidateRepository insightRepository) {
        this.feedbackRepository = feedbackRepository;
        this.patternRepository = patternRepository;
        this.insightRepository = insightRepository;
    }

    @Transactional
    public TeacherFeedback submit(TeacherFeedbackRequest req) {
        if (!"TEACHER".equalsIgnoreCase(SecurityUtils.currentRole())) {
            throw new IllegalStateException("仅老师可提交反馈");
        }
        Long teacherId = SecurityUtils.currentUserId();

        TeacherFeedback fb = new TeacherFeedback();
        fb.setTeacherId(teacherId);
        fb.setStudentId(req.getStudentId());
        fb.setSessionId(req.getSessionId());
        fb.setTargetType(req.getTargetType());
        fb.setTargetId(req.getTargetId());
        fb.setFeedbackType(req.getFeedbackType());
        fb.setTeacherText(req.getTeacherText());

        // 同步推动目标对象状态机 + 记录系统当时的值
        switch (req.getTargetType()) {
            case "pattern" -> applyToPattern(req, fb);
            case "insight" -> applyToInsight(req, fb);
            // teaching_decision / artifact 暂未做反向修改（待 F 阶段补充）
            default -> {}
        }

        return feedbackRepository.save(fb);
    }

    private void applyToPattern(TeacherFeedbackRequest req, TeacherFeedback fb) {
        Pattern p = patternRepository.findById(req.getTargetId())
                .orElseThrow(() -> new IllegalArgumentException("Pattern 不存在"));
        fb.setSystemValue("pattern.type=" + p.getType()
                + ", confidence=" + p.getConfidenceScore()
                + ", evidence=" + p.getEvidenceSummary());
        switch (req.getFeedbackType()) {
            case "confirm" -> {
                p.setStatus("confirmed");
                p.setConfirmed(true);
            }
            case "reject" -> {
                p.setStatus("rejected");
                p.setConfirmed(false);
            }
            case "modify" -> {
                if (req.getCorrectedPatternType() != null && !req.getCorrectedPatternType().isBlank()) {
                    p.setType(req.getCorrectedPatternType());
                }
                p.setStatus("modified");
                p.setConfirmed(true);
            }
            case "uncertain" -> p.setStatus("observing");
            default -> {}
        }
        patternRepository.save(p);
    }

    private void applyToInsight(TeacherFeedbackRequest req, TeacherFeedback fb) {
        InsightCandidate ic = insightRepository.findById(req.getTargetId())
                .orElseThrow(() -> new IllegalArgumentException("Insight 不存在"));
        fb.setSystemValue("insight=" + ic.getInsightText());
        switch (req.getFeedbackType()) {
            case "confirm" -> ic.setStatus("confirmed");
            case "reject" -> ic.setStatus("rejected");
            case "modify" -> {
                if (req.getCorrectedInsightText() != null && !req.getCorrectedInsightText().isBlank()) {
                    ic.setInsightText(req.getCorrectedInsightText());
                }
                ic.setStatus("modified");
            }
            case "uncertain" -> ic.setStatus("pending_review");
            default -> {}
        }
        insightRepository.save(ic);
    }

    public List<TeacherFeedback> listByStudent(Long studentId) {
        return feedbackRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }
}
