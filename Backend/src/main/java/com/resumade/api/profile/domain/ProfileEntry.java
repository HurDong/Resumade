package com.resumade.api.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "profile_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProfileEntry {

    @Id
    @Column(nullable = false, updatable = false, length = 64)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProfileCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 200)
    private String organization;

    @Column(length = 120)
    private String dateLabel;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 120)
    private String referenceId;

    @Column(length = 120)
    private String highlight;

    @Column(nullable = false)
    private int sortOrder = 0;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public ProfileEntry(
            String id,
            ProfileCategory category,
            String title,
            String organization,
            String dateLabel,
            String summary,
            String referenceId,
            String highlight,
            int sortOrder
    ) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.organization = organization;
        this.dateLabel = dateLabel;
        this.summary = summary;
        this.referenceId = referenceId;
        this.highlight = highlight;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    public void ensureId() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }

    public void update(
            ProfileCategory category,
            String title,
            String organization,
            String dateLabel,
            String summary,
            String referenceId,
            String highlight
    ) {
        this.category = category;
        this.title = title;
        this.organization = organization;
        this.dateLabel = dateLabel;
        this.summary = summary;
        this.referenceId = referenceId;
        this.highlight = highlight;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
