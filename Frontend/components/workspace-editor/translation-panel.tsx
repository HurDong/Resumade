"use client"

import { useEffect, useRef } from "react"

import {
  FileText,
  AlertTriangle,
  CheckCircle,
  Lightbulb,
  ArrowRight,
  Wand2,
  Copy,
} from "lucide-react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { useWorkspaceStore } from "@/lib/store/workspace-store"

export function TranslationPanel() {
  const { 
    questions, 
    activeQuestionId, 
    isProcessing, 
    progressMessage,
    hoveredMistranslationId,
    setHoveredMistranslationId
  } = useWorkspaceStore()
  
  const containerRef = useRef<HTMLDivElement>(null)
  const activeQuestion = questions.find(q => q.id === activeQuestionId) || questions[0]
  const { content: draft, washedKr, mistranslations, aiReviewReport } = activeQuestion

  // Helper to escape regex special characters
  const escapeRegExp = (string: string) => {
    return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  };

  // Function to highlight mistranslated words in a single pass to avoid tag corruption
  const highlightText = (text: string) => {
    if (!text || !mistranslations.length) return text;
    
    // Normalize text to NFC
    const normalizedText = text.normalize('NFC');
    const cleanText = normalizedText.replace(/\s+/g, '').toLowerCase();
    
    // Find all occurrences of all mistranslations
    const matches: { start: number; end: number; mis: any }[] = [];
    
    mistranslations.forEach((mis) => {
      if (!mis.translated) return;
      
      const target = mis.translated.normalize('NFC').trim();
      const cleanTarget = target.replace(/\s+/g, '').toLowerCase();
      
      if (!cleanTarget) return;

      // Try exact match first
      const escaped = escapeRegExp(target);
      const exactRegex = new RegExp(escaped, "g");
      let exactMatch;
      let foundExact = false;
      
      while ((exactMatch = exactRegex.exec(normalizedText)) !== null) {
        matches.push({ start: exactMatch.index, end: exactMatch.index + target.length, mis });
        foundExact = true;
      }

      // If exact match fails, try soft match (ignoring spaces and case)
      if (!foundExact) {
        let lastPos = 0;
        while (true) {
          const foundIdx = cleanText.indexOf(cleanTarget, lastPos);
          if (foundIdx === -1) break;
          
          // Map clean index back to original index
          // This is complex, but we can approximate or find the actual indices in normalizedText
          let realStart = -1;
          let realEnd = -1;
          let currentCleanIdx = 0;
          
          for (let i = 0; i < normalizedText.length; i++) {
            if (!/\s/.test(normalizedText[i])) {
              if (currentCleanIdx === foundIdx) realStart = i;
              if (currentCleanIdx === foundIdx + cleanTarget.length - 1) {
                realEnd = i + 1;
                break;
              }
              currentCleanIdx++;
            }
          }
          
          if (realStart !== -1 && realEnd !== -1) {
            matches.push({ start: realStart, end: realEnd, mis });
          }
          
          lastPos = foundIdx + 1;
        }
      }
    });

    if (matches.length === 0) return normalizedText;

    // Sort and build result as before...
    // Sort matches by start index, then by length (longer first)
    matches.sort((a, b) => a.start - b.start || (b.end - b.start) - (a.end - a.start));

    const finalMatches: typeof matches = [];
    let lastEnd = -1;
    for (const match of matches) {
      if (match.start >= lastEnd) {
        finalMatches.push(match);
        lastEnd = match.end;
      }
    }

    let result = "";
    let lastIndex = 0;
    const hasAnyHover = hoveredMistranslationId !== null;

    finalMatches.forEach((match) => {
      result += normalizedText.substring(lastIndex, match.start);
      const mis = match.mis;
      const severityClass = mis.severity === "high" ? "bg-highlight-error text-foreground" : "bg-highlight-warning text-foreground";
      const isHovered = hoveredMistranslationId === mis.id;
      const hoverEffect = isHovered ? "ring-2 ring-primary scale-110 shadow-2xl z-[50] opacity-100" : hasAnyHover ? "opacity-20 grayscale scale-95 blur-[0.5px]" : "opacity-100";
      result += `<mark data-mis-id="${mis.id}" class="${severityClass} ${hoverEffect} px-0.5 rounded cursor-help transition-all duration-300 inline-block origin-center">${normalizedText.substring(match.start, match.end)}</mark>`;
      lastIndex = match.end;
    });

    result += normalizedText.substring(lastIndex);
    return result;
  };

  // Function to highlight original words corresponding to mistranslations
  const highlightOriginalText = (text: string) => {
    if (!text) return ""
    
    // Normalize text and mistranslations to NFC for consistent matching
    const normalizedText = text.normalize('NFC');
    let result = normalizedText;
    
    mistranslations.forEach((mis) => {
      if (!mis.original) return;
      const normalizedOriginal = mis.original.normalize('NFC');
      const escaped = escapeRegExp(normalizedOriginal);
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
      <div className="flex flex-col items-center justify-center h-full bg-muted/5 text-muted-foreground p-10 text-center">
        <div className="relative mb-6">
          <Wand2 className="size-16 opacity-20" />
          <div className="absolute inset-0 size-16 bg-primary/10 rounded-full blur-2xl -z-10" />
        </div>
        <h3 className="text-xl font-bold mb-3">휴먼 패치 작업실</h3>
        <p className="text-sm max-w-md text-balance text-muted-foreground/80 leading-relaxed font-medium">
          왼쪽 패널에서 초안을 생성하면 [영어 → 한국어] 역번역을 통한 
          AI 탐지 우회 및 문맥 정제 과정이 이곳에 표시됩니다.
        </p>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col bg-muted/10 overflow-hidden">
      {/* Header */}
      <div className="shrink-0 border-b border-border p-6 bg-background/50 backdrop-blur-md sticky top-0 z-10">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-bold flex items-center gap-2">
              <CheckCircle className="size-5 text-emerald-500" />
              휴먼 패치 결과
            </h3>
            <p className="text-xs text-muted-foreground mt-1">
              역번역(EN→KR)을 거쳐 기계적인 문투를 지우고 자연스럽게 다듬었습니다.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="outline"
              className="gap-2 h-8 px-3 font-bold text-[11px] rounded-lg border-primary/20 hover:bg-primary/5 transition-all shadow-sm"
              onClick={() => {
                navigator.clipboard.writeText(washedKr);
                toast.success("클립보드에 복사되었습니다.", {
                  description: "지원서에 바로 붙여넣어 사용하세요.",
                  position: "top-center"
                });
              }}
              disabled={!washedKr}
            >
              <Copy className="size-3.5" />
              복사하기
            </Button>
            {isProcessing && (
              <Badge variant="secondary" className="animate-in fade-in zoom-in-95 gap-2 py-1.5 px-3">
                <div className="size-2 rounded-full bg-primary animate-pulse" />
                <span className="text-[11px] font-bold">{progressMessage}</span>
              </Badge>
            )}
          </div>
        </div>
      </div>

      <ScrollArea className="flex-1 h-full min-h-0">
        <div className="p-6 pb-20 space-y-8 animate-in fade-in slide-in-from-bottom-2 duration-500">
          <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
            {/* Original AI Draft */}
            <Card className="shadow-sm border-none bg-background/80">
              <CardHeader className="pb-4">
                <CardTitle className="text-xs font-black uppercase tracking-widest text-muted-foreground/60 flex items-center gap-2">
                  <FileText className="size-3.5" />
                  원문 (AI 초안)
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div 
                  className={`text-sm leading-relaxed text-muted-foreground whitespace-pre-wrap min-h-[200px] font-medium italic transition-all duration-300 ${hoveredMistranslationId ? 'opacity-100' : 'opacity-70'}`}
                  dangerouslySetInnerHTML={{ __html: highlightOriginalText(draft) }}
                />
              </CardContent>
            </Card>
 
            {/* Washed Version (Human Patch) */}
            <Card className="shadow-lg border-primary/20 bg-background ring-1 ring-primary/5">
              <CardHeader className="pb-4">
                <CardTitle className="text-xs font-bold uppercase tracking-widest text-primary flex items-center gap-2">
                  <CheckCircle className="size-3.5" />
                  번역본 (Human Patch)
                </CardTitle>
                <CardDescription className="text-[10px] text-muted-foreground/80 italic font-semibold">
                  하이라이트된 용어는 IT 문맥에 맞춰 검수가 필요한 부분입니다.
                </CardDescription>
              </CardHeader>
              <CardContent ref={containerRef}>
                <div
                  className="text-sm leading-relaxed whitespace-pre-wrap min-h-[200px] font-medium"
                  dangerouslySetInnerHTML={{ __html: highlightText(washedKr) }}
                />
                {!washedKr && isProcessing && (
                  <div className="flex flex-col items-center justify-center py-20 gap-3 opacity-20">
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
