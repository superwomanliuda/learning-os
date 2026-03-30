package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "homework_attempts")
public class HomeworkAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "student_answer", columnDefinition = "TEXT")
    private String studentAnswer;

    @Column(name = "hint_count")
    private Integer hintCount = 0;

    @Column(name = "last_hint_level")
    private Integer lastHintLevel = 0;

    @Enumerated(EnumType.STRING)
    private AttemptResult result = AttemptResult.PENDING;

    @Column(name = "stuck_duration_sec")
    private Integer stuckDurationSec = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum AttemptResult {
        PENDING, CORRECT, INCORRECT, ABANDONED
    }
}
