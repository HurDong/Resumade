"use client"

import { useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Textarea } from "@/components/ui/textarea"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Button } from "@/components/ui/button"
import { Sparkles, FileText, Bot, Send, Loader2 } from "lucide-react"
import { type Experience, type ExperienceFacet } from "@/lib/mock-data"

const INITIAL_CHAT_MESSAGES: Array<{ role: "user" | "assistant"; content: string }> = [
  {
    role: "assistant",
    content:
      "이 영역은 아직 실제 AI와 연결되어 있지 않습니다. 우선은 최신 권장 Markdown 구조로 경험을 직접 정리하고 저장할 수 있도록 맞춰두었습니다.",
  },
]

function isLikelyJsonContent(value?: string | null) {
  const trimmed = value?.trim() ?? ""
  return trimmed.startsWith("{") || trimmed.startsWith("[")
}

function asTrimmedText(value: unknown) {
  if (typeof value === "string") {
    return value.trim()
  }

  if (typeof value === "number" || typeof value === "boolean") {
    return String(value).trim()
  }

  return ""
}

function asTextList(value: unknown) {
  if (Array.isArray(value)) {
    return value
      .map((item) => asTrimmedText(item))
      .filter((item) => item.length > 0)
  }

  const text = asTrimmedText(value)
  if (!text) {
    return []
  }

  return text
    .split(/\r?\n|,\s*/g)
    .map((item) => item.replace(/^[-*]\s*/, "").trim())
    .filter((item) => item.length > 0)
}

function buildNarrativeSummary(value: string) {
  const normalized = value.replace(/\s+/g, " ").trim()
  if (!normalized) {
    return ""
  }

  if (normalized.length <= 180) {
    return normalized
  }

  const sentenceBoundary = normalized.lastIndexOf(". ", 180)
  if (sentenceBoundary >= 80) {
    return normalized.slice(0, sentenceBoundary + 1).trim()
  }

  return `${normalized.slice(0, 180).trim()}...`
}

function buildListBlock(values?: string[] | null, fallback = "- 미확인") {
  if (!values?.length) {
    return fallback
  }

  return values.map((value) => `- ${value}`).join("\n")
}

function buildFallbackFacet(experience: Experience): ExperienceFacet {
  return {
    id: "fallback",
    title: experience.title,
    situation: [],
    role: experience.role ? [experience.role] : [],
    judgment: [],
    actions: [],
    results: experience.metrics,
    techStack: experience.techStack,
    jobKeywords: experience.jobKeywords,
    questionTypes: experience.questionTypes,
  }
}

function toMarkdownSource(experience: Experience): Experience {
  if (!isLikelyJsonContent(experience.rawContent)) {
    return experience
  }

  try {
    const parsed = JSON.parse(experience.rawContent ?? "") as Record<string, unknown>
    const narrative =
      asTrimmedText(parsed.content) ||
      asTrimmedText(parsed.body) ||
      asTrimmedText(parsed.narrative) ||
      asTrimmedText(parsed.details)
    const projectTechStack = asTextList(parsed.projectTechStack)
    const overallTechStack = asTextList(parsed.overallTechStack)
    const techStack = asTextList(parsed.techStack)
    const metrics = asTextList(parsed.metrics)
    const jobKeywords = asTextList(parsed.jobKeywords)
    const questionTypes = asTextList(parsed.questionTypes)

    const parsedFacets: ExperienceFacet[] = Array.isArray(parsed.facets)
      ? parsed.facets
          .map((facet, index) => {
            const candidate = typeof facet === "object" && facet ? (facet as Record<string, unknown>) : null
            if (!candidate) {
              return null
            }

            const facetResults = asTextList(candidate.results)
            const facetMetrics = asTextList(candidate.metrics)

            return {
              id: index,
              displayOrder: index,
              title: asTrimmedText(candidate.title) || `Facet ${index + 1}`,
              situation: asTextList(candidate.situation),
              role: asTextList(candidate.role),
              judgment: asTextList(candidate.judgment),
              actions: asTextList(candidate.actions),
              results: facetResults.length > 0 ? facetResults : facetMetrics,
              techStack: asTextList(candidate.techStack),
              jobKeywords: asTextList(candidate.jobKeywords),
              questionTypes: asTextList(candidate.questionTypes),
            } satisfies ExperienceFacet
          })
          .filter((facet): facet is ExperienceFacet => facet !== null)
      : []

    const fallbackFacet: ExperienceFacet[] =
      parsedFacets.length > 0
        ? parsedFacets
        : [
            {
              id: 0,
              displayOrder: 0,
              title: asTrimmedText(parsed.facetTitle) || asTrimmedText(parsed.title) || experience.title,
              situation: asTextList(parsed.situation),
              role: asTextList(parsed.role).length > 0 ? asTextList(parsed.role) : experience.role ? [experience.role] : [],
              judgment: asTextList(parsed.judgment),
              actions: asTextList(parsed.actions).length > 0 ? asTextList(parsed.actions) : narrative ? [narrative] : [],
              results: asTextList(parsed.results).length > 0 ? asTextList(parsed.results) : metrics,
              techStack: techStack.length > 0 ? techStack : overallTechStack.length > 0 ? overallTechStack : projectTechStack,
              jobKeywords,
              questionTypes,
            },
          ]

    return {
      ...experience,
      title: asTrimmedText(parsed.title) || experience.title,
      category: asTrimmedText(parsed.category) || experience.category,
      description:
        asTrimmedText(parsed.summary) ||
        asTrimmedText(parsed.description) ||
        buildNarrativeSummary(narrative) ||
        experience.description,
      origin:
        asTrimmedText(parsed.origin) ||
        asTrimmedText(parsed.source) ||
        asTrimmedText(parsed.context) ||
        experience.origin,
      organization: asTrimmedText(parsed.organization) || experience.organization,
      period: asTrimmedText(parsed.period) || experience.period,
      role: asTrimmedText(parsed.role) || experience.role,
      overallTechStack:
        overallTechStack.length > 0
          ? overallTechStack
          : projectTechStack.length > 0
            ? projectTechStack
            : experience.overallTechStack,
      techStack: techStack.length > 0 ? techStack : experience.techStack,
      metrics: metrics.length > 0 ? metrics : experience.metrics,
      jobKeywords: jobKeywords.length > 0 ? jobKeywords : experience.jobKeywords,
      questionTypes: questionTypes.length > 0 ? questionTypes : experience.questionTypes,
      facets: fallbackFacet,
    }
  } catch {
    return experience
  }
}

