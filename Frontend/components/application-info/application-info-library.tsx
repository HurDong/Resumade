"use client";

import { useState } from "react";
import {
  Copy,
  Pin,
  PinOff,
  Plus,
  Search,
  Sparkles,
  Trash2,
  UserRound,
} from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import {
  applicantProfileFieldDefinitions,
  applicationInfoCategoryOrder,
  getApplicationInfoCopyText,
  getApplicationInfoDefinition,
  getApplicationInfoEntrySummary,
  getApplicationInfoEntryTitle,
  matchesApplicationInfoSearch,
  type ApplicationInfoCategory,
  type ApplicationInfoEntry,
} from "@/lib/application-info";
import { useApplicationInfoStore } from "@/lib/store/application-info-store";
import { ApplicationInfoEditorDialog } from "./application-info-editor-dialog";

async function copyWithToast(label: string, value: string) {
  if (!value.trim()) {
    return;
  }

  await navigator.clipboard.writeText(value);
  toast.success(`${label} 복사 완료`, {
    description: "클립보드에 저장했습니다.",
    position: "top-center",
  });
}

function sortEntries(entries: ApplicationInfoEntry[]) {
  return [...entries].sort((a, b) => {
    if (a.pinned !== b.pinned) {
      return Number(b.pinned) - Number(a.pinned);
    }
    return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
  });
}

