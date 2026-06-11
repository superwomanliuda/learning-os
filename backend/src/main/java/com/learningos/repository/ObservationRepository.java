package com.learningos.repository;

import com.learningos.model.Observation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObservationRepository extends JpaRepository<Observation, Long> {
    List<Observation> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<Observation> findByStudentIdAndCategoryOrderByCreatedAtDesc(Long studentId, String category);
    List<Observation> findBySessionIdOrderByCreatedAtDesc(String sessionId);
    List<Observation> findByStudentIdAndObservationTypeOrderByCreatedAtDesc(Long studentId, String observationType);
}
