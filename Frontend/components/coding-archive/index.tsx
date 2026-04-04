"use client"

import { useState, useEffect } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { Search, Plus, Code2, Loader2 } from "lucide-react"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { toast } from "sonner"
import { ProblemCard } from "./problem-card"
import { ProblemDetailModal } from "./problem-detail-modal"
import { AddProblemDialog } from "./add-problem-dialog"
import { type CodingProblem } from "./mock-data"

const TYPE_FILTERS = ["전체", "BFS/DFS", "DP", "구현", "조합", "정렬", "이진탐색", "MST", "그리디", "Union-Find"]

const API = "/api/coding-problems"

async function apiRequest<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, options)
  if (!res.ok) throw new Error(await res.text())
  if (res.status === 204) return undefined as T
  return res.json()
}

export function CodingArchive() {
  const [problems, setProblems] = useState<CodingProblem[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState("")
  const [selectedType, setSelectedType] = useState("전체")
  const [selectedProblem, setSelectedProblem] = useState<CodingProblem | null>(null)
  const [editingProblem, setEditingProblem] = useState<CodingProblem | null>(null)
  const [isAddOpen, setIsAddOpen] = useState(false)

  useEffect(() => {
    apiRequest<CodingProblem[]>(API)
      .then(setProblems)
      .catch(() => toast.error("문제 목록을 불러오지 못했습니다."))
      .finally(() => setLoading(false))
  }, [])

  const filtered = problems.filter((p) => {
    if (
      search &&
      !p.title.includes(search) &&
      !p.company.includes(search) &&
      !p.types.some((t) => t.includes(search))
    )
      return false
    if (selectedType !== "전체" && !p.types.some((t) => t.includes(selectedType))) return false
    return true
  })

  const handleAdd = async (data: Omit<CodingProblem, "id">) => {
    try {
      const created = await apiRequest<CodingProblem>(API, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      })
      setProblems((prev) => [created, ...prev])
      toast.success("문제 기록 저장", { description: `"${data.title}" 문제가 추가됐습니다.` })
    } catch {
      toast.error("저장에 실패했습니다.")
    }
  }

  const handleEdit = async (data: Omit<CodingProblem, "id">) => {
    if (!editingProblem) return
    try {
      const updated = await apiRequest<CodingProblem>(`${API}/${editingProblem.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      })
      setProblems((prev) => prev.map((p) => (p.id === editingProblem.id ? updated : p)))
      toast.success("문제 수정 완료")
    } catch {
      toast.error("수정에 실패했습니다.")
    } finally {
      setEditingProblem(null)
    }
  }

  const handleDelete = async (id: number, title: string) => {
    try {
      await apiRequest<void>(`${API}/${id}`, { method: "DELETE" })
      setProblems((prev) => prev.filter((p) => p.id !== id))
      toast.success("삭제 완료", { description: `"${title}" 기록이 삭제됐습니다.` })
      if (selectedProblem?.id === id) setSelectedProblem(null)
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
        <p className="text-sm text-muted-foreground">
          총 <span className="font-semibold text-foreground">{problems.length}</span>개의 문제 기록
        </p>
        <Button size="sm" className="gap-2" onClick={() => setIsAddOpen(true)}>
          <Plus className="size-4" />
          문제 추가
        </Button>
      </div>

      {/* 필터 */}
      <div className="space-y-3">
        <div className="relative max-w-xs">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
          <Input
            placeholder="문제명, 회사, 유형 검색"
            className="pl-9"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="flex flex-wrap gap-2">
          {TYPE_FILTERS.map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setSelectedType(t)}
              className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                selectedType === t
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground hover:text-foreground"
              }`}
            >
              {t}
            </button>
          ))}
        </div>
      </div>

      {/* 카드 그리드 */}
      {filtered.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <Code2 className="size-10 text-muted-foreground/40 mb-3" />
          <p className="text-sm font-medium text-muted-foreground">기록된 문제가 없어요</p>
          <p className="text-xs text-muted-foreground/60 mt-1">
            풀었던 문제를 추가해서 나만의 아카이브를 만들어보세요
          </p>
          <Button size="sm" className="mt-4 gap-2" onClick={() => setIsAddOpen(true)}>
            <Plus className="size-4" />
            첫 문제 기록하기
          </Button>
        </div>
      ) : (
        <motion.div
          className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3"
          initial="hidden"
          animate="show"
          variants={{ hidden: {}, show: { transition: { staggerChildren: 0.05 } } }}
        >
          <AnimatePresence mode="popLayout">
            {filtered.map((problem) => (
              <motion.div
                key={problem.id}
                variants={{
                  hidden: { opacity: 0, y: 12 },
                  show: { opacity: 1, y: 0, transition: { duration: 0.2 } },
                }}
                exit={{ opacity: 0, scale: 0.96 }}
              >
                <ProblemCard
                  problem={problem}
                  onClick={() => setSelectedProblem(problem)}
                  onEdit={(e) => { e.stopPropagation(); setEditingProblem(problem) }}
                  onDelete={(e) => { e.stopPropagation(); handleDelete(problem.id, problem.title) }}
                />
              </motion.div>
            ))}
          </AnimatePresence>
        </motion.div>
      )}

      <ProblemDetailModal
        problem={selectedProblem}
        open={!!selectedProblem}
        onClose={() => setSelectedProblem(null)}
        onEdit={() => setEditingProblem(selectedProblem)}
      />
      <AddProblemDialog open={isAddOpen} onClose={() => setIsAddOpen(false)} onSubmit={handleAdd} />
      <AddProblemDialog
        open={!!editingProblem}
        onClose={() => setEditingProblem(null)}
        onSubmit={handleEdit}
        initialData={editingProblem}
      />
    </div>
  )
}
