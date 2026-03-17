"use client"

import { useEffect, useState, type ReactNode } from "react"
import {
  Building2,
  FileSearch,
  Lightbulb,
  Loader2,
  MessageSquareQuote,
  Sparkles,
  Target,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Textarea } from "@/components/ui/textarea"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { toApiUrl } from "@/lib/network/api-base"
import { streamSse } from "@/lib/network/stream-sse"
import {
  parseCompanyResearch,
  type CompanyResearchData,
} from "@/lib/application-intelligence"
import { type Application } from "@/lib/mock-data"

interface CompanyResearchPanelProps {
  application: Application
  onUpdateApplication: (updates: Partial<Application>) => void
}

const DEFAULT_GOAL =
  "\uc9c0\uc6d0\ub3d9\uae30\u002c\u0020\uc11c\ube44\uc2a4\u0020\ud638\uac10\ub3c4\u002c\u0020\uc9c1\ubb34\u0020\uc801\ud569\ub3c4\ub97c\u0020\uc790\uc18c\uc11c\uc5d0\u0020\uad6c\uccb4\uc801\uc73c\ub85c\u0020\ub179\uc77c\u0020\uc218\u0020\uc788\uc744\u0020\uc815\ub3c4\ub85c\u0020\ubd84\uc11d\ud569\ub2c8\ub2e4\u002e"

