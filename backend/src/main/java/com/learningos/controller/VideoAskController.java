package com.learningos.controller;

import com.learningos.dto.VideoAskRequest;
import com.learningos.dto.VideoPracticeRequest;
import com.learningos.dto.TextbookAskRequest;
import com.learningos.dto.TextbookLessonRequest;
import com.learningos.dto.TextbookTutorRequest;
import com.learningos.dto.TextToSpeechRequest;
import com.learningos.service.VideoAskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/textbook/ask")
    public Map<String, Object> textbookAsk(@Valid @RequestBody TextbookAskRequest req) {
        return videoAskService.textbookAsk(req, req.getSessionId());
    }

    @PostMapping("/textbook/lesson")
    public Map<String, Object> textbookLesson(@Valid @RequestBody TextbookLessonRequest req) {
        return videoAskService.textbookLesson(req, req.getSessionId());
    }

    @PostMapping("/textbook/tutor")
    public Map<String, Object> textbookTutor(@Valid @RequestBody TextbookTutorRequest req) {
        return videoAskService.textbookTutor(req, req.getSessionId());
    }

    @PostMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> tts(@Valid @RequestBody TextToSpeechRequest req) {
        byte[] audio = videoAskService.textToSpeech(req.getText());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.valueOf("audio/mpeg").toString())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(audio.length))
                .body(audio);
    }
}
