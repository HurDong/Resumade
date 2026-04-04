"use client"

import { DragDropContext, Draggable, Droppable, type DropResult } from "@hello-pangea/dnd"
import { startTransition, useDeferredValue, useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { GripVertical, Loader2, Plus, Search } from "lucide-react"
import { toast } from "sonner"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { CoteWikiCard } from "@/components/cote-wiki/cote-wiki-card"
import { CreateCoteWikiDialog } from "@/components/cote-wiki/create-cote-wiki-dialog"
import {
  createCoteWikiNote,
  deleteCoteWikiNote,
  fetchCoteWikiNotes,
  reorderCoteWikiNotes,
} from "@/lib/api/cote-wiki"
import {
  COTE_WIKI_FILTERS,
  type CoteWikiFilter,
  type CoteWikiNote,
} from "@/lib/cote-wiki"
import { cn } from "@/lib/utils"

function reorderList<T>(items: T[], startIndex: number, endIndex: number) {
  const next = [...items]
  const [moved] = next.splice(startIndex, 1)
  next.splice(endIndex, 0, moved)
  return next
}

function LoadingCards() {
  return (
    <div className="flex flex-col gap-3">
      {Array.from({ length: 4 }).map((_, index) => (
        <div
          key={`wiki-skeleton-${index}`}
          className="rounded-2xl border border-border/70 bg-background px-5 py-5"
        >
          <div className="flex items-start justify-between gap-3">
            <Skeleton className="h-8 w-56" />
            <Skeleton className="h-6 w-16 rounded-full" />
          </div>
        </div>
      ))}
    </div>
  )
}

export function CoteWiki() {
  const router = useRouter()
  const [notes, setNotes] = useState<CoteWikiNote[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState("")
  const [selectedFilter, setSelectedFilter] = useState<CoteWikiFilter>("전체")
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const deferredSearch = useDeferredValue(searchQuery.trim().toLowerCase())

  useEffect(() => {
    let cancelled = false

    fetchCoteWikiNotes()
      .then((loadedNotes) => {
        if (cancelled) {
          return
        }
        setNotes(loadedNotes)
      })
      .catch((error) => {
        if (cancelled) {
          return
        }
        setLoadError(error instanceof Error ? error.message : "코테 위키를 불러오지 못했습니다.")
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  const filteredNotes = useMemo(() => {
    return notes.filter((note) => {
      if (selectedFilter !== "전체" && note.category !== selectedFilter) {
        return false
      }

      if (!deferredSearch) {
        return true
      }

      return `${note.title} ${note.content}`.toLowerCase().includes(deferredSearch)
    })
  }, [deferredSearch, notes, selectedFilter])

  useEffect(() => {
    if (!filteredNotes.some((note) => note.id === expandedId)) {
      setExpandedId(null)
    }
  }, [expandedId, filteredNotes])

  const isOrderLocked = Boolean(deferredSearch) || selectedFilter !== "전체"

  const handleCreate = async ({
    title,
    category,
  }: {
    title: string
    category: "문법" | "알고리즘"
  }) => {
    try {
      setIsCreating(true)
      const created = await createCoteWikiNote({ title, category })
      setNotes((current) => [...current, created].sort((a, b) => a.sortOrder - b.sortOrder))
      setCreateOpen(false)
      router.push(`/vault/wiki/${created.id}/edit`)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "카드를 만들지 못했습니다.")
    } finally {
      setIsCreating(false)
    }
  }

  const handleDelete = async (note: CoteWikiNote) => {
    if (!window.confirm(`"${note.title}" 카드를 삭제할까요?`)) {
      return
    }

    const previous = notes
    const nextNotes = notes
      .filter((item) => item.id !== note.id)
      .map((item, index) => ({ ...item, sortOrder: index }))

    setNotes(nextNotes)
    setExpandedId((current) => (current === note.id ? null : current))

    try {
      await deleteCoteWikiNote(note.id)
      toast.success("카드를 삭제했습니다.")
    } catch (error) {
      setNotes(previous)
      toast.error(error instanceof Error ? error.message : "카드를 삭제하지 못했습니다.")
    }
  }

  const handleDragEnd = async (result: DropResult) => {
    if (!result.destination || isOrderLocked) {
      return
    }

    if (result.source.index === result.destination.index) {
      return
    }

    const previous = notes
    const reordered = reorderList(notes, result.source.index, result.destination.index).map(
      (note, index) => ({
        ...note,
        sortOrder: index,
      }),
    )

    setNotes(reordered)

    try {
      const saved = await reorderCoteWikiNotes(reordered.map((note) => note.id))
      setNotes(saved)
    } catch (error) {
      setNotes(previous)
      toast.error(error instanceof Error ? error.message : "순서를 저장하지 못했습니다.")
    }
  }

  if (isLoading) {
    return <LoadingCards />
  }

  if (loadError) {
    return (
      <Empty className="min-h-[420px] rounded-3xl border border-dashed border-border/70 bg-background/70">
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <Search />
          </EmptyMedia>
          <EmptyTitle>코테 위키를 불러오지 못했습니다.</EmptyTitle>
          <EmptyDescription>{loadError}</EmptyDescription>
        </EmptyHeader>
        <Button onClick={() => window.location.reload()}>다시 시도</Button>
      </Empty>
    )
  }

  return (
    <>
      <div className="flex flex-col gap-5">
        <div className="flex flex-col gap-4 rounded-3xl border border-border/70 bg-background px-5 py-5 shadow-sm">
          <div className="flex flex-col justify-between gap-4 lg:flex-row lg:items-end">
            <div className="flex flex-col gap-1">
              <p className="text-sm font-medium text-muted-foreground">코테 직전 복습용 개인 위키</p>
              <h2 className="text-2xl font-semibold tracking-tight">
                짧은 설명과 Java 템플릿만 빠르게 훑을 수 있게 정리하세요.
              </h2>
            </div>

            <Button onClick={() => setCreateOpen(true)}>
              <Plus data-icon="inline-start" />
              새 카드
            </Button>
          </div>

          <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto]">
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={searchQuery}
                onChange={(event) => {
                  const value = event.target.value
                  startTransition(() => setSearchQuery(value))
                }}
                className="h-11 rounded-2xl border-border/60 bg-muted/20 pl-10"
                placeholder="제목이나 내용으로 검색"
              />
            </div>

            <ToggleGroup
              type="single"
              value={selectedFilter}
              onValueChange={(value) => {
                if (value === "전체" || value === "문법" || value === "알고리즘") {
                  setSelectedFilter(value)
                }
              }}
              variant="outline"
              className="w-full lg:w-auto"
            >
              {COTE_WIKI_FILTERS.map((filter) => (
                <ToggleGroupItem key={filter} value={filter} className="flex-1 lg:flex-none">
                  {filter}
                </ToggleGroupItem>
              ))}
            </ToggleGroup>
          </div>

          {isOrderLocked ? (
            <Alert className="border-amber-200 bg-amber-50/70 text-amber-950 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-100">
              <Loader2 className="animate-none text-amber-500" />
              <AlertTitle>순서 변경이 잠겨 있습니다.</AlertTitle>
              <AlertDescription className="text-amber-900/85 dark:text-amber-200">
                검색/필터 중에는 순서를 변경할 수 없습니다. 전체 보기에서 정렬하세요.
              </AlertDescription>
            </Alert>
          ) : (
            <div className="flex items-center justify-between gap-3 rounded-2xl border border-dashed border-border/70 bg-muted/15 px-4 py-3 text-sm text-muted-foreground">
              <p>카드를 끌어 원하는 복습 순서대로 직접 정렬할 수 있습니다.</p>
              <div className="flex items-center gap-2 text-xs font-medium">
                <GripVertical className="size-4" />
                드래그 정렬
              </div>
            </div>
          )}
        </div>

        {filteredNotes.length === 0 ? (
          <Empty className="min-h-[340px] rounded-3xl border border-dashed border-border/70 bg-background/70">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <Search />
              </EmptyMedia>
              <EmptyTitle>조건에 맞는 카드가 없습니다.</EmptyTitle>
              <EmptyDescription>
                검색어를 지우거나 필터를 전체로 바꾸면 전체 위키 순서를 다시 정렬할 수 있습니다.
              </EmptyDescription>
            </EmptyHeader>
            <Button onClick={() => setCreateOpen(true)}>
              <Plus data-icon="inline-start" />
              새 카드 만들기
            </Button>
          </Empty>
        ) : (
          <DragDropContext onDragEnd={(result) => void handleDragEnd(result)}>
            <Droppable droppableId="cote-wiki-list" isDropDisabled={isOrderLocked}>
              {(droppableProvided) => (
                <div
                  ref={droppableProvided.innerRef}
                  {...droppableProvided.droppableProps}
                  className="flex flex-col gap-3"
                >
                  {filteredNotes.map((note, index) => (
                    <Draggable
                      key={note.id}
                      draggableId={String(note.id)}
                      index={index}
                      isDragDisabled={isOrderLocked}
                    >
                      {(draggableProvided, snapshot) => (
                        <div
                          ref={draggableProvided.innerRef}
                          {...draggableProvided.draggableProps}
                          {...draggableProvided.dragHandleProps}
                          className={cn(snapshot.isDragging && "rotate-[0.4deg]")}
                        >
                          <CoteWikiCard
                            note={note}
                            expanded={expandedId === note.id}
                            onToggle={() =>
                              setExpandedId((current) => (current === note.id ? null : note.id))
                            }
                            onEdit={() => router.push(`/vault/wiki/${note.id}/edit`)}
                            onDelete={() => void handleDelete(note)}
                            className={cn(snapshot.isDragging && "shadow-xl ring-1 ring-primary/20")}
                          />
                        </div>
                      )}
                    </Draggable>
                  ))}
                  {droppableProvided.placeholder}
                </div>
              )}
            </Droppable>
          </DragDropContext>
        )}
      </div>

      <CreateCoteWikiDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onCreate={handleCreate}
        isCreating={isCreating}
      />
    </>
  )
}
