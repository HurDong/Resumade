const LOCAL_BACKEND_ORIGIN = "http://127.0.0.1:8080";

function trimTrailingSlash(value: string) {
  return value.endsWith("/") ? value.slice(0, -1) : value;
}

function readConfiguredBackendOrigin() {
  const configuredOrigin =
    process.env.RESUMADE_API_BASE_URL ??
    process.env.NEXT_PUBLIC_RESUMADE_API_BASE_URL;

  if (!configuredOrigin) {
    return null;
  }

  return trimTrailingSlash(configuredOrigin);
}

export function resolveServerBackendOrigin() {
  const configuredOrigin = readConfiguredBackendOrigin();
  if (configuredOrigin) {
    return configuredOrigin;
  }

  return process.env.NODE_ENV === "production" ? null : LOCAL_BACKEND_ORIGIN;
}

export function getRequiredServerBackendOrigin() {
  const backendOrigin = resolveServerBackendOrigin();

  if (!backendOrigin) {
    throw new Error(
      "RESUMADE_API_BASE_URL must be set for server-side API proxying in production.",
    );
  }

  return backendOrigin;
}
