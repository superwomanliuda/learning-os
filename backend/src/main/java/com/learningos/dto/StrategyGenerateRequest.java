package com.learningos.dto;

import lombok.Data;

@Data
public class StrategyGenerateRequest {
    /** 可选；老师端建议显式传入 */
    private Long studentId;
    /** 可选；不传则对该学生所有已确认 Pattern 生成 */
    private Long patternId;
}
