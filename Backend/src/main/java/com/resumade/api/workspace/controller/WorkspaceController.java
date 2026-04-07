package com.resumade.api.workspace.controller;

import com.resumade.api.infra.sse.Utf8SseSupport;
import com.resumade.api.workspace.dto.ApplyTitleSuggestionRequest;
import com.resumade.api.workspace.dto.BatchPlanRequest;
import com.resumade.api.workspace.dto.BatchPlanResponse;
import com.resumade.api.workspace.dto.TitleSuggestionResponse;
import com.resumade.api.workspace.dto.UpdateCategoryRequest;
import com.resumade.api.workspace.service.WorkspaceBatchPlanService;
import com.resumade.api.workspace.service.WorkspaceService;
import com.resumade.api.workspace.service.WorkspaceTaskCache;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceBatchPlanService workspaceBatchPlanService;
    private final WorkspaceTaskCache workspaceTaskCache;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @GetMapping(value = "/stream/{questionId}", produces = Utf8SseSupport.TEXT_EVENT_STREAM_UTF8_VALUE)
    public SseEmitter streamHumanPatch(
            @PathVariable Long questionId,
            @RequestParam(defaultValue = "true") boolean useDirective,
            @RequestParam(required = false) Integer targetChars,
            @RequestParam(required = false) java.util.List<Long> storyIds) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis());
        executorService.execute(() -> workspaceService.processHumanPatch(questionId, useDirective, targetChars, storyIds, emitter));
        return emitter;
    }

    @GetMapping(value = "/refine-stream/{questionId}", produces = Utf8SseSupport.TEXT_EVENT_STREAM_UTF8_VALUE)
    public SseEmitter streamRefinement(
            @PathVariable Long questionId,
            @RequestParam String directive,
            @RequestParam(required = false) Integer targetChars,
            @RequestParam(required = false) java.util.List<Long> storyIds) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis());
        executorService.execute(() -> workspaceService.processRefinement(questionId, directive, targetChars, storyIds, emitter));
        return emitter;
    }

    @GetMapping(value = "/rewash-stream/{questionId}", produces = Utf8SseSupport.TEXT_EVENT_STREAM_UTF8_VALUE)
    public SseEmitter streamRewash(@PathVariable Long questionId) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis());
        executorService.execute(() -> workspaceService.processRewash(questionId, emitter));
        return emitter;
    }

    @GetMapping(value = "/repatch-stream/{questionId}", produces = Utf8SseSupport.TEXT_EVENT_STREAM_UTF8_VALUE)
    public SseEmitter streamRepatch(@PathVariable Long questionId) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis());
        executorService.execute(() -> workspaceService.processRepatch(questionId, emitter));
        return emitter;
    }

    @GetMapping("/task-status/{questionId}")
    public ResponseEntity<java.util.Map<String, Object>> getTaskStatus(@PathVariable Long questionId) {
        return workspaceTaskCache.getStatus(questionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/title-suggestions/{questionId}")
    public TitleSuggestionResponse suggestTitles(@PathVariable Long questionId) {
        return workspaceService.suggestTitles(questionId);
    }

    @PostMapping("/batch-plan")
    public BatchPlanResponse createBatchPlan(@RequestBody BatchPlanRequest request) {
        return workspaceBatchPlanService.createPlan(request);
    }

    @PostMapping("/title/{questionId}")
    public com.resumade.api.workspace.domain.WorkspaceQuestion applyTitleSuggestion(
            @PathVariable Long questionId,
            @RequestBody ApplyTitleSuggestionRequest request) {
        return workspaceService.applyTitleSuggestion(questionId, request.getTitle());
    }

    @PatchMapping("/questions/{questionId}/category")
    public com.resumade.api.workspace.domain.WorkspaceQuestion updateCategory(
            @PathVariable Long questionId,
            @RequestBody UpdateCategoryRequest request) {
        return workspaceService.updateCategory(questionId, request.getCategory());
    }
}
