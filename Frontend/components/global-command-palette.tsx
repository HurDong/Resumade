"use client"

import * as React from "react"
import { usePathname, useRouter } from "next/navigation"
import {
  Archive,
  ChevronLeft,
  FilePenLine,
  LayoutDashboard,
  Library,
  Loader2,
  PenTool,
  Search,
  Settings2,
} from "lucide-react"

import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
  CommandShortcut,
} from "@/components/ui/command"
import { readLastWorkspaceContext } from "@/lib/workspace/last-workspace-context"

type PaletteAction = {
  id: string
  label: string
  description: string
  href: string
  keywords: string[]
  icon: React.ComponentType<{ className?: string }>
}

type PaletteMode = "root" | "workspace" | "edit"

type PaletteApplication = {
  id: string
  company: string
  position: string
  questionId: number | null
}

const ROOT_ACTIONS: PaletteAction[] = [
  {
    id: "dashboard",
    label: "대시보드",
    description: "채용 파이프라인 보드로 이동",
    href: "/",
    keywords: ["dashboard", "kanban", "home", "지원현황"],
    icon: LayoutDashboard,
  },
  {
    id: "vault",
    label: "경험 보관소",
    description: "경험 업로드와 검색 화면으로 이동",
    href: "/vault",
    keywords: ["vault", "experience", "rag", "경험"],
    icon: Archive,
  },
  {
    id: "settings",
    label: "설정",
    description: "프로필과 환경 설정으로 이동",
    href: "/settings",
    keywords: ["settings", "profile", "설정", "프로필"],
    icon: Settings2,
  },
  {
    id: "library",
    label: "스펙 라이브러리",
    description: "프로필 라이브러리로 이동",
    href: "/settings",
    keywords: ["library", "spec", "portfolio", "스펙"],
    icon: Library,
  },
]

function resolvePrimaryQuestionId(questions: unknown): number | null {
  if (!Array.isArray(questions)) return null

  const normalized = questions
    .map((question) => {
      if (!question || typeof question !== "object") return null

      const record = question as { id?: unknown; isCompleted?: unknown }
      const rawId = typeof record.id === "number" ? record.id : Number(record.id)

      if (!Number.isFinite(rawId)) return null

      return {
        id: rawId,
        isCompleted: Boolean(record.isCompleted),
      }
    })
    .filter((question): question is { id: number; isCompleted: boolean } => question != null)

  if (normalized.length === 0) return null

  return normalized.find((question) => !question.isCompleted)?.id ?? normalized[0].id
}

async function fetchPaletteApplications(): Promise<PaletteApplication[]> {
  const response = await fetch("/api/applications/workspace-selector")
  if (!response.ok) {
    throw new Error(`Failed to load applications: ${response.status}`)
  }

  const data = await response.json()
  if (!Array.isArray(data)) return []

  return data.map((app: any) => ({
    id: String(app.id),
    company: app.companyName || "미지정 공고",
    position: app.position || "직무 미지정",
    questionId: resolvePrimaryQuestionId(app.questions),
  }))
}

