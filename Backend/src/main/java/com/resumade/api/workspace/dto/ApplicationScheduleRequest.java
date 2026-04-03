package com.resumade.api.workspace.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApplicationScheduleRequest {

    /** ScheduleType 명칭 (DOCUMENT_DEADLINE / CODING_TEST / APTITUDE / INTERVIEW1 / INTERVIEW2 / CUSTOM) */
    private String type;

    /** ScheduleType.CUSTOM일 때만 사용 (예: "커피챗", "과제전형") */
    private String customLabel;

    private LocalDateTime scheduledAt;

    private String memo;

    private Integer sortOrder;
}
