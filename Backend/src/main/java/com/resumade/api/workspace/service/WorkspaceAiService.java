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
        "기존의 뻔한 표현(예: '노력하는', '열정적인')은 지양하고, 실제 수치와 상황 중심의 STAR(Situation, Task, Action, Result) 기법을 적용하세요.",
        "특히 'Action' 부분에서 지원자가 사용한 기술적 도구와 해결 과정을 구체적으로 묘사하여 전문성을 강조하세요.",
        "반드시 한국어로 작성하며, 전문적이고 신뢰감 있는 어조(스타일: ~했습니다, ~합니다)를 유지하세요.",
        "**[중요: 실력 있는 소제목 작성 규칙]**",
        "1. 반드시 글의 도입부에 해당 문항의 핵심 성과를 요약하는 소제목을 작성하세요.",
        "2. 소제목 형식 규칙: 소제목은 반드시 [소제목 내용] 형식을 사용해야 합니다.",
        "3. **절대 금지 사항**: 소제목에 ** (마크다운 굵게) 표시를 절대로, 어떠한 경우에도 사용하지 마세요. 대괄호 []만 허용됩니다. (FAIL 예시: **[제목]**, SUCCESS 예시: [제목])",
        "4. 본문 시작 규칙: 소제목 작성이 끝나면 엔터를 두 번 입력하여 한 줄의 빈 줄을 만든 뒤 본문을 시작하세요. ([제목]\\n\\n본문...)",
        "소제목은 30자 이내로 간결해야 하며, 문장형보다는 전문적인 명사형 종결(~달성, ~해소 등)을 사용하세요.",
        "**[중요: 글자수 제한 엄수]** 작성할 초안은 반드시 공백 포함 {{maxLength}}자 이내여야 합니다. 뒤이어 진행될 번역 세탁 과정에서 분량이 늘어날 수 있으므로, 초기 초안은 전체 제한인 {{maxLength}}자의 70~80% 수준으로 매우 간결하게 작성하세요. 이 제한을 초과하면 절대로 안 됩니다.",
        "특히 '하고자 합니다'와 같은 상투적이고 수동적인 표현은 절대 사용하지 마세요. 대신 '하고 싶습니다' 또는 더 능동적이고 직접적인 서술어형 문장을 사용하세요.",
        "**[중요: 중복 방지 및 유기적 연결]** 전체 자소서 문항들(다른 문항 정보: {{others}})을 참고하여, 다른 문항에서 강조한 소재나 핵심 성과를 그대로 반복하지 마세요.",
        "각 문항이 지원자의 서로 다른 역량(인성, 기술, 리더십 등)을 다루도록 소재를 지능적으로 배분하세요. 만약 부득이하게 같은 프로젝트를 언급해야 한다면, 이전 문항과는 완전히 다른 각도(예: 1번은 아키텍처, 2번은 협업 과정)에서 서술하세요.",
        "결과는 반드시 아래의 json 구조를 가진 객체여야 합니다: {\"text\": \"초안 내용...\"}"
    })
    @UserMessage("지원 회사: {{company}}\n지원 직무: {{position}}\n글자수 제한: {{maxLength}}자 이내\n질문: {{question}}\n\n관련 경험 데이터: {{context}}\n\n다른 문항 정보: {{others}}\n\n**[사용자 특별 지시사항]**: {{directive}}\n\n위 정보와 특히 [사용자 특별 지시사항]을 최우선으로 반영하여, 다른 문항과 주제가 겹치지 않게 주의하며 이 문항에 가장 적합한 고유의 에피소드를 선택해 초안을 작성해줘. 반드시 {{maxLength}}자 이내로 작성해야 함을 명심하세요.")
    DraftResponse generateDraft(
            @V("company") String company,
            @V("position") String position,
            @V("question") String question,
            @V("maxLength") int maxLength,
            @V("context") String context,
            @V("others") String others,
            @V("directive") String directive);

    @SystemMessage({
        "당신은 IT 전문 번역 검수자이자 시니어 개발자입니다.",
        "원본 한국어 자소서와, [한국어 -> 영어 -> 한국어] 과정을 거쳐 세탁된 자소서를 비교 분석하세요.",
        "단순한 오역 수정을 넘어, 개발자의 입장에서 어색한 기술적 문맥을 찾아내어 교정하세요.",
        "특히 'transaction', 'architecture', 'scalability' 등 핵심 IT 키워드가 일반 어휘(거래, 건축, 확장성 등)로 번역된 경우를 엄격히 찾아내어 'high' severity로 보고하세요.",
        "휴먼 패치 버전은 원문의 의도를 유지하되, 번역기의 흔적이 전혀 없는 세련된 현직자 말투로 다듬어야 합니다.",
        "결과는 반드시 아래의 json 구조를 정의된 DraftAnalysisResult 형식으로 반환해야 합니다: {\"summary\": \"...\", \"mistranslations\": [{\"original\": \"...\", \"translated\": \"...\", \"suggestion\": \"...\", \"severity\": \"low/high\"}]}",
        "**[중요 데이터 형식 규칙]**:",
        "1. mistranslations의 'translated' 필드는 반드시 세탁본({{washed}})에 존재하는 **정확한 문자열(Exact Match)**이어야 합니다. 한 글자, 공백 하나도 틀리지 않게 본문에서 그대로 복사해오세요.",
        "2. 'suggestion' 필드는 부연 설명 없이 **교체할 텍스트만** 포함해야 합니다.",
        "3. 만약 세탁본이 소제목에 마크다운 굵게(**)를 사용했다면, 이를 제거한 [소제목] 형태로 교정 제안을 포함하세요.",
        "4. 'summary' 필드에는 세탁본의 전체적인 품질과 보완점에 대한 짧고 간결한 요약(1-2문장)을 작성하세요."
    })
    @UserMessage("원문: {{original}}\n\n세탁본: {{washed}}\n\n두 버전을 비교하여 기술 용어 오역을 리포트하고, 최적의 휴먼 패치 자소서를 완성해줘. json 형식으로 보고해.")
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
