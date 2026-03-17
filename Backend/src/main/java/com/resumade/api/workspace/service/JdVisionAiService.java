package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.JdAnalysisResponse;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface JdVisionAiService {

    @SystemMessage({
        "당신은 채용 공고(JD) 분석 및 데이터 구조화 전문 AI 에이전트입니다.",
        "오타가 섞인 OCR 텍스트와 이미지를 대조하여 완벽하게 정제된 공고 데이터를 생성하세요.",
        "결과는 반드시 아래의 json 구조를 가진 객체여야 합니다: {\"companyName\": \"기업명\", \"position\": \"지원직무\", \"rawJd\": \"...\", \"aiInsight\": \"...\", \"extractedQuestions\": []}"
    })
    @UserMessage("OCR 추출 텍스트: {{ocrText}}\n이미지 정보를 참고하여 정제해줘.")
    JdAnalysisResponse analyzeJdWithOcr(@V("ocrText") String ocrText, @V("image") ImageContent image);
}
