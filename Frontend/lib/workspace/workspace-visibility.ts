export type WorkspaceVisibleApplication = {
  status?: string | null
  result?: string | null
}

export function isWorkspaceVisibleApplication<T extends WorkspaceVisibleApplication>(
  application: T,
) {
  const normalizedStatus = application.status?.trim().toLowerCase()
  const normalizedResult = application.result?.trim().toLowerCase()

  return normalizedStatus === "document" && normalizedResult === "pending"
}
