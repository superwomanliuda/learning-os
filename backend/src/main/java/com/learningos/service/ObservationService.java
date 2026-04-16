package com.learningos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningos.dto.ObservationGenerateRequest;
import com.learningos.model.LearningEvent;
import com.learningos.model.Observation;
import com.learningos.repository.ObservationRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ObservationService {

    private final ObservationRepository observationRepository;
    private final ObjectMapper objectMapper;

    public ObservationService(ObservationRepository observationRepository, ObjectMapper objectMapper) {
        this.observationRepository = observationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Observation generateObservation(LearningEvent event) {
        Observation o = new Observation();
        o.setStudentId(event.getStudentId());
        o.setType(resolveType(event.getEventType()));
        o.setCategory(resolveCategory(event.getEventType()));
        o.setContent(resolveContent(event.getEventType(), event.getPayload()));
        o.setEvidence(toEvidenceJson(event));
        return observationRepository.save(o);
    }

    @Transactional
    public Observation generateObservation(ObservationGenerateRequest req) {
        Long studentId = req.getStudentId() != null ? req.getStudentId() : SecurityUtils.currentUserId();
        Observation o = new Observation();
        o.setStudentId(studentId);
        o.setType(normalizeType(req.getType()));
        o.setCategory(normalizeCategory(req.getCategory()));
        o.setContent(req.getContent() == null || req.getContent().isBlank() ? "手动观察记录" : req.getContent());
        o.setEvidence(toJson(req.getEvidence() == null ? Map.of() : req.getEvidence()));
        return observationRepository.save(o);
    }

    private String resolveType(String eventType) {
        if (eventType == null) {
            return "system";
        }
        String t = eventType.toLowerCase();
        if (t.contains("teacher") || t.equals("class_note")) {
            return "teacher";
        }
        return "system";
    }

    private String resolveCategory(String eventType) {
        if (eventType == null) {
            return "behavior";
        }
        return switch (eventType) {
            case "class_confusion_mark" -> "hesitation";
            case "homework_hint_used" -> "behavior";
            default -> "behavior";
        };
    }

    private String resolveContent(String eventType, String payloadJson) {
        Map<String, Object> payload = parsePayload(payloadJson);
        if (eventType == null) {
            return "学习行为记录";
        }
        return switch (eventType) {
            case "class_confusion_mark" -> "课堂中出现卡顿/不懂标记";
            case "class_note" -> "老师记录课堂备注";
            case "homework_hint_used" -> "作业中使用提示";
            case "video_question" -> "学生提出视频片段问题";
            case "video_practice_generated" -> "系统生成视频片段练习";
            case "textbook_question" -> "学生提出教材问题";
            case "textbook_lesson_generated" -> "系统生成教材微课";
            case "textbook_tutor_turn" -> "教材微课教室产生一轮辅导";
            default -> {
                if (payload.containsKey("context")) {
                    yield "课堂观察：" + String.valueOf(payload.get("context"));
                }
                yield "学习行为记录：" + eventType;
            }
        };
    }

    private String toEvidenceJson(LearningEvent event) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("event_type", event.getEventType());
        evidence.put("session_id", event.getSessionId());
        evidence.put("timestamp_sec", event.getTimestampSec());
        evidence.put("payload", parsePayload(event.getPayload()));
        return toJson(evidence);
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("raw", payloadJson);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String normalizeType(String type) {
        if ("teacher".equalsIgnoreCase(type)) {
            return "teacher";
        }
        return "system";
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "behavior";
        }
        String c = category.toLowerCase();
        if (c.equals("step_error") || c.equals("hesitation") || c.equals("behavior")) {
            return c;
        }
        return "behavior";
    }

    public List<Observation> listByStudent(Long requestedStudentId, Integer limit) {
        Long studentId = resolveStudentId(requestedStudentId);
        List<Observation> all = observationRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        int max = (limit == null || limit <= 0) ? 20 : Math.min(limit, 200);
        return all.size() <= max ? all : all.subList(0, max);
    }

    private Long resolveStudentId(Long requestedStudentId) {
        String role = SecurityUtils.currentRole();
        if ("TEACHER".equalsIgnoreCase(role)) {
            if (requestedStudentId == null) {
                throw new IllegalArgumentException("老师侧查询 Observation 需要 studentId");
            }
            return requestedStudentId;
        }
        return SecurityUtils.currentUserId();
    }
}
