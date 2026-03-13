"use client"

import { useRouter } from "next/navigation"
import { Card, CardContent } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Button } from "@/components/ui/button"
import { PenLine } from "lucide-react"
import { type Application } from "@/lib/mock-data"

export function QuestionsPanel({ application }: { application: Application }) {
  const router = useRouter()

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        {application.company} {application.position} 자소서 문항
      </p>
      
      <div className="space-y-3">
        {application.questions.map((question, idx) => {
          const progress = Math.round((question.currentLength / question.maxLength) * 100)
          return (
            <Card key={question.id} className="overflow-hidden">
              <CardContent className="p-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="size-6 shrink-0 rounded-full bg-primary/10 text-primary flex items-center justify-center text-xs font-bold">
                        {idx + 1}
                      </span>
                      <h4 className="font-medium text-sm truncate">{question.title}</h4>
                    </div>
                    <div className="space-y-1.5">
                      <Progress value={progress} className="h-1.5" />
                      <p className="text-xs text-muted-foreground">
                        {question.currentLength} / {question.maxLength}자 ({progress}%)
                      </p>
                    </div>
                  </div>
                  <Button 
                    size="sm" 
                    className="shrink-0 gap-1.5"
                    onClick={() => router.push('/workspace')}
                  >
                    <PenLine className="size-3.5" />
                    작업실로 이동
                  </Button>
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>
    </div>
  )
}
