package com.resumade.api.technote.controller;

import com.resumade.api.technote.dto.TechNoteRequest;
import com.resumade.api.technote.dto.TechNoteResponse;
import com.resumade.api.technote.service.TechNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tech-notes")
@RequiredArgsConstructor
public class TechNoteController {

    private final TechNoteService techNoteService;

    @GetMapping
    public ResponseEntity<List<TechNoteResponse>> getAll() {
        return ResponseEntity.ok(techNoteService.findAll());
    }

    @PostMapping
    public ResponseEntity<TechNoteResponse> create(@RequestBody TechNoteRequest request) {
        return ResponseEntity.ok(techNoteService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TechNoteResponse> update(
            @PathVariable Long id,
            @RequestBody TechNoteRequest request) {
        return ResponseEntity.ok(techNoteService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        techNoteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
