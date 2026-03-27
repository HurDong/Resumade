import type { ProfileEntry } from "@/lib/profile-library";

const PROFILE_LIBRARY_API = "/api/profile-library";

async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || "요청 처리에 실패했습니다.");
  }

  return (await response.json()) as T;
}

export async function fetchProfileEntries() {
  const response = await fetch(PROFILE_LIBRARY_API, {
    cache: "no-store",
  });

  return parseResponse<ProfileEntry[]>(response);
}

export async function createProfileEntry(entry: ProfileEntry) {
  const response = await fetch(PROFILE_LIBRARY_API, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(entry),
  });

  return parseResponse<ProfileEntry>(response);
}

export async function updateProfileEntry(entry: ProfileEntry) {
  const response = await fetch(`${PROFILE_LIBRARY_API}/${entry.id}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(entry),
  });

  return parseResponse<ProfileEntry>(response);
}

export async function deleteProfileEntry(id: string) {
  const response = await fetch(`${PROFILE_LIBRARY_API}/${id}`, {
    method: "DELETE",
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || "삭제에 실패했습니다.");
  }
}

export async function reorderProfileEntries(category: string, ids: string[]) {
  const response = await fetch(`${PROFILE_LIBRARY_API}/reorder`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ category, ids }),
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || "순서 저장에 실패했습니다.");
  }
}
