---
description: Synchronize Antigravity rules to Cursor rules
---
# Sync Rules Workflow

이 워크플로우는 사용자가 Antigravity 요원의 공식 룰북(`.agents/rules/workspace-rules.md`)을 수정했을 때, 타 AI 에디터(Cursor 등)가 바라보는 전역 룰북(`.cursor/rules/project.mdc`)으로 변경 사항을 완벽하게 덮어써서 동기화(Sync)하는 역할을 합니다.

## 실행 단계

1. `.agents/rules/workspace-rules.md` 파일의 전체 내용을 읽어옵니다. (view_file 도구 사용)
// turbo
2. 읽어온 파일의 내용에 어떠한 가공이나 수정도 가하지 않고 원문 그대로 `.cursor/rules/project.mdc` 파일에 덮어씁니다. (본 파일은 프로젝트 전역 룰이므로 시스템 프롬프트 및 `globs: *` 헤더가 포함되어 있어야 합니다)
3. 작업이 완료되면 "✅ 룰 동기화가 성공적으로 완료되었습니다!"라는 메시지와 함께 사용자에게 처리 결과를 보고합니다.
