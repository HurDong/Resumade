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
    public ResponseEntity<List<PersonalStoryResponse>> getAllStories() {
        return ResponseEntity.ok(service.getAllStories());
    }

    @PostMapping
    public ResponseEntity<PersonalStoryResponse> createStory(@RequestBody PersonalStoryResponse.UpsertRequest request) {
        return ResponseEntity.ok(service.createStory(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PersonalStoryResponse> updateStory(
            @PathVariable Long id,
            @RequestBody PersonalStoryResponse.UpsertRequest request
    ) {
        return ResponseEntity.ok(service.updateStory(id, request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<PersonalStoryResponse>> bulkCreate(
            @RequestBody List<PersonalStoryResponse.UpsertRequest> requests
    ) {
        return ResponseEntity.ok(service.bulkCreate(requests));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStory(@PathVariable Long id) {
        service.deleteStory(id);
        return ResponseEntity.noContent().build();
    }
}
