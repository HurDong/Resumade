import { type Experience } from "@/lib/mock-data"

const FILE_CITATION_PATTERN = /?fileciteturn\d+file\d+(?:-L\d+)?/gu
const BRACKET_CITATION_PATTERN = /【\d+:\d+†[^】]+】/gu
const ZERO_WIDTH_PATTERN = /[\u200B-\u200D\u2060\uFEFF]/gu

function stripCitationArtifacts(value: string) {
  return value
    .replace(FILE_CITATION_PATTERN, " ")
    .replace(BRACKET_CITATION_PATTERN, " ")
    .replace(ZERO_WIDTH_PATTERN, "")
}

export function sanitizeExperienceText(value?: string | null) {
  if (!value) {
    return ""
  }

  return stripCitationArtifacts(value)
    .replace(/\s+/g, " ")
    .trim()
}

export function sanitizeExperienceMarkdown(value?: string | null) {
  if (!value) {
    return undefined
  }

  return stripCitationArtifacts(value)
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim()
}

export function sanitizeExperienceList(values?: string[] | null) {
  if (!values?.length) {
    return []
  }

  const seen = new Set<string>()

  return values
    .map((value) => sanitizeExperienceText(value).replace(/^[-*]\s*/, ""))
    .filter((value) => {
      if (!value || seen.has(value)) {
        return false
      }

      seen.add(value)
      return true
    })
}

export function normalizeExperienceForUi(experience: Experience): Experience {
  return {
    ...experience,
    id: String(experience.id),
    title: sanitizeExperienceText(experience.title),
    category: sanitizeExperienceText(experience.category),
    description: sanitizeExperienceText(experience.description),
    origin: sanitizeExperienceText(experience.origin),
    techStack: sanitizeExperienceList(experience.techStack),
    metrics: sanitizeExperienceList(experience.metrics),
    overallTechStack: sanitizeExperienceList(experience.overallTechStack),
    jobKeywords: sanitizeExperienceList(experience.jobKeywords),
    questionTypes: sanitizeExperienceList(experience.questionTypes),
    period: sanitizeExperienceText(experience.period),
    role: sanitizeExperienceText(experience.role),
    organization: sanitizeExperienceText(experience.organization),
    rawContent: sanitizeExperienceMarkdown(experience.rawContent),
    facets: experience.facets?.map((facet) => ({
      ...facet,
      id: facet.id == null ? undefined : String(facet.id),
      title: sanitizeExperienceText(facet.title),
      situation: sanitizeExperienceList(facet.situation),
      role: sanitizeExperienceList(facet.role),
      judgment: sanitizeExperienceList(facet.judgment),
      actions: sanitizeExperienceList(facet.actions),
      results: sanitizeExperienceList(facet.results),
      techStack: sanitizeExperienceList(facet.techStack),
      jobKeywords: sanitizeExperienceList(facet.jobKeywords),
      questionTypes: sanitizeExperienceList(facet.questionTypes),
    })),
  }
}
