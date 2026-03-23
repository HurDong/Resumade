"use client";

import { Archive, CopyPlus, Settings2 } from "lucide-react";
import { AppSidebar } from "@/components/app-sidebar";
import { ApplicationInfoLibrary } from "@/components/application-info/application-info-library";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";

export default function SettingsPage() {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <div className="flex h-screen flex-col bg-muted/20">
          <header className="shrink-0 border-b border-border bg-background/95 px-8 py-7 backdrop-blur supports-[backdrop-filter]:bg-background/80">
            <div className="mx-auto flex w-full max-w-7xl flex-wrap items-start justify-between gap-6">
              <div className="space-y-3">
                <div className="flex items-center gap-2 text-primary">
                  <Settings2 className="size-4" />
                  <span className="text-xs font-black uppercase tracking-[0.28em]">
                    Profile Vault
                  </span>
                </div>
                <div>
                  <h1 className="text-3xl font-black tracking-tight text-foreground">
                    지원 정보 라이브러리
                  </h1>
                  <p className="mt-2 max-w-3xl text-sm leading-7 text-muted-foreground">
                    실제 공채 지원서에 반복해서 넣는 등록번호, 기간, 활동 설명을 카드처럼 저장합니다.
                    저장한 항목은 작업실과 대시보드에서 바로 열어 한 줄 요약 또는 상세 포맷으로 복사할 수
                    있습니다.
                  </p>
                </div>
              </div>

              <div className="grid min-w-[280px] gap-3 sm:grid-cols-2">
                <div className="rounded-[26px] border border-primary/15 bg-primary/[0.06] p-4">
                  <div className="flex items-center gap-2 text-primary">
                    <Archive className="size-4" />
                    <p className="text-[11px] font-black uppercase tracking-[0.24em]">
                      저장
                    </p>
                  </div>
                  <p className="mt-2 text-sm leading-6 text-foreground/80">
                    자격증, 수상, 활동, 외국어 정보를 분류해 누적 관리합니다.
                  </p>
                </div>
                <div className="rounded-[26px] border border-border/70 bg-background p-4">
                  <div className="flex items-center gap-2 text-primary">
                    <CopyPlus className="size-4" />
                    <p className="text-[11px] font-black uppercase tracking-[0.24em]">
                      복사
                    </p>
                  </div>
                  <p className="mt-2 text-sm leading-6 text-foreground/80">
                    요약 복사와 상세 복사를 분리해 지원 사이트 양식에 맞춰 빠르게 붙여넣습니다.
                  </p>
                </div>
              </div>
            </div>
          </header>

          <main className="flex-1 overflow-auto px-6 py-6">
            <div className="mx-auto w-full max-w-7xl">
              <ApplicationInfoLibrary />
            </div>
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  );
}
