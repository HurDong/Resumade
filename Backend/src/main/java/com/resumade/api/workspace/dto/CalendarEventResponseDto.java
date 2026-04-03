package com.resumade.api.workspace.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CalendarEventResponseDto {

    private Long id;
    private Long applicationId;

    /**
     * 이벤트 타입: "deadline" | "codingTest" | "aptitude" | "interview"
     * Application.status 값을 기준으로 서비스 계층에서 파생합니다.
     */
    private String type;

    private LocalDateTime date;
    private String company;
    private String position;

    /** ApplicationStatus.getId() 값 (document / aptitude / interview1 / interview2 / passed) */
    private String status;

    /** ApplicationResult.getId() 값 (pending / pass / fail) */
    private String result;

    private String logoUrl;
    private String memo;
}
