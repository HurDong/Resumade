package com.resumade.api.workspace.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "application_schedules")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ApplicationSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleType type;

    /**
     * ScheduleType.CUSTOM일 때 사용자가 직접 입력한 전형명
     * (예: "커피챗", "과제전형", "역량면접" 등)
     */
    @Column(length = 100)
    private String customLabel;

    /** 전형 예정일시 */
    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Column(columnDefinition = "TEXT")
    private String memo;

    /** 같은 Application 내 여러 전형의 표시 순서 */
    @Column(nullable = false)
    private Integer sortOrder = 0;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public ApplicationSchedule(Application application, ScheduleType type,
                                String customLabel, LocalDateTime scheduledAt,
                                String memo, Integer sortOrder) {
        this.application = application;
        this.type = type;
        this.customLabel = customLabel;
        this.scheduledAt = scheduledAt;
        this.memo = memo;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }

    /** UI에 표시할 레이블을 반환합니다 (CUSTOM이면 customLabel 우선 사용) */
    public String resolveLabel() {
        if (type == ScheduleType.CUSTOM && customLabel != null && !customLabel.isBlank()) {
            return customLabel;
        }
        return type.getDefaultLabel();
    }
}
