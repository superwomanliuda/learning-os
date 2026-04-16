package com.learningos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TextbookAskRequest {

    /** 可选；不传则自动创建视频类学习会话 */
    private String sessionId;

    @NotBlank
    private String textbookId;

    @NotBlank
    private String question;
}
