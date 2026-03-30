package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "video_assets")
public class VideoAsset {

    @Id
    private String id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "source_type")
    private String sourceType = "bilibili";

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    private String title;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "duration_sec")
    private Integer durationSec = 0;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(columnDefinition = "JSON")
    private String outline;

    @Enumerated(EnumType.STRING)
    private VideoStatus status = VideoStatus.PENDING;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum VideoStatus {
        PENDING, PROCESSING, READY, FAILED
    }
}
