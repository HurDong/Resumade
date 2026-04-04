package com.resumade.api.technote.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "tech_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class TechNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(length = 300)
    private String summary;

    @Column(columnDefinition = "json")
    private String conditions;

    @Column(columnDefinition = "TEXT")
    private String template;

    @Column(columnDefinition = "json")
    private String tags;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public TechNote(String title, String category, String content, Integer sortOrder,
                    String summary, String conditions, String template, String tags) {
        this.title = title;
        this.category = category;
        this.content = content;
        this.sortOrder = sortOrder;
        this.summary = summary;
        this.conditions = conditions;
        this.template = template;
        this.tags = tags;
    }

    public void updateWiki(String title, String category, String content) {
        this.title = title;
        this.category = category;
        this.content = content;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
