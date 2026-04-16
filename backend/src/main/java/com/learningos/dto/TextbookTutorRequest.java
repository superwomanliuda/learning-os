package com.learningos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TextbookTutorRequest {
    /** 可选；不传则自动创建视频类学习会话 */
    private String sessionId;

    @NotBlank
    private String textbookId;

    /** 最近几轮对话（可选） */
    private List<TutorMessage> messages = new ArrayList<>();

    @Data
    public static class TutorMessage {
        private String role; // user / assistant
        private String content;
    }
}
