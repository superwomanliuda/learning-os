package com.learningos.repository;

import com.learningos.model.TeachingDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeachingDecisionRepository extends JpaRepository<TeachingDecision, Long> {
    List<TeachingDecision> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<TeachingDecision> findBySessionIdOrderByCreatedAtDesc(String sessionId);
    List<TeachingDecision> findByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, String status);
}
