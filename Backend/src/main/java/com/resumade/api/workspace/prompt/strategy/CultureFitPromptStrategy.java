package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 조직문화 적합성 / 빠른 실행 / 고객 중심 / 실험 문화 문항 전용 프롬프트 전략.
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
                You are an expert Korean cover letter writer specializing in culture-fit and execution questions for product companies.

                <Question_Intent>
                This is a CULTURE_FIT question. This category covers TWO sub-types:

                [Sub-type A: 실행력/문화 적합성]
                When the question asks about execution speed, MVP mindset, customer focus, or working style:
                1. BEHAVIORAL PROOF — not "저는 빠르게 실행합니다", but one real moment proving speed, ownership, or customer focus.
                2. EXECUTION WITH JUDGMENT — what was shipped quickly, what was intentionally simplified, and why.
                3. FEEDBACK LOOP — what signal validated the action? (user response, data, operations, conversion, retention, etc.)
                4. COMPANY FIT — why this way of working matches the target company's culture.

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
                    [PRIMARY]  Baseline problem or hypothesis → fast action or experiment → measurable feedback
                    [SECONDARY] Trade-off awareness: what was intentionally not overbuilt
                    [TERTIARY]  Why this working style fits the target company and role
                  [Sub-type B]
                    [PRIMARY]  Trait → project experience where it played out → concrete result or change
                    [SECONDARY] Growth arc (especially for weaknesses): recognized → improved → applied
                    [TERTIARY]  Connection to how this trait serves the target role
                </Question_Intent>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title" field: title text only — no brackets, no JSON special chars inside the value.
                3. "text" field: body only — do NOT repeat the title inside the text.
                4. Count ONLY characters inside "text" value for the character limit.
                5. Never exceed maxLength. Never write below minTarget.
                6. Title must name the concrete execution context or specific trait, NOT 조직문화 적합성, 실행력, 도전 정신, 성격의 장단점.
                7. Avoid abstract praise of company culture unless it is tied to the applicant's real behavior.
                8. If metrics exist, use them. If not, use an operational or user-facing signal that is actually present in the context.
                9. Do NOT invent A/B tests, MVP launches, or customer feedback that are not in the supplied context.
                10. Keep the tone believable for a junior applicant: show ownership in a bounded scope, not exaggerated executive authority.
                11. Natural Korean narrative only. No bullet lists or parenthetical labels unless requested.
                12. For personality weakness questions: the weakness must be REAL and specific (not a disguised strength like "너무 꼼꼼합니다"). Show honest recognition, concrete improvement action, and behavioral change evidence from a project.
                13. For personality strength questions: do NOT just declare the trait — show it in action within a team project and the result it produced.
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

                ## Relevant Experience Data (find fast execution, customer feedback, experiment, ownership, or MVP stories here)
                %s

                ## Other questions already written (avoid reusing the same execution story or the same metric cluster)
                %s
                </Context>

                <Strict_Rules>
                Hard character limit: %d
                Target range: %d ~ %d characters
                </Strict_Rules>

                ## Additional User Directive
                %s

                <Output_Format>
                Return ONLY valid JSON:
                {"title": "제목 텍스트", "text": "본문..."}
                - "title": names the concrete execution context or measurable shift, no brackets
                - "text": baseline or hypothesis → fast action → feedback signal → why this matches the company
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
