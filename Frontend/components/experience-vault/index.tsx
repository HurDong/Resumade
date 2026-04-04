"use client"

import { useEffect, useState } from "react"
import { Info, Sparkles } from "lucide-react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { mockExperiences, type Experience } from "@/lib/mock-data"
import { normalizeExperienceForUi } from "@/lib/experience/normalize"
import { isFrontendOnlyMode } from "@/lib/frontend-only"
import { DropZone } from "./drop-zone"
import { ExperienceCard } from "./experience-card"
import { ExperienceDetailModal } from "./experience-detail-modal"
import { UploadGuidePanel } from "./upload-guide-panel"

export function ExperienceVault() {
  const [selectedExperience, setSelectedExperience] = useState<Experience | null>(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [experiences, setExperiences] = useState<Experience[]>([])
  const [isClassifying, setIsClassifying] = useState(false)

  const fetchExperiences = async () => {
    if (isFrontendOnlyMode()) {
      setExperiences(mockExperiences.map((experience) => normalizeExperienceForUi(experience)))
      return
    }

    try {
      const response = await fetch("/api/experiences", {
        cache: "no-store",
      })

      if (!response.ok) {
        throw new Error("경험 목록을 불러오는 데 실패했습니다.")
      }

      const data = (await response.json()) as Experience[]
      setExperiences(
        data.map((experience) => normalizeExperienceForUi(experience))
      )
    } catch (error) {
      console.error("Failed to fetch experiences:", error)
      setExperiences(
        mockExperiences.map((experience) => normalizeExperienceForUi(experience))
      )
    }
  }

  const handleAutoClassify = async () => {
    try {
      setIsClassifying(true)
      const response = await fetch("/api/experiences/reclassify-all", {
        method: "POST",
      })

      if (!response.ok) {
        throw new Error("자동 분류에 실패했습니다.")
      }

      await fetchExperiences()
      toast.success("자동 분류 완료", {
        description: "모든 경험 데이터가 AI에 의해 재분류되었습니다.",
      })
    } catch (error) {
      console.error("Auto classify failed:", error)
      toast.error("자동 분류 실패", {
        description: error instanceof Error ? error.message : "오류가 발생했습니다.",
      })
    } finally {
      setIsClassifying(false)
    }
  }

  useEffect(() => {
    fetchExperiences()
  }, [])

  const handleCardClick = (experience: Experience) => {
    setSelectedExperience(experience)
    setIsModalOpen(true)
  }

  const handleCloseModal = () => {
    setIsModalOpen(false)
    setSelectedExperience(null)
  }

  const handleExperienceUpdated = (updatedExperience: Experience) => {
    const normalizedExperience = normalizeExperienceForUi(updatedExperience)

    setExperiences((prev) =>
      prev.map((experience) =>
        experience.id === normalizedExperience.id ? normalizedExperience : experience
      )
    )
    setSelectedExperience((prev) =>
      prev?.id === normalizedExperience.id ? normalizedExperience : prev
    )
  }

  return (
    <TooltipProvider>
      <div className="mx-auto max-w-6xl space-y-8">
        <UploadGuidePanel />
        <DropZone onUploadSuccess={fetchExperiences} />

        <div>
          <div className="mb-6 flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold">저장된 경험들</h2>
              <p className="text-sm text-muted-foreground">보관소에 {experiences.length}개의 항목이 있습니다</p>
            </div>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button 
                  variant="outline" 
                  size="sm" 
                  className="gap-2"
                  onClick={handleAutoClassify}
                  disabled={isClassifying || experiences.length === 0}
                >
                  <Sparkles className={`size-4 ${isClassifying ? "animate-spin" : ""}`} />
                  {isClassifying ? "분류 중..." : "자동 분류"}
                  <Info className="size-3 text-muted-foreground" />
                </Button>
              </TooltipTrigger>
              <TooltipContent side="bottom" className="max-w-xs">
                <p className="text-sm">
                  업로드된 `.md` 또는 `.json` 파일은 AI가 분석하여 자동으로 경험 카드로 분류합니다.
                </p>
              </TooltipContent>
            </Tooltip>
          </div>

          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {experiences.map((experience) => (
              <ExperienceCard
                key={experience.id}
                experience={experience}
                onClick={() => handleCardClick(experience)}
                onDelete={fetchExperiences}
              />
            ))}
          </div>
        </div>

        <ExperienceDetailModal
          experience={selectedExperience}
          isOpen={isModalOpen}
          onClose={handleCloseModal}
          onExperienceUpdated={handleExperienceUpdated}
        />
      </div>
    </TooltipProvider>
  )
}
