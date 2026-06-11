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

/**
 * 把原始事件压缩成"事实性观察"。
 * 新增对 v2 事件集的识别：step_submit / step_revision / hesitation /
 * help_request / explanation_response / variant_question_submit /
 * student_speak / consecutive_error / skip_step / frustration_signal。
 *
 * 旧字段 type/category 保留向下兼容，新字段 observationType/source/confidence/sessionId
 * 给 v2 流水线用。
 */
@Service
public class ObservationService {

    private final ObservationRepository observationRepository;
    private final ObjectMapper objectMapper;

    public ObservationService(ObservationRepository observationRepository, ObjectMapper objectMapper) {
        this.observationRepository = observationRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 事件触发的 observation 抽取。返回 null 表示该事件不需要抽（如 step_submit 且正确）。
     */
    @Transactional
    public Observation generateObservation(LearningEvent event) {
        String eventType = event.getEventType();
        Map<String, Object> payload = parsePayload(event.getPayload());

        ObservationSpec spec = mapEventToSpec(eventType, payload);
        if (spec == null) {
            return null;
        }

        Observation o = new Observation();
        o.setStudentId(event.getStudentId());
        o.setSessionId(event.getSessionId());
        o.setObservationType(spec.observationType);
        o.setSource(spec.source);
        o.setConfidence(spec.confidence);
        o.setType(spec.legacyType);
        o.setCategory(spec.legacyCategory);
        o.setContent(spec.content);
        o.setEvidence(toEvidenceJson(event));
        return observationRepository.save(o);
    }

    /**
     * 老师/前端手动创建的 observation。保留旧 API 兼容。
     */
    @Transactional
    public Observation generateObservation(ObservationGenerateRequest req) {
        Long studentId = req.getStudentId() != null ? req.getStudentId() : SecurityUtils.currentUserId();
        Observation o = new Observation();
        o.setStudentId(studentId);
        o.setType(normalizeLegacyType(req.getType()));
        o.setCategory(normalizeLegacyCategory(req.getCategory()));
        o.setContent(req.getContent() == null || req.getContent().isBlank() ? "手动观察记录" : req.getContent());
        o.setEvidence(toJson(req.getEvidence() == null ? Map.of() : req.getEvidence()));
        o.setSource("teacher".equalsIgnoreCase(o.getType()) ? "teacher_note" : "system");
        o.setConfidence(1.0);
        return observationRepository.save(o);
    }

    /**
     * v2 事件类型 → observation 规格映射表。
     * 触发条件不满足时返回 null（事件入库但不抽 observation）。
     */
    private ObservationSpec mapEventToSpec(String eventType, Map<String, Object> payload) {
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "step_submit" -> mapStepSubmit(payload);
            case "step_revision" -> new ObservationSpec(
                    "step_revision_signal", "system", "behavior", "system", 0.85,
                    "学生修改了第 " + payload.getOrDefault("step", "?") + " 步");
            case "hesitation" -> mapHesitation(payload);
            case "help_request" -> mapHelpRequest(payload);
            case "explanation_response" -> mapExplanation(payload);
            case "variant_question_submit" -> mapVariantSubmit(payload);
            case "student_speak" -> mapStudentSpeak(payload);
            case "consecutive_error" -> new ObservationSpec(
                    "high_load_signal", "system", "step_error", "system", 0.9,
                    "连续错误 " + payload.getOrDefault("count", "?") + " 次");
            case "skip_step" -> new ObservationSpec(
                    "skip_steps_signal", "system", "behavior", "system", 0.9,
                    "学生跳过中间步骤");
            case "frustration_signal" -> new ObservationSpec(
                    "frustration_signal", "student_response", "behavior", "student_response", 0.8,
                    "学生表现出挫败情绪");
            case "class_confusion_mark" -> new ObservationSpec(
                    "high_load_signal", "system", "hesitation", "student_response", 0.85,
                    "课堂中标记不懂：" + payload.getOrDefault("context", ""));
            case "class_note" -> new ObservationSpec(
                    "teacher_note_marker", "teacher", "behavior", "teacher_note", 0.9,
                    "老师课堂备注：" + truncate(String.valueOf(payload.getOrDefault("text", "")), 200));
            case "homework_hint_used" -> mapHomeworkHint(payload);
            case "video_question" -> new ObservationSpec(
                    "vague_understanding_signal", "system", "behavior", "system", 0.6,
                    "学生提出视频片段问题");
            case "textbook_question" -> new ObservationSpec(
                    "vague_understanding_signal", "system", "behavior", "system", 0.6,
                    "学生提出教材问题");
            default -> defaultSpec(eventType, payload);
        };
    }

    private ObservationSpec mapStepSubmit(Map<String, Object> payload) {
        boolean correct = boolValue(payload.get("correct"), false);
        if (correct) {
            return null; // 步骤正确不抽 observation
        }
        Object errorStep = payload.getOrDefault("error_step", payload.getOrDefault("step", "?"));
        return new ObservationSpec(
                "step_error", "system", "step_error", "system", 0.9,
                "在第 " + errorStep + " 步答错：" + truncate(String.valueOf(payload.getOrDefault("content", "")), 100));
    }

