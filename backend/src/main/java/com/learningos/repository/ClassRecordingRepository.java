package com.learningos.repository;

import com.learningos.model.ClassRecording;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClassRecordingRepository extends JpaRepository<ClassRecording, String> {
    Optional<ClassRecording> findBySessionId(String sessionId);
}
