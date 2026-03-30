package com.learningos.dto;

import com.learningos.model.LearningSession;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SessionCreateRequest {
    @NotNull
    private LearningSession.SessionType type;
    private String title;
}
