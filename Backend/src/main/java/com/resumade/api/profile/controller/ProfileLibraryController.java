package com.resumade.api.profile.controller;

import com.resumade.api.profile.dto.ProfileEntryResponse;
import com.resumade.api.profile.dto.ProfileEntryUpsertRequest;
import com.resumade.api.profile.dto.ReorderRequest;
import com.resumade.api.profile.service.ProfileLibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profile-library")
@RequiredArgsConstructor
public class ProfileLibraryController {

    private final ProfileLibraryService profileLibraryService;

    @GetMapping
    public ResponseEntity<List<ProfileEntryResponse>> getEntries() {
        return ResponseEntity.ok(profileLibraryService.getAllEntries());
    }

    @PostMapping
    public ResponseEntity<ProfileEntryResponse> createEntry(
            @RequestBody ProfileEntryUpsertRequest request
    ) {
        return ResponseEntity.ok(profileLibraryService.createEntry(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfileEntryResponse> updateEntry(
            @PathVariable String id,
            @RequestBody ProfileEntryUpsertRequest request
    ) {
        return ResponseEntity.ok(profileLibraryService.updateEntry(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(@PathVariable String id) {
        profileLibraryService.deleteEntry(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorderEntries(@RequestBody ReorderRequest request) {
        profileLibraryService.reorderEntries(request);
        return ResponseEntity.noContent().build();
    }
}
