package com.resumade.api.workspace.prompt;

import java.util.List;

/**
 * 문항 카테고리별 프롬프트 생성 전략 인터페이스.
 *
 * <h2>설계 원칙</h2>
 * <ul>
 *   <li>{@link #buildSystemPrompt()} — 카테고리 특화 시스템 프롬프트.
 *       {@code <Strict_Rules>}, {@code <Question_Intent>}, {@code <Output_Format>} XML 태그를 포함해야 합니다.</li>
 *   <li>{@link #getFewShotExamples()} — 합격 자소서 수준의 입출력 예시 쌍.
 *       PromptFactory가 {@code List<ChatMessage>}에 user/assistant 쌍으로 삽입합니다.</li>
 *   <li>{@link #buildUserMessage(DraftParams)} — 실제 생성 요청 메시지.
 *       RAG 경험 데이터는 {@code <Context>}, 제약 조건은 {@code <Strict_Rules>} 태그로 감쌉니다.</li>
 * </ul>
 */
public interface PromptStrategy {

    /**
     * 이 전략이 담당하는 문항 카테고리.
     */
    QuestionCategory getCategory();

    /**
     * 카테고리 특화 시스템 프롬프트.
     *
     * <p>반드시 아래 XML 구조를 포함해야 합니다:
     * <pre>{@code
     * <Question_Intent> ... </Question_Intent>
     * <Strict_Rules>    ... </Strict_Rules>
     * <Output_Format>   ... </Output_Format>
     * }</pre>
     */
    String buildSystemPrompt();

    /**
     * Few-shot 예시 쌍 목록.
     * 빈 리스트 반환 허용 (DEFAULT 전략 등에서 사용).
     */
    List<FewShotExample> getFewShotExamples();

    /**
     * 실제 생성 요청에 사용되는 유저 메시지 조립.
     *
     * <p>RAG context는 {@code <Context>} 태그로,
     * 글자 수 제약은 {@code <Strict_Rules>} 태그로 감싸야 합니다.
     *
     * @param params 초안 생성에 필요한 모든 파라미터
     * @return 완성된 유저 메시지 문자열
     */
    String buildUserMessage(DraftParams params);
}
