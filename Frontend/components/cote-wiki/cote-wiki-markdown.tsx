"use client"

import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import { cn } from "@/lib/utils"

export function CoteWikiMarkdown({
  content,
  className,
}: {
  content: string
  className?: string
}) {
  return (
    <div className={cn("min-w-0", className)}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children }) => (
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">{children}</h1>
          ),
          h2: ({ children }) => (
            <h2 className="mt-8 text-lg font-semibold tracking-tight text-foreground first:mt-0">
              {children}
            </h2>
          ),
          h3: ({ children }) => (
            <h3 className="mt-6 text-sm font-semibold uppercase tracking-[0.16em] text-muted-foreground">
              {children}
            </h3>
          ),
          p: ({ children }) => (
            <p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-foreground/88 first:mt-0">
              {children}
            </p>
          ),
          ul: ({ children }) => <ul className="mt-3 flex list-disc flex-col gap-2 pl-5 text-sm">{children}</ul>,
          ol: ({ children }) => (
            <ol className="mt-3 flex list-decimal flex-col gap-2 pl-5 text-sm">{children}</ol>
          ),
          li: ({ children }) => <li className="leading-7 text-foreground/88">{children}</li>,
          pre: ({ children }) => (
            <pre className="mt-4 overflow-x-auto rounded-2xl border border-border/70 bg-zinc-950 px-4 py-4 text-sm leading-6 text-zinc-100 shadow-sm">
              {children}
            </pre>
          ),
          code: ({ children, className }) => {
            if (className) {
              return <code className={cn("font-mono text-[13px]", className)}>{children}</code>
            }

            return (
              <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-[13px] text-foreground">
                {children}
              </code>
            )
          },
          blockquote: ({ children }) => (
            <blockquote className="mt-4 border-l-2 border-primary/30 pl-4 text-sm text-muted-foreground">
              {children}
            </blockquote>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}
