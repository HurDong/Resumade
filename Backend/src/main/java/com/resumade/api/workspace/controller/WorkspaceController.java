package com.resumade.api.workspace.controller;

import com.resumade.api.workspace.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @GetMapping("/stream/{questionId}")
    public SseEmitter streamHumanPatch(
            @PathVariable Long questionId,
            @RequestParam(defaultValue = "true") boolean useDirective,
            @RequestParam(required = false) Integer targetChars) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis());
        executorService.execute(() -> workspaceService.processHumanPatch(questionId, useDirective, targetChars, emitter));
        return emitter;
    }

    @GetMapping("/refine-stream/{questionId}")
    public SseEmitter streamRefinement(
            @PathVariable Long questionId,
            @RequestParam String directive,
            @RequestParam(required = false) Integer targetChars) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis());
        executorService.execute(() -> workspaceService.processRefinement(questionId, directive, targetChars, emitter));
        return emitter;
    }

    @GetMapping("/rewash-stream/{questionId}")
    public SseEmitter streamRewash(@PathVariable Long questionId) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis());
        executorService.execute(() -> workspaceService.processRewash(questionId, emitter));
        return emitter;
    }

    @GetMapping("/repatch-stream/{questionId}")
    public SseEmitter streamRepatch(@PathVariable Long questionId) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis());
        executorService.execute(() -> workspaceService.processRepatch(questionId, emitter));
        return emitter;
    }
}
