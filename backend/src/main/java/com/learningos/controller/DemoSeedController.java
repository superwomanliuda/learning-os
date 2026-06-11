package com.learningos.controller;

import com.learningos.service.DemoSeedService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class DemoSeedController {

    private final DemoSeedService demoSeedService;

    public DemoSeedController(DemoSeedService demoSeedService) {
        this.demoSeedService = demoSeedService;
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return demoSeedService.seed();
    }

    @PostMapping("/seed-numberline")
    public Map<String, Object> seedNumberLine() {
        return demoSeedService.seedNumberLineCase();
    }
}

