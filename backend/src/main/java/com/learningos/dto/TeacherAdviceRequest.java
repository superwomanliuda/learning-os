package com.learningos.dto;

import lombok.Data;
import java.util.List;

@Data
public class TeacherAdviceRequest {
    private String sessionId;
    private String currentQuestion;
    private String studentAnswer;
    private List<String> teacherTags;
    private int hintCount;
    private String notes;
}
