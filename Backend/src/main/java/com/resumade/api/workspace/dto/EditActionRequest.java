package com.resumade.api.workspace.dto;

public record EditActionRequest(
        String selectedText,
        String actionKey,
        String customPrompt   // null for standard actions, required for "custom"
) {}
