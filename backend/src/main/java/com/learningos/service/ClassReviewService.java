package com.learningos.service;

import com.learningos.model.LearningEvent;
import com.learningos.repository.LearningEventRepository;
import com.learningos.repository.LearningSessionRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClassReviewService {

    private final LearningSessionRepository sessionRepository;
    private final LearningEventRepository eventRepository;
    private final AiService aiService;

    public ClassReviewService(LearningSessionRepository sessionRepository,
                              LearningEventRepository eventRepository,
                              AiService aiService) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.aiService = aiService;
    }

    public String studentReview(String sessionId) {
        var session = sessionRepository.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getStudentId().equals(SecurityUtils.currentUserId())) {
            throw new IllegalArgumentException("无权复盘该会话");
        }
        List<LearningEvent> marks = eventRepository.findBySessionIdAndEventType(sessionId, "class_confusion_mark");
        String confusion = marks.stream()
                .map(m -> "t=" + m.getTimestampSec() + " " + (m.getPayload() != null ? m.getPayload() : ""))
                .collect(Collectors.joining("\n"));
        if (confusion.isBlank()) {
            confusion = "（暂无「不懂」标记）";
        }
        List<LearningEvent> notes = eventRepository.findBySessionIdAndEventType(sessionId, "class_note");
        String noteText = notes.stream().map(LearningEvent::getPayload).collect(Collectors.joining("\n"));
        return aiService.classReview("（录音转写待接入）", confusion, noteText.isBlank() ? null : noteText);
    }
}
