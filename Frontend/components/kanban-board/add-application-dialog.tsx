"use client"

import { useRef, useState } from "react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  CheckCircle2,
  FileText,
  Image as ImageIcon,
  Loader2,
  Plus,
  Sparkles,
  Trash2,
  Upload,
} from "lucide-react"

interface AddApplicationDialogProps {
  isOpen: boolean
  onClose: () => void
  onAdd: (app: {
    company: string
    position: string
    rawJd: string
    aiInsight: string
    questions: { title: string; maxLength: number }[]
  }) => void
}

export function AddApplicationDialog({
  isOpen,
  onClose,
  onAdd,
}: AddApplicationDialogProps) {
  const [step, setStep] = useState<"input" | "extracting" | "review">("input")
  const [company, setCompany] = useState("")
  const [position, setPosition] = useState("")
  const [rawJd, setRawJd] = useState("")
  const [extractedQuestions, setExtractedQuestions] = useState<
    { title: string; maxLength: number }[]
  >([])
  const [aiInsight, setAiInsight] = useState("")
  const [isUploading, setIsUploading] = useState(false)
  const [extractionError, setExtractionError] = useState<string | null>(null)

  const fileInputRef = useRef<HTMLInputElement>(null)

  const processFile = (file: File) => {
    setIsUploading(true)
    void performExtraction(null, file)
  }

  const handleFileUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    processFile(file)
  }

  const handlePaste = (event: React.ClipboardEvent) => {
    const items = event.clipboardData?.items
    if (!items) return

    for (let index = 0; index < items.length; index += 1) {
      if (!items[index].type.includes("image")) continue
      const file = items[index].getAsFile()
      if (!file) continue
      processFile(file)
      return
    }
  }

  const performExtraction = async (
    textToAnalyze: string | null,
    fileToUpload: File | null
  ) => {
    setStep("extracting")
    setExtractionError(null)
    setIsUploading(!!fileToUpload)

    try {
      let uuid = ""
      let streamUrl = ""

      if (fileToUpload) {
        const formData = new FormData()
        formData.append("image", fileToUpload)

        const initResponse = await fetch("/api/applications/analyze/upload", {
          method: "POST",
          body: formData,
        })

        if (!initResponse.ok) {
          throw new Error("이미지 분석 초기화에 실패했습니다.")
        }

        const data = await initResponse.json()
        uuid = data.uuid
        streamUrl = `/api/applications/analyze/image/stream/${uuid}`
      } else if (textToAnalyze) {
        const initResponse = await fetch("/api/applications/analyze/init", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ rawJd: textToAnalyze }),
        })

        if (!initResponse.ok) {
          throw new Error("JD 분석 초기화에 실패했습니다.")
        }

        const data = await initResponse.json()
        uuid = data.uuid
        streamUrl = `/api/applications/analyze/stream/${uuid}`
      } else {
        setStep("input")
        setIsUploading(false)
        return
      }

      const eventSource = new EventSource(streamUrl)

      eventSource.addEventListener("COMPLETE", (event) => {
        const data = JSON.parse(event.data)
        setCompany(data.companyName || "")
        setPosition(data.position || "")
        setRawJd(data.rawJd || textToAnalyze || "")
        setExtractedQuestions([])
        setAiInsight(data.aiInsight || "")
        setIsUploading(false)
        setStep("review")
        eventSource.close()
      })

      eventSource.addEventListener("ERROR", (event) => {
        try {
          const data = JSON.parse(event.data)
          setExtractionError(data.message || "AI 분석 중 오류가 발생했습니다.")
        } catch {
          setExtractionError("AI 분석 중 오류가 발생했습니다.")
        }
        setIsUploading(false)
        setStep("input")
        eventSource.close()
      })

      eventSource.onerror = () => {
        setExtractionError("분석 스트림 연결이 중간에 끊어졌습니다.")
        setIsUploading(false)
        setStep("input")
        eventSource.close()
      }
    } catch (error) {
      setExtractionError(
        error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다."
      )
      setIsUploading(false)
      setStep("input")
    }
  }

  const handleStartAiExtraction = () => {
    void performExtraction(rawJd, null)
  }

  const handleReset = () => {
    setStep("input")
    setCompany("")
    setPosition("")
    setRawJd("")
    setExtractedQuestions([])
    setAiInsight("")
    setIsUploading(false)
    setExtractionError(null)
  }

  const handleCancel = () => {
    handleReset()
    onClose()
  }

  const handleSubmit = () => {
    onAdd({
      company,
      position,
      rawJd,
      aiInsight,
      questions: [],
    })
    handleReset()
    onClose()
  }

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleCancel()}>
      <DialogContent
        className="flex h-[90vh] max-h-[700px] flex-col overflow-hidden p-0 sm:max-w-[600px]"
        onPaste={handlePaste}
      >
        <DialogHeader className="p-6 pb-0">
          <DialogTitle>새 채용 공고 추가</DialogTitle>
          <DialogDescription>
            이미지 업로드나 텍스트 붙여넣기로 JD를 넣으면 AI가 기업명, 직무,
            자소서 문항을 추출합니다.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto px-6 py-4">
          {step === "input" && (
            <div className="space-y-4">
              {extractionError && (
                <Card className="border-destructive/20 bg-destructive/5 p-4 text-sm text-destructive shadow-none">
                  {extractionError}
                </Card>
              )}

              <Tabs defaultValue="image" className="w-full">
                <TabsList className="grid w-full grid-cols-2">
                  <TabsTrigger value="image" className="gap-2">
                    <ImageIcon className="size-4" />
                    이미지 업로드
                  </TabsTrigger>
                  <TabsTrigger value="text" className="gap-2">
                    <FileText className="size-4" />
                    텍스트 붙여넣기
                  </TabsTrigger>
                </TabsList>

                <TabsContent value="image" className="mt-6">
                  <div
                    onClick={() => fileInputRef.current?.click()}
                    className="flex cursor-pointer flex-col items-center justify-center gap-4 rounded-xl border-2 border-dashed border-muted-foreground/20 p-12 transition-colors hover:bg-muted/30"
                  >
                    {isUploading ? (
                      <Loader2 className="size-10 animate-spin text-primary" />
                    ) : (
                      <Upload className="size-10 text-muted-foreground" />
                    )}
                    <div className="space-y-2 text-center">
                      <p className="font-medium text-balance">
                        공고 캡처 이미지를 클릭하거나 붙여넣어 주세요.
                      </p>
                      <p className="text-sm text-muted-foreground">
                        클립보드 이미지도 <kbd className="rounded border bg-muted px-1.5 py-0.5 text-[10px]">Ctrl+V</kbd> 로 넣을 수 있습니다.
                      </p>
                    </div>
                    <input
                      ref={fileInputRef}
                      type="file"
                      className="hidden"
                      accept="image/*"
                      onChange={handleFileUpload}
                    />
                  </div>
                </TabsContent>

                <TabsContent value="text" className="mt-6 space-y-4">
                  <div className="space-y-2">
                    <Label>채용 공고 원문</Label>
                    <Textarea
                      placeholder="채용 공고 내용을 붙여 넣어 주세요."
                      className="min-h-[300px] resize-none border-primary/20 focus-visible:ring-primary/30"
                      value={rawJd}
                      onChange={(event) => setRawJd(event.target.value)}
                    />
                  </div>
                  <Button
                    onClick={handleStartAiExtraction}
                    className="w-full gap-2 shadow-lg shadow-primary/20"
                    disabled={!rawJd.trim()}
                  >
                    <Sparkles className="size-4" />
                    AI 추출 시작
                  </Button>
                </TabsContent>
              </Tabs>
            </div>
          )}

          {step === "extracting" && (
            <div className="flex flex-col items-center justify-center gap-6 py-20">
              <div className="relative">
                <div className="absolute inset-0 scale-150 rounded-full bg-primary/20 animate-ping" />
                <div className="relative flex size-20 items-center justify-center rounded-full bg-primary/10">
                  <Sparkles className="size-10 animate-pulse text-primary" />
                </div>
              </div>
              <div className="space-y-2 text-center">
                <h3 className="text-xl font-bold italic">
                  "AI가 공고를 분석하고 있습니다..."
                </h3>
                <p className="text-sm text-muted-foreground">
                  OCR, JD 구조화, 문항 추출을 순서대로 처리하고 있습니다.
                </p>
              </div>
              <div className="w-full max-w-xs space-y-2">
                <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
                  <div className="h-full animate-progress bg-primary" />
                </div>
                <p className="text-center text-[10px] uppercase tracking-widest text-muted-foreground">
                  Processing Job Description...
                </p>
              </div>
            </div>
          )}

          {step === "review" && (
            <div className="space-y-6 pb-4">
              <div className="flex items-center gap-3 rounded-xl border border-emerald-100 bg-emerald-50/50 p-4 text-emerald-600 shadow-sm">
                <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-emerald-500 text-white">
                  <CheckCircle2 className="size-5" />
                </div>
                <p className="text-sm font-bold">
                  추출이 완료되었습니다. 등록 전에 내용을 한 번만 확인하세요.
                </p>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label className="text-xs font-bold uppercase tracking-tighter text-muted-foreground">
                    기업명
                  </Label>
                  <Input
                    value={company}
                    onChange={(event) => setCompany(event.target.value)}
                    className="border-none bg-muted/30 font-bold"
                  />
                </div>
                <div className="space-y-2">
                  <Label className="text-xs font-bold uppercase tracking-tighter text-muted-foreground">
                    지원 직무
                  </Label>
                  <Input
                    value={position}
                    onChange={(event) => setPosition(event.target.value)}
                    className="border-none bg-muted/30 font-bold"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label className="text-xs font-bold uppercase tracking-tighter text-muted-foreground">
                  추출된 JD 원문
                </Label>
                <Textarea
                  value={rawJd}
                  onChange={(event) => setRawJd(event.target.value)}
                  className="min-h-[150px] border-none bg-muted/20 text-sm leading-relaxed"
                />
              </div>

              <Card className="rounded-xl border border-border/50 bg-muted/20 p-4 shadow-none">
                <div className="space-y-2">
                  <Label className="text-xs font-bold uppercase tracking-tighter text-muted-foreground">
                    문항 추가 방식
                  </Label>
                  <p className="text-sm leading-6 text-muted-foreground">
                    공고 등록 단계에서는 문항을 자동으로 만들지 않습니다. 공고를 먼저
                    저장한 뒤, 상세 사이드바에서 필요한 문항만 직접 추가해 주세요.
                  </p>
                </div>
              </Card>

              {aiInsight && (
                <Card className="rounded-xl border border-primary/10 bg-primary/5 p-4 shadow-none">
                  <div className="mb-2 flex items-center gap-2">
                    <Sparkles className="size-4 text-primary" />
                    <span className="text-sm font-bold">AI 인사이트</span>
                  </div>
                  <p className="whitespace-pre-line text-xs leading-relaxed text-muted-foreground">
                    {aiInsight}
                  </p>
                </Card>
              )}
            </div>
          )}
        </div>

        <DialogFooter className="flex items-center border-t bg-muted/10 p-6 pt-4 sm:justify-between">
          <Button variant="ghost" onClick={handleCancel} className="h-11">
            취소
          </Button>
          {step === "review" && (
            <Button
              onClick={handleSubmit}
              className="h-11 gap-2 rounded-full px-8 shadow-lg shadow-primary/20"
            >
              최종 공고 등록하기
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
