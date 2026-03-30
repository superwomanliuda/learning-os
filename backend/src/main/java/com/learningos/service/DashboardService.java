package com.learningos.service;

import com.learningos.model.LearningSession;
import com.learningos.model.VideoAsset;
import com.learningos.repository.HomeworkAttemptRepository;
import com.learningos.repository.LearningEventRepository;
import com.learningos.repository.LearningSessionRepository;
import com.learningos.repository.VideoAssetRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final LearningSessionRepository sessionRepository;
    private final VideoAssetRepository videoRepository;
    private final HomeworkAttemptRepository homeworkRepository;
    private final LearningEventRepository eventRepository;

    public DashboardService(LearningSessionRepository sessionRepository,
                            VideoAssetRepository videoRepository,
                            HomeworkAttemptRepository homeworkRepository,
                            LearningEventRepository eventRepository) {
        this.sessionRepository = sessionRepository;
        this.videoRepository = videoRepository;
        this.homeworkRepository = homeworkRepository;
        this.eventRepository = eventRepository;
    }

    public Map<String, Object> today() {
        Long sid = SecurityUtils.currentUserId();
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        List<LearningSession> sessions = sessionRepository.findByStudentIdOrderByStartedAtDesc(sid).stream()
                .filter(s -> s.getStartedAt() != null && !s.getStartedAt().isBefore(start) && s.getStartedAt().isBefore(end))
                .collect(Collectors.toList());

        List<VideoAsset> videos = videoRepository.findByStudentIdOrderByCreatedAtDesc(sid).stream().limit(5).toList();
        List<Map<String, Object>> ongoingHw = homeworkRepository.findByStudentIdOrderByCreatedAtDesc(sid).stream()
                .filter(a -> a.getResult() == com.learningos.model.HomeworkAttempt.AttemptResult.PENDING)
                .limit(5)
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "questionExcerpt", truncate(a.getQuestionText(), 100),
                        "hintCount", a.getHintCount()))
                .collect(Collectors.toList());

        List<String> confusion = eventRepository.findByStudentIdAndEventTypeOrderByCreatedAtDesc(sid, "class_confusion_mark")
                .stream().limit(5)
                .map(e -> e.getPayload() != null ? e.getPayload() : e.getEventType())
                .collect(Collectors.toList());

        Long independent = homeworkRepository.countIndependentCorrectByStudentId(sid,
                com.learningos.model.HomeworkAttempt.AttemptResult.CORRECT);
        Double avgHints = homeworkRepository.avgHintCountByStudentId(sid);

        Map<String, Object> out = new HashMap<>();
        out.put("todaySessions", sessions);
        out.put("recentVideos", videos);
        out.put("ongoingHomework", ongoingHw);
        out.put("confusionMarks", confusion);
        out.put("metrics", Map.of(
                "independentCorrectCount", independent != null ? independent : 0L,
                "avgHintCount", avgHints != null ? avgHints : 0.0
        ));
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
