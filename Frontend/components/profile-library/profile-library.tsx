"use client";

import {
  BadgeCheck,
  Building2,
  CalendarDays,
  Clock3,
  Copy,
  GraduationCap,
  GripVertical,
  Hash,
  Languages,
  PencilLine,
  Plus,
  Search,
  Trophy,
  Zap,
  type LucideIcon,
} from "lucide-react";
import { useDeferredValue, useEffect, useState } from "react";
import { DragDropContext, Droppable, Draggable, type DropResult } from "@hello-pangea/dnd";
import { motion } from "framer-motion";
import { toast } from "sonner";
import {
  createProfileEntry,
  deleteProfileEntry,
  fetchProfileEntries,
  reorderProfileEntries,
  updateProfileEntry,
} from "@/lib/api/profile-library";
import { ProfileLibraryEditor } from "@/components/profile-library/profile-library-editor";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  createEmptyProfileEntry,
  getProfileDateCopyValue,
  getProfileDateParts,
  normalizeProfileDateLabel,
  normalizeProfileEntryForSave,
  profileCategoryOrder,
  type ProfileCategory,
  type ProfileEntry,
} from "@/lib/profile-library";
import { cn } from "@/lib/utils";

type CategoryMeta = {
  label: string;
  description: string;
  icon: LucideIcon;
  toneClassName: string;
  activeClassName: string;
};

type FieldRow = {
  label: string;
  value: string;
  copyable: boolean;
  copyValue?: string;
};

type MetaChip = {
  icon: LucideIcon;
  value: string;
  accent?: boolean;
};

type DraftMode = "create" | "edit";

const categoryMeta: Record<ProfileCategory, CategoryMeta> = {
  certification: {
    label: "자격증",
    description: "자격증명, 발급기관, 등록번호, 취득일",
    icon: BadgeCheck,
    toneClassName:
      "border-violet-200 bg-violet-50/80 text-violet-700",
    activeClassName:
      "data-[state=active]:border-violet-200 data-[state=active]:bg-violet-50/80 data-[state=active]:text-violet-700",
  },
  language: {
    label: "어학",
    description: "시험명, 등급, 등록번호, 취득일",
    icon: Languages,
    toneClassName:
      "border-sky-200 bg-sky-50/80 text-sky-700",
    activeClassName:
      "data-[state=active]:border-sky-200 data-[state=active]:bg-sky-50/80 data-[state=active]:text-sky-700",
  },
  award: {
    label: "수상",
    description: "수상명, 수여 기관, 수상일, 상세 내용",
    icon: Trophy,
    toneClassName:
      "border-amber-200 bg-amber-50/80 text-amber-700",
    activeClassName:
      "data-[state=active]:border-amber-200 data-[state=active]:bg-amber-50/80 data-[state=active]:text-amber-700",
  },
  education: {
    label: "교육활동",
    description: "교육명, 교육 기관, 교육 기간, 시간, 교육 내용",
    icon: GraduationCap,
    toneClassName:
      "border-rose-200 bg-rose-50/80 text-rose-700",
    activeClassName:
      "data-[state=active]:border-rose-200 data-[state=active]:bg-rose-50/80 data-[state=active]:text-rose-700",
  },
  skill: {
    label: "기술 역량",
    description: "기술·언어명, 역량 수준(★), 할 수 있는 것",
    icon: Zap,
    toneClassName:
      "border-emerald-200 bg-emerald-50/80 text-emerald-700",
    activeClassName:
      "data-[state=active]:border-emerald-200 data-[state=active]:bg-emerald-50/80 data-[state=active]:text-emerald-700",
  },
};

