package com.learningos.controller;

import com.learningos.dto.ManualTranscriptRequest;
import com.learningos.dto.VideoImportRequest;
import com.learningos.model.VideoAsset;
import com.learningos.service.VideoService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/import")
    public VideoAsset importVideo(@Valid @RequestBody VideoImportRequest req) {
        return videoService.importVideo(req);
    }

    @PostMapping(value = "/textbook/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoAsset uploadTextbook(@RequestPart("file") MultipartFile file,
                                     @RequestPart(value = "title", required = false) String title) {
        return videoService.uploadTextbook(file, title);
    }

    @GetMapping
    public List<VideoAsset> list() {
        return videoService.myVideos();
    }

    @PatchMapping("/{id}/transcript")
    public VideoAsset transcript(@PathVariable String id, @Valid @RequestBody ManualTranscriptRequest req) {
        return videoService.setManualTranscript(id, req);
    }
}
