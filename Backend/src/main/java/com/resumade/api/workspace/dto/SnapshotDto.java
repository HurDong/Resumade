package com.resumade.api.workspace.dto;

import com.resumade.api.workspace.domain.QuestionSnapshot;
import com.resumade.api.workspace.domain.SnapshotType;

import java.time.LocalDateTime;

public record SnapshotDto(
        Long id,
        SnapshotType snapshotType,
        String content,
        LocalDateTime createdAt
) {
    public static SnapshotDto from(QuestionSnapshot snapshot) {
        return new SnapshotDto(
                snapshot.getId(),
                snapshot.getSnapshotType(),
                snapshot.getContent(),
                snapshot.getCreatedAt()
        );
    }
}
