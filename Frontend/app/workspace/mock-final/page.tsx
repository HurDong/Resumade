"use client"

import { useMemo, useState } from "react"
import Link from "next/link"
import {
  ArrowRight,
  CheckCircle2,
  Copy,
  FileText,
  Highlighter,
  Lightbulb,
  Loader2,
  RefreshCw,
  ScanSearch,
  Sparkles,
  Wand2,
} from "lucide-react"
import { AppSidebar } from "@/components/app-sidebar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"

const originalDraft = `[결제 흐름의 병목을 안정성 과제로 바꾼 경험]
SSAFY 공통 프로젝트에서 결제 서버를 맡았을 때, 저는 기능 구현보다 먼저 실패 지점을 추적 가능한 구조로 바꾸는 데 집중했습니다. 당시 결제 승인과 재시도 로직이 한 서비스에 섞여 있어 장애가 나면 원인 파악에 시간이 오래 걸렸고, 운영 관점에서도 불안정했습니다.

저는 승인, 검증, 재시도 흐름을 역할별로 분리하고 트랜잭션 경계를 다시 설계했습니다. 또한 Redis 기반 임시 상태 저장과 로그 추적 포인트를 추가해 실패 요청이 어디에서 끊겼는지 바로 확인할 수 있게 했습니다. 그 결과 QA 단계에서 반복되던 결제 누락 이슈를 사전에 줄일 수 있었고, 팀원들도 장애 상황을 훨씬 빠르게 재현하고 대응할 수 있었습니다.

이 경험을 통해 저는 백엔드 개발에서 중요한 것은 단순한 정상 동작이 아니라, 문제가 생겼을 때 빠르게 원인을 좁히고 안정적으로 복구할 수 있는 구조를 만드는 일이라는 점을 배웠습니다.`

const washedDraft = `[장애 추적이 가능한 결제 흐름으로 안정성을 높인 경험]
SSAFY 공통 프로젝트에서 결제 서버를 담당했을 때 저는 기능 구현 자체보다 장애가 발생했을 때 빠르게 원인을 파악할 수 있는 구조를 만드는 일을 우선했습니다. 당시 결제 승인과 재시도 로직이 하나의 서비스 안에 혼재되어 있어 문제가 생기면 원인 파악과 대응 속도가 모두 느려지는 상태였습니다.

이 문제를 해결하기 위해 승인, 검증, 재시도 흐름을 역할 단위로 분리하고 트랜잭션 경계를 다시 설계했습니다. 또한 Redis 기반 임시 상태 저장과 로그 추적 지점을 추가해 실패한 요청이 어느 단계에서 끊겼는지 바로 확인할 수 있도록 정리했습니다. 그 결과 QA 단계에서 반복되던 결제 누락 이슈를 사전에 줄일 수 있었고, 팀원들도 장애 상황을 더 빠르게 재현하고 대응할 수 있었습니다.

이 경험은 제가 백엔드 개발에서 중요하게 생각하는 기준을 분명하게 만들었습니다. 정상적으로 동작하는 기능을 만드는 것에서 그치지 않고, 문제가 발생했을 때 원인을 빠르게 좁히고 안정적으로 복구할 수 있는 구조까지 설계하는 개발자가 되고자 합니다.`

const initialFinalText = `[결제 장애 원인 추적으로 운영 안정성을 끌어올린 경험]
SSAFY 공통 프로젝트에서 결제 서버를 맡았을 때 저는 기능 추가보다 장애 원인을 빠르게 좁힐 수 있는 구조를 만드는 데 먼저 집중했습니다. 당시 결제 승인과 재시도 로직이 한 서비스에 섞여 있어 문제가 생기면 로그를 여러 군데 뒤져야 했고, QA 단계에서도 결제 누락 이슈를 재현하는 데 시간이 오래 걸렸습니다.

저는 승인, 검증, 재시도 흐름을 역할별로 분리하고 트랜잭션 경계를 다시 설계했습니다. 여기에 Redis 기반 임시 상태 저장과 추적 로그 지점을 추가해 실패 요청이 어느 단계에서 끊겼는지 바로 확인할 수 있게 했습니다. 그 결과 반복되던 결제 누락 이슈를 사전에 줄였고, 팀원들도 장애 상황을 더 빠르게 재현하고 대응할 수 있었습니다.

이 경험 이후 저는 백엔드 개발을 단순히 기능이 동작하도록 만드는 일이 아니라, 운영 중 문제가 생겼을 때 빠르게 원인을 좁히고 복구 가능한 구조를 설계하는 일로 바라보고 있습니다.`

const directionTitle = "[장애 추적이 가능한 결제 흐름으로 안정성을 높인 경험]"

