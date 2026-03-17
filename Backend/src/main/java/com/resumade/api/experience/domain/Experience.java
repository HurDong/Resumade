package com.resumade.api.experience.domain;

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
@Table(name = "experiences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    // We can store arrays as JSON strings in the DB
    @Column(columnDefinition = "JSON")
    private String techStack;

    @Column(columnDefinition = "JSON")
    private String metrics;

    @Column(length = 100)
    private String period;

    @Column(length = 100)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String rawContent;

    @Column(nullable = false, updatable = false)
    private String originalFileName;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Experience(String title, String category, String description, String techStack, String metrics, String period, String role, String rawContent, String originalFileName) {
        this.title = title;
        this.category = category;
        this.description = description;
        this.techStack = techStack;
        this.metrics = metrics;
        this.period = period;
        this.role = role;
        this.rawContent = rawContent;
        this.originalFileName = originalFileName;
    }

    public void updateFromAi(String title, String category, String description, String techStack, String metrics, String period, String role) {
        this.title = title;
        this.category = category;
        this.description = description;
        this.techStack = techStack;
        this.metrics = metrics;
        this.period = period;
        this.role = role;
    }

    public void updateFromMarkdown(String title, String description, String techStack, String metrics, String period, String role, String rawContent) {
        this.title = title;
        this.description = description;
        this.techStack = techStack;
        this.metrics = metrics;
        this.period = period;
        this.role = role;
        this.rawContent = rawContent;
    }
}
