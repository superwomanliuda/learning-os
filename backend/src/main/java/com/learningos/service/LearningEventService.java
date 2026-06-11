package com.learningos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningos.dto.ConfusionMarkRequest;
import com.learningos.dto.EventRequest;
import com.learningos.model.LearningEvent;
import com.learningos.model.Observation;
import com.learningos.repository.LearningEventRepository;
import com.learningos.repository.LearningSessionRepository;
import com.learningos.repository.ObservationRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearningEventService {

    private final LearningEventRepository eventRepository;
    private final LearningSessionRepository sessionRepository;
    private final ObservationRepository observationRepository;
    private final ObjectMapper objectMapper;
    private final ObservationService observationService;
    private final AiService aiService;

    public LearningEventService(LearningEventRepository eventRepository,
                                LearningSessionRepository sessionRepository,
                                ObservationRepository observationRepository,
                                ObjectMapper objectMapper,
                                ObservationService observationService,
                                AiService aiService) {
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.observationRepository = observationRepository;
        this.objectMapper = objectMapper;
        this.observationService = observationService;
        this.aiService = aiService;
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

    /**
     * 老师课堂备注 + 自动从备注里抽多条 observation。
     * 老师写一句话 → 系统弹出 3 条结构化证据，是 demo 高光时刻。
     */
    @Transactional
    public LearningEvent classNote(String sessionId, String noteText) {
        Long studentId = SecurityUtils.currentUserId();
        var session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));

        // 1. 写事件本身
        LearningEvent e = new LearningEvent();
        e.setSessionId(sessionId);
        e.setStudentId(session.getStudentId());
        e.setEventType("class_note");
        e.setTimestampSec(0);
        try {
            e.setPayload(objectMapper.writeValueAsString(Map.of("text", noteText != null ? noteText : "")));
        } catch (JsonProcessingException ex) {
            e.setPayload("{}");
        }
        LearningEvent saved = eventRepository.save(e);

        // 2. 触发普通 observation（"老师课堂备注"占位）
        observationService.generateObservation(saved);

        // 3. AI 抽多条结构化 observation（D 阶段核心）
        if (noteText != null && !noteText.isBlank()) {
            extractAndPersistEvidence(noteText, session.getStudentId(), sessionId);
        }
        return saved;
    }

    private void extractAndPersistEvidence(String noteText, Long studentId, String sessionId) {
        try {
            List<Map<String, Object>> evidences = aiService.extractTeacherNoteEvidence(noteText);
            for (Map<String, Object> ev : evidences) {
                String obsType = String.valueOf(ev.getOrDefault("observation_type", ""));
                String content = String.valueOf(ev.getOrDefault("content", ""));
                if (obsType.isBlank() || content.isBlank()) continue;
                Observation o = new Observation();
                o.setStudentId(studentId);
                o.setSessionId(sessionId);
                o.setObservationType(obsType);
                o.setContent(content);
                o.setSource("teacher_note");
                o.setType("teacher");
                o.setCategory(deriveLegacyCategory(obsType));
                Object conf = ev.getOrDefault("confidence", 0.7);
                o.setConfidence(conf instanceof Number n ? n.doubleValue() : 0.7);
                Map<String, Object> evidenceWrap = new HashMap<>();
                evidenceWrap.put("source", "teacher_note");
                evidenceWrap.put("note_excerpt", noteText.length() > 200 ? noteText.substring(0, 200) + "…" : noteText);
                try {
                    o.setEvidence(objectMapper.writeValueAsString(evidenceWrap));
                } catch (JsonProcessingException e) {
                    o.setEvidence("{}");
                }
                observationRepository.save(o);
            }
        } catch (Exception ex) {
            // AI 抽取失败不阻塞老师备注本体
        }
    }

    private String deriveLegacyCategory(String obsType) {
        if (obsType == null) return "behavior";
        if (obsType.contains("hesitation") || obsType.contains("high_load") || obsType.contains("frustration")) {
            return "hesitation";
        }
        if (obsType.contains("step_error") || obsType.contains("transfer")) {
            return "step_error";
        }
        return "behavior";
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
