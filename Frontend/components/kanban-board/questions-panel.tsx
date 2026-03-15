"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { Card, CardContent } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { 
  PenLine, 
  Trash2, 
  Plus, 
  MoreVertical, 
  ExternalLink,
  Loader2
} from "lucide-react"
import { 
  Dialog, 
  DialogContent, 
  DialogHeader, 
  DialogTitle, 
  DialogFooter,
  DialogDescription
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
  onRefresh 
}: { 
  application: Application,
  onRefresh: () => void
}) {
  const router = useRouter()
  const [isAddOpen, setIsAddOpen] = useState(false)
  const [isEditOpen, setIsEditOpen] = useState(false)
  const [isDeleting, setIsDeleting] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  
  const [editingQuestion, setEditingQuestion] = useState<{
    id: string,
    title: string,
    maxLength: number
  } | null>(null)
  
  const [newQuestion, setNewQuestion] = useState({
    title: "",
    maxLength: 1000
  })

  const handleAdd = async () => {
    if (!newQuestion.title) return
    setIsSubmitting(true)
    try {
      const response = await fetch(`/api/applications/${application.id}/questions`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(newQuestion)
      })
      if (!response.ok) throw new Error("문항 추가 실패")
      onRefresh()
      setIsAddOpen(false)
      setNewQuestion({ title: "", maxLength: 1000 })
    } catch (err) {
      console.error(err)
      alert("문항 추가에 실패했습니다.")
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleEdit = async () => {
    if (!editingQuestion || !editingQuestion.title) return
    setIsSubmitting(true)
    try {
      const response = await fetch(`/api/applications/questions/${editingQuestion.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title: editingQuestion.title,
          maxLength: editingQuestion.maxLength
        })
      })
      if (!response.ok) throw new Error("문항 수정 실패")
      onRefresh()
      setIsEditOpen(false)
      setEditingQuestion(null)
    } catch (err) {
      console.error(err)
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
        method: "DELETE"
      })
      if (!response.ok) throw new Error("문항 삭제 실패")
      onRefresh()
    } catch (err) {
      console.error(err)
      alert("문항 삭제에 실패했습니다.")
    } finally {
      setIsDeleting(null)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-semibold">자소서 문항 ({application.questions.length})</h3>
          <p className="text-xs text-muted-foreground mt-0.5">기업별 맞춤형 문항을 관리하세요.</p>
        </div>
        <Button size="sm" variant="outline" className="gap-1.5 h-8" onClick={() => setIsAddOpen(true)}>
          <Plus className="size-3.5" />
          문항 추가
        </Button>
      </div>
      
      <div className="space-y-3">
        {application.questions.length === 0 ? (
          <div className="py-12 text-center border-2 border-dashed border-muted rounded-2xl">
            <p className="text-sm text-muted-foreground">등록된 자소서 문항이 없습니다.</p>
            <Button variant="link" size="sm" className="mt-1" onClick={() => setIsAddOpen(true)}>
              첫 문항 추가하기
            </Button>
          </div>
        ) : (
          application.questions.map((question, idx) => {
            const progress = Math.round((question.currentLength / question.maxLength) * 100)
            const isLoad = isDeleting === question.id
            
            return (
              <Card key={question.id} className="overflow-hidden border-border/50 hover:border-primary/30 transition-colors group">
                <CardContent className="p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-3 mb-3">
                        <span className="size-6 shrink-0 rounded-full bg-primary/10 text-primary flex items-center justify-center text-[10px] font-bold">
                          {idx + 1}
                        </span>
                        <h4 className="font-bold text-sm truncate pr-2">{question.title}</h4>
                      </div>
                      <div className="space-y-2">
                        <div className="flex justify-between items-center text-[10px] mb-1">
                          <span className="text-muted-foreground font-medium">진행도</span>
                          <span className={`${progress >= 100 ? 'text-emerald-500 font-bold' : 'text-primary'}`}>
                            {question.currentLength} / {question.maxLength}자 ({progress}%)
                          </span>
                        </div>
                        <Progress value={Math.min(progress, 100)} className="h-1.5" />
                      </div>
                    </div>

                    <div className="flex flex-col gap-2">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="size-8">
                            <MoreVertical className="size-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-32">
                          <DropdownMenuItem onClick={() => {
                            setEditingQuestion({
                              id: question.id,
                              title: question.title,
                              maxLength: question.maxLength
                            })
                            setIsEditOpen(true)
                          }}>
                            <PenLine className="size-4 mr-2" />
                            제목 수정
                          </DropdownMenuItem>
                          <DropdownMenuItem 
                            className="text-destructive focus:text-destructive"
                            onClick={() => handleDelete(question.id)}
                            disabled={isLoad}
                          >
                            {isLoad ? <Loader2 className="size-4 mr-2 animate-spin" /> : <Trash2 className="size-4 mr-2" />}
                            문항 삭제
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                      
                      <Button 
                        size="sm" 
                        variant="secondary"
                        className="size-8 p-0 shrink-0"
                        title="작업실로 이동"
                        onClick={() => router.push(`/workspace/${application.id}?q=${question.id}`)}
                      >
                        <ExternalLink className="size-4 text-primary" />
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            )
          })
        )}
      </div>

      {/* Add Question Dialog */}
      <Dialog open={isAddOpen} onOpenChange={setIsAddOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>새 자소서 문항 추가</DialogTitle>
            <DialogDescription>
              지원하시는 공고의 자기소개서 문항을 입력해주세요.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="title">문항 내용</Label>
              <Input 
                id="title" 
                placeholder="예: 지원동기 및 포부" 
                value={newQuestion.title}
                onChange={(e) => setNewQuestion({...newQuestion, title: e.target.value})}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="maxLength">글자수 제한 (공백 포함)</Label>
              <Input 
                id="maxLength" 
                type="number"
                value={newQuestion.maxLength}
                onChange={(e) => setNewQuestion({...newQuestion, maxLength: parseInt(e.target.value) || 0})}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsAddOpen(false)}>취소</Button>
            <Button onClick={handleAdd} disabled={isSubmitting || !newQuestion.title}>
              {isSubmitting && <Loader2 className="size-4 mr-2 animate-spin" />}
              추가하기
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit Question Dialog */}
      <Dialog open={isEditOpen} onOpenChange={setIsEditOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>문항 정보 수정</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="edit-title">문항 내용</Label>
              <Input 
                id="edit-title" 
                value={editingQuestion?.title || ""}
                onChange={(e) => setEditingQuestion(prev => prev ? {...prev, title: e.target.value} : null)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="edit-maxLength">글자수 제한</Label>
              <Input 
                id="edit-maxLength" 
                type="number"
                value={editingQuestion?.maxLength || 0}
                onChange={(e) => setEditingQuestion(prev => prev ? {...prev, maxLength: parseInt(e.target.value) || 0} : null)}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsEditOpen(false)}>취소</Button>
            <Button onClick={handleEdit} disabled={isSubmitting || !editingQuestion?.title}>
              {isSubmitting && <Loader2 className="size-4 mr-2 animate-spin" />}
              수정 완료
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