function getFieldRows(entry: ProfileEntry): FieldRow[] {
  switch (entry.category) {
    case "certification":
      return [
        { label: "자격증명", value: entry.title, copyable: true },
        { label: "발급기관", value: entry.organization, copyable: true },
        { label: "등록번호", value: entry.referenceId, copyable: true },
        {
          label: "취득일",
          value: normalizeProfileDateLabel(entry.dateLabel),
          copyable: true,
          copyValue: getProfileDateCopyValue(entry.dateLabel),
        },
      ];
    case "language":
      return [
        { label: "어학 종류", value: entry.title, copyable: true },
        { label: "등급", value: entry.highlight, copyable: true },
        {
          label: "취득일",
          value: normalizeProfileDateLabel(entry.dateLabel),
          copyable: true,
          copyValue: getProfileDateCopyValue(entry.dateLabel),
        },
        { label: "등록번호", value: entry.referenceId, copyable: true },
      ];
    case "award":
      return [
        { label: "수상명", value: entry.title, copyable: true },
        { label: "수여 기관", value: entry.organization, copyable: true },
        {
          label: "수상일",
          value: normalizeProfileDateLabel(entry.dateLabel),
          copyable: true,
          copyValue: getProfileDateCopyValue(entry.dateLabel),
        },
        { label: "상세 내용", value: entry.summary, copyable: true },
      ];
    case "education": {
      const periodDates = getProfileDateParts(entry.dateLabel);

      return [
        { label: "교육명", value: entry.title, copyable: true },
        { label: "교육 기관", value: entry.organization, copyable: true },
        ...(periodDates.length >= 2
          ? [
              {
                label: "교육 시작일",
                value: periodDates[0].value,
                copyable: true,
                copyValue: periodDates[0].copyValue,
              },
              {
                label: "교육 종료일",
                value: periodDates[1].value,
                copyable: true,
                copyValue: periodDates[1].copyValue,
              },
            ]
          : [
              {
                label: "교육 기간",
                value: normalizeProfileDateLabel(entry.dateLabel),
                copyable: true,
                copyValue: getProfileDateCopyValue(entry.dateLabel),
              },
            ]),
        { label: "교육 시간", value: entry.highlight, copyable: true },
        { label: "교육 내용", value: entry.summary, copyable: true },
      ];
    }
    case "skill":
      return [
        { label: "기술 / 언어", value: entry.title, copyable: true },
        { label: "역량 수준", value: renderStars(entry.highlight), copyable: false },
        { label: "할 수 있는 것", value: entry.summary, copyable: true },
      ];
  }
}

function renderStars(level: string): string {
  const n = parseInt(level, 10);
  if (isNaN(n) || n < 1 || n > 5) return level || "—";
  const labels = ["", "기초", "입문", "활용", "숙련", "전문가"];
  return "★".repeat(n) + "☆".repeat(5 - n) + `  ${n}/5 · ${labels[n]}`;
}


function getEntryMetaChips(entry: ProfileEntry): MetaChip[] {
  switch (entry.category) {
    case "certification":
      return [
        ...(entry.organization
          ? [{ icon: Building2, value: entry.organization }]
          : []),
        ...(entry.referenceId ? [{ icon: Hash, value: entry.referenceId }] : []),
        ...(entry.dateLabel
          ? [{ icon: CalendarDays, value: normalizeProfileDateLabel(entry.dateLabel), accent: true }]
          : []),
      ];
    case "language":
      return [
        ...(entry.highlight ? [{ icon: BadgeCheck, value: entry.highlight }] : []),
        ...(entry.dateLabel
          ? [{ icon: CalendarDays, value: normalizeProfileDateLabel(entry.dateLabel), accent: true }]
          : []),
        ...(entry.referenceId ? [{ icon: Hash, value: entry.referenceId }] : []),
      ];
    case "award":
      return [
        ...(entry.organization
          ? [{ icon: Building2, value: entry.organization }]
          : []),
        ...(entry.dateLabel
          ? [{ icon: CalendarDays, value: normalizeProfileDateLabel(entry.dateLabel), accent: true }]
          : []),
      ];
    case "education":
      return [
        ...(entry.organization
          ? [{ icon: Building2, value: entry.organization }]
          : []),
        ...(entry.dateLabel
          ? [{ icon: CalendarDays, value: normalizeProfileDateLabel(entry.dateLabel), accent: true }]
          : []),
        ...(entry.highlight ? [{ icon: Clock3, value: entry.highlight }] : []),
      ];
    case "skill":
      return [
        ...(entry.highlight
          ? [{ icon: Zap, value: renderStars(entry.highlight), accent: true }]
          : []),
      ];
  }
}

