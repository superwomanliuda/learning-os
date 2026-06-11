package com.learningos.repository;

import com.learningos.model.LessonKit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonKitRepository extends JpaRepository<LessonKit, Long> {
    List<LessonKit> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<LessonKit> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);
    List<LessonKit> findBySourceSessionIdOrderByCreatedAtDesc(String sourceSessionId);
}
