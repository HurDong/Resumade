"use client"

import { AnimatePresence, motion } from "framer-motion"
import { ChevronDown, PencilLine, Trash2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { CoteWikiMarkdown } from "@/components/cote-wiki/cote-wiki-markdown"
import { getCoteWikiBadgeClassName, getCoteWikiUpdatedLabel, type CoteWikiNote } from "@/lib/cote-wiki"
import { cn } from "@/lib/utils"

export function CoteWikiCard({
  note,
  expanded,
  onToggle,
  onEdit,
  onDelete,
  className,
}: {
  note: CoteWikiNote
  expanded: boolean
  onToggle: () => void
  onEdit: () => void
  onDelete: () => void
  className?: string
}) {
  return (
    <Card
      className={cn(
        "overflow-hidden border-border/70 bg-background py-0 transition-colors transition-shadow duration-150 hover:border-primary/30 hover:shadow-md",
        expanded && "border-primary/30 shadow-sm",
        className,
      )}
    >
      <button
        type="button"
        onClick={onToggle}
        className="w-full text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset"
      >
        <CardHeader className="px-5 py-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <CardTitle className="text-2xl font-semibold tracking-tight">{note.title}</CardTitle>
            <div className="flex items-center gap-2.5">
              <Badge
                variant="outline"
                className={cn("rounded-full text-[11px] font-semibold", getCoteWikiBadgeClassName(note.category))}
              >
                {note.category}
              </Badge>
              <motion.div
                animate={{ rotate: expanded ? 180 : 0 }}
                transition={{ duration: 0.2, ease: "easeInOut" }}
                className="text-muted-foreground"
              >
                <ChevronDown className="size-4" />
              </motion.div>
            </div>
          </div>
        </CardHeader>
      </button>

      <AnimatePresence initial={false}>
        {expanded ? (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.22, ease: "easeInOut" }}
            className="overflow-hidden"
          >
            <CardContent className="border-t border-border/70 px-5 py-5">
              <CoteWikiMarkdown content={note.content} />
            </CardContent>
            <CardFooter className="flex flex-wrap items-center justify-between gap-3 border-t border-border/70 px-5 py-4">
              <p className="text-xs text-muted-foreground">
                최근 저장: <span className="font-medium text-foreground">{getCoteWikiUpdatedLabel(note)}</span>
              </p>
              <div className="flex items-center gap-2">
                <Button variant="outline" size="sm" onClick={onEdit}>
                  <PencilLine data-icon="inline-start" />
                  수정
                </Button>
                <Button variant="ghost" size="sm" onClick={onDelete}>
                  <Trash2 data-icon="inline-start" />
                  삭제
                </Button>
              </div>
            </CardFooter>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </Card>
  )
}
