package com.resumade.api.workspace.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false, length = 100)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.DOCUMENT;

    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationResult result = ApplicationResult.PENDING;

    @Column(columnDefinition = "TEXT")
    private String rawJd;

    @Column(columnDefinition = "TEXT")
    private String aiInsight;

    private String logoUrl;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkspaceQuestion> questions = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Application(String companyName, String position, String rawJd, String aiInsight, ApplicationStatus status, LocalDateTime deadline, ApplicationResult result, String logoUrl) {
        this.companyName = companyName;
        this.position = position;
        this.rawJd = rawJd;
        this.aiInsight = aiInsight;
        this.status = status != null ? status : ApplicationStatus.DOCUMENT;
        this.deadline = deadline;
        this.result = result != null ? result : ApplicationResult.PENDING;
        this.logoUrl = logoUrl;
    }

    public void addQuestion(WorkspaceQuestion question) {
        this.questions.add(question);
        question.setApplication(this);
    }
}
