package com.learningos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VideoImportRequest {
    private String sourceType = "bilibili";

    @NotBlank
    private String sourceUrl;
}
