"use client"

import { useState, useEffect } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { Lightbulb, Plus, Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { toast } from "sonner"
import { TemplateCard } from "./template-card"
import { AddNoteDialog } from "./add-note-dialog"
import { TECH_CATEGORIES, type TechTemplate } from "./mock-data"
import { cn } from "@/lib/utils"

const API = "/api/tech-notes"

async function apiRequest<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, options)
  if (!res.ok) throw new Error(await res.text())
  if (res.status === 204) return undefined as T
  return res.json()
}

export function TechNotes() {
  const [templates, setTemplates] = useState<TechTemplate[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedCategory, setSelectedCategory] = useState("전체")
  const [editingNote, setEditingNote] = useState<TechTemplate | null>(null)
  const [isAddOpen, setIsAddOpen] = useState(false)

  useEffect(() => {
    apiRequest<TechTemplate[]>(API)
      .then(setTemplates)
      .catch(() => toast.error("노트 목록을 불러오지 못했습니다."))
      .finally(() => setLoading(false))
  }, [])

  const filtered =
    selectedCategory === "전체"
      ? templates
      : templates.filter((t) => t.category === selectedCategory)

  const handleAdd = async (data: Omit<TechTemplate, "id">) => {
    try {
      const created = await apiRequest<TechTemplate>(API, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      })
      setTemplates((prev) => [created, ...prev])
      toast.success("노트 저장", { description: `"${data.title}" 노트가 추가됐습니다.` })
    } catch {
      toast.error("저장에 실패했습니다.")
    }
  }

  const handleEdit = async (data: Omit<TechTemplate, "id">) => {
    if (!editingNote) return
    try {
      const updated = await apiRequest<TechTemplate>(`${API}/${editingNote.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      })
      setTemplates((prev) => prev.map((t) => (t.id === editingNote.id ? updated : t)))
      toast.success("노트 수정 완료")
    } catch {
      toast.error("수정에 실패했습니다.")
    } finally {
      setEditingNote(null)
    }
  }

  const handleDelete = async (id: number, title: string) => {
    try {
      await apiRequest<void>(`${API}/${id}`, { method: "DELETE" })
      setTemplates((prev) => prev.filter((t) => t.id !== id))
      toast.success("삭제 완료", { description: `"${title}" 노트가 삭제됐습니다.` })
    } catch {
      toast.error("삭제에 실패했습니다.")
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-32">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  return (
    <div className="space-y-5">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div className="flex flex-wrap gap-2">
          {TECH_CATEGORIES.map((cat) => (
            <button
              key={cat}
              type="button"
              onClick={() => setSelectedCategory(cat)}
              className={cn(
                "rounded-full px-3.5 py-1.5 text-xs font-medium transition-colors",
                selectedCategory === cat
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground hover:text-foreground"
              )}
            >
              {cat}
              {cat !== "전체" && (
                <span className="ml-1.5 opacity-60">
                  {templates.filter((t) => t.category === cat).length}
                </span>
              )}
            </button>
          ))}
        </div>

        <Button size="sm" className="ml-4 shrink-0 gap-2" onClick={() => setIsAddOpen(true)}>
          <Plus className="size-4" />
          노트 추가
        </Button>
      </div>

      {/* 벼락치기 안내 */}
      <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50/50 px-4 py-3 dark:border-amber-900/40 dark:bg-amber-950/20">
        <Lightbulb className="size-4 mt-0.5 shrink-0 text-amber-500" />
        <p className="text-xs text-amber-700 dark:text-amber-300 leading-relaxed">
          <strong>핵심 포인트</strong>를 먼저 빠르게 읽은 뒤, 코드 템플릿을 펼쳐서 패턴을 확인하세요.
          시험 직전엔 핵심 포인트 목록만 훑는 것도 효과적입니다.
        </p>
      </div>

      {/* 카드 그리드 */}
      {filtered.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <Lightbulb className="size-10 text-muted-foreground/40 mb-3" />
          <p className="text-sm font-medium text-muted-foreground">이 카테고리에 노트가 없어요</p>
          <Button size="sm" className="mt-4 gap-2" onClick={() => setIsAddOpen(true)}>
            <Plus className="size-4" />
            노트 추가하기
          </Button>
        </div>
      ) : (
        <motion.div
          key={selectedCategory}
          className="grid grid-cols-1 gap-4 lg:grid-cols-2"
          initial="hidden"
          animate="show"
          variants={{ hidden: {}, show: { transition: { staggerChildren: 0.06 } } }}
        >
          <AnimatePresence mode="popLayout">
            {filtered.map((template) => (
              <motion.div
                key={template.id}
                variants={{
                  hidden: { opacity: 0, y: 12 },
                  show: { opacity: 1, y: 0, transition: { duration: 0.22 } },
                }}
                exit={{ opacity: 0, scale: 0.97 }}
              >
                <TemplateCard
                  template={template}
                  onEdit={() => setEditingNote(template)}
                  onDelete={() => handleDelete(template.id, template.title)}
                />
              </motion.div>
            ))}
          </AnimatePresence>
        </motion.div>
      )}

      {/* 추가 다이얼로그 */}
      <AddNoteDialog
        open={isAddOpen}
        onClose={() => setIsAddOpen(false)}
        onSubmit={handleAdd}
      />

      {/* 수정 다이얼로그 */}
      <AddNoteDialog
        open={!!editingNote}
        onClose={() => setEditingNote(null)}
        onSubmit={handleEdit}
        initialData={editingNote}
      />
    </div>
  )
}
