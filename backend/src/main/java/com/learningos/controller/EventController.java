package com.learningos.controller;

import com.learningos.dto.EventRequest;
import com.learningos.model.LearningEvent;
import com.learningos.service.LearningEventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final LearningEventService eventService;

    public EventController(LearningEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public LearningEvent report(@Valid @RequestBody EventRequest req) {
        return eventService.report(req);
    }

    @GetMapping("/session/{sessionId}")
    public List<LearningEvent> bySession(@PathVariable String sessionId) {
        return eventService.bySession(sessionId);
    }
}
