package com.resumade.api.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JdAnalysisResponse {
    private String companyName;
    private String position;
    private String rawJd;
    private String aiInsight;
    private List<String> extractedQuestions;
}
