package com.resumade.api.workspace.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_snapshots",
        indexes = @Index(name = "idx_snapshot_question_created",
                columnList = "question_id, created_at DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class QuestionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SnapshotType snapshotType;

    @Lob
    @Column(length = 16777215)
    private String content;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public QuestionSnapshot(Long questionId, SnapshotType snapshotType, String content) {
        this.questionId = questionId;
        this.snapshotType = snapshotType;
        this.content = content;
    }
}
