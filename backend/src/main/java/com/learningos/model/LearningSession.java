package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "learning_sessions")
public class LearningSession {

    @Id
    private String id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionType type;

    private String title;

    @Column(name = "started_at")
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_sec")
    private Integer durationSec = 0;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ONGOING;

    public enum SessionType {
        CLASS, VIDEO, HOMEWORK
    }

    public enum Status {
        ONGOING, COMPLETED
    }
}
