package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "observation")
public class Observation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** system | teacher */
    @Column(nullable = false, length = 20)
    private String type;

    /** step_error | hesitation | behavior */
    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "JSON")
    private String evidence;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
