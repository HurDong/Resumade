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
    private String businessUnit;
    private String targetService;
    private String focusRole;
    private String techFocus;
    private String questionGoal;
}
