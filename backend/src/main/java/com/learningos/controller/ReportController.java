package com.learningos.controller;

import com.learningos.service.ReportService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/session/{sessionId}")
    public Map<String, String> session(@PathVariable String sessionId) {
        return Map.of("summary", reportService.sessionReport(sessionId));
    }
}
