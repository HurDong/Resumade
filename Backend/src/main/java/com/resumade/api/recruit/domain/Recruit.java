package com.resumade.api.recruit.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recruits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Recruit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long jasoseolId; // 자소설닷컴 원본 공고 ID

    @Column(nullable = false, length = 255)
    private String companyName;

    @Column(nullable = false, length = 500)
    private String title;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Column(length = 2000)
    private String imageFileName;

    @Column(columnDefinition = "JSON")
    private String careerTypes; // List<Integer> 를 JSON으로 저장

    @Column(columnDefinition = "JSON")
    private String jobGroups;

    @OneToMany(mappedBy = "recruit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private final List<RecruitQuestion> questions = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Recruit(Long jasoseolId, String companyName, String title, LocalDateTime startTime, LocalDateTime endTime, String imageFileName, String careerTypes, String jobGroups) {
        this.jasoseolId = jasoseolId;
        this.companyName = companyName;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.imageFileName = imageFileName;
        this.careerTypes = careerTypes;
        this.jobGroups = jobGroups;
    }

    public void addQuestion(RecruitQuestion question) {
        if (question == null) return;
        question.attachTo(this);
        questions.add(question);
    }

    public void replaceQuestions(List<RecruitQuestion> requestQuestions) {
        questions.clear();
        if (requestQuestions != null) {
            requestQuestions.forEach(this::addQuestion);
        }
    }

    public void updateDetails(String companyName, String title, LocalDateTime startTime, LocalDateTime endTime, String imageFileName, String careerTypes, String jobGroups) {
        this.companyName = companyName;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.imageFileName = imageFileName;
        this.careerTypes = careerTypes;
        this.jobGroups = jobGroups;
    }
}
