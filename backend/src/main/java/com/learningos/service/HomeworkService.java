package com.learningos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningos.dto.HomeworkHintRequest;
import com.learningos.dto.HomeworkHintResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.learningos.model.HomeworkAttempt;
import com.learningos.model.LearningEvent;
import com.learningos.model.LearningSession;
import com.learningos.repository.HomeworkAttemptRepository;
import com.learningos.repository.LearningEventRepository;
import com.learningos.repository.LearningSessionRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class HomeworkService {

    private final HomeworkAttemptRepository attemptRepository;
    private final LearningEventRepository eventRepository;
    private final LearningSessionRepository sessionRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public HomeworkService(HomeworkAttemptRepository attemptRepository,
                           LearningEventRepository eventRepository,
                           LearningSessionRepository sessionRepository,
                           AiService aiService,
                           ObjectMapper objectMapper) {
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    /**
     * 服务端决定提示等级：已用 n 次提示后，下一轮为 min(n+1, 4)。
     */
    @Transactional
    public HomeworkHintResponse hint(HomeworkHintRequest req) {
        Long studentId = SecurityUtils.currentUserId();
        String sessionId = resolveHomeworkSessionId(studentId, req.getSessionId());
        HomeworkAttempt a;
        String answer = req.getStudentAnswer() == null ? "" : req.getStudentAnswer();
        if (req.getHomeworkAttemptId() != null) {
            a = attemptRepository.findById(req.getHomeworkAttemptId())
                    .orElseThrow(() -> new IllegalArgumentException("作业记录不存在"));
            if (!a.getStudentId().equals(studentId)) {
                throw new IllegalArgumentException("无权访问该作业记录");
            }
            a.setQuestionText(req.getQuestionText());
            a.setStudentAnswer(answer);
            a.setSessionId(sessionId);
        } else {
            a = new HomeworkAttempt();
            a.setStudentId(studentId);
            a.setSessionId(sessionId);
            a.setQuestionText(req.getQuestionText());
            a.setStudentAnswer(answer);
        }
        attemptRepository.save(a);

        int usedBefore = a.getHintCount() == null ? 0 : a.getHintCount();
        int level = Math.min(usedBefore + 1, 4);

        String reply = aiService.homeworkHint(level, a.getQuestionText(), a.getStudentAnswer(),
                usedBefore, req.getErrorTypes(), req.getContext());

        a.setHintCount(usedBefore + 1);
        a.setLastHintLevel(level);
        attemptRepository.save(a);

        LearningEvent ev = new LearningEvent();
        ev.setSessionId(sessionId);
        ev.setStudentId(studentId);
        ev.setEventType("homework_hint_used");
        ev.setTimestampSec(0);
        Map<String, Object> payload = new HashMap<>();
        payload.put("homework_attempt_id", a.getId());
        payload.put("hint_level", level);
        payload.put("question_excerpt", truncate(a.getQuestionText(), 200));
        try {
            ev.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            ev.setPayload("{}");
        }
        eventRepository.save(ev);

        return new HomeworkHintResponse(a.getId(), level, a.getHintCount(), reply);
    }

    private String resolveHomeworkSessionId(Long studentId, String requested) {
        if (requested != null && !requested.isBlank()) {
            var s = sessionRepository.findById(requested).orElseThrow(() -> new IllegalArgumentException("会话不存在"));
            if (!s.getStudentId().equals(studentId)) {
                throw new IllegalArgumentException("无权使用该会话");
            }
            return requested;
        }
        LearningSession s = new LearningSession();
        s.setId("homework_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6));
        s.setStudentId(studentId);
        s.setType(LearningSession.SessionType.HOMEWORK);
        s.setTitle("作业辅导（自动会话）");
        s.setStartedAt(LocalDateTime.now());
        s.setStatus(LearningSession.Status.ONGOING);
        sessionRepository.save(s);
        return s.getId();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    public java.util.List<HomeworkAttempt> myAttempts() {
        return attemptRepository.findByStudentIdOrderByCreatedAtDesc(SecurityUtils.currentUserId());
    }
}
