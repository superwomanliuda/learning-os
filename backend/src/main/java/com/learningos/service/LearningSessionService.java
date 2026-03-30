package com.learningos.service;

import com.learningos.dto.SessionCreateRequest;
import com.learningos.model.LearningSession;
import com.learningos.repository.LearningSessionRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class LearningSessionService {

    private final LearningSessionRepository sessionRepository;

    public LearningSessionService(LearningSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public LearningSession create(SessionCreateRequest req) {
        Long studentId = SecurityUtils.currentUserId();
        LearningSession s = new LearningSession();
        s.setId(req.getType().name().toLowerCase() + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8));
        s.setStudentId(studentId);
        s.setType(req.getType());
        s.setTitle(req.getTitle() != null ? req.getTitle() : defaultTitle(req.getType()));
        s.setStartedAt(LocalDateTime.now());
        s.setStatus(LearningSession.Status.ONGOING);
        return sessionRepository.save(s);
    }

    private static String defaultTitle(LearningSession.SessionType type) {
        return switch (type) {
            case CLASS -> "1对1课堂";
            case VIDEO -> "视频学习";
            case HOMEWORK -> "作业辅导";
        };
    }

    public List<LearningSession> mySessions() {
        return sessionRepository.findByStudentIdOrderByStartedAtDesc(SecurityUtils.currentUserId());
    }

    public LearningSession getOwned(String sessionId) {
        LearningSession s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!s.getStudentId().equals(SecurityUtils.currentUserId())) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        return s;
    }

    @Transactional
    public LearningSession end(String sessionId) {
        LearningSession s = getOwned(sessionId);
        s.setEndedAt(LocalDateTime.now());
        s.setStatus(LearningSession.Status.COMPLETED);
        if (s.getStartedAt() != null && s.getEndedAt() != null) {
            s.setDurationSec((int) java.time.Duration.between(s.getStartedAt(), s.getEndedAt()).getSeconds());
        }
        return sessionRepository.save(s);
    }
}
