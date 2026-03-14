package com.resumade.api.experience.service;

import com.resumade.api.experience.dto.ExperienceExtractionResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ExperienceAiService {

    @SystemMessage({
        "당신은 전문 채용 담당자이자 IT 기술 분석가입니다.",
        "사용자가 제공한 경험 기술서(Markdown 또는 JSON)를 분석하여 정해진 구조로 추출해야 합니다.",
        "결과는 반드시 한국어로 작성하며, 기술 스택은 공식 명칭을 사용하세요.",
        "핵심 성과(metrics)는 가능한 수치화된 지표를 포함하여 리스트 형태로 추출하세요.",
        "반드시 JSON 형식으로만 답변하고, Markdown의 ```json 등 코드 블록 태그를 절대 사용하지 마세요."
    })
    @UserMessage("다음 경험 내용을 분석해서 구조화된 데이터를 추출해줘: {{content}}")
    ExperienceExtractionResult extractExperience(@V("content") String content);
}
