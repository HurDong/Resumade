"use client"

import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ScrollArea } from "@/components/ui/scroll-area"
import { FileText, ClipboardList } from "lucide-react"
import { type Application } from "@/lib/mock-data"
import { JDAnalysisPanel } from "./jd-analysis-panel"
import { QuestionsPanel } from "./questions-panel"

export function ApplicationDetailSheet({ 
  application, 
  isOpen, 
  onClose 
}: { 
  application: Application | null
  isOpen: boolean
  onClose: () => void
}) {
  if (!application) return null

  return (
    <Sheet open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full sm:max-w-lg p-0 gap-0">
        <SheetHeader className="px-6 py-5 border-b border-border">
          <div className="flex items-center gap-3">
            <div className={`size-12 rounded-xl ${application.logoColor} flex items-center justify-center`}>
              <span className="text-lg font-bold text-white">
                {application.company.charAt(0)}
              </span>
            </div>
            <div>
              <SheetTitle className="text-left">{application.company}</SheetTitle>
              <p className="text-sm text-muted-foreground">{application.position}</p>
            </div>
          </div>
        </SheetHeader>

        <Tabs defaultValue="jd" className="flex-1">
          <TabsList className="w-full justify-start rounded-none border-b border-border bg-transparent p-0 h-auto">
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

          <ScrollArea className="h-[calc(100vh-180px)]">
            <TabsContent value="jd" className="m-0 p-6">
              <JDAnalysisPanel application={application} />
            </TabsContent>
            <TabsContent value="questions" className="m-0 p-6">
              <QuestionsPanel application={application} />
            </TabsContent>
          </ScrollArea>
        </Tabs>
      </SheetContent>
    </Sheet>
  )
}
