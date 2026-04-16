package com.learningos.dto;

import lombok.Data;

@Data
public class TeacherTagRequest {
    /** UNDERSTOOD | NOT_UNDERSTOOD | BARELY_UNDERSTOOD | FAKE_UNDERSTANDING |
     *  PROCESS_BREAK | CONCEPT_ISSUE | CALCULATION_ISSUE | HINT_DEPENDENCY */
    private String tag;
    private String note;
}
