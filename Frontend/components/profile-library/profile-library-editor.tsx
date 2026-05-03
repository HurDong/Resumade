"use client";

import { LoaderCircle, Trash2 } from "lucide-react";
import {
  normalizeProfileDateLabel,
  type ProfileCategory,
  type ProfileEntry,
} from "@/lib/profile-library";
import { Button } from "@/components/ui/button";
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";

type CategoryOption = {
  value: ProfileCategory;
  label: string;
};

type ProfileLibraryEditorProps = {
  entry: ProfileEntry | null;
  categories: CategoryOption[];
  mode: "create" | "edit";
  isSaving: boolean;
  isDeleting: boolean;
  onChange: (patch: Partial<ProfileEntry>) => void;
  onSave: () => void;
  onDelete: () => void;
  onCancel: () => void;
};

const STAR_OPTIONS = [
  { value: "1", label: "★☆☆☆☆  1 — 기초 (개념 이해, 튜토리얼 수준)" },
  { value: "2", label: "★★☆☆☆  2 — 입문 (간단한 기능 구현 가능)" },
  { value: "3", label: "★★★☆☆  3 — 활용 (실무 프로젝트 적용 경험)" },
  { value: "4", label: "★★★★☆  4 — 숙련 (독립적 설계·구현 가능)" },
  { value: "5", label: "★★★★★  5 — 전문가 (최적화·아키텍처 설계)" },
];

const editorMeta: Record<
  ProfileCategory,
  {
    titleLabel: string;
    titlePlaceholder: string;
    dateLabel?: string;
    datePlaceholder?: string;
    organizationLabel?: string;
    organizationPlaceholder?: string;
    highlightLabel?: string;
    highlightPlaceholder?: string;
    highlightIsStarSelect?: boolean;
    summaryLabel?: string;
    summaryPlaceholder?: string;
    referenceLabel?: string;
    referencePlaceholder?: string;
    referenceDescription?: string;
  }
> = {
  certification: {
    titleLabel: "자격증명",
    titlePlaceholder: "정보처리기사",
    dateLabel: "취득일",
    datePlaceholder: "2026.03.27 또는 20260327",
    organizationLabel: "발급기관",
    organizationPlaceholder: "한국산업인력공단",
    referenceLabel: "등록번호",
    referencePlaceholder: "등록번호를 입력하세요",
    referenceDescription: "채용 사이트 입력칸에 그대로 붙여 넣을 값을 입력합니다.",
  },
  language: {
    titleLabel: "어학 종류",
    titlePlaceholder: "OPIc",
    dateLabel: "취득일",
    datePlaceholder: "2026.03.27 또는 20260327",
    highlightLabel: "등급",
    highlightPlaceholder: "IH",
    referenceLabel: "등록번호",
    referencePlaceholder: "등록번호를 입력하세요",
    referenceDescription: "등급과 등록번호를 따로 적는 폼 기준으로 관리합니다.",
  },
  award: {
    titleLabel: "수상명",
    titlePlaceholder: "광명융합기술교육원 우수상",
    dateLabel: "수상일",
    datePlaceholder: "2026.03.27 또는 20260327",
    organizationLabel: "수여 기관",
    organizationPlaceholder: "한국폴리텍대학 광명융합기술교육원",
    summaryLabel: "상세 내용",
    summaryPlaceholder: "수상 배경이나 공로를 한두 문장으로 정리합니다.",
  },
  education: {
    titleLabel: "교육명",
    titlePlaceholder: "삼성 청년 SW 아카데미 11기",
    dateLabel: "교육 기간",
    datePlaceholder: "2024.01.02 - 2024.12.19 또는 20240102 - 20241219",
    organizationLabel: "교육 기관",
    organizationPlaceholder: "SSAFY",
    highlightLabel: "교육 시간",
    highlightPlaceholder: "1600시간",
    summaryLabel: "교육 내용",
    summaryPlaceholder:
      "채용 사이트 입력칸에 붙일 수 있도록 교육 내용을 짧게 정리합니다.",
  },
  skill: {
    titleLabel: "기술 / 언어",
    titlePlaceholder: "Java, Spring Boot, React …",
    highlightLabel: "역량 수준",
    highlightIsStarSelect: true,
    summaryLabel: "할 수 있는 것",
    summaryPlaceholder:
      "이 기술로 어느 수준까지 구현할 수 있는지 한두 줄로 정리합니다.",
  },
};