    private ObservationSpec mapHesitation(Map<String, Object> payload) {
        int pause = intValue(payload.get("pause_sec"), 0);
        if (pause < 30) {
            return null;
        }
        Object step = payload.getOrDefault("step", payload.getOrDefault("at_step", "?"));
        return new ObservationSpec(
                "hesitation", "system", "hesitation", "system", 0.9,
                "学生在步骤 " + step + " 停顿 " + pause + " 秒");
    }

    private ObservationSpec mapHelpRequest(Map<String, Object> payload) {
        int withinSec = intValue(payload.get("within_sec"), -1);
        boolean isQuickHelp = withinSec >= 0 && withinSec <= 15;
        String message = String.valueOf(payload.getOrDefault("message", ""));
        if (isQuickHelp || message.contains("不知道从哪") || message.contains("不知道怎么开始")) {
            return new ObservationSpec(
                    "start_difficulty_signal", "student_response", "behavior", "student_response", 0.85,
                    "学生开始即求助：" + truncate(message, 100));
        }
        return new ObservationSpec(
                "hint_dependency_signal", "system", "behavior", "system", 0.8,
                "学生求助：" + truncate(message, 100));
    }

    private ObservationSpec mapExplanation(Map<String, Object> payload) {
        String quality = String.valueOf(payload.getOrDefault("quality", "")).toLowerCase();
        String content = String.valueOf(payload.getOrDefault("content", ""));
        return switch (quality) {
            case "vague" -> new ObservationSpec(
                    "vague_understanding_signal", "student_response", "behavior", "student_response", 0.8,
                    "学生解释含混：" + truncate(content, 100));
            case "failed", "fail" -> new ObservationSpec(
                    "concept_explanation_failure", "student_response", "behavior", "student_response", 0.85,
                    "学生无法解释：" + truncate(content, 100));
            case "clear" -> null; // 解释清晰不抽
            default -> new ObservationSpec(
                    "expression_difficulty_signal", "student_response", "behavior", "student_response", 0.7,
                    "学生表达不充分：" + truncate(content, 100));
        };
    }

    private ObservationSpec mapVariantSubmit(Map<String, Object> payload) {
        boolean correct = boolValue(payload.get("correct"), true);
        if (correct) {
            return null;
        }
        return new ObservationSpec(
                "transfer_signal", "system", "step_error", "system", 0.85,
                "学生原型题会、变式题失败：" + truncate(String.valueOf(payload.getOrDefault("question", "")), 80));
    }

    private ObservationSpec mapStudentSpeak(Map<String, Object> payload) {
        String message = String.valueOf(payload.getOrDefault("message", payload.getOrDefault("content", "")));
        String lower = message.toLowerCase();
        if (message.contains("不会") || message.contains("不知道从哪")) {
            return new ObservationSpec(
                    "start_difficulty_signal", "student_response", "behavior", "student_response", 0.8,
                    "学生表达启动困难：" + truncate(message, 100));
        }
        if (message.contains("对吗") || message.contains("对不对") || message.contains("可以吗") || message.contains("是这样吗")) {
            return new ObservationSpec(
                    "confirmation_dependency_signal", "student_response", "behavior", "student_response", 0.85,
                    "学生求确认：" + truncate(message, 100));
        }
        if (message.contains("卡住") || message.contains("不懂") || message.contains("懵")) {
            return new ObservationSpec(
                    "high_load_signal", "student_response", "hesitation", "student_response", 0.8,
                    "学生主动标记卡顿：" + truncate(message, 100));
        }
        if (message.contains("反正") || message.contains("差不多") || lower.contains("whatever")) {
            return new ObservationSpec(
                    "vague_understanding_signal", "student_response", "behavior", "student_response", 0.75,
                    "学生表达含混：" + truncate(message, 100));
        }
        // 其他主动发言不抽强信号 observation
        return null;
    }

    private ObservationSpec mapHomeworkHint(Map<String, Object> payload) {
        int level = intValue(payload.get("hint_level"), 1);
        if (level < 2) {
            return null;
        }
        return new ObservationSpec(
                "hint_dependency_signal", "system", "behavior", "system", 0.7,
                "作业中使用第 " + level + " 次提示");
    }

    private ObservationSpec defaultSpec(String eventType, Map<String, Object> payload) {
        if (payload.containsKey("context")) {
            return new ObservationSpec(
                    "general_signal", "system", "behavior", "system", 0.5,
                    "学习行为：" + payload.get("context"));
        }
        // 其他未知事件不主动抽 observation
        return null;
    }

    private boolean boolValue(Object v, boolean fallback) {
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return fallback;
    }

    private int intValue(Object v, int fallback) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return fallback;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
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

    private String normalizeLegacyType(String type) {
        if ("teacher".equalsIgnoreCase(type)) {
            return "teacher";
        }
        return "system";
    }

    private String normalizeLegacyCategory(String category) {
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

    /** 用于 mapEventToSpec 的内部规格对象 */
    private record ObservationSpec(
            String observationType,
            String legacyType,
            String legacyCategory,
            String source,
            double confidence,
            String content
    ) {}
}
