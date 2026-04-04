"use client"

import { useState, useEffect } from "react"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Label } from "@/components/ui/label"
import { X } from "lucide-react"
import { type CodingProblem } from "./mock-data"

const PLATFORMS = ["프로그래머스", "백준", "LeetCode", "기타"]

type FormState = Omit<CodingProblem, "id">

const EMPTY_FORM: FormState = {
  company: "",
  date: new Date().toISOString().slice(0, 10),
  title: "",
  types: [],
  platform: "프로그래머스",
  level: null,
  myApproach: "",
  betterApproach: "",
  betterCode: "",
  note: "",
}

export function AddProblemDialog({
  open,
  onClose,
  onSubmit,
  initialData,
}: {
  open: boolean
  onClose: () => void
  onSubmit: (data: FormState) => void
  initialData?: CodingProblem | null
}) {
  const isEdit = !!initialData
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [typeInput, setTypeInput] = useState("")

  useEffect(() => {
    if (open) {
      setForm(initialData ? { ...initialData } : EMPTY_FORM)
      setTypeInput("")
    }
  }, [open, initialData])

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  const addType = () => {
    const val = typeInput.trim()
    if (val && !form.types.includes(val)) {
      set("types", [...form.types, val])
    }
    setTypeInput("")
  }

  const removeType = (t: string) => set("types", form.types.filter((x) => x !== t))

  const handleSubmit = () => {
    if (!form.title.trim() || !form.company.trim()) return
    onSubmit(form)
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEdit ? "문제 수정" : "새 문제 기록"}</DialogTitle>
        </DialogHeader>

        <div className="space-y-5 py-1">
          {/* 기본 정보 */}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label className="text-xs">문제 제목 *</Label>
              <Input
                placeholder="예: 메뉴 리뉴얼"
                value={form.title}
                onChange={(e) => set("title", e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">회사 *</Label>
              <Input
                placeholder="예: 카카오"
                value={form.company}
                onChange={(e) => set("company", e.target.value)}
              />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5">
              <Label className="text-xs">플랫폼</Label>
              <select
                value={form.platform}
                onChange={(e) => set("platform", e.target.value)}
                className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              >
                {PLATFORMS.map((p) => (
                  <option key={p} value={p}>{p}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">레벨</Label>
              <Input
                type="number"
                placeholder="예: 3"
                value={form.level ?? ""}
                onChange={(e) => set("level", e.target.value ? Number(e.target.value) : null)}
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">날짜</Label>
              <Input
                type="date"
                value={form.date}
                onChange={(e) => set("date", e.target.value)}
              />
            </div>
          </div>

          {/* 유형 태그 */}
          <div className="space-y-1.5">
            <Label className="text-xs">유형 태그</Label>
            <div className="flex gap-2">
              <Input
                placeholder="예: BFS (엔터로 추가)"
                value={typeInput}
                onChange={(e) => setTypeInput(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && (e.preventDefault(), addType())}
                className="flex-1"
              />
              <Button type="button" variant="outline" size="sm" onClick={addType}>
                추가
              </Button>
            </div>
            {form.types.length > 0 && (
              <div className="flex flex-wrap gap-1.5 mt-1.5">
                {form.types.map((t) => (
                  <Badge key={t} variant="secondary" className="gap-1 text-xs font-normal">
                    {t}
                    <button type="button" onClick={() => removeType(t)}>
                      <X className="size-3" />
                    </button>
                  </Badge>
                ))}
              </div>
            )}
          </div>

          {/* 내 풀이 */}
          <div className="space-y-1.5">
            <Label className="text-xs">내 풀이 접근법</Label>
            <textarea
              rows={3}
              placeholder="어떤 방식으로 접근했나요? 막혔던 부분은 무엇인가요?"
              value={form.myApproach}
              onChange={(e) => set("myApproach", e.target.value)}
              className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
            />
          </div>

          {/* 더 나은 풀이 */}
          <div className="space-y-1.5">
            <Label className="text-xs">더 나은 풀이 (알게 된 방법)</Label>
            <textarea
              rows={3}
              placeholder="찾아보니 이렇게 하면 더 깔끔하더라..."
              value={form.betterApproach}
              onChange={(e) => set("betterApproach", e.target.value)}
              className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
            />
          </div>

          {/* 코드 */}
          <div className="space-y-1.5">
            <Label className="text-xs">코드 (선택)</Label>
            <textarea
              rows={6}
              placeholder="# 더 나은 풀이 코드"
              value={form.betterCode}
              onChange={(e) => set("betterCode", e.target.value)}
              className="flex w-full rounded-md border border-input bg-zinc-900 px-3 py-2 text-xs text-zinc-100 font-mono shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
            />
          </div>

          {/* 회고 */}
          <div className="space-y-1.5">
            <Label className="text-xs">회고 메모</Label>
            <textarea
              rows={2}
              placeholder="다음에 비슷한 문제를 만날 때 기억해야 할 것..."
              value={form.note}
              onChange={(e) => set("note", e.target.value)}
              className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>취소</Button>
          <Button
            onClick={handleSubmit}
            disabled={!form.title.trim() || !form.company.trim()}
          >
            {isEdit ? "수정 완료" : "기록 저장"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