export function CompanyResearchPanel({
  application,
  onUpdateApplication,
}: CompanyResearchPanelProps) {
  const existingResearch = parseCompanyResearch(application.companyResearch)
  const [businessUnit, setBusinessUnit] = useState("")
  const [targetService, setTargetService] = useState("")
  const [focusRole, setFocusRole] = useState(application.position || "")
  const [techFocus, setTechFocus] = useState("")
  const [questionGoal, setQuestionGoal] = useState(DEFAULT_GOAL)
  const [researchData, setResearchData] = useState<CompanyResearchData | null>(existingResearch)
  const [progressMessage, setProgressMessage] = useState("")
  const [isResearching, setIsResearching] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const parsed = parseCompanyResearch(application.companyResearch)
    setResearchData(parsed)
    setBusinessUnit(parsed?.focus.businessUnit || "")
    setTargetService(parsed?.focus.targetService || "")
    setFocusRole(parsed?.focus.focusRole || application.position || "")
    setTechFocus(parsed?.focus.techFocus || "")
    setQuestionGoal(parsed?.focus.questionGoal || DEFAULT_GOAL)
    setProgressMessage("")
    setError(null)
  }, [application.id, application.companyResearch, application.position])

  const handleResearch = async () => {
    setIsResearching(true)
    setError(null)
    setProgressMessage("\uae30\uc5c5\u0020\ubd84\uc11d\u0020\uc900\ube44\u0020\uc911\uc785\ub2c8\ub2e4\u002e")

    let completed = false
    let streamFailed = false

    try {
      const initResponse = await fetch(
        `/api/applications/${application.id}/company-research/init`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            businessUnit,
            targetService,
            focusRole,
            techFocus,
            questionGoal,
          }),
        }
      )

      if (!initResponse.ok) {
        throw new Error("\uae30\uc5c5\u0020\ubd84\uc11d\u0020\ucd08\uae30\ud654\uc5d0\u0020\uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4\u002e")
      }

      const { uuid } = await initResponse.json()

      await streamSse({
        url: toApiUrl(`/api/applications/company-research/stream/${uuid}`),
        onEvent: ({ event, data }) => {
          const normalizedEvent = event.toUpperCase()

          if (normalizedEvent === "START" || normalizedEvent === "ANALYZING") {
            setProgressMessage(data)
            return
          }

          if (normalizedEvent === "COMPLETE") {
            const parsed = parseCompanyResearch(data)
            if (!parsed) {
              streamFailed = true
              setError("\uae30\uc5c5\u0020\ubd84\uc11d\u0020\uacb0\uacfc\ub97c\u0020\ud574\uc11d\ud558\uc9c0\u0020\ubabb\ud588\uc2b5\ub2c8\ub2e4\u002e")
              return
            }

            completed = true
            setResearchData(parsed)
            setProgressMessage("\uae30\uc5c5\u0020\ubd84\uc11d\uc774\u0020\uc644\ub8cc\ub418\uc5c8\uc2b5\ub2c8\ub2e4\u002e")
            onUpdateApplication({ companyResearch: JSON.stringify(parsed) })
            return
          }

          if (normalizedEvent === "ERROR") {
            streamFailed = true
            try {
              const parsedError = JSON.parse(data)
              setError(parsedError.message || "\uae30\uc5c5\u0020\ubd84\uc11d\u0020\uc911\u0020\uc624\ub958\uac00\u0020\ubc1c\uc0dd\ud588\uc2b5\ub2c8\ub2e4\u002e")
            } catch {
              setError(data || "\uae30\uc5c5\u0020\ubd84\uc11d\u0020\uc911\u0020\uc624\ub958\uac00\u0020\ubc1c\uc0dd\ud588\uc2b5\ub2c8\ub2e4\u002e")
            }
          }
        },
      })

      if (!completed && !streamFailed) {
        setError("\uae30\uc5c5\u0020\ubd84\uc11d\u0020\uc2a4\ud2b8\ub9bc\uc774\u0020\uc608\uc0c1\ubcf4\ub2e4\u0020\uc77c\ucc0d\u0020\uc885\ub8cc\ub418\uc5c8\uc2b5\ub2c8\ub2e4\u002e")
      }
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "\uae30\uc5c5\u0020\ubd84\uc11d\u0020\uc694\uccad\uc5d0\u0020\uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4\u002e"
      )
    } finally {
      setIsResearching(false)
    }
  }

  return (
    <div className="min-w-0 space-y-5 overflow-x-hidden">
      <Card className="min-w-0 overflow-hidden border-primary/15 bg-gradient-to-br from-primary/5 via-background to-background shadow-none">
        <CardContent className="space-y-5 p-4 sm:p-5">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="space-y-2">
              <div className="flex items-center gap-2 text-primary">
                <FileSearch className="size-4 shrink-0" />
                <span className="text-sm font-black uppercase tracking-[0.18em]">
                  {"\uae30\uc5c5\u0020\ubd84\uc11d"}
                </span>
              </div>
              <p className="text-sm leading-6 text-muted-foreground">
                {
                  "\u0047\u0065\u006d\u0069\u006e\u0069\uac00\u0020\uacf5\uace0\uc640\u0020\uc785\ub825\ud55c\u0020\ud3ec\ucee4\uc2a4\ub97c\u0020\ubc14\ud0d5\uc73c\ub85c\u0020\ud68c\uc0ac\uc640\u0020\uc9c1\ubb34\u0020\ub9e5\ub77d\uc744\u0020\uc790\uc18c\uc11c\uc6a9\uc73c\ub85c\u0020\uae4a\uac8c\u0020\uc7ac\uad6c\uc131\ud569\ub2c8\ub2e4\u002e\u0020\ud544\uc694\ud558\uba74\u0020\uacf5\uac1c\ub41c\u0020\ucd5c\uc2e0\u0020\uc815\ubcf4\ub97c\u0020\ubc14\ud0d5\uc73c\ub85c\u0020\ubd84\uc11d\ud558\ub3c4\ub85d\u0020\uc124\uacc4\ub41c\u0020\uc790\uc18c\uc11c\u0020\uc791\uc131\uc6a9\u0020\uc2ec\uce35\u0020\ubd84\uc11d\uc785\ub2c8\ub2e4\u002e"
                }
              </p>
            </div>
            <Button
              onClick={handleResearch}
              disabled={isResearching}
              className="w-full gap-2 rounded-full px-4 shadow-sm shadow-primary/20 sm:w-auto"
            >
              {isResearching ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Sparkles className="size-4" />
              )}
              {researchData ? "\ub2e4\uc2dc\u0020\ubd84\uc11d" : "\ubd84\uc11d\u0020\uc2dc\uc791"}
            </Button>
          </div>

          <div className="grid gap-3">
            <FieldBlock
              label="\uc0ac\uc5c5\ubd80\u0020\u002f\u0020\ud300"
              value={businessUnit}
              onChange={setBusinessUnit}
              placeholder="\uc608\u003a\u0020\u004d\u0058\uc0ac\uc5c5\ubd80\u002c\u0020\ubcd1\uc6d0\u0020\uc804\uc0b0\ud300\u002c\u0020\u0041\u0049\u0020\ud50c\ub7ab\ud3fc\u0020\uc870\uc9c1"
            />
            <FieldBlock
              label="\uc11c\ube44\uc2a4\u0020\u002f\u0020\uc81c\ud488"
              value={targetService}
              onChange={setTargetService}
              placeholder="\uc608\u003a\u0020\ubaa8\ubc14\uc77c\u0020\ud50c\ub7ab\ud3fc\u002c\u0020\ubcd1\uc6d0\u0020\uc815\ubcf4\uc2dc\uc2a4\ud15c\u002c\u0020\u0041\u0049\u0020\uc9c0\uc6d0\u0020\uc11c\ube44\uc2a4"
            />
            <FieldBlock
              label="\uc9c1\ubb34\u0020\ud3ec\ucee4\uc2a4"
              value={focusRole}
              onChange={setFocusRole}
              placeholder="\uc608\u003a\u0020\u004a\u0061\u0076\u0061\u0020\ubc31\uc5d4\ub4dc\u002c\u0020\uc758\ub8cc\u0020\u0041\u0049\u0020\uc6b4\uc601\u002c\u0020\ud50c\ub7ab\ud3fc\u0020\uc790\ub3d9\ud654"
            />
            <FieldBlock
              label="\uae30\uc220\u0020\ud3ec\ucee4\uc2a4"
              value={techFocus}
              onChange={setTechFocus}
              placeholder="\uc608\u003a\u0020\u004a\u0061\u0076\u0061\u002c\u0020\u0053\u0070\u0072\u0069\u006e\u0067\u002c\u0020\ubcf4\uc548\u002c\u0020\uc778\ud504\ub77c\u002c\u0020\ub370\uc774\ud130\u0020\ud30c\uc774\ud504\ub77c\uc778\u002c\u0020\u004d\u004c\u004f\u0070\u0073"
            />
          </div>

          <div className="space-y-1.5">
            <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">
              {"\ubd84\uc11d\u0020\ubaa9\ud45c"}
            </p>
            <Textarea
              value={questionGoal}
              onChange={(event) => setQuestionGoal(event.target.value)}
              placeholder="\uc774\u0020\ubd84\uc11d\uc5d0\uc11c\u0020\uc5b4\ub5a4\u0020\ub2f5\uc744\u0020\uc5bb\uace0\u0020\uc2f6\uc740\uc9c0\u0020\uc801\uc5b4\uc8fc\uc138\uc694\u002e"
              className="min-h-[110px] resize-none bg-background/80 text-sm leading-6"
            />
          </div>

          {(progressMessage || error) && (
            <div
              className={`rounded-2xl border px-4 py-3 text-sm leading-6 ${
                error
                  ? "border-destructive/20 bg-destructive/5 text-destructive"
                  : "border-primary/10 bg-primary/5 text-foreground"
              }`}
            >
              {error || progressMessage}
            </div>
          )}
        </CardContent>
      </Card>

      {researchData && (
        <div className="min-w-0 space-y-4">
          <Card className="min-w-0 overflow-hidden border-none bg-muted/30 shadow-none">
            <CardContent className="space-y-4 p-4 sm:p-5">
              <div className="flex items-center gap-2">
                <Building2 className="size-4 shrink-0 text-primary" />
                <span className="text-sm font-black uppercase tracking-[0.16em] text-primary">
                  {"\ud575\uc2ec\u0020\uc694\uc57d"}
                </span>
              </div>
              <p className="break-words text-sm leading-7 text-foreground/90">
                {researchData.executiveSummary}
              </p>
              <div className="flex flex-wrap gap-2">
                {[
                  researchData.focus.businessUnit,
                  researchData.focus.targetService,
                  researchData.focus.focusRole,
                  researchData.focus.techFocus,
                ]
                  .filter(Boolean)
                  .map((item) => (
                    <Badge
                      key={item}
                      variant="outline"
                      className="max-w-full whitespace-normal break-all border-primary/20 bg-background px-3 py-1 text-primary"
                    >
                      {item}
                    </Badge>
                  ))}
              </div>
            </CardContent>
          </Card>

          <div className="grid min-w-0 gap-4">
            <InsightListCard
              icon={<Target className="size-4 text-primary" />}
              title="\uacbd\uc601\u002f\uc0ac\uc5c5\u0020\ub9e5\ub77d"
              items={researchData.businessContext}
            />
            <InsightListCard
              icon={<Sparkles className="size-4 text-primary" />}
              title="\uc11c\ube44\uc2a4\u002f\uc81c\ud488\u0020\ub9e5\ub77d"
              items={researchData.serviceLandscape}
            />
            <InsightListCard
              icon={<Building2 className="size-4 text-primary" />}
              title="\uc9c1\ubb34\u0020\ubc94\uc704"
              items={researchData.roleScope}
            />
            <InsightListCard
              icon={<FileSearch className="size-4 text-primary" />}
              title="\uae30\uc220\u0020\uc2dc\uadf8\ub110"
              items={researchData.techSignals}
            />
          </div>

          <Card className="min-w-0 overflow-hidden border-primary/10 bg-primary/[0.03] shadow-none">
            <CardContent className="space-y-5 p-4 sm:p-5">
              <div className="flex items-center gap-2">
                <MessageSquareQuote className="size-4 shrink-0 text-primary" />
                <span className="text-sm font-black uppercase tracking-[0.16em] text-primary">
                  {"\uc790\uc18c\uc11c\u0020\uc5f0\uacb0\u0020\ud3ec\uc778\ud2b8"}
                </span>
              </div>

              <div className="grid gap-4">
                <MiniList title="\uc9c0\uc6d0\ub3d9\uae30\u0020\ud3ec\uc778\ud2b8" items={researchData.motivationHooks} />
                <MiniList title="\uc11c\ube44\uc2a4\u0020\uad00\uc2ec\u0020\ud3ec\uc778\ud2b8" items={researchData.serviceHooks} />
                <MiniList title="\uc790\uc18c\uc11c\u0020\uc5f0\uacb0\u0020\uac01\ub3c4" items={researchData.resumeAngles} />
                <MiniList title="\uba74\uc811\u0020\uc608\uc0c1\u0020\ud3ec\uc778\ud2b8" items={researchData.interviewSignals} />
              </div>

              {researchData.recommendedNarrative && (
                <>
                  <Separator className="bg-primary/10" />
                  <div className="space-y-2">
                    <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">
                      {"\ucd94\ucc9c\u0020\uc11c\uc220\u0020\ubc29\ud5a5"}
                    </p>
                    <p className="break-words rounded-2xl bg-background p-4 text-sm leading-7 text-foreground/90 shadow-sm">
                      {researchData.recommendedNarrative}
                    </p>
                  </div>
                </>
              )}
            </CardContent>
          </Card>

          <div className="grid min-w-0 gap-4">
            <InsightListCard
              icon={<Lightbulb className="size-4 text-primary" />}
              title="\ud6c4\uc18d\u0020\uc9c8\ubb38"
              items={researchData.followUpQuestions}
            />
            <InsightListCard
              icon={<FileSearch className="size-4 text-primary" />}
              title="\uc2e0\ub8b0\ub3c4\u0020\uba54\ubaa8"
              items={researchData.confidenceNotes}
            />
          </div>
        </div>
      )}
    </div>
  )
}

