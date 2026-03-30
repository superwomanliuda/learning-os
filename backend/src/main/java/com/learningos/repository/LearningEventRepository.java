package com.learningos.repository;

import com.learningos.model.LearningEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearningEventRepository extends JpaRepository<LearningEvent, Long> {
    List<LearningEvent> findBySessionIdOrderByTimestampSec(String sessionId);
    List<LearningEvent> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<LearningEvent> findBySessionIdAndEventType(String sessionId, String eventType);
    List<LearningEvent> findByStudentIdAndEventTypeOrderByCreatedAtDesc(Long studentId, String eventType);
}
