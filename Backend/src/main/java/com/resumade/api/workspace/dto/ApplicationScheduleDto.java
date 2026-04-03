package com.resumade.api.workspace.dto;

import com.resumade.api.workspace.domain.ApplicationSchedule;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApplicationScheduleDto {

    private Long id;
    private Long applicationId;
    private String companyName;
    private String position;

    /** ScheduleType 명칭 (DOCUMENT_DEADLINE, CODING_TEST, ..., CUSTOM) */
    private String type;

    /** UI에 표시할 레이블 (CUSTOM이면 customLabel, 나머지는 defaultLabel) */
    private String label;

    /** CalendarEventType으로 매핑된 문자열 (deadline / codingTest / aptitude / interview) */
    private String calendarEventType;

    private LocalDateTime scheduledAt;
    private String memo;
    private Integer sortOrder;

    public static ApplicationScheduleDto from(ApplicationSchedule s) {
        return ApplicationScheduleDto.builder()
                .id(s.getId())
                .applicationId(s.getApplication().getId())
                .companyName(s.getApplication().getCompanyName())
                .position(s.getApplication().getPosition())
                .type(s.getType().name())
                .label(s.resolveLabel())
                .calendarEventType(s.getType().getCalendarEventType())
                .scheduledAt(s.getScheduledAt())
                .memo(s.getMemo())
                .sortOrder(s.getSortOrder())
                .build();
    }
}
