package com.resumade.api.workspace.controller;

import com.resumade.api.infra.sse.Utf8SseSupport;
import com.resumade.api.workspace.dto.DraftWashRequest;
import com.resumade.api.workspace.service.DraftWashService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/draft-wash")
@RequiredArgsConstructor
public class DraftWashController {

    private final DraftWashService draftWashService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @PostMapping(value = "/stream", produces = Utf8SseSupport.TEXT_EVENT_STREAM_UTF8_VALUE)
    public SseEmitter streamWash(@RequestBody DraftWashRequest request) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        executorService.execute(() -> draftWashService.process(request, emitter));
        return emitter;
    }
}
