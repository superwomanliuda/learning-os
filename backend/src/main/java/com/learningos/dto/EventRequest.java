package com.learningos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EventRequest {
    @NotBlank
    private String sessionId;
    @NotBlank
    private String eventType;
    private Integer timestampSec;
    private Object payload;
}
