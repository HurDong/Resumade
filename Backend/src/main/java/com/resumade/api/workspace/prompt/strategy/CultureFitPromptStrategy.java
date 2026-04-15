package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 조직문화 적합성 / 가치관 / 장단점 / 고객 중심 / 실행 방식 문항 전용 프롬프트 전략.
 */
@Component
public class CultureFitPromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.CULTURE_FIT;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                You are an expert Korean cover letter writer specializing in culture-fit questions.

                <Question_Intent>
                This is a CULTURE_FIT question. This category covers TWO sub-types:

                [Sub-type A: 조직문화/가치/일하는 방식 적합성]
                When the question asks about customer focus, ownership, execution speed, communication style, or how the applicant matches the company's values:
                1. COMPANY VALUE ANCHOR — identify one value, people principle, or working style from companyContext.
                2. BEHAVIORAL PROOF — not "저는 고객 중심입니다", but one real episode proving customer focus, ownership, communication, or execution style.
                3. TEAM OR CUSTOMER IMPACT — show what changed for the team, customer, user, or operation.
                4. COMPANY FIT — explain why this repeated behavior pattern matches the target company's culture and role.

                [Sub-type B: 성격 장단점/가치관/일하는 스타일]
                When the question asks about personality strengths/weaknesses, values, or work style:
                1. TRAIT DEFINITION — name the specific trait (strength or weakness), not a generic label.
                2. PROJECT EVIDENCE — show how this trait manifested in a real team project experience.
                   - For strengths: how was it actively utilized and what result did it produce?
                   - For weaknesses: how was it recognized, what concrete steps were taken to improve, and how has the improved behavior been applied in subsequent projects?
                3. GROWTH ARC — for weaknesses, the before/after behavioral change must be visible. For strengths, show deliberate leverage rather than passive possession.
                4. TEAM IMPACT — the trait's effect on team dynamics or project outcomes, not just personal satisfaction.

                Priority order:
                  [Sub-type A]
                    [PRIMARY]  Company value or working principle → one bounded behavior episode → team/customer impact
                    [SECONDARY] Judgment, feedback, or trade-off awareness from that episode
                    [TERTIARY]  Why this way of working fits the target company and role
                  [Sub-type B]
                    [PRIMARY]  Trait → project experience where it played out → concrete result or change
                    [SECONDARY] Growth arc (especially for weaknesses): recognized → improved → applied
                    [TERTIARY]  Connection to how this trait serves the target company and role
                </Question_Intent>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title" field: title text only — no brackets, no JSON special chars inside the value.
                3. "text" field: body only — do NOT repeat the title inside the text.
                4. Count ONLY characters inside "text" value for the character limit.
                5. Never exceed maxLength. Never write below minTarget.
                6. Title must name the specific trait, behavior context, or behavioral shift, NOT 조직문화 적합성, 성격의 장단점, 실행력, 고객 중심, 오너십, 열정.
                7. Avoid abstract praise of company culture unless it is tied to the applicant's real behavior.
                8. If metrics exist, use them. If not, use another bounded signal that is actually present in the context: customer response, fewer conflicts, faster alignment, higher participation, clearer ownership, lower inquiry volume, or a before/after comparison.
                9. Do NOT invent A/B tests, MVP launches, customer feedback, or company values that are not in the supplied context.
                10. Keep the tone believable for a junior applicant: show ownership in a bounded scope, not exaggerated executive authority.
                11. Natural Korean narrative only. No bullet lists or parenthetical labels unless requested.
                12. For personality weakness questions: the weakness must be REAL and specific (not a disguised strength like "너무 꼼꼼합니다"). Show honest recognition, concrete improvement action, and behavioral change evidence from a project.
                13. For personality strength questions: do NOT just declare the trait — show it in action within a team, customer, or organization context and the result it produced.
                14. The closing must connect the proven behavior pattern to how the applicant will work in this company or role. Do NOT end with empty promises such as "최선을 다하겠습니다" or "열심히 하겠습니다".
                </Strict_Rules>

                <Output_Format>
                Return ONLY: {"title": "제목 텍스트", "text": "본문..."}
                </Output_Format>
                """;
    }

    @Override
    public List<FewShotExample> getFewShotExamples() {
        return List.of(
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: 토스
                        Position: 프론트엔드 개발자
                        Question: 토스의 빠른 실행 문화와 잘 맞는다고 생각하는 이유를 구체적인 경험으로 설명해 주세요. (650자 이내)
                        Hard limit: 650 characters | Target: 520 ~ 650 characters
                        """,
                        """
                        {"title": "전환율 2.1에서 3.4로, 3일 안에 MVP를 만들고 바로 검증한 경험", "text": "동아리 모집 페이지를 운영하던 당시, 방문자는 많은데 신청 전환율이 2.1 수준에서 정체된 문제가 있었습니다. 회의에서 완성된 기획안을 오래 다듬기보다, 핵심 가설부터 빠르게 검증하는 편이 맞다고 판단했습니다. 저는 소개 문구를 세 줄로 압축하고 신청 버튼 위치와 색상을 다시 설계한 MVP를 3일 안에 제작했습니다.\\n\\n이후 1주일 동안 유입 데이터를 비교하며 반응을 확인했고, 버튼 클릭률과 최종 신청 전환율이 모두 상승했습니다. 이 경험을 통해 빠른 실행은 대충 만드는 태도가 아니라, 가장 중요한 가설부터 먼저 검증하는 방식이라는 점을 배웠습니다. 토스에서도 같은 방식으로 사용자 반응을 기준 삼아 더 빠르게 개선하는 개발자로 기여하고 싶습니다."}
                        """
                ),
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: BC카드
                        Position: 마케팅
                        Question: BC카드가 강조하는 고객 중심 관점과 잘 맞는 이유를 경험을 바탕으로 작성해 주세요. (650자 이내)
                        Hard limit: 650 characters | Target: 520 ~ 650 characters
                        """,
                        """
                        {"title": "고객 문의 42건을 다시 읽고 혜택 문구를 바꿔 전환율 9%p 높인 경험", "text": "카드 프로모션 페이지 운영 인턴으로 일할 때 가장 먼저 확인한 것은 내부 기획 의도가 아니라 고객이 실제로 어디에서 멈추는지였습니다. 당시 유입은 충분했지만 혜택 구조가 복잡해 신청 전환이 기대보다 낮았고, 문의 게시판에도 비슷한 질문이 반복되고 있었습니다. 저는 42건의 문의 내용을 유형별로 정리해 고객이 헷갈리는 표현을 먼저 찾았고, 이를 바탕으로 혜택 안내 문구와 FAQ 구조를 다시 설계했습니다.\\n\\n그 결과 주요 문의가 눈에 띄게 줄었고, 신청 전환율은 기존 대비 9%p 상승했습니다. 이 경험을 통해 고객 중심은 '고객이 중요하다'고 말하는 태도가 아니라, 고객이 실제로 이해하지 못한 지점을 먼저 고치는 행동이라는 점을 배웠습니다. BC카드에서도 같은 방식으로 고객 반응을 해석하고 더 이해하기 쉬운 경험을 만드는 데 기여할 수 있다고 생각합니다."}
                        """
                ),
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: 네이버클라우드
                        Position: HR
                        Question: 본인의 성격상 단점과 이를 보완해 온 과정을 구체적으로 작성해 주세요. (650자 이내)
                        Hard limit: 650 characters | Target: 520 ~ 650 characters
                        """,
                        """
                        {"title": "신중함 때문에 늦던 의사 표현, 회의 전 정리 습관으로 협업 속도 보완", "text": "제 단점은 판단을 충분히 검토하려다 회의에서 의견 제시가 늦어지는 점이었습니다. 팀 프로젝트 초반에는 다른 의견을 먼저 듣고 정리한 뒤 말하려는 습관 때문에, 제 생각이 맞더라도 공유 시점이 늦어 일정이 밀리는 경우가 있었습니다. 저는 이 문제를 그대로 두면 협업 속도에 부담이 된다고 판단해, 회의 전 아젠다별 쟁점과 제 의견, 대안 하나를 미리 정리하는 방식을 만들었습니다.\\n\\n이후 스탠드업 미팅에서 제 의견을 먼저 말하는 연습을 반복했고, 다음 프로젝트에서는 일정 조율과 역할 분담이 훨씬 빨라졌습니다. 팀원들도 논의가 명확해졌다고 평가했고, 누락되는 업무도 줄었습니다. 이 경험을 통해 단점을 숨기기보다 일하는 습관을 바꾸는 방식으로 보완해야 실제 협업 방식이 달라진다는 점을 배웠습니다. 네이버클라우드에서도 이런 방식으로 더 투명하고 빠르게 소통하는 구성원이 되고 싶습니다."}
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

                ## Relevant Experience Data (find value-fit, customer orientation, ownership, communication, weakness-improvement, trust-building, or bounded execution stories here)
                %s

                ## Other questions already written (avoid reusing the same behavior episode, trait framing, or metric cluster)
                %s
                </Context>

                <Strict_Rules>
                Hard character limit: %d
                Target range: %d ~ %d characters
                </Strict_Rules>

                <CultureFit_Checklist>
                - Identify one company value, working principle, or people keyword from companyContext when the question is about fit.
                - Use one bounded behavioral episode rather than a generic personality description.
                - Show what changed for the team, customer, user, or operation.
                - If this is a weakness question, show recognized weakness → improvement action → later application.
                - If this is a strength or value question, show deliberate use of the trait, not passive possession.
                - Use metrics if available; otherwise use another bounded signal such as lower inquiry volume, faster alignment, fewer conflicts, higher participation, better customer response, or a before/after comparison.
                - End with how this way of working fits the company's culture and the target role.
                - Do NOT praise the culture abstractly or use empty diligence language.
                </CultureFit_Checklist>

                ## Additional User Directive
                %s

                <Output_Format>
                Return ONLY valid JSON:
                {"title": "제목 텍스트", "text": "본문..."}
                - "title": names the specific trait, behavior context, or measurable shift, no brackets
                - "text": one value or trait → behavioral proof → team/customer impact → why this matches the company
                - For weakness questions, use: weakness recognition → improvement action → changed behavior → company fit
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
