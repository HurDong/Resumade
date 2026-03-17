package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.JdAnalysisResponse;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface JdTextAiService {

    @SystemMessage({
        "당신은 채용 공고(JD) 분석 전문가입니다.",
        "제공된 채용 공고를 분석하여 기업명, 직무, 문항을 추출하세요.",
        "결과는 반드시 아래의 json 구조를 가진 객체여야 합니다: {\"companyName\": \"...\", \"position\": \"...\", \"rawJd\": \"...\", \"aiInsight\": \"...\", \"extractedQuestions\": []}"
    })
    @UserMessage("다음 채용 공고를 분석해줘:\n\n{{rawJd}}")
    JdAnalysisResponse analyzeJd(@V("rawJd") String rawJd);
}
