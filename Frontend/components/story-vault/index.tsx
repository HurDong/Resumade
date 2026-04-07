"use client"

import { useEffect, useState, useCallback } from "react"
import { Plus, UserRound, Clock, Tag, MessageSquare, Trash2, Edit2, Loader2 } from "lucide-react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { type PersonalStory, STORY_TYPE_LABELS } from "@/lib/workspace/types"
import { StoryForm } from "./story-form"
import { StoryCard } from "./story-card"

export function StoryVault() {
  const [stories, setStories] = useState<PersonalStory[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingStory, setEditingStory] = useState<PersonalStory | null>(null)

  const fetchStories = useCallback(async () => {
    try {
      setIsLoading(true)
      const response = await fetch("/api/personal-stories")
      if (!response.ok) throw new Error("서사 목록을 불러오는 데 실패했습니다.")
      const data = await response.json()
      setStories(data)
    } catch (error) {
      console.error("Failed to fetch stories:", error)
      toast.error("오류 발생", { description: "서림 목록을 불러오는 중 문제가 발생했습니다." })
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchStories()
  }, [fetchStories])

  const handleOpenAddForm = () => {
    setEditingStory(null)
    setIsFormOpen(true)
  }

  const handleEdit = (story: PersonalStory) => {
    setEditingStory(story)
    setIsFormOpen(true)
  }

  const handleDelete = async (id: number) => {
    if (!confirm("정말 이 서사를 삭제하시겠습니까?")) return

    try {
      const response = await fetch(`/api/personal-stories/${id}`, { method: "DELETE" })
      if (!response.ok) throw new Error("삭제에 실패했습니다.")
      
      setStories(prev => prev.filter(s => s.id !== id))
      toast.success("삭제 완료", { description: "인생 서사가 보관소에서 제거되었습니다." })
    } catch (error) {
      toast.error("삭제 실패", { description: "서사를 삭제하는 중 오류가 발생했습니다." })
    }
  }

  const handleFormSuccess = () => {
    setIsFormOpen(false)
    fetchStories()
  }

  return (
    <div className="mx-auto max-w-6xl space-y-8">
      <section className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">인생 서사 보관소</h2>
          <p className="text-muted-foreground">
            성장과정의 재료가 되는 삶의 전환점과 가치관 형성 경험을 기록하세요.
          </p>
        </div>
        <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
          <DialogTrigger asChild>
            <Button onClick={handleOpenAddForm} className="gap-2">
              <Plus className="size-4" />
              서사 추가하기
            </Button>
          </DialogTrigger>
          <DialogContent className="max-w-2xl">
            <DialogHeader>
              <DialogTitle>{editingStory ? "서사 수정" : "새로운 인생 서사 등록"}</DialogTitle>
              <DialogDescription>
                구체적인 정황과 그때 느낀 감정, 변화된 생각을 적어두면 AI가 더 진정성 있는 글을 써줍니다.
              </DialogDescription>
            </DialogHeader>
            <StoryForm 
              initialData={editingStory} 
              onSuccess={handleFormSuccess} 
              onCancel={() => setIsFormOpen(false)} 
            />
          </DialogContent>
        </Dialog>
      </section>

      {isLoading ? (
        <div className="flex h-64 items-center justify-center">
          <Loader2 className="size-8 animate-spin text-muted-foreground/50" />
        </div>
      ) : stories.length === 0 ? (
        <Card className="flex flex-col items-center justify-center border-dashed p-12 text-center">
          <div className="rounded-full bg-muted p-4">
            <UserRound className="size-8 text-muted-foreground/50" />
          </div>
          <CardTitle className="mt-4">저장된 서사가 없습니다</CardTitle>
          <CardDescription className="max-w-xs">
            첫 번째 인생 서사 소재를 등록하여 성장과정 문항 작성을 준비하세요.
          </CardDescription>
          <Button onClick={handleOpenAddForm} variant="outline" className="mt-6">
            첫 서사 등록하기
          </Button>
        </Card>
      ) : (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {stories.map(story => (
            <StoryCard 
              key={story.id} 
              story={story} 
              onEdit={() => handleEdit(story)}
              onDelete={() => handleDelete(story.id)}
            />
          ))}
        </div>
      )}
    </div>
  )
}
