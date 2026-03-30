package com.learningos.controller;

import com.learningos.dto.VideoAskRequest;
import com.learningos.dto.VideoPracticeRequest;
import com.learningos.service.VideoAskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/video")
public class VideoAskController {

    private final VideoAskService videoAskService;

    public VideoAskController(VideoAskService videoAskService) {
        this.videoAskService = videoAskService;
    }

    @PostMapping("/ask")
    public Map<String, Object> ask(@Valid @RequestBody VideoAskRequest req) {
        return videoAskService.ask(req, req.getSessionId());
    }

    @PostMapping("/practice")
    public Map<String, Object> practice(@Valid @RequestBody VideoPracticeRequest req) {
        return videoAskService.practice(req, req.getSessionId());
    }
}
