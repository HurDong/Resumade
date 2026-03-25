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
@NoArgsConstructor
@AllArgsConstructor
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

    @Lob
    @Column(length = 16777215)
    private String content;

    @Lob
    @Column(length = 16777215)
    private String washedKr;

    @Lob
    @Column(length = 16777215)
    private String mistranslations;

    @Lob
    @Column(length = 16777215)
    private String aiReview;

    @com.fasterxml.jackson.annotation.JsonProperty("isCompleted")
    @Column(nullable = false)
    private boolean isCompleted = false;

    @Lob
    @Column(length = 16777215)
    private String userDirective;

    @Lob
    @Column(length = 16777215)
    private String batchStrategyDirective;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public WorkspaceQuestion(String title, Integer maxLength, String content, String washedKr, String mistranslations, String aiReview, String userDirective, String batchStrategyDirective, Boolean isCompleted) {
        this.title = title;
        this.maxLength = maxLength;
        this.content = content;
        this.washedKr = washedKr;
        this.mistranslations = mistranslations;
        this.aiReview = aiReview;
        this.userDirective = userDirective;
        this.batchStrategyDirective = batchStrategyDirective;
        this.isCompleted = isCompleted != null ? isCompleted : false;
    }
}