function buildMarkdownFromExperience(experience: Experience) {
  if (experience.rawContent?.trim() && !isLikelyJsonContent(experience.rawContent)) {
    return experience.rawContent
  }

  const source = toMarkdownSource(experience)
  const facets = source.facets?.length ? source.facets : [buildFallbackFacet(source)]
  const overallTechStack =
    source.overallTechStack?.length && source.overallTechStack.length > 0
      ? source.overallTechStack
      : source.techStack

  const facetBlocks = facets
    .map(
      (facet, index) => `## Facet ${index + 1} | ${facet.title || source.title}
### 문제 상황
${buildListBlock(facet.situation)}

### 내가 맡은 역할
${buildListBlock(facet.role, `- ${source.role || "미확인"}`)}

### 내가 한 판단
${buildListBlock(facet.judgment)}

### 내가 실제로 한 행동
${buildListBlock(facet.actions)}

### 결과
${buildListBlock(facet.results, buildListBlock(source.metrics))}

### 기술 스택
${buildListBlock(facet.techStack, buildListBlock(source.techStack))}

### 직무 연결 키워드
${buildListBlock(facet.jobKeywords, buildListBlock(source.jobKeywords))}

### 활용 가능한 문항 유형
${buildListBlock(facet.questionTypes, buildListBlock(source.questionTypes))}`
    )
    .join("\n\n")

  return `# ${source.title}

## 한 줄 요약
${source.description || "미확인"}

## 프로젝트 메타데이터
- 출처/맥락: ${source.origin || "미확인"}
- 조직/소속: ${source.organization || "미확인"}
- 역할: ${source.role || "미확인"}
- 기간: ${source.period || "미확인"}
- 카테고리: ${source.category || "미확인"}

## 프로젝트 전체 기술 스택
${buildListBlock(overallTechStack)}

## 대표 직무 연결 키워드
${buildListBlock(source.jobKeywords)}

## 대표 활용 가능한 문항 유형
${buildListBlock(source.questionTypes)}

${facetBlocks}`
}

