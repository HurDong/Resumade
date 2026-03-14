package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.DraftAnalysisResult;
import com.resumade.api.workspace.dto.JdAnalysisResponse;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WorkspaceAiService {

    class DraftResponse {
        public String text;
    }

    @SystemMessage({
        "당신은 구직자를 돕는 전문 자소서 어시스턴트입니다.",
        "제공된 사용자의 지원 정보(회사, 직무)와 경험 데이터를 바탕으로, 질문에 가장 적합한 자소서 초안을 작성하세요.",
        "반드시 한국어로 작성하며, 전문적이고 신뢰감 있는 어조(스타일: ~했습니다, ~합니다)를 유지하세요.",
        "경험 데이터에 있는 구체적인 기술 스택과 성과 수치를 적극적으로 활용하세요.",
        "결과는 반드시 아래의 json 구조를 가진 객체여야 합니다: {\"text\": \"초안 내용...\"}"
    })
    @UserMessage("지원 회사: {{company}}\n지원 직무: {{position}}\n질문: {{question}}\n\n관련 경험 데이터: {{context}}\n\n이 내용을 바탕으로 해당 기업과 직무에 최적화된 자소서 초안을 작성해줘.")
    DraftResponse generateDraft(
            @V("company") String company,
            @V("position") String position,
            @V("question") String question,
            @V("context") String context);

    @SystemMessage({
        "당신은 IT 전문 번역 검수자입니다.",
        "원본 한국어 자소서와, [한국어 -> 영어 -> 한국어] 과정을 거쳐 세탁된 자소서를 비교 분석하세요.",
        "특히 IT 기술 용어가 잘못 번역되었거나(예: transaction -> 거래), 문맥이 어색해진 부분을 찾으세요.",
        "결과는 반드시 아래의 json 구조를 정의된 DraftAnalysisResult 형식으로 반환해야 합니다: {\"score\": 0~100, \"summary\": \"...\", \"mistranslations\": [{\"original\": \"...\", \"translated\": \"...\", \"suggestion\": \"...\", \"severity\": \"low/medium/high\"}], \"recommendations\": [\"...\", \"...\"]}"
    })
    @UserMessage("원문: {{original}}\n\n세탁본: {{washed}}\n\n위 두 내용을 비교해서 오역을 찾고 종합 리뷰를 작성해줘. json 형식으로 보고하세요.")
    DraftAnalysisResult analyzePatch(@V("original") String original, @V("washed") String washed);

    @SystemMessage({
        "당신은 채용 공고(JD) 분석 전문가입니다.",
        "제공된 채용 공고 텍스트에서 기업명, 지원 직무, 그리고 자소서 문항이 있다면 이를 추출하세요.",
        "또한 해당 공고의 핵심 키워드나 기술 스택을 바탕으로 짧은 인사이트를 제공하세요.",
        "결과는 반드시 아래의 json 구조를 가진 객체여야 합니다: {\"companyName\": \"...\", \"position\": \"...\", \"rawJd\": \"분석한 JD 원문 전체\", \"aiInsight\": \"...\", \"extractedQuestions\": [\"문항 1\", \"문항 2\"]}"
    })
    @UserMessage("다음 채용 공고를 분석해줘:\n\n{{rawJd}}")
    JdAnalysisResponse analyzeJd(@V("rawJd") String rawJd);
}
