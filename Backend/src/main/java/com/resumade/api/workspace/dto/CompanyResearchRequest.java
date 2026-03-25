package com.resumade.api.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResearchRequest {
    /** 사용자가 특별히 궁금한 점 (선택). 없으면 AI가 JD만으로 자동 분석. */
    private String additionalFocus;
}
