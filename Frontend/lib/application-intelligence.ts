export interface JdInsightData {
  summary?: string
  requirements?: {
    technicalSkills?: string[]
    coreCompetencies?: string[]
    preferredExperience?: string[]
  }
}

// ── 기업 분석 타입 ─────────────────────────────────────────────────

export interface CompanyResearchFocus {
  company?: string
  position?: string
  /** AI가 추론한 사업부/팀 */
  inferredBusinessUnit?: string
  /** AI가 추론한 제품/서비스 */
  inferredProduct?: string
}

export interface DiscoveredContext {
  businessUnit?: string
  product?: string
  evidenceSources?: string[]
}

export type TechConfidence = "CONFIRMED" | "INFERRED" | "UNCERTAIN"
export type TechCategory =
  | "Backend"
  | "Frontend"
  | "Database"
  | "Infrastructure"
  | "DevOps"
  | "Mobile"
  | "AI-ML"
  | string

export interface TechStackItem {
  name: string
  category: TechCategory
  confidence: TechConfidence
  source: string
}

export interface TechFact {
  summary: string
  detail: string
  source: string
}

export interface FitAnalysis {
  jdStatedRequirements: string[]
  actualTechStack: string[]
  gapAnalysis: string
  coverLetterHints: string[]
}

export interface SearchSource {
  title: string
  uri: string
}

export interface CompanyResearchData {
  focus: CompanyResearchFocus
  executiveSummary: string
  // 기존 섹션
  businessContext: string[]
  serviceLandscape: string[]
  roleScope: string[]
  motivationHooks: string[]
  serviceHooks: string[]
  resumeAngles: string[]
  interviewSignals: string[]
  recommendedNarrative: string
  followUpQuestions: string[]
  confidenceNotes: string[]
  // 신규 구조화 섹션
  discoveredContext?: DiscoveredContext
  techStack?: TechStackItem[]
  recentTechWork?: TechFact[]
  fitAnalysis?: FitAnalysis
  // Gemini 실제 검색 출처
  searchQueries?: string[]
  searchSources?: SearchSource[]
}

// ── 파서 ───────────────────────────────────────────────────────────

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

  const parsed = unwrapPossiblyEscapedJson(raw)
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    return buildFallbackCompanyResearch(normalizeLooseText(raw))
  }

  const record = parsed as Record<string, unknown>
  const focus = normalizeObject(record.focus)

  return {
    focus: {
      company: asString(focus.company),
      position: asString(focus.position),
      inferredBusinessUnit: asString(focus.inferredBusinessUnit),
      inferredProduct: asString(focus.inferredProduct),
    },
    executiveSummary: asString(record.executiveSummary),
    businessContext: asArray(record.businessContext),
    serviceLandscape: asArray(record.serviceLandscape),
    roleScope: asArray(record.roleScope),
    motivationHooks: asArray(record.motivationHooks),
    serviceHooks: asArray(record.serviceHooks),
    resumeAngles: asArray(record.resumeAngles),
    interviewSignals: asArray(record.interviewSignals),
    recommendedNarrative: asString(record.recommendedNarrative),
    followUpQuestions: asArray(record.followUpQuestions),
    confidenceNotes: asArray(record.confidenceNotes),
    discoveredContext: parseDiscoveredContext(record.discoveredContext),
    techStack: parseTechStack(record.techStack),
    recentTechWork: parseTechFacts(record.recentTechWork),
    fitAnalysis: parseFitAnalysis(record.fitAnalysis),
    searchQueries: asArray(record.searchQueries),
    searchSources: parseSearchSources(record.searchSources),
  }
}

function parseDiscoveredContext(raw: unknown): DiscoveredContext | undefined {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return undefined
  const r = raw as Record<string, unknown>
  return {
    businessUnit: asString(r.businessUnit),
    product: asString(r.product),
    evidenceSources: asArray(r.evidenceSources),
  }
}

function parseTechStack(raw: unknown): TechStackItem[] | undefined {
  if (!Array.isArray(raw)) return undefined
  return raw
    .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
    .map((item) => ({
      name: asString(item.name),
      category: asString(item.category) as TechCategory,
      confidence: (asString(item.confidence) as TechConfidence) || "UNCERTAIN",
      source: asString(item.source),
    }))
    .filter((item) => item.name.length > 0)
}

function parseTechFacts(raw: unknown): TechFact[] | undefined {
  if (!Array.isArray(raw)) return undefined
  return raw
    .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
    .map((item) => ({
      summary: asString(item.summary),
      detail: asString(item.detail),
      source: asString(item.source),
    }))
    .filter((item) => item.summary.length > 0)
}

function parseFitAnalysis(raw: unknown): FitAnalysis | undefined {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return undefined
  const r = raw as Record<string, unknown>
  return {
    jdStatedRequirements: asArray(r.jdStatedRequirements),
    actualTechStack: asArray(r.actualTechStack),
    gapAnalysis: asString(r.gapAnalysis),
    coverLetterHints: asArray(r.coverLetterHints),
  }
}

function parseSearchSources(raw: unknown): SearchSource[] | undefined {
  if (!Array.isArray(raw)) return undefined
  return raw
    .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
    .map((item) => ({
      title: asString(item.title),
      uri: asString(item.uri),
    }))
    .filter((item) => item.uri.length > 0)
}

function buildFallbackCompanyResearch(raw: string): CompanyResearchData {
  return {
    focus: {},
    executiveSummary: normalizeLooseText(raw),
    businessContext: [],
    serviceLandscape: [],
    roleScope: [],
    motivationHooks: [],
    serviceHooks: [],
    resumeAngles: [],
    interviewSignals: [],
    recommendedNarrative: "",
    followUpQuestions: [],
    confidenceNotes: [],
  }
}

// ── 유틸 ───────────────────────────────────────────────────────────

function asString(value: unknown) {
  return typeof value === "string" ? normalizeLooseText(value) : ""
}

function asArray(value: unknown) {
  return Array.isArray(value)
    ? value
        .filter((item): item is string => typeof item === "string" && item.trim().length > 0)
        .map((item) => normalizeLooseText(item))
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

function normalizeLooseText(value: string) {
  let current = value.trim()
  for (let i = 0; i < 4; i += 1) {
    const unwrapped = unwrapQuotedString(current)
    const normalizedEscapes = decodeLooseEscapes(unwrapped)
    if (normalizedEscapes === current) break
    current = normalizedEscapes.trim()
  }
  return current
}

function unwrapPossiblyEscapedJson(raw: string) {
  let current: unknown = raw.trim()
  for (let i = 0; i < 4; i += 1) {
    if (typeof current !== "string") return current
    const trimmed = current.trim()
    if (!trimmed) return null
    try {
      current = JSON.parse(trimmed)
    } catch {
      const decoded = decodeLooseEscapes(trimmed)
      if (decoded === trimmed) return null
      current = decoded
    }
  }
  return typeof current === "string" ? null : current
}

function unwrapQuotedString(value: string) {
  try {
    const parsed = JSON.parse(value)
    return typeof parsed === "string" ? parsed : value
  } catch {
    return value
  }
}

function decodeLooseEscapes(value: string) {
  return decodeUnicodeEscapes(
    value
      .replace(/\\\\u/gi, "\\u")
      .replace(/\\\\n/g, "\\n")
      .replace(/\\\\t/g, "\\t")
      .replace(/\\"/g, "\"")
      .replace(/\\\\/g, "\\")
  )
    .replace(/\\n/g, "\n")
    .replace(/\\t/g, "\t")
}

function normalizeObject(value: unknown) {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {}
}
