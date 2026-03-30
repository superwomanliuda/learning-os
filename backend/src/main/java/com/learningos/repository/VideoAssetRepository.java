package com.learningos.repository;

import com.learningos.model.VideoAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VideoAssetRepository extends JpaRepository<VideoAsset, String> {
    List<VideoAsset> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<VideoAsset> findByStudentIdAndStatus(Long studentId, VideoAsset.VideoStatus status);
}
