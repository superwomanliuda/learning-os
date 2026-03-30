package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_states",
    uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "knowledge_key"}))
public class KnowledgeState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "knowledge_key", nullable = false)
    private String knowledgeKey;

    @Enumerated(EnumType.STRING)
    private KnowledgeStatus status = KnowledgeStatus.UNKNOWN;

    @Column(columnDefinition = "JSON")
    private String evidence;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum KnowledgeStatus {
        UNKNOWN, WEAK, UNSTABLE, MASTERED
    }
}
