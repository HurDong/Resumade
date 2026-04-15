export type WorkspaceVisibleApplication = {
  result?: string | null
}

export function isWorkspaceVisibleApplication<T extends WorkspaceVisibleApplication>(
  application: T,
) {
  return (application.result ?? "pending") === "pending"
}
