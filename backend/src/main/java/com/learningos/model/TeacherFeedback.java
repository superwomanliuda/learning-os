package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v2: 老师对 AI 判断的纠错记录。最简版：
 * - 4 个按钮（confirm/reject/modify/uncertain）
 * - 老师可补一段文字反对，不做 AI 澄清对话
 */
@Data
@Entity
@Table(name = "teacher_feedback")
public class TeacherFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** pattern / insight / teaching_decision / artifact */
    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 系统当时的判断（用于事后回溯） */
    @Column(name = "system_value", columnDefinition = "TEXT")
    private String systemValue;

    /** confirm / reject / modify / uncertain */
    @Column(name = "feedback_type", nullable = false, length = 30)
    private String feedbackType;

    /** 老师补充的自然语言文字（最简版直接存原文，不做结构化） */
    @Column(name = "teacher_text", columnDefinition = "TEXT")
    private String teacherText;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
