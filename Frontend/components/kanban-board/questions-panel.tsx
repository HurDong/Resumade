"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { Card, CardContent } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  CheckCircle2,
  ExternalLink,
  Loader2,
  MoreVertical,
  PenLine,
  Plus,
  Trash2,
} from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { type Application } from "@/lib/mock-data"

export function QuestionsPanel({
  application,
  onRefresh,
  onUpdateApplication,
}: {
  application: Application
  onRefresh: () => void
  onUpdateApplication?: (updates: Partial<Application>) => void
}) {
  const router = useRouter()
  const [isAddOpen, setIsAddOpen] = useState(false)
  const [isEditOpen, setIsEditOpen] = useState(false)
  const [isDeleting, setIsDeleting] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [editingQuestion, setEditingQuestion] = useState<{
    id: string
    title: string
    maxLength: number
  } | null>(null)
  const [newQuestion, setNewQuestion] = useState({
    title: "",
    maxLength: 1000,
  })

  const handleAdd = async () => {
    if (!newQuestion.title.trim()) return
    setIsSubmitting(true)

    try {
      const response = await fetch(`/api/applications/${application.id}/questions`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(newQuestion),
      })

      if (!response.ok) {
        throw new Error("문항 추가 실패")
      }

      const saved = await response.json()

      if (onUpdateApplication) {
        onUpdateApplication({
          questions: [
            ...application.questions,
            {
              id: saved.id.toString(),
              title: saved.title,
              maxLength: saved.maxLength,
              currentLength: 0,
              content: "",
              isCompleted: false,
            },
          ],
        })
      } else {
        onRefresh()
      }

      setIsAddOpen(false)
      setNewQuestion({ title: "", maxLength: 1000 })
    } catch (error) {
      console.error(error)
      onRefresh()
      alert("문항 추가에 실패했습니다.")
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleEdit = async () => {
    if (!editingQuestion || !editingQuestion.title.trim()) return
    setIsSubmitting(true)

    const updatedQuestions = application.questions.map((question) =>
      question.id === editingQuestion.id
        ? {
            ...question,
            title: editingQuestion.title,
            maxLength: editingQuestion.maxLength,
          }
        : question
    )

    try {
      if (onUpdateApplication) {
        onUpdateApplication({ questions: updatedQuestions })
      }

      const response = await fetch(`/api/applications/questions/${editingQuestion.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title: editingQuestion.title,
          maxLength: editingQuestion.maxLength,
        }),
      })

      if (!response.ok) {
        throw new Error("문항 수정 실패")
      }

      setIsEditOpen(false)
      setEditingQuestion(null)
    } catch (error) {
      console.error(error)
      onRefresh()
      alert("문항 수정에 실패했습니다.")
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleDelete = async (questionId: string) => {
    if (!confirm("정말 이 문항을 삭제하시겠습니까?")) return
    setIsDeleting(questionId)

    try {
      const response = await fetch(`/api/applications/questions/${questionId}`, {
        method: "DELETE",
      })

      if (!response.ok) {
        throw new Error("문항 삭제 실패")
      }

      if (onUpdateApplication) {
        onUpdateApplication({
          questions: application.questions.filter((question) => question.id !== questionId),
        })
      } else {
        onRefresh()
      }
    } catch (error) {
      console.error(error)
      onRefresh()
      alert("문항 삭제에 실패했습니다.")
    } finally {
      setIsDeleting(null)
    }
  }

  const handleToggleComplete = async (questionId: string, nextValue: boolean) => {
    try {
      if (onUpdateApplication) {
        onUpdateApplication({
          questions: application.questions.map((question) =>
            question.id === questionId
              ? { ...question, isCompleted: nextValue }
              : question
          ),
        })
      }

      const response = await fetch(`/api/applications/questions/${questionId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ isCompleted: nextValue }),
      })

      if (!response.ok) {
        throw new Error("문항 상태 변경 실패")
      }
    } catch (error) {
      console.error(error)
      onRefresh()
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4 border-b border-primary/10 pb-4">
        <div className="flex-1">
          <h3 className="text-sm font-bold">자소서 문항 ({application.questions.length})</h3>
          <p className="mt-1 text-xs text-muted-foreground">
            여기서는 프론트에 먼저 반영하고, 시트를 닫을 때 다시 백엔드 기준으로 동기화합니다.
          </p>
        </div>
        <Button
          size="sm"
          className="h-9 shrink-0 gap-1.5 px-4 shadow-sm"
          onClick={() => setIsAddOpen(true)}
        >
          <Plus className="size-4" />
          문항 추가
        </Button>
      </div>

      <div className="space-y-4">
        {application.questions.length === 0 ? (
          <div className="rounded-3xl border-2 border-dashed border-muted bg-muted/5 py-16 text-center">
            <p className="text-sm font-medium text-muted-foreground">
              등록된 자소서 문항이 없습니다.
            </p>
            <p className="mt-1 text-xs text-muted-foreground/60">
              상단 버튼으로 필요한 문항만 직접 추가하세요.
            </p>
            <Button
              variant="outline"
              size="sm"
              className="mt-4 gap-2"
              onClick={() => setIsAddOpen(true)}
            >
              <Plus className="size-4" />
              첫 문항 추가
            </Button>
          </div>
        ) : (
          application.questions.map((question, index) => {
            const progress = Math.round(
              (question.currentLength / Math.max(question.maxLength, 1)) * 100
            )
            const isDeletingCurrent = isDeleting === question.id

            return (
              <Card
                key={question.id}
                className="group overflow-hidden border-border/60 shadow-sm transition-all duration-200 hover:border-primary/40 hover:shadow-md"
              >
                <CardContent className="p-5">
                  <div className="flex items-start justify-between gap-5">
                    <div className="min-w-0 flex-1">
                      <div className="mb-4 flex items-start gap-4">
                        <span className="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-[10px] font-black text-primary">
                          {index + 1}
                        </span>
                        <h4 className="whitespace-pre-line text-sm font-bold leading-relaxed text-foreground/90">
                          {question.title}
                        </h4>
                      </div>

                      <div className="space-y-2.5">
                        <div className="flex items-center justify-between text-[11px]">
                          <span className="font-semibold text-muted-foreground">
                            작성 진행률
                          </span>
                          <span
                            className={
                              progress >= 100
                                ? "font-bold text-emerald-500"
                                : "font-bold text-primary"
                            }
                          >
                            {question.currentLength} / {question.maxLength}자 ({progress}%)
                          </span>
                        </div>
                        <Progress
                          value={Math.min(progress, 100)}
                          className="h-2 rounded-full bg-primary/5"
                        />
                      </div>
                    </div>

                    <div className="flex flex-col gap-2">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="size-9 rounded-full font-bold hover:bg-muted"
                          >
                            <MoreVertical className="size-5" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-44 p-1">
                          <DropdownMenuItem
                            className="cursor-pointer"
                            onClick={() => {
                              setEditingQuestion({
                                id: question.id,
                                title: question.title,
                                maxLength: question.maxLength,
                              })
                              setIsEditOpen(true)
                            }}
                          >
                            <PenLine className="mr-2 size-4 text-primary" />
                            문항 내용 수정
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            className="cursor-pointer font-medium text-emerald-600 focus:text-emerald-700"
                            onClick={() =>
                              handleToggleComplete(question.id, !question.isCompleted)
                            }
                          >
                            <CheckCircle2
                              className={`mr-2 size-4 ${
                                question.isCompleted
                                  ? "text-emerald-600"
                                  : "text-muted-foreground"
                              }`}
                            />
                            {question.isCompleted ? "작성 중으로 변경" : "작성 완료로 변경"}
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            className="cursor-pointer text-destructive focus:text-destructive"
                            onClick={() => handleDelete(question.id)}
                            disabled={isDeletingCurrent}
                          >
                            {isDeletingCurrent ? (
                              <Loader2 className="mr-2 size-4 animate-spin" />
                            ) : (
                              <Trash2 className="mr-2 size-4" />
                            )}
                            문항 삭제
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>

                      <Button
                        size="sm"
                        variant="secondary"
                        className="group/link size-9 shrink-0 rounded-full border-0 bg-primary/5 p-0 hover:bg-primary/10"
                        title="작업실로 이동"
                        onClick={() => router.push(`/workspace/${application.id}?q=${question.id}`)}
                      >
                        <ExternalLink className="size-5 text-primary transition-transform group-hover/link:scale-110" />
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            )
          })
        )}
      </div>

      <Dialog open={isAddOpen} onOpenChange={setIsAddOpen}>
        <DialogContent className="rounded-3xl sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-xl font-bold">자소서 문항 추가</DialogTitle>
            <DialogDescription className="text-muted-foreground/80">
              공고에 필요한 문항만 직접 입력해 주세요.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-5 py-4">
            <div className="space-y-2.5">
              <Label htmlFor="title" className="text-sm font-bold">
                자소서 문항
              </Label>
              <Textarea
                id="title"
                placeholder="예: 지원동기와 입사 후 목표를 작성해 주세요."
                className="min-h-[120px] resize-none border-primary/20 focus-visible:ring-primary"
                value={newQuestion.title}
                onChange={(event) =>
                  setNewQuestion({ ...newQuestion, title: event.target.value })
                }
              />
            </div>
            <div className="space-y-2.5">
              <Label htmlFor="maxLength" className="text-sm font-bold">
                글자수 제한
              </Label>
              <div className="relative">
                <Input
                  id="maxLength"
                  type="number"
                  className="border-primary/20 pl-4 pr-10"
                  value={newQuestion.maxLength}
                  onChange={(event) =>
                    setNewQuestion({
                      ...newQuestion,
                      maxLength: parseInt(event.target.value, 10) || 0,
                    })
                  }
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-medium text-muted-foreground">
                  자
                </span>
              </div>
              <div className="mt-2 grid grid-cols-4 gap-2">
                {[-100, -50, 50, 100].map((delta) => (
                  <Button
                    key={delta}
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-9 w-full border-primary/10 text-[11px] font-black shadow-sm transition-all hover:bg-primary/20"
                    onClick={() =>
                      setNewQuestion((current) => ({
                        ...current,
                        maxLength: Math.max(0, current.maxLength + delta),
                      }))
                    }
                  >
                    {delta > 0 ? `+${delta}` : delta}
                  </Button>
                ))}
              </div>
            </div>
          </div>
          <DialogFooter className="gap-2 sm:gap-0">
            <Button
              variant="ghost"
              className="rounded-xl"
              onClick={() => setIsAddOpen(false)}
            >
              취소
            </Button>
            <Button
              className="rounded-xl px-8 font-bold"
              onClick={handleAdd}
              disabled={isSubmitting || !newQuestion.title.trim()}
            >
              {isSubmitting && <Loader2 className="mr-2 size-4 animate-spin" />}
              문항 등록
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isEditOpen} onOpenChange={setIsEditOpen}>
        <DialogContent className="rounded-3xl sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-xl font-bold">문항 정보 수정</DialogTitle>
          </DialogHeader>
          <div className="space-y-5 py-4">
            <div className="space-y-2.5">
              <Label htmlFor="edit-title" className="text-sm font-bold">
                문항 내용
              </Label>
              <Textarea
                id="edit-title"
                className="min-h-[120px] resize-none border-primary/20 focus-visible:ring-primary"
                value={editingQuestion?.title || ""}
                onChange={(event) =>
                  setEditingQuestion((current) =>
                    current ? { ...current, title: event.target.value } : null
                  )
                }
              />
            </div>
            <div className="space-y-2.5">
              <Label htmlFor="edit-maxLength" className="text-sm font-bold">
                글자수 제한
              </Label>
              <div className="relative">
                <Input
                  id="edit-maxLength"
                  type="number"
                  className="border-primary/20 pl-4 pr-10"
                  value={editingQuestion?.maxLength || 0}
                  onChange={(event) =>
                    setEditingQuestion((current) =>
                      current
                        ? {
                            ...current,
                            maxLength: parseInt(event.target.value, 10) || 0,
                          }
                        : null
                    )
                  }
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-medium text-muted-foreground">
                  자
                </span>
              </div>
              <div className="mt-2 grid grid-cols-4 gap-2">
                {[-100, -50, 50, 100].map((delta) => (
                  <Button
                    key={delta}
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-9 w-full border-primary/10 text-[11px] font-black shadow-sm transition-all hover:bg-primary/20"
                    onClick={() =>
                      setEditingQuestion((current) =>
                        current
                          ? {
                              ...current,
                              maxLength: Math.max(0, current.maxLength + delta),
                            }
                          : null
                      )
                    }
                  >
                    {delta > 0 ? `+${delta}` : delta}
                  </Button>
                ))}
              </div>
            </div>
          </div>
          <DialogFooter className="gap-2 sm:gap-0">
            <Button
              variant="ghost"
              className="rounded-xl"
              onClick={() => setIsEditOpen(false)}
            >
              취소
            </Button>
            <Button
              className="rounded-xl px-8 font-bold"
              onClick={handleEdit}
              disabled={isSubmitting || !editingQuestion?.title.trim()}
            >
              {isSubmitting && <Loader2 className="mr-2 size-4 animate-spin" />}
              수정 완료
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
