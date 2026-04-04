package com.resumade.api.workspace.service;

import com.resumade.api.workspace.domain.ApplicationSchedule;
import com.resumade.api.workspace.domain.ApplicationScheduleRepository;
import com.resumade.api.workspace.dto.CalendarEventResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventService {

    private final ApplicationScheduleRepository scheduleRepository;

    /**
     * 특정 년/월에 예정된 ApplicationSchedule 목록을 CalendarEvent로 변환합니다.
     * Application.deadline 대신 ApplicationSchedule을 단일 진실의 출처(Single Source of Truth)로 사용합니다.
     */
    public List<CalendarEventResponseDto> getCalendarEvents(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);

        return scheduleRepository.findByScheduledAtBetween(start, end)
                .stream()
                .map(this::toCalendarEvent)
                .collect(Collectors.toList());
    }

    private CalendarEventResponseDto toCalendarEvent(ApplicationSchedule s) {
        var app = s.getApplication();
        return CalendarEventResponseDto.builder()
                .id(s.getId())
                .applicationId(app.getId())
                .type(s.getType().getCalendarEventType())
                .date(s.getScheduledAt())
                .company(app.getCompanyName())
                .position(app.getPosition())
                .status(app.getStatus().getId())
                .result(app.getResult().getId())
                .logoUrl(app.getLogoUrl())
                .memo(s.getMemo())
                .build();
    }
}
