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
                Your primary goal is to reveal the applicant's diagnostic depth and decision-making quality.

                <Question_Analysis>
                Before writing a single word, analyze the question text carefully:

                STEP 1 — QUESTION DEMAND
                What is the question explicitly asking for? Identify the core ask in one sentence.

                STEP 2 — CONCLUSION TYPE
                Scan the question for contribution/application signals:
                  • Contribution signals: "어떻게 기여", "직무에서 어떻게 활용", "입사 후", "어떻게 쓰일지", "무엇을 할 수 있는지"
                  • If signals present → Type C (Contribution): end with 1-2 sentences naming a SPECIFIC problem or challenge you would address in this role. Never generic.
                  • If signals absent → Type R (Reflection): end with what you learned from this problem-solving experience and how it shaped your diagnostic approach. Do NOT add contribution language.

                STEP 3 — TITLE ANCHOR
                The title must answer: "What does this answer prove about me, given WHAT THIS QUESTION IS ASKING?"
                  ✗ Wrong: just naming the project or technology used
                  ✓ Right: frames the specific problem or challenge and how it was resolved — the evaluator can immediately ask a follow-up
                </Question_Analysis>

                <Question_Intent>
                This is a PROBLEM-SOLVING question. The evaluator measures:
                1. PROBLEM DIAGNOSIS — did the applicant identify the ROOT CAUSE, not just symptoms?
                2. ALTERNATIVE CONSIDERATION — were multiple solutions considered before choosing one?
                3. EXECUTION UNDER CONSTRAINT — time, resource, or organizational limitations?
                4. REFLECTION — what was learned, and how was it applied afterward?

                Priority order:
                  [PRIMARY]  Clear problem definition → root cause analysis → chosen solution with rationale
                  [SECONDARY] Concrete execution steps and outcome (ideally with metrics)
                  [TERTIARY]  Learning and how it shapes current approach
                </Question_Intent>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title" field: title text only — no brackets, no JSON special chars inside the value.
                3. "text" field: body only — do NOT repeat the title inside the text.
                4. Count ONLY characters inside "text" value for the character limit.
                5. Never exceed maxLength. Never write below minTarget.
                6. Title must name the problem or challenge specifically, NOT 도전 경험 or 실패 극복.
                7. The problem description must be specific enough for an interviewer to ask follow-up questions.
                8. Show the applicant's own judgment and responsibility — avoid passive constructions.
                9. If the challenge resulted in partial failure, be honest about it and emphasize the learning.
                10. Do NOT fabricate problem details or metrics not found in experience context.
                11. Write in the applicant's own reflective voice — not an evaluator's summarization.
                12. No parenthetical labels. No bullet enumerations unless explicitly requested.
                13. Keep the scope credible for a junior applicant: reveal depth of diagnosis and follow-through without overstating senior ownership.
                14. CRITICAL — The experience context may contain structural labels such as '문제:', '원인 진단:', '원인:', '조치:', '임시 조치:', '근본 조치:', '결과:', '배경:', '과정:' etc. These are INPUT METADATA ONLY. Do NOT reproduce any of these labels in the output. Convert every labeled segment into natural first-person Korean narrative seamlessly woven into the text (e.g., '문제: X였습니다' → '저는 X 상황을 마주했습니다').
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
                        Question: 예상치 못한 기술적 문제에 직면했을 때, 어떻게 해결했는지 구체적인 사례를 통해 설명해 주세요. (700자 이내)
                        Hard limit: 700 characters | Target: 560 ~ 700 characters
                        """,
                        """
                        {"title": "배포 직후 결제 실패율 8% 급증, 12시간 내 롤백 없이 핫픽스 완료", "text": "라이브 배포 3시간 후 결제 완료 API의 실패율이 갑자기 8%대로 치솟았습니다. 처음에는 새로 배포된 프로모션 로직을 의심했지만, 로그를 파고들자 실제 원인은 다른 곳에 있었습니다. Connection Pool의 소켓 재사용 시 TCP FIN_WAIT 상태가 누적되면서 DB 연결이 고갈되는 현상이었습니다.\\n\\n롤백은 배포 윈도우 정책상 불가했습니다. 저는 즉시 HikariCP 설정의 keepaliveTime 파라미터를 조정하는 핫픽스를 작성하고, 스테이징 환경에서 10분 내 재현 및 검증 후 긴급 배포를 진행했습니다. 결제 실패율은 30분 만에 0.3% 수준으로 복구됐습니다. 이 사건 이후 DB 연결 메트릭을 Grafana 대시보드에 추가하고 임계값 알림을 설정했습니다."}
                        """
                )
        );
    }

    @Override
    public String buildUserMessage(DraftParams params) {
        return """
                ## [STEP 1 — PRIMARY ANCHOR: Read and analyze this question before writing anything]
                Question (문항): %s

                Perform the Question_Analysis protocol above on this question now.
                Your title and body must answer what THIS question is asking.
                Experience data below is evidence — the question decides what that evidence must prove.

                Company: %s | Position: %s

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
                - "title": anchored to what the question is asking — frames the problem, not just the project name; no brackets
                - "text": natural first-person Korean narrative — weave Problem → Root Cause → Decision → Outcome → conclusion per Question_Analysis STEP 2
                </Output_Format>
                """.formatted(
                nullSafe(params.questionTitle()),
                nullSafe(params.company()),
                nullSafe(params.position()),
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
