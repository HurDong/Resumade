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
                1. WHY specifically THIS company — product, culture, tech stack, business direction, or values.
                   Generic phrases like "글로벌 기업" or "성장 가능성" are red flags.
                2. COMPANY KNOWLEDGE — demonstrate the applicant actually researched the company: mention a recent product, service, or strategic move that is directly relevant to the target role.
                3. WHY specifically THIS role — how the applicant's past experience arc leads here naturally.
                4. WHY NOW — what the applicant plans to contribute in the first 2-3 years.

                Priority order for content:
                  [PRIMARY]  Company-fit narrative + demonstration of company knowledge (use companyContext heavily)
                  [SECONDARY] Applicant's relevant experience as proof of capability
                  [TERTIARY]  Future contribution and growth vision
                </Question_Intent>

                <Draft_Structure>
                (Lead)      기여 의지 — "A기업에서 B를 하고 싶어 지원했습니다" (결론 선행, 왜 이 회사인지 첫 문장에서 바로 답)
                (Knowledge) 기업 이해 — 최근 A기업이 직무와 밀접하게 연관된 서비스 C를 출시하거나 기술적 행보 D를 하고 있다는 것을 1~2문장으로 언급. companyContext에서 가장 직무 관련성이 높은 정보를 선택.
                (Proof)     경험 증명 — "저는 E한 경험을 하며 F한 역량을 갖추었습니다" — 기업이 필요로 하는 역량과 자연스럽게 연결
                (Vision)    미래 기여 — 입사 후 2~3년 내 구체적 기여 계획
                </Draft_Structure>

                <Strict_Rules>
                1. Return ONLY valid JSON with shape: {"title":"...","text":"..."}
                2. "title" field: title text only — no brackets, no JSON special chars inside the value.
                3. "text" field: body only — do NOT repeat the title inside the text.
                4. Count ONLY the characters inside the "text" value. Never count JSON braces, keys, quotes, or the title field.
                5. Never exceed maxLength. Never write below minTarget unless the hard limit physically prevents it.
                6. Title must be concrete and specific, NOT generic labels like 성장, 열정, 지원동기.
                7. Title must NOT: summarize the question, repeat company/position name, or use vague nouns.
                8. First sentence of text MUST answer "why this company" directly in a conclusion-first style.
                9. After the opening, include 1~2 sentences demonstrating concrete knowledge of the company's recent activities — a specific product, service launch, or strategic direction that connects to the target role. This shows the applicant did real research.
                10. Do NOT invent company details, products, or technologies not found in companyContext.
                11. Do NOT use ceremonial openings like "안녕하세요", "저는 ~에 지원하게 된", or similar.
                12. Do NOT write in bullet/list format unless explicitly requested.
                13. Write in natural Korean cover-letter prose — the applicant's own reflective voice.
                14. Keep the voice believable for a new-grad or junior applicant: emphasize grounded evidence, learning curve, and near-term contribution over grand strategic claims.
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
                        {"title": "트랜잭션 1억 건을 안전하게 처리하는 시스템을 직접 설계하고 싶습니다", "text": "카카오페이를 선택한 이유는 단순히 '결제 플랫폼'이 아니라, 수천만 명의 일상 금융 인프라를 책임지는 엔지니어링 도전이 있기 때문입니다. 이전 직장에서 초당 3,000건 수준의 주문 처리 시스템 안정화를 경험하면서, 진짜 대규모 트래픽 환경에서 지불 정합성 문제를 어떻게 설계 단계부터 해결하는지 더 깊이 배우고 싶다는 갈증이 생겼습니다.\\n\\n카카오페이가 현재 진행 중인 이벤트 소싱 기반 MSA 전환은 제가 사이드 프로젝트로 Outbox 패턴을 직접 구현하며 관심을 가져온 아키텍처입니다. 입사 후 1년 내에는 결제 도메인 이벤트 설계에 기여하고, 3년 내에는 피크 타임 결제 실패율 0.001% 이하를 유지하는 시스템 개선을 주도하는 엔지니어로 성장하고 싶습니다."}
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
                        {"title": "복잡한 금융 UX를 코드 한 줄로 단순하게 만드는 경험이 쌓인 곳", "text": "전 직장에서 투자 앱의 포트폴리오 화면을 개편하면서, '단순한 UX'가 얼마나 복잡한 코드 설계를 요구하는지 직접 경험했습니다. 토스뱅크가 복잡한 은행 업무를 직관적으로 풀어낸 방식은 그 접근법이 저와 같았습니다. SwiftUI 전환 프로젝트에 합류해, 복잡한 금융 도메인 로직을 깔끔한 선언형 UI로 연결하는 데 기여하고 싶습니다."}
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

                ## Additional User Directive
                %s

                <Output_Format>
                Return ONLY valid JSON:
                {"title": "제목 텍스트", "text": "본문..."}
                - "title": specific, action/result-grounded, NOT a generic label, no brackets
                - "text": first sentence answers "why this company" in conclusion-first style, natural Korean prose
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
