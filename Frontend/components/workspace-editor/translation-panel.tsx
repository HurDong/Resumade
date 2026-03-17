"use client"

import { useEffect, useRef } from "react"
import {
  FileText,
  AlertTriangle,
  CheckCircle,
  Wand2,
  Copy,
} from "lucide-react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { ScrollArea } from "@/components/ui/scroll-area"
import { useWorkspaceStore } from "@/lib/store/workspace-store"

export function TranslationPanel() {
  const {
    questions,
    activeQuestionId,
    isProcessing,
    progressMessage,
    processingError,
    hoveredMistranslationId,
    setHoveredMistranslationId,
  } = useWorkspaceStore()

  const containerRef = useRef<HTMLDivElement>(null)
  const activeQuestion = questions.find((q) => q.id === activeQuestionId) || questions[0]
  const { content: draft, washedKr, mistranslations } = activeQuestion
  const hasHighlights = mistranslations.length > 0

  const escapeRegExp = (string: string) => {
    return string.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
  }

  const findUniqueExactRange = (source: string, target: string) => {
    const escaped = escapeRegExp(target)
    const exactRegex = new RegExp(escaped, "g")
    const matches: number[] = []
    let exactMatch: RegExpExecArray | null

    while ((exactMatch = exactRegex.exec(source)) !== null) {
      matches.push(exactMatch.index)
      if (matches.length > 1) {
        return null
      }
    }

    if (matches.length !== 1) {
      return null
    }

    return { start: matches[0], end: matches[0] + target.length }
  }

  const highlightText = (text: string) => {
    if (!text || !mistranslations.length) return text

    const normalizedText = text.normalize("NFC")
    const matches: { start: number; end: number; mis: any }[] = []

    mistranslations.forEach((mis) => {
      if (
        Number.isInteger(mis.startIndex) &&
        Number.isInteger(mis.endIndex) &&
        (mis.startIndex ?? -1) >= 0 &&
        (mis.endIndex ?? -1) > (mis.startIndex ?? -1) &&
        (mis.endIndex ?? 0) <= normalizedText.length
      ) {
        matches.push({
          start: mis.startIndex as number,
          end: mis.endIndex as number,
          mis,
        })
        return
      }

      if (!mis.translated) return

      const target = mis.translated.normalize("NFC").trim()
      if (!target) return

      const uniqueRange = findUniqueExactRange(normalizedText, target)
      if (uniqueRange) {
        matches.push({ ...uniqueRange, mis })
      }
    })

    if (matches.length === 0) return normalizedText

    matches.sort((a, b) => a.start - b.start || (b.end - b.start) - (a.end - a.start))

    const finalMatches: typeof matches = []
    let lastEnd = -1
    for (const match of matches) {
      if (match.start >= lastEnd) {
        finalMatches.push(match)
        lastEnd = match.end
      }
    }

    let result = ""
    let lastIndex = 0
    const hasAnyHover = hoveredMistranslationId !== null

    finalMatches.forEach((match) => {
      result += normalizedText.substring(lastIndex, match.start)
      const mis = match.mis
      const isHovered = hoveredMistranslationId === mis.id
      const hoverEffect = isHovered
        ? "ring-2 ring-primary scale-110 shadow-2xl z-[50] opacity-100"
        : hasAnyHover
          ? "opacity-20 grayscale scale-95 blur-[0.5px]"
          : "opacity-100"
      const reasonLabel = mis.reason ? ` [${mis.reason}]` : ""
      result += `<mark data-mis-id="${mis.id}" title="검토 사유${reasonLabel}" class="bg-highlight-warning text-foreground ${hoverEffect} px-0.5 rounded cursor-help transition-all duration-300 inline-block origin-center">${normalizedText.substring(match.start, match.end)}</mark>`
      lastIndex = match.end
    })

    result += normalizedText.substring(lastIndex)
    return result
  }

  const highlightOriginalText = (text: string) => {
    if (!text) return ""

    const normalizedText = text.normalize("NFC")
    let result = normalizedText

    mistranslations.forEach((mis) => {
      if (!mis.original) return
      const normalizedOriginal = mis.original.normalize("NFC")
      const escaped = escapeRegExp(normalizedOriginal)
      const regex = new RegExp(escaped, "g")
      const isHovered = hoveredMistranslationId === mis.id

      if (isHovered) {
        result = result.replace(
          regex,
          `<span class="bg-primary/20 text-primary ring-1 ring-primary/50 px-0.5 rounded transition-all duration-300 font-bold">${normalizedOriginal}</span>`
        )
      }
    })

    return result
  }

  useEffect(() => {
    const el = containerRef.current
    if (!el) return

    const handleMouseOver = (e: MouseEvent) => {
      const target = e.target as HTMLElement
      const misId = target.getAttribute("data-mis-id")
      if (misId) {
        setHoveredMistranslationId(misId)
      }
    }

    const handleMouseOut = (e: MouseEvent) => {
      const target = e.target as HTMLElement
      if (target.getAttribute("data-mis-id")) {
        setHoveredMistranslationId(null)
      }
    }

    el.addEventListener("mouseover", handleMouseOver)
    el.addEventListener("mouseout", handleMouseOut)

    return () => {
      el.removeEventListener("mouseover", handleMouseOver)
      el.removeEventListener("mouseout", handleMouseOut)
    }
  }, [setHoveredMistranslationId])

  if (!draft && !washedKr && !isProcessing) {
    return (
      <div className="flex h-full flex-col items-center justify-center bg-muted/5 p-10 text-center text-muted-foreground">
        <div className="relative mb-6">
          <Wand2 className="size-16 opacity-20" />
          <div className="absolute inset-0 -z-10 size-16 rounded-full bg-primary/10 blur-2xl" />
        </div>
        <h3 className="mb-3 text-xl font-bold">휴먼 패치 작업실</h3>
        <p className="max-w-md text-balance text-sm font-medium leading-relaxed text-muted-foreground/80">
          왼쪽 작업소에서 초안을 생성하면 번역, 역번역, 휴먼 패치 검토 결과가 이 영역에 순서대로 표시됩니다.
        </p>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col overflow-hidden bg-muted/10">
      <div className="sticky top-0 z-10 shrink-0 border-b border-border bg-background/50 p-6 backdrop-blur-md">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="flex items-center gap-2 text-lg font-bold">
              <CheckCircle className="size-5 text-emerald-500" />
              휴먼 패치 결과
            </h3>
            <p className="mt-1 text-xs text-muted-foreground">
              역번역(EN→KR)을 거쳐 기계적인 문투를 지우고 자연스럽게 다듬었습니다.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="outline"
              className="h-8 gap-2 rounded-lg border-primary/20 px-3 text-[11px] font-bold shadow-sm transition-all hover:bg-primary/5"
              onClick={() => {
                navigator.clipboard.writeText(washedKr)
                toast.success("클립보드에 복사했습니다.", {
                  description: "지원서에 바로 붙여 넣어 사용할 수 있습니다.",
                  position: "top-center",
                })
              }}
              disabled={!washedKr}
            >
              <Copy className="size-3.5" />
              복사하기
            </Button>
            {isProcessing && (
              <Badge variant="secondary" className="animate-in fade-in zoom-in-95 gap-2 px-3 py-1.5">
                <div className="size-2 rounded-full bg-primary animate-pulse" />
                <span className="text-[11px] font-bold">{progressMessage}</span>
              </Badge>
            )}
            {!isProcessing && processingError && (
              <Badge variant="destructive" className="max-w-full gap-2 px-3 py-1.5">
                <AlertTriangle className="size-3.5 shrink-0" />
                <span className="break-all text-[11px] font-bold">{processingError}</span>
              </Badge>
            )}
          </div>
        </div>
      </div>

      <ScrollArea className="h-full min-h-0 flex-1">
        <div className="animate-in fade-in slide-in-from-bottom-2 space-y-8 p-6 pb-20 duration-500">
          <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
            <Card className="border-none bg-background/80 shadow-sm">
              <CardHeader className="pb-4">
                <CardTitle className="flex items-center gap-2 text-xs font-black uppercase tracking-widest text-muted-foreground/60">
                  <FileText className="size-3.5" />
                  원문 (AI 초안)
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div
                  className={`min-h-[200px] whitespace-pre-wrap text-sm font-medium italic leading-relaxed text-muted-foreground transition-all duration-300 ${hoveredMistranslationId ? "opacity-100" : "opacity-70"}`}
                  dangerouslySetInnerHTML={{ __html: highlightOriginalText(draft) }}
                />
              </CardContent>
            </Card>

            <Card className="border-primary/20 bg-background shadow-lg ring-1 ring-primary/5">
              <CardHeader className="pb-4">
                <CardTitle className="flex items-center gap-2 text-xs font-bold uppercase tracking-widest text-primary">
                  <CheckCircle className="size-3.5" />
                  세탁본
                </CardTitle>
                <CardDescription className="text-[10px] font-semibold italic text-muted-foreground/80">
                  {hasHighlights
                    ? "하이라이트된 표현은 의미가 약해졌거나 검토가 필요한 부분입니다."
                    : "하이라이트 전에는 세탁본 전체 텍스트를 그대로 보여줍니다."}
                </CardDescription>
              </CardHeader>
              <CardContent ref={containerRef}>
                {hasHighlights ? (
                  <div
                    className="min-h-[200px] whitespace-pre-wrap text-sm font-medium leading-relaxed text-foreground"
                    dangerouslySetInnerHTML={{ __html: highlightText(washedKr) }}
                  />
                ) : (
                  <div className="min-h-[200px] whitespace-pre-wrap text-[15px] font-medium leading-8 text-foreground">
                    {washedKr}
                  </div>
                )}
                {!washedKr && isProcessing && (
                  <div className="flex flex-col items-center justify-center gap-3 py-20 opacity-20">
                    <Wand2 className="size-8 animate-spin" />
                    <span className="text-xs font-bold tracking-tighter">Washing in progress...</span>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      </ScrollArea>
    </div>
  )
}
