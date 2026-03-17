export interface JdInsightData {
  summary?: string
  requirements?: {
    technicalSkills?: string[]
    coreCompetencies?: string[]
    preferredExperience?: string[]
  }
}

export interface CompanyResearchFocus {
  company?: string
  position?: string
  businessUnit?: string
  targetService?: string
  focusRole?: string
  techFocus?: string
  questionGoal?: string
}

export interface CompanyResearchData {
  focus: CompanyResearchFocus
  executiveSummary: string
  businessContext: string[]
  serviceLandscape: string[]
  roleScope: string[]
  techSignals: string[]
  motivationHooks: string[]
  serviceHooks: string[]
  resumeAngles: string[]
  interviewSignals: string[]
  recommendedNarrative: string
  followUpQuestions: string[]
  confidenceNotes: string[]
}

export function parseJdInsight(raw?: string): JdInsightData | null {
  if (!raw?.trim()) return null

  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== "object") return null
    return {
      summary: asString((parsed as Record<string, unknown>).summary),
      requirements: {
        technicalSkills: asStringArray((parsed as Record<string, unknown>).requirements, "technicalSkills"),
        coreCompetencies: asStringArray((parsed as Record<string, unknown>).requirements, "coreCompetencies"),
        preferredExperience: asStringArray((parsed as Record<string, unknown>).requirements, "preferredExperience"),
      },
    }
  } catch {
    return { summary: raw.trim() }
  }
}

export function parseCompanyResearch(raw?: string): CompanyResearchData | null {
  if (!raw?.trim()) return null

  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== "object") {
      return buildFallbackCompanyResearch(raw)
    }

    const record = parsed as Record<string, unknown>
    const focus = (record.focus ?? {}) as Record<string, unknown>

    return {
      focus: {
        company: asString(focus.company),
        position: asString(focus.position),
        businessUnit: asString(focus.businessUnit),
        targetService: asString(focus.targetService),
        focusRole: asString(focus.focusRole),
        techFocus: asString(focus.techFocus),
        questionGoal: asString(focus.questionGoal),
      },
      executiveSummary: asString(record.executiveSummary),
      businessContext: asArray(record.businessContext),
      serviceLandscape: asArray(record.serviceLandscape),
      roleScope: asArray(record.roleScope),
      techSignals: asArray(record.techSignals),
      motivationHooks: asArray(record.motivationHooks),
      serviceHooks: asArray(record.serviceHooks),
      resumeAngles: asArray(record.resumeAngles),
      interviewSignals: asArray(record.interviewSignals),
      recommendedNarrative: asString(record.recommendedNarrative),
      followUpQuestions: asArray(record.followUpQuestions),
      confidenceNotes: asArray(record.confidenceNotes),
    }
  } catch {
    return buildFallbackCompanyResearch(raw)
  }
}

function buildFallbackCompanyResearch(raw: string): CompanyResearchData {
  return {
    focus: {},
    executiveSummary: raw.trim(),
    businessContext: [],
    serviceLandscape: [],
    roleScope: [],
    techSignals: [],
    motivationHooks: [],
    serviceHooks: [],
    resumeAngles: [],
    interviewSignals: [],
    recommendedNarrative: "",
    followUpQuestions: [],
    confidenceNotes: [],
  }
}

function asString(value: unknown) {
  return typeof value === "string" ? decodeUnicodeEscapes(value) : ""
}

function asArray(value: unknown) {
  return Array.isArray(value)
    ? value
        .filter((item): item is string => typeof item === "string" && item.trim().length > 0)
        .map((item) => decodeUnicodeEscapes(item))
    : []
}

function asStringArray(parent: unknown, key: string) {
  if (!parent || typeof parent !== "object") return []
  return asArray((parent as Record<string, unknown>)[key])
}

function decodeUnicodeEscapes(value: string) {
  return value.replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) =>
    String.fromCharCode(Number.parseInt(hex, 16))
  )
}
