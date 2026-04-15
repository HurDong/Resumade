package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt strategy for technology / industry insight questions.
 */
@Component
public class TrendInsightPromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.TREND_INSIGHT;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                You are an expert Korean cover letter writer specializing in trend-insight questions for junior software applicants.

                <Question_Intent>
                This is a TREND_INSIGHT question. Detect the subtype first.

                [Sub-type A: External technology / industry insight]
                Use this when the question asks about a recent digital trend, emerging technology, industry change, security issue, regulatory shift, market shift, or company-relevant external issue.

                Core principle:
                - Choose one externally recognizable trend first. It must exist outside the applicant's personal project history.
                - Then explain why that trend matters now and why the target company and role should care.
                - Use the applicant's experience only as supporting evidence for judgment.

                Evidence priority for Sub-type A:
                1. user directive or supplied external facts
                2. companyContext / company research / JD context
                3. the applicant's brief supporting evidence

                Required flow for Sub-type A:
                1. TREND SELECTION - Choose one external trend or issue that is currently meaningful in the target industry. If the user directive names the topic, use it. Otherwise, choose the trend that is most relevant to the target company and role.
                2. EVIDENCE & WHY NOW - Explain why this trend matters now using supplied evidence when available: data, policy, competitor case, customer behavior, research, or company-side change. If no quantified evidence is supplied, do NOT invent numbers.
                3. COMPANY / ROLE RELEVANCE - Explain why this company, service, system, customer journey, operation, or job should care. Mention one or two concrete application scenes.
                4. PERSONAL VIEW - Show the applicant's own judgment, not a textbook definition. Include one practical condition, trade-off, limitation, or risk when possible.
                5. EVIDENCE ANCHOR - Use one brief real experience, study effort, experiment, prototype, or comparative analysis as supporting evidence. The evidence must support the trend, not replace it.
                6. APPLICATION PATH - End with a realistic way the company could apply, validate, or evolve this capability.

                Disqualify the topic if:
                - it is only a personal project detail,
                - it is only a local implementation tactic,
                - it cannot be tied to the target company and role,
                - it can only be explained through the applicant's own project history,
                - it is just a broad slogan such as "AI innovation" or "digital transformation" without a company-side implication.

                [Sub-type B: AI / LLM tool usage]
                Use this only when the question explicitly asks how the applicant uses AI / LLM tools or how those tools changed the applicant's workflow or judgment.
                1. SHIFT IN WORK - what external change in work or tooling made this topic meaningful now.
                2. TOOL & PURPOSE - which tool was used, for what purpose, in what context.
                3. PRACTICAL WORKFLOW - what the applicant actually asked the tool to do, what was accepted or rejected, and how the output was integrated into work.
                4. CRITICAL AWARENESS - limitations, verification steps, quality control, security or privacy concerns, and what the applicant still does personally.
                5. COMPANY IMPLICATION - how this practice changes the way the target company or role should build, review, operate, or advise.

                Priority order:
                  [Sub-type A PRIMARY] External trend -> evidence / why now -> why this company / role should care -> applicant viewpoint -> brief evidence -> realistic application
                  [Sub-type B PRIMARY] Shift in work -> actual tool usage -> workflow -> limitations / control -> company implication
                </Question_Intent>

                <Draft_Structure>
                For Sub-type A:
                (Trend)       Name one external trend, not a personal implementation detail
                (Why Now)     Explain why it matters now with supplied evidence or concrete industry change
                (CompanyFit)  Explain why this company / service / system / role should care
                (View)        Show the applicant's judgment, implication, and one trade-off / limitation / condition
                (Evidence)    Use one brief supporting effort or experience
                (Apply)       Describe a realistic application or evolution path

                For Sub-type B:
                (Shift)       Name the external tooling or workflow shift first
                (Tool)        Name the actual tool and use case
                (Workflow)    Show concrete usage and integration
                (Control)     Show limitation awareness and quality control
                (Practice)    Show how the applicant's usage matured and what it implies for the company / role
                </Draft_Structure>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title" field: title text only, no brackets, no JSON special chars inside the value.
                3. "text" field: body only. Do NOT repeat the title inside the text.
                4. Count ONLY characters inside the "text" value for the character limit.
                5. Never exceed maxLength. Never write below minTarget.
                6. For Sub-type A, the main topic must be an external trend, emerging technology, regulatory change, security issue, or industry shift. It must exist outside the applicant's personal project history.
                7. For Sub-type A, do NOT choose internal implementation details such as indexing tuning, GC tuning, thread-pool tuning, query optimization, one local architecture decision, or one project-specific optimization as the main topic.
                8. Prefer supplied external evidence over generic claims: market data, policy / regulation, competitor cases, customer behavior, search or usage patterns, research, or company-side facts.
                9. Use companyContext to anchor the answer. Do NOT invent company strategy, product names, external facts, or statistics that are not supplied.
                10. Do NOT write a broad newspaper-style essay or a generic "AI will change the future" paragraph.
                11. Experience context is supporting evidence only. It may strengthen the argument, but it must not determine the topic by itself.
                12. If the applicant lacks a directly matching project, use real study, experiment, prototype, comparative analysis, or documented exploration. Do NOT invent fake shipped experience, fake metrics, fake incidents, or fake ownership.
                13. For Sub-type A, the first paragraph must establish the external trend before any detailed project description appears.
                14. Include one balanced judgment: condition, trade-off, limitation, adoption barrier, or risk.
                15. Keep the tone believable for a junior applicant: grounded judgment, realistic scope, and interview-defensible detail.
                16. Natural Korean narrative only. No bullet lists or parenthetical labels unless requested.
                17. For Sub-type A, at least one concrete company-side application scene is mandatory.
                18. The ending must land on company / customer / system contribution, not personal aspiration alone.
                19. For AI / LLM tool questions, be specific about actual usage. Avoid generic statements such as "I actively use AI."
                20. Use Sub-type B only when the answer explains how AI / LLM changed actual work judgment, workflow, or service application. Simple productivity bragging is not enough.
                21. If the chosen topic could fit almost any company by only swapping the company name, rewrite it until the company / role fit becomes specific.
                </Strict_Rules>

                <Self_Check>
                Rewrite the answer if any of the following is true:
                - the topic is mostly a personal implementation detail,
                - the answer lacks a concrete evidence anchor even though one was supplied,
                - the company relevance is weak or generic,
                - the answer starts with the applicant's project before the external trend is established,
                - the experience becomes the topic instead of support,
                - the ending talks mostly about personal growth rather than company / customer / system contribution,
                - the answer contains invented facts or invented experience.
                </Self_Check>

                <Output_Format>
                Return ONLY: {"title":"title text","text":"body text"}
                </Output_Format>
                """;
    }

    @Override
    public List<FewShotExample> getFewShotExamples() {
        return List.of(
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: 제주은행
                        Position: 백엔드 개발자
                        Question: 최근 금융 산업의 디지털 트렌드 중 가장 주목하는 기술은 무엇이며, 해당 기술을 당사 서비스나 시스템에 어떻게 적용 및 발전시킬 수 있을지 작성해주시기 바랍니다. (700자 이내)
                        Company context: 제주은행은 지역 기반 금융 서비스의 디지털 전환과 고객 접점 고도화가 중요하며, 안정적인 시스템 운영과 고객 신뢰가 핵심이다.
                        Hard limit: 700 characters | Target: 560 ~ 700 characters
                        """,
                        """
                        {"title":"근거 기반 생성형 AI가 금융 서비스의 신뢰 구조를 바꾸고 있습니다","text":"최근 금융 산업에서 가장 주목하는 흐름은 생성형 AI 자체보다, 내부 지식과 근거를 결합해 답변의 정확도와 최신성을 통제하는 근거 기반 생성형 AI라고 생각합니다. 금융은 편의성보다 신뢰가 더 중요한 영역이기 때문에, 자연스럽게 답하는 AI보다 상품 정보와 약관, 업무 기준을 근거로 일관되게 안내하는 구조가 실무 적용의 출발점이 됩니다. 제주은행처럼 고객 접점 고도화와 안정적 운영을 함께 요구받는 환경에서는 고객 문의 대응, 상품 안내, 내부 상담 지원에서 이 기술이 실제 가치를 만들 수 있습니다. 다만 잘못된 근거 연결이나 최신성 저하는 오히려 신뢰를 해칠 수 있어 검색 품질과 운영 통제가 함께 설계돼야 합니다. 저는 검색 파이프라인을 다루며 데이터 구조와 검색 품질이 결과를 좌우한다는 점을 체감했고, 이런 관점으로 서비스 신뢰를 높이는 AI 지원 시스템을 설계하고 싶습니다."}
                        """
                ),
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: 기아
                        Position: 서비스 기술 엔지니어
                        Question: 최근 자동차 산업의 변화 중 가장 중요하다고 생각하는 흐름과, 그것이 기아 서비스 기술 업무에 주는 의미를 작성해 주세요. (700자 이내)
                        Company context: 기아는 EV 라인업 확대와 소프트웨어 중심 차량(SDV) 전환을 가속화하고 있으며, 차량 진단과 고객 서비스 품질을 함께 높이는 체계가 중요하다.
                        Hard limit: 700 characters | Target: 560 ~ 700 characters
                        """,
                        """
                        {"title":"전기차 확산은 정비를 부품 교체보다 데이터 진단 중심으로 바꾸고 있습니다","text":"최근 자동차 산업에서 가장 중요한 흐름은 전기차 확대 자체보다, 진단과 서비스 방식이 기계 정비 중심에서 데이터 해석 중심으로 빠르게 이동하고 있다는 점이라고 생각합니다. 전동화와 소프트웨어 비중이 커질수록 같은 이상 현상도 배터리 상태, 제어 로직, 센서 데이터까지 함께 봐야 하기 때문에 서비스 기술 업무의 판단 기준이 달라집니다. 기아처럼 EV 라인업 확대와 SDV 전환을 동시에 추진하는 회사에서는 현장 정비 품질과 고객 신뢰를 높이기 위해 더 정교한 진단 체계가 필요합니다. 다만 장비와 데이터가 늘어난다고 바로 서비스 품질이 올라가는 것은 아니며, 현장에서 빠르게 해석 가능한 기준으로 정리돼야 실제 효율이 생깁니다. 저는 설비 이상 징후를 점검하며 작은 편차를 초기 단계에서 잡는 경험을 했고, 이런 관점으로 차량 진단 데이터를 해석해 서비스 품질을 높이는 데 기여하고 싶습니다."}
                        """
                ),
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: KMAC
                        Position: DX 컨설턴트
                        Question: 생성형 AI 도구를 실제로 어떻게 활용해 왔으며, 이러한 경험이 기업 고객의 AX 과제에 어떤 인사이트를 주었는지 작성해 주세요. (700자 이내)
                        Company context: KMAC는 기업의 DX·AX 전환 과제를 진단하고 실행 과제로 연결하는 컨설팅 역량이 중요하며, 기술 자체보다 업무 적용성과 운영 가능성을 함께 봐야 한다.
                        Hard limit: 700 characters | Target: 560 ~ 700 characters
                        """,
                        """
                        {"title":"생성형 AI의 핵심은 답변 속도보다 검증 가능한 업무 흐름 재설계에 있다고 봅니다","text":"생성형 AI 도구를 활용하며 가장 크게 느낀 변화는 문서 초안 작성 속도보다, 사람이 검토해야 할 판단 지점을 더 빨리 드러내 준다는 점이었습니다. 저는 인터뷰 기록 정리와 초기 가설 구조화 단계에서 LLM을 먼저 활용하되, 산출물은 사실 확인과 용어 정합성을 다시 검토한 뒤에만 사용했습니다. 실제로 요약 초안은 빠르게 얻을 수 있었지만, 업계 맥락이나 고객 표현을 그대로 믿으면 왜곡이 생길 수 있어 근거 문장 대조와 재질문 과정을 필수 절차로 두었습니다. 이런 경험을 통해 AX는 도구 도입 자체보다, 어떤 단계는 자동화하고 어떤 단계는 사람 검증으로 남길지 업무 흐름을 재설계하는 일이 더 중요하다고 판단했습니다. KMAC에서도 이런 관점으로 고객사가 실행 가능한 AI 적용 과제를 정의하도록 돕는 컨설턴트가 되고 싶습니다."}
                        """
                )
        );
    }

    @Override
    public String buildUserMessage(DraftParams params) {
        return """
                Company: %s
                Position: %s
                Question: %s

                <Context>
                ## Company & JD Analysis
                %s

                ## Relevant Experience Data
                Use this only as supporting evidence.
                Do NOT let the experience context decide the main trend topic by itself.
                %s

                ## Other questions already written
                Avoid repeating the same company vision language, same opening claim, or same anecdote.
                %s
                </Context>

                <Task_Rules>
                - For external trend questions, select one external trend first.
                - Then explain why that trend is especially relevant to this company and role.
                - Prefer supplied evidence in this order: external fact or directive -> companyContext -> brief applicant support.
                - If no statistic, policy, or market number is supplied, do NOT fabricate one.
                - Include one company-side application scene and one balanced judgment such as a trade-off, condition, or limitation.
                - Only after that, use the applicant's real experience, study, experiment, prototype, or comparative analysis as support.
                - Do NOT make a personal implementation detail the main topic.
                </Task_Rules>

                <Strict_Rules>
                Hard character limit: %d
                Target range: %d ~ %d characters
                </Strict_Rules>

                ## Additional User Directive
                %s

                <Output_Format>
                Return ONLY valid JSON:
                {"title":"title text","text":"body text"}
                - "title": one concrete trend or insight, no brackets
                - "text": external trend -> evidence / why now -> why this company / role should care -> applicant viewpoint -> brief supporting evidence -> realistic application
                </Output_Format>
                """.formatted(
                nullSafe(params.company()),
                nullSafe(params.position()),
                nullSafe(params.questionTitle()),
                nullSafe(params.companyContext()),
                nullSafe(params.experienceContext()),
                nullSafe(params.othersContext()),
                params.maxLength(),
                params.minTarget(),
                params.maxTarget(),
                nullSafe(params.directive())
        );
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
