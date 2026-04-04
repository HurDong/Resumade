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
    public TechNote(String title, String category, String summary,
                    String conditions, String template, String tags) {
        this.title = title;
        this.category = category;
        this.summary = summary;
        this.conditions = conditions;
        this.template = template;
        this.tags = tags;
    }

    public void update(String title, String category, String summary,
                       String conditions, String template, String tags) {
        this.title = title;
        this.category = category;
        this.summary = summary;
        this.conditions = conditions;
        this.template = template;
        this.tags = tags;
    }
}
