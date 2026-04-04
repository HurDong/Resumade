"use client"

import { useEffect, useMemo, useRef, useState } from "react"
import Link from "next/link"
import { ArrowLeft, Eye, Loader2 } from "lucide-react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { fetchCoteWikiNote, updateCoteWikiNote } from "@/lib/api/cote-wiki"
import {
  buildCoteWikiPayload,
  getCoteWikiUpdatedLabel,
  type CoteWikiDraft,
  type CoteWikiNote,
} from "@/lib/cote-wiki"
import { CoteWikiMarkdown } from "./cote-wiki-markdown"

type SaveState = "idle" | "dirty" | "saving" | "saved" | "error" | "invalid"

const TOOLBAR_SNIPPETS = [
  { label: "# 제목", snippet: "\n# 제목\n" },
  { label: "## 섹션", snippet: "\n## 섹션\n" },
  { label: "### 포인트", snippet: "\n### 포인트\n" },
  { label: "글머리표", snippet: "\n- 포인트\n" },
  { label: "번호 리스트", snippet: "\n1. 순서\n" },
  { label: "코드블록", snippet: "\n```java\n\n```\n" },
] as const

function serializeDraft(draft: CoteWikiDraft) {
  return JSON.stringify(buildCoteWikiPayload(draft))
}

