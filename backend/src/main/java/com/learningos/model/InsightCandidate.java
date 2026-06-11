package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v2: AI 对 Pattern 的教育解释。
 * 不是 4 字标签，而是对"问题本质 / 可能原因 / 教学影响"的一段说明。
 */
@Data
@Entity
@Table(name = "insight_candidate")
public class InsightCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** 关联的 LearningSession.id 是 String 形式 */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** 关联的 Pattern.id */
    @Column(name = "pattern_id")
    private Long patternId;

    @Column(name = "insight_text", columnDefinition = "TEXT")
    private String insightText;

    /** ["可能原因1","可能原因2"] */
    @Column(name = "possible_causes", columnDefinition = "JSON")
    private String possibleCauses;

    /** ["教学影响1","教学影响2"] */
    @Column(name = "teaching_implications", columnDefinition = "JSON")
    private String teachingImplications;

    @Column
    private Double confidence;

    /** pending_review / confirmed / rejected / modified */
    @Column(length = 30)
    private String status = "pending_review";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
