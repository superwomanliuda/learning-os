package com.learningos.repository;

import com.learningos.model.LearningSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearningSessionRepository extends JpaRepository<LearningSession, String> {
    List<LearningSession> findByStudentIdOrderByStartedAtDesc(Long studentId);
    List<LearningSession> findByStudentIdAndTypeOrderByStartedAtDesc(Long studentId, LearningSession.SessionType type);
    List<LearningSession> findByStudentIdAndStatusOrderByStartedAtDesc(Long studentId, LearningSession.Status status);
}
