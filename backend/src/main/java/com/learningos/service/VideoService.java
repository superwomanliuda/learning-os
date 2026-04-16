package com.learningos.service;

import com.learningos.dto.ManualTranscriptRequest;
import com.learningos.dto.VideoImportRequest;
import com.learningos.model.VideoAsset;
import com.learningos.repository.VideoAssetRepository;
import com.learningos.util.SecurityUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VideoService {

    private static final Pattern BV = Pattern.compile("(BV[a-zA-Z0-9]{10})");
    private static final Pattern HTTP_URL = Pattern.compile("(https?://\\S+)");
    private static final Pattern PAGE_PARAM = Pattern.compile("[?&]p=(\\d+)");

    private final VideoAssetRepository videoRepository;

    public VideoService(VideoAssetRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Transactional
    public VideoAsset importVideo(VideoImportRequest req) {
        Long studentId = SecurityUtils.currentUserId();
        String normalizedUrl = normalizeSourceUrl(req.getSourceUrl());
        String bv = extractBv(normalizedUrl);
        Integer page = extractPage(normalizedUrl);
        VideoAsset v = new VideoAsset();
        v.setId("video_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        v.setStudentId(studentId);
        v.setSourceType(req.getSourceType() != null ? req.getSourceType() : "bilibili");
        v.setSourceUrl(normalizedUrl);
        if (bv != null) {
            v.setTitle(page != null ? ("B站视频 " + bv + " P" + page) : ("B站视频 " + bv));
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

    @Transactional
    public VideoAsset uploadTextbook(MultipartFile file, String title) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先上传教材文件");
        }
        String filename = file.getOriginalFilename() == null ? "textbook.txt" : file.getOriginalFilename();
        String lower = filename.toLowerCase();
        if (!(lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".pdf"))) {
            throw new IllegalArgumentException("当前仅支持 .txt / .md / .pdf 教材上传");
        }
        try {
            String content = extractTextbookText(file, lower).trim();
            if (content.isBlank()) {
                throw new IllegalArgumentException("教材内容为空，请检查文件内容");
            }
            VideoAsset v = new VideoAsset();
            v.setId("video_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            v.setStudentId(SecurityUtils.currentUserId());
            v.setSourceType("textbook");
            v.setSourceUrl("upload://" + filename);
            v.setTitle((title == null || title.isBlank()) ? filename : title.trim());
            v.setCoverUrl(null);
            v.setDurationSec(0);
            v.setTranscript(content.length() > 20000 ? content.substring(0, 20000) : content);
            v.setOutline(null);
            v.setStatus(VideoAsset.VideoStatus.READY);
            return videoRepository.save(v);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("教材上传失败，请确认文件编码为 UTF-8");
        }
    }

    private String extractTextbookText(MultipartFile file, String lowerFilename) throws Exception {
        byte[] bytes = file.getBytes();
        if (lowerFilename.endsWith(".pdf")) {
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            } catch (Exception ex) {
                throw new IllegalArgumentException("PDF 解析失败，请确认文件未加密且内容可复制");
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String extractBv(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = BV.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private static String normalizeSourceUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String input = raw.trim();
        if (input.isEmpty()) {
            return "";
        }

        String bv = extractBv(input);
        if (bv != null) {
            Integer page = extractPage(input);
            if (page != null) {
                return "https://www.bilibili.com/video/" + bv + "/?p=" + page;
            }
            return "https://www.bilibili.com/video/" + bv + "/";
        }

        Matcher m = HTTP_URL.matcher(input);
        if (m.find()) {
            return m.group(1);
        }
        return input;
    }

    private static Integer extractPage(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = PAGE_PARAM.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
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
