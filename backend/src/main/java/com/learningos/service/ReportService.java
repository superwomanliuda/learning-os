package com.learningos.service;

import com.learningos.model.HomeworkAttempt;
import com.learningos.model.LearningEvent;
import com.learningos.model.LearningSession;
import com.learningos.repository.HomeworkAttemptRepository;
import com.learningos.repository.LearningEventRepository;
import com.learningos.repository.LearningSessionRepository;
import com.learningos.repository.VideoAssetRepository;
import com.learningos.security.AuthPrincipal;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final LearningSessionRepository sessionRepository;
    private final LearningEventRepository eventRepository;
    private final HomeworkAttemptRepository homeworkAttemptRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final AiService aiService;

    public ReportService(LearningSessionRepository sessionRepository,
                         LearningEventRepository eventRepository,
                         HomeworkAttemptRepository homeworkAttemptRepository,
                         VideoAssetRepository videoAssetRepository,
                         AiService aiService) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.homeworkAttemptRepository = homeworkAttemptRepository;
        this.videoAssetRepository = videoAssetRepository;
        this.aiService = aiService;
    }

    public String sessionReport(String sessionId) {
        LearningSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        AuthPrincipal p = SecurityUtils.currentPrincipal();
        boolean teacher = "TEACHER".equals(p.getRole()) || "PARENT".equals(p.getRole());
        if (!teacher && !session.getStudentId().equals(p.getUserId())) {
            throw new IllegalArgumentException("无权查看该会话报告");
        }

        List<LearningEvent> events = eventRepository.findBySessionIdOrderByTimestampSec(sessionId);
        String eventsSummary = summarizeEvents(events);
        List<HomeworkAttempt> hw = homeworkAttemptRepository.findBySessionId(sessionId);
        String hwSummary = hw.stream()
                .map(a -> "题目片段:" + truncate(a.getQuestionText(), 80)
                        + " 提示次数:" + a.getHintCount() + " 结果:" + a.getResult())
                .collect(Collectors.joining("\n"));
        if (hwSummary.isBlank()) {
            hwSummary = "本会话暂无关联作业记录";
        }
        String videoSummary = videoAssetRepository.findByStudentIdOrderByCreatedAtDesc(session.getStudentId()).stream()
                .limit(3)
                .map(v -> v.getTitle() + " 状态:" + v.getStatus())
                .collect(Collectors.joining("\n"));
        if (videoSummary.isBlank()) {
            videoSummary = "近期无导入视频";
        }

        return aiService.teacherSessionReport(eventsSummary, hwSummary, videoSummary);
    }

    private static String summarizeEvents(List<LearningEvent> events) {
        if (events.isEmpty()) {
            return "无事件";
        }
        return events.stream()
                .map(e -> e.getEventType() + " t=" + e.getTimestampSec() + " " + truncate(e.getPayload(), 120))
                .collect(Collectors.joining("\n"));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
