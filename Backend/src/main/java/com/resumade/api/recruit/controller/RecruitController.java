package com.resumade.api.recruit.controller;

import com.resumade.api.recruit.dto.RecruitDto;
import com.resumade.api.recruit.service.RecruitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/recruits")
@RequiredArgsConstructor
public class RecruitController {

    private final RecruitService recruitService;

    @PostMapping("/sync")
    public ResponseEntity<?> syncRecruits(@RequestBody List<RecruitDto.SyncRequest> requests) {
        recruitService.syncRecruits(requests);
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Synced " + requests.size() + " recruits successfully."
        ));
    }

    @GetMapping("/calendar")
    public ResponseEntity<?> getCalendar(
            @RequestParam int year,
            @RequestParam int month
    ) {
        List<RecruitDto.CalendarResponse> response = recruitService.getCalendar(year, month);
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "data", response
        ));
    }
}
