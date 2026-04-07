package com.resumade.api.experience.dto;

import com.resumade.api.experience.domain.PersonalStory;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * {@link PersonalStory} 엔티티에 대한 요청/응답 DTO.
 */
@Getter
@Builder
public class PersonalStoryResponse {
    private Long id;
    private PersonalStory.StoryType type;
    private String period;
    private String content;
    private List<String> keywords;

    public static PersonalStoryResponse from(PersonalStory story, List<String> parsedKeywords) {
        return PersonalStoryResponse.builder()
                .id(story.getId())
                .type(story.getType())
                .period(story.getPeriod())
                .content(story.getContent())
                .keywords(parsedKeywords)
                .build();
    }

    @Getter
    public static class UpsertRequest {
        private PersonalStory.StoryType type;
        private String period;
        private String content;
        private List<String> keywords;
    }
}
