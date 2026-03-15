"use client"
 
import * as React from "react"

import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ScrollArea } from "@/components/ui/scroll-area"
import { FileText, ClipboardList, Wand2, Trash2, Clock, CheckCircle2, XCircle, Calendar as CalendarIcon, ChevronDown, Check, Save } from "lucide-react"
import { format } from "date-fns"
import { ko } from "date-fns/locale"
import { cn } from "@/lib/utils"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Calendar } from "@/components/ui/calendar"
import { useRouter } from "next/navigation"
import { Button } from "@/components/ui/button"
import { type Application } from "@/lib/mock-data"
import { JDAnalysisPanel } from "./jd-analysis-panel"
import { QuestionsPanel } from "./questions-panel"
import { columns } from "./index"
import { 
  DropdownMenu, 
  DropdownMenuContent, 
  DropdownMenuItem, 
  DropdownMenuTrigger 
} from "@/components/ui/dropdown-menu"

export function ApplicationDetailSheet({ 
  application, 
  isOpen, 
  onClose,
  onRefresh,
  onUpdateApplication,
  onDelete
}: { 
  application: Application | null
  isOpen: boolean
  onClose: () => void
  onRefresh: () => void
  onUpdateApplication: (updates: Partial<Application>) => void
  onDelete: (id: string) => void
}) {
  const router = useRouter()
  const [localDeadline, setLocalDeadline] = React.useState<Date | null>(null)
  const [isCalendarOpen, setIsCalendarOpen] = React.useState(false)

  React.useEffect(() => {
    if (application?.deadline) {
      setLocalDeadline(new Date(application.deadline))
    } else {
      setLocalDeadline(null)
    }
  }, [application?.deadline, isOpen])

  if (!application) return null

  const handleUpdate = async (updates: Partial<Application>) => {
    onUpdateApplication(updates)
  }

  // Convert Date/string to datetime-local format (YYYY-MM-DDTHH:mm)
  const formatForInput = (date: Date | string | null | undefined) => {
    if (!date) return ""
    const d = new Date(date)
    return d.toISOString().slice(0, 16)
  }

  const currentStatus = columns.find(c => c.id === application.status)

  return (
    <Sheet open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full sm:max-w-lg p-0 flex flex-col h-full">
        <SheetHeader className="px-6 py-5 border-b border-border bg-muted/5 shrink-0">
          <div className="flex items-center gap-3 mb-4">
            <div className={`size-12 rounded-xl ${application.logoColor} flex items-center justify-center shadow-sm`}>
              <span className="text-lg font-bold text-white">
                {application.company.charAt(0)}
              </span>
            </div>
            <div className="flex-1 min-w-0">
              <SheetTitle className="text-left truncate">{application.company}</SheetTitle>
              <p className="text-sm text-muted-foreground truncate">{application.position}</p>
            </div>
            <div className="flex items-center gap-2">
              <Button 
                onClick={() => router.push(`/workspace/${application.id}`)}
                className="gap-2 rounded-full shadow-sm bg-primary hover:bg-primary/90 hidden sm:flex"
              >
                <Wand2 className="size-4" />
                작업실
              </Button>
              
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="icon" className="size-10 rounded-full">
                    <Trash2 className="size-5 text-muted-foreground hover:text-destructive transition-colors" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem 
                    className="text-destructive focus:text-destructive cursor-pointer font-medium"
                    onClick={() => onDelete(application.id)}
                  >
                    공고 삭제
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="text-[10px] font-bold uppercase text-muted-foreground ml-1">현재 전형 상태</label>
              <select 
                value={application.status}
                onChange={(e) => handleUpdate({ status: e.target.value as any })}
                className="w-full h-10 px-3 py-1 text-sm bg-background border border-border rounded-xl focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all cursor-pointer font-medium"
              >
                {columns.map(col => (
                  <option key={col.id} value={col.id}>{col.title}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5 flex flex-col">
              <label className="text-[10px] font-bold uppercase text-muted-foreground ml-1">제출/면접 마감일</label>
              <Popover open={isCalendarOpen} onOpenChange={setIsCalendarOpen}>
                <PopoverTrigger asChild>
                  <Button
                    variant="outline"
                    className={cn(
                      "w-full h-10 px-3 justify-start text-left font-medium rounded-xl border-border bg-background hover:bg-muted/50 transition-all",
                      !localDeadline && "text-muted-foreground",
                      localDeadline?.toISOString() !== (application.deadline ? new Date(application.deadline).toISOString() : null) && "border-primary ring-1 ring-primary/20"
                    )}
                  >
                    <CalendarIcon className="mr-2 h-4 w-4 text-primary" />
                    {localDeadline ? (
                      format(localDeadline, "PPP p", { locale: ko })
                    ) : (
                      <span>날짜 선택</span>
                    )}
                    <ChevronDown className="ml-auto h-4 w-4 opacity-50" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-4 rounded-2xl shadow-2xl border-border/50" align="end">
                  <div className="space-y-4">
                    <Calendar
                      mode="single"
                      selected={localDeadline || undefined}
                      onSelect={(date) => {
                        if (date) {
                          const newDate = new Date(date)
                          if (localDeadline) {
                            newDate.setHours(localDeadline.getHours())
                            newDate.setMinutes(localDeadline.getMinutes())
                          } else {
                            newDate.setHours(9, 0, 0, 0)
                          }
                          setLocalDeadline(newDate)
                        }
                      }}
                      initialFocus
                      className="rounded-xl border border-border/50"
                    />
                    <div className="flex flex-col gap-2">
                      <label className="text-[10px] font-bold uppercase text-muted-foreground ml-1">상세 시간 설정</label>
                      <div className="flex flex-col gap-3">
                        <div className="flex items-center gap-2 bg-muted/20 p-1 rounded-2xl border border-border/40 shadow-inner">
                          {/* AM/PM Toggle */}
                          <div className="flex bg-muted/50 rounded-xl p-1 gap-1">
                            {["AM", "PM"].map((period) => {
                              const isPM = localDeadline ? localDeadline.getHours() >= 12 : false
                              const active = period === (isPM ? "PM" : "AM")
                              return (
                                <button
                                  key={period}
                                  onClick={() => {
                                    const newDate = localDeadline ? new Date(localDeadline) : new Date()
                                    const currentHours = newDate.getHours()
                                    if (period === "AM" && currentHours >= 12) {
                                      newDate.setHours(currentHours - 12)
                                    } else if (period === "PM" && currentHours < 12) {
                                      newDate.setHours(currentHours + 12)
                                    }
                                    setLocalDeadline(newDate)
                                  }}
                                  className={cn(
                                    "px-3 py-1.5 text-[10px] font-bold rounded-lg transition-all",
                                    active ? "bg-background text-primary shadow-sm" : "text-muted-foreground hover:bg-background/30"
                                  )}
                                >
                                  {period === "AM" ? "오전" : "오후"}
                                </button>
                              )
                            })}
                          </div>

                          {/* Time Selects */}
                          <div className="flex-1 flex items-center justify-center gap-1">
                            <select
                              value={localDeadline ? (((localDeadline.getHours() + 11) % 12) + 1).toString().padStart(2, '0') : "10"}
                              onChange={(e) => {
                                const hours12 = parseInt(e.target.value)
                                const isPM = localDeadline ? localDeadline.getHours() >= 12 : false
                                const newDate = localDeadline ? new Date(localDeadline) : new Date()
                                let newHours24 = hours12
                                if (isPM && hours12 < 12) newHours24 += 12
                                if (!isPM && hours12 === 12) newHours24 = 0
                                newDate.setHours(newHours24)
                                setLocalDeadline(newDate)
                              }}
                              className="bg-transparent text-sm font-bold w-12 focus:outline-none cursor-pointer appearance-none text-center hover:text-primary transition-colors"
                            >
                              {Array.from({ length: 12 }).map((_, i) => {
                                const h = (i + 1).toString().padStart(2, '0')
                                return <option key={h} value={h}>{h}시</option>
                              })}
                            </select>
                            <span className="text-muted-foreground font-bold animate-pulse">:</span>
                            <select
                              value={localDeadline ? format(localDeadline, "mm") : "00"}
                              onChange={(e) => {
                                const newDate = localDeadline ? new Date(localDeadline) : new Date()
                                newDate.setMinutes(parseInt(e.target.value))
                                setLocalDeadline(newDate)
                              }}
                              className="bg-transparent text-sm font-bold w-12 focus:outline-none cursor-pointer appearance-none text-center hover:text-primary transition-colors"
                            >
                              {[0, 10, 20, 30, 40, 50].map((m) => {
                                const min = m.toString().padStart(2, '0')
                                return <option key={min} value={min}>{min}분</option>
                              })}
                            </select>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="flex flex-col gap-2 pt-2 border-t border-border/50">
                      <Button 
                        className="w-full gap-2 rounded-xl shadow-lg shadow-primary/20 h-10 mt-2"
                        disabled={!localDeadline || localDeadline.toISOString() === (application.deadline ? new Date(application.deadline).toISOString() : null)}
                        onClick={() => {
                          if (localDeadline) {
                            handleUpdate({ deadline: format(localDeadline, "yyyy-MM-dd'T'HH:mm:ss") })
                            setIsCalendarOpen(false)
                          }
                        }}
                      >
                        <Save className="size-4" />
                        변경 사항 적용하기
                      </Button>
                    </div>
                  </div>
                </PopoverContent>
              </Popover>
            </div>
          </div>

          <div className="space-y-1.5 mt-4">
            <label className="text-[10px] font-bold uppercase text-muted-foreground ml-1">전형 결과</label>
            <div className="flex gap-2 p-1 bg-muted/30 rounded-2xl border border-muted-foreground/10">
              <Button 
                variant="ghost"
                size="sm"
                className={`flex-1 rounded-xl h-9 text-xs transition-all ${application.result === "pending" ? "bg-background shadow-sm text-foreground font-bold" : "text-muted-foreground hover:bg-background/50"}`}
                onClick={() => handleUpdate({ result: "pending" })}
              >
                <Clock className="size-3.5 mr-1.5" /> 대기
              </Button>
              <Button 
                variant="ghost"
                size="sm"
                className={`flex-1 rounded-xl h-9 text-xs transition-all ${application.result === "pass" ? "bg-emerald-500 text-white shadow-md font-bold hover:bg-emerald-600" : "text-muted-foreground hover:bg-emerald-100/50"}`}
                onClick={() => handleUpdate({ result: "pass" })}
              >
                <CheckCircle2 className="size-3.5 mr-1.5" /> 합격
              </Button>
              <Button 
                variant="ghost"
                size="sm"
                className={`flex-1 rounded-xl h-9 text-xs transition-all ${application.result === "fail" ? "bg-red-500 text-white shadow-md font-bold hover:bg-red-600" : "text-muted-foreground hover:bg-red-100/50"}`}
                onClick={() => handleUpdate({ result: "fail" })}
              >
                <XCircle className="size-3.5 mr-1.5" /> 불참/탈락
              </Button>
            </div>
          </div>
        </SheetHeader>

        <Tabs 
          key={application.id + (application.aiInsight ? "-insight" : "-noinsight")}
          defaultValue={application.aiInsight ? "questions" : "jd"} 
          className="flex-1 flex flex-col h-full min-h-0"
        >
          <TabsList className="w-full justify-start rounded-none border-b border-border bg-background p-0 h-auto shrink-0">
            <TabsTrigger 
              value="jd" 
              className="rounded-none border-b-2 border-transparent data-[state=active]:border-primary data-[state=active]:bg-transparent px-6 py-3 gap-2"
            >
              <FileText className="size-4" />
              JD 분석기
            </TabsTrigger>
            <TabsTrigger 
              value="questions"
              className="rounded-none border-b-2 border-transparent data-[state=active]:border-primary data-[state=active]:bg-transparent px-6 py-3 gap-2"
            >
              <ClipboardList className="size-4" />
              자소서 문항 관리
            </TabsTrigger>
          </TabsList>

          <ScrollArea className="flex-1 h-full min-h-0">
            <TabsContent value="jd" className="m-0 p-6">
              <JDAnalysisPanel application={application} />
            </TabsContent>
            <TabsContent value="questions" className="m-0 p-6 outline-none">
              <QuestionsPanel 
                application={application} 
                onRefresh={onRefresh} 
                onUpdateApplication={onUpdateApplication}
              />
            </TabsContent>
          </ScrollArea>
        </Tabs>
      </SheetContent>
    </Sheet>
  )
}
