"use client"

import { useEffect, useState } from "react"
import { Loader2, Plus } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { type CoteWikiCategory } from "@/lib/cote-wiki"

const CATEGORY_OPTIONS: CoteWikiCategory[] = ["문법", "알고리즘"]

type CreateDraft = {
  title: string
  category: CoteWikiCategory
}

const INITIAL_DRAFT: CreateDraft = {
  title: "",
  category: "알고리즘",
}

export function CreateCoteWikiDialog({
  open,
  onOpenChange,
  onCreate,
  isCreating,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onCreate: (draft: CreateDraft) => Promise<void> | void
  isCreating: boolean
}) {
  const [draft, setDraft] = useState<CreateDraft>(INITIAL_DRAFT)

  useEffect(() => {
    if (!open) {
      setDraft(INITIAL_DRAFT)
    }
  }, [open])

  const handleSubmit = async () => {
    if (!draft.title.trim() || isCreating) {
      return
    }

    await onCreate({
      title: draft.title.trim(),
      category: draft.category,
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>새 코테 위키 카드</DialogTitle>
          <DialogDescription>
            제목과 분류만 먼저 만든 뒤, 편집 페이지에서 내용을 바로 적어 내려가세요.
          </DialogDescription>
        </DialogHeader>

        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="cote-wiki-title">제목</FieldLabel>
            <Input
              id="cote-wiki-title"
              autoFocus
              value={draft.title}
              onChange={(event) =>
                setDraft((current) => ({ ...current, title: event.target.value }))
              }
              placeholder="예: lowerBound / 다익스트라 / TreeMap"
            />
            <FieldDescription>전날에 다시 찾을 수 있게 짧고 선명한 제목을 쓰세요.</FieldDescription>
          </Field>

          <Field>
            <FieldLabel>분류</FieldLabel>
            <ToggleGroup
              type="single"
              value={draft.category}
              onValueChange={(value) => {
                if (value === "문법" || value === "알고리즘") {
                  setDraft((current) => ({ ...current, category: value }))
                }
              }}
              variant="outline"
              className="w-full"
            >
              {CATEGORY_OPTIONS.map((category) => (
                <ToggleGroupItem key={category} value={category} className="flex-1">
                  {category}
                </ToggleGroupItem>
              ))}
            </ToggleGroup>
          </Field>
        </FieldGroup>

        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={isCreating}>
            취소
          </Button>
          <Button onClick={() => void handleSubmit()} disabled={!draft.title.trim() || isCreating}>
            {isCreating ? <Loader2 className="animate-spin" data-icon="inline-start" /> : <Plus data-icon="inline-start" />}
            편집 페이지로 이동
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
