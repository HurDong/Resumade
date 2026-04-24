package com.resumade.api.experience.controller;

import com.resumade.api.experience.dto.PersonalStoryResponse;
import com.resumade.api.experience.service.PersonalStoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * {@link com.resumade.api.experience.domain.PersonalStory} CRUD를 위한 REST 컨트롤러.
 */
@RestController
@RequestMapping("/api/personal-stories")
@RequiredArgsConstructor
public class PersonalStoryController {

    private final PersonalStoryService service;

    @GetMapping
    public ResponseEntity<PersonalStoryResponse> getLifeStory() {
        return ResponseEntity.ok(service.getLifeStory());
    }

    @PostMapping
    public ResponseEntity<PersonalStoryResponse> saveLifeStory(@RequestBody PersonalStoryResponse.UpsertRequest request) {
        return ResponseEntity.ok(service.saveLifeStory(request));
    }

    @PutMapping
    public ResponseEntity<PersonalStoryResponse> updateLifeStory(@RequestBody PersonalStoryResponse.UpsertRequest request) {
        return ResponseEntity.ok(service.saveLifeStory(request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<PersonalStoryResponse> importLifeStory(
            @RequestBody List<PersonalStoryResponse.UpsertRequest> requests
    ) {
        return ResponseEntity.ok(service.importLifeStory(requests));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearLifeStory() {
        service.clearLifeStory();
        return ResponseEntity.noContent().build();
    }
}