const finalTitleCandidates = [
  {
    title: "[결제 장애 원인 추적으로 운영 안정성을 끌어올린 경험]",
    reason: "최종본이 강조한 원인 추적과 운영 대응력을 가장 직접적으로 보여줍니다.",
  },
  {
    title: "[트랜잭션 경계 재설계로 결제 누락 이슈를 줄인 경험]",
    reason: "기술 액션은 분명하지만 운영 관점의 메시지는 조금 약합니다.",
  },
  {
    title: "[재현 가능한 장애 대응 구조를 만든 백엔드 경험]",
    reason: "질문 의도에는 맞지만 표현이 다소 설명적입니다.",
  },
]

const reviewCards = [
  {
    label: "문장 톤",
    issue: "마지막 문단은 설명형이라 약간 힘이 빠집니다.",
    suggestion: "가치관 설명보다 운영 기준이 어떻게 바뀌었는지 더 직접적으로 써도 됩니다.",
  },
  {
    label: "기술 설명",
    issue: "Redis와 로그 추적 지점의 효과가 한 문장 정도 더 드러나도 좋습니다.",
    suggestion: "실패 요청을 단계별로 좁혀 볼 수 있었다는 운영 관점을 유지하는 편이 좋습니다.",
  },
  {
    label: "제목 시점",
    issue: "세탁본 제목은 방향용으로는 적절하지만 제출용 제목으로는 범위가 넓습니다.",
    suggestion: "finalText 수정 이후 최종 제목을 다시 고르는 흐름이 자연스럽습니다.",
  },
]

const assistActions = [
  "선택 문단만 자연화",
  "기술 표현만 더 선명하게",
  "말투만 담백하게",
  "최종본 기준 제목 다시 추천",
]

function countVisibleCharacters(text: string) {
  return text.replace(/\r\n/g, "\n").replace(/\r/g, "\n").length
}

