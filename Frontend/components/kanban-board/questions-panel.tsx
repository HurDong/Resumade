import { useState } from "react"
import { useRouter } from "next/navigation"
import { Card, CardContent } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
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
      <div className="flex items-start justify-between gap-4 border-b border-primary/10 pb-4">
        <div className="flex-1">
          <h3 className="text-sm font-bold">자소서 문항 ({application.questions.length})</h3>
          <p className="text-xs text-muted-foreground mt-1">기업별 문항을 자유롭게 추가하여 관리하세요.</p>
        </div>
        <Button size="sm" className="gap-1.5 h-9 px-4 shrink-0 shadow-sm" onClick={() => setIsAddOpen(true)}>
          <Plus className="size-4" />
          문항 추가
        </Button>
      </div>
      
      <div className="space-y-4">
        {application.questions.length === 0 ? (
          <div className="py-16 text-center border-2 border-dashed border-muted rounded-3xl bg-muted/5">
            <p className="text-sm text-muted-foreground font-medium">등록된 자소서 문항이 없습니다.</p>
            <p className="text-xs text-muted-foreground/60 mt-1">우측 상단의 버튼을 눌러 문항을 추가해보세요.</p>
            <Button variant="outline" size="sm" className="mt-4 gap-2" onClick={() => setIsAddOpen(true)}>
              <Plus className="size-4" />
              첫 문항 추가하기
            </Button>
          </div>
        ) : (
          application.questions.map((question, idx) => {
            const progress = Math.round((question.currentLength / question.maxLength) * 100)
            const isLoad = isDeleting === question.id
            
            return (
              <Card key={question.id} className="overflow-hidden border-border/60 hover:border-primary/40 transition-all duration-200 group shadow-sm hover:shadow-md">
                <CardContent className="p-5">
                  <div className="flex items-start justify-between gap-5">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-start gap-4 mb-4">
                        <span className="size-6 shrink-0 rounded-lg bg-primary/10 text-primary flex items-center justify-center text-[10px] font-black mt-0.5">
                          {idx + 1}
                        </span>
                        <h4 className="font-bold text-sm leading-relaxed whitespace-pre-line text-foreground/90">{question.title}</h4>
                      </div>
                      <div className="space-y-2.5">
                        <div className="flex justify-between items-center text-[11px]">
                          <span className="text-muted-foreground font-semibold">작성 진행도</span>
                          <span className={`${progress >= 100 ? 'text-emerald-500 font-bold' : 'text-primary font-bold'}`}>
                            {question.currentLength} / {question.maxLength}자 ({progress}%)
                          </span>
                        </div>
                        <Progress value={Math.min(progress, 100)} className="h-2 rounded-full bg-primary/5" />
                      </div>
                    </div>

                    <div className="flex flex-col gap-2">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="size-9 rounded-full hover:bg-muted font-bold">
                            <MoreVertical className="size-5" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-40 p-1">
                          <DropdownMenuItem className="cursor-pointer" onClick={() => {
                            setEditingQuestion({
                              id: question.id,
                              title: question.title,
                              maxLength: question.maxLength
                            })
                            setIsEditOpen(true)
                          }}>
                            <PenLine className="size-4 mr-2 text-primary" />
                            문항 내용 수정
                          </DropdownMenuItem>
                          <DropdownMenuItem 
                            className="text-destructive focus:text-destructive cursor-pointer"
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
                        className="size-9 p-0 shrink-0 rounded-full bg-primary/5 hover:bg-primary/10 border-0 group/link"
                        title="작업실로 이동"
                        onClick={() => router.push(`/workspace/${application.id}?q=${question.id}`)}
                      >
                        <ExternalLink className="size-5 text-primary group-hover/link:scale-110 transition-transform" />
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
        <DialogContent className="sm:max-w-md rounded-3xl">
          <DialogHeader>
            <DialogTitle className="text-xl font-bold">새 자소서 문항 추가</DialogTitle>
            <DialogDescription className="text-muted-foreground/80">
              지원하시는 공고의 자기소개서 문항을 정확히 입력해주세요.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-5 py-4">
            <div className="space-y-2.5">
              <Label htmlFor="title" className="font-bold text-sm">자소서 문항 (Question)</Label>
              <Textarea 
                id="title" 
                placeholder="내용을 붙여넣으세요. 예: 지원동기 및 입사 후 포부" 
                className="min-h-[120px] resize-none border-primary/20 focus-visible:ring-primary"
                value={newQuestion.title}
                onChange={(e) => setNewQuestion({...newQuestion, title: e.target.value})}
              />
            </div>
            <div className="space-y-2.5">
              <Label htmlFor="maxLength" className="font-bold text-sm">글자수 제한 (공백 포함)</Label>
              <div className="relative">
                <Input 
                  id="maxLength" 
                  type="number"
                  className="pl-4 pr-10 border-primary/20"
                  value={newQuestion.maxLength}
                  onChange={(e) => setNewQuestion({...newQuestion, maxLength: parseInt(e.target.value) || 0})}
                />
                <span className="absolute right-4 top-1/2 -translate-y-1/2 text-xs font-medium text-muted-foreground">자</span>
              </div>
            </div>
          </div>
          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="ghost" className="rounded-xl" onClick={() => setIsAddOpen(false)}>취소</Button>
            <Button 
              className="rounded-xl px-8 font-bold" 
              onClick={handleAdd} 
              disabled={isSubmitting || !newQuestion.title}
            >
              {isSubmitting && <Loader2 className="size-4 mr-2 animate-spin" />}
              문항 등록하기
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit Question Dialog */}
      <Dialog open={isEditOpen} onOpenChange={setIsEditOpen}>
        <DialogContent className="sm:max-w-md rounded-3xl">
          <DialogHeader>
            <DialogTitle className="text-xl font-bold">문항 정보 수정</DialogTitle>
          </DialogHeader>
          <div className="space-y-5 py-4">
            <div className="space-y-2.5">
              <Label htmlFor="edit-title" className="font-bold text-sm">문항 내용</Label>
              <Textarea 
                id="edit-title" 
                className="min-h-[120px] resize-none border-primary/20 focus-visible:ring-primary"
                value={editingQuestion?.title || ""}
                onChange={(e) => setEditingQuestion(prev => prev ? {...prev, title: e.target.value} : null)}
              />
            </div>
            <div className="space-y-2.5">
              <Label htmlFor="edit-maxLength" className="font-bold text-sm">글자수 제한</Label>
              <div className="relative">
                <Input 
                  id="edit-maxLength" 
                  type="number"
                  className="pl-4 pr-10 border-primary/20"
                  value={editingQuestion?.maxLength || 0}
                  onChange={(e) => setEditingQuestion(prev => prev ? {...prev, maxLength: parseInt(e.target.value) || 0} : null)}
                />
                <span className="absolute right-4 top-1/2 -translate-y-1/2 text-xs font-medium text-muted-foreground">자</span>
              </div>
            </div>
          </div>
          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="ghost" className="rounded-xl" onClick={() => setIsEditOpen(false)}>취소</Button>
            <Button 
              className="rounded-xl px-8 font-bold" 
              onClick={handleEdit} 
              disabled={isSubmitting || !editingQuestion?.title}
            >
              {isSubmitting && <Loader2 className="size-4 mr-2 animate-spin" />}
              수정 완료
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
