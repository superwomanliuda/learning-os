package com.learningos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VideoPracticeRequest {

    /** 可选；不传则自动创建视频类学习会话 */
    private String sessionId;

    @NotBlank
    private String videoId;

    @NotNull
    private Integer segmentStart;

    @NotNull
    private Integer segmentEnd;

    /** 题目难度：基础 / 进阶 */
    private String level = "基础";

    @Min(1)
    private Integer questionCount = 3;
}
