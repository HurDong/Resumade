"use client"

import * as React from "react"
import { useRouter } from "next/navigation"
import { format } from "date-fns"
import { ko } from "date-fns/locale"
import {
  Calendar as CalendarIcon,
  CalendarDays,
  CheckCircle2,
  ChevronDown,
  ClipboardList,
  Clock,
  FileText,
  Save,
  Trash2,
  Wand2,
  XCircle,
} from "lucide-react"
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Calendar } from "@/components/ui/calendar"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { type Application } from "@/lib/mock-data"
import { JDAnalysisPanel } from "./jd-analysis-panel"
import { CompanyResearchPanel } from "./company-research-panel"
import { QuestionsPanel } from "./questions-panel"
import { ApplicationSchedulePanel } from "./application-schedule-panel"
import { columns } from "./index"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"

export function ApplicationDetailSheet({
  application,
  isOpen,
  onClose,
  onRefresh,
  onUpdateApplication,
  onDelete,
}: {
  application: Application | null
  isOpen: boolean
  onClose: () => void
  onRefresh: () => void
  onUpdateApplication: (updates: Partial<Application>) => Promise<void>
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
    await onUpdateApplication(updates)
  }

  return (
    <Sheet open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="flex h-full !w-[min(100vw-12px,980px)] min-w-0 flex-col overflow-hidden rounded-l-3xl p-0 !max-w-none">
        <SheetHeader className="shrink-0 border-b border-border bg-muted/5 px-4 py-4 sm:px-6 sm:py-5 lg:px-8">
          <div className="mb-4 flex min-w-0 flex-wrap items-start gap-3 sm:items-center">
            <div
              className={`flex size-12 items-center justify-center rounded-xl ${application.logoColor} shadow-sm`}
            >
              <span className="text-lg font-bold text-white">
                {application.company.charAt(0)}
              </span>
            </div>
            <div className="min-w-0 flex-1">
              <SheetTitle className="truncate text-left">
                {application.company}
              </SheetTitle>
              <p className="truncate text-sm text-muted-foreground">
                {application.position}
              </p>
            </div>
            <div className="ml-auto flex shrink-0 items-center gap-2">
              <Button
                onClick={() => router.push(`/workspace?applicationId=${application.id}`)}
                className="hidden gap-2 rounded-full bg-primary shadow-sm hover:bg-primary/90 sm:flex"
              >
                <Wand2 className="size-4" />
                {"\uc791\uc5c5\uc2e4"}
              </Button>

              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="icon" className="size-10 rounded-full">
                    <Trash2 className="size-5 text-muted-foreground transition-colors hover:text-destructive" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem
                    className="cursor-pointer font-medium text-destructive focus:text-destructive"
                    onClick={() => onDelete(application.id)}
                  >
                    {"\uacf5\uace0\u0020\uc0ad\uc81c"}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
            <div className="space-y-1.5">
              <label className="ml-1 text-[10px] font-bold uppercase text-muted-foreground">
                {"\ud604\uc7ac\u0020\uc804\ud615\u0020\uc0c1\ud0dc"}
              </label>
              <select
                value={application.status}
                onChange={(event) =>
                  handleUpdate({ status: event.target.value as Application["status"] })
                }
                className="h-10 w-full cursor-pointer rounded-xl border border-border bg-background px-3 py-1 text-sm font-medium transition-all focus:outline-none focus:ring-2 focus:ring-primary/20"
              >
                {columns.map((column) => (
                  <option key={column.id} value={column.id}>
                    {column.title}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex flex-col space-y-1.5">
              <label className="ml-1 text-[10px] font-bold uppercase text-muted-foreground">
                {"\uc81c\ucd9c\u002f\uba74\uc811\u0020\ub9c8\uac10\uc77c"}
              </label>
              <Popover open={isCalendarOpen} onOpenChange={setIsCalendarOpen}>
                <PopoverTrigger asChild>
                  <Button
                    variant="outline"
                    className={cn(
                      "h-10 w-full justify-start rounded-xl border-border bg-background px-3 text-left font-medium transition-all hover:bg-muted/50",
                      !localDeadline && "text-muted-foreground",
                      localDeadline?.toISOString() !==
                        (application.deadline
                          ? new Date(application.deadline).toISOString()
                          : null) && "border-primary ring-1 ring-primary/20"
                    )}
                  >
                    <CalendarIcon className="mr-2 h-4 w-4 text-primary" />
                    {localDeadline ? (
                      format(localDeadline, "PPP p", { locale: ko })
                    ) : (
                      <span>{"\ub0a0\uc9dc\u0020\uc120\ud0dd"}</span>
                    )}
                    <ChevronDown className="ml-auto h-4 w-4 opacity-50" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent
                  className="w-auto rounded-2xl border-border/50 p-4 shadow-2xl"
                  align="end"
                >
                  <div className="space-y-4">
                    <Calendar
                      mode="single"
                      selected={localDeadline || undefined}
                      onSelect={(date) => {
                        if (!date) return
                        const next = new Date(date)
                        if (localDeadline) {
                          next.setHours(localDeadline.getHours())
                          next.setMinutes(localDeadline.getMinutes())
                        } else {
                          next.setHours(9, 0, 0, 0)
                        }
                        setLocalDeadline(next)
                      }}
                      initialFocus
                      className="rounded-xl border border-border/50"
                    />

                    <div className="space-y-2 border-t border-border/50 pt-2">
                      <Button
                        className="mt-2 h-10 w-full gap-2 rounded-xl shadow-lg shadow-primary/20"
                        disabled={
                          !localDeadline ||
                          localDeadline.toISOString() ===
                            (application.deadline
                              ? new Date(application.deadline).toISOString()
                              : null)
                        }
                        onClick={() => {
                          if (!localDeadline) return
                          handleUpdate({
                            deadline: format(localDeadline, "yyyy-MM-dd'T'HH:mm:ss"),
                          })
                          setIsCalendarOpen(false)
                        }}
                      >
                        <Save className="size-4" />
                        {"\ubcc0\uacbd\uc0ac\ud56d\u0020\uc801\uc6a9"}
                      </Button>
                    </div>
                  </div>
                </PopoverContent>
              </Popover>
            </div>
          </div>

          <div className="mt-4 space-y-1.5">
            <label className="ml-1 text-[10px] font-bold uppercase text-muted-foreground">
              {"\uc804\ud615\u0020\uacb0\uacfc"}
            </label>
            <div className="flex flex-col gap-2 rounded-2xl border border-muted-foreground/10 bg-muted/30 p-1 sm:flex-row">
              <Button
                variant="ghost"
                size="sm"
                className={`h-9 flex-1 rounded-xl text-xs transition-all ${
                  application.result === "pending"
                    ? "bg-background font-bold text-foreground shadow-sm"
                    : "text-muted-foreground hover:bg-background/50"
                }`}
                onClick={() => handleUpdate({ result: "pending" })}
              >
                <Clock className="mr-1.5 size-3.5" />
                {"\ub300\uae30"}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                className={`h-9 flex-1 rounded-xl text-xs transition-all ${
                  application.result === "pass"
                    ? "bg-emerald-500 font-bold text-white shadow-md hover:bg-emerald-600"
                    : "text-muted-foreground hover:bg-emerald-100/50"
                }`}
                onClick={() => handleUpdate({ result: "pass" })}
              >
                <CheckCircle2 className="mr-1.5 size-3.5" />
                {"\ud569\uaca9"}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                className={`h-9 flex-1 rounded-xl text-xs transition-all ${
                  application.result === "fail"
                    ? "bg-red-500 font-bold text-white shadow-md hover:bg-red-600"
                    : "text-muted-foreground hover:bg-red-100/50"
                }`}
                onClick={() => handleUpdate({ result: "fail" })}
              >
                <XCircle className="mr-1.5 size-3.5" />
                {"\ubd88\ud569\uaca9\u002f\ud0c8\ub77d"}
              </Button>
            </div>
          </div>
        </SheetHeader>

        <Tabs
          key={
            application.id +
            (application.aiInsight ? "-insight" : "-noinsight") +
            (application.companyResearch ? "-research" : "-noresearch")
          }
          defaultValue={
            application.companyResearch
              ? "research"
              : application.aiInsight
                ? "questions"
                : "jd"
          }
          className="flex h-full min-h-0 flex-1 flex-col"
        >
          <TabsList className="grid h-auto w-full shrink-0 grid-cols-4 rounded-none border-b border-border bg-background p-0">
            <TabsTrigger
              value="jd"
              className="gap-1.5 whitespace-normal break-words rounded-none border-b-2 border-transparent px-2 py-3 text-[11px] leading-tight data-[state=active]:border-primary data-[state=active]:bg-transparent sm:gap-2 sm:px-4 sm:text-sm"
            >
              <FileText className="size-4" />
              JD
            </TabsTrigger>
            <TabsTrigger
              value="research"
              className="gap-1.5 whitespace-normal break-words rounded-none border-b-2 border-transparent px-2 py-3 text-[11px] leading-tight data-[state=active]:border-primary data-[state=active]:bg-transparent sm:gap-2 sm:px-4 sm:text-sm"
            >
              <Wand2 className="size-4" />
              {"\uae30\uc5c5\u0020\ubd84\uc11d"}
            </TabsTrigger>
            <TabsTrigger
              value="questions"
              className="gap-1.5 whitespace-normal break-words rounded-none border-b-2 border-transparent px-2 py-3 text-[11px] leading-tight data-[state=active]:border-primary data-[state=active]:bg-transparent sm:gap-2 sm:px-4 sm:text-sm"
            >
              <ClipboardList className="size-4" />
              {"\ubb38\ud56d\u0020\uad00\ub9ac"}
            </TabsTrigger>
            <TabsTrigger
              value="schedules"
              className="gap-1.5 whitespace-normal break-words rounded-none border-b-2 border-transparent px-2 py-3 text-[11px] leading-tight data-[state=active]:border-primary data-[state=active]:bg-transparent sm:gap-2 sm:px-4 sm:text-sm"
            >
              <CalendarDays className="size-4" />
              {"\uc804\ud615\u0020\uc77c\uc815"}
            </TabsTrigger>
          </TabsList>

          <ScrollArea className="h-full min-h-0 flex-1">
            <TabsContent value="jd" className="m-0 min-w-0 overflow-x-auto p-4 sm:p-6 lg:p-7">
              <JDAnalysisPanel
                application={application}
                onUpdateApplication={onUpdateApplication}
              />
            </TabsContent>
            <TabsContent value="research" className="m-0 min-w-0 overflow-x-auto p-4 sm:p-6 lg:p-7">
              <CompanyResearchPanel
                application={application}
                onUpdateApplication={onUpdateApplication}
              />
            </TabsContent>
            <TabsContent value="questions" className="m-0 min-w-0 overflow-x-auto p-4 sm:p-6 lg:p-7 outline-none">
              <QuestionsPanel
                application={application}
                onRefresh={onRefresh}
                onUpdateApplication={onUpdateApplication}
              />
            </TabsContent>
            <TabsContent value="schedules" className="m-0 p-4 sm:p-6 lg:p-7">
              <ApplicationSchedulePanel applicationId={Number(application.id)} />
            </TabsContent>
          </ScrollArea>
        </Tabs>
      </SheetContent>
    </Sheet>
  )
}
