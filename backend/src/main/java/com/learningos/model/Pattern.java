package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生问题候选（v2 spec 中的 pattern_candidate）。
 * 兼容旧字段：type / confidenceScore / evidenceSummary / confirmed。
 * 新增字段：sessionId / severity / trend / status / evidenceIds / falsePositiveRisks / updatedAt。
 *
 * status 状态机：pending_review / confirmed / rejected / modified / observing
 * 旧 confirmed:Boolean 保留（向后兼容旧驾驶舱），但实际状态以 status 为准。
 */
@Data
@Entity
@Table(name = "pattern")
public class Pattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** v2 pattern 标签：dependency / process_break / surface_understanding / modeling_entry_difficulty / confirmation_dependency / high_cognitive_load 等 */
    @Column(nullable = false, length = 60)
    private String type;

    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore;

    /** low / medium / high */
    @Column(length = 20)
    private String severity;

    /** improving / stable / worsening / unknown */
    @Column(length = 30)
    private String trend;

    @Column(name = "evidence_summary", columnDefinition = "TEXT")
    private String evidenceSummary;

    /** 关联的 observation id 列表，JSON：[1,2,3] */
    @Column(name = "evidence_ids", columnDefinition = "JSON")
    private String evidenceIds;

    /** 误判风险列表，JSON：["新题型","建模入口困难"] */
    @Column(name = "false_positive_risks", columnDefinition = "JSON")
    private String falsePositiveRisks;

    /** pending_review / confirmed / rejected / modified / observing */
    @Column(length = 30)
    private String status = "pending_review";

    /** 旧字段：保留兼容，实际状态以 status 为准 */
    @Column(nullable = false)
    private Boolean confirmed = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
