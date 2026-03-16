"use client"

import { useState, useRef } from "react"
import { 
  Dialog, 
  DialogContent, 
  DialogHeader, 
  DialogTitle, 
  DialogDescription,
  DialogFooter
} from "@/components/ui/dialog"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { 
  Upload, 
  FileText, 
  Sparkles, 
  Loader2, 
  Image as ImageIcon,
  CheckCircle2
} from "lucide-react"

interface AddApplicationDialogProps {
  isOpen: boolean
  onClose: () => void
  onAdd: (app: { company: string; position: string; rawJd: string; aiInsight: string; questions: { title: string; maxLength: number }[] }) => void
}

export function AddApplicationDialog({ isOpen, onClose, onAdd }: AddApplicationDialogProps) {
  const [step, setStep] = useState<"input" | "extracting" | "review">("input")
  const [company, setCompany] = useState("")
  const [position, setPosition] = useState("")
  const [rawJd, setRawJd] = useState("")
  const [extractedQuestions, setExtractedQuestions] = useState<{ title: string; maxLength: number }[]>([])
  const [aiInsight, setAiInsight] = useState("")
  const [isUploading, setIsUploading] = useState(false)
  const [extractionError, setExtractionError] = useState<string | null>(null)
  
  const fileInputRef = useRef<HTMLInputElement>(null)

  const processFile = (file: File) => {
    performExtraction(null, file)
  }

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    processFile(file)
  }

  const handlePaste = (e: React.ClipboardEvent) => {
    const items = e.clipboardData?.items
    if (!items) return

    for (let i = 0; i < items.length; i++) {
      if (items[i].type.indexOf("image") !== -1) {
        const file = items[i].getAsFile()
        if (file) {
          processFile(file)
          return 
        }
      }
    }
  }

  const performExtraction = async (textToAnalyze: string | null, fileToUpload: File | null) => {
    setStep("extracting")
    setExtractionError(null)
    setIsUploading(false)
    
    try {
      let uuid = ""
      let streamUrl = ""

      if (fileToUpload) {
        // Image Mode
        const formData = new FormData()
        formData.append("image", fileToUpload)
        const initResponse = await fetch("/api/applications/analyze/upload", {
          method: "POST",
          body: formData
        })
        if (!initResponse.ok) throw new Error("이미지 분석 초기화 실패")
        const data = await initResponse.json()
        uuid = data.uuid
        streamUrl = `/api/applications/analyze/image/stream/${uuid}`
      } else if (textToAnalyze) {
        // Text Mode
        const initResponse = await fetch("/api/applications/analyze/init", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ rawJd: textToAnalyze })
        })
        if (!initResponse.ok) throw new Error("분석 초기화 실패")
        const data = await initResponse.json()
        uuid = data.uuid
        streamUrl = `/api/applications/analyze/stream/${uuid}`
      } else {
        return
      }

      // 2. Connect to SSE stream
      const eventSource = new EventSource(streamUrl)

      eventSource.addEventListener("START", (e) => {
        console.log("Extraction started:", e.data)
      })

      eventSource.addEventListener("ANALYZING", (e) => {
        console.log("AI is analyzing:", e.data)
      })

      eventSource.addEventListener("COMPLETE", (e) => {
        const data = JSON.parse(e.data)
        setCompany(data.companyName || "")
        setPosition(data.position || "")
        setRawJd(data.rawJd || textToAnalyze || "")
        const questions = (data.extractedQuestions || []).map((q: string) => ({
          title: q,
          maxLength: 1000
        }))
        setExtractedQuestions(questions)
        setAiInsight(data.aiInsight || "")
        setStep("review")
        eventSource.close()
      })

      eventSource.addEventListener("ERROR", (e) => {
        const data = JSON.parse(e.data)
        setExtractionError(data.message || "AI 분석 중 오류가 발생했습니다.")
        setStep("input")
        eventSource.close()
      })

      eventSource.onerror = (err) => {
        console.error("SSE Connection Error:", err)
        setExtractionError("서버와의 연결이 끊어졌거나 분석 시간이 초과되었습니다.")
        setStep("input")
        eventSource.close()
      }

    } catch (err) {
      console.error(err)
      setExtractionError(err instanceof Error ? err.message : "알 수 없는 오류 발생")
      setStep("input")
    }
  }

  const handleStartAiExtraction = () => {
    performExtraction(rawJd, null)
  }

  const handleReset = () => {
    setStep("input")
    setCompany("")
    setPosition("")
    setRawJd("")
    setExtractedQuestions([])
    setAiInsight("")
    setExtractionError(null)
  }

  const handleCancel = () => {
    handleReset()
    onClose()
  }

  const handleSubmit = () => {
    onAdd({ company, position, rawJd, aiInsight, questions: extractedQuestions })
    handleReset()
    onClose()
  }

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleCancel()}>
      <DialogContent className="sm:max-w-[600px] h-[90vh] max-h-[700px] flex flex-col p-0 overflow-hidden" onPaste={handlePaste}>
        <DialogHeader className="p-6 pb-0">
          <DialogTitle>새 채용 공고 추가</DialogTitle>
          <DialogDescription>
            이미지를 업로드(또는 Ctrl+V)하거나 텍스트를 붙여넣으세요. AI가 핵심 정보를 추출해 드립니다.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto px-6 py-4">
          {step === "input" && (
            <Tabs defaultValue="image" className="w-full">
              <TabsList className="grid w-full grid-cols-2">
                <TabsTrigger value="image" className="gap-2">
                  <ImageIcon className="size-4" />
                  공고 이미지 업로드
                </TabsTrigger>
                <TabsTrigger value="text" className="gap-2">
                  <FileText className="size-4" />
                  텍스트 붙여넣기
                </TabsTrigger>
              </TabsList>
              
              <TabsContent value="image" className="mt-6">
                <div 
                  onClick={() => fileInputRef.current?.click()}
                  className="border-2 border-dashed border-muted-foreground/20 rounded-xl p-12 flex flex-col items-center justify-center gap-4 hover:bg-muted/30 transition-colors cursor-pointer"
                >
                  {isUploading ? (
                    <Loader2 className="size-10 text-primary animate-spin" />
                  ) : (
                    <Upload className="size-10 text-muted-foreground" />
                  )}
                  <div className="text-center">
                    <p className="font-medium text-balance">공고 캡처 이미지를 드롭하거나 클릭하여 선택하세요</p>
                    <p className="text-sm text-muted-foreground mt-2">클립보드 이미지를 <kbd className="px-1.5 py-0.5 rounded border bg-muted text-[10px]">Ctrl+V</kbd> 해도 동작합니다</p>
                  </div>
                  <input 
                    type="file" 
                    ref={fileInputRef} 
                    className="hidden" 
                    accept="image/*"
                    onChange={handleFileUpload}
                  />
                </div>
              </TabsContent>

              <TabsContent value="text" className="mt-6 space-y-4">
                <div className="space-y-2">
                  <Label>채용 공고 전문</Label>
                  <Textarea 
                    placeholder="공고 내용을 복사해서 붙여넣으세요..." 
                    className="min-h-[300px] resize-none border-primary/20 focus-visible:ring-primary/30"
                    value={rawJd}
                    onChange={(e) => setRawJd(e.target.value)}
                  />
                </div>
                <Button 
                  onClick={handleStartAiExtraction} 
                  className="w-full gap-2 shadow-lg shadow-primary/20"
                  disabled={!rawJd}
                >
                  <Sparkles className="size-4" />
                  AI 데이터 추출 시작
                </Button>
              </TabsContent>
            </Tabs>
          )}

          {step === "extracting" && (
            <div className="flex flex-col items-center justify-center py-20 gap-6">
              <div className="relative">
                <div className="absolute inset-0 bg-primary/20 rounded-full animate-ping scale-150" />
                <div className="relative size-20 rounded-full bg-primary/10 flex items-center justify-center">
                  <Sparkles className="size-10 text-primary animate-pulse" />
                </div>
              </div>
              <div className="text-center space-y-2">
                <h3 className="text-xl font-bold italic">"AI가 공고 내용을 분석 중입니다..."</h3>
                <p className="text-sm text-muted-foreground">
                  이미지에서 텍스트를 추출하고 기업명, 직무, 문항 정보를 발라내고 있습니다.
                </p>
              </div>
              <div className="w-full max-w-xs space-y-2">
                <div className="h-1.5 w-full bg-muted rounded-full overflow-hidden">
                  <div className="h-full bg-primary animate-progress" />
                </div>
                <p className="text-[10px] text-center text-muted-foreground uppercase tracking-widest">
                  Processing Neural Network...
                </p>
              </div>
            </div>
          )}

          {step === "review" && (
            <div className="space-y-6 pb-4">
              <div className="flex items-center gap-3 text-emerald-600 bg-emerald-50/50 p-4 rounded-xl border border-emerald-100 shadow-sm">
                <div className="size-8 rounded-full bg-emerald-500 text-white flex items-center justify-center shrink-0">
                  <CheckCircle2 className="size-5" />
                </div>
                <p className="text-sm font-bold">AI가 핵심 정보를 성공적으로 추출했습니다!</p>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label className="text-xs font-bold text-muted-foreground uppercase tracking-tighter">가져온 기업명</Label>
                  <Input 
                    value={company} 
                    onChange={(e) => setCompany(e.target.value)} 
                    className="bg-muted/30 border-none font-bold"
                  />
                </div>
                <div className="space-y-2">
                  <Label className="text-xs font-bold text-muted-foreground uppercase tracking-tighter">가져온 지원 직무</Label>
                  <Input 
                    value={position} 
                    onChange={(e) => setPosition(e.target.value)} 
                    className="bg-muted/30 border-none font-bold"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label className="text-xs font-bold text-muted-foreground uppercase tracking-tighter">추출된 JD 원문</Label>
                <Textarea 
                  value={rawJd} 
                  onChange={(e) => setRawJd(e.target.value)}
                  className="min-h-[150px] bg-muted/20 border-none text-sm leading-relaxed"
                />
              </div>

              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <Label className="text-xs font-bold text-muted-foreground uppercase tracking-tighter">추출된 자소서 문항</Label>
                  <Button 
                    variant="outline" 
                    size="sm" 
                    className="h-7 text-[10px] gap-1 px-2 rounded-lg border-primary/20"
                    onClick={() => setExtractedQuestions([...extractedQuestions, { title: "", maxLength: 1000 }])}
                  >
                    <Plus className="size-3" />
                    문항 추가
                  </Button>
                </div>
                <div className="space-y-4">
                  {extractedQuestions.map((q, idx) => (
                    <div key={idx} className="p-4 rounded-xl bg-muted/30 border border-border/50 space-y-3 relative group">
                      <div className="flex items-start gap-3">
                        <span className="size-6 shrink-0 rounded-lg bg-primary/10 text-primary flex items-center justify-center text-[10px] font-black mt-1">
                          {idx + 1}
                        </span>
                        <Textarea 
                          value={q.title}
                          onChange={(e) => {
                            const newQs = [...extractedQuestions]
                            newQs[idx].title = e.target.value
                            setExtractedQuestions(newQs)
                          }}
                          placeholder="문항 내용을 입력하세요..."
                          className="flex-1 min-h-[80px] text-sm bg-transparent border-none focus-visible:ring-0 p-0 resize-none font-medium leading-relaxed"
                        />
                        <Button 
                          variant="ghost" 
                          size="icon" 
                          className="size-8 rounded-full text-muted-foreground hover:text-destructive opacity-0 group-hover:opacity-100 transition-opacity"
                          onClick={() => setExtractedQuestions(extractedQuestions.filter((_, i) => i !== idx))}
                        >
                          <Trash2 className="size-4" />
                        </Button>
                      </div>
                      
                      <div className="pt-3 border-t border-border/10 space-y-2.5">
                        <div className="flex items-center justify-between text-[11px] font-bold text-muted-foreground mb-1 px-1">
                          <span>글자수 제한 (공백 포함)</span>
                          <span className="font-black text-primary/60">{q.maxLength.toLocaleString()}자</span>
                        </div>
                        <div className="relative">
                          <Input 
                            type="number" 
                            value={q.maxLength}
                            onChange={(e) => {
                              const newQs = [...extractedQuestions]
                              newQs[idx].maxLength = parseInt(e.target.value) || 0
                              setExtractedQuestions(newQs)
                            }}
                            className="w-full h-10 pl-4 pr-10 text-[13px] font-black bg-muted/30 border-primary/10 focus-visible:ring-primary/20 rounded-xl"
                          />
                          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-black text-muted-foreground/40">자</span>
                        </div>
                        <div className="grid grid-cols-4 gap-2">
                          {[-100, -50, 50, 100].map((val) => (
                            <Button
                              key={val}
                              variant="outline"
                              size="sm"
                              className="h-9 text-[11px] font-black w-full border-primary/10 hover:bg-primary/20 transition-all shadow-sm rounded-lg"
                              onClick={() => {
                                const newQs = [...extractedQuestions]
                                newQs[idx].maxLength = Math.max(0, newQs[idx].maxLength + val)
                                setExtractedQuestions(newQs)
                              }}
                            >
                              {val > 0 ? `+${val}` : val}
                            </Button>
                          ))}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
              
              {aiInsight && (
                <Card className="p-4 bg-primary/5 border border-primary/10 shadow-none rounded-xl">
                  <div className="flex items-center gap-2 mb-2">
                    <Sparkles className="size-4 text-primary" />
                    <span className="text-sm font-bold">AI 인사이트</span>
                  </div>
                  <p className="text-xs text-muted-foreground leading-relaxed whitespace-pre-line">
                    {aiInsight}
                  </p>
                </Card>
              )}
            </div>
          )}
        </div>

        <DialogFooter className="p-6 pt-4 border-t bg-muted/10 flex sm:justify-between items-center">
          <Button variant="ghost" onClick={handleCancel} className="h-11">취소</Button>
          {step === "review" && (
            <Button onClick={handleSubmit} className="gap-2 h-11 px-8 rounded-full shadow-lg shadow-primary/20">
              최종 공고 등록하기
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
