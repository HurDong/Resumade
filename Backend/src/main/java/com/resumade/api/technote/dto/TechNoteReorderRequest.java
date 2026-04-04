package com.resumade.api.technote.dto;

import java.util.List;

public record TechNoteReorderRequest(
        List<Long> noteIds
) {}
