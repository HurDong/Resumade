package com.resumade.api.recruit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public class RecruitDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SyncRequest {
        private Long id;
        private String name;
        private String title;
        @JsonProperty("start_time")
        private OffsetDateTime startTime;
        @JsonProperty("end_time")
        private OffsetDateTime endTime;
        @JsonProperty("image_file_name")
        private String imageFileName;
        @JsonProperty("career_types")
        private List<Integer> careerTypes;
        @JsonProperty("job_groups")
        private List<Integer> jobGroups;
        private List<QuestionSyncRequest> questions;
        
        public LocalDateTime getStartLocalDateTime() {
            return startTime != null ? startTime.toLocalDateTime() : null;
        }
        public LocalDateTime getEndLocalDateTime() {
            return endTime != null ? endTime.toLocalDateTime() : null;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class QuestionSyncRequest {
        private Long id;
        private String question;
        @JsonProperty("word_limit")
        private Integer wordLimit;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CalendarResponse {
        private Long id;
        private String name;
        private String title;
        @JsonProperty("start_time")
        private LocalDateTime startTime;
        @JsonProperty("end_time")
        private LocalDateTime endTime;
        @JsonProperty("image_file_name")
        private String imageFileName;
        @JsonProperty("career_types")
        private List<Integer> careerTypes;
        @JsonProperty("job_groups")
        private List<Integer> jobGroups;
        private List<QuestionResponse> questions;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionResponse {
        private Long id;
        private String question;
        @JsonProperty("word_limit")
        private Integer wordLimit;
    }
}
