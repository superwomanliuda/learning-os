package com.learningos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningos.dto.ConfusionMarkRequest;
import com.learningos.dto.EventRequest;
import com.learningos.model.LearningEvent;
import com.learningos.repository.LearningEventRepository;
import com.learningos.repository.LearningSessionRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class LearningEventService {

    private final LearningEventRepository eventRepository;
    private final LearningSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final ObservationService observationService;

    public LearningEventService(LearningEventRepository eventRepository,
                                LearningSessionRepository sessionRepository,
                                ObjectMapper objectMapper,
                                ObservationService observationService) {
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
        this.observationService = observationService;
    }

    @Transactional
    public LearningEvent report(EventRequest req) {
        Long studentId = SecurityUtils.currentUserId();
        if (!sessionRepository.existsById(req.getSessionId())) {
            throw new IllegalArgumentException("会话不存在");
        }
        var session = sessionRepository.findById(req.getSessionId()).orElseThrow();
        if (!session.getStudentId().equals(studentId)) {
            throw new IllegalArgumentException("无权向该会话上报事件");
        }
        LearningEvent e = new LearningEvent();
        e.setSessionId(req.getSessionId());
        e.setStudentId(studentId);
        e.setEventType(req.getEventType());
        e.setTimestampSec(req.getTimestampSec() != null ? req.getTimestampSec() : 0);
        try {
            e.setPayload(req.getPayload() == null ? null : objectMapper.writeValueAsString(req.getPayload()));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("payload 无法序列化为 JSON");
        }
        LearningEvent saved = eventRepository.save(e);
        observationService.generateObservation(saved);
        return saved;
    }

    @Transactional
    public LearningEvent classConfusionMark(String sessionId, ConfusionMarkRequest req) {
        EventRequest er = new EventRequest();
        er.setSessionId(sessionId);
        er.setEventType("class_confusion_mark");
        er.setTimestampSec(req.getTimestampSec());
        er.setPayload(Map.of("context", req.getContext() != null ? req.getContext() : ""));
        return report(er);
    }

    @Transactional
    public LearningEvent classNote(String sessionId, String noteText) {
        EventRequest er = new EventRequest();
        er.setSessionId(sessionId);
        er.setEventType("class_note");
        er.setTimestampSec(0);
        er.setPayload(Map.of("text", noteText != null ? noteText : ""));
        return report(er);
    }

    public List<LearningEvent> bySession(String sessionId) {
        var s = sessionRepository.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!s.getStudentId().equals(SecurityUtils.currentUserId())) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        return eventRepository.findBySessionIdOrderByTimestampSec(sessionId);
    }

    public List<LearningEvent> myRecent(int limit) {
        List<LearningEvent> all = eventRepository.findByStudentIdOrderByCreatedAtDesc(SecurityUtils.currentUserId());
        return all.size() <= limit ? all : all.subList(0, limit);
    }
}
