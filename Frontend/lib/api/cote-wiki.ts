import { type CoteWikiDraft, type CoteWikiNote, buildCoteWikiPayload } from "@/lib/cote-wiki"

const API = "/api/tech-notes"

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, options)
  if (!response.ok) {
    const message = (await response.text()).trim()
    throw new Error(message || "코테 위키 요청에 실패했습니다.")
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json()
}

export function fetchCoteWikiNotes() {
  return request<CoteWikiNote[]>(API)
}

export function fetchCoteWikiNote(id: number | string) {
  return request<CoteWikiNote>(`${API}/${id}`)
}

export function createCoteWikiNote(draft: Pick<CoteWikiDraft, "title" | "category">) {
  return request<CoteWikiNote>(API, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      title: draft.title.trim(),
      category: draft.category,
      content: "",
    }),
  })
}

export function updateCoteWikiNote(id: number | string, draft: CoteWikiDraft) {
  return request<CoteWikiNote>(`${API}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(buildCoteWikiPayload(draft)),
  })
}

export function reorderCoteWikiNotes(noteIds: number[]) {
  return request<CoteWikiNote[]>(`${API}/reorder`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ noteIds }),
  })
}

export function deleteCoteWikiNote(id: number | string) {
  return request<void>(`${API}/${id}`, { method: "DELETE" })
}
