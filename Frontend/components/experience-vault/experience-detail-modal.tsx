"use client"

import { useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Textarea } from "@/components/ui/textarea"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Button } from "@/components/ui/button"
import { Sparkles, FileText, Bot, Send, Loader2 } from "lucide-react"
import { type Experience } from "@/lib/mock-data"

const INITIAL_CHAT_MESSAGES: Array<{ role: "user" | "assistant"; content: string }> = [
  {
    role: "assistant",
    content:
      "이 영역은 아직 실제 AI와 연결되어 있지 않습니다. 우선은 최신 권장 Markdown 구조로 경험을 직접 정리하고 저장하는 용도로 맞춰두었습니다.",
  },
]

function buildMarkdownFromExperience(experience: Experience) {
  if (experience.rawContent?.trim()) {
    return experience.rawContent
  }

  return `# ${experience.title}

## 한 줄 요약
${experience.description}

## 출처 / 맥락
- 미작성

## 문제 상황
- 미작성

## 내가 맡은 역할
- ${experience.role}

## 내가 한 판단
- 미작성

## 내가 실제로 한 행동
- 미작성

## 결과
${experience.metrics.map((metric) => `- ${metric}`).join("\n")}

## 기술 스택
${experience.techStack.map((tech) => `- ${tech}`).join("\n")}

## 프로젝트 기간
${experience.period}

## 직무 연결 키워드
- 미작성

## 활용 가능한 문항 유형
- 문제 해결
- 직무 역량`
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
          "이 채팅은 아직 데모 응답입니다. 실제 AI 편집 기능을 붙이기 전까지는 오른쪽 Markdown 편집기를 기준으로 경험 구조를 정리해 주세요.",
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
