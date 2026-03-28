package com.resumade.api.workspace.dto;

/**
 * 맞춤법 검사 요청.
 * 편집기에서 현재 보이는 텍스트를 그대로 전송한다 (자동저장 여부 무관).
 *
 * @param text 검사 대상 자기소개서 본문 텍스트
 */
public record SpellCheckRequest(String text) {}
