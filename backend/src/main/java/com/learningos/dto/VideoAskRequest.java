package com.learningos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VideoAskRequest {

    /** 可选；不传则自动创建视频类学习会话 */
    private String sessionId;

    @NotBlank
    private String videoId;

    @NotNull
    private Integer segmentStart;

    @NotNull
    private Integer segmentEnd;

    @NotBlank
    private String question;
}
