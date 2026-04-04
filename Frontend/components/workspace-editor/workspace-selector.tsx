"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Search, PenTool, LayoutDashboard, ArrowRight, Loader2 } from "lucide-react"
import { Input } from "@/components/ui/input"
import { getDDay, mockApplications, type Application } from "@/lib/mock-data"
import { isFrontendOnlyMode } from "@/lib/frontend-only"

export function WorkspaceSelector() {
  const router = useRouter()
  const [applications, setApplications] = useState<Application[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState("")

  useEffect(() => {
    const fetchApps = async () => {
      if (isFrontendOnlyMode()) {
        setApplications(mockApplications)
        setLoading(false)
        return
      }

      try {
        const response = await fetch("/api/applications/workspace-selector")
        const data = await response.json()
        if (Array.isArray(data)) {
          // Map backend data to frontend Application type
          const mapped = data.map((app: any) => ({
            id: app.id.toString(),
            company: app.companyName || "무제",
            position: app.position || "직무 미정",
            deadline: app.deadline,
            techStack: [],
            logoColor: app.logoColor || "bg-primary",
            logoUrl: app.logoUrl,
            status: app.status,
            result: app.result,
            questions: app.questions || []
          }))
          setApplications(mapped)
        } else {
          setApplications(mockApplications)
        }
      } catch (err) {
        console.error("Failed to fetch apps", err)
        setApplications(mockApplications)
      } finally {
        setLoading(false)
      }
    }
    fetchApps()
  }, [])

  const filteredApps = applications.filter(app => 
    (app.company?.toLowerCase() || "").includes(search.toLowerCase()) ||
    (app.position?.toLowerCase() || "").includes(search.toLowerCase())
  )

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center bg-muted/5">
        <div className="flex flex-col items-center gap-4">
          <Loader2 className="size-8 animate-spin text-primary opacity-50" />
          <p className="text-sm font-medium text-muted-foreground animate-pulse">공고 목록을 불러오는 중...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col items-center justify-center bg-muted/5 p-6">
      <div className="w-full max-w-3xl space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
        <div className="text-center space-y-3">
          <div className="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/10 text-primary mb-2 ring-1 ring-primary/20 shadow-inner">
            <PenTool className="size-6" />
          </div>
          <h2 className="text-3xl font-black tracking-tight">어떤 공고를 작성해 볼까요?</h2>
          <p className="text-muted-foreground font-medium">작업을 계속하거나 새로운 공고를 위해 작업실을 열어보세요.</p>
        </div>

        <div className="relative group">
          <Search className="absolute left-4 top-1/2 size-4 -translate-y-1/2 text-muted-foreground group-focus-within:text-primary transition-colors" />
          <Input 
            placeholder="회사명 또는 직무로 검색..." 
            className="h-14 pl-11 pr-4 rounded-2xl bg-background shadow-xl shadow-primary/5 border-primary/10 focus-visible:ring-primary/20 text-lg transition-all"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="grid gap-4">
          <ScrollArea className="h-[400px] pr-4">
            <div className="grid gap-3">
              {filteredApps.length > 0 ? (
                filteredApps.map((app) => (
                  <Card 
                    key={app.id} 
                    className="group border border-primary/5 hover:border-primary/30 hover:shadow-2xl hover:shadow-primary/10 transition-all duration-300 cursor-pointer overflow-hidden bg-background"
                    onClick={() => router.push(`/workspace/${app.id}`)}
                  >
                    <CardContent className="p-5 flex items-center gap-4">
                      <div className={`size-12 rounded-xl ${app.logoColor} flex items-center justify-center shadow-sm shrink-0 border border-white/10`}>
                        <span className="text-lg font-black text-white">{app.company.charAt(0)}</span>
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-0.5">
                          <h4 className="font-bold text-base truncate">{app.company}</h4>
                          <Badge variant="outline" className="h-4 text-[10px] font-bold px-1.5 opacity-60">
                            {getDDay(app.deadline)}
                          </Badge>
                        </div>
                        <p className="text-xs text-muted-foreground truncate font-medium">{app.position}</p>
                      </div>
                      <div className="shrink-0 opacity-0 -translate-x-2 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-300">
                        <Button size="icon" variant="ghost" className="rounded-full bg-primary/5 text-primary hover:bg-primary hover:text-white border border-primary/10">
                          <ArrowRight className="size-4" />
                        </Button>
                      </div>
                    </CardContent>
                  </Card>
                ))
              ) : (
                <div className="flex flex-col items-center justify-center py-20 text-center space-y-4">
                  <div className="p-4 rounded-full bg-muted/30">
                    <LayoutDashboard className="size-8 text-muted-foreground/40" />
                  </div>
                  <div>
                    <p className="text-sm font-bold text-muted-foreground">검색 결과가 없거나 공고가 아직 없어요.</p>
                    <Button 
                      variant="link" 
                      className="text-primary font-bold text-xs"
                      onClick={() => router.push("/")}
                    >
                      대시보드에서 새 공고 추가하기
                    </Button>
                  </div>
                </div>
              )}
            </div>
          </ScrollArea>
        </div>
      </div>
    </div>
  )
}
