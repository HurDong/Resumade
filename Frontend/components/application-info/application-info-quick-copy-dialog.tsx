"use client";

import Link from "next/link";
import { useState, type ReactNode } from "react";
import { Copy, ExternalLink, Pin, Search, UserRound } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  applicationInfoCategoryOrder,
  getApplicationInfoCopyText,
  getApplicationInfoDefinition,
  getApplicationInfoEntryFields,
  getApplicationInfoEntrySummary,
  getApplicationInfoEntryTitle,
  getProfileCopyFields,
  matchesApplicationInfoSearch,
} from "@/lib/application-info";
import { useApplicationInfoStore } from "@/lib/store/application-info-store";

interface ApplicationInfoQuickCopyDialogProps {
  trigger: ReactNode;
}

async function copyWithToast(label: string, value: string) {
  if (!value.trim()) {
    return;
  }

  await navigator.clipboard.writeText(value);
  toast.success(`${label} 복사 완료`, {
    description: "바로 붙여넣을 수 있도록 클립보드에 저장했습니다.",
    position: "top-center",
  });
}

export function ApplicationInfoQuickCopyDialog({
  trigger,
}: ApplicationInfoQuickCopyDialogProps) {
  const profile = useApplicationInfoStore((state) => state.profile);
  const entries = useApplicationInfoStore((state) => state.entries);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");

  const sortedEntries = [...entries].sort((a, b) => {
    if (a.pinned !== b.pinned) {
      return Number(b.pinned) - Number(a.pinned);
    }
    return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
  });

  const filteredEntries = applicationInfoCategoryOrder
    .flatMap((category) => sortedEntries.filter((entry) => entry.category === category))
    .filter((entry) => matchesApplicationInfoSearch(entry, query));

  const profileFields = getProfileCopyFields(profile);
  const hasAnyData = profileFields.length > 0 || sortedEntries.length > 0;

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen);
        if (!nextOpen) {
          setQuery("");
        }
      }}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="max-h-[88vh] max-w-5xl overflow-hidden rounded-[28px] border-border/70 p-0">
        <DialogHeader className="border-b border-border/60 px-6 py-5 sm:px-7">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="space-y-2">
              <DialogTitle className="text-2xl font-black tracking-tight">
                지원 정보 퀵 라이브러리
              </DialogTitle>
              <DialogDescription className="text-sm leading-6">
                등록번호, 기간, 설명을 카드별로 바로 복사합니다. 고정한 항목이 먼저 보입니다.
              </DialogDescription>
            </div>
            <Button asChild variant="outline" className="rounded-full">
              <Link href="/settings">
                스펙 라이브러리에서 관리
                <ExternalLink className="size-4" />
              </Link>
            </Button>
          </div>
          <div className="mt-4 relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="시험명, 기관명, 등록번호, 설명으로 검색"
              className="h-11 rounded-2xl border-border/60 bg-muted/20 pl-10"
            />
          </div>
        </DialogHeader>

        <ScrollArea className="h-full max-h-[calc(88vh-170px)]">
          <div className="space-y-6 p-6 sm:p-7">
            {profileFields.length > 0 ? (
              <section className="rounded-[24px] border border-border/60 bg-muted/15 p-5">
                <div className="mb-4 flex items-center gap-2">
                  <UserRound className="size-4 text-primary" />
                  <h3 className="text-sm font-black uppercase tracking-[0.24em] text-primary">
                    기본 프로필
                  </h3>
                </div>
                <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                  {profileFields.map((field) => (
                    <button
                      key={String(field.key)}
                      type="button"
                      onClick={() => void copyWithToast(field.label, field.value)}
                      className="rounded-2xl border border-border/60 bg-background px-4 py-3 text-left transition-all hover:border-primary/35 hover:bg-primary/[0.04]"
                    >
                      <p className="text-[11px] font-black uppercase tracking-[0.2em] text-muted-foreground">
                        {field.label}
                      </p>
                      <p className="mt-1 break-words text-sm font-semibold text-foreground">
                        {field.value}
                      </p>
                    </button>
                  ))}
                </div>
              </section>
            ) : null}

            {!hasAnyData ? (
              <div className="rounded-[28px] border border-dashed border-border/70 bg-muted/10 px-6 py-16 text-center">
                <p className="text-lg font-black tracking-tight text-foreground">
                  아직 저장된 지원 정보가 없습니다.
                </p>
                <p className="mt-2 text-sm leading-6 text-muted-foreground">
                  스펙 라이브러리에서 자격증, 활동, 수상 내역을 먼저 등록하면 여기서 바로 복사할 수 있습니다.
                </p>
                <Button asChild className="mt-6 rounded-full px-5">
                  <Link href="/settings">스펙 라이브러리로 이동</Link>
                </Button>
              </div>
            ) : null}

            {hasAnyData && filteredEntries.length === 0 ? (
              <div className="rounded-[28px] border border-dashed border-border/70 bg-muted/10 px-6 py-16 text-center">
                <p className="text-lg font-black tracking-tight text-foreground">
                  검색 결과가 없습니다.
                </p>
                <p className="mt-2 text-sm leading-6 text-muted-foreground">
                  다른 키워드로 검색하거나 스펙 라이브러리에서 항목을 추가해 주세요.
                </p>
              </div>
            ) : null}

            {filteredEntries.length > 0 ? (
              <div className="grid gap-4 xl:grid-cols-2">
                {filteredEntries.map((entry) => {
                  const definition = getApplicationInfoDefinition(entry.category);
                  const fields = getApplicationInfoEntryFields(entry);

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
                          <h3 className="break-words text-lg font-black tracking-tight text-foreground">
                            {getApplicationInfoEntryTitle(entry)}
                          </h3>
                          <p className="break-words text-sm leading-6 text-muted-foreground">
                            {getApplicationInfoEntrySummary(entry) || "세부 필드를 입력하면 여기에 요약이 표시됩니다."}
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
                        </div>
                      </div>

                      {fields.length > 0 ? (
                        <div className="mt-4 grid gap-2 sm:grid-cols-2">
                          {fields.map((field) => (
                            <button
                              key={field.key}
                              type="button"
                              onClick={() =>
                                void copyWithToast(
                                  `${getApplicationInfoEntryTitle(entry)} · ${field.label}`,
                                  field.value
                                )
                              }
                              className="rounded-2xl border border-border/60 bg-muted/15 px-4 py-3 text-left transition-all hover:border-primary/35 hover:bg-primary/[0.04]"
                            >
                              <p className="text-[11px] font-black uppercase tracking-[0.2em] text-muted-foreground">
                                {field.label}
                              </p>
                              <p
                                className={`mt-1 break-words text-sm font-semibold text-foreground ${
                                  field.multiline ? "leading-6" : ""
                                }`}
                              >
                                {field.value}
                              </p>
                            </button>
                          ))}
                        </div>
                      ) : null}
                    </section>
                  );
                })}
              </div>
            ) : null}
          </div>
        </ScrollArea>
      </DialogContent>
    </Dialog>
  );
}
