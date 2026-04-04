"use client"

import { useState } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { ChevronDown, CheckCircle, Pencil, Trash2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import { type TechTemplate } from "./mock-data"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"

const CATEGORY_COLORS: Record<string, string> = {
  "문법·유틸": "bg-violet-500/10 text-violet-600 dark:text-violet-400",
  "그래프 탐색": "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400",
  "최단 경로": "bg-blue-500/10 text-blue-600 dark:text-blue-400",
  "동적 프로그래밍": "bg-amber-500/10 text-amber-600 dark:text-amber-400",
  "자료구조": "bg-rose-500/10 text-rose-600 dark:text-rose-400",
  "탐색·정렬": "bg-cyan-500/10 text-cyan-600 dark:text-cyan-400",
  "위상 정렬": "bg-orange-500/10 text-orange-600 dark:text-orange-400",
}

export function TemplateCard({
  template,
  onEdit,
  onDelete,
}: {
  template: TechTemplate
  onEdit: () => void
  onDelete: () => void
}) {
  const [expanded, setExpanded] = useState(false)
  const categoryColor = CATEGORY_COLORS[template.category] ?? "bg-muted text-muted-foreground"

  return (
    <div className="group rounded-xl border border-border bg-background overflow-hidden transition-colors hover:border-primary/30">
      <div className="p-5 space-y-3">
        {/* 헤더 */}
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h3 className="text-base font-bold tracking-tight">{template.title}</h3>
              <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium shrink-0", categoryColor)}>
                {template.category}
              </span>
            </div>
            <p className="text-xs text-muted-foreground mt-0.5 leading-snug">{template.summary}</p>
          </div>

          {/* 호버 액션 */}
          <div className="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
            <Button variant="ghost" size="icon" className="size-7 text-muted-foreground hover:text-foreground" onClick={onEdit}>
              <Pencil className="size-3.5" />
            </Button>
            <Button variant="ghost" size="icon" className="size-7 text-muted-foreground hover:text-destructive hover:bg-destructive/10" onClick={onDelete}>
              <Trash2 className="size-3.5" />
            </Button>
          </div>
        </div>

        {/* 핵심 포인트 */}
        <ul className="space-y-1.5">
          {template.conditions.map((c, idx) => (
            <li key={idx} className="flex items-start gap-2 text-sm text-foreground/80">
              <CheckCircle className="size-3.5 mt-0.5 shrink-0 text-primary/60" />
              <span className="leading-snug">{c}</span>
            </li>
          ))}
        </ul>

        {/* 태그 */}
        <div className="flex flex-wrap gap-1.5 pt-1">
          {template.tags.map((tag) => (
            <Badge key={tag} variant="secondary" className="text-xs font-normal">
              #{tag}
            </Badge>
          ))}
        </div>

        {/* 내용 토글 */}
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="flex w-full items-center justify-between rounded-md bg-muted px-3 py-2 text-xs font-medium text-muted-foreground hover:text-foreground transition-colors"
        >
          <span>내용 {expanded ? "접기" : "펼치기"}</span>
          <motion.div animate={{ rotate: expanded ? 180 : 0 }} transition={{ duration: 0.2 }}>
            <ChevronDown className="size-3.5" />
          </motion.div>
        </button>
      </div>

      {/* 마크다운 내용 */}
      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.25, ease: "easeInOut" }}
            className="overflow-hidden"
          >
            <div className="border-t border-border overflow-x-auto px-5 py-4">
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                  table: ({ children }) => (
                    <table className="w-full text-xs border-collapse my-1">{children}</table>
                  ),
                  thead: ({ children }) => (
                    <thead className="bg-muted">{children}</thead>
                  ),
                  tbody: ({ children }) => <tbody>{children}</tbody>,
                  tr: ({ children }) => (
                    <tr className="border-b border-border even:bg-muted/30">{children}</tr>
                  ),
                  th: ({ children }) => (
                    <th className="border border-border px-3 py-2 text-left text-xs font-semibold text-foreground whitespace-nowrap">{children}</th>
                  ),
                  td: ({ children }) => (
                    <td className="border border-border px-3 py-2 text-xs text-foreground/80 align-top leading-relaxed">{children}</td>
                  ),
                  pre: ({ children }) => (
                    <pre className="overflow-x-auto rounded-md bg-zinc-900 px-4 py-3 my-2 leading-relaxed">{children}</pre>
                  ),
                  code: ({ children, className }) => {
                    if (className) {
                      return <code className={cn("font-mono text-xs text-zinc-100", className)}>{children}</code>
                    }
                    return <code className="rounded bg-muted px-1 py-0.5 font-mono text-xs text-foreground">{children}</code>
                  },
                  p: ({ children }) => <p className="mb-2 text-sm text-foreground/80 leading-relaxed last:mb-0">{children}</p>,
                  strong: ({ children }) => <strong className="font-semibold text-foreground">{children}</strong>,
                }}
              >
                {template.template}
              </ReactMarkdown>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
