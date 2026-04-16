package com.learningos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TextToSpeechRequest {
    @NotBlank
    private String text;
}
