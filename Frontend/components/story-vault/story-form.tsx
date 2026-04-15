"use client"

import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import * as z from "zod"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Badge } from "@/components/ui/badge"
import { X, Loader2 } from "lucide-react"
import { type PersonalStory, STORY_TYPE_LABELS, type StoryType } from "@/lib/workspace/types"

const formSchema = z.object({
  type: z.enum(["TURNING_POINT", "VALUE", "ENVIRONMENT", "INFLUENCE", "FAILURE_RECOVERY", "MILESTONE", "WRITING_GUIDE"]),
  period: z.string().optional(),
  content: z.string().min(20, "20자 이상 구체적으로 적어주세요."),
  keywords: z.array(z.string()),
}).refine(data => data.type === "WRITING_GUIDE" || (data.period && data.period.length > 0), {
  message: "시기를 입력해 주세요 (예: 고교 시절, 대학교 2학년)",
  path: ["period"],
}).refine(data => data.type === "WRITING_GUIDE" || data.keywords.length > 0, {
  message: "최소 1개의 키워드를 입력해 주세요.",
  path: ["keywords"],
})

interface StoryFormProps {
  initialData?: PersonalStory | null
  onSuccess: () => void
  onCancel: () => void
}

export function StoryForm({ initialData, onSuccess, onCancel }: StoryFormProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [keywordInput, setKeywordInput] = useState("")

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: initialData
      ? { ...initialData }
      : {
          type: "TURNING_POINT",
          period: "",
          content: "",
          keywords: [],
        },
  })

  const watchedType = form.watch("type")

  const onSubmit = async (values: z.infer<typeof formSchema>) => {
    try {
      setIsSubmitting(true)
      const url = initialData 
        ? `/api/personal-stories/${initialData.id}` 
        : "/api/personal-stories"
      const method = initialData ? "PUT" : "POST"

      const response = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(values),
      })

      if (!response.ok) throw new Error("서사 저장에 실패했습니다.")
      
      toast.success(initialData ? "수정 완료" : "등록 완료", {
        description: "인생 서사가 보관소에 안전하게 저장되었습니다.",
      })
      onSuccess()
    } catch (error) {
      toast.error("저장 실패", { description: "서사를 저장하는 중 문제가 발생했습니다." })
    } finally {
      setIsSubmitting(false)
    }
  }

  const addKeyword = () => {
    const trimmed = keywordInput.trim()
    if (!trimmed) return
    
    // 중복 제거
    const current = form.getValues("keywords")
    if (current.includes(trimmed)) {
      setKeywordInput("")
      return
    }

    form.setValue("keywords", [...current, trimmed])
    setKeywordInput("")
  }

  const removeKeyword = (kw: string) => {
    const current = form.getValues("keywords")
    form.setValue("keywords", current.filter(k => k !== kw))
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6 pt-4">
        <div className={watchedType === "WRITING_GUIDE" ? "" : "grid gap-6 md:grid-cols-2"}>
          <FormField
            control={form.control}
            name="type"
            render={({ field }) => (
              <FormItem>
                <FormLabel>유형</FormLabel>
                <Select onValueChange={field.onChange} defaultValue={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="소재 유형 선택" />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {Object.entries(STORY_TYPE_LABELS).map(([key, label]) => (
                      <SelectItem key={key} value={key}>{label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
          {watchedType !== "WRITING_GUIDE" && (
            <FormField
              control={form.control}
              name="period"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>시기</FormLabel>
                  <FormControl>
                    <Input placeholder="시기 (예: 고교 시절, 대학교 2학년)" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}
        </div>

        {watchedType === "WRITING_GUIDE" && (
          <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
            작성 가이드는 AI가 성장과정 문항을 쓸 때 따르는 지침입니다. 강조 역량, 성장 흐름, 문체 가이드, 금지 항목 등을 자유 형식으로 입력하세요.
          </p>
        )}

        <FormField
          control={form.control}
          name="content"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{watchedType === "WRITING_GUIDE" ? "작성 가이드라인" : "서사 본문"}</FormLabel>
              <FormControl>
                <Textarea
                  placeholder={
                    watchedType === "WRITING_GUIDE"
                      ? "[강조 역량]\n- 역량1\n- 역량2\n\n[성장 흐름]\n...\n\n[문체 가이드]\n...\n\n[금지]\n..."
                      : "당시의 상황, 내가 내린 결정, 그 결과와 느낀 점을 자유롭게 적어주세요."
                  }
                  className={watchedType === "WRITING_GUIDE" ? "min-h-[240px] resize-none font-mono text-xs" : "min-h-[150px] resize-none"}
                  {...field}
                />
              </FormControl>
              <FormDescription>
                {watchedType === "WRITING_GUIDE"
                  ? "이 내용은 성장과정 문항 생성 시 AI 프롬프트에 직접 주입됩니다."
                  : "AI가 이 내용을 바탕으로 당신의 가치관과 일하는 기준을 추출합니다."}
              </FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />

        {watchedType !== "WRITING_GUIDE" && (
          <FormField
            control={form.control}
            name="keywords"
            render={({ field }) => (
              <FormItem>
                <FormLabel>핵심 키워드</FormLabel>
                <div className="flex flex-wrap gap-2 py-2">
                  {field.value.map((kw, idx) => (
                    <Badge key={idx} variant="secondary" className="gap-1 pl-2">
                      {kw}
                      <button type="button" onClick={() => removeKeyword(kw)} className="rounded-full hover:bg-muted-foreground/20">
                        <X className="size-3" />
                      </button>
                    </Badge>
                  ))}
                </div>
                <div className="flex gap-2">
                  <FormControl>
                    <Input
                      placeholder="핵심 단어 입력 후 추가 (예: 도전, 협업, 책임감)"
                      value={keywordInput}
                      onChange={(e) => setKeywordInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          e.preventDefault()
                          addKeyword()
                        }
                      }}
                    />
                  </FormControl>
                  <Button type="button" variant="outline" onClick={addKeyword}>추가</Button>
                </div>
                <FormMessage />
              </FormItem>
            )}
          />
        )}

        <div className="flex justify-end gap-3 pt-4 border-t border-border/50">
          <Button type="button" variant="ghost" onClick={onCancel}>취소</Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting && <Loader2 className="mr-2 size-4 animate-spin" />}
            {initialData ? "수정하기" : "등록하기"}
          </Button>
        </div>
      </form>
    </Form>
  )
}