export function ExperienceDetailModal({
  experience,
  isOpen,
  onClose,
  onExperienceUpdated,
}: {
  experience: Experience | null
  isOpen: boolean
  onClose: () => void
  onExperienceUpdated: (experience: Experience) => void
}) {
  const [markdownContent, setMarkdownContent] = useState("")
  const [chatInput, setChatInput] = useState("")
  const [chatMessages, setChatMessages] = useState(INITIAL_CHAT_MESSAGES)
  const [isSaving, setIsSaving] = useState(false)
  const loadedMarkdownRef = useRef("")
  const latestMarkdownRef = useRef("")

  useEffect(() => {
    latestMarkdownRef.current = markdownContent
  }, [markdownContent])

  useEffect(() => {
    if (!experience || !isOpen) {
      return
    }

    const nextMarkdown = buildMarkdownFromExperience(experience)
    setMarkdownContent(nextMarkdown)
    loadedMarkdownRef.current = nextMarkdown
    latestMarkdownRef.current = nextMarkdown
    setChatInput("")
    setChatMessages(INITIAL_CHAT_MESSAGES)
  }, [experience?.id, isOpen])

  const persistMarkdown = async () => {
    if (!experience) {
      return
    }

    const nextMarkdown = latestMarkdownRef.current.trim()
    const loadedMarkdown = loadedMarkdownRef.current.trim()

    if (!nextMarkdown || nextMarkdown === loadedMarkdown) {
      return
    }

    try {
      setIsSaving(true)

      const response = await fetch(`/api/experiences/${experience.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ rawContent: nextMarkdown }),
      })

      if (!response.ok) {
        const message = (await response.text()).trim()
        throw new Error(message || "경험 Markdown 저장에 실패했습니다.")
      }

      const updatedExperience = {
        ...((await response.json()) as Experience),
        id: String(experience.id),
      }
      onExperienceUpdated(updatedExperience)

      const savedMarkdown = buildMarkdownFromExperience(updatedExperience)
      loadedMarkdownRef.current = savedMarkdown
      latestMarkdownRef.current = savedMarkdown
      setMarkdownContent(savedMarkdown)
    } catch (error) {
      console.error("Failed to save experience markdown:", error)
      toast.error("저장 실패", {
        description:
          error instanceof Error ? error.message : "경험 Markdown 저장 중 오류가 발생했습니다.",
      })
    } finally {
      setIsSaving(false)
    }
  }

  const handleRequestClose = () => {
    void persistMarkdown()
    onClose()
  }

  const handleSendMessage = () => {
    if (!chatInput.trim()) return

    setChatMessages((prev) => [
      ...prev,
      { role: "user", content: chatInput },
      {
        role: "assistant",
        content:
          "이 채팅은 아직 데모 응답입니다. 실제 AI 편집 기능을 붙이기 전까지는 왼쪽 Markdown 편집기를 기준으로 경험 구조를 다듬어 주세요.",
      },
    ])
    setChatInput("")
  }

  if (!experience) return null

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleRequestClose()}>
      <DialogContent className="h-[80vh] max-w-5xl gap-0 overflow-hidden p-0">
        <DialogHeader className="border-b border-border px-6 py-4">
          <DialogTitle className="flex items-center gap-3">
            <div className="flex size-8 items-center justify-center rounded-lg bg-primary/10">
              <Sparkles className="size-4 text-primary" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate">{experience.title}</div>
            </div>
            {isSaving && (
              <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
                <Loader2 className="size-3.5 animate-spin" />
                저장 중
              </div>
            )}
          </DialogTitle>
        </DialogHeader>

        <div className="grid h-full min-h-0 grid-cols-1 md:grid-cols-2">
          <div className="flex min-h-0 flex-col border-b border-border md:border-r md:border-b-0">
            <div className="border-b border-border bg-muted/30 px-4 py-3">
              <h3 className="flex items-center gap-2 text-sm font-medium">
                <FileText className="size-4" />
                Markdown 편집기
              </h3>
            </div>
            <Textarea
              value={markdownContent}
              onChange={(e) => setMarkdownContent(e.target.value)}
              className="flex-1 resize-none rounded-none border-0 font-mono text-sm focus-visible:ring-0"
              placeholder="최신 권장 구조에 맞춰 경험 Markdown을 작성해 주세요."
            />
          </div>

          <div className="flex min-h-0 flex-col bg-muted/20">
            <div className="border-b border-border bg-muted/30 px-4 py-3">
              <h3 className="flex items-center gap-2 text-sm font-medium">
                <Bot className="size-4 text-primary" />
                AI 어시스트
              </h3>
            </div>

            <ScrollArea className="flex-1">
              <div className="space-y-4 p-4">
                {chatMessages.map((msg, idx) => (
                  <div
                    key={idx}
                    className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
                  >
                    <div
                      className={`max-w-[85%] rounded-xl px-4 py-3 text-sm ${
                        msg.role === "user"
                          ? "bg-primary text-primary-foreground"
                          : "border border-border bg-card"
                      }`}
                    >
                      {msg.content}
                    </div>
                  </div>
                ))}
              </div>
            </ScrollArea>

            <div className="border-t border-border p-4">
              <div className="flex gap-2">
                <Input
                  value={chatInput}
                  onChange={(e) => setChatInput(e.target.value)}
                  placeholder="AI에게 구조화 방향을 물어보는 UI 자리입니다."
                  className="flex-1"
                  onKeyDown={(e) => e.key === "Enter" && handleSendMessage()}
                />
                <Button size="icon" onClick={handleSendMessage}>
                  <Send className="size-4" />
                </Button>
              </div>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
