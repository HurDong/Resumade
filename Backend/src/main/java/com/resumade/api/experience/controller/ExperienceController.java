package com.resumade.api.experience.controller;

import com.resumade.api.experience.dto.ExperienceResponse;
import com.resumade.api.experience.service.ExperienceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/experiences")
@RequiredArgsConstructor
public class ExperienceController {

    private final ExperienceService experienceService;

    @PostMapping("/upload")
    public ResponseEntity<ExperienceResponse> uploadExperience(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "category", required = false, defaultValue = "개발") String category,
            @RequestParam(value = "period", required = false, defaultValue = "2024.01 - 2024.06") String period,
            @RequestParam(value = "role", required = false, defaultValue = "백엔드 개발자") String role
    ) {
        try {
            ExperienceResponse response = experienceService.uploadAndIndexExperience(file, title, category, period, role);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ExperienceResponse>> getAllExperiences() {
        List<ExperienceResponse> responses = experienceService.getAllExperiences();
        return ResponseEntity.ok(responses);
    }
}
