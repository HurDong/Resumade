package com.resumade.api.coding.controller;

import com.resumade.api.coding.dto.CodingProblemRequest;
import com.resumade.api.coding.dto.CodingProblemResponse;
import com.resumade.api.coding.service.CodingArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coding-problems")
@RequiredArgsConstructor
public class CodingArchiveController {

    private final CodingArchiveService codingArchiveService;

    @GetMapping
    public ResponseEntity<List<CodingProblemResponse>> getAll() {
        return ResponseEntity.ok(codingArchiveService.findAll());
    }

    @PostMapping
    public ResponseEntity<CodingProblemResponse> create(@RequestBody CodingProblemRequest request) {
        return ResponseEntity.ok(codingArchiveService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CodingProblemResponse> update(
            @PathVariable Long id,
            @RequestBody CodingProblemRequest request) {
        return ResponseEntity.ok(codingArchiveService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        codingArchiveService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
