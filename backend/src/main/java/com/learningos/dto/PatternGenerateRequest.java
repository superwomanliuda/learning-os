package com.learningos.dto;

import lombok.Data;

@Data
public class PatternGenerateRequest {
    /** 可选；不传时默认当前登录用户 */
    private Long studentId;
}
