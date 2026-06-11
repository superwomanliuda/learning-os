package com.learningos.controller;

import com.learningos.dto.TeacherFeedbackRequest;
import com.learningos.model.TeacherFeedback;
import com.learningos.service.TeacherReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teacher-review")
public class TeacherReviewController {

    private final TeacherReviewService reviewService;

    public TeacherReviewController(TeacherReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public TeacherFeedback submit(@Valid @RequestBody TeacherFeedbackRequest req) {
        return reviewService.submit(req);
    }

    @GetMapping("/student/{studentId}")
    public List<TeacherFeedback> listByStudent(@PathVariable Long studentId) {
        return reviewService.listByStudent(studentId);
    }
}
