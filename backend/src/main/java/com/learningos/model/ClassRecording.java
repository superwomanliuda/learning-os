package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "class_recordings")
public class ClassRecording {

    @Id
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "file_path")
    private String filePath;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "confusion_marks", columnDefinition = "JSON")
    private String confusionMarks;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
