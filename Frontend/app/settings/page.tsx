"use client"

import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { AppSidebar } from "@/components/app-sidebar"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Separator } from "@/components/ui/separator"
import { Bell, Globe, Key, User, Palette, Database } from "lucide-react"

export default function SettingsPage() {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <div className="flex h-screen flex-col">
          <header className="flex h-16 shrink-0 items-center gap-4 border-b border-border bg-background px-8">
            <div>
              <h1 className="text-xl font-semibold tracking-tight">설정</h1>
              <p className="text-sm text-muted-foreground">
                앱 환경설정 및 계정 관리
              </p>
            </div>
          </header>
          <main className="flex-1 overflow-auto bg-muted/30 p-6">
            <div className="max-w-3xl mx-auto space-y-6">
              {/* Profile Settings */}
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <User className="size-5" />
                    프로필 설정
                  </CardTitle>
                  <CardDescription>
                    계정 정보를 관리하세요
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="grid gap-2">
                    <Label htmlFor="name">이름</Label>
                    <Input id="name" defaultValue="김개발" />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="email">이메일</Label>
                    <Input id="email" type="email" defaultValue="dev.kim@example.com" />
                  </div>
                </CardContent>
              </Card>

              {/* API Settings */}
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <Key className="size-5" />
                    API 연결
                  </CardTitle>
                  <CardDescription>
                    외부 서비스 API 키를 관리하세요
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="grid gap-2">
                    <Label htmlFor="openai">OpenAI API Key</Label>
                    <Input id="openai" type="password" placeholder="sk-..." />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="anthropic">Anthropic API Key</Label>
                    <Input id="anthropic" type="password" placeholder="sk-ant-..." />
                  </div>
                </CardContent>
              </Card>

              {/* Notification Settings */}
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <Bell className="size-5" />
                    알림 설정
                  </CardTitle>
                  <CardDescription>
                    알림 수신 방법을 설정하세요
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="flex items-center justify-between">
                    <div className="space-y-0.5">
                      <Label>마감일 알림</Label>
                      <p className="text-sm text-muted-foreground">
                        채용공고 마감 3일 전 알림 받기
                      </p>
                    </div>
                    <Switch defaultChecked />
                  </div>
                  <Separator />
                  <div className="flex items-center justify-between">
                    <div className="space-y-0.5">
                      <Label>AI 분석 완료 알림</Label>
                      <p className="text-sm text-muted-foreground">
                        자기소개서 분석 완료 시 알림 받기
                      </p>
                    </div>
                    <Switch defaultChecked />
                  </div>
                </CardContent>
              </Card>

              {/* Language Settings */}
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <Globe className="size-5" />
                    언어 및 지역
                  </CardTitle>
                  <CardDescription>
                    앱 언어 및 지역 설정
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="flex items-center justify-between">
                    <div className="space-y-0.5">
                      <Label>인터페이스 언어</Label>
                      <p className="text-sm text-muted-foreground">
                        한국어로 설정됨
                      </p>
                    </div>
                    <Button variant="outline" size="sm">변경</Button>
                  </div>
                </CardContent>
              </Card>

              {/* Save Button */}
              <div className="flex justify-end">
                <Button>설정 저장</Button>
              </div>
            </div>
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
