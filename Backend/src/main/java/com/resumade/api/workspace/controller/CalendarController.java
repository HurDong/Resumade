package com.resumade.api.workspace.controller;

import com.resumade.api.workspace.dto.CalendarEventResponseDto;
import com.resumade.api.workspace.service.CalendarEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarEventService calendarEventService;

    /**
     * GET /api/v1/calendar/events?year=2026&month=4
     * 해당 월에 마감일(deadline)이 설정된 지원서를 캘린더 이벤트로 반환합니다.
     */
    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventResponseDto>> getCalendarEvents(
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(calendarEventService.getCalendarEvents(year, month));
    }
}
