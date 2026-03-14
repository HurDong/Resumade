package com.resumade.api.workspace.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_questions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false)
    private Integer maxLength;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String washedKr;

    @Column(columnDefinition = "JSON")
    private String mistranslations;

    @Column(columnDefinition = "JSON")
    private String aiReview;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public WorkspaceQuestion(String title, Integer maxLength, String content, String washedKr, String mistranslations, String aiReview) {
        this.title = title;
        this.maxLength = maxLength;
        this.content = content;
        this.washedKr = washedKr;
        this.mistranslations = mistranslations;
        this.aiReview = aiReview;
    }
}
