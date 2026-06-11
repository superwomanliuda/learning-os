package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习观察（事实性观察，不是诊断）。
 * 兼容旧字段：type / category / content / evidence。
 * 新增字段：sessionId / source / confidence / observationType。
 *
 * source: system / teacher_note / student_response
 * observationType (v2 spec)：hesitation / step_error / repeated_error /
 *   hint_dependency_signal / confirmation_dependency_signal /
 *   concept_explanation_failure / vague_understanding_signal /
 *   expression_difficulty_signal / start_difficulty_signal /
 *   transfer_signal / skip_steps_signal / high_load_signal / frustration_signal 等
 */
@Data
@Entity
@Table(name = "observation")
public class Observation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** 旧字段：system | teacher，保留兼容 */
    @Column(nullable = false, length = 20)
    private String type;

    /** 旧字段：step_error | hesitation | behavior，保留兼容 */
    @Column(nullable = false, length = 50)
    private String category;

    /** 新字段：v2 spec 标准 observation_type 枚举 */
    @Column(name = "observation_type", length = 60)
    private String observationType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "JSON")
    private String evidence;

    /** system / teacher_note / student_response */
    @Column(length = 30)
    private String source = "system";

    @Column
    private Double confidence = 1.0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
