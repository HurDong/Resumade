"use client"

import { useEffect, useState, useCallback } from "react"
import { Plus, UserRound, Upload, Loader2 } from "lucide-react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Textarea } from "@/components/ui/textarea"
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
  const [isBulkOpen, setIsBulkOpen] = useState(false)
  const [bulkJson, setBulkJson] = useState("")
  const [isBulkSubmitting, setIsBulkSubmitting] = useState(false)

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

  const handleBulkImport = async () => {
    try {
      const parsed = JSON.parse(bulkJson)
      if (!Array.isArray(parsed)) throw new Error("JSON 배열 형식이어야 합니다.")
      setIsBulkSubmitting(true)
      const response = await fetch("/api/personal-stories/bulk", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(parsed),
      })
      if (!response.ok) throw new Error("일괄 저장에 실패했습니다.")
      const saved: PersonalStory[] = await response.json()
      toast.success("일괄 가져오기 완료", { description: `${saved.length}개의 서사가 저장되었습니다.` })
      setBulkJson("")
      setIsBulkOpen(false)
      fetchStories()
    } catch (e) {
      toast.error("가져오기 실패", { description: e instanceof Error ? e.message : "JSON 형식을 확인해 주세요." })
    } finally {
      setIsBulkSubmitting(false)
    }
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
        <div className="flex gap-2">
          {/* JSON 일괄 가져오기 */}
          <Dialog open={isBulkOpen} onOpenChange={setIsBulkOpen}>
            <DialogTrigger asChild>
              <Button variant="outline" className="gap-2">
                <Upload className="size-4" />
                JSON 가져오기
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-2xl">
              <DialogHeader>
                <DialogTitle>서사 일괄 가져오기</DialogTitle>
                <DialogDescription>
                  아래 형식의 JSON 배열을 붙여넣으면 한 번에 저장됩니다.
                  <code className="mt-2 block rounded bg-muted p-2 text-xs leading-relaxed">
                    {`[{"type":"TURNING_POINT","period":"고등학교 시절","content":"...","keywords":["도전"]},\n {"type":"WRITING_GUIDE","period":null,"content":"[강조 역량]\\n- ...","keywords":[]}]`}
                  </code>
                </DialogDescription>
              </DialogHeader>
              <Textarea
                value={bulkJson}
                onChange={(e) => setBulkJson(e.target.value)}
                placeholder='[{"type":"TURNING_POINT","period":"...","content":"...","keywords":["..."]}]'
                className="min-h-[240px] font-mono text-xs"
              />
              <div className="flex justify-end gap-2 pt-2">
                <Button variant="ghost" onClick={() => setIsBulkOpen(false)}>취소</Button>
                <Button onClick={handleBulkImport} disabled={isBulkSubmitting || !bulkJson.trim()}>
                  {isBulkSubmitting && <Loader2 className="mr-2 size-4 animate-spin" />}
                  저장하기
                </Button>
              </div>
            </DialogContent>
          </Dialog>

          {/* 단건 추가 */}
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
        </div>
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
