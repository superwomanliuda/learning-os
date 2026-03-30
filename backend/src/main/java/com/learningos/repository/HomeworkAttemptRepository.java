package com.learningos.repository;

import com.learningos.model.HomeworkAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HomeworkAttemptRepository extends JpaRepository<HomeworkAttempt, Long> {
    List<HomeworkAttempt> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<HomeworkAttempt> findBySessionId(String sessionId);

    @Query("SELECT AVG(a.hintCount) FROM HomeworkAttempt a WHERE a.studentId = :studentId")
    Double avgHintCountByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT COUNT(a) FROM HomeworkAttempt a WHERE a.studentId = :studentId AND a.result = :result AND a.hintCount = 0")
    Long countIndependentCorrectByStudentId(@Param("studentId") Long studentId,
                                            @Param("result") HomeworkAttempt.AttemptResult result);
}
