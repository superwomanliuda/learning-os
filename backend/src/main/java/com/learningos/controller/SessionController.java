package com.learningos.controller;

import com.learningos.dto.SessionCreateRequest;
import com.learningos.model.LearningSession;
import com.learningos.service.LearningSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final LearningSessionService sessionService;

    public SessionController(LearningSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public LearningSession create(@Valid @RequestBody SessionCreateRequest req) {
        return sessionService.create(req);
    }

    @GetMapping
    public List<LearningSession> list() {
        return sessionService.mySessions();
    }

    @PostMapping("/{id}/end")
    public LearningSession end(@PathVariable String id) {
        return sessionService.end(id);
    }

    @GetMapping("/{id}")
    public LearningSession get(@PathVariable String id) {
        return sessionService.getOwned(id);
    }
}
