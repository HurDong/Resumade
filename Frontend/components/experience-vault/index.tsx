"use client"

import { useState, useEffect } from "react"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { Button } from "@/components/ui/button"
import { Sparkles, Info } from "lucide-react"
import axios from "axios"
import { type Experience } from "@/lib/mock-data"
import { DropZone } from "./drop-zone"
import { ExperienceCard } from "./experience-card"
import { ExperienceDetailModal } from "./experience-detail-modal"

export function ExperienceVault() {
  const [selectedExperience, setSelectedExperience] = useState<Experience | null>(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [experiences, setExperiences] = useState<Experience[]>([])

  const fetchExperiences = async () => {
    try {
      const { data } = await axios.get("/api/experiences")
      setExperiences(data)
    } catch (error) {
      console.error("Failed to fetch experiences:", error)
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

  return (
    <TooltipProvider>
      <div className="space-y-8 max-w-6xl mx-auto">
        <DropZone onUploadSuccess={fetchExperiences} />
        
        <div>
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="text-lg font-semibold">내 경험</h2>
              <p className="text-sm text-muted-foreground">
                {experiences.length}개의 경험 저장됨
              </p>
            </div>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button variant="outline" size="sm" className="gap-2">
                  <Sparkles className="size-4" />
                  자동 분류
                  <Info className="size-3 text-muted-foreground" />
                </Button>
              </TooltipTrigger>
              <TooltipContent side="bottom" className="max-w-xs">
                <p className="text-sm">
                  업로드된 통짜 .md 파일을 AI가 분석하여 개별 경험 카드로 자동 분리합니다.
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
              />
            ))}
          </div>
        </div>

        <ExperienceDetailModal
          experience={selectedExperience}
          isOpen={isModalOpen}
          onClose={handleCloseModal}
        />
      </div>
    </TooltipProvider>
  )
}
