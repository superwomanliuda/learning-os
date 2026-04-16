package com.learningos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TextbookLessonRequest {

    /** 可选；不传则自动创建视频类学习会话 */
    private String sessionId;

    @NotBlank
    private String textbookId;

    /** 目标时长（分钟） */
    @Min(2)
    @Max(10)
    private Integer targetMinutes = 4;

    /** 学生想重点解决的问题（可选） */
    private String focusQuestion;
}
