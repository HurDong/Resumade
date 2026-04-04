package com.resumade.api.experience.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "experience_facets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperienceFacet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "experience_id", nullable = false)
    private Experience experience;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(columnDefinition = "JSON")
    private String situation;

    @Column(columnDefinition = "JSON")
    private String role;

    @Column(columnDefinition = "JSON")
    private String judgment;

    @Column(columnDefinition = "JSON")
    private String actions;

    @Column(columnDefinition = "JSON")
    private String results;

    @Column(columnDefinition = "JSON")
    private String techStack;

    @Column(columnDefinition = "JSON")
    private String jobKeywords;

    @Column(columnDefinition = "JSON")
    private String questionTypes;

    @Builder
    public ExperienceFacet(
            String title,
            Integer displayOrder,
            String situation,
            String role,
            String judgment,
            String actions,
            String results,
            String techStack,
            String jobKeywords,
            String questionTypes
    ) {
        this.title = title;
        this.displayOrder = displayOrder;
        this.situation = situation;
        this.role = role;
        this.judgment = judgment;
        this.actions = actions;
        this.results = results;
        this.techStack = techStack;
        this.jobKeywords = jobKeywords;
        this.questionTypes = questionTypes;
    }

    void attachTo(Experience experience) {
        this.experience = experience;
    }
}
