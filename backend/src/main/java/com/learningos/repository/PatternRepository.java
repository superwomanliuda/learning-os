package com.learningos.repository;

import com.learningos.model.Pattern;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatternRepository extends JpaRepository<Pattern, Long> {
    List<Pattern> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<Pattern> findByStudentIdAndConfirmedOrderByCreatedAtDesc(Long studentId, Boolean confirmed);
    Pattern findTopByStudentIdAndTypeAndConfirmedOrderByCreatedAtDesc(Long studentId, String type, Boolean confirmed);
    List<Pattern> findByStudentIdAndConfirmedOrderByConfidenceScoreDescCreatedAtDesc(Long studentId, Boolean confirmed);
    List<Pattern> findBySessionIdOrderByCreatedAtDesc(String sessionId);
    List<Pattern> findByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, String status);
    Pattern findTopByStudentIdAndTypeAndStatusOrderByCreatedAtDesc(Long studentId, String type, String status);
}
