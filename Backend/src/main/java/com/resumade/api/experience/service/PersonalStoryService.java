package com.resumade.api.experience.service;

import com.resumade.api.experience.domain.PersonalStory;
import com.resumade.api.experience.domain.PersonalStoryRepository;
import com.resumade.api.experience.dto.PersonalStoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@link PersonalStory} 엔티티에 대한 CRUD 및 비즈니스 로직을 담당하는 서비스.
 */
@Service
@RequiredArgsConstructor
public class PersonalStoryService {

    private final PersonalStoryRepository repository;

    @Transactional
    public PersonalStoryResponse getLifeStory() {
        return PersonalStoryResponse.from(ensureSingleLifeStory());
    }

    @Transactional
    public PersonalStoryResponse saveLifeStory(PersonalStoryResponse.UpsertRequest request) {
        PersonalStory story = ensureSingleLifeStory();
        story.update(
                PersonalStory.LIFE_STORY_TYPE,
                normalizeContent(request == null ? null : request.getContent())
        );
        return PersonalStoryResponse.from(story);
    }

    @Transactional
    public PersonalStoryResponse importLifeStory(List<PersonalStoryResponse.UpsertRequest> requests) {
        String content = requests == null ? "" : requests.stream()
                .map(PersonalStoryResponse.UpsertRequest::getContent)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        return replaceLifeStory(content);
    }

    @Transactional
    public PersonalStoryResponse replaceLifeStory(String content) {
        repository.deleteAll(repository.findAllByOrderByIdAsc());
        PersonalStory saved = repository.save(PersonalStory.builder()
                .type(PersonalStory.LIFE_STORY_TYPE)
                .content(normalizeContent(content))
                .build());
        return PersonalStoryResponse.from(saved);
    }

    @Transactional
    public void clearLifeStory() {
        repository.deleteAll(repository.findAllByOrderByIdAsc());
    }

    private PersonalStory ensureSingleLifeStory() {
        List<PersonalStory> all = repository.findAllByOrderByIdAsc();
        if (all.isEmpty()) {
            return repository.save(PersonalStory.builder()
                    .type(PersonalStory.LIFE_STORY_TYPE)
                    .content("")
                    .build());
        }

        PersonalStory lifeStory = all.stream()
                .filter(story -> PersonalStory.LIFE_STORY_TYPE.equals(story.getType()))
                .findFirst()
                .orElse(null);
        String content = lifeStory != null && lifeStory.getContent() != null && !lifeStory.getContent().isBlank()
                ? lifeStory.getContent()
                : buildLegacyLifeStoryContent(all);

        repository.deleteAll(all);
        return repository.save(PersonalStory.builder()
                .type(PersonalStory.LIFE_STORY_TYPE)
                .content(normalizeContent(content))
                .build());
    }

    private String buildLegacyLifeStoryContent(List<PersonalStory> stories) {
        return stories.stream()
                .filter(story -> !PersonalStory.WRITING_GUIDE_LEGACY_TYPE.equals(story.getType()))
                .map(PersonalStory::getContent)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }
}
