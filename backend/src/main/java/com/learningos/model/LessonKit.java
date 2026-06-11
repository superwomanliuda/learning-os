package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v2: 一份完整课件的总信息。具体内容拆到 LessonKitArtifact。
 */
@Data
@Entity
@Table(name = "lesson_kit")
public class LessonKit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    /** 来源 session（采集了诊断证据的那一节课） */
    @Column(name = "source_session_id", length = 64)
    private String sourceSessionId;

    @Column(length = 255)
    private String title;

    /** 学科：初中数学 等 */
    @Column(length = 50)
    private String subject;

    @Column(length = 50)
    private String grade;

    /** 知识点（教师生成时填写） */
    @Column(name = "knowledge_point", length = 255)
    private String knowledgePoint;

    /** 教材范围：粘贴的教材文字 / PDF 提取的文本 */
    @Column(name = "textbook_excerpt", columnDefinition = "TEXT")
    private String textbookExcerpt;

    /** 老师特殊诉求 */
    @Column(name = "special_request", columnDefinition = "TEXT")
    private String specialRequest;

    /** 备课偏好：ai_recommend / concept_first / problem_first / more_interaction / more_explanation */
    @Column(name = "lesson_preference", length = 50)
    private String lessonPreference = "ai_recommend";

    @Column(name = "duration_minutes")
    private Integer durationMinutes = 45;

    /** draft / finalized */
    @Column(length = 30)
    private String status = "draft";

    /** AI 写的"备课思路解读"，告诉老师为什么这样设计这节课 */
    @Column(name = "design_rationale", columnDefinition = "TEXT")
    private String designRationale;

    /** 生成上下文：用了哪些 patterns/insights/decisions/feedback */
    @Column(name = "generation_context", columnDefinition = "JSON")
    private String generationContext;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
