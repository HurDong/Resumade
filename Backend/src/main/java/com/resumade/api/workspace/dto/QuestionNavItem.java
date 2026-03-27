package com.resumade.api.workspace.dto;

/**
 * 다림질 편집기 문항 네비게이터용 경량 DTO.
 * 동일 Application에 속한 문항 목록을 순서와 함께 반환합니다.
 */
public record QuestionNavItem(
        Long id,
        int index,          // 1-based 순서 (UI 표시용)
        String title,
        boolean hasWashedDraft  // washedKr 존재 여부 — 다림질 가능 문항 표시
) {}
