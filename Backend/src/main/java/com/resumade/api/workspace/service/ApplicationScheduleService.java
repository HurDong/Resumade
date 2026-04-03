package com.resumade.api.workspace.service;

import com.resumade.api.workspace.domain.ApplicationSchedule;
import com.resumade.api.workspace.domain.ApplicationScheduleRepository;
import com.resumade.api.workspace.domain.ApplicationRepository;
import com.resumade.api.workspace.domain.ScheduleType;
import com.resumade.api.workspace.dto.ApplicationScheduleDto;
import com.resumade.api.workspace.dto.ApplicationScheduleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationScheduleService {

    private final ApplicationScheduleRepository scheduleRepository;
    private final ApplicationRepository applicationRepository;

    /** 특정 공고의 전형 일정 전체 조회 */
    public List<ApplicationScheduleDto> getSchedules(Long applicationId) {
        return scheduleRepository
                .findByApplicationIdOrderBySortOrderAsc(applicationId)
                .stream()
                .map(ApplicationScheduleDto::from)
                .collect(Collectors.toList());
    }

    /** 전형 일정 추가 */
    @Transactional
    public ApplicationScheduleDto createSchedule(Long applicationId, ApplicationScheduleRequest req) {
        var application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        ScheduleType type = ScheduleType.valueOf(req.getType().toUpperCase());

        ApplicationSchedule schedule = ApplicationSchedule.builder()
                .application(application)
                .type(type)
                .customLabel(req.getCustomLabel())
                .scheduledAt(req.getScheduledAt())
                .memo(req.getMemo())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : resolveDefaultSortOrder(type))
                .build();

        return ApplicationScheduleDto.from(scheduleRepository.save(schedule));
    }

    /** 전형 일정 수정 */
    @Transactional
    public ApplicationScheduleDto updateSchedule(Long scheduleId, ApplicationScheduleRequest req) {
        ApplicationSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        if (req.getScheduledAt() != null) schedule.setScheduledAt(req.getScheduledAt());
        if (req.getMemo() != null)        schedule.setMemo(req.getMemo());
        if (req.getCustomLabel() != null) schedule.setCustomLabel(req.getCustomLabel());
        if (req.getSortOrder() != null)   schedule.setSortOrder(req.getSortOrder());

        return ApplicationScheduleDto.from(scheduleRepository.save(schedule));
    }

    /** 전형 일정 삭제 */
    @Transactional
    public void deleteSchedule(Long scheduleId) {
        scheduleRepository.deleteById(scheduleId);
    }

    /** ScheduleType별 기본 정렬 순서 (서류→코테→인적성→1차→2차→커스텀) */
    private int resolveDefaultSortOrder(ScheduleType type) {
        return switch (type) {
            case DOCUMENT_DEADLINE -> 0;
            case CODING_TEST       -> 1;
            case APTITUDE          -> 2;
            case INTERVIEW1        -> 3;
            case INTERVIEW2        -> 4;
            case CUSTOM            -> 99;
        };
    }
}
