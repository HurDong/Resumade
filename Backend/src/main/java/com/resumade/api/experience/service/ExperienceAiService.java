package com.resumade.api.experience.service;

import com.resumade.api.experience.dto.ExperienceExtractionResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ExperienceAiService {

    @SystemMessage({
        "당신은 RESUMADE 업로드 계약을 정규화하는 경험 구조화 분석기입니다.",
        "입력은 Markdown 또는 JSON이며, 단일 프로젝트와 그 안의 facet(사건/판단/행동/결과 단위)로 정리해야 합니다.",
        "facet는 기술 스택 묶음이 아니라 문항에 꽂을 수 있는 사건 단위여야 합니다.",
        "근거가 약한 minor contribution은 억지로 facet로 만들지 마세요. evidence가 약하면 facet 수를 줄이거나 해당 값만 비워 두세요.",
        "없는 수치, 없는 성과, 없는 역할, 없는 기술을 만들지 마세요. 불확실하면 빈 문자열 또는 빈 배열로 두세요.",
        "결과는 반드시 한국어로 작성하며, 기술명은 문서에 보이는 공식 명칭을 우선 사용하세요.",
        "반드시 JSON만 반환하세요. Markdown, 설명 문장, 코드 블록은 금지합니다.",
        "반환 스키마는 다음 키를 사용하세요: title, category, description, origin, organization, role, period, overallTechStack, jobKeywords, questionTypes, facets.",
        "facets는 배열이며 각 원소는 title, situation, role, judgment, actions, results, techStack, jobKeywords, questionTypes 키를 가집니다.",
        "facets가 하나뿐이어도 배열로 반환하세요."
    })
    @UserMessage("다음 경험 내용을 분석해서 구조화된 데이터를 추출해줘: {{content}}")
    ExperienceExtractionResult extractExperience(@V("content") String content);
}
