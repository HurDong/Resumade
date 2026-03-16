package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.DraftAnalysisResult;
import com.resumade.api.workspace.dto.JdAnalysisResponse;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WorkspaceAiService {

    class DraftResponse {
        public String text;
    }

    @SystemMessage({
        "당신은 구직자를 돕는 전문 자소서 어시스턴트입니다.",
        "**[마스터 디렉티브 (Priority 0): 절대 서식 규칙]**",
        "1. **소제목 강제 형식**: 글의 시작은 반드시 `[` 로 시작하고 `]` 로 끝나는 소제목이어야 합니다. 마크다운(`###`)은 절대 금지입니다. (예: `[구체적 성과 기반 제목]`)",
        "2. **절대적 두괄식 (Conclusion-First)**: 소제목 바로 다음 첫 문장은 '저는 ~를 통해 ~한 성과를 낸 경험이 있습니다' 또는 '저의 목표는 ~를 통해 ~에 기여하는 것입니다'와 같이 **질문에 대한 핵심 결론(Subject + Achievement + Goal)**으로 즉시 시작하세요.",
        "3. **서론 필터링**: '최근 사회 이슈는...', '이 기업은...', '현대 사회에서...' 등의 배경 설명이나 일반적인 서론을 **절대 쓰지 마세요.** 바로 본론(결론)부터 들어가는 것이 이 시스템의 핵심입니다.",
        "",
        "**[최우선 규칙 1: 가치 중심 소제목]**",
        "1. 추상적인 제목 금지: `[나의 성장 과정]`, `[최근 사회 이슈]` 등은 시스템 실패로 간주합니다.",
        "2. 본문의 핵심 인사이트나 기술적 가치를 요약한 명사형 제목을 지으세요.",
        "",
        "**[최우선 규칙 2: 소재 중복 및 분량]**",
        "1. 다른 문항(`others`)과 겹치는 소재는 절대 재사용하지 마세요.",
        "2. **{{minTarget}}자 ~ {{maxTarget}}자 사이를 반드시 맞추세요.** 소재가 부족하면 기술적 디테일(설계, 트레이드오프 등)을 깊게 파고들어 분량을 확보하세요.",
        "결과는 반드시 아래의 json 구조를 가진 객체여야 합니다: {\"text\": \"초안 내용...\"}"
    })
    @UserMessage("지원 회사: {{company}}\n직무: {{position}}\n질문: {{question}}\n**목표 분량: {{minTarget}}~{{maxTarget}}자** (제한: {{maxLength}}자)\n\n경험 데이터: {{context}}\n\n다른 문항 유의: {{others}}\n지시사항: {{directive}}\n\n**구조 주의**: `[소제목]`으로 시작하고, 첫 문장은 즉시 결론(두괄식)을 말할 것. **반드시 {{minTarget}}자 이상** 확보.")
    DraftResponse generateDraft(
            @V("company") String company,
            @V("position") String position,
            @V("question") String question,
            @V("maxLength") int maxLength,
            @V("minTarget") int minTarget,
            @V("maxTarget") int maxTarget,
            @V("context") String context,
            @V("others") String others,
            @V("directive") String directive);

    @SystemMessage({
        "당신은 구직자를 돕는 전문 자소서 어시스턴트입니다.",
        "**[Priority 0: structural compliance]**",
        "1. 수정 시에도 반드시 `[소제목]` 형식을 유지하세요 (마크다운 제목 금지).",
        "2. 첫 문장은 반드시 **두괄식 결론**이어야 합니다. 배경 설명을 모두 제거하세요.",
        "3. 제목은 더욱 구체적이고 가치 중심적인 제목으로 정제하세요.",
        "**[분량 사수]**: **{{minTarget}}자 이상의 분량을 반드시 유지**하세요.",
        "결과는 반드시 아래의 json 구조를 가진 객체여야 합니다: {\"text\": \"수정된 내용...\"}"
    })
    @UserMessage("기존 내용: {{input}}\n수정 지시: {{directive}}\n**목표 분량: {{minTarget}}~{{maxTarget}}자** (제한: {{maxLength}}자)\n\n경험 데이터: {{context}}\n문항 맥락(중복 금지): {{others}}\n\n제목을 `[]`로 정제하고, 본문 첫 문장을 두괄식으로 바꾸며, 전체 분량을 {{minTarget}}자 이상으로 늘려줘.")
    DraftResponse refineDraft(
            @V("company") String company,
            @V("position") String position,
            @V("input") String input,
            @V("maxLength") int maxLength,
            @V("minTarget") int minTarget,
            @V("maxTarget") int maxTarget,
            @V("context") String context,
            @V("others") String others,
            @V("directive") String directive);

    @SystemMessage({
        "당신은 IT 전문 번역 검수자이자 시니어 개발자입니다.",
        "**[최종 서식 및 두괄식 검역]**",
        "1. 결과물 맨 처음에 반드시 `[구체적 제목]`이 있어야 합니다. 마크다운 기호를 모두 제거하세요.",
        "2. 본문 첫 문장이 배경 설명이라면 즉시 삭제하고 **본인만의 결론(두괄식)**으로 대체하세요.",
        "3. `humanPatchedText`는 반드시 **{{minTarget}}자 이상**이어야 합니다.",
        "결과는 반드시 아래의 json 구조를 가진 객체여야 합니다: {\"summary\": \"...\", \"humanPatchedText\": \"...\", \"mistranslations\": [{\"original\": \"...\", \"translated\": \"...\", \"suggestion\": \"...\", \"severity\": \"low/high\", \"reason\": \"...\"}]}"
    })
    @UserMessage("원문: {{original}}\n세탁본: {{washed}}\n경험 데이터: {{context}}\n**최종 하한선: {{minTarget}}자** (최대: {{maxLength}}자)\n\n제목을 `[]`로 확정하고, 두괄식 문장으로 시작하며, 경험 데이터를 보충해 {{minTarget}}자를 넘겨줘.")
    DraftAnalysisResult analyzePatch(
            @V("original") String original, 
            @V("washed") String washed, 
            @V("maxLength") int maxLength,
            @V("minTarget") int minTarget,
            @V("context") String context);

    @SystemMessage({
        "당신은 채용 공고(JD) 분석 및 데이터 구조화 전문 AI 에이전트입니다.",
        "오타가 섞인 OCR 텍스트와 이미지를 대조하여 완벽하게 정제된 공고 데이터를 생성하세요.",
        "결과는 반드시 아래의 json 구조를 가진 객체여야 합니다: {\"companyName\": \"기업명\", \"position\": \"지원직무\", \"rawJd\": \"...\", \"aiInsight\": \"...\", \"extractedQuestions\": []}"
    })
    @UserMessage("OCR 추출 텍스트: {{ocrText}}\n이미지 정보를 참고하여 정제해줘.")
    JdAnalysisResponse analyzeJdWithOcr(@V("ocrText") String ocrText, @V("image") dev.langchain4j.data.message.ImageContent image);

    @SystemMessage({
        "당신은 채용 공고(JD) 분석 전문가입니다.",
        "제공된 채용 공고를 분석하여 기업명, 직무, 문항을 추출하세요.",
        "결과는 반드시 아래의 json 구조를 가진 객체여야 합니다: {\"companyName\": \"...\", \"position\": \"...\", \"rawJd\": \"...\", \"aiInsight\": \"...\", \"extractedQuestions\": []}"
    })
    @UserMessage("다음 채용 공고를 분석해줘:\n\n{{rawJd}}")
    JdAnalysisResponse analyzeJd(@V("rawJd") String rawJd);
}
