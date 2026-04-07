package com.resumade.api.experience.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.domain.PersonalStory;
import com.resumade.api.experience.domain.PersonalStoryRepository;
import com.resumade.api.experience.dto.PersonalStoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link PersonalStory} 엔티티에 대한 CRUD 및 비즈니스 로직을 담당하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalStoryService {

    private final PersonalStoryRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<PersonalStoryResponse> getAllStories() {
        return repository.findAll().stream()
                .map(story -> PersonalStoryResponse.from(story, parseKeywords(story.getKeywords())))
                .collect(Collectors.toList());
    }

    @Transactional
    public PersonalStoryResponse createStory(PersonalStoryResponse.UpsertRequest request) {
        PersonalStory story = PersonalStory.builder()
                .type(request.getType())
                .period(request.getPeriod())
                .content(request.getContent())
                .keywords(serializeKeywords(request.getKeywords()))
                .build();
        
        PersonalStory saved = repository.save(story);
        return PersonalStoryResponse.from(saved, request.getKeywords());
    }

    @Transactional
    public PersonalStoryResponse updateStory(Long id, PersonalStoryResponse.UpsertRequest request) {
        PersonalStory story = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Story not found: " + id));
        
        story.update(
                request.getType(),
                request.getPeriod(),
                request.getContent(),
                serializeKeywords(request.getKeywords())
        );
        
        return PersonalStoryResponse.from(story, request.getKeywords());
    }

    @Transactional
    public void deleteStory(Long id) {
        repository.deleteById(id);
    }

    private List<String> parseKeywords(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse keywords JSON: {}", json, e);
            return Collections.emptyList();
        }
    }

    private String serializeKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(keywords);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize keywords: {}", keywords, e);
            return "[]";
        }
    }
}
