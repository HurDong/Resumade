package com.resumade.api.workspace.controller;

import com.resumade.api.workspace.dto.ApplicationScheduleDto;
import com.resumade.api.workspace.dto.ApplicationScheduleRequest;
import com.resumade.api.workspace.service.ApplicationScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/schedules")
@RequiredArgsConstructor
public class ApplicationScheduleController {

    private final ApplicationScheduleService scheduleService;

    /** GET /api/v1/applications/{applicationId}/schedules */
    @GetMapping
    public ResponseEntity<List<ApplicationScheduleDto>> getSchedules(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(scheduleService.getSchedules(applicationId));
    }

    /** POST /api/v1/applications/{applicationId}/schedules */
    @PostMapping
    public ResponseEntity<ApplicationScheduleDto> createSchedule(
            @PathVariable Long applicationId,
            @RequestBody ApplicationScheduleRequest request
    ) {
        return ResponseEntity.ok(scheduleService.createSchedule(applicationId, request));
    }

    /** PATCH /api/v1/applications/{applicationId}/schedules/{scheduleId} */
    @PatchMapping("/{scheduleId}")
    public ResponseEntity<ApplicationScheduleDto> updateSchedule(
            @PathVariable Long applicationId,
            @PathVariable Long scheduleId,
            @RequestBody ApplicationScheduleRequest request
    ) {
        return ResponseEntity.ok(scheduleService.updateSchedule(scheduleId, request));
    }

    /** DELETE /api/v1/applications/{applicationId}/schedules/{scheduleId} */
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long applicationId,
            @PathVariable Long scheduleId
    ) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.noContent().build();
    }
}