export function ProfileLibraryEditor({
  entry,
  categories,
  mode,
  isSaving,
  isDeleting,
  onChange,
  onSave,
  onDelete,
  onCancel,
}: ProfileLibraryEditorProps) {
  if (!entry) {
    return null;
  }

  const meta = editorMeta[entry.category];

  return (
    <div className="flex flex-col gap-6">
      <FieldGroup>
        <Field>
          <FieldLabel htmlFor="entry-category">카테고리</FieldLabel>
          <FieldContent>
            <Select
              value={entry.category}
              onValueChange={(value) =>
                onChange({ category: value as ProfileCategory })
              }
            >
              <SelectTrigger
                id="entry-category"
                className="h-11 w-full rounded-2xl border-border/60 bg-muted/20"
              >
                <SelectValue placeholder="카테고리를 선택하세요." />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {categories.map((category) => (
                    <SelectItem key={category.value} value={category.value}>
                      {category.label}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </FieldContent>
        </Field>

        <Field>
          <FieldLabel htmlFor="entry-title">{meta.titleLabel}</FieldLabel>
          <FieldContent>
            <Input
              id="entry-title"
              value={entry.title}
              onChange={(event) => onChange({ title: event.target.value })}
              placeholder={meta.titlePlaceholder}
              className="h-11 rounded-2xl border-border/60 bg-muted/20"
            />
          </FieldContent>
        </Field>

        {meta.organizationLabel ? (
          <Field>
            <FieldLabel htmlFor="entry-organization">
              {meta.organizationLabel}
            </FieldLabel>
            <FieldContent>
              <Input
                id="entry-organization"
                value={entry.organization}
                onChange={(event) =>
                  onChange({ organization: event.target.value })
                }
                placeholder={meta.organizationPlaceholder}
                className="h-11 rounded-2xl border-border/60 bg-muted/20"
              />
            </FieldContent>
          </Field>
        ) : null}

        {meta.dateLabel ? (
          <Field>
            <FieldLabel htmlFor="entry-date-label">{meta.dateLabel}</FieldLabel>
            <FieldContent>
              <Input
                id="entry-date-label"
                value={entry.dateLabel}
                onChange={(event) => onChange({ dateLabel: event.target.value })}
                onBlur={(event) =>
                  onChange({
                    dateLabel: normalizeProfileDateLabel(event.target.value),
                  })
                }
                placeholder={meta.datePlaceholder}
                className="h-11 rounded-2xl border-border/60 bg-muted/20"
              />
            </FieldContent>
          </Field>
        ) : null}

        {meta.highlightLabel ? (
          <Field>
            <FieldLabel htmlFor="entry-highlight">
              {meta.highlightLabel}
            </FieldLabel>
            <FieldContent>
              {meta.highlightIsStarSelect ? (
                <Select
                  value={entry.highlight}
                  onValueChange={(value) => onChange({ highlight: value })}
                >
                  <SelectTrigger
                    id="entry-highlight"
                    className="h-11 w-full rounded-2xl border-border/60 bg-muted/20"
                  >
                    <SelectValue placeholder="역량 수준을 선택하세요." />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {STAR_OPTIONS.map((opt) => (
                        <SelectItem key={opt.value} value={opt.value}>
                          {opt.label}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              ) : (
                <Input
                  id="entry-highlight"
                  value={entry.highlight}
                  onChange={(event) => onChange({ highlight: event.target.value })}
                  placeholder={meta.highlightPlaceholder}
                  className="h-11 rounded-2xl border-border/60 bg-muted/20"
                />
              )}
            </FieldContent>
          </Field>
        ) : null}

        {meta.referenceLabel ? (
          <Field>
            <FieldLabel htmlFor="entry-reference-id">
              {meta.referenceLabel}
            </FieldLabel>
            <FieldContent>
              <Input
                id="entry-reference-id"
                value={entry.referenceId}
                onChange={(event) =>
                  onChange({ referenceId: event.target.value })
                }
                placeholder={meta.referencePlaceholder}
                className="h-11 rounded-2xl border-border/60 bg-muted/20"
              />
              {meta.referenceDescription ? (
                <FieldDescription>{meta.referenceDescription}</FieldDescription>
              ) : null}
            </FieldContent>
          </Field>
        ) : null}

        {meta.summaryLabel ? (
          <Field>
            <FieldLabel htmlFor="entry-summary">{meta.summaryLabel}</FieldLabel>
            <FieldContent>
              <Textarea
                id="entry-summary"
                value={entry.summary}
                onChange={(event) => onChange({ summary: event.target.value })}
                className="min-h-[132px] resize-none rounded-2xl border-border/60 bg-muted/20 leading-7"
                placeholder={meta.summaryPlaceholder}
              />
            </FieldContent>
          </Field>
        ) : null}
      </FieldGroup>

      {/* Footer actions */}
      <div className="flex items-center justify-between border-t border-border/60 pt-5">
        <div>
          {mode === "edit" ? (
            <Button
              type="button"
              variant="ghost"
              className="rounded-full text-destructive hover:text-destructive"
              onClick={onDelete}
              disabled={isDeleting || isSaving}
            >
              <Trash2 className="size-3.5" />
              {isDeleting ? "삭제 중..." : "삭제"}
            </Button>
          ) : null}
        </div>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            className="rounded-full"
            onClick={onCancel}
            disabled={isSaving || isDeleting}
          >
            취소
          </Button>
          <Button
            type="button"
            className="rounded-full px-5"
            onClick={onSave}
            disabled={isSaving || isDeleting}
          >
            {isSaving ? (
              <LoaderCircle className="size-3.5 animate-spin" />
            ) : null}
            {mode === "create" ? "저장 후 추가" : "저장하기"}
          </Button>
        </div>
      </div>
    </div>
  );
}
