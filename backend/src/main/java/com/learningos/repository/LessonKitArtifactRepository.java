package com.learningos.repository;

import com.learningos.model.LessonKitArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonKitArtifactRepository extends JpaRepository<LessonKitArtifact, Long> {
    List<LessonKitArtifact> findByLessonKitIdOrderByModuleAscPositionAsc(Long lessonKitId);
    List<LessonKitArtifact> findByLessonKitIdAndModuleOrderByPositionAsc(Long lessonKitId, String module);
    void deleteByLessonKitId(Long lessonKitId);
}
