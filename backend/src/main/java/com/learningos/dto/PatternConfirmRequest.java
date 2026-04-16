package com.learningos.dto;

import lombok.Data;

@Data
public class PatternConfirmRequest {
    private Long patternId;
    /** true=确认；false=忽略 */
    private Boolean confirmed;
}
