package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v2: 老师偏好画像。Reflection 闭环本期砍了，先建表占位。
 * profile_json 结构后续拓展，最简内容：
 * {
 *   "preferred_lesson_style": "...",
 *   "prefers_question_count": 5,
 *   "pattern_correction_bias": {"concept_misconception_to_expression_difficulty": 3}
 * }
 */
@Data
@Entity
@Table(name = "teacher_profile")
public class TeacherProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false, unique = true)
    private Long teacherId;

    @Column(name = "profile_json", columnDefinition = "JSON")
    private String profileJson;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
