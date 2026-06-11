package com.learningos.repository;

import com.learningos.model.TeacherFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeacherFeedbackRepository extends JpaRepository<TeacherFeedback, Long> {
    List<TeacherFeedback> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);
    List<TeacherFeedback> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<TeacherFeedback> findBySessionIdOrderByCreatedAtDesc(String sessionId);
    List<TeacherFeedback> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);
}
