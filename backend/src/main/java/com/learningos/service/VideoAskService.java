package com.learningos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningos.dto.TextbookAskRequest;
import com.learningos.dto.TextbookLessonRequest;
import com.learningos.dto.TextbookTutorRequest;
import com.learningos.dto.VideoAskRequest;
import com.learningos.dto.VideoPracticeRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.learningos.model.LearningEvent;
import com.learningos.model.LearningSession;
import com.learningos.repository.LearningEventRepository;
import com.learningos.repository.LearningSessionRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VideoAskService {

    private final VideoService videoService;
    private final AiService aiService;
    private final LearningEventRepository eventRepository;
    private final LearningSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final ObservationService observationService;

    public VideoAskService(VideoService videoService, AiService aiService,
                           LearningEventRepository eventRepository,
                           LearningSessionRepository sessionRepository,
                           ObjectMapper objectMapper,
                           ObservationService observationService) {
        this.videoService = videoService;
        this.aiService = aiService;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
        this.observationService = observationService;
    }

    @Transactional
    public Map<String, Object> ask(VideoAskRequest req, String videoSessionId) {
        var v = videoService.getOwned(req.getVideoId());
        String segmentText = videoService.segmentTranscript(v, req.getSegmentStart(), req.getSegmentEnd());
        String reply = aiService.videoSegmentExplain(v.getTitle(), req.getSegmentStart(), req.getSegmentEnd(),
                segmentText, req.getQuestion());

        Long studentId = SecurityUtils.currentUserId();
        String sid = resolveVideoSessionId(studentId, videoSessionId);
        LearningEvent ev = new LearningEvent();
        ev.setSessionId(sid);
        ev.setStudentId(studentId);
        ev.setEventType("video_question");
        ev.setTimestampSec(req.getSegmentStart());
        Map<String, Object> payload = new HashMap<>();
        payload.put("question", req.getQuestion());
        payload.put("video_id", req.getVideoId());
        payload.put("video_segment", formatSeg(req.getSegmentStart(), req.getSegmentEnd()));
        try {
            ev.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            ev.setPayload("{}");
        }
        LearningEvent saved = eventRepository.save(ev);
        observationService.generateObservation(saved);

        return Map.of(
                "reply", reply,
                "videoId", v.getId(),
                "segment", formatSeg(req.getSegmentStart(), req.getSegmentEnd())
        );
    }

    @Transactional
    public Map<String, Object> practice(VideoPracticeRequest req, String videoSessionId) {
        var v = videoService.getOwned(req.getVideoId());
        String segmentText = videoService.segmentTranscript(v, req.getSegmentStart(), req.getSegmentEnd());
        Map<String, Object> practice = aiService.videoPractice(
                v.getTitle(),
                req.getSegmentStart(),
                req.getSegmentEnd(),
                segmentText,
                req.getLevel(),
                req.getQuestionCount() == null ? 3 : req.getQuestionCount()
        );

        Long studentId = SecurityUtils.currentUserId();
        String sid = resolveVideoSessionId(studentId, videoSessionId);
        LearningEvent ev = new LearningEvent();
        ev.setSessionId(sid);
        ev.setStudentId(studentId);
        ev.setEventType("video_practice_generated");
        ev.setTimestampSec(req.getSegmentStart());
        Map<String, Object> payload = new HashMap<>();
        payload.put("video_id", req.getVideoId());
        payload.put("video_segment", formatSeg(req.getSegmentStart(), req.getSegmentEnd()));
        payload.put("level", req.getLevel() == null ? "基础" : req.getLevel());
        payload.put("question_count", req.getQuestionCount() == null ? 3 : req.getQuestionCount());
        try {
            ev.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            ev.setPayload("{}");
        }
        LearningEvent saved = eventRepository.save(ev);
        observationService.generateObservation(saved);

        return Map.of(
                "summary", practice.getOrDefault("summary", ""),
                "questions", practice.getOrDefault("questions", List.of()),
                "videoId", v.getId(),
                "segment", formatSeg(req.getSegmentStart(), req.getSegmentEnd())
        );
    }

    @Transactional
    public Map<String, Object> textbookAsk(TextbookAskRequest req, String videoSessionId) {
        var v = videoService.getOwned(req.getTextbookId());
        if (!"textbook".equalsIgnoreCase(v.getSourceType())) {
            throw new IllegalArgumentException("该资源不是教材");
        }
        String answer = aiService.textbookAnswer(v.getTitle(), v.getTranscript(), req.getQuestion());

        Long studentId = SecurityUtils.currentUserId();
        String sid = resolveVideoSessionId(studentId, videoSessionId);
        LearningEvent ev = new LearningEvent();
        ev.setSessionId(sid);
        ev.setStudentId(studentId);
        ev.setEventType("textbook_question");
        ev.setTimestampSec(0);
        Map<String, Object> payload = new HashMap<>();
        payload.put("textbook_id", req.getTextbookId());
        payload.put("question", req.getQuestion());
        try {
            ev.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            ev.setPayload("{}");
        }
        LearningEvent saved = eventRepository.save(ev);
        observationService.generateObservation(saved);

        return Map.of(
                "reply", answer,
                "textbookId", v.getId()
        );
    }

    @Transactional
    public Map<String, Object> textbookLesson(TextbookLessonRequest req, String videoSessionId) {
        var v = videoService.getOwned(req.getTextbookId());
        if (!"textbook".equalsIgnoreCase(v.getSourceType())) {
            throw new IllegalArgumentException("该资源不是教材");
        }
        int minutes = req.getTargetMinutes() == null ? 4 : req.getTargetMinutes();
        Map<String, Object> lesson = aiService.textbookLessonPlan(v.getTitle(), v.getTranscript(), minutes, req.getFocusQuestion());

        Long studentId = SecurityUtils.currentUserId();
        String sid = resolveVideoSessionId(studentId, videoSessionId);
        LearningEvent ev = new LearningEvent();
        ev.setSessionId(sid);
        ev.setStudentId(studentId);
        ev.setEventType("textbook_lesson_generated");
        ev.setTimestampSec(0);
        Map<String, Object> payload = new HashMap<>();
        payload.put("textbook_id", req.getTextbookId());
        payload.put("target_minutes", minutes);
        payload.put("focus_question", req.getFocusQuestion() == null ? "" : req.getFocusQuestion());
        try {
            ev.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            ev.setPayload("{}");
        }
        LearningEvent saved = eventRepository.save(ev);
        observationService.generateObservation(saved);

        return Map.of(
                "lessonTitle", lesson.getOrDefault("lessonTitle", "1对1微课"),
                "durationMin", lesson.getOrDefault("durationMin", minutes),
                "opening", lesson.getOrDefault("opening", ""),
                "teachingPhases", lesson.getOrDefault("teachingPhases", List.of()),
                "keyPoints", lesson.getOrDefault("keyPoints", List.of()),
                "interactiveQuestions", lesson.getOrDefault("interactiveQuestions", List.of()),
                "endingTask", lesson.getOrDefault("endingTask", ""),
                "textbookId", v.getId()
        );
    }

    public byte[] textToSpeech(String text) {
        return aiService.textToSpeech(text);
    }

    @Transactional
    public Map<String, Object> textbookTutor(TextbookTutorRequest req, String videoSessionId) {
        var v = videoService.getOwned(req.getTextbookId());
        if (!"textbook".equalsIgnoreCase(v.getSourceType())) {
            throw new IllegalArgumentException("该资源不是教材");
        }
        Map<String, Object> turn = aiService.textbookTutorTurn(v.getTitle(), v.getTranscript(), req.getMessages());

        Long studentId = SecurityUtils.currentUserId();
        String sid = resolveVideoSessionId(studentId, videoSessionId);
        LearningEvent ev = new LearningEvent();
        ev.setSessionId(sid);
        ev.setStudentId(studentId);
        ev.setEventType("textbook_tutor_turn");
        ev.setTimestampSec(0);
        try {
            ev.setPayload(objectMapper.writeValueAsString(Map.of(
                    "textbook_id", req.getTextbookId(),
                    "message_count", req.getMessages() == null ? 0 : req.getMessages().size()
            )));
        } catch (JsonProcessingException e) {
            ev.setPayload("{}");
        }
        LearningEvent saved = eventRepository.save(ev);
        observationService.generateObservation(saved);

        return Map.of(
                "speech", turn.getOrDefault("speech", "我们继续用教材这一段来讲。"),
                "board", turn.getOrDefault("board", null),
                "textbookId", v.getId()
        );
    }

    private static String formatSeg(int a, int b) {
        return "%02d:%02d-%02d:%02d".formatted(a / 60, a % 60, b / 60, b % 60);
    }

    private String resolveVideoSessionId(Long studentId, String requested) {
        if (requested != null && !requested.isBlank()) {
            var s = sessionRepository.findById(requested).orElseThrow(() -> new IllegalArgumentException("会话不存在"));
            if (!s.getStudentId().equals(studentId)) {
                throw new IllegalArgumentException("无权使用该会话");
            }
            return requested;
        }
        LearningSession s = new LearningSession();
        s.setId("video_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6));
        s.setStudentId(studentId);
        s.setType(LearningSession.SessionType.VIDEO);
        s.setTitle("视频问答（自动会话）");
        s.setStartedAt(LocalDateTime.now());
        s.setStatus(LearningSession.Status.ONGOING);
        sessionRepository.save(s);
        return s.getId();
    }
}
