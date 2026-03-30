package com.learningos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClassNoteRequest {
    @NotBlank
    private String text;
}
