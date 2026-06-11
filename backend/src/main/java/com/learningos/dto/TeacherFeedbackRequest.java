package com.learningos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeacherFeedbackRequest {
    private Long studentId;
    private String sessionId;
    @NotBlank
    private String targetType;  // pattern / insight / teaching_decision / artifact
    private Long targetId;
    @NotBlank
    private String feedbackType; // confirm / reject / modify / uncertain
    private String teacherText;
    /** 当 targetType=pattern 且 feedbackType=modify 时，新 pattern 类型 */
    private String correctedPatternType;
    /** 当 targetType=insight 且 feedbackType=modify 时，新 insight 文本 */
    private String correctedInsightText;
}
