package com.resumade.api.coding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.coding.domain.CodingProblem;
import com.resumade.api.coding.domain.CodingProblemRepository;
import com.resumade.api.coding.dto.CodingProblemRequest;
import com.resumade.api.coding.dto.CodingProblemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodingArchiveService {

    private final CodingProblemRepository codingProblemRepository;
    private final ObjectMapper objectMapper;

    public List<CodingProblemResponse> findAll() {
        return codingProblemRepository.findAllByOrderByDateDescCreatedAtDesc()
                .stream()
                .map(CodingProblemResponse::from)
                .toList();
    }

    @Transactional
    public CodingProblemResponse create(CodingProblemRequest request) {
        CodingProblem problem = CodingProblem.builder()
                .company(request.company())
                .date(request.date())
                .title(request.title())
                .types(toJson(request.types()))
                .platform(request.platform())
                .level(request.level())
                .myApproach(request.myApproach())
                .betterApproach(request.betterApproach())
                .betterCode(request.betterCode())
                .note(request.note())
                .build();
        return CodingProblemResponse.from(codingProblemRepository.save(problem));
    }

    @Transactional
    public CodingProblemResponse update(Long id, CodingProblemRequest request) {
        CodingProblem problem = codingProblemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("코딩 문제를 찾을 수 없습니다: " + id));
        problem.update(
                request.company(),
                request.date(),
                request.title(),
                toJson(request.types()),
                request.platform(),
                request.level(),
                request.myApproach(),
                request.betterApproach(),
                request.betterCode(),
                request.note()
        );
        return CodingProblemResponse.from(problem);
    }

    @Transactional
    public void delete(Long id) {
        if (!codingProblemRepository.existsById(id)) {
            throw new IllegalArgumentException("코딩 문제를 찾을 수 없습니다: " + id);
        }
        codingProblemRepository.deleteById(id);
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
