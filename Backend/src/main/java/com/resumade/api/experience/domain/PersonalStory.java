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

/**
 * 성장과정 및 가치관 문항의 소재가 되는 '인생 서사' 데이터 엔티티.
 */
@Entity
@Table(name = "personal_stories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PersonalStory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StoryType type;

    @Column(length = 100)
    private String period; // 시기 (예: "고등학생 시절", "대학교 2학년")

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content; // 서사 본문

    @Column(columnDefinition = "JSON")
    private String keywords; // 핵심 키워드 (JSON 배열 문자열)

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public PersonalStory(StoryType type, String period, String content, String keywords) {
        this.type = type;
        this.period = period;
        this.content = content;
        this.keywords = keywords;
    }

    public void update(StoryType type, String period, String content, String keywords) {
        this.type = type;
        this.period = period;
        this.content = content;
        this.keywords = keywords;
    }

    public enum StoryType {
        TURNING_POINT,    // 전환점
        VALUE,             // 가치관
        ENVIRONMENT,       // 성장 환경
        INFLUENCE,         // 영향받은 인물/경험
        FAILURE_RECOVERY, // 실패와 극복
        MILESTONE,         // 인생 이정표
        WRITING_GUIDE      // 작성 가이드 (강조 역량·성장 흐름·문체 지침 — AI 프롬프트에 직접 주입)
    }
}