export function CoteWikiEditor({ noteId }: { noteId: number }) {
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)
  const latestSnapshotRef = useRef("")
  const savedSnapshotRef = useRef("")
  const hydratedRef = useRef(false)

  const [draft, setDraft] = useState<CoteWikiNote | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [saveState, setSaveState] = useState<SaveState>("idle")
  const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    fetchCoteWikiNote(noteId)
      .then((note) => {
        if (cancelled) {
          return
        }

        setDraft(note)
        const snapshot = serializeDraft(note)
        latestSnapshotRef.current = snapshot
        savedSnapshotRef.current = snapshot
        hydratedRef.current = true
        setSaveState("idle")
      })
      .catch((error) => {
        if (cancelled) {
          return
        }
        setLoadError(error instanceof Error ? error.message : "카드를 불러오지 못했습니다.")
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [noteId])

  useEffect(() => {
    if (!draft) {
      latestSnapshotRef.current = ""
      return
    }

    latestSnapshotRef.current = serializeDraft(draft)
  }, [draft])

  useEffect(() => {
    if (!draft || !hydratedRef.current) {
      return
    }

    const snapshot = serializeDraft(draft)
    if (snapshot === savedSnapshotRef.current) {
      return
    }

    if (!draft.title.trim()) {
      setSaveState("invalid")
      setSaveErrorMessage("제목을 입력하면 자동 저장이 다시 시작됩니다.")
      return
    }

    setSaveState("dirty")
    setSaveErrorMessage(null)

    const timeoutId = window.setTimeout(async () => {
      setSaveState("saving")

      try {
        const payload = JSON.parse(snapshot) as CoteWikiDraft
        const saved = await updateCoteWikiNote(noteId, payload)
        savedSnapshotRef.current = snapshot

        if (latestSnapshotRef.current === snapshot) {
          setDraft(saved)
          setSaveState("saved")
          setSaveErrorMessage(null)
        }
      } catch (error) {
        const message =
          error instanceof Error ? error.message : "자동 저장 중 문제가 발생했습니다."

        setSaveState("error")
        setSaveErrorMessage(message)
        toast.error(message)
      }
    }, 800)

    return () => window.clearTimeout(timeoutId)
  }, [draft, noteId])

  const statusMeta = useMemo(() => {
    switch (saveState) {
      case "saving":
        return { label: "자동 저장 중", className: "border-primary/20 bg-primary/10 text-primary" }
      case "saved":
        return { label: "방금 저장됨", className: "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-300" }
      case "dirty":
        return { label: "저장 대기 중", className: "border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-300" }
      case "error":
        return { label: "저장 실패", className: "border-destructive/20 bg-destructive/10 text-destructive" }
      case "invalid":
        return { label: "제목 필요", className: "border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-300" }
      default:
        return { label: "편집 준비 완료", className: "border-border/70 bg-background text-muted-foreground" }
    }
  }, [saveState])

  const insertSnippet = (snippet: string) => {
    const textarea = textareaRef.current
    if (!textarea || !draft) {
      return
    }

    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const nextContent = `${draft.content.slice(0, start)}${snippet}${draft.content.slice(end)}`
    const nextCursor = start + snippet.length

    setDraft((current) => (current ? { ...current, content: nextContent } : current))

    requestAnimationFrame(() => {
      textarea.focus()
      textarea.setSelectionRange(nextCursor, nextCursor)
    })
  }

  if (isLoading) {
    return (
      <div className="flex min-h-[480px] items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (loadError || !draft) {
    return (
      <Empty className="min-h-[420px] rounded-3xl border border-dashed border-border/70 bg-background/70">
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <Eye />
          </EmptyMedia>
          <EmptyTitle>카드를 열 수 없습니다.</EmptyTitle>
          <EmptyDescription>{loadError ?? "요청한 코테 위키 카드가 없습니다."}</EmptyDescription>
        </EmptyHeader>
        <Button asChild>
          <Link href="/vault?view=wiki">
            <ArrowLeft data-icon="inline-start" />
            목록으로 돌아가기
          </Link>
        </Button>
      </Empty>
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-4 rounded-3xl border border-border/70 bg-background px-5 py-5 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Button variant="ghost" asChild>
            <Link href="/vault?view=wiki">
              <ArrowLeft data-icon="inline-start" />
              코테 위키로 돌아가기
            </Link>
          </Button>

          <Badge variant="outline" className={statusMeta.className}>
            {saveState === "saving" ? <Loader2 className="animate-spin" /> : null}
            {statusMeta.label}
          </Badge>
        </div>

        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-end">
          <div className="flex flex-col gap-3">
            <Input
              value={draft.title}
              onChange={(event) => {
                setDraft((current) =>
                  current ? { ...current, title: event.target.value } : current,
                )
              }}
              className="h-14 rounded-2xl border-border/60 text-xl font-semibold tracking-tight"
              placeholder="카드 제목"
            />
            <p className="text-sm text-muted-foreground">
              큰 제목은 카드 리스트에 그대로 노출됩니다. 전날에 다시 찾을 이름으로 유지하세요.
            </p>
          </div>

          <ToggleGroup
            type="single"
            value={draft.category}
            onValueChange={(value) => {
              if (value === "문법" || value === "알고리즘") {
                setDraft((current) => (current ? { ...current, category: value } : current))
              }
            }}
            variant="outline"
            className="w-full lg:w-auto"
          >
            <ToggleGroupItem value="문법" className="flex-1 lg:flex-none">
              문법
            </ToggleGroupItem>
            <ToggleGroupItem value="알고리즘" className="flex-1 lg:flex-none">
              알고리즘
            </ToggleGroupItem>
          </ToggleGroup>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {TOOLBAR_SNIPPETS.map((tool) => (
            <Button
              key={tool.label}
              type="button"
              variant="outline"
              size="sm"
              onClick={() => insertSnippet(tool.snippet)}
            >
              {tool.label}
            </Button>
          ))}
        </div>

        <p className="text-xs text-muted-foreground">
          최소 문법만 지원합니다. 일반 텍스트, 제목, 리스트, 코드블록 중심으로 빠르게 적고 자동 저장하세요.
        </p>
        {saveErrorMessage ? <p className="text-sm text-destructive">{saveErrorMessage}</p> : null}
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.05fr)_minmax(360px,0.95fr)]">
        <Card className="py-0">
          <CardHeader className="border-b border-border/70 px-5 py-4">
            <CardTitle>문서 편집</CardTitle>
            <CardDescription>
              긴 설명보다 핵심 포인트와 Java 템플릿이 먼저 보이도록 작성하세요.
            </CardDescription>
          </CardHeader>
          <CardContent className="px-0 py-0">
            <Textarea
              ref={textareaRef}
              value={draft.content}
              onChange={(event) =>
                setDraft((current) =>
                  current ? { ...current, content: event.target.value } : current,
                )
              }
              className="min-h-[68vh] resize-none rounded-none border-0 px-5 py-5 text-base leading-7 shadow-none focus-visible:ring-0"
              placeholder={`# ${draft.title}\n\n## 핵심 요약\n- 여기에 적으세요.`}
            />
          </CardContent>
        </Card>

        <Card className="py-0 xl:sticky xl:top-6 xl:self-start">
          <CardHeader className="border-b border-border/70 px-5 py-4">
            <CardTitle>미리보기</CardTitle>
            <CardDescription>
              최근 저장: {getCoteWikiUpdatedLabel(draft)}
            </CardDescription>
          </CardHeader>
          <CardContent className="px-5 py-5">
            <CoteWikiMarkdown content={draft.content || `# ${draft.title}`} />
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
