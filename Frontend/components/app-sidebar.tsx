"use client"

import { usePathname } from "next/navigation"
import Link from "next/link"
import { LayoutDashboard, Archive, PenTool, Moon, Sun, Settings, LogOut, ChevronUp } from "lucide-react"
import { useTheme } from "next-themes"

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Separator } from "@/components/ui/separator"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"

const navigation = [
  {
    title: "대시보드",
    href: "/",
    icon: LayoutDashboard,
    description: "칸반 보드",
  },
  {
    title: "경험 보관소",
    href: "/vault",
    icon: Archive,
    description: "저장 및 관리",
  },
  {
    title: "작업공간",
    href: "/workspace",
    icon: PenTool,
    description: "작성 및 편집",
  },
]

export function AppSidebar() {
  const pathname = usePathname()
  const { theme, setTheme } = useTheme()

  return (
    <Sidebar className="border-r border-sidebar-border">
      <SidebarHeader className="px-6 py-6">
        <Link href="/" className="flex items-center gap-2">
          <span className="text-2xl font-bold tracking-tight text-primary">
            RESUMADE
          </span>
        </Link>
        <p className="mt-1 text-xs text-muted-foreground">
          AI 자기소개서 작성 어시스턴트
        </p>
      </SidebarHeader>

      <SidebarContent className="px-3">
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              {navigation.map((item) => {
                const isActive = pathname === item.href
                return (
                  <SidebarMenuItem key={item.href}>
                    <SidebarMenuButton
                      asChild
                      isActive={isActive}
                      className="h-12 px-4 transition-all duration-200"
                    >
                      <Link href={item.href}>
                        <item.icon className="size-5" />
                        <div className="flex flex-col items-start">
                          <span className="font-medium">{item.title}</span>
                          <span className="text-xs text-muted-foreground">
                            {item.description}
                          </span>
                        </div>
                      </Link>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter className="px-4 py-4">
        <Separator className="mb-3" />
        <Popover>
          <PopoverTrigger asChild>
            <button className="flex w-full items-center gap-3 px-2 py-2.5 rounded-lg hover:bg-sidebar-accent transition-all duration-200 cursor-pointer group">
              <Avatar className="size-9 transition-transform duration-200 group-hover:scale-105">
                <AvatarImage src="https://github.com/shadcn.png" alt="사용자 아바타" />
                <AvatarFallback className="bg-primary/10 text-primary text-sm font-medium">김</AvatarFallback>
              </Avatar>
              <div className="flex-1 min-w-0 text-left">
                <p className="text-sm font-medium truncate">김개발</p>
                <p className="text-xs text-muted-foreground truncate">dev.kim@example.com</p>
              </div>
              <ChevronUp className="size-4 text-muted-foreground group-hover:text-foreground transition-all duration-200 group-hover:-translate-y-0.5" />
            </button>
          </PopoverTrigger>
          <PopoverContent 
            side="top" 
            align="start" 
            className="w-56 p-2 animate-in fade-in-0 zoom-in-95 slide-in-from-bottom-2 duration-200"
            sideOffset={8}
          >
            <div className="space-y-1">
              <Link
                href="/settings"
                className="flex w-full items-center gap-3 px-3 py-2.5 rounded-md hover:bg-accent transition-all duration-150 text-sm group"
              >
                <Settings className="size-4 transition-transform duration-200 group-hover:rotate-45" />
                <span>설정</span>
              </Link>
              <button
                onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
                className="flex w-full items-center gap-3 px-3 py-2.5 rounded-md hover:bg-accent transition-all duration-150 text-sm group"
              >
                <div className="relative size-4">
                  <Sun className="size-4 rotate-0 scale-100 transition-all duration-300 dark:-rotate-90 dark:scale-0 absolute" />
                  <Moon className="size-4 rotate-90 scale-0 transition-all duration-300 dark:rotate-0 dark:scale-100 absolute" />
                </div>
                <span>테마 전환</span>
              </button>
              <Separator className="my-1" />
              <button
                className="flex w-full items-center gap-3 px-3 py-2.5 rounded-md hover:bg-destructive/10 hover:text-destructive transition-all duration-150 text-sm group"
              >
                <LogOut className="size-4 transition-transform duration-200 group-hover:translate-x-0.5" />
                <span>로그아웃</span>
              </button>
            </div>
          </PopoverContent>
        </Popover>
      </SidebarFooter>
    </Sidebar>
  )
}
