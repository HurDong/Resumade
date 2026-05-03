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

const fullDateTokenPattern = /(\d{4})[.\-/\s]?(\d{2})[.\-/\s]?(\d{2})/g;

function isProfileDateCategory(category: ProfileCategory) {
  return category !== "skill";
}

function isValidDate(year: string, month: string, day: string) {
  const yearNumber = Number(year);
  const monthNumber = Number(month);
  const dayNumber = Number(day);

  if (monthNumber < 1 || monthNumber > 12 || dayNumber < 1) {
    return false;
  }

  const lastDay = new Date(yearNumber, monthNumber, 0).getDate();
  return dayNumber <= lastDay;
}

export function normalizeProfileDateLabel(value: string): string {
  const trimmedValue = value.trim();

  return trimmedValue.replace(
    fullDateTokenPattern,
    (matchedValue, year: string, month: string, day: string) => {
      if (!isValidDate(year, month, day)) {
        return matchedValue;
      }

      return `${year}.${month}.${day}`;
    },
  );
}

export function getProfileDateCopyValue(value: string): string {
  const normalizedValue = normalizeProfileDateLabel(value);

  return normalizedValue.replace(
    /(\d{4})\.(\d{2})\.(\d{2})/g,
    "$1$2$3",
  );
}

export function getProfileDateParts(value: string) {
  return Array.from(
    normalizeProfileDateLabel(value).matchAll(/(\d{4})\.(\d{2})\.(\d{2})/g),
    (match) => ({
      value: match[0],
      copyValue: `${match[1]}${match[2]}${match[3]}`,
    }),
  );
}

export function normalizeProfileEntryForSave(entry: ProfileEntry): ProfileEntry {
  if (!isProfileDateCategory(entry.category)) {
    return entry;
  }

  return {
    ...entry,
    dateLabel: normalizeProfileDateLabel(entry.dateLabel),
  };
}

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
