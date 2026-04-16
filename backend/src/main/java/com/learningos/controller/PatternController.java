package com.learningos.controller;

import com.learningos.dto.PatternConfirmRequest;
import com.learningos.dto.PatternGenerateRequest;
import com.learningos.model.Pattern;
import com.learningos.service.PatternService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pattern")
public class PatternController {

    private final PatternService patternService;

    public PatternController(PatternService patternService) {
        this.patternService = patternService;
    }

    @PostMapping("/generate")
    public List<Pattern> generate(@RequestBody(required = false) PatternGenerateRequest req) {
        Long studentId = req == null ? null : req.getStudentId();
        return patternService.generatePattern(studentId);
    }

    @PostMapping("/confirm")
    public Pattern confirm(@RequestBody PatternConfirmRequest req) {
        if (req == null || req.getPatternId() == null) {
            throw new IllegalArgumentException("patternId 不能为空");
        }
        return patternService.confirmPattern(req.getPatternId(), req.getConfirmed());
    }

    @GetMapping("/student/{studentId}")
    public List<Pattern> list(@PathVariable Long studentId,
                              @RequestParam(required = false) Boolean confirmed) {
        return patternService.listByStudent(studentId, confirmed);
    }
}
