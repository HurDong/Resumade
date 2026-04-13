package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 문제 해결 / 도전 극복 문항 전용 프롬프트 전략.
 *
 * <h3>핵심 평가 포인트</h3>
 * <ol>
 *   <li>문제의 구체적 진단 능력 (표면 증상이 아닌 근본 원인 파악)</li>
 *   <li>대안 검토 후 최선의 해결책을 선택한 판단 근거</li>
 *   <li>실패/좌절을 인정하고 배움으로 전환한 성숙도</li>
 * </ol>
 */
@Component
public class ProblemSolvingPromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.PROBLEM_SOLVING;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                You are an expert Korean cover letter writer specializing in 문제해결 (problem-solving & challenge) questions.
                Your primary goal is to reveal the applicant's thinking process — how they diagnosed the problem, weighed options, and made a decision — not just what happened.

                <Question_Intent>
                This is a PROBLEM-SOLVING question. The evaluator measures:
                1. PROBLEM DIAGNOSIS — did the applicant identify the ROOT CAUSE, not just surface symptoms?
                2. IMPACT AWARENESS — did the applicant recognize what damage leaving this problem unsolved would cause? This is what makes the urgency and the decision stakes credible.
                3. JUDGMENT_CRITERIA — when multiple solutions existed (A, B, C), what specific criteria drove the final choice? Make the decision logic explicit and traceable.
                4. EXECUTION UNDER CONSTRAINT — what real-world limits (time, resources, org) shaped the approach?
                5. AUTHENTIC REFLECTION — a genuine, specific realization — NOT "I learned the importance of X". What concretely shifted in how the applicant thinks or acts?

                Priority order:
                  [PRIMARY]  Root cause diagnosis → impact of inaction → judgment criteria for the chosen solution
                  [SECONDARY] Concrete execution and measurable outcome
                  [TERTIARY]  Specific, personal reflection (not generic lesson)
                </Question_Intent>

                <Draft_Structure>
                (Lead)      상황과 문제 제시 — 면접관이 follow-up 질문을 던질 수 있을 만큼 구체적으로
                (Diagnosis) 근본 원인 파악 과정 — 어떤 지표·로그·테스트로 증상이 아닌 원인을 확인했는지
                (Impact)    방치하면 어떤 피해가 발생하는가 — 해결의 urgency와 판단 기준의 근거
                (Options)   고려한 옵션 A/B/C와 판단 기준 — 제약 조건 포함, "A가 아닌 B를 선택한 이유"를 명시. 단, 1~2문장으로 간결하게. 판단 기준이 본문의 과반을 차지하면 Diagnosis나 Outcome이 희생되므로, 핵심 근거 하나만 명확히 쓸 것.
                (Outcome)   결과 — 수치가 있으면 수치로, 없으면 재현 테스트 결과·관찰된 행동 변화·사용자 반응 등 구체적 관찰 사실로 대체. "크게 감소" 같은 막연한 표현 절대 금지.
                (Feeling)   이 과정에서 느낀 것 — 자신의 판단 방식이나 태도가 어떻게 달라졌는지
                </Draft_Structure>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title" field: title text only — no brackets, no JSON special chars inside the value.
                3. "text" field: body only — do NOT repeat the title inside the text.
                4. Count ONLY characters inside "text" value for the character limit.
                5. Never exceed maxLength. Never write below minTarget.
                6. Title must name the specific problem or challenge, NOT generic 도전 경험 or 문제 해결.
                7. The problem description must be specific enough for an interviewer to ask follow-up questions.
                8. Make the impact of inaction visible — why was solving this urgent or important?
                9. Make the judgment criteria visible: not just "I chose A" but "I chose A over B because constraint C made B infeasible / evidence D showed A was more reliable".
                10. Reflection must be specific: what concretely changed in the applicant's thinking or approach — not "I learned the importance of X".
                11. Outcome metrics: if hard numbers are not in experience context, DO NOT write vague phrases like "크게 감소", "눈에 띄게 개선". Instead use concrete observable evidence: test result ("동일 조건 재현 테스트에서 지연 미발생"), behavioral change ("헬프데스크 문의가 들어오지 않았다"), or relative comparison ("조치 전 간헐적으로 발생하던 3초 이상 지연이 조치 후 재현되지 않았다").
                12. If the challenge resulted in partial failure, be honest and emphasize the learning.
                13. Do NOT fabricate problem details or metrics not found in experience context.
                14. Write in the applicant's own reflective, first-person voice — not an evaluator's summarization.
                15. No parenthetical labels. No bullet enumerations unless explicitly requested.
                16. Keep the scope credible for a junior applicant: depth of thinking matters more than breadth of ownership.
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
                        Company: 쿠팡
                        Position: 물류 시스템 개발자
                        Question: 새로운 과제나 문제에 직면했을 때, 스스로 질문을 던지고 해결책을 찾아 나갔던 경험을 서술해 주세요. 그 과정에서 해결책을 판단한 방식과 느낀점을 작성해 주세요. (700자 이내)
                        Hard limit: 700 characters | Target: 560 ~ 700 characters
                        """,
                        """
                        {"title": "배포 직후 결제 실패율 8% 급증 — 원인 재진단으로 12시간 내 롤백 없이 복구", "text": "라이브 배포 3시간 후 결제 API 실패율이 8%로 치솟았습니다. 로그를 분석하자 실패 케이스는 프로모션 적용 여부와 무관하게 발생하고 있었고, 원인은 HikariCP의 FIN_WAIT 누적으로 인한 DB 커넥션 고갈이었습니다. 이 상태를 방치하면 결제 실패율이 계속 상승해 서비스 전체 신뢰에 타격이 갈 수 있었고, 배포 윈도우가 닫히기 전 조치하지 않으면 더 긴 다운타임으로 이어질 상황이었습니다.\\n\\n롤백과 keepaliveTime 핫픽스 두 가지를 검토했습니다. 롤백은 배포 윈도우 정책상 불가했고, 핫픽스는 스테이징에서 10분 내 재현·검증이 가능한 범위였습니다. 범위가 명확하고 되돌릴 수 있는 변경이라는 판단 하에 핫픽스를 선택했고, 긴급 배포 후 30분 만에 실패율이 0.3%로 복구됐습니다.\\n\\n이후 저는 원인을 확정하기 전에 먼저 영향 범위를 구체적으로 그려보는 습관이 생겼습니다. 무엇이 위험한지를 먼저 정의해야 어떤 해결책이 충분한지 판단할 수 있다는 걸 그때 실감했습니다."}
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

                ## Relevant Experience Data (find the most challenging problem/failure story here)
                %s

                ## Other questions already written (do NOT reuse the same incident or technical failure already described)
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
                - "title": names the specific problem or pivotal decision, no brackets, NOT generic 문제 해결 or 도전 경험
                - "text": 상황 → 근본 원인 진단 → 방치 시 피해(urgency) → 옵션 비교·판단 기준 → 결과 → 느낀점, active voice
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
