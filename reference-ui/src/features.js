import { get } from "./api.js";

let cached = null;
let inFlight = null;
const listeners = [];

function notify() {
  listeners.forEach((fn) => fn(cached));
}

/** Fetch and cache the /features response. Returns null if unavailable. */
export async function getFeatures() {
  if (cached) return cached;
  if (inFlight) return inFlight;

  inFlight = (async () => {
    try {
      cached = await get("/features");
    } catch {
      cached = null;
    } finally {
      inFlight = null;
      notify();
    }
    return cached;
  })();

  return inFlight;
}

/** Return the cached features without fetching (may be null if not yet loaded) */
export function getCachedFeatures() {
  return cached;
}

/** Register a callback for feature cache updates. */
export function onFeaturesChange(fn) {
  listeners.push(fn);
}
