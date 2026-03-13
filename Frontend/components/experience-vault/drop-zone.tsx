"use client"

import { useState, useCallback } from "react"
import { Upload, FileJson, FileText } from "lucide-react"
import axios from "axios"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"

export function DropZone({ onUploadSuccess }: { onUploadSuccess?: () => void }) {
  const [isDragging, setIsDragging] = useState(false)

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(true)
  }, [])

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
  }, [])

  const handleDrop = useCallback(async (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
    
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const file = e.dataTransfer.files[0]
      const formData = new FormData()
      formData.append("file", file)
      formData.append("title", file.name.replace(/\.[^/.]+$/, "")) // Remove extension

      try {
        const response = await axios.post("/api/experiences/upload", formData, {
          headers: {
            "Content-Type": "multipart/form-data",
          },
        })
        toast("업로드 성공", { description: "경험 파일이 성공적으로 보관소에 저장되었습니다." })
        if (onUploadSuccess) {
          onUploadSuccess()
        }
      } catch (error) {
        toast("업로드 실패", { description: "파일 서버 전송에 실패했습니다." })
      }
    }
  }, [])

  return (
    <div
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={`
        relative rounded-xl border-2 border-dashed transition-all duration-200
        ${isDragging 
          ? "border-primary bg-primary/5 scale-[1.01]" 
          : "border-border hover:border-primary/50 hover:bg-muted/50"
        }
      `}
    >
      <div className="flex flex-col items-center justify-center gap-4 p-10">
        <div className={`
          rounded-full p-4 transition-colors
          ${isDragging ? "bg-primary/10" : "bg-muted"}
        `}>
          <Upload className={`size-8 ${isDragging ? "text-primary" : "text-muted-foreground"}`} />
        </div>
        <div className="text-center">
          <p className="text-lg font-medium">
            {isDragging ? "여기에 파일을 놓으세요" : "경험 파일을 드래그 앤 드롭하세요"}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            .md 및 .json 파일 지원
          </p>
        </div>
        <div className="flex items-center gap-6 mt-2">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <FileText className="size-4" />
            <span>마크다운</span>
          </div>
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <FileJson className="size-4" />
            <span>JSON</span>
          </div>
        </div>
        <Button variant="outline" className="mt-2">
          <Upload className="size-4 mr-2" />
          파일 선택
        </Button>
      </div>
    </div>
  )
}