function getEntryPreview(entry: ProfileEntry) {
  switch (entry.category) {
    case "certification":
      return "기본 정보 입력에 자주 쓰는 자격 항목";
    case "language":
      return "등급과 등록번호를 함께 관리하는 어학 항목";
    case "award":
      return entry.summary || "수상 배경을 짧게 정리한 항목";
    case "education":
      return entry.summary || "교육 내용을 짧게 정리한 항목";
    case "skill":
      return entry.summary || "기술 수준과 활용 범위를 정리한 항목";
  }
}

function getEmptyStateCopy(category: ProfileCategory) {
  switch (category) {
    case "certification":
      return "등록한 자격증이 없습니다. 새 항목을 추가해 주세요.";
    case "language":
      return "등록한 어학 성적이 없습니다. 새 항목을 추가해 주세요.";
    case "award":
      return "등록한 수상 이력이 없습니다. 새 항목을 추가해 주세요.";
    case "education":
      return "등록한 교육활동이 없습니다. 새 항목을 추가해 주세요.";
    case "skill":
      return "등록한 기술 역량이 없습니다. 새 항목을 추가해 주세요.";
  }
}

async function copyField(field: FieldRow) {
  const copyValue = (field.copyValue ?? field.value).trim();

  if (!copyValue) {
    toast.error("복사할 값이 없습니다.");
    return;
  }
  try {
    await navigator.clipboard.writeText(copyValue);
    toast.success(`${field.label} 복사 완료`, {
      description: "바로 붙여넣을 수 있도록 클립보드에 저장했습니다.",
      position: "top-center",
    });
  } catch {
    toast.error("클립보드 복사에 실패했습니다.");
  }
}

