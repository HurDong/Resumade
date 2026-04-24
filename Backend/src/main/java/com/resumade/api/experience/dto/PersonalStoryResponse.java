package com.resumade.api.experience.dto;

import com.resumade.api.experience.domain.PersonalStory;
import lombok.Builder;
import lombok.Getter;

/**
 * 성장과정 전체 라이프스토리 요청/응답 DTO.
 */
@Getter
@Builder
public class PersonalStoryResponse {
    private Long id;
    private String content;

    public static PersonalStoryResponse from(PersonalStory story) {
        return PersonalStoryResponse.builder()
                .id(story.getId())
                .content(story.getContent())
                .build();
    }

    @Getter
    public static class UpsertRequest {
        private String content;
    }
}
