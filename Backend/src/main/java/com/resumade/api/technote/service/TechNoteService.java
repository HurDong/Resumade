package com.resumade.api.technote.service;

import com.resumade.api.technote.domain.TechNote;
import com.resumade.api.technote.domain.TechNoteRepository;
import com.resumade.api.technote.dto.TechNoteReorderRequest;
import com.resumade.api.technote.dto.TechNoteRequest;
import com.resumade.api.technote.dto.TechNoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TechNoteService {

    private static final String CATEGORY_SYNTAX = "문법";
    private static final String CATEGORY_ALGORITHM = "알고리즘";

    private final TechNoteRepository techNoteRepository;

    public List<TechNoteResponse> findAll() {
        return loadOrderedNotes().stream()
                .map(TechNoteResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TechNoteResponse findById(Long id) {
        TechNote note = techNoteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("코테 위키 카드를 찾을 수 없습니다: " + id));
        return TechNoteResponse.from(note);
    }

    public TechNoteResponse create(TechNoteRequest request) {
        int nextSortOrder = loadOrderedNotes().size();

        TechNote note = TechNote.builder()
                .title(normalizeTitle(request.title()))
                .category(normalizeCategory(request.category()))
                .content(defaultContent(request))
                .sortOrder(nextSortOrder)
                .build();

        return TechNoteResponse.from(techNoteRepository.save(note));
    }

    public TechNoteResponse update(Long id, TechNoteRequest request) {
        TechNote note = techNoteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("코테 위키 카드를 찾을 수 없습니다: " + id));

        note.updateWiki(
                normalizeTitle(request.title()),
                normalizeCategory(request.category()),
                sanitizeContent(request.content(), request.title())
        );

        return TechNoteResponse.from(note);
    }

    public List<TechNoteResponse> reorder(TechNoteReorderRequest request) {
        List<TechNote> orderedNotes = loadOrderedNotes();
        List<Long> noteIds = request.noteIds();

        if (noteIds == null || noteIds.size() != orderedNotes.size()) {
            throw new IllegalArgumentException("전체 보기에서만 코테 위키 순서를 변경할 수 있습니다.");
        }

        Map<Long, TechNote> noteById = orderedNotes.stream()
                .collect(Collectors.toMap(TechNote::getId, Function.identity()));

        for (Long noteId : noteIds) {
            if (!noteById.containsKey(noteId)) {
                throw new IllegalArgumentException("코테 위키 카드가 누락되었습니다: " + noteId);
            }
        }

        for (int index = 0; index < noteIds.size(); index++) {
            noteById.get(noteIds.get(index)).updateSortOrder(index);
        }

        techNoteRepository.saveAll(noteById.values());

        return noteIds.stream()
                .map(noteById::get)
                .map(TechNoteResponse::from)
                .toList();
    }

    public void delete(Long id) {
        TechNote note = techNoteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("코테 위키 카드를 찾을 수 없습니다: " + id));

        techNoteRepository.delete(note);
        reindexSortOrders();
    }

    private List<TechNote> loadOrderedNotes() {
        List<TechNote> notes = new ArrayList<>(techNoteRepository.findAll());
        notes.sort(
                Comparator.comparing(
                                TechNote::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(
                                TechNote::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(TechNote::getId)
        );

        boolean changed = false;
        for (int index = 0; index < notes.size(); index++) {
            TechNote note = notes.get(index);
            if (!Objects.equals(note.getSortOrder(), index)) {
                note.updateSortOrder(index);
                changed = true;
            }
        }

        if (changed) {
            techNoteRepository.saveAll(notes);
        }

        return notes;
    }

    private void reindexSortOrders() {
        List<TechNote> orderedNotes = loadOrderedNotes();
        for (int index = 0; index < orderedNotes.size(); index++) {
            orderedNotes.get(index).updateSortOrder(index);
        }
        techNoteRepository.saveAll(orderedNotes);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("코테 위키 제목은 비어 있을 수 없습니다.");
        }
        return title.trim();
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return CATEGORY_ALGORITHM;
        }

        String normalized = category.trim().toLowerCase();
        if (normalized.contains("문법") || normalized.contains("syntax")) {
            return CATEGORY_SYNTAX;
        }
        return CATEGORY_ALGORITHM;
    }

    private String defaultContent(TechNoteRequest request) {
        String normalizedTitle = normalizeTitle(request.title());
        String category = normalizeCategory(request.category());
        String content = sanitizeContent(request.content(), normalizedTitle);

        if (!content.isBlank()) {
            return content;
        }

        return """
                # %s

                ## 핵심 요약
                - 시험 직전에 다시 볼 한 줄 요약을 적어두세요.

                ## 빠르게 체크할 것
                - 실수하기 쉬운 포인트를 짧게 적어두세요.

                ## Java 템플릿
                ```java
                import java.io.*;
                import java.util.*;

                public class Main {
                    public static void main(String[] args) throws Exception {
                        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                    }
                }
                ```
                """.formatted(normalizedTitle + " (" + category + ")");
    }

    private String sanitizeContent(String content, String fallbackTitle) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String trimmed = content.trim();
        if (trimmed.startsWith("# ")) {
            return trimmed;
        }

        return "# " + fallbackTitle.trim() + "\n\n" + trimmed;
    }
}
