"use client"

import { useCallback, useEffect, useState } from "react"
import { Loader2, Save, ScrollText, Trash2, Upload } from "lucide-react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Textarea } from "@/components/ui/textarea"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import type { PersonalStory } from "@/lib/workspace/types"

const LIFE_STORY_PLACEHOLDER = `고등학교 시절부터 현재까지의 성장 흐름을 하나의 이야기로 적어주세요.

예시 흐름:
- 처음 개발에 관심을 갖게 된 계기
- 어떤 경험을 지나며 관점이 바뀌었는지
- 실패나 시행착오를 통해 만든 기준
- 최근 프로젝트에서 그 기준이 어떻게 이어졌는지

문항이 특정 지점을 요구하면 AI가 이 전체 흐름 안에서 해당 부분만 더 자세히 풀어냅니다.`

export function StoryVault() {
  const [lifeStory, setLifeStory] = useState<PersonalStory | null>(null)
  const [draft, setDraft] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [isBulkOpen, setIsBulkOpen] = useState(false)
  const [bulkJson, setBulkJson] = useState("")
  const [isBulkSubmitting, setIsBulkSubmitting] = useState(false)

  const fetchLifeStory = useCallback(async () => {
    try {
      setIsLoading(true)
      const response = await fetch("/api/personal-stories")
      if (!response.ok) throw new Error("라이프스토리를 불러오는 데 실패했습니다.")
      const data: PersonalStory = await response.json()
      setLifeStory(data)
      setDraft(data.content ?? "")
    } catch (error) {
      console.error("Failed to fetch life story:", error)
      toast.error("오류 발생", { description: "라이프스토리를 불러오는 중 문제가 발생했습니다." })
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchLifeStory()
  }, [fetchLifeStory])

  const handleSave = async () => {
    try {
      setIsSaving(true)
      const response = await fetch("/api/personal-stories", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content: draft }),
      })
      if (!response.ok) throw new Error("라이프스토리 저장에 실패했습니다.")
      const saved: PersonalStory = await response.json()
      setLifeStory(saved)
      setDraft(saved.content ?? "")
      toast.success("저장 완료", { description: "성장과정 라이프스토리를 업데이트했습니다." })
    } catch (error) {
      toast.error("저장 실패", { description: error instanceof Error ? error.message : "저장 중 문제가 발생했습니다." })
    } finally {
      setIsSaving(false)
    }
  }

  const handleClear = async () => {
    if (!confirm("저장된 라이프스토리를 비울까요?")) return
    try {
      setIsDeleting(true)
      const response = await fetch("/api/personal-stories", { method: "DELETE" })
      if (!response.ok) throw new Error("라이프스토리 삭제에 실패했습니다.")
      setLifeStory(null)
      setDraft("")
      toast.success("삭제 완료", { description: "라이프스토리를 비웠습니다." })
    } catch (error) {
      toast.error("삭제 실패", { description: error instanceof Error ? error.message : "삭제 중 문제가 발생했습니다." })
    } finally {
      setIsDeleting(false)
    }
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
      if (!response.ok) throw new Error("라이프스토리 가져오기에 실패했습니다.")
      const saved: PersonalStory = await response.json()
      setLifeStory(saved)
      setDraft(saved.content ?? "")
      toast.success("가져오기 완료", { description: "JSON 내용을 하나의 라이프스토리로 저장했습니다." })
      setBulkJson("")
      setIsBulkOpen(false)
    } catch (error) {
      toast.error("가져오기 실패", { description: error instanceof Error ? error.message : "JSON 형식을 확인해 주세요." })
    } finally {
      setIsBulkSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <section className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">라이프스토리</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            성장과정 문항에 사용할 전체 생애 흐름을 하나의 문서로 관리합니다.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Dialog open={isBulkOpen} onOpenChange={setIsBulkOpen}>
            <DialogTrigger asChild>
              <Button variant="outline" className="gap-2">
                <Upload className="size-4" />
                JSON 가져오기
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-2xl">
              <DialogHeader>
                <DialogTitle>JSON을 라이프스토리로 가져오기</DialogTitle>
                <DialogDescription>
                  기존 조각 JSON 배열을 붙여넣으면 content 값을 순서대로 이어 하나의 라이프스토리로 저장합니다.
                </DialogDescription>
              </DialogHeader>
              <Textarea
                value={bulkJson}
                onChange={(event) => setBulkJson(event.target.value)}
                placeholder='[{"content":"고등학교 시절 ..."}, {"content":"대학교 시절 ..."}]'
                className="min-h-[260px] font-mono text-xs"
              />
              <div className="flex justify-end gap-2 pt-2">
                <Button variant="ghost" onClick={() => setIsBulkOpen(false)}>취소</Button>
                <Button onClick={handleBulkImport} disabled={isBulkSubmitting || !bulkJson.trim()}>
                  {isBulkSubmitting && <Loader2 className="mr-2 size-4 animate-spin" />}
                  가져오기
                </Button>
              </div>
            </DialogContent>
          </Dialog>
          <Button variant="outline" className="gap-2 text-destructive hover:text-destructive" onClick={handleClear} disabled={isDeleting || (!lifeStory && !draft.trim())}>
            {isDeleting ? <Loader2 className="size-4 animate-spin" /> : <Trash2 className="size-4" />}
            비우기
          </Button>
          <Button className="gap-2" onClick={handleSave} disabled={isSaving || isLoading}>
            {isSaving ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
            저장
          </Button>
        </div>
      </section>

      <Card className="overflow-hidden border-border/70">
        <CardHeader className="border-b bg-muted/20">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-lg bg-rose-100 text-rose-600">
              <ScrollText className="size-5" />
            </div>
            <div>
              <CardTitle className="text-base">전체 라이프스토리</CardTitle>
              <CardDescription>
                세부 카테고리 없이 이 문서 전체가 성장과정 RAG 컨텍스트로 들어갑니다.
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="flex h-[420px] items-center justify-center">
              <Loader2 className="size-7 animate-spin text-muted-foreground/50" />
            </div>
          ) : (
            <Textarea
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder={LIFE_STORY_PLACEHOLDER}
              className="min-h-[560px] resize-y rounded-none border-0 p-6 text-sm leading-7 shadow-none focus-visible:ring-0"
            />
          )}
        </CardContent>
      </Card>
    </div>
  )
}
