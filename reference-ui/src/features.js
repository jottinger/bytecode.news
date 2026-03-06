import { get } from "./api.js";

let cached = null;

/** Fetch and cache the /features response. Returns null if unavailable. */
export async function getFeatures() {
  if (cached) return cached;
  try {
    cached = await get("/features");
  } catch {
    cached = null;
  }
  return cached;
}

/** Return the cached features without fetching (may be null if not yet loaded) */
export function getCachedFeatures() {
  return cached;
}
