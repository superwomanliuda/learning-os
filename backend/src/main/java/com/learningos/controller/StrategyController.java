package com.learningos.controller;

import com.learningos.dto.StrategyGenerateRequest;
import com.learningos.model.Strategy;
import com.learningos.service.StrategyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @PostMapping("/generate")
    public List<Strategy> generate(@RequestBody(required = false) StrategyGenerateRequest req) {
        Long studentId = req == null ? null : req.getStudentId();
        Long patternId = req == null ? null : req.getPatternId();
        return strategyService.generateStrategy(studentId, patternId);
    }

    @GetMapping("/student/{studentId}")
    public List<Strategy> list(@PathVariable Long studentId,
                               @RequestParam(defaultValue = "true") Boolean activeOnly) {
        return strategyService.listByStudent(studentId, activeOnly);
    }
}
