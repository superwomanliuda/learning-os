package com.learningos.repository;

import com.learningos.model.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StrategyRepository extends JpaRepository<Strategy, Long> {
    List<Strategy> findByStudentIdOrderByPriorityAscCreatedAtDesc(Long studentId);
    List<Strategy> findByStudentIdAndActiveOrderByPriorityAscCreatedAtDesc(Long studentId, Boolean active);
    Strategy findTopByStudentIdAndPatternIdAndActiveOrderByCreatedAtDesc(Long studentId, Long patternId, Boolean active);
}
