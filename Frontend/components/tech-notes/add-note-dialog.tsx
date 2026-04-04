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
import { Label } from "@/components/ui/label"
import { Badge } from "@/components/ui/badge"
import { Plus, X } from "lucide-react"
import { type TechTemplate, TECH_CATEGORIES } from "./mock-data"

type FormState = Omit<TechTemplate, "id">

const EMPTY_FORM: FormState = {
  title: "",
  category: "문법·유틸",
  summary: "",
  conditions: [],
  template: "",
  tags: [],
}

export function AddNoteDialog({
  open,
  onClose,
  onSubmit,
  initialData,
}: {
  open: boolean
  onClose: () => void
  onSubmit: (data: FormState) => void
  initialData?: TechTemplate | null
}) {
  const isEdit = !!initialData
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [conditionInput, setConditionInput] = useState("")
  const [tagInput, setTagInput] = useState("")

  useEffect(() => {
    if (open) {
      setForm(initialData ? { ...initialData } : EMPTY_FORM)
      setConditionInput("")
      setTagInput("")
    }
  }, [open, initialData])

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  const addCondition = () => {
    const val = conditionInput.trim()
    if (val) {
      set("conditions", [...form.conditions, val])
      setConditionInput("")
    }
  }

  const removeCondition = (idx: number) =>
    set("conditions", form.conditions.filter((_, i) => i !== idx))

  const addTag = () => {
    const val = tagInput.trim()
    if (val && !form.tags.includes(val)) {
      set("tags", [...form.tags, val])
    }
    setTagInput("")
  }

  const removeTag = (t: string) => set("tags", form.tags.filter((x) => x !== t))

  const handleSubmit = () => {
    if (!form.title.trim()) return
    onSubmit(form)
    onClose()
  }

  const categories = TECH_CATEGORIES.filter((c) => c !== "전체")

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEdit ? "노트 수정" : "새 기술 노트"}</DialogTitle>
        </DialogHeader>

        <div className="space-y-5 py-1">
          {/* 기본 정보 */}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label className="text-xs">제목 *</Label>
              <Input
                placeholder="예: collections.deque"
                value={form.title}
                onChange={(e) => set("title", e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">카테고리</Label>
              <select
                value={form.category}
                onChange={(e) => set("category", e.target.value)}
                className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              >
                {categories.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
          </div>

          {/* 한줄 요약 */}
          <div className="space-y-1.5">
            <Label className="text-xs">한줄 요약</Label>
            <Input
              placeholder="예: 양방향 O(1) 큐. BFS 필수, list.pop(0) 대체"
              value={form.summary}
              onChange={(e) => set("summary", e.target.value)}
            />
          </div>

          {/* 핵심 포인트 */}
          <div className="space-y-1.5">
            <Label className="text-xs">핵심 포인트</Label>
            <div className="flex gap-2">
              <Input
                placeholder="기억해야 할 조건 또는 패턴 (엔터로 추가)"
                value={conditionInput}
                onChange={(e) => setConditionInput(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && (e.preventDefault(), addCondition())}
                className="flex-1"
              />
              <Button type="button" variant="outline" size="icon" onClick={addCondition}>
                <Plus className="size-4" />
              </Button>
            </div>
            {form.conditions.length > 0 && (
              <ul className="space-y-1.5 mt-2">
                {form.conditions.map((c, idx) => (
                  <li key={idx} className="flex items-start gap-2 rounded-md bg-muted px-3 py-2 text-sm">
                    <span className="flex-1">{c}</span>
                    <button type="button" onClick={() => removeCondition(idx)} className="shrink-0 text-muted-foreground hover:text-destructive">
                      <X className="size-3.5" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* 내용 */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label className="text-xs">내용</Label>
              <span className="text-[10px] text-muted-foreground">마크다운 지원 — 표: <code className="bg-muted px-1 rounded">| 항목 | 설명 |</code>　코드: <code className="bg-muted px-1 rounded">```python</code></span>
            </div>
            <textarea
              rows={10}
              placeholder={`# 코드 블록 예시\n\`\`\`python\nfrom collections import deque\nq = deque()\n\`\`\`\n\n# 표 예시\n| 항목 | 설명 | 예제 |\n|------|------|------|\n| split | 문자열 분리 | s.split(",") |`}
              value={form.template}
              onChange={(e) => set("template", e.target.value)}
              className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-xs font-mono shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
            />
          </div>

          {/* 태그 */}
          <div className="space-y-1.5">
            <Label className="text-xs">태그</Label>
            <div className="flex gap-2">
              <Input
                placeholder="예: BFS (엔터로 추가)"
                value={tagInput}
                onChange={(e) => setTagInput(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && (e.preventDefault(), addTag())}
                className="flex-1"
              />
              <Button type="button" variant="outline" size="sm" onClick={addTag}>추가</Button>
            </div>
            {form.tags.length > 0 && (
              <div className="flex flex-wrap gap-1.5 mt-1.5">
                {form.tags.map((t) => (
                  <Badge key={t} variant="secondary" className="gap-1 text-xs font-normal">
                    #{t}
                    <button type="button" onClick={() => removeTag(t)}>
                      <X className="size-3" />
                    </button>
                  </Badge>
                ))}
              </div>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>취소</Button>
          <Button onClick={handleSubmit} disabled={!form.title.trim()}>
            {isEdit ? "수정 완료" : "노트 저장"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
