package com.resumade.api.workspace.controller;

import com.resumade.api.workspace.dto.SnapshotDto;
import com.resumade.api.workspace.service.QuestionSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspace")
@RequiredArgsConstructor
public class VersionHistoryController {

    private final QuestionSnapshotService questionSnapshotService;

    /**
     * GET /api/v1/workspace/questions/{questionId}/history
     * 최신순 최대 20개 스냅샷 반환
     */
    @GetMapping("/questions/{questionId}/history")
    public List<SnapshotDto> getHistory(@PathVariable Long questionId) {
        return questionSnapshotService.getHistory(questionId)
                .stream()
                .map(SnapshotDto::from)
                .toList();
    }
}
