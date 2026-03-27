export const profileCategoryOrder = [
  "certification",
  "language",
  "award",
  "education",
  "skill",
] as const;

export type ProfileCategory = (typeof profileCategoryOrder)[number];

export type ProfileEntry = {
  id: string;
  category: ProfileCategory;
  title: string;
  organization: string;
  dateLabel: string;
  summary: string;
  referenceId: string;
  highlight: string;
  sortOrder: number;
};

export function createEmptyProfileEntry(
  category: ProfileCategory,
): ProfileEntry {
  return {
    id:
      typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID()
        : `entry-${Date.now()}`,
    category,
    title: "",
    organization: "",
    dateLabel: "",
    summary: "",
    referenceId: "",
    highlight: "",
    sortOrder: 0,
  };
}
