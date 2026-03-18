export const normalizeResumeCountText = (value: string) =>
  value.replace(/\r\n?/g, "\n")

export const countResumeCharacters = (value?: string | null) => {
  if (!value) return 0
  return Array.from(normalizeResumeCountText(value)).length
}
