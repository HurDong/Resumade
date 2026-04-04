export function isFrontendOnlyMode() {
  return process.env.NEXT_PUBLIC_RESUMADE_FRONTEND_ONLY === "true";
}
