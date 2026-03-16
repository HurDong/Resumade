const DEFAULT_BACKEND_ORIGIN = "http://127.0.0.1:8080";

export function resolveApiBaseUrl() {
  if (typeof window === "undefined") {
    return process.env.RESUMADE_API_BASE_URL ?? DEFAULT_BACKEND_ORIGIN;
  }

  const configuredBaseUrl = process.env.NEXT_PUBLIC_RESUMADE_API_BASE_URL;
  if (configuredBaseUrl) {
    return configuredBaseUrl;
  }

  const { protocol, hostname } = window.location;
  if (hostname === "localhost" || hostname === "127.0.0.1") {
    return `${protocol}//${hostname}:8080`;
  }

  return "";
}

export function toApiUrl(pathname: string) {
  const baseUrl = resolveApiBaseUrl();
  return baseUrl ? `${baseUrl}${pathname}` : pathname;
}
