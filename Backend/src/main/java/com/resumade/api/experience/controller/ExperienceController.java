package com.resumade.api.experience.controller;

import com.resumade.api.experience.dto.ExperienceResponse;
import com.resumade.api.experience.service.ExperienceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/experiences")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class ExperienceController {

    private final ExperienceService experienceService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadExperience(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "category", required = false, defaultValue = "Development") String category,
            @RequestParam(value = "period", required = false, defaultValue = "2024.01 - 2024.06") String period,
            @RequestParam(value = "role", required = false, defaultValue = "Backend Developer") String role
    ) {
        try {
            ExperienceResponse response = experienceService.uploadAndIndexExperience(file, title, category, period, role);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to upload experience file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body("Failed to upload experience file.");
        }
    }

    @GetMapping
    public ResponseEntity<List<ExperienceResponse>> getAllExperiences() {
        List<ExperienceResponse> responses = experienceService.getAllExperiences();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/reclassify-all")
    public ResponseEntity<Void> reclassifyAll() {
        experienceService.reclassifyAll();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reclassify")
    public ResponseEntity<ExperienceResponse> reclassifySingle(@PathVariable Long id) {
        ExperienceResponse response = experienceService.reclassifySingle(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExperience(@PathVariable Long id) {
        experienceService.deleteExperience(id);
        return ResponseEntity.ok().build();
    }
}