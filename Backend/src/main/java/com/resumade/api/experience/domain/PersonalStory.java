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

    public static final String LIFE_STORY_TYPE = "LIFE_STORY";
    public static final String WRITING_GUIDE_LEGACY_TYPE = "WRITING_GUIDE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public PersonalStory(String type, String content) {
        this.type = type;
        this.content = content;
    }

    public void update(String type, String content) {
        this.type = type;
        this.content = content;
    }
}
