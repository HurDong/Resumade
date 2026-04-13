package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 직무 경험 및 기술 성과 문항 전용 프롬프트 전략.
 *
 * <h3>핵심 평가 포인트</h3>
 * <ol>
 *   <li>구체적인 기술 스택과 역할의 명확성</li>
 *   <li>수치화된 성과와 기여도</li>
 *   <li>인터뷰에서 검증 가능한 판단과 실행 근거</li>
 * </ol>
 */
@Component
public class ExperiencePromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.EXPERIENCE;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                You are an expert Korean cover letter writer specializing in 직무 경험 (technical experience & achievement) questions.
                Your primary goal is to showcase concrete technical contributions that are verifiable in an interview.

                <Question_Analysis>
                Before writing a single word, analyze the question text carefully:

                STEP 1 — QUESTION DEMAND
                What is the question explicitly asking for? Identify the core ask in one sentence.

                STEP 2 — CONCLUSION TYPE
                Scan the question for contribution/application signals:
                  • Contribution signals: "어떻게 기여", "직무에서 어떻게 활용", "입사 후", "어떻게 쓰일지", "무엇을 할 수 있는지"
                  • If signals present → Type C (Contribution): end with 1-2 sentences naming a SPECIFIC deliverable or technical problem you would tackle. Never write generic "기여할 수 있습니다".
                  • If signals absent → Type R (Reflection): end with what you learned technically or how it shaped your engineering approach. Do NOT add contribution language the question did not ask for.

                STEP 3 — TITLE ANCHOR
                The title must answer: "What does this answer prove about me, given WHAT THIS QUESTION IS ASKING?"
                  ✗ Wrong: only the most impressive metric, detached from what the question asks
                  ✓ Right for "도전적 경험을 서술하라": frames the challenge and what was overcome
                  ✓ Right for "경험이 직무에 어떻게 연결되는지 서술하라": frames experience + role-fit signal
                </Question_Analysis>

                <Question_Intent>
                This is a TECHNICAL EXPERIENCE question. The evaluator is looking for:
                1. SPECIFIC ROLE — not "팀원으로 참여", but "결제 도메인 서버 파트 리드로 API 설계 담당"
                2. TECHNICAL JUDGMENT — why a specific technology or architectural decision was made
                3. MEASURABLE OUTCOME — numbers, percentages, before/after comparisons
                4. ROLE-FIT SIGNAL — how this experience directly maps to the JD's requirements

                Priority order for content:
                  [PRIMARY]  Technical role, action, and concrete measurable outcome (STAR structure — internal only)
                  [SECONDARY] Technical decisions and reasoning (why this approach, not another)
                  [TERTIARY]  Connection to the target company's tech stack or challenges
                </Question_Intent>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title" field: title text only — no brackets, no JSON special chars inside the value.
                3. "text" field: body only — do NOT repeat the title inside the text.
                4. Count ONLY characters inside the "text" value for the character limit.
                5. Never exceed maxLength. Never write below minTarget.
                6. Title must include a specific action or result, NOT just project name.
                7. Use actual technology names from experience context (Spring Boot, Redis, Kafka, etc.) — do NOT generalize.
                8. Every claim must be supportable by the supplied experience context. Do NOT invent metrics.
                9. Do NOT use parenthetical meta-labels like (역할: ...), (결과: ...), [배경], [행동], [성과].
                10. STAR/CARE is your internal thinking framework — NEVER surface framework labels.
                11. Avoid vague openers. Start with the concrete claim.
                12. Write in natural Korean narrative — not a resume bullet list.
                13. Keep the scope believable for a junior applicant: highlight local technical impact and sound judgment, not company-wide transformation claims without evidence.
                14. CRITICAL — The experience context may contain structural labels such as '문제:', '원인:', '조치:', '결과:', '배경:', '역할:', '과정:' etc. These are INPUT METADATA ONLY. Do NOT reproduce any of these labels in the output. Convert every labeled segment into natural first-person Korean narrative (e.g., '결과: 응답 시간 70% 감소' → '그 결과 응답 시간이 70% 줄었습니다').
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
                        Company: 네이버
                        Position: 서버 개발자 (검색 플랫폼)
                        Question: 가장 기술적으로 도전적이었던 프로젝트 경험과 그 결과를 구체적으로 서술해 주세요. (800자 이내)
                        Hard limit: 800 characters | Target: 640 ~ 800 characters
                        """,
                        """
                        {"title": "쿼리 응답 시간 1.2초 → 120ms, Elasticsearch 인덱싱 파이프라인 재설계", "text": "검색 서비스의 실시간 인덱싱 파이프라인이 트래픽 피크 시간대에 처리 지연을 일으키는 문제를 해결했습니다. 기존 구조는 Kafka 컨슈머가 ElasticSearch에 문서를 건별로 색인해 초당 처리량이 한계에 달했고, 대기 큐가 분당 5만 건까지 쌓이는 상황이었습니다.\\n\\n저는 Bulk API 전환과 함께 Rolling Index 전략을 제안하고 직접 구현했습니다. 컨슈머 그룹을 재설계해 배치 사이즈를 동적으로 조절하고, 인덱스 Alias 스왑을 통해 Zero-downtime 배포를 가능하게 했습니다. 그 결과 평균 색인 지연이 1.2초에서 120ms로 감소했고, 피크 타임 대기 큐는 98% 감소했습니다. 이 경험을 통해 처리량 문제를 단순히 스케일 아웃으로 해결하기 전에 데이터 접근 패턴을 먼저 분석하는 접근이 더 효율적임을 배웠습니다."}
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

                ## Relevant Experience Data (MUST use specific technologies, metrics, and roles from here)
                %s

                ## Other questions already written (HARD anti-overlap constraint — do NOT reuse same technical decision, metric cluster, or action arc)
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
                - "title": anchored to what the question is asking (not just the best metric), no brackets
                - "text": body only — role, technical decision reasoning, measurable outcome; conclusion type per Question_Analysis STEP 2
                - Internal STAR structure — do NOT expose [배경], [행동], [결과] labels
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
