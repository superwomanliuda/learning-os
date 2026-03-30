package com.learningos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HomeworkHintResponse {
    private Long homeworkAttemptId;
    /** 本轮实际使用的提示等级 1-4 */
    private int hintLevel;
    /** 累计已使用提示次数 */
    private int hintsUsedTotal;
    private String reply;
}
