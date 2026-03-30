package com.learningos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class HomeworkHintRequest {
    /** 已有作业尝试时传入，否则服务端新建 */
    private Long homeworkAttemptId;

    private String sessionId;

    @NotBlank
    private String questionText;

    /** 允许先空，服务端按「尚未写出步骤」处理 */
    private String studentAnswer;

    /** 可选：历史错误类型 */
    private List<String> errorTypes;

    private String context;
}
