export type WorkspaceVisibleApplication = {
  result?: string | null
}

export function isWorkspaceVisibleApplication<T extends WorkspaceVisibleApplication>(
  application: T,
) {
  const normalizedResult = application.result?.trim().toLowerCase()

  return normalizedResult === "pending"
}
