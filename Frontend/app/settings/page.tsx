"use client";

import { AppSidebar } from "@/components/app-sidebar";
import { ProfileLibrary } from "@/components/profile-library";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";

export default function SettingsPage() {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <div className="flex h-screen flex-col">
          <header className="flex h-16 shrink-0 items-center gap-4 border-b border-border bg-background px-8">
            <div>
              <h1 className="text-xl font-black tracking-tight">
                지원 기본 정보
              </h1>
              <p className="text-sm text-muted-foreground">
                자격증, 어학, 수상, 교육 이력을 정리하고 필요한 필드만 바로 복사합니다.
              </p>
            </div>
          </header>

          <main className="flex-1 overflow-auto bg-muted/30 p-6">
            <div className="mx-auto w-full max-w-7xl">
              <ProfileLibrary />
            </div>
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  );
}
