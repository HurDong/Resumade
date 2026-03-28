package com.resumade.api.workspace.controller;

import com.resumade.api.workspace.dto.EditActionRequest;
import com.resumade.api.workspace.dto.EditActionResponse;
import com.resumade.api.workspace.dto.FinalEditorResponse;
import com.resumade.api.workspace.dto.FinalSaveRequest;
import com.resumade.api.workspace.dto.FinalSaveResponse;
import com.resumade.api.workspace.dto.QuestionNavItem;
import com.resumade.api.workspace.dto.SpellCheckRequest;
import com.resumade.api.workspace.dto.SpellCheckResponse;
import java.util.Map;
import com.resumade.api.workspace.service.FinalEditorService;
import com.resumade.api.workspace.service.SpellCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspace/final")
@RequiredArgsConstructor
public class FinalEditorController {

    private final FinalEditorService finalEditorService;
    private final SpellCheckService spellCheckService;

    /**
     * 최종 편집기 진입 시 필요한 모든 데이터 로드
     * (문항, 원본 초안, 세탁본, 기존 최종본, 선택된 제목)
     */
    @GetMapping("/{questionId}")
    public ResponseEntity<FinalEditorResponse> getFinalEditorData(@PathVariable Long questionId) {
        return ResponseEntity.ok(finalEditorService.getFinalEditorData(questionId));
    }

    /**
     * 자동저장 — 1.5초 디바운스 후 FE에서 호출
     */
    @PatchMapping("/{questionId}/save")
    public ResponseEntity<FinalSaveResponse> saveFinalText(
            @PathVariable Long questionId,
            @RequestBody FinalSaveRequest request) {
        return ResponseEntity.ok(finalEditorService.saveFinalText(questionId, request));
    }

    /**
     * 동일 Application 내 모든 문항 목록 — 편집기 문항 네비게이터용
     */
    @GetMapping("/{questionId}/siblings")
    public ResponseEntity<List<QuestionNavItem>> getSiblingQuestions(@PathVariable Long questionId) {
        return ResponseEntity.ok(finalEditorService.getSiblingQuestions(questionId));
    }

    /**
     * 선택 텍스트 세탁 — 한→영→한 번역 왕복
     */
    @PostMapping("/{questionId}/rewash-selection")
    public ResponseEntity<Map<String, String>> rewashSelection(
            @PathVariable Long questionId,
            @RequestBody Map<String, String> body) {
        String selectedText = body.get("selectedText");
        String washedText = finalEditorService.rewashSelection(selectedText);
        return ResponseEntity.ok(Map.of("washedText", washedText));
    }

    /**
     * AI 편집 액션 — 선택된 텍스트를 actionKey에 따라 변환
     * 무거운 연산이므로 FE에서 로딩 상태를 표시합니다.
     */
    @PostMapping("/{questionId}/edit-action")
    public ResponseEntity<EditActionResponse> applyEditAction(
            @PathVariable Long questionId,
            @RequestBody EditActionRequest request) {
        return ResponseEntity.ok(finalEditorService.applyEditAction(questionId, request));
    }

    /**
     * 맞춤법 검사 — 현재 에디터 텍스트의 오류 제안 목록 반환.
     * LLM 호출 실패 시 빈 배열로 폴백하므로 FE는 항상 200 OK를 받는다.
     */
    @PostMapping("/{questionId}/spell-check")
    public ResponseEntity<SpellCheckResponse> spellCheck(
            @PathVariable Long questionId,
            @RequestBody SpellCheckRequest request) {
        return ResponseEntity.ok(spellCheckService.check(request.text()));
    }
}
