package com.learningos.controller;

import com.learningos.dto.HomeworkHintRequest;
import com.learningos.dto.HomeworkHintResponse;
import com.learningos.model.HomeworkAttempt;
import com.learningos.service.HomeworkService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/homework")
public class HomeworkController {

    private final HomeworkService homeworkService;

    public HomeworkController(HomeworkService homeworkService) {
        this.homeworkService = homeworkService;
    }

    @PostMapping("/hint")
    public HomeworkHintResponse hint(@Valid @RequestBody HomeworkHintRequest req) {
        return homeworkService.hint(req);
    }

    @GetMapping("/attempts")
    public List<HomeworkAttempt> attempts() {
        return homeworkService.myAttempts();
    }
}