export default function WorkspaceMockFinalPage() {
  const [finalText, setFinalText] = useState(initialFinalText)
  const [selectedTitle, setSelectedTitle] = useState(finalTitleCandidates[0].title)
  const [activeAction, setActiveAction] = useState(assistActions[0])
  const [isRefreshingTitle, setIsRefreshingTitle] = useState(false)

  const charCount = useMemo(() => countVisibleCharacters(finalText), [finalText])
  const currentTitle = finalText.match(/^\s*(\[[^\]\n]+\])/m)?.[1] ?? ""

  const handleRefreshTitle = () => {
    setIsRefreshingTitle(true)
    window.setTimeout(() => {
      setSelectedTitle(finalTitleCandidates[0].title)
      setIsRefreshingTitle(false)
    }, 500)
  }

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset className="min-h-0">
        <div className="flex h-screen min-h-0 flex-col">
          <header className="flex h-16 shrink-0 items-center justify-between gap-4 border-b border-border bg-background px-8">
            <div>
              <h1 className="text-xl font-semibold tracking-tight">작업실 실험 목업</h1>
              <p className="text-sm text-muted-foreground">
                세탁본 이후 최종 제출본을 다듬는 별도 편집 흐름
              </p>
            </div>

            <Button asChild variant="outline" className="rounded-full">
              <Link href="/workspace">
                현재 작업실
                <ArrowRight className="size-4" />
              </Link>
            </Button>
          </header>

          <main className="flex-1 min-h-0 overflow-hidden bg-muted/30 p-4">
            <div className="grid h-full min-h-0 gap-4 xl:grid-cols-[380px_minmax(0,1fr)]">
              <div className="min-h-0 overflow-hidden rounded-3xl border border-border bg-background">
                <ScrollArea className="h-full">
                  <div className="space-y-5 p-6">
                    <section className="space-y-3">
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <Sparkles className="size-4 text-primary" />
                        제목 2단계 흐름
                      </div>

                      <Card className="border border-border/70 bg-muted/20 shadow-none">
                        <CardHeader className="pb-3">
                          <div className="flex items-center justify-between gap-2">
                            <CardTitle className="text-sm font-bold">세탁본 기준 방향 제목</CardTitle>
                            <Badge variant="secondary" className="rounded-full text-[11px] font-bold">
                              자동
                            </Badge>
                          </div>
                          <CardDescription>
                            세탁 직후 글의 결을 빠르게 잡아주는 제목
                          </CardDescription>
                        </CardHeader>
                        <CardContent className="pt-0">
                          <div className="rounded-2xl border border-border/70 bg-background px-4 py-4 text-sm font-semibold leading-6">
                            {directionTitle}
                          </div>
                        </CardContent>
                      </Card>

                      <Card className="border border-primary/20 bg-primary/5 shadow-none">
                        <CardHeader className="pb-3">
                          <div className="flex items-center justify-between gap-2">
                            <CardTitle className="text-sm font-bold text-primary">finalText 기준 최종 제목</CardTitle>
                            <Badge className="rounded-full bg-primary px-3 py-1 text-[11px] font-bold text-primary-foreground hover:bg-primary">
                              수동
                            </Badge>
                          </div>
                          <CardDescription>
                            사용자가 본문을 다듬은 뒤 제출 직전에 다시 고르는 제목
                          </CardDescription>
                        </CardHeader>
                        <CardContent className="space-y-3 pt-0">
                          {finalTitleCandidates.map((candidate, index) => {
                            const isSelected = selectedTitle === candidate.title
                            return (
                              <button
                                key={candidate.title}
                                type="button"
                                onClick={() => setSelectedTitle(candidate.title)}
                                className={`w-full rounded-2xl border px-4 py-4 text-left transition-all ${
                                  isSelected
                                    ? "border-primary bg-background shadow-sm"
                                    : "border-border/70 bg-background/80 hover:border-primary/40"
                                }`}
                              >
                                <div className="flex items-center justify-between gap-2">
                                  <Badge variant={index === 0 ? "default" : "secondary"} className="rounded-full">
                                    {index === 0 ? "추천" : `${index + 1}안`}
                                  </Badge>
                                  {isSelected ? <CheckCircle2 className="size-4 text-primary" /> : null}
                                </div>
                                <p className="mt-3 text-sm font-semibold leading-6 text-foreground">
                                  {candidate.title}
                                </p>
                                <p className="mt-2 text-xs leading-5 text-muted-foreground">
                                  {candidate.reason}
                                </p>
                              </button>
                            )
                          })}

                          <Button
                            variant="outline"
                            className="w-full rounded-2xl"
                            onClick={handleRefreshTitle}
                            disabled={isRefreshingTitle}
                          >
                            {isRefreshingTitle ? <Loader2 className="size-4 animate-spin" /> : <Lightbulb className="size-4" />}
                            최종본 기준 제목 다시 추천
                          </Button>
                        </CardContent>
                      </Card>
                    </section>

                    <Separator />

                    <section className="space-y-3">
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <Wand2 className="size-4 text-primary" />
                        AI 보조 액션
                      </div>

                      <div className="grid grid-cols-1 gap-2">
                        {assistActions.map((action) => (
                          <Button
                            key={action}
                            variant={activeAction === action ? "default" : "outline"}
                            className="justify-start rounded-2xl"
                            onClick={() => setActiveAction(action)}
                          >
                            {action}
                          </Button>
                        ))}
                      </div>

                      <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-4 text-xs leading-5 text-amber-900">
                        본문은 자동으로 덮어쓰지 않고 제안만 주는 구조를 가정했습니다. 사용자가 finalText를 직접 수정하는 흐름이 중심입니다.
                      </div>
                    </section>

                    <Separator />

                    <section className="space-y-3">
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <ScanSearch className="size-4 text-primary" />
                        검토 포인트
                      </div>

                      <div className="space-y-3">
                        {reviewCards.map((card) => (
                          <div key={card.label} className="rounded-2xl border border-border/70 bg-muted/20 p-4">
                            <div className="flex items-center gap-2">
                              <Highlighter className="size-4 text-amber-500" />
                              <p className="text-sm font-semibold">{card.label}</p>
                            </div>
                            <p className="mt-3 text-sm leading-6 text-foreground">{card.issue}</p>
                            <p className="mt-2 text-xs leading-5 text-muted-foreground">{card.suggestion}</p>
                          </div>
                        ))}
                      </div>
                    </section>
                  </div>
                </ScrollArea>
              </div>

              <div className="grid min-h-0 gap-4 xl:grid-rows-[minmax(0,1.15fr)_minmax(320px,0.85fr)]">
                <Card className="min-h-0 overflow-hidden border-none bg-background shadow-sm">
                  <CardHeader className="shrink-0 border-b border-border bg-muted/20">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <CardTitle className="text-base font-bold">최종 제출본 편집기</CardTitle>
                        <CardDescription>
                          세탁본을 참고하되 실제 수정은 이 공간에서만 진행
                        </CardDescription>
                      </div>

                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="secondary" className="rounded-full px-3 py-1 text-[11px] font-bold">
                          {charCount} / 1000자
                        </Badge>
                        <Button
                          variant="outline"
                          size="sm"
                          className="rounded-full"
                          onClick={() => setFinalText(washedDraft)}
                        >
                          <RefreshCw className="size-4" />
                          세탁본으로 초기화
                        </Button>
                        <Button
                          size="sm"
                          className="rounded-full"
                          onClick={() => navigator.clipboard.writeText(finalText)}
                        >
                          <Copy className="size-4" />
                          복사
                        </Button>
                      </div>
                    </div>
                  </CardHeader>

                  <CardContent className="min-h-0 p-0">
                    <div className="flex h-full min-h-0 flex-col">
                      <div className="border-b border-border bg-background px-6 py-4">
                        <div className="flex flex-wrap items-center gap-2">
                          <Badge variant="outline" className="rounded-full border-primary/30 bg-primary/5 text-primary">
                            현재 제목
                          </Badge>
                          <span className="text-sm font-semibold text-foreground">
                            {currentTitle || "제목 라인이 없습니다."}
                          </span>
                        </div>
                        <p className="mt-2 text-xs text-muted-foreground">
                          선택된 제출용 제목: {selectedTitle}
                        </p>
                      </div>

                      <div className="min-h-0 flex-1 p-6">
                        <Textarea
                          value={finalText}
                          onChange={(event) => setFinalText(event.target.value)}
                          className="h-full min-h-[420px] resize-none rounded-3xl border-border/70 bg-muted/20 px-6 py-5 text-sm leading-8"
                        />
                      </div>
                    </div>
                  </CardContent>
                </Card>

                <Card className="min-h-0 overflow-hidden border-none bg-background shadow-sm">
                  <CardHeader className="shrink-0 border-b border-border bg-muted/20 pb-4">
                    <CardTitle className="text-base font-bold">참고 패널</CardTitle>
                    <CardDescription>
                      원본 초안과 세탁본은 참고용으로만 두고, 수정은 위 finalText에서만 진행
                    </CardDescription>
                  </CardHeader>

                  <CardContent className="min-h-0 p-0">
                    <Tabs defaultValue="washed" className="flex h-full min-h-0 flex-col">
                      <div className="border-b border-border px-6 py-4">
                        <TabsList className="grid w-full grid-cols-3 rounded-2xl">
                          <TabsTrigger value="original" className="rounded-xl">
                            원본 초안
                          </TabsTrigger>
                          <TabsTrigger value="washed" className="rounded-xl">
                            세탁본
                          </TabsTrigger>
                          <TabsTrigger value="flow" className="rounded-xl">
                            흐름 메모
                          </TabsTrigger>
                        </TabsList>
                      </div>

                      <TabsContent value="original" className="min-h-0">
                        <ScrollArea className="h-[320px]">
                          <div className="p-6">
                            <div className="whitespace-pre-wrap rounded-3xl border border-border/70 bg-muted/20 p-5 text-sm leading-7">
                              {originalDraft}
                            </div>
                          </div>
                        </ScrollArea>
                      </TabsContent>

                      <TabsContent value="washed" className="min-h-0">
                        <ScrollArea className="h-[320px]">
                          <div className="p-6">
                            <div className="mb-3 flex items-center justify-between gap-2">
                              <Badge variant="secondary" className="rounded-full">
                                방향 제목 기준본
                              </Badge>
                              <Button
                                variant="outline"
                                size="sm"
                                className="rounded-full"
                                onClick={() => setFinalText(washedDraft)}
                              >
                                <ArrowRight className="size-4" />
                                finalText로 가져오기
                              </Button>
                            </div>
                            <div className="whitespace-pre-wrap rounded-3xl border border-border/70 bg-muted/20 p-5 text-sm leading-7">
                              {washedDraft}
                            </div>
                          </div>
                        </ScrollArea>
                      </TabsContent>

                      <TabsContent value="flow" className="min-h-0">
                        <ScrollArea className="h-[320px]">
                          <div className="space-y-3 p-6">
                            <div className="rounded-2xl border border-border/70 bg-muted/20 p-4">
                              <div className="flex items-center gap-2">
                                <FileText className="size-4 text-primary" />
                                <p className="text-sm font-semibold">1. 세탁본으로 방향 잡기</p>
                              </div>
                              <p className="mt-2 text-xs leading-5 text-muted-foreground">
                                세탁 직후 제목은 글의 결을 빠르게 고정하는 역할만 담당합니다.
                              </p>
                            </div>
                            <div className="rounded-2xl border border-border/70 bg-muted/20 p-4">
                              <div className="flex items-center gap-2">
                                <Wand2 className="size-4 text-primary" />
                                <p className="text-sm font-semibold">2. finalText에서 수작업 수정</p>
                              </div>
                              <p className="mt-2 text-xs leading-5 text-muted-foreground">
                                실제 제출 문장은 따로 두고, AI는 부분 수정 제안만 합니다.
                              </p>
                            </div>
                            <div className="rounded-2xl border border-border/70 bg-muted/20 p-4">
                              <div className="flex items-center gap-2">
                                <Lightbulb className="size-4 text-primary" />
                                <p className="text-sm font-semibold">3. 최종본 기준 제목 재추천</p>
                              </div>
                              <p className="mt-2 text-xs leading-5 text-muted-foreground">
                                사용자가 수정한 강조점이 반영된 상태에서 제출용 제목을 다시 고릅니다.
                              </p>
                            </div>
                          </div>
                        </ScrollArea>
                      </TabsContent>
                    </Tabs>
                  </CardContent>
                </Card>
              </div>
            </div>
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
