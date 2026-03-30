package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "learning_events")
public class LearningEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "timestamp_sec")
    private Integer timestampSec = 0;

    @Column(columnDefinition = "JSON")
    private String payload;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
