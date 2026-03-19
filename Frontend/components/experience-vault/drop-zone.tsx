"use client"

import { useCallback, useRef, useState } from "react"
import { FileJson, FileText, Upload } from "lucide-react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"

const ALLOWED_EXTENSIONS = [".md", ".json"] as const

function hasSupportedExtension(fileName: string) {
  const lowerCaseFileName = fileName.toLowerCase()
  return ALLOWED_EXTENSIONS.some((extension) => lowerCaseFileName.endsWith(extension))
}

export function DropZone({ onUploadSuccess }: { onUploadSuccess?: () => void }) {
  const [isDragging, setIsDragging] = useState(false)
  const [isInvalid, setIsInvalid] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const uploadFile = useCallback(
    async (file: File) => {
      if (!hasSupportedExtension(file.name)) {
        toast.error("지원하지 않는 파일 형식입니다", {
          description: ".md 또는 .json 파일만 업로드할 수 있습니다.",
        })
        return
      }

      const formData = new FormData()
      formData.append("file", file)
      formData.append("title", file.name.replace(/\.[^/.]+$/, ""))

      try {
        setIsUploading(true)

        const response = await fetch("/api/experiences/upload", {
          method: "POST",
          body: formData,
        })

        if (!response.ok) {
          const message = (await response.text()).trim()
          throw new Error(message || "경험 데이터를 업로드하는 데 실패했습니다.")
        }

        toast.success("업로드 완료", {
          description: "경험 파일이 보관소에 저장되었습니다.",
        })
        onUploadSuccess?.()
      } catch (error) {
        console.error("Failed to upload experience:", error)
        toast.error("업로드 실패", {
          description: error instanceof Error ? error.message : "파일을 업로드할 수 없습니다.",
        })
      } finally {
        setIsUploading(false)
      }
    },
    [onUploadSuccess],
  )

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(true)
    
    // Check if files being dragged are supported
    const items = Array.from(e.dataTransfer.items)
    const hasInvalidFile = items.some(item => {
      if (item.kind === 'file') {
        const file = item.getAsFile()
        if (file && !hasSupportedExtension(file.name)) return true
        // Some browsers don't give the file name during dragover for security
        // But we can check item.type or just assume it's okay unless it's obviously wrong
      }
      return false
    })
    
    // items.some doesn't work well for file names during dragover in some browsers
    // so we'll just check if any item is not a file or has a type we don't like if available
    setIsInvalid(hasInvalidFile)
  }, [])

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
    setIsInvalid(false)
  }, [])

  const handleDrop = useCallback(
    async (e: React.DragEvent) => {
      e.preventDefault()
      setIsDragging(false)

      const file = e.dataTransfer.files?.[0]
      if (!file) {
        return
      }

      if (!hasSupportedExtension(file.name)) {
        toast.error("지원하지 않는 파일 형식", {
          description: `${file.name}은(는) 지원되지 않습니다. .md 또는 .json 파일을 사용해 주세요.`,
        })
        return
      }

      await uploadFile(file)
    },
    [uploadFile],
  )

  const handleFileChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) {
        return
      }

      await uploadFile(file)
      e.target.value = ""
    },
    [uploadFile],
  )

  return (
    <div
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={`
        relative rounded-xl border-2 border-dashed transition-all duration-200
        ${
          isDragging
            ? isInvalid 
              ? "border-destructive bg-destructive/5 scale-[1.01]"
              : "border-primary bg-primary/5 scale-[1.01]"
            : "border-border hover:border-primary/50 hover:bg-muted/50"
        }
      `}
    >
      <input
        ref={inputRef}
        type="file"
        accept=".md,.json"
        className="hidden"
        onChange={handleFileChange}
      />
      <div className="flex flex-col items-center justify-center gap-4 p-10">
        <div
          className={`
            rounded-full p-4 transition-colors
            ${isDragging ? "bg-primary/10" : "bg-muted"}
          `}
        >
          <Upload className={`size-8 ${isDragging ? "text-primary" : "text-muted-foreground"}`} />
        </div>
        <div className="text-center">
          <p className="text-lg font-medium">
            {isInvalid 
              ? "지원하지 않는 파일 형식입니다" 
              : isDragging 
                ? "파일을 여기에 놓으세요" 
                : "경험 파일을 여기에 드래그 앤 드랍하세요"}
          </p>
          <p className="mt-1 text-sm text-muted-foreground font-mono">
            {isInvalid ? "지원 형식: .md, .json만 가능합니다" : "지원 포맷: .md, .json"}
          </p>
        </div>
        <div className="mt-2 flex items-center gap-6">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <FileText className="size-4" />
            <span>마크다운</span>
          </div>
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <FileJson className="size-4" />
            <span>JSON</span>
          </div>
        </div>
        <Button
          type="button"
          variant="outline"
          className="mt-2"
          disabled={isUploading}
          onClick={() => inputRef.current?.click()}
        >
          <Upload className="mr-2 size-4" />
          {isUploading ? "업로드 중..." : "파일 선택"}
        </Button>
      </div>
    </div>
  )
}