export function ApplicationInfoLibrary() {
  const profile = useApplicationInfoStore((state) => state.profile);
  const entries = useApplicationInfoStore((state) => state.entries);
  const setProfileField = useApplicationInfoStore((state) => state.setProfileField);
  const saveEntry = useApplicationInfoStore((state) => state.saveEntry);
  const deleteEntry = useApplicationInfoStore((state) => state.deleteEntry);
  const duplicateEntry = useApplicationInfoStore((state) => state.duplicateEntry);
  const togglePinned = useApplicationInfoStore((state) => state.togglePinned);
  const createDraftEntry = useApplicationInfoStore((state) => state.createDraftEntry);

  const [query, setQuery] = useState("");
  const [activeCategory, setActiveCategory] = useState<ApplicationInfoCategory | "all">(
    "all"
  );
  const [editingEntry, setEditingEntry] = useState<ApplicationInfoEntry | null>(null);
  const [isEditorOpen, setIsEditorOpen] = useState(false);

  const sortedEntries = sortEntries(entries);
  const filteredEntries = sortedEntries.filter((entry) => {
    if (activeCategory !== "all" && entry.category !== activeCategory) {
      return false;
    }

    return matchesApplicationInfoSearch(entry, query);
  });

  const pinnedCount = entries.filter((entry) => entry.pinned).length;
  const usedCategoryCount = new Set(entries.map((entry) => entry.category)).size;

  const openCreateDialog = (category: ApplicationInfoCategory) => {
    setEditingEntry(createDraftEntry(category));
    setIsEditorOpen(true);
  };

  const handleSaveEntry = (entry: ApplicationInfoEntry) => {
    saveEntry(entry);
    toast.success("지원 정보 저장 완료", {
      description: "이제 작업실과 대시보드에서 바로 복사할 수 있습니다.",
      position: "top-center",
    });
  };

  return (
    <>
      <div className="space-y-6">
        <div className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr_0.9fr]">
          <Card className="overflow-hidden border-border/70">
            <CardHeader className="bg-gradient-to-br from-primary/[0.08] via-background to-background">
              <div className="flex items-center gap-2 text-primary">
                <UserRound className="size-4" />
                <p className="text-xs font-black uppercase tracking-[0.24em]">
                  기본 프로필
                </p>
              </div>
              <CardTitle className="text-2xl font-black tracking-tight">
                지원서 기본 정보
              </CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 p-6 md:grid-cols-2">
              {applicantProfileFieldDefinitions.map((field) => (
                <div
                  key={String(field.key)}
                  className={field.key === "notionUrl" || field.key === "portfolioUrl" ? "space-y-2 md:col-span-2" : "space-y-2"}
                >
                  <label className="text-sm font-semibold text-foreground">
                    {field.label}
                  </label>
                  <Input
                    type={field.inputType ?? "text"}
                    value={profile[field.key]}
                    onChange={(event) => setProfileField(field.key, event.target.value)}
                    placeholder={field.placeholder}
                    className="h-11 rounded-2xl border-border/60 bg-muted/20"
                  />
                </div>
              ))}
            </CardContent>
          </Card>

          <Card className="border-border/70 bg-gradient-to-br from-primary/[0.08] via-background to-background">
            <CardHeader>
              <p className="text-xs font-black uppercase tracking-[0.24em] text-primary">
                라이브러리 현황
              </p>
              <CardTitle className="text-4xl font-black tracking-tight">
                {entries.length}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm text-muted-foreground">
              <p>저장된 지원 정보 카드 수</p>
              <Separator />
              <div className="flex items-center justify-between">
                <span>상단 고정</span>
                <span className="font-black text-foreground">{pinnedCount}</span>
              </div>
              <div className="flex items-center justify-between">
                <span>사용 중 카테고리</span>
                <span className="font-black text-foreground">{usedCategoryCount}</span>
              </div>
            </CardContent>
          </Card>

          <Card className="border-border/70">
            <CardHeader>
              <div className="flex items-center gap-2 text-primary">
                <Sparkles className="size-4" />
                <p className="text-xs font-black uppercase tracking-[0.24em]">
                  빠른 추가
                </p>
              </div>
              <CardTitle className="text-xl font-black tracking-tight">
                자주 쓰는 제출 정보를 먼저 등록하세요
              </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-wrap gap-2">
              {applicationInfoCategoryOrder.map((category) => {
                const definition = getApplicationInfoDefinition(category);

                return (
                  <Button
                    key={category}
                    type="button"
                    variant="outline"
                    className="rounded-full"
                    onClick={() => openCreateDialog(category)}
                  >
                    <Plus className="size-3.5" />
                    {definition.label}
                  </Button>
                );
              })}
            </CardContent>
          </Card>
        </div>

        <Card className="border-border/70">
          <CardHeader className="gap-4">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <p className="text-xs font-black uppercase tracking-[0.24em] text-primary">
                  지원 정보 라이브러리
                </p>
                <CardTitle className="mt-1 text-2xl font-black tracking-tight">
                  등록번호, 기간, 설명을 카드로 관리
                </CardTitle>
              </div>
              <div className="relative w-full max-w-md">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="시험명, 기관명, 등록번호, 설명으로 검색"
                  className="h-11 rounded-2xl border-border/60 bg-muted/20 pl-10"
                />
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                size="sm"
                variant={activeCategory === "all" ? "default" : "outline"}
                className="rounded-full"
                onClick={() => setActiveCategory("all")}
              >
                전체
              </Button>
              {applicationInfoCategoryOrder.map((category) => {
                const definition = getApplicationInfoDefinition(category);
                const count = entries.filter((entry) => entry.category === category).length;

                return (
                  <Button
                    key={category}
                    type="button"
                    size="sm"
                    variant={activeCategory === category ? "default" : "outline"}
                    className="rounded-full"
                    onClick={() => setActiveCategory(category)}
                  >
                    {definition.label}
                    {count > 0 ? (
                      <span className="ml-1 text-[11px] font-black opacity-70">{count}</span>
                    ) : null}
                  </Button>
                );
              })}
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            {filteredEntries.length === 0 ? (
              <div className="rounded-[28px] border border-dashed border-border/70 bg-muted/10 px-6 py-16 text-center">
                <p className="text-lg font-black tracking-tight text-foreground">
                  {entries.length === 0
                    ? "아직 등록된 지원 정보가 없습니다."
                    : "조건에 맞는 지원 정보가 없습니다."}
                </p>
                <p className="mt-2 text-sm leading-6 text-muted-foreground">
                  {entries.length === 0
                    ? "상단의 빠른 추가 버튼으로 자격증, 활동, 수상 내역을 먼저 저장해 주세요."
                    : "검색어나 카테고리를 바꾸거나 새 항목을 추가해 보세요."}
                </p>
              </div>
            ) : (
              <div className="grid gap-4 xl:grid-cols-2">
                {filteredEntries.map((entry) => {
                  const definition = getApplicationInfoDefinition(entry.category);

                  return (
                    <section
                      key={entry.id}
                      className="rounded-[26px] border border-border/60 bg-background p-5 shadow-sm"
                    >
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div className="min-w-0 space-y-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <Badge className={`border ${definition.toneClassName}`} variant="outline">
                              {definition.label}
                            </Badge>
                            {entry.pinned ? (
                              <Badge variant="secondary" className="gap-1 font-bold">
                                <Pin className="size-3.5" />
                                자주 사용
                              </Badge>
                            ) : null}
                          </div>
                          <h3 className="break-words text-xl font-black tracking-tight text-foreground">
                            {getApplicationInfoEntryTitle(entry)}
                          </h3>
                          <p className="break-words text-sm leading-6 text-muted-foreground">
                            {getApplicationInfoEntrySummary(entry) || "세부 필드를 입력하면 요약 문구가 표시됩니다."}
                          </p>
                        </div>
                        <div className="flex flex-wrap items-center gap-2">
                          <Button
                            size="sm"
                            variant="outline"
                            className="rounded-full"
                            onClick={() =>
                              void copyWithToast(
                                `${getApplicationInfoEntryTitle(entry)} 요약`,
                                getApplicationInfoCopyText(entry, "summary")
                              )
                            }
                          >
                            <Copy className="size-3.5" />
                            요약 복사
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            className="rounded-full"
                            onClick={() => {
                              setEditingEntry(entry);
                              setIsEditorOpen(true);
                            }}
                          >
                            편집
                          </Button>
                        </div>
                      </div>

                      <div className="mt-5 flex flex-wrap items-center gap-2 border-t border-border/60 pt-4">
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          className="rounded-full"
                          onClick={() =>
                            void copyWithToast(
                              `${getApplicationInfoEntryTitle(entry)} 상세`,
                              getApplicationInfoCopyText(entry)
                            )
                          }
                        >
                          <Copy className="size-3.5" />
                          상세 복사
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          className="rounded-full"
                          onClick={() => {
                            duplicateEntry(entry.id);
                            toast.success("지원 정보 복제 완료", {
                              description: "비슷한 항목을 빠르게 수정할 수 있도록 복제했습니다.",
                              position: "top-center",
                            });
                          }}
                        >
                          복제
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          className="rounded-full"
                          onClick={() => togglePinned(entry.id)}
                        >
                          {entry.pinned ? <PinOff className="size-3.5" /> : <Pin className="size-3.5" />}
                          {entry.pinned ? "고정 해제" : "고정"}
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          className="rounded-full text-destructive hover:text-destructive"
                          onClick={() => {
                            if (!confirm("이 지원 정보 카드를 삭제하시겠습니까?")) {
                              return;
                            }
                            deleteEntry(entry.id);
                          }}
                        >
                          <Trash2 className="size-3.5" />
                          삭제
                        </Button>
                      </div>
                    </section>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <ApplicationInfoEditorDialog
        open={isEditorOpen}
        entry={editingEntry}
        onOpenChange={setIsEditorOpen}
        onSave={handleSaveEntry}
      />
    </>
  );
}