export function GlobalCommandPalette() {
  const router = useRouter()
  const pathname = usePathname()
  const [open, setOpen] = React.useState(false)
  const [mode, setMode] = React.useState<PaletteMode>("root")
  const [selectedValue, setSelectedValue] = React.useState("")
  const [lastWorkspaceContext, setLastWorkspaceContext] = React.useState(() =>
    readLastWorkspaceContext(),
  )
  const [applications, setApplications] = React.useState<PaletteApplication[]>([])
  const [isLoadingApplications, setIsLoadingApplications] = React.useState(false)

  React.useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.isComposing) return

      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault()
        setOpen((current) => !current)
      }
    }

    window.addEventListener("keydown", onKeyDown)
    return () => window.removeEventListener("keydown", onKeyDown)
  }, [])

  React.useEffect(() => {
    setOpen(false)
  }, [pathname])

  React.useEffect(() => {
    if (!open) {
      setMode("root")
      return
    }

    setLastWorkspaceContext(readLastWorkspaceContext())

    let cancelled = false
    setIsLoadingApplications(true)

    fetchPaletteApplications()
      .then((items) => {
        if (!cancelled) {
          setApplications(items)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setApplications([])
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoadingApplications(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [open])

  React.useEffect(() => {
    if (!open || mode === "root") return

    const handleModeEscape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return

      event.preventDefault()
      event.stopPropagation()
      setMode("root")
    }

    window.addEventListener("keydown", handleModeEscape, true)
    return () => window.removeEventListener("keydown", handleModeEscape, true)
  }, [mode, open])

  const handleSelect = React.useCallback(
    (href: string) => {
      setOpen(false)
      router.push(href)
    },
    [router],
  )

  const currentWorkspaceHref = lastWorkspaceContext
    ? `/workspace/${lastWorkspaceContext.applicationId}${
        lastWorkspaceContext.questionDbId != null
          ? `?questionId=${lastWorkspaceContext.questionDbId}`
          : ""
      }`
    : "/workspace"

  const currentEditHref =
    lastWorkspaceContext?.questionDbId != null
      ? `/workspace/edit/${lastWorkspaceContext.questionDbId}`
      : "/workspace"

  const paletteMeta = {
    root: {
      title: "빠른 이동",
      description: "이동할 화면이나 작업 흐름을 선택합니다.",
      placeholder: "페이지나 기능을 검색하세요...",
      empty: "검색 결과가 없습니다.",
    },
    workspace: {
      title: "작업공간 선택",
      description: "이어서 작성할 공고를 고르면 해당 작업공간으로 이동합니다.",
      placeholder: "회사명이나 직무로 공고를 찾으세요...",
      empty: "선택할 공고가 없습니다.",
    },
    edit: {
      title: "다림질실 선택",
      description: "진행 중인 공고를 고르면 해당 문항의 다림질실로 이동합니다.",
      placeholder: "회사명이나 직무로 공고를 찾으세요...",
      empty: "이동할 다림질실 대상이 없습니다.",
    },
  }[mode]

  const selectionItems = React.useMemo(() => {
    return applications.map((application) => ({
      ...application,
      commandValue: `${mode}-application-${application.id}`,
      selectedValue: `${mode}-application-${application.id} ${application.company} ${application.position} ${mode}`,
      workspaceHref: `/workspace/${application.id}${
        application.questionId != null ? `?questionId=${application.questionId}` : ""
      }`,
      editHref:
        application.questionId != null
          ? `/workspace/edit/${application.questionId}`
          : `/workspace/${application.id}`,
    }))
  }, [applications])

  const defaultSelectionValue = React.useMemo(() => {
    if (mode === "workspace" || mode === "edit") {
      return selectionItems[0]?.selectedValue ?? "palette-back 뒤로 back root"
    }

    return "palette-last-workspace 마지막 작업 작업공간 recent workspace"
  }, [mode, selectionItems])

  React.useEffect(() => {
    if (!open) return
    setSelectedValue(defaultSelectionValue)
  }, [defaultSelectionValue, open])

  return (
    <CommandDialog
      open={open}
      onOpenChange={setOpen}
      title={paletteMeta.title}
      description={paletteMeta.description}
      className="sm:max-w-xl"
      commandProps={{
        value: selectedValue,
        onValueChange: setSelectedValue,
      }}
    >
      <CommandInput key={mode} placeholder={paletteMeta.placeholder} />
      <CommandList>
        {mode === "root" ? (
          <>
            <CommandEmpty>{paletteMeta.empty}</CommandEmpty>
            <CommandGroup heading="최근 작업">
              <CommandItem
                value="palette-last-workspace 마지막 작업 작업공간 recent workspace"
                onSelect={() => handleSelect(currentWorkspaceHref)}
              >
                <PenTool className="size-4" />
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="truncate font-medium">마지막 작업 작업공간</span>
                  <span className="truncate text-xs text-muted-foreground">
                    마지막으로 열었던 공고와 문항의 작업공간으로 이동합니다
                  </span>
                </div>
                <CommandShortcut>최근</CommandShortcut>
              </CommandItem>
              <CommandItem
                value="palette-last-edit 마지막 작업 다림질실 recent final editor"
                onSelect={() => handleSelect(currentEditHref)}
                disabled={lastWorkspaceContext?.questionDbId == null}
              >
                <FilePenLine className="size-4" />
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="truncate font-medium">마지막 작업 다림질실</span>
                  <span className="truncate text-xs text-muted-foreground">
                    마지막으로 열었던 문항의 다림질실로 바로 이동합니다
                  </span>
                </div>
                <CommandShortcut>
                  {lastWorkspaceContext?.questionDbId != null ? "최근" : "없음"}
                </CommandShortcut>
              </CommandItem>
            </CommandGroup>
            <CommandSeparator />
            <CommandGroup heading="작성 흐름">
              <CommandItem
                value="palette-open-workspace 작업공간 select workspace application"
                onSelect={() => setMode("workspace")}
              >
                <PenTool className="size-4" />
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="truncate font-medium">작업공간</span>
                  <span className="truncate text-xs text-muted-foreground">
                    공고를 고른 뒤 해당 작업공간으로 이동합니다
                  </span>
                </div>
                <CommandShortcut>Enter</CommandShortcut>
              </CommandItem>
              <CommandItem
                value="palette-open-edit 다림질실 select final editor application"
                onSelect={() => setMode("edit")}
              >
                <FilePenLine className="size-4" />
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="truncate font-medium">다림질실</span>
                  <span className="truncate text-xs text-muted-foreground">
                    공고를 고른 뒤 진행 문항의 다림질실로 이동합니다
                  </span>
                </div>
                <CommandShortcut>Enter</CommandShortcut>
              </CommandItem>
            </CommandGroup>
            <CommandSeparator />
            <CommandGroup heading="페이지">
              {ROOT_ACTIONS.map((action) => {
                const Icon = action.icon
                const isCurrent = pathname === action.href

                return (
                  <CommandItem
                    key={action.id}
                    value={`palette-page-${action.id} ${[action.label, action.description, ...action.keywords].join(" ")}`}
                    onSelect={() => handleSelect(action.href)}
                    disabled={isCurrent}
                  >
                    <Icon className="size-4" />
                    <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="truncate font-medium">{action.label}</span>
                      <span className="truncate text-xs text-muted-foreground">
                        {action.description}
                      </span>
                    </div>
                    <CommandShortcut>{isCurrent ? "현재" : "이동"}</CommandShortcut>
                  </CommandItem>
                )
              })}
            </CommandGroup>
          </>
        ) : (
          <>
            <CommandEmpty>
              {isLoadingApplications ? "공고를 불러오는 중입니다..." : paletteMeta.empty}
            </CommandEmpty>
            <CommandGroup heading="탐색">
              <CommandItem value="palette-back 뒤로 back root" onSelect={() => setMode("root")}>
                <ChevronLeft className="size-4" />
                <span>뒤로</span>
                <CommandShortcut>Esc</CommandShortcut>
              </CommandItem>
            </CommandGroup>
            <CommandSeparator />
            <CommandGroup heading={mode === "workspace" ? "작업할 공고" : "다림질할 공고"}>
              {isLoadingApplications ? (
                <CommandItem disabled>
                  <Loader2 className="size-4 animate-spin" />
                  <span>공고 목록을 불러오는 중입니다</span>
                </CommandItem>
              ) : (
                selectionItems.map((application) => (
                  <CommandItem
                    key={`${mode}-${application.id}`}
                    value={`${application.commandValue} ${application.company} ${application.position} ${mode}`}
                    onSelect={() =>
                      handleSelect(
                        mode === "workspace"
                          ? application.workspaceHref
                          : application.editHref,
                      )
                    }
                  >
                    {mode === "workspace" ? (
                      <PenTool className="size-4" />
                    ) : (
                      <FilePenLine className="size-4" />
                    )}
                    <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="truncate font-medium">{application.company}</span>
                      <span className="truncate text-xs text-muted-foreground">
                        {application.position}
                      </span>
                    </div>
                    <CommandShortcut>
                      {application.questionId != null ? "진행중" : "문항 없음"}
                    </CommandShortcut>
                  </CommandItem>
                ))
              )}
            </CommandGroup>
          </>
        )}
        <CommandSeparator />
        <CommandGroup heading="사용 방법">
          <CommandItem disabled>
            <Search className="size-4" />
            <span>언제든 빠른 이동을 열 수 있습니다</span>
            <CommandShortcut>Ctrl/⌘ K</CommandShortcut>
          </CommandItem>
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  )
}
