import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import {
  createApplicationInfoEntry,
  defaultApplicantProfile,
  type ApplicantProfile,
  type ApplicationInfoCategory,
  type ApplicationInfoEntry,
} from "@/lib/application-info";

interface ApplicationInfoState {
  profile: ApplicantProfile;
  entries: ApplicationInfoEntry[];
  setProfileField: (field: keyof ApplicantProfile, value: string) => void;
  saveEntry: (entry: ApplicationInfoEntry) => void;
  deleteEntry: (entryId: string) => void;
  duplicateEntry: (entryId: string) => void;
  togglePinned: (entryId: string) => void;
  createDraftEntry: (category: ApplicationInfoCategory) => ApplicationInfoEntry;
}

function cloneEntry(entry: ApplicationInfoEntry): ApplicationInfoEntry {
  const duplicated = createApplicationInfoEntry(entry.category);

  return {
    ...duplicated,
    pinned: false,
    values: { ...entry.values },
  };
}

export const useApplicationInfoStore = create<ApplicationInfoState>()(
  persist(
    (set, get) => ({
      profile: defaultApplicantProfile,
      entries: [],
      setProfileField: (field, value) =>
        set((state) => ({
          profile: {
            ...state.profile,
            [field]: value,
          },
        })),
      saveEntry: (entry) =>
        set((state) => {
          const now = new Date().toISOString();
          const normalizedEntry = {
            ...entry,
            values: { ...entry.values },
            updatedAt: now,
            createdAt: entry.createdAt || now,
          };
          const exists = state.entries.some((current) => current.id === entry.id);

          return {
            entries: exists
              ? state.entries.map((current) =>
                  current.id === entry.id ? normalizedEntry : current
                )
              : [normalizedEntry, ...state.entries],
          };
        }),
      deleteEntry: (entryId) =>
        set((state) => ({
          entries: state.entries.filter((entry) => entry.id !== entryId),
        })),
      duplicateEntry: (entryId) =>
        set((state) => {
          const entry = state.entries.find((current) => current.id === entryId);
          if (!entry) {
            return state;
          }

          return {
            entries: [cloneEntry(entry), ...state.entries],
          };
        }),
      togglePinned: (entryId) =>
        set((state) => ({
          entries: state.entries.map((entry) =>
            entry.id === entryId
              ? {
                  ...entry,
                  pinned: !entry.pinned,
                  updatedAt: new Date().toISOString(),
                }
              : entry
          ),
        })),
      createDraftEntry: (category) => createApplicationInfoEntry(category),
    }),
    {
      name: "resumade-application-info-v1",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        profile: state.profile,
        entries: state.entries,
      }),
    }
  )
);
