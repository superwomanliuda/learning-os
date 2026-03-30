package com.learningos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    public VideoAskService(VideoService videoService, AiService aiService,
                           LearningEventRepository eventRepository,
                           LearningSessionRepository sessionRepository,
                           ObjectMapper objectMapper) {
        this.videoService = videoService;
        this.aiService = aiService;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
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
        eventRepository.save(ev);

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
        eventRepository.save(ev);

        return Map.of(
                "summary", practice.getOrDefault("summary", ""),
                "questions", practice.getOrDefault("questions", List.of()),
                "videoId", v.getId(),
                "segment", formatSeg(req.getSegmentStart(), req.getSegmentEnd())
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
