package com.resumade.api.technote.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.technote.domain.TechNote;
import com.resumade.api.technote.domain.TechNoteRepository;
import com.resumade.api.technote.dto.TechNoteRequest;
import com.resumade.api.technote.dto.TechNoteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TechNoteService {

    private final TechNoteRepository techNoteRepository;
    private final ObjectMapper objectMapper;

    public List<TechNoteResponse> findAll() {
        return techNoteRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .map(TechNoteResponse::from)
                .toList();
    }

    @Transactional
    public TechNoteResponse create(TechNoteRequest request) {
        TechNote note = TechNote.builder()
                .title(request.title())
                .category(request.category())
                .summary(request.summary())
                .conditions(toJson(request.conditions()))
                .template(request.template())
                .tags(toJson(request.tags()))
                .build();
        return TechNoteResponse.from(techNoteRepository.save(note));
    }

    @Transactional
    public TechNoteResponse update(Long id, TechNoteRequest request) {
        TechNote note = techNoteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("기술 노트를 찾을 수 없습니다: " + id));
        note.update(
                request.title(),
                request.category(),
                request.summary(),
                toJson(request.conditions()),
                request.template(),
                toJson(request.tags())
        );
        return TechNoteResponse.from(note);
    }

    @Transactional
    public void delete(Long id) {
        if (!techNoteRepository.existsById(id)) {
            throw new IllegalArgumentException("기술 노트를 찾을 수 없습니다: " + id);
        }
        techNoteRepository.deleteById(id);
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("JSON 직렬화 실패: {}", e.getMessage());
            return "[]";
        }
    }
}
