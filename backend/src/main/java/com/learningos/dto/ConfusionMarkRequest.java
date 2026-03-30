package com.learningos.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfusionMarkRequest {
    @NotNull
    private Integer timestampSec;
    private String context;
}