function LoadingListSkeleton() {
  return (
    <div className="flex flex-col gap-3 p-4">
      {Array.from({ length: 4 }).map((_, index) => (
        <div
          key={`profile-skeleton-${index}`}
          className="rounded-[18px] border border-border/60 bg-background px-5 py-5"
        >
          <div className="flex items-start gap-4">
            <Skeleton className="size-10 rounded-2xl" />
            <div className="min-w-0 flex-1">
              <Skeleton className="h-5 w-40" />
              <Skeleton className="mt-2 h-4 w-full max-w-md" />
              <div className="mt-4 flex flex-wrap gap-2">
                <Skeleton className="h-7 w-28 rounded-full" />
                <Skeleton className="h-7 w-24 rounded-full" />
              </div>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

export function ProfileLibrary() {
  const [entries, setEntries] = useState<ProfileEntry[]>([]);
  const [activeCategory, setActiveCategory] =
    useState<ProfileCategory>("certification");
  const [selectedEntryId, setSelectedEntryId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [draftEntry, setDraftEntry] = useState<ProfileEntry | null>(null);
  const [draftMode, setDraftMode] = useState<DraftMode>("edit");
  const [editingEntryId, setEditingEntryId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSavingDraft, setIsSavingDraft] = useState(false);
  const [isDeletingDraft, setIsDeletingDraft] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const deferredQuery = useDeferredValue(searchQuery.trim().toLowerCase());

  const loadEntries = async () => {
    try {
      setIsLoading(true);
      setLoadError(null);
      const loadedEntries = await fetchProfileEntries();
      setEntries(loadedEntries);
      setSelectedEntryId((current) => {
        if (current && loadedEntries.some((entry) => entry.id === current)) {
          return current;
        }
        return (
          loadedEntries.find((entry) => entry.category === activeCategory)?.id ??
          loadedEntries[0]?.id ??
          null
        );
      });
    } catch (error) {
      setLoadError(
        error instanceof Error ? error.message : "목록을 불러오지 못했습니다.",
      );
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadEntries();
  }, []);

  const filteredEntries = entries.filter((entry) => {
    if (entry.category !== activeCategory) {
      return false;
    }
    if (!deferredQuery) {
      return true;
    }
    return [
      entry.title,
      entry.organization,
      entry.summary,
      entry.highlight,
      entry.referenceId,
    ]
      .join(" ")
      .toLowerCase()
      .includes(deferredQuery);
  });

  useEffect(() => {
    if (!filteredEntries.some((entry) => entry.id === selectedEntryId)) {
      setSelectedEntryId(filteredEntries[0]?.id ?? null);
    }
  }, [filteredEntries, selectedEntryId]);

  const selectedEntry =
    filteredEntries.find((entry) => entry.id === selectedEntryId) ??
    entries.find((entry) => entry.id === selectedEntryId) ??
    null;

  const openCreateDialog = () => {
    setDraftMode("create");
    setEditingEntryId(null);
    setDraftEntry(createEmptyProfileEntry(activeCategory));
  };

  const openEditDialog = (entry: ProfileEntry) => {
    setDraftMode("edit");
    setEditingEntryId(entry.id);
    setDraftEntry({ ...entry });
  };

  const closeDialog = () => {
    if (isSavingDraft || isDeletingDraft) return;
    setDraftEntry(null);
    setEditingEntryId(null);
  };

  const handleSaveDraft = async () => {
    if (!draftEntry) return;
    const normalizedDraftEntry = normalizeProfileEntryForSave(draftEntry);
    try {
      setIsSavingDraft(true);
      if (draftMode === "create") {
        const createdEntry = await createProfileEntry(normalizedDraftEntry);
        setEntries((current) => [createdEntry, ...current]);
        setSelectedEntryId(createdEntry.id);
      } else if (editingEntryId) {
        const savedEntry = await updateProfileEntry(normalizedDraftEntry);
        setEntries((current) =>
          current.map((entry) => (entry.id === savedEntry.id ? savedEntry : entry)),
        );
        setSelectedEntryId(savedEntry.id);
      }
      setDraftEntry(null);
      setEditingEntryId(null);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "저장에 실패했습니다.");
    } finally {
      setIsSavingDraft(false);
    }
  };

  const handleDragEnd = async (result: DropResult) => {
    if (!result.destination) return;
    if (result.source.index === result.destination.index) return;

    const previousEntries = entries;

    const catEntries = entries.filter((e) => e.category === activeCategory);
    const otherEntries = entries.filter((e) => e.category !== activeCategory);
    const reordered = [...catEntries];
    const [moved] = reordered.splice(result.source.index, 1);
    reordered.splice(result.destination.index, 0, moved);

    setEntries([...reordered, ...otherEntries]);

    try {
      await reorderProfileEntries(activeCategory, reordered.map((e) => e.id));
    } catch {
      setEntries(previousEntries);
      toast.error("순서 저장에 실패했습니다.");
    }
  };

  const handleDeleteDraft = async () => {
    if (!editingEntryId) return;
    try {
      setIsDeletingDraft(true);
      await deleteProfileEntry(editingEntryId);
      const nextEntries = entries.filter((entry) => entry.id !== editingEntryId);
      setEntries(nextEntries);
      setSelectedEntryId(
        nextEntries.find((entry) => entry.category === activeCategory)?.id ??
          nextEntries[0]?.id ??
          null,
      );
      setDraftEntry(null);
      setEditingEntryId(null);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "삭제에 실패했습니다.");
    } finally {
      setIsDeletingDraft(false);
    }
  };

  return (
    <>
      <div className="flex flex-col gap-5">
        {/* Search + Add */}
        <div className="flex items-center gap-3">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              className="h-11 rounded-2xl border-border/60 bg-muted/20 pl-10"
              placeholder="자격증명, 기관명, 교육 내용으로 검색"
            />
          </div>
          <Button
            type="button"
            className="h-11 rounded-full px-5"
            onClick={openCreateDialog}
            disabled={isSavingDraft || isDeletingDraft}
          >
            <Plus className="size-4" />
            새 항목
          </Button>
        </div>

        {/* Tabs */}
        <DragDropContext onDragEnd={handleDragEnd}>
        <Tabs
          value={activeCategory}
          onValueChange={(value) => setActiveCategory(value as ProfileCategory)}
          className="gap-5"
        >
          <TabsList className="h-auto flex-wrap justify-start gap-1.5 rounded-2xl bg-muted/40 p-1.5">
            {profileCategoryOrder.map((category) => {
              const Icon = categoryMeta[category].icon;
              const count = entries.filter((e) => e.category === category).length;

              return (
                <TabsTrigger
                  key={category}
                  value={category}
                  className={cn(
                    "rounded-xl px-4 py-2 text-sm font-bold transition-all",
                    "data-[state=active]:shadow-sm",
                    categoryMeta[category].activeClassName,
                  )}
                >
                  <Icon className="size-3.5" />
                  {categoryMeta[category].label}
                  <span
                    className={cn(
                      "ml-1.5 rounded-full px-1.5 py-0.5 text-[11px] font-black tabular-nums",
                      activeCategory === category
                        ? "bg-current/10 text-current"
                        : "bg-muted text-muted-foreground",
                    )}
                  >
                    {count}
                  </span>
                </TabsTrigger>
              );
            })}
          </TabsList>

          {profileCategoryOrder.map((category) => (
            <TabsContent key={category} value={category} className="mt-0">
              <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">

                {/* ── Left: Entry List ── */}
                <div className="overflow-hidden rounded-[24px] border border-border/60 bg-background shadow-sm">
                  <div className="flex items-center justify-between gap-4 border-b border-border/60 px-5 py-4">
                    <div>
                      <h2 className="text-base font-black tracking-tight text-foreground">
                        {categoryMeta[category].label} 목록
                      </h2>
                      <p className="mt-0.5 text-sm text-muted-foreground">
                        항목을 선택하면 오른쪽에서 필드를 바로 복사할 수 있습니다.
                      </p>
                    </div>
                    <span className="rounded-full border border-border/60 px-2.5 py-1 text-[11px] font-black text-muted-foreground tabular-nums">
                      {filteredEntries.length}개
                    </span>
                  </div>

                  <ScrollArea className="h-[calc(100vh-280px)] min-h-[480px]">
                    {isLoading ? (
                      <LoadingListSkeleton />
                    ) : loadError ? (
                      <div className="p-4">
                        <Empty className="min-h-[300px] rounded-[18px] border border-dashed border-border/60 bg-muted/10">
                          <EmptyHeader>
                            <EmptyMedia variant="icon"><Search /></EmptyMedia>
                            <EmptyTitle>목록을 불러오지 못했습니다.</EmptyTitle>
                            <EmptyDescription>{loadError}</EmptyDescription>
                          </EmptyHeader>
                          <Button type="button" variant="outline" className="rounded-full" onClick={() => void loadEntries()}>
                            다시 시도
                          </Button>
                        </Empty>
                      </div>
                    ) : filteredEntries.length === 0 ? (
                      <div className="p-4">
                        <Empty className="min-h-[300px] rounded-[18px] border border-dashed border-border/60 bg-muted/10">
                          <EmptyHeader>
                            <EmptyMedia variant="icon"><Search /></EmptyMedia>
                            <EmptyTitle>표시할 항목이 없습니다.</EmptyTitle>
                            <EmptyDescription>{getEmptyStateCopy(category)}</EmptyDescription>
                          </EmptyHeader>
                          <Button
                            type="button"
                            className="rounded-full px-5"
                            onClick={openCreateDialog}
                          >
                            <Plus className="size-4" />
                            첫 항목 추가
                          </Button>
                        </Empty>
                      </div>
                    ) : (
                      <Droppable droppableId={`list-${category}`} isDropDisabled={!!deferredQuery}>
                        {(droppableProvided) => (
                          <div
                            ref={droppableProvided.innerRef}
                            {...droppableProvided.droppableProps}
                            className="flex flex-col gap-2.5 p-4"
                          >
                            {filteredEntries.map((entry, index) => {
                              const meta = categoryMeta[entry.category];
                              const isSelected = selectedEntryId === entry.id;
                              const metaChips = getEntryMetaChips(entry);

                              return (
                                <Draggable
                                  key={entry.id}
                                  draggableId={entry.id}
                                  index={index}
                                  isDragDisabled={!!deferredQuery}
                                >
                                  {(draggableProvided, snapshot) => (
                                    <div
                                      ref={draggableProvided.innerRef}
                                      {...draggableProvided.draggableProps}
                                      role="button"
                                      tabIndex={0}
                                      onClick={() => setSelectedEntryId(entry.id)}
                                      onKeyDown={(e) => {
                                        if (e.key === "Enter" || e.key === " ") {
                                          e.preventDefault();
                                          setSelectedEntryId(entry.id);
                                        }
                                      }}
                                      className={cn(
                                        "rounded-[18px] border px-5 py-4 text-left transition-colors",
                                        isSelected
                                          ? "border-primary/30 bg-primary/[0.05]"
                                          : "border-border/60 bg-background hover:border-primary/35 hover:bg-primary/[0.04]",
                                        snapshot.isDragging && "shadow-xl ring-1 ring-primary/20 opacity-95",
                                      )}
                                    >
                                      <div className="flex items-start gap-2">
                                        {/* Drag handle */}
                                        {!deferredQuery && (
                                          <div
                                            {...draggableProvided.dragHandleProps}
                                            className="mt-1 shrink-0 cursor-grab active:cursor-grabbing text-muted-foreground/25 hover:text-muted-foreground/60 transition-colors"
                                            onClick={(e) => e.stopPropagation()}
                                          >
                                            <GripVertical className="size-4" />
                                          </div>
                                        )}

                                        <div className="min-w-0 flex-1">
                                          <span
                                            className={cn(
                                              "mb-2.5 inline-flex rounded-full border px-2.5 py-0.5 text-[11px] font-black uppercase tracking-[0.2em]",
                                              meta.toneClassName,
                                            )}
                                          >
                                            {meta.label}
                                          </span>

                                          <h3 className="truncate text-base font-black tracking-tight text-foreground">
                                            {entry.title || "제목 없는 항목"}
                                          </h3>
                                          <p className="mt-1 text-sm leading-5 text-muted-foreground">
                                            {getEntryPreview(entry)}
                                          </p>

                                          {metaChips.length > 0 && (
                                            <div className="mt-3 flex flex-wrap gap-1.5">
                                              {metaChips.map((chip) => {
                                                const ChipIcon = chip.icon;
                                                return (
                                                  <div
                                                    key={`${entry.id}-${chip.value}`}
                                                    className={cn(
                                                      "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold",
                                                      chip.accent
                                                        ? "border-primary/15 bg-primary/[0.06] text-primary"
                                                        : "border-border/60 bg-muted/20 text-muted-foreground",
                                                    )}
                                                  >
                                                    <ChipIcon className="size-3" />
                                                    {chip.value}
                                                  </div>
                                                );
                                              })}
                                            </div>
                                          )}
                                        </div>

                                        <button
                                          type="button"
                                          className="inline-flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1.5 text-sm font-semibold text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                                          onClick={(e) => {
                                            e.stopPropagation();
                                            openEditDialog(entry);
                                          }}
                                        >
                                          <PencilLine className="size-3.5" />
                                          편집
                                        </button>
                                      </div>
                                    </div>
                                  )}
                                </Draggable>
                              );
                            })}
                            {droppableProvided.placeholder}
                          </div>
                        )}
                      </Droppable>
                    )}
                  </ScrollArea>
                </div>

                {/* ── Right: Copy Panel ── */}
                <div className="overflow-hidden rounded-[24px] border border-border/60 bg-background shadow-sm xl:sticky xl:top-6 xl:self-start">
                  {selectedEntry ? (
                    <>
                      <div className="border-b border-border/60 px-5 py-4">
                        <div className="flex flex-wrap items-center gap-2">
                          <span
                            className={cn(
                              "rounded-full border px-2.5 py-0.5 text-[11px] font-black uppercase tracking-[0.2em]",
                              categoryMeta[selectedEntry.category].toneClassName,
                            )}
                          >
                            {categoryMeta[selectedEntry.category].label}
                          </span>
                          <span className="rounded-full border border-border/60 px-2.5 py-0.5 text-[11px] font-black text-muted-foreground">
                            클릭하면 복사
                          </span>
                        </div>
                        <h2 className="mt-2.5 text-lg font-black tracking-tight text-foreground">
                          {selectedEntry.title || "제목 없는 항목"}
                        </h2>
                        <p className="mt-1 text-sm text-muted-foreground">
                          채용 플랫폼에 붙여넣을 값을 클릭해서 바로 복사합니다.
                        </p>
                      </div>

                      <div className="p-4">
                        <motion.div
                          key={selectedEntry.id}
                          initial={{ opacity: 0, y: 6 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ duration: 0.18, ease: "easeOut" }}
                          className="flex flex-col gap-2"
                        >
                          {getFieldRows(selectedEntry).map((field) =>
                            field.copyable ? (
                              <button
                                key={`${selectedEntry.id}-${field.label}`}
                                type="button"
                                disabled={!(field.copyValue ?? field.value).trim()}
                                onClick={() => void copyField(field)}
                                className="group w-full rounded-2xl border border-border/60 bg-muted/15 px-4 py-3 text-left transition-all hover:border-primary/35 hover:bg-primary/[0.04] disabled:cursor-not-allowed disabled:opacity-40"
                              >
                                <div className="flex items-start justify-between gap-3">
                                  <div className="min-w-0">
                                    <p className="text-[11px] font-black uppercase tracking-[0.2em] text-muted-foreground">
                                      {field.label}
                                    </p>
                                    <p className="mt-1 break-words text-sm font-semibold text-foreground">
                                      {field.value || "—"}
                                    </p>
                                    {field.copyValue && field.copyValue !== field.value ? (
                                      <p className="mt-1 font-mono text-xs text-muted-foreground">
                                        복사값 {field.copyValue}
                                      </p>
                                    ) : null}
                                  </div>
                                  <Copy className="mt-0.5 size-3.5 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100 group-disabled:opacity-0" />
                                </div>
                              </button>
                            ) : (
                              <div
                                key={`${selectedEntry.id}-${field.label}`}
                                className="rounded-2xl border border-border/60 bg-muted/15 px-4 py-3"
                              >
                                <p className="text-[11px] font-black uppercase tracking-[0.2em] text-muted-foreground">
                                  {field.label}
                                </p>
                                <p className="mt-1 text-sm font-black text-primary">
                                  {field.value || "—"}
                                </p>
                              </div>
                            ),
                          )}

                          <Button
                            type="button"
                            variant="outline"
                            className="mt-1 w-full rounded-full"
                            onClick={() => openEditDialog(selectedEntry)}
                          >
                            <PencilLine className="size-3.5" />
                            이 항목 편집
                          </Button>
                        </motion.div>
                      </div>
                    </>
                  ) : (
                    <div className="p-4">
                      <Empty className="min-h-[320px] rounded-[18px] border border-dashed border-border/60 bg-muted/10">
                        <EmptyHeader>
                          <EmptyMedia variant="icon"><Copy /></EmptyMedia>
                          <EmptyTitle>선택된 항목이 없습니다.</EmptyTitle>
                          <EmptyDescription>
                            왼쪽에서 항목을 고르면 등록번호, 등급, 기관명을 바로 복사할 수 있습니다.
                          </EmptyDescription>
                        </EmptyHeader>
                      </Empty>
                    </div>
                  )}
                </div>

              </div>
            </TabsContent>
          ))}
        </Tabs>
        </DragDropContext>
      </div>

      {/* Edit / Create Dialog */}
      <Dialog
        open={Boolean(draftEntry)}
        onOpenChange={(open) => {
          if (!open) closeDialog();
        }}
      >
        <DialogContent className="max-h-[92vh] max-w-2xl overflow-hidden rounded-[28px] border-border/60 p-0">
          {draftEntry ? (
            <ScrollArea className="max-h-[92vh]">
              <div className="p-6 sm:p-7">
                <DialogHeader className="mb-6">
                  <div className="flex flex-wrap items-center gap-2">
                    <span
                      className={cn(
                        "rounded-full border px-3 py-1 text-[11px] font-black uppercase tracking-[0.24em]",
                        categoryMeta[draftEntry.category].toneClassName,
                      )}
                    >
                      {categoryMeta[draftEntry.category].label}
                    </span>
                  </div>
                  <DialogTitle className="text-2xl font-black tracking-tight">
                    {draftMode === "create" ? "새 항목 추가" : "항목 편집"}
                  </DialogTitle>
                  <DialogDescription className="leading-6">
                    저장 전까지 목록은 바뀌지 않습니다.
                  </DialogDescription>
                </DialogHeader>

                <ProfileLibraryEditor
                  entry={draftEntry}
                  mode={draftMode}
                  isSaving={isSavingDraft}
                  isDeleting={isDeletingDraft}
                  categories={profileCategoryOrder.map((cat) => ({
                    value: cat,
                    label: categoryMeta[cat].label,
                  }))}
                  onChange={(patch) =>
                    setDraftEntry((current) =>
                      current ? { ...current, ...patch } : current,
                    )
                  }
                  onSave={() => void handleSaveDraft()}
                  onDelete={() => void handleDeleteDraft()}
                  onCancel={closeDialog}
                />
              </div>
            </ScrollArea>
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
}
