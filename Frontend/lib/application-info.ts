export type ApplicationInfoCategory =
  | "languageTest"
  | "languageSkill"
  | "certification"
  | "award"
  | "activity"
  | "custom";

export interface ApplicantProfile {
  fullName: string;
  email: string;
  phone: string;
  birthDate: string;
  portfolioUrl: string;
  notionUrl: string;
}

export interface ApplicationInfoEntry {
  id: string;
  category: ApplicationInfoCategory;
  values: Record<string, string>;
  pinned: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ApplicationInfoFieldDefinition {
  key: string;
  label: string;
  placeholder: string;
  inputType?: "text" | "email" | "tel" | "url" | "date";
  multiline?: boolean;
}

export interface ApplicationInfoCategoryDefinition {
  id: ApplicationInfoCategory;
  label: string;
  description: string;
  toneClassName: string;
  primaryFieldKey: string;
  summaryKeys: string[];
  fields: ApplicationInfoFieldDefinition[];
}

export const applicantProfileFieldDefinitions: Array<{
  key: keyof ApplicantProfile;
  label: string;
  placeholder: string;
  inputType?: "text" | "email" | "tel" | "url" | "date";
}> = [
  { key: "fullName", label: "이름", placeholder: "홍길동" },
  {
    key: "email",
    label: "이메일",
    placeholder: "resume@example.com",
    inputType: "email",
  },
  {
    key: "phone",
    label: "휴대전화",
    placeholder: "010-1234-5678",
    inputType: "tel",
  },
  {
    key: "birthDate",
    label: "생년월일",
    placeholder: "1999-01-01",
    inputType: "date",
  },
  {
    key: "portfolioUrl",
    label: "포트폴리오",
    placeholder: "https://portfolio.example.com",
    inputType: "url",
  },
  {
    key: "notionUrl",
    label: "노션 / 참고 링크",
    placeholder: "https://www.notion.so/...",
    inputType: "url",
  },
];

export const applicationInfoCategoryOrder: ApplicationInfoCategory[] = [
  "languageTest",
  "languageSkill",
  "certification",
  "award",
  "activity",
  "custom",
];

export const applicationInfoCategoryDefinitions: Record<
  ApplicationInfoCategory,
  ApplicationInfoCategoryDefinition
> = {
  languageTest: {
    id: "languageTest",
    label: "어학시험",
    description: "시험명, 등록번호, 취득일, 점수를 저장해 바로 복사합니다.",
    toneClassName:
      "border-sky-200 bg-sky-50/80 text-sky-700 dark:border-sky-500/40 dark:bg-sky-500/10 dark:text-sky-200",
    primaryFieldKey: "examName",
    summaryKeys: ["scoreOrGrade", "acquiredAt", "registrationNumber"],
    fields: [
      { key: "examName", label: "시험명", placeholder: "OPIc(영어)" },
      {
        key: "registrationNumber",
        label: "등록번호",
        placeholder: "2A4959107618",
      },
      {
        key: "acquiredAt",
        label: "취득일",
        placeholder: "2026-02-24",
        inputType: "date",
      },
      {
        key: "scoreOrGrade",
        label: "등급 / 점수",
        placeholder: "Intermediate High",
      },
      {
        key: "notes",
        label: "메모",
        placeholder: "유효기간 또는 제출 시 주의사항",
        multiline: true,
      },
    ],
  },
  languageSkill: {
    id: "languageSkill",
    label: "외국어 활용",
    description: "언어별 회화·작문·독해 수준을 항목으로 관리합니다.",
    toneClassName:
      "border-teal-200 bg-teal-50/80 text-teal-700 dark:border-teal-500/40 dark:bg-teal-500/10 dark:text-teal-200",
    primaryFieldKey: "language",
    summaryKeys: ["conversation", "writing", "reading", "accentOrNote"],
    fields: [
      { key: "language", label: "외국어", placeholder: "영어" },
      { key: "conversation", label: "회화", placeholder: "Advanced" },
      { key: "writing", label: "작문", placeholder: "Advanced" },
      { key: "reading", label: "독해", placeholder: "Advanced" },
      {
        key: "accentOrNote",
        label: "발음 / 기타",
        placeholder: "Authentic",
      },
    ],
  },
  certification: {
    id: "certification",
    label: "자격증 / 면허",
    description: "자격증명, 발급기관, 등록번호를 한 번에 관리합니다.",
    toneClassName:
      "border-violet-200 bg-violet-50/80 text-violet-700 dark:border-violet-500/40 dark:bg-violet-500/10 dark:text-violet-200",
    primaryFieldKey: "certificateName",
    summaryKeys: ["issuer", "acquiredAt", "registrationNumber"],
    fields: [
      {
        key: "certificateName",
        label: "자격증명",
        placeholder: "정보처리기사",
      },
      {
        key: "issuer",
        label: "발급기관",
        placeholder: "한국산업인력공단",
      },
      {
        key: "registrationNumber",
        label: "등록번호",
        placeholder: "25201070652C",
      },
      {
        key: "acquiredAt",
        label: "취득일",
        placeholder: "2025-06-13",
        inputType: "date",
      },
      {
        key: "notes",
        label: "비고",
        placeholder: "갱신 필요 여부, 제출용 메모",
        multiline: true,
      },
    ],
  },
  award: {
    id: "award",
    label: "수상경력",
    description: "수상명과 수상내역을 그대로 저장해 지원서에 재사용합니다.",
    toneClassName:
      "border-amber-200 bg-amber-50/80 text-amber-700 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-200",
    primaryFieldKey: "awardName",
    summaryKeys: ["issuer", "awardedAt", "description"],
    fields: [
      {
        key: "awardName",
        label: "수상명",
        placeholder: "광명융합기술교육원 우수상",
      },
      {
        key: "issuer",
        label: "수여기관",
        placeholder: "한국폴리텍대학 광명융합기술교육원",
      },
      {
        key: "awardedAt",
        label: "수상일자",
        placeholder: "2025-12-12",
        inputType: "date",
      },
      {
        key: "description",
        label: "수상내역",
        placeholder: "교육과정 동안 학과에 기여한 공로로 수상",
        multiline: true,
      },
    ],
  },
  activity: {
    id: "activity",
    label: "활동",
    description: "활동 구분, 기간, 역할, 설명을 정리해 즉시 복사합니다.",
    toneClassName:
      "border-rose-200 bg-rose-50/80 text-rose-700 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-200",
    primaryFieldKey: "organization",
    summaryKeys: ["activityType", "startDate", "endDate", "role"],
    fields: [
      {
        key: "activityType",
        label: "활동구분",
        placeholder: "동아리활동 / 교육 / 봉사",
      },
      {
        key: "organization",
        label: "기관 및 조직명",
        placeholder: "삼성 청년 SW 아카데미 11기",
      },
      {
        key: "startDate",
        label: "시작일",
        placeholder: "2024-01-02",
        inputType: "date",
      },
      {
        key: "endDate",
        label: "종료일",
        placeholder: "2024-12-19",
        inputType: "date",
      },
      { key: "role", label: "직위 또는 역할", placeholder: "교육생" },
      {
        key: "description",
        label: "활동내역",
        placeholder: "Spring Framework와 Vue.js를 활용한 프로젝트 개발",
        multiline: true,
      },
    ],
  },
  custom: {
    id: "custom",
    label: "직접 입력",
    description: "정형화되지 않은 제출 정보를 자유 형식으로 저장합니다.",
    toneClassName:
      "border-slate-200 bg-slate-50/80 text-slate-700 dark:border-slate-500/40 dark:bg-slate-500/10 dark:text-slate-200",
    primaryFieldKey: "label",
    summaryKeys: ["value", "description"],
    fields: [
      { key: "label", label: "항목명", placeholder: "병역사항 / 교육이수 / 링크" },
      { key: "value", label: "값", placeholder: "예: 육군 만기전역" },
      {
        key: "description",
        label: "설명",
        placeholder: "지원서에 그대로 넣을 설명",
        multiline: true,
      },
    ],
  },
};

export const defaultApplicantProfile: ApplicantProfile = {
  fullName: "",
  email: "",
  phone: "",
  birthDate: "",
  portfolioUrl: "",
  notionUrl: "",
};

function createId() {
  return `info-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export function createApplicationInfoEntry(
  category: ApplicationInfoCategory
): ApplicationInfoEntry {
  const definition = applicationInfoCategoryDefinitions[category];
  const values = definition.fields.reduce<Record<string, string>>((acc, field) => {
    acc[field.key] = "";
    return acc;
  }, {});

  const now = new Date().toISOString();

  return {
    id: createId(),
    category,
    values,
    pinned: false,
    createdAt: now,
    updatedAt: now,
  };
}

export function getApplicationInfoDefinition(category: ApplicationInfoCategory) {
  return applicationInfoCategoryDefinitions[category];
}

export function getApplicationInfoEntryTitle(entry: ApplicationInfoEntry) {
  const definition = getApplicationInfoDefinition(entry.category);
  const primaryValue = entry.values[definition.primaryFieldKey]?.trim();

  if (primaryValue) {
    return primaryValue;
  }

  return `${definition.label} 항목`;
}

export function getApplicationInfoEntryFields(entry: ApplicationInfoEntry) {
  const definition = getApplicationInfoDefinition(entry.category);

  return definition.fields
    .map((field) => ({
      key: field.key,
      label: field.label,
      value: entry.values[field.key]?.trim() ?? "",
      multiline: field.multiline ?? false,
    }))
    .filter((field) => field.value.length > 0);
}

export function getApplicationInfoEntrySummary(entry: ApplicationInfoEntry) {
  const definition = getApplicationInfoDefinition(entry.category);

  const summaryValues = definition.summaryKeys
    .map((key) => entry.values[key]?.trim() ?? "")
    .filter(Boolean);

  if (summaryValues.length > 0) {
    return summaryValues.join(" · ");
  }

  const fallbackValues = getApplicationInfoEntryFields(entry)
    .slice(0, 3)
    .map((field) => field.value);

  return fallbackValues.join(" · ");
}

export function getApplicationInfoCopyText(
  entry: ApplicationInfoEntry,
  mode: "summary" | "detail" = "detail"
) {
  const definition = getApplicationInfoDefinition(entry.category);
  const title = getApplicationInfoEntryTitle(entry);
  const summary = getApplicationInfoEntrySummary(entry);
  const fields = getApplicationInfoEntryFields(entry);

  if (mode === "summary") {
    return [title, summary].filter(Boolean).join(" | ");
  }

  return [
    `[${definition.label}] ${title}`,
    ...fields.map((field) => `${field.label}: ${field.value}`),
  ].join("\n");
}

export function matchesApplicationInfoSearch(
  entry: ApplicationInfoEntry,
  rawQuery: string
) {
  const query = rawQuery.trim().toLowerCase();
  if (!query) {
    return true;
  }

  const definition = getApplicationInfoDefinition(entry.category);
  const haystack = [
    definition.label,
    getApplicationInfoEntryTitle(entry),
    getApplicationInfoEntrySummary(entry),
    ...Object.values(entry.values),
  ]
    .join(" ")
    .toLowerCase();

  return haystack.includes(query);
}

export function getProfileCopyFields(profile: ApplicantProfile) {
  return applicantProfileFieldDefinitions
    .map((field) => ({
      key: field.key,
      label: field.label,
      value: profile[field.key].trim(),
    }))
    .filter((field) => field.value.length > 0);
}
