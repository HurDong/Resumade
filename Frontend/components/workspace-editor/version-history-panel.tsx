"use client"

import { useEffect, useState } from "react"
import { History, X, RotateCcw, FileText, Languages, CheckCheck, Loader2, ClipboardCopy } from "lucide-react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { cn } from "@/lib/utils"
import { fetchVersionHistory, type Snapshot, type SnapshotType } from "@/lib/api/version-history"

// ─────────────────────────────────────────────────────────
// 스냅샷 타입 메타
// ─────────────────────────────────────────────────────────
const TYPE_META: Record<SnapshotType, { label: string; color: string; icon: React.ElementType }> = {
  DRAFT_GENERATED: {
    label: "AI 초안",
    color: "bg-amber-100 text-amber-700 dark:bg-amber-900/50 dark:text-amber-300",
    icon: FileText,
  },
  WASHED: {
    label: "세탁본",
    color: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/50 dark:text-emerald-300",
    icon: Languages,
  },
  FINAL_EDIT: {
    label: "최종 저장",
    color: "bg-primary/10 text-primary",
    icon: CheckCheck,
  },
}

function formatRelativeTime(isoString: string): string {
  const date = new Date(isoString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMin / 60)
  const diffDay = Math.floor(diffHour / 24)

  if (diffMin < 1) return "방금 전"
  if (diffMin < 60) return `${diffMin}분 전`
  if (diffHour < 24) return `${diffHour}시간 전`
  if (diffDay < 7) return `${diffDay}일 전`
  return date.toLocaleDateString("ko-KR", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })
}

// ─────────────────────────────────────────────────────────
// 스냅샷 카드
// ─────────────────────────────────────────────────────────
interface SnapshotCardProps {
  snapshot: Snapshot
  index: number
  isSelected: boolean
  onSelect: () => void
  onRestore: () => void
}

function SnapshotCard({ snapshot, index, isSelected, onSelect, onRestore }: SnapshotCardProps) {
  const meta = TYPE_META[snapshot.snapshotType]
  const Icon = meta.icon
  const preview = snapshot.content?.slice(0, 120).replace(/\n/g, " ") ?? ""

  return (
    <div
      onClick={onSelect}
      className={cn(
        "group relative cursor-pointer rounded-xl border p-4 transition-all duration-200",
        isSelected
          ? "border-primary bg-primary/5 shadow-sm"
          : "border-transparent hover:border-border hover:bg-muted/40"
      )}
    >
      {/* 타임라인 점 */}
      <div className="flex items-start gap-3">
        <div className={cn("mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-lg", meta.color)}>
          <Icon className="size-3.5" />
        </div>
        <div className="min-w-0 flex-1 space-y-1.5">
          <div className="flex items-center justify-between gap-2">
            <Badge className={cn("text-[10px] font-semibold px-2 py-0.5", meta.color)}>
              {meta.label}
            </Badge>
            <span className="text-[10px] text-muted-foreground tabular-nums shrink-0">
              {formatRelativeTime(snapshot.createdAt)}
            </span>
          </div>
          <p className="text-xs text-muted-foreground leading-relaxed line-clamp-2">
            {preview}
            {snapshot.content?.length > 120 && "..."}
          </p>
          <p className="text-[10px] text-muted-foreground/60">{snapshot.content?.length ?? 0}자</p>
        </div>
      </div>

      {/* 복원 버튼 (호버 시 표시) */}
      {isSelected && (
        <div
          className="mt-3 flex gap-2 animate-in fade-in-0 slide-in-from-bottom-1 duration-200"
          onClick={(e) => e.stopPropagation()}
        >
          <Button
            size="sm"
            variant="outline"
            className="h-7 gap-1.5 text-[11px] flex-1"
            onClick={() => {
              navigator.clipboard.writeText(snapshot.content ?? "")
              toast.success("클립보드에 복사했습니다.")
            }}
          >
            <ClipboardCopy className="size-3" />
            복사
          </Button>
          <Button
            size="sm"
            className="h-7 gap-1.5 text-[11px] flex-1"
            onClick={onRestore}
          >
            <RotateCcw className="size-3" />
            이 버전으로 복원
          </Button>
        </div>
      )}
    </div>
  )
}

// ─────────────────────────────────────────────────────────
// 메인 패널 컴포넌트
// ─────────────────────────────────────────────────────────
interface VersionHistoryPanelProps {
  questionId: number | null
  open: boolean
  onClose: () => void
  /** 복원 시 호출 — washedKr 필드를 외부에서 업데이트할 때 사용 */
  onRestore?: (content: string, type: SnapshotType) => void
}

export function VersionHistoryPanel({
  questionId,
  open,
  onClose,
  onRestore,
}: VersionHistoryPanelProps) {
  const [snapshots, setSnapshots] = useState<Snapshot[]>([])
  const [loading, setLoading] = useState(false)
  const [selectedId, setSelectedId] = useState<number | null>(null)

  useEffect(() => {
    if (!open || !questionId) return
    setLoading(true)
    setSelectedId(null)
    fetchVersionHistory(questionId)
      .then(setSnapshots)
      .catch(() => toast.error("버전 히스토리를 불러오지 못했습니다."))
      .finally(() => setLoading(false))
  }, [open, questionId])

  function handleRestore(snapshot: Snapshot) {
    onRestore?.(snapshot.content, snapshot.snapshotType)
    toast.success(`${TYPE_META[snapshot.snapshotType].label} 버전으로 복원했습니다.`)
    onClose()
  }

  return (
    <Sheet open={open} onOpenChange={(v) => { if (!v) onClose() }}>
      <SheetContent side="right" className="w-[400px] sm:w-[440px] p-0 flex flex-col">
        <SheetHeader className="px-6 py-5 border-b shrink-0">
          <div className="flex items-center justify-between">
            <SheetTitle className="flex items-center gap-2 text-base">
              <History className="size-4 text-primary" />
              버전 히스토리
            </SheetTitle>
            <Button variant="ghost" size="icon" className="size-8" onClick={onClose}>
              <X className="size-4" />
            </Button>
          </div>
          <p className="text-xs text-muted-foreground mt-0.5">
            세탁 완료 및 최종 저장 시점마다 자동 저장됩니다. 최대 20개
          </p>
        </SheetHeader>

        <ScrollArea className="flex-1">
          <div className="px-4 py-4">
            {loading ? (
              <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
                <Loader2 className="size-4 animate-spin" />
                <span className="text-sm">불러오는 중...</span>
              </div>
            ) : snapshots.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-16 text-center gap-3 text-muted-foreground">
                <History className="size-10 opacity-20" />
                <p className="text-sm font-medium">저장된 버전이 없습니다</p>
                <p className="text-xs">세탁을 실행하면 자동으로 버전이 저장됩니다.</p>
              </div>
            ) : (
              <div className="relative space-y-1">
                {/* 세로 타임라인 선 */}
                <div className="absolute left-[22px] top-4 bottom-4 w-px bg-border" />

                {snapshots.map((snapshot, index) => (
                  <SnapshotCard
                    key={snapshot.id}
                    snapshot={snapshot}
                    index={index}
                    isSelected={selectedId === snapshot.id}
                    onSelect={() => setSelectedId(selectedId === snapshot.id ? null : snapshot.id)}
                    onRestore={() => handleRestore(snapshot)}
                  />
                ))}
              </div>
            )}
          </div>
        </ScrollArea>
      </SheetContent>
    </Sheet>
  )
}