function FieldBlock({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  placeholder: string
}) {
  return (
    <div className="space-y-1.5">
      <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">
        {label}
      </p>
      <Textarea
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="min-h-[72px] resize-none bg-background/80 text-sm leading-6"
      />
    </div>
  )
}

function InsightListCard({
  title,
  items,
  icon,
}: {
  title: string
  items: string[]
  icon: ReactNode
}) {
  if (items.length === 0) return null

  return (
    <Card className="min-w-0 overflow-hidden border-none bg-muted/30 shadow-none">
      <CardContent className="space-y-3 p-4 sm:p-5">
        <div className="flex items-center gap-2">
          {icon}
          <span className="text-sm font-black uppercase tracking-[0.16em] text-primary">
            {title}
          </span>
        </div>
        <MiniList items={items} />
      </CardContent>
    </Card>
  )
}

function MiniList({ title, items }: { title?: string; items: string[] }) {
  if (items.length === 0) return null

  return (
    <div className="space-y-2">
      {title && (
        <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">
          {title}
        </p>
      )}
      <div className="min-w-0 space-y-2">
        {items.map((item, index) => (
          <div
            key={`${title || "item"}-${index}`}
            className="w-full whitespace-pre-wrap break-words rounded-2xl bg-background px-3 py-3 text-sm leading-6 text-foreground/90 shadow-sm"
          >
            {item}
          </div>
        ))}
      </div>
    </div>
  )
}
