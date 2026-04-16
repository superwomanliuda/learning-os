package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "pattern")
public class Pattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** step_break | transfer_issue | dependency */
    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore;

    @Column(name = "evidence_summary", columnDefinition = "TEXT")
    private String evidenceSummary;

    @Column(nullable = false)
    private Boolean confirmed = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
