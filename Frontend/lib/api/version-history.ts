const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"

export type SnapshotType = "DRAFT_GENERATED" | "WASHED" | "FINAL_EDIT"

export interface Snapshot {
  id: number
  snapshotType: SnapshotType
  content: string
  createdAt: string
}

export async function fetchVersionHistory(questionId: number): Promise<Snapshot[]> {
  const res = await fetch(`${API_BASE}/api/v1/workspace/questions/${questionId}/history`)
  if (!res.ok) throw new Error("버전 히스토리 조회 실패")
  return res.json()
}
