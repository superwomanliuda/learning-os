package com.learningos.controller;

import com.learningos.service.AiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 文档 10.6：学生执意要答案时的拒答话术（服务端可直接返回，不依赖模型）
 */
@RestController
@RequestMapping("/api/ai")
public class AiGuardController {

    private final AiService aiService;

    public AiGuardController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/refuse-full-answer")
    public Map<String, String> refuse() {
        return Map.of("message", aiService.refuseFullAnswer());
    }
}
