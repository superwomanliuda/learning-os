package com.learningos.controller;

import com.learningos.dto.ClassNoteRequest;
import com.learningos.dto.ConfusionMarkRequest;
import com.learningos.model.LearningEvent;
import com.learningos.service.ClassReviewService;
import com.learningos.service.LearningEventService;
import com.learningos.service.LearningSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/class/sessions")
public class ClassController {

    private final LearningSessionService sessionService;
    private final LearningEventService eventService;
    private final ClassReviewService classReviewService;

    public ClassController(LearningSessionService sessionService,
                           LearningEventService eventService,
                           ClassReviewService classReviewService) {
        this.sessionService = sessionService;
        this.eventService = eventService;
        this.classReviewService = classReviewService;
    }

    @PostMapping("/{sessionId}/confusion")
    public LearningEvent confusion(@PathVariable String sessionId, @Valid @RequestBody ConfusionMarkRequest req) {
        sessionService.getOwned(sessionId);
        return eventService.classConfusionMark(sessionId, req);
    }

    @PostMapping("/{sessionId}/notes")
    public LearningEvent note(@PathVariable String sessionId, @Valid @RequestBody ClassNoteRequest req) {
        sessionService.getOwned(sessionId);
        return eventService.classNote(sessionId, req.getText());
    }

    @GetMapping("/{sessionId}/review")
    public Map<String, String> review(@PathVariable String sessionId) {
        return Map.of("summary", classReviewService.studentReview(sessionId));
    }
}
