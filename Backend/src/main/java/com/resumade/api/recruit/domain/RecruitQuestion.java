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

@Entity
@Table(name = "recruit_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RecruitQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruit_id")
    private Recruit recruit;

    private Long jasoseolQuestionId; // 원본 문항 ID

    @Column(columnDefinition = "TEXT")
    private String question;

    private Integer wordLimit;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public RecruitQuestion(Long jasoseolQuestionId, String question, Integer wordLimit) {
        this.jasoseolQuestionId = jasoseolQuestionId;
        this.question = question;
        this.wordLimit = wordLimit;
    }

    public void attachTo(Recruit recruit) {
        this.recruit = recruit;
    }
}
