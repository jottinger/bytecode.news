export function getBackendBaseUrl(): string {
  const explicit = process.env.API_URL?.trim();
  if (explicit) {
    return explicit.endsWith("/") ? explicit.slice(0, -1) : explicit;
  }

  const scheme = process.env.BACKEND_SCHEME?.trim() || "http";
  const host = process.env.BACKEND_HOST?.trim() || "localhost:8080";
  return `${scheme}://${host}`;
}
