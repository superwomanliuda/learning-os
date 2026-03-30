package com.learningos.repository;

import com.learningos.model.KnowledgeState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface KnowledgeStateRepository extends JpaRepository<KnowledgeState, Long> {
    List<KnowledgeState> findByStudentId(Long studentId);
    Optional<KnowledgeState> findByStudentIdAndKnowledgeKey(Long studentId, String knowledgeKey);
    List<KnowledgeState> findByStudentIdAndStatus(Long studentId, KnowledgeState.KnowledgeStatus status);
}
