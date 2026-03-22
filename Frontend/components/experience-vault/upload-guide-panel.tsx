"use client"

import { useState } from "react"
import { BookOpen, Copy, FileCode2, FileJson, Github, RefreshCcw } from "lucide-react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  EXPERIENCE_GITHUB_PROMPT,
  EXPERIENCE_JSON_TEMPLATE,
  EXPERIENCE_LOCAL_PROMPT,
  EXPERIENCE_MARKDOWN_TEMPLATE,
  REINDEXING_NOTES,
} from "@/lib/experience/upload-guides"

type GuideKey = "markdown" | "json" | "localPrompt" | "githubPrompt"

const GUIDE_CONTENT: Record<
  GuideKey,
  { label: string; icon: typeof FileCode2; value: string }
> = {
  markdown: {
    label: "Markdown 템플릿",
    icon: FileCode2,
    value: EXPERIENCE_MARKDOWN_TEMPLATE,
  },
  json: {
    label: "JSON 템플릿",
    icon: FileJson,
    value: EXPERIENCE_JSON_TEMPLATE,
  },
  localPrompt: {
    label: "로컬 경험 정리 프롬프트",
    icon: BookOpen,
    value: EXPERIENCE_LOCAL_PROMPT,
  },
  githubPrompt: {
    label: "GitHub 추출 프롬프트",
    icon: Github,
    value: EXPERIENCE_GITHUB_PROMPT,
  },
}

async function copyToClipboard(label: string, value: string) {
  await navigator.clipboard.writeText(value)
  toast.success(`${label} 복사 완료`, {
    description: "업로드 전 구조화 가이드로 바로 붙여넣을 수 있습니다.",
  })
}

export function UploadGuidePanel() {
  const [activeGuide, setActiveGuide] = useState<GuideKey>("markdown")
  const currentGuide = GUIDE_CONTENT[activeGuide]
  const CurrentIcon = currentGuide.icon

  return (
    <Card className="border-primary/15 bg-primary/[0.03] shadow-none">
      <CardHeader className="gap-3">
        <CardTitle className="flex items-center gap-2 text-base">
          <BookOpen className="size-4 text-primary" />
          경험 파일 구조화 가이드
        </CardTitle>
        <CardDescription className="leading-6">
          RAG가 강해지려면 파일 원문도 질문 답변 재료 단위로 구조화되어 있어야 합니다.
          아래 템플릿과 프롬프트를 그대로 써도 됩니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="rounded-2xl border border-border/60 bg-background/80 p-4">
          <div className="mb-3 flex items-center gap-2 text-sm font-semibold">
            <RefreshCcw className="size-4 text-primary" />
            재인덱싱이 필요한 경우
          </div>
          <div className="space-y-2 text-sm text-muted-foreground">
            {REINDEXING_NOTES.map((note) => (
              <p key={note}>{note}</p>
            ))}
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          {(Object.entries(GUIDE_CONTENT) as Array<[GuideKey, (typeof GUIDE_CONTENT)[GuideKey]]>).map(
            ([key, guide]) => {
              const Icon = guide.icon
              return (
                <Button
                  key={key}
                  type="button"
                  size="sm"
                  variant={activeGuide === key ? "default" : "outline"}
                  className="gap-2"
                  onClick={() => setActiveGuide(key)}
                >
                  <Icon className="size-3.5" />
                  {guide.label}
                </Button>
              )
            }
          )}
        </div>

        <div className="overflow-hidden rounded-2xl border border-border/60 bg-zinc-950 text-zinc-100">
          <div className="flex items-center justify-between border-b border-zinc-800 px-4 py-3">
            <div className="flex items-center gap-2 text-sm font-semibold">
              <CurrentIcon className="size-4 text-primary" />
              {currentGuide.label}
            </div>
            <Button
              type="button"
              size="sm"
              variant="secondary"
              className="gap-2"
              onClick={() => void copyToClipboard(currentGuide.label, currentGuide.value)}
            >
              <Copy className="size-3.5" />
              복사
            </Button>
          </div>
          <pre className="max-h-[420px] overflow-auto whitespace-pre-wrap px-4 py-4 text-xs leading-6">
            {currentGuide.value}
          </pre>
        </div>
      </CardContent>
    </Card>
  )
}
