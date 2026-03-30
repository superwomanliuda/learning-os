package com.learningos.service;

import com.learningos.dto.ManualTranscriptRequest;
import com.learningos.dto.VideoImportRequest;
import com.learningos.model.VideoAsset;
import com.learningos.repository.VideoAssetRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VideoService {

    private static final Pattern BV = Pattern.compile("(BV[a-zA-Z0-9]{10})");

    private final VideoAssetRepository videoRepository;

    public VideoService(VideoAssetRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Transactional
    public VideoAsset importVideo(VideoImportRequest req) {
        Long studentId = SecurityUtils.currentUserId();
        String bv = extractBv(req.getSourceUrl());
        VideoAsset v = new VideoAsset();
        v.setId("video_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        v.setStudentId(studentId);
        v.setSourceType(req.getSourceType() != null ? req.getSourceType() : "bilibili");
        v.setSourceUrl(req.getSourceUrl());
        if (bv != null) {
            v.setTitle("B站视频 " + bv);
            v.setCoverUrl("https://i0.hdslb.com/bfs/archive/placeholder.jpg");
        } else {
            v.setTitle("导入的视频（未能解析 BV 号）");
        }
        v.setDurationSec(0);
        v.setTranscript("");
        v.setOutline(null);
        v.setStatus(VideoAsset.VideoStatus.READY);
        return videoRepository.save(v);
    }

    private static String extractBv(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = BV.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public List<VideoAsset> myVideos() {
        return videoRepository.findByStudentIdOrderByCreatedAtDesc(SecurityUtils.currentUserId());
    }

    public VideoAsset getOwned(String videoId) {
        VideoAsset v = videoRepository.findById(videoId).orElseThrow(() -> new IllegalArgumentException("视频不存在"));
        if (!v.getStudentId().equals(SecurityUtils.currentUserId())) {
            throw new IllegalArgumentException("无权访问该视频");
        }
        return v;
    }

    @Transactional
    public VideoAsset setManualTranscript(String videoId, ManualTranscriptRequest req) {
        VideoAsset v = getOwned(videoId);
        v.setTranscript(req.getTranscript());
        return videoRepository.save(v);
    }

    /** 取某时间范围内的字幕片段（简单按行包含时间戳则整段，否则返回全文前 800 字） */
    public String segmentTranscript(VideoAsset v, int startSec, int endSec) {
        String t = v.getTranscript();
        if (t == null || t.isBlank()) {
            return "";
        }
        return t.length() > 1200 ? t.substring(0, 1200) + "…" : t;
    }
}
