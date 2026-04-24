package com.resumade.api.experience.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "experience_units")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperienceUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "experience_id", nullable = false)
    private Experience experience;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facet_id")
    private ExperienceFacet facet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExperienceUnitType unitType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "JSON")
    private String intentTags;

    @Column(columnDefinition = "JSON")
    private String techStack;

    @Column(columnDefinition = "JSON")
    private String jobKeywords;

    @Column(columnDefinition = "JSON")
    private String questionTypes;

    @Column(nullable = false)
    private Integer displayOrder;

    @Builder
    public ExperienceUnit(
            ExperienceFacet facet,
            ExperienceUnitType unitType,
            String text,
            String intentTags,
            String techStack,
            String jobKeywords,
            String questionTypes,
            Integer displayOrder
    ) {
        this.facet = facet;
        this.unitType = unitType;
        this.text = text;
        this.intentTags = intentTags;
        this.techStack = techStack;
        this.jobKeywords = jobKeywords;
        this.questionTypes = questionTypes;
        this.displayOrder = displayOrder == null ? 0 : displayOrder;
    }

    void attachTo(Experience experience) {
        this.experience = experience;
    }
}
