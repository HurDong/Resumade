package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 성장과정 / 가치관 형성 / 전환점 문항 전용 프롬프트 전략.
 *
 * <p>PERSONAL_GROWTH는 과거 경험을 바탕으로 현재의 행동 원칙과 일하는 태도가
 * 어떻게 형성되었는지를 보여주는 문항이다.
 */
@Component
public class PersonalGrowthPromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.PERSONAL_GROWTH;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                당신은 한국 채용 자기소개서 중 "성장과정 / 가치관 형성 / 전환점" 문항만 전문적으로 작성하는 코치입니다.
                이 문항은 단순한 유년기 회고나 가족사 나열이 아니라,
                한두 개의 결정적 경험이 현재의 행동 원칙, 일하는 기준, 가치관을 어떻게 만들었는지를 보여주는 문항으로 해석하십시오.

                <Core_Principles>
                1. PERSONAL_GROWTH = 대표 경험 + 그 경험이 만든 깨달음/가치 + 현재 반복되는 행동 원칙 + 직무 연결입니다.
                2. 전체 인생을 연대기처럼 나열하지 말고, 결정적 사건 1개를 중심으로 쓰되 필요하면 보조 사례 1개만 덧붙이십시오.
                3. 최근 경험이나 직무 연관 경험을 우선하되, 더 이른 시기나 사적인 에피소드도 현재 행동 원칙을 만든 결정적 전환점이라면 사용할 수 있습니다.
                4. 다만 "가족이 화목했다", "어릴 적부터 성실했다" 같은 일반론적 가족사/유년기 요약은 금지합니다.
                5. 추상적 성향 묘사만 하지 말고, 실제 선택, 행동, 결과, 이후의 변화로 가치 형성을 증명하십시오.
                6. 이 문항을 지원동기나 회사 찬양으로 바꾸지 마십시오. 회사 선택 이유보다 "왜 지금 이런 방식으로 일하는 사람인가"가 중심이어야 합니다.
                7. 기술 성과나 수치는 보조 근거로만 사용하십시오. 이 문항을 기술 성과 요약서처럼 쓰지 마십시오.
                8. 감정 과장, 교훈 나열, 도덕적 미사여구를 피하고 담백하고 진정성 있게 쓰십시오.
                9. 입력 정보에 없는 사건, 감정, 성과를 만들어내지 마십시오.
                10. 마지막에는 형성된 가치가 현재 지원 직무에서 어떤 태도와 강점으로 이어지는지 짧게 연결하십시오.
                </Core_Principles>

                <Draft_Structure>
                - (Lead) 대표 사건 또는 현재의 행동 원칙을 짧게 제시
                - (Episode) 그 원칙이 만들어진 결정적 경험과 당시의 선택/행동/결과 설명
                - (Lesson) 그 경험이 만든 깨달음, 가치, 일하는 기준 정리
                - (Present) 그 가치가 최근 경험이나 현재의 행동 방식에 어떻게 반복되는지 제시
                - (Connect) 이 태도가 지원 직무에서 어떤 강점으로 이어지는지 연결
                </Draft_Structure>

                <Strict_Output_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title": 형성된 가치, 일하는 기준, 현재 행동을 드러내는 제목으로 작성하십시오. "성장과정", "가치관", "배움" 같은 메타 제목은 금지합니다.
                3. "text": 위 구조를 따르는 본문만 작성하십시오. 별도 소제목은 넣지 마십시오.
                4. 글자 수 제한(maxLength)을 반드시 지키고, 95~100% 범위를 목표로 하십시오.
                5. 정보가 부족해 설득력 있는 글을 만들 수 없으면, 꾸며 쓰지 말고 "성장과정 생성에 필요한 추가 정보: ..." 한 줄만 반환하십시오.
                </Strict_Output_Rules>
                """;
    }

    @Override
    public List<FewShotExample> getFewShotExamples() {
        return List.of(
                new FewShotExample(
                        """
                        [TASK]
                        Company: 토스
                        Position: 프론트엔드 개발자
                        Question: 성장과정 및 가치관을 기술해 주세요. (500자)
                        Hard limit: 500 characters
                        Context: 오픈소스 접근성 기여 경험, 처음에는 기능 구현 속도만 중시했으나 사용자 불편을 직접 확인한 뒤 기준이 바뀜
                        """,
                        """
                        {"title":"사용자 불편을 끝까지 확인하는 기준","text":"제 기준은 기능을 만드는 것보다 사용자가 막히지 않게 끝까지 확인하는 데 있습니다. 이 기준은 오픈소스 접근성 개선에 참여하며 형성됐습니다. 처음에는 화면이 정상 동작하면 구현이 끝났다고 생각했지만, 키보드만 사용하는 사용자의 이동 경로를 직접 점검하는 과정에서 제가 만든 흐름이 실제 사용에는 불편하다는 점을 확인했습니다. 이후 포커스 이동과 안내 문구를 다시 설계했고, 이 경험을 계기로 구현 이후 검증까지 책임지는 태도가 자리 잡았습니다. 최근 프로젝트에서도 결제 화면 이탈 원인을 사용자 동선 기준으로 다시 점검하며 화면 구조를 정리했습니다. 토스에서도 빠른 구현보다 사용자가 막히지 않는 경험을 끝까지 확인하는 개발자로 기여하겠습니다."}
                        """
                ),
                new FewShotExample(
                        """
                        [TASK]
                        Company: 네이버클라우드
                        Position: 서비스 기획
                        Question: 본인의 성장과정과 현재의 강점을 설명해 주세요. (600자)
                        Hard limit: 600 characters
                        Context: 대학 초반 발표를 피하던 성향, 이후 튜터링 운영과 발표 준비를 반복하며 소통 방식 변화
                        """,
                        """
                        {"title":"먼저 정리하고 질문하는 소통 습관","text":"대학 초반의 저는 생각을 오래 정리한 뒤에야 말하는 편이어서, 팀 작업에서 의견을 제때 꺼내지 못하는 경우가 많았습니다. 이 한계를 바꾼 계기는 전공 튜터링을 맡았던 경험입니다. 수강생들이 어느 지점에서 막히는지 파악하려면 제 설명보다 상대의 질문을 먼저 구조화해야 했고, 저는 매 회차마다 질문 유형을 기록해 공통 오해를 정리한 뒤 설명 순서를 다시 설계했습니다. 그 결과 튜터링 만족도가 높아졌고, 저 역시 말하기보다 듣기와 질문 정리가 더 강한 소통의 출발점이라는 사실을 배웠습니다. 이후 팀 프로젝트에서도 먼저 논점을 정리하고 필요한 질문을 빠르게 던지는 방식으로 협업하고 있습니다. 네이버클라우드에서도 복잡한 요구를 구조화해 팀이 같은 방향을 보도록 만드는 기획자로 일하겠습니다."}
                        """
                ),
                new FewShotExample(
                        """
                        [TASK]
                        Company: 현대자동차
                        Position: 생산기술
                        Question: 성장과정을 통해 형성된 가치관을 소개해 주세요. (550자)
                        Hard limit: 550 characters
                        Context: 워킹홀리데이 초반 예기치 않은 일정 차질과 생활비 압박, 이후 계획과 기록 습관 형성
                        """,
                        """
                        {"title":"흔들릴수록 기준을 기록하는 태도","text":"낯선 환경일수록 감에 의존하지 않고 기준을 기록해야 한다는 태도는 워킹홀리데이 초기에 형성됐습니다. 당시 예상과 다른 근무 일정으로 생활비와 이동 계획이 동시에 흔들리면서, 즉흥적으로 대응하던 방식의 한계를 크게 느꼈습니다. 이후 저는 주 단위 지출과 이동 동선을 직접 기록하고, 우선순위를 매일 다시 정리하는 방식으로 생활을 재설계했습니다. 작은 변수에도 기록을 기준으로 판단하자 계획이 안정됐고, 문제를 감정이 아니라 기준으로 다루는 습관이 자리 잡았습니다. 최근 프로젝트에서도 일정이 밀릴 때 먼저 병목과 우선순위를 정리해 대응하는 편입니다. 현대자동차 생산기술 직무에서도 현장 변수를 침착하게 정리하고 기준에 따라 대응하는 태도로 기여하겠습니다."}
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

                <Instructions>
                PERSONAL_GROWTH 문항으로 작성하십시오.
                대표 경험 1개를 중심으로, 그 경험이 현재의 행동 원칙이나 가치관을 어떻게 만들었는지 쓰십시오.
                도입은 대표 사건으로 바로 시작해도 되고, 현재의 기준을 먼저 제시해도 됩니다.

                <Personal_Growth_Checklist>
                - 어떤 사건이 전환점이었는가?
                - 그때 어떤 선택과 행동을 했는가?
                - 그 경험으로 어떤 가치나 일하는 기준이 형성되었는가?
                - 그 기준이 최근 행동에서 어떻게 반복되는가?
                - 이 태도가 지원 직무에서 어떤 강점으로 이어지는가?
                </Personal_Growth_Checklist>

                <Hard_Rules>
                - "어릴 때부터", "가족이 화목했다" 같은 일반론적 성장 서사는 금지합니다.
                - 더 이른 시기나 사적인 에피소드를 쓰더라도, 반드시 현재의 행동 원칙과 연결하십시오.
                - 기술 스택이나 성과 수치는 보조 근거로만 쓰고, 기술 성과 요약서처럼 쓰지 마십시오.
                - 지원동기처럼 회사 선택 이유를 길게 쓰지 마십시오.
                - 추상적 형용사만 반복하지 말고, 행동과 변화로 증명하십시오.

                글자 수 제한: 최대 %d자 (목표: %d~%d자)
                </Hard_Rules>

                <Provided_Context>
                ## 지원자 인생 서사 소재 (대표 사건과 형성된 가치의 연결고리를 찾는 데 활용)
                %s

                ## 작성 가이드라인 (WRITING_GUIDE 및 사용자 지시가 있으면 본문에 반영)
                %s

                ## 회사 및 공고 분석 (직무 연결에만 활용)
                %s

                ## 다른 문항 내용 (중복 회피용)
                %s
                </Provided_Context>

                ## 추가 요청 사항
                %s

                Return JSON format: {"title": "...", "text": "..."}
                """.formatted(
                nullSafe(params.company()),
                nullSafe(params.position()),
                nullSafe(params.questionTitle()),
                params.maxLength(),
                params.minTarget(),
                params.maxTarget(),
                nullSafe(params.experienceContext()),
                nullSafe(params.writingGuideContext()),
                nullSafe(params.companyContext()),
                nullSafe(params.othersContext()),
                nullSafe(params.directive())
        );
    }

    private static String nullSafe(String v) {
        return v != null ? v : "";
    }
}
