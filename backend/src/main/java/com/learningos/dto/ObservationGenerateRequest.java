package com.learningos.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ObservationGenerateRequest {
    private Long studentId;
    /** system | teacher */
    private String type;
    /** step_error | hesitation | behavior */
    private String category;
    private String content;
    private Map<String, Object> evidence;
}
