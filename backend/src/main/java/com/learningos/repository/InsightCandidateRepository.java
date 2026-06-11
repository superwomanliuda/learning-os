package com.learningos.repository;

import com.learningos.model.InsightCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsightCandidateRepository extends JpaRepository<InsightCandidate, Long> {
    List<InsightCandidate> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<InsightCandidate> findBySessionIdOrderByCreatedAtDesc(String sessionId);
    List<InsightCandidate> findByPatternIdOrderByCreatedAtDesc(Long patternId);
    List<InsightCandidate> findByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, String status);
}
