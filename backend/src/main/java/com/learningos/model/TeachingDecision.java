package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v2: 把 Insight 转成"下节课怎么教"的方向。
 * 不直接给题目，只给方向（重点/练习/互动/材料/难度）。
 */
@Data
@Entity
@Table(name = "teaching_decision")
public class TeachingDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "insight_id")
    private Long insightId;

    /** 教学重点：本节课要解决的真问题 */
    @Column(name = "lesson_focus", columnDefinition = "TEXT")
    private String lessonFocus;

    /** 练习策略：少量分步题 / 变式题 / 半成品题 等 */
    @Column(name = "practice_policy", columnDefinition = "TEXT")
    private String practicePolicy;

    /** 互动策略：每步追问 / 自检 / 复述 等 */
    @Column(name = "interaction_policy", columnDefinition = "TEXT")
    private String interactionPolicy;

    /** 材料策略：图示 / 路线图 / PPT / 动画脚本 等 */
    @Column(name = "material_policy", columnDefinition = "TEXT")
    private String materialPolicy;

    /** 难度策略：降低起步 / 保持 / 提升 */
    @Column(name = "difficulty_policy", columnDefinition = "TEXT")
    private String difficultyPolicy;

    /** pending_review / confirmed / modified */
    @Column(length = 30)
    private String status = "pending_review";

    /** 生成依据 ids 集合：{patterns:[],insights:[],teacherFeedback:[]} */
    @Column(name = "source_context", columnDefinition = "JSON")
    private String sourceContext;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
