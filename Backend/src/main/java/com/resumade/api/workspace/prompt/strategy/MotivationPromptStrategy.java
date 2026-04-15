package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 지원동기 / 입사 이유 문항 전용 프롬프트 전략.
 *
 * <h3>핵심 평가 포인트</h3>
 * 채용 담당자가 이 유형 문항에서 확인하는 것:
 * <ol>
 *   <li><b>Why This Company</b> — 유사 경쟁사 대비 이 기업을 선택한 구체적 근거</li>
 *   <li><b>Why This Role</b>    — 지원자의 경험 arc와 해당 직무의 자연스러운 연결</li>
 *   <li><b>Why Now</b>          — 이 시점에 이 기업이 최선의 커리어 선택인 이유</li>
 * </ol>
 */
@Component
public class MotivationPromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.MOTIVATION;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                You are an expert Korean cover letter writer specializing in 지원동기 (motivation/why-this-company) questions.
                Your primary goal is to craft a highly convincing, human-sounding answer that passes AI-detection screening.

                <Question_Intent>
                This is a MOTIVATION question. The evaluator wants evidence for:
                1. WHY specifically THIS company — business direction, operating principle, product/service, industry issue, value, or capability the company is building.
                   Generic phrases like "글로벌 기업" or "성장 가능성" are red flags.
                2. COMPANY KNOWLEDGE — demonstrate the applicant actually researched the company by using one company-specific anchor from companyContext.
                   The anchor may be a strategic direction, operating principle, customer problem, value, product/service, or industry response directly tied to the role.
                3. WHY specifically THIS role — how one or two past experiences, certifications, or outcomes make this role the applicant's logical next step.
                4. WHY NOW — explain what changed or accumulated so that applying now is credible: readiness, solved capability gap, clearer role direction, or company/industry timing.
                5. FUTURE CONTRIBUTION — present a concrete early contribution plan in believable junior scope. Prefer near-term execution over vague long-term ambition.

                Priority order for content:
                  [PRIMARY]  Company-choice logic + company-specific anchor + timing rationale
                  [SECONDARY] Role-fit proof from relevant evidence
                  [TERTIARY]  Near-term contribution plan aligned with company direction
                </Question_Intent>

                <Draft_Structure>
                (Opening)   첫 1~2문장 안에 왜 이 회사와 직무가 지금의 다음 단계인지 결론형으로 제시
                (Anchor)    companyContext에서 회사 고유 근거 1개를 골라 직무와 연결
                (Proof)     experienceContext에서 1~2개의 근거만 골라 준비성을 증명
                (Why_Now)   왜 하필 지금 지원하는지: 준비 완료, 경험 축적, 산업/회사 변화 중 하나 이상으로 설명
                (Closing)   입사 후 1~3년 내 실행 가능한 기여 계획으로 마무리. 장기 비전은 질문이 요구하거나 글자 수가 넉넉할 때만 덧붙임
                </Draft_Structure>

                <Strict_Rules>
                1. Return ONLY valid JSON with shape: {"title":"...","text":"..."}
                2. "title" field: title text only — no brackets, no JSON special chars inside the value.
                3. "text" field: body only — do NOT repeat the title inside the text.
                4. Count ONLY the characters inside the "text" value. Never count JSON braces, keys, quotes, or the title field.
                5. Never exceed maxLength. Never write below minTarget unless the hard limit physically prevents it.
                6. Title must be concrete and specific, NOT generic labels like 성장, 열정, 지원동기.
                7. Title must NOT: summarize the question, repeat company/position name, or use vague nouns.
                8. Within the first 1~2 sentences, answer why this company and role make sense now in a conclusion-first style.
                9. Use at least one company-specific anchor from companyContext. It does NOT have to be a recent launch only.
                   It may be a business direction, operating principle, value, customer problem, product/service, or industry response that is explicitly present in companyContext.
                10. Do NOT invent company details, products, technologies, values, or industry facts not found in companyContext.
                11. Use only 1~2 past evidence blocks. Motivation answers are not autobiography, project retrospectives, or generic 성장 서사.
                12. If you open with a personal value or belief, connect it to the company by the second sentence.
                13. Do NOT use motivations based on salary, stability, welfare, brand prestige, or vague admiration.
                14. Avoid empty closings such as "최선을 다하겠습니다", "열심히 하겠습니다", or "회사 성장에 기여하겠습니다" without an execution path.
                15. Do NOT use ceremonial openings like "안녕하세요", "저는 ~에 지원하게 된", or similar.
                16. Do NOT write in bullet/list format unless explicitly requested.
                17. Write in natural Korean cover-letter prose — the applicant's own reflective voice.
                18. Keep the voice believable for a new-grad or junior applicant: emphasize grounded evidence, learning curve, and near-term contribution over grand strategic claims.
                </Strict_Rules>

                <Output_Format>
                Return ONLY this JSON shape, nothing else:
                {"title": "제목 텍스트", "text": "본문 내용..."}
                </Output_Format>
                """;
    }

    @Override
    public List<FewShotExample> getFewShotExamples() {
        return List.of(
                new FewShotExample(
                        // -- FEW-SHOT USER MESSAGE --
                        """
                        [EXAMPLE TASK]
                        Company: 카카오페이
                        Position: 백엔드 엔지니어 (결제 플랫폼)
                        Question: 카카오페이에 지원하게 된 동기와, 입사 후 3년 내 이루고 싶은 목표를 서술해 주세요. (700자 이내)
                        Company context: 카카오페이는 국내 최대 규모의 간편결제 플랫폼으로, 월 MAU 2,000만 명 이상의 트랜잭션 처리 시스템을 보유. 최근 MSA 전환 및 이벤트 소싱 도입을 통한 결제 안정성 고도화를 진행 중.
                        Hard limit: 700 characters | Target: 560 ~ 700 characters
                        """,
                        // -- FEW-SHOT ASSISTANT RESPONSE --
                        """
                        {"title": "정합성 문제를 설계 단계에서 줄여 온 경험을 결제 안정성 고도화로 잇고 싶습니다", "text": "카카오페이에 지원한 이유는 대규모 결제 인프라의 안정성을 가장 치열하게 다루는 환경에서, 제가 쌓아 온 정합성 설계 경험을 바로 확장할 수 있다고 판단했기 때문입니다. 주문 시스템 안정화 업무를 맡으며 초당 3,000건 수준의 트래픽에서도 중복 처리와 상태 불일치가 왜 사용자 신뢰를 무너뜨리는지 직접 겪었고, 그 경험이 결제 플랫폼 백엔드로 방향을 분명히 잡게 만들었습니다.\\n\\n카카오페이가 이벤트 소싱 기반 MSA 전환으로 결제 안정성 고도화를 추진하고 있다는 점은 제가 Outbox 패턴과 비동기 이벤트 흐름을 구현하며 더 깊이 다뤄 보고 싶었던 주제와 맞닿아 있습니다. 입사 후 초기에는 결제 도메인 이벤트 설계와 장애 대응 흐름을 빠르게 익혀 안정성 개선에 기여하고, 3년 안에는 피크 타임 결제 실패율을 낮추는 운영 개선 과제를 책임질 수 있는 엔지니어로 성장하겠습니다."}
                        """
                ),
                new FewShotExample(
                        // -- FEW-SHOT USER MESSAGE --
                        """
                        [EXAMPLE TASK]
                        Company: 토스뱅크
                        Position: iOS 개발자
                        Question: 왜 토스뱅크 iOS 개발자로 지원하셨나요? 본인의 경력과 연결지어 설명해주세요. (500자 이내)
                        Company context: 토스뱅크는 국내 최초 인터넷전문은행으로 서비스 단순화를 핵심 철학으로 삼음. iOS 앱 MAU 600만, SwiftUI 기반 전면 리팩토링 진행 중.
                        Hard limit: 500 characters | Target: 400 ~ 500 characters
                        """,
                        // -- FEW-SHOT ASSISTANT RESPONSE --
                        """
                        {"title": "복잡한 금융 흐름을 단순한 화면 경험으로 바꿔 온 준비가 토스뱅크와 맞닿아 있습니다", "text": "토스뱅크 iOS 개발자로 지원한 이유는 금융 사용자의 복잡한 판단 과정을 짧고 명확한 화면 흐름으로 바꾸는 일이 제 경험과 가장 자연스럽게 이어지는 다음 단계라고 판단했기 때문입니다. 투자 앱 포트폴리오 화면을 개편하며 정보 밀도를 낮추면서도 핵심 지표를 놓치지 않는 인터랙션을 설계했고, 그 과정에서 단순한 UX가 오히려 더 정교한 상태 관리와 구조화를 요구한다는 기준을 갖게 됐습니다.\\n\\n토스뱅크가 서비스 단순화를 핵심 철학으로 두고 SwiftUI 기반 리팩토링을 진행 중이라는 점은 제가 지금까지 쌓은 UI 구조화 경험을 제품 수준에서 확장해 볼 수 있는 이유입니다. 입사 후에는 금융 도메인 로직을 이해한 iOS 개발자로서 복잡한 업무를 직관적인 화면 흐름으로 정리하는 데 기여하겠습니다."}
                        """
                ),
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: CJ씨푸드
                        Position: 품질관리
                        Question: CJ씨푸드 품질관리 직무에 지원한 동기와 입사 후 목표를 기술해 주세요. (700자 이내)
                        Company context: CJ씨푸드는 HACCP 기반 품질 관리와 ESG 관점을 반영한 생산 체계 고도화를 추진하고 있으며, 글로벌 시장 대응을 위해 공정별 품질 기준과 점검 체계를 지속 강화 중.
                        Hard limit: 700 characters | Target: 560 ~ 700 characters
                        """,
                        """
                        {"title": "현장 점검 경험을 공정 예방형 품질 관리로 연결하고 싶습니다", "text": "CJ씨푸드에 지원한 이유는 품질을 사후 검사보다 공정 단계에서 예방해야 한다는 제가 가진 기준이, 회사가 강화하고 있는 HACCP 기반 품질 관리 방향과 정확히 맞닿아 있기 때문입니다. 군 복무 중 설비 점검과 이상 징후 대응을 맡으며 작은 편차를 초기에 잡는 체계가 전체 품질과 안전을 좌우한다는 사실을 배웠고, 그 경험이 식품 품질관리 직무로 시야를 분명히 넓혀 주었습니다.\\n\\nCJ씨푸드가 ESG 관점까지 반영해 생산 체계를 고도화하고 있다는 점은 단순 점검이 아니라 기준을 운영으로 정착시키는 역할의 중요성을 보여 줍니다. 입사 후 초기에는 공정별 점검 기준과 이탈 대응 프로세스를 빠르게 익혀 현장 실행력을 높이고, 이후에는 품질 데이터 축적과 체크리스트 개선을 통해 예방 중심의 품질 관리에 기여하겠습니다."}
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

                ## Relevant Experience Data (from personal history)
                %s

                ## Other questions already written (HARD anti-overlap constraint)
                %s
                </Context>

                <Strict_Rules>
                Hard character limit: %d
                Target range: %d ~ %d characters (count only the text value, not JSON)
                </Strict_Rules>

                <Motivation_Checklist>
                - Pick one company-specific anchor from companyContext and explain why it matters to this role.
                - Use one or two proof points from experienceContext only: project, internship, certification, field practice, quantified improvement, or validated result.
                - Explain why the applicant is applying NOW: readiness, experience accumulation, solved capability gap, or timely company/industry direction.
                - End with a concrete 1~3 year contribution plan. Add a longer horizon only if the question explicitly asks for it.
                - Do NOT center the answer on salary, stability, welfare, brand name, or vague admiration.
                - Do NOT let the past-experience section dominate the whole answer. The draft must move toward company-fit and contribution.
                </Motivation_Checklist>

                ## Additional User Directive
                %s

                <Output_Format>
                Return ONLY valid JSON:
                {"title": "제목 텍스트", "text": "본문..."}
                - "title": specific, action/result-grounded, NOT a generic label, no brackets
                - "text": within the first 1~2 sentences, answer why this company and role make sense now in natural Korean prose
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
