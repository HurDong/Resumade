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
                1. Return ONLY valid JSON: {"text":"..."}
                2. Count ONLY characters inside the "text" value.
                3. Never exceed maxLength. Never write below minTarget.
                4. Start with a bracketed [제목] — must include a specific action or result, NOT just project name.
                5. Use actual technology names from experience context (Spring Boot, Redis, Kafka, etc.) — do NOT generalize.
                6. Every claim must be supportable by the supplied experience context. Do NOT invent metrics.
                7. Do NOT use parenthetical meta-labels like (역할: ...), (결과: ...), [배경], [행동], [성과].
                8. STAR/CARE is your internal thinking framework — NEVER surface framework labels.
                9. Avoid vague openers. Start with the concrete claim.
                10. Write in natural Korean narrative — not a resume bullet list.
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
                        Company: 네이버
                        Position: 서버 개발자 (검색 플랫폼)
                        Question: 가장 기술적으로 도전적이었던 프로젝트 경험과 그 결과를 구체적으로 서술해 주세요. (800자 이내)
                        Hard limit: 800 characters | Target: 640 ~ 800 characters
                        """,
                        """
                        {"text": "[쿼리 응답 시간 1.2초 → 120ms, Elasticsearch 인덱싱 파이프라인 재설계]\\n\\n검색 서비스의 실시간 인덱싱 파이프라인이 트래픽 피크 시간대에 처리 지연을 일으키는 문제를 해결했습니다. 기존 구조는 Kafka 컨슈머가 ElasticSearch에 문서를 건별로 색인해 초당 처리량이 한계에 달했고, 대기 큐가 분당 5만 건까지 쌓이는 상황이었습니다.\\n\\n저는 Bulk API 전환과 함께 Rolling Index 전략을 제안하고 직접 구현했습니다. 컨슈머 그룹을 재설계해 배치 사이즈를 동적으로 조절하고, 인덱스 Alias 스왑을 통해 Zero-downtime 배포를 가능하게 했습니다. 그 결과 평균 색인 지연이 1.2초에서 120ms로 감소했고, 피크 타임 대기 큐는 98% 감소했습니다. 이 경험을 통해 처리량 문제를 단순히 스케일 아웃으로 해결하기 전에 데이터 접근 패턴을 먼저 분석하는 접근이 더 효율적임을 배웠습니다."}
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
                {"text": "[제목]\\n\\n본문..."}
                - [제목]: specific action + result (e.g., "[응답 시간 70%% 단축, Redis 캐시 레이어 설계]")
                - Include: role, technical decision reasoning, measurable outcome
                - Internal STAR structure — do NOT expose [배경], [행동], [결과] labels
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
