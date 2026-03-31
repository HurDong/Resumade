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
                This is a CULTURE_FIT question. The evaluator checks:
                1. BEHAVIORAL PROOF — not "저는 빠르게 실행합니다", but one real moment proving speed, ownership, or customer focus.
                2. EXECUTION WITH JUDGMENT — what was shipped quickly, what was intentionally simplified, and why.
                3. FEEDBACK LOOP — what signal validated the action? (user response, data, operations, conversion, retention, etc.)
                4. COMPANY FIT — why this way of working matches the target company's culture.

                Priority order:
                  [PRIMARY]  Baseline problem or hypothesis → fast action or experiment → measurable feedback
                  [SECONDARY] Trade-off awareness: what was intentionally not overbuilt
                  [TERTIARY]  Why this working style fits the target company and role
                </Question_Intent>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"text":"..."}
                2. Count ONLY characters inside "text" value.
                3. Never exceed maxLength. Never write below minTarget.
                4. [제목]: must name the concrete execution context or result, NOT [조직문화 적합성], [실행력], or [도전 정신].
                5. Avoid abstract praise of company culture unless it is tied to the applicant's real behavior.
                6. If metrics exist, use them. If not, use an operational or user-facing signal that is actually present in the context.
                7. Do NOT invent A/B tests, MVP launches, or customer feedback that are not in the supplied context.
                8. Keep the tone believable for a junior applicant: show ownership in a bounded scope, not exaggerated executive authority.
                9. Natural Korean narrative only. No bullet lists or parenthetical labels unless requested.
                </Strict_Rules>

                <Output_Format>
                Return ONLY: {"text": "[제목]\\n\\n본문..."}
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
                        {"text": "[전환율 2.1에서 3.4로, 3일 안에 MVP를 만들고 바로 검증한 경험]\\n\\n동아리 모집 페이지를 운영하던 당시, 방문자는 많은데 신청 전환율이 2.1 수준에서 정체된 문제가 있었습니다. 회의에서 완성된 기획안을 오래 다듬기보다, 핵심 가설부터 빠르게 검증하는 편이 맞다고 판단했습니다. 저는 소개 문구를 세 줄로 압축하고 신청 버튼 위치와 색상을 다시 설계한 MVP를 3일 안에 제작했습니다.\\n\\n이후 1주일 동안 유입 데이터를 비교하며 반응을 확인했고, 버튼 클릭률과 최종 신청 전환율이 모두 상승했습니다. 이 경험을 통해 빠른 실행은 대충 만드는 태도가 아니라, 가장 중요한 가설부터 먼저 검증하는 방식이라는 점을 배웠습니다. 토스에서도 같은 방식으로 사용자 반응을 기준 삼아 더 빠르게 개선하는 개발자로 기여하고 싶습니다."}
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
                {"text": "[제목]\\n\\n본문..."}
                - [제목]: names the concrete execution context or measurable shift
                - Show: baseline or hypothesis → fast action → feedback signal → why this matches the company
                - Natural Korean prose, not a culture essay or slogan list
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
