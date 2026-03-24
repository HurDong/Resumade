package com.resumade.api.infra.sse;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

public final class Utf8SseSupport {

    public static final String TEXT_EVENT_STREAM_UTF8_VALUE = "text/event-stream;charset=UTF-8";
    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private Utf8SseSupport() {
    }

    public static SseEmitter.SseEventBuilder textEvent(String name, String data) {
        return SseEmitter.event()
                .name(name)
                .data(data, TEXT_PLAIN_UTF8);
    }

    public static SseEmitter.SseEventBuilder jsonEvent(String name, Object data) {
        return SseEmitter.event()
                .name(name)
                .data(data, MediaType.APPLICATION_JSON);
    }
}
