package com.resumade.api.coding.domain;

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
@Table(name = "coding_problems")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CodingProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String company;

    @Column(nullable = false, length = 20)
    private String date;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "json")
    private String types;

    @Column(length = 50)
    private String platform;

    private Integer level;

    @Column(columnDefinition = "TEXT")
    private String myApproach;

    @Column(columnDefinition = "TEXT")
    private String betterApproach;

    @Column(columnDefinition = "TEXT")
    private String betterCode;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public CodingProblem(String company, String date, String title, String types,
                         String platform, Integer level, String myApproach,
                         String betterApproach, String betterCode, String note) {
        this.company = company;
        this.date = date;
        this.title = title;
        this.types = types;
        this.platform = platform;
        this.level = level;
        this.myApproach = myApproach;
        this.betterApproach = betterApproach;
        this.betterCode = betterCode;
        this.note = note;
    }

    public void update(String company, String date, String title, String types,
                       String platform, Integer level, String myApproach,
                       String betterApproach, String betterCode, String note) {
        this.company = company;
        this.date = date;
        this.title = title;
        this.types = types;
        this.platform = platform;
        this.level = level;
        this.myApproach = myApproach;
        this.betterApproach = betterApproach;
        this.betterCode = betterCode;
        this.note = note;
    }
}
