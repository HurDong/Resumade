package com.resumade.api.experience.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.ArrayList;
import java.util.List;
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

    @Column(length = 300)
    private String origin;

    @Column(columnDefinition = "JSON")
    private String overallTechStack;

    @Column(columnDefinition = "JSON")
    private String jobKeywords;

    @Column(columnDefinition = "JSON")
    private String questionTypes;

    @Column(length = 150)
    private String period;

    @Column(length = 500)
    private String role;

    @Column(length = 300)
    private String organization;

    @Column(columnDefinition = "TEXT")
    private String rawContent;

    @Column(nullable = false, updatable = false)
    private String originalFileName;

    @OneToMany(mappedBy = "experience", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private final List<ExperienceFacet> facets = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Experience(
            String title,
            String category,
            String description,
            String techStack,
            String metrics,
            String origin,
            String overallTechStack,
            String jobKeywords,
            String questionTypes,
            String period,
            String role,
            String organization,
            String rawContent,
            String originalFileName
    ) {
        this.title = title;
        this.category = category;
        this.description = description;
        this.techStack = techStack;
        this.metrics = metrics;
        this.origin = origin;
        this.overallTechStack = overallTechStack;
        this.jobKeywords = jobKeywords;
        this.questionTypes = questionTypes;
        this.period = period;
        this.role = role;
        this.organization = organization;
        this.rawContent = rawContent;
        this.originalFileName = originalFileName;
    }

    public void updateStructuredDetails(
            String title,
            String category,
            String description,
            String techStack,
            String metrics,
            String origin,
            String overallTechStack,
            String jobKeywords,
            String questionTypes,
            String period,
            String role,
            String organization,
            String rawContent
    ) {
        this.title = title;
        this.category = category;
        this.description = description;
        this.techStack = techStack;
        this.metrics = metrics;
        this.origin = origin;
        this.overallTechStack = overallTechStack;
        this.jobKeywords = jobKeywords;
        this.questionTypes = questionTypes;
        this.period = period;
        this.role = role;
        this.organization = organization;
        this.rawContent = rawContent;
    }

    public void replaceFacets(List<ExperienceFacet> nextFacets) {
        facets.clear();
        if (nextFacets == null || nextFacets.isEmpty()) {
            return;
        }

        nextFacets.forEach(this::addFacet);
    }

    public void addFacet(ExperienceFacet facet) {
        if (facet == null) {
            return;
        }

        facet.attachTo(this);
        facets.add(facet);
    }
}
