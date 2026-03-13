"use client"

import { useState } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Textarea } from "@/components/ui/textarea"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Button } from "@/components/ui/button"
import { Sparkles, FileText, Bot, Send } from "lucide-react"
import { type Experience } from "@/lib/mock-data"

export function ExperienceDetailModal({ 
  experience, 
  isOpen, 
  onClose 
}: { 
  experience: Experience | null
  isOpen: boolean
  onClose: () => void 
}) {
  const [markdownContent, setMarkdownContent] = useState("")
  const [chatInput, setChatInput] = useState("")
  const [chatMessages, setChatMessages] = useState<{ role: "user" | "assistant"; content: string }[]>([
    { 
      role: "assistant", 
      content: "안녕하세요! 이 경험 내용을 어떻게 수정하면 좋을지 도와드릴게요. 궁금한 점이나 수정하고 싶은 부분이 있으시면 말씀해주세요." 
    }
  ])

  // Initialize markdown content when experience changes
  useState(() => {
    if (experience) {
      setMarkdownContent(`# ${experience.title}

## 프로젝트 개요
${experience.description}

## 기술 스택
${experience.techStack.map(t => `- ${t}`).join('\n')}

## 주요 성과
${experience.metrics.map(m => `- ${m}`).join('\n')}

## 프로젝트 기간
${experience.period}

## 담당 역할
${experience.role}`)
    }
  })

  const handleSendMessage = () => {
    if (!chatInput.trim()) return
    
    setChatMessages(prev => [
      ...prev, 
      { role: "user", content: chatInput },
      { role: "assistant", content: `좋은 질문이에요! "${chatInput}"에 대해 답변드리자면, 이 경험에서 더 강조할 수 있는 부분은 구체적인 숫자나 성과 지표입니다. 예를 들어 "응답 시간 40% 감소"와 같은 정량적 지표를 더 부각시켜 보시는 건 어떨까요?` }
    ])
    setChatInput("")
  }

  if (!experience) return null

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-5xl h-[80vh] p-0 gap-0">
        <DialogHeader className="px-6 py-4 border-b border-border">
          <DialogTitle className="flex items-center gap-3">
            <div className="flex size-8 items-center justify-center rounded-lg bg-primary/10">
              <Sparkles className="size-4 text-primary" />
            </div>
            {experience.title}
          </DialogTitle>
        </DialogHeader>
        
        <div className="grid grid-cols-2 h-full">
          {/* Left: Markdown Editor */}
          <div className="border-r border-border flex flex-col">
            <div className="px-4 py-3 border-b border-border bg-muted/30">
              <h3 className="text-sm font-medium flex items-center gap-2">
                <FileText className="size-4" />
                마크다운 편집기
              </h3>
            </div>
            <Textarea
              value={markdownContent || `# ${experience.title}\n\n## 프로젝트 개요\n${experience.description}\n\n## 기술 스택\n${experience.techStack.map(t => `- ${t}`).join('\n')}\n\n## 주요 성과\n${experience.metrics.map(m => `- ${m}`).join('\n')}\n\n## 프로젝트 기간\n${experience.period}\n\n## 담당 역할\n${experience.role}`}
              onChange={(e) => setMarkdownContent(e.target.value)}
              className="flex-1 resize-none border-0 rounded-none font-mono text-sm focus-visible:ring-0"
              placeholder="마크다운 형식으로 경험을 작성하세요..."
            />
          </div>

          {/* Right: AI Chat */}
          <div className="flex flex-col bg-muted/20">
            <div className="px-4 py-3 border-b border-border bg-muted/30">
              <h3 className="text-sm font-medium flex items-center gap-2">
                <Bot className="size-4 text-primary" />
                AI 어시스트
              </h3>
            </div>
            
            <ScrollArea className="flex-1 p-4">
              <div className="space-y-4">
                {chatMessages.map((msg, idx) => (
                  <div 
                    key={idx} 
                    className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
                  >
                    <div className={`max-w-[85%] rounded-xl px-4 py-3 text-sm ${
                      msg.role === "user" 
                        ? "bg-primary text-primary-foreground" 
                        : "bg-card border border-border"
                    }`}>
                      {msg.content}
                    </div>
                  </div>
                ))}
              </div>
            </ScrollArea>

            <div className="p-4 border-t border-border">
              <div className="flex gap-2">
                <Input
                  value={chatInput}
                  onChange={(e) => setChatInput(e.target.value)}
                  placeholder="AI에게 수정 방향을 물어보세요..."
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
