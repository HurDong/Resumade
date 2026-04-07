package com.resumade.api.recruit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.recruit.domain.Recruit;
import com.resumade.api.recruit.domain.RecruitQuestion;
import com.resumade.api.recruit.domain.RecruitRepository;
import com.resumade.api.recruit.dto.RecruitDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitService {

    private final RecruitRepository recruitRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void syncRecruits(List<RecruitDto.SyncRequest> requests) {
        log.info("Starting sync for {} recruits", requests.size());

        for (RecruitDto.SyncRequest request : requests) {
            if (request.getId() == null) continue;

            Recruit recruit = recruitRepository.findByJasoseolId(request.getId())
                    .orElse(null);

            String careerTypesJson = "[]";
            String jobGroupsJson = "[]";
            try {
                if (request.getCareerTypes() != null) {
                    careerTypesJson = objectMapper.writeValueAsString(request.getCareerTypes());
                }
                if (request.getJobGroups() != null) {
                    jobGroupsJson = objectMapper.writeValueAsString(request.getJobGroups());
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize lists to JSON for jasoseolId: {}", request.getId(), e);
            }

            if (recruit == null) {
                recruit = Recruit.builder()
                        .jasoseolId(request.getId())
                        .companyName(request.getName())
                        .title(request.getTitle())
                        .startTime(request.getStartLocalDateTime())
                        .endTime(request.getEndLocalDateTime())
                        .imageFileName(request.getImageFileName())
                        .careerTypes(careerTypesJson)
                        .jobGroups(jobGroupsJson)
                        .build();
            } else {
                recruit.updateDetails(
                        request.getName(),
                        request.getTitle(),
                        request.getStartLocalDateTime(),
                        request.getEndLocalDateTime(),
                        request.getImageFileName(),
                        careerTypesJson,
                        jobGroupsJson
                );
            }

            List<RecruitQuestion> newQuestions = new ArrayList<>();
            if (request.getQuestions() != null) {
                newQuestions = request.getQuestions().stream()
                        .map(qReq -> RecruitQuestion.builder()
                                .jasoseolQuestionId(qReq.getId())
                                .question(qReq.getQuestion())
                                .wordLimit(qReq.getWordLimit() != null ? qReq.getWordLimit() : 0)
                                .build())
                        .collect(Collectors.toList());
            }

            recruit.replaceQuestions(newQuestions);
            recruitRepository.save(recruit);
        }
        
        log.info("Finished sync for recruits.");
    }

    @Transactional(readOnly = true)
    public List<RecruitDto.CalendarResponse> getCalendar(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = yearMonth.atEndOfMonth().atTime(23, 59, 59, 999999999);

        // UI 달력에서 한 주 앞뒤의 공고도 볼 수 있도록 패딩을 줍니다.
        List<Recruit> recruits = recruitRepository.findByStartTimeBetweenOrEndTimeBetween(
                startOfMonth.minusDays(15), endOfMonth.plusDays(15),
                startOfMonth.minusDays(15), endOfMonth.plusDays(15)
        );

        return recruits.stream().map(this::toResponseDto).collect(Collectors.toList());
    }

    private RecruitDto.CalendarResponse toResponseDto(Recruit recruit) {
        List<Integer> careerTypes = new ArrayList<>();
        List<Integer> jobGroups = new ArrayList<>();
        try {
            if (recruit.getCareerTypes() != null) {
                careerTypes = objectMapper.readValue(recruit.getCareerTypes(), new TypeReference<List<Integer>>() {});
            }
            if (recruit.getJobGroups() != null) {
                jobGroups = objectMapper.readValue(recruit.getJobGroups(), new TypeReference<List<Integer>>() {});
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON", e);
        }

        List<RecruitDto.QuestionResponse> questions = recruit.getQuestions().stream()
                .map(q -> RecruitDto.QuestionResponse.builder()
                        .id(q.getJasoseolQuestionId())
                        .question(q.getQuestion())
                        .wordLimit(q.getWordLimit())
                        .build())
                .collect(Collectors.toList());

        return RecruitDto.CalendarResponse.builder()
                .id(recruit.getId()) // Internal ID
                .name(recruit.getCompanyName())
                .title(recruit.getTitle())
                .startTime(recruit.getStartTime())
                .endTime(recruit.getEndTime())
                .imageFileName(recruit.getImageFileName())
                .careerTypes(careerTypes)
                .jobGroups(jobGroups)
                .questions(questions)
                .build();
    }
}
