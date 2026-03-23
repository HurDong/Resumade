"use client";

import { useEffect, useState } from "react";
import { Pin, PinOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  getApplicationInfoDefinition,
  type ApplicationInfoEntry,
} from "@/lib/application-info";

interface ApplicationInfoEditorDialogProps {
  open: boolean;
  entry: ApplicationInfoEntry | null;
  onOpenChange: (open: boolean) => void;
  onSave: (entry: ApplicationInfoEntry) => void;
}

export function ApplicationInfoEditorDialog({
  open,
  entry,
  onOpenChange,
  onSave,
}: ApplicationInfoEditorDialogProps) {
  const [draft, setDraft] = useState<ApplicationInfoEntry | null>(entry);

  useEffect(() => {
    setDraft(entry);
  }, [entry]);

  if (!draft) {
    return null;
  }

  const definition = getApplicationInfoDefinition(draft.category);

  const updateField = (fieldKey: string, value: string) => {
    setDraft((current) =>
      current
        ? {
            ...current,
            values: {
              ...current.values,
              [fieldKey]: value,
            },
          }
        : current
    );
  };

  const handleSave = () => {
    if (!draft) {
      return;
    }

    onSave(draft);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[88vh] max-w-2xl overflow-hidden rounded-[28px] border-border/70 p-0">
        <div className="max-h-[88vh] overflow-y-auto p-6 sm:p-7">
          <DialogHeader className="mb-6">
            <div className="flex flex-wrap items-center gap-2">
              <span
                className={`rounded-full border px-3 py-1 text-[11px] font-black uppercase tracking-[0.24em] ${definition.toneClassName}`}
              >
                {definition.label}
              </span>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                className="h-8 rounded-full px-3 text-xs font-bold"
                onClick={() =>
                  setDraft((current) =>
                    current
                      ? {
                          ...current,
                          pinned: !current.pinned,
                        }
                      : current
                  )
                }
              >
                {draft.pinned ? <PinOff className="size-3.5" /> : <Pin className="size-3.5" />}
                {draft.pinned ? "고정 해제" : "상단 고정"}
              </Button>
            </div>
            <DialogTitle className="text-2xl font-black tracking-tight">
              지원 정보 편집
            </DialogTitle>
            <DialogDescription className="text-sm leading-6">
              {definition.description}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 md:grid-cols-2">
            {definition.fields.map((field) => (
              <div
                key={field.key}
                className={field.multiline ? "space-y-2 md:col-span-2" : "space-y-2"}
              >
                <label className="text-sm font-semibold text-foreground">
                  {field.label}
                </label>
                {field.multiline ? (
                  <Textarea
                    value={draft.values[field.key] ?? ""}
                    onChange={(event) => updateField(field.key, event.target.value)}
                    placeholder={field.placeholder}
                    className="min-h-[132px] resize-none rounded-2xl border-border/60 bg-muted/20 leading-7"
                  />
                ) : (
                  <Input
                    type={field.inputType ?? "text"}
                    value={draft.values[field.key] ?? ""}
                    onChange={(event) => updateField(field.key, event.target.value)}
                    placeholder={field.placeholder}
                    className="h-11 rounded-2xl border-border/60 bg-muted/20"
                  />
                )}
              </div>
            ))}
          </div>
        </div>

        <DialogFooter className="border-t border-border/60 px-6 py-4 sm:px-7">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            닫기
          </Button>
          <Button onClick={handleSave}>저장하기</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
