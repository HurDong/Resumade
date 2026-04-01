package com.resumade.api.workspace.dto;

import com.resumade.api.workspace.prompt.QuestionCategory;
import lombok.Data;

@Data
public class UpdateCategoryRequest {
    /** null 허용 — null로 보내면 유저 지정 카테고리를 초기화하고 AI 자동 분류로 복귀 */
    private QuestionCategory category;
}
