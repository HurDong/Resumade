package com.resumade.api.workspace.service;

import com.resumade.api.workspace.domain.Application;
import com.resumade.api.workspace.domain.ApplicationRepository;
import com.resumade.api.workspace.domain.ApplicationStatus;
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

    private final ApplicationRepository applicationRepository;

    /**
     * 특정 년/월에 마감일(deadline)이 존재하는 Application 목록을 CalendarEvent로 변환합니다.
     */
    public List<CalendarEventResponseDto> getCalendarEvents(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);

        return applicationRepository.findByDeadlineBetween(start, end)
                .stream()
                .map(this::toCalendarEvent)
                .collect(Collectors.toList());
    }

    private CalendarEventResponseDto toCalendarEvent(Application app) {
        return CalendarEventResponseDto.builder()
                .id(app.getId())
                .applicationId(app.getId())
                .type(resolveEventType(app.getStatus()))
                .date(app.getDeadline())
                .company(app.getCompanyName())
                .position(app.getPosition())
                .status(app.getStatus().getId())
                .result(app.getResult().getId())
                .logoUrl(app.getLogoUrl())
                .memo(null)
                .build();
    }

    /**
     * Application의 파이프라인 단계를 프론트엔드 캘린더 이벤트 타입으로 변환합니다.
     * <ul>
     *   <li>DOCUMENT  → "deadline"   (서류 마감)</li>
     *   <li>APTITUDE  → "codingTest" (코딩테스트/인적성)</li>
     *   <li>INTERVIEW1 / INTERVIEW2 / PASSED → "interview" (면접)</li>
     * </ul>
     */
    private String resolveEventType(ApplicationStatus status) {
        return switch (status) {
            case DOCUMENT -> "deadline";
            case APTITUDE -> "codingTest";
            case INTERVIEW1, INTERVIEW2, PASSED -> "interview";
        };
    }
}
