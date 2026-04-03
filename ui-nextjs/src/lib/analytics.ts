declare global {
  interface Window {
    gtag?: (...args: [string, ...unknown[]]) => void;
  }
}

/**
 * Sends a custom event to Google Analytics via gtag.
 */
export function trackEvent(name: string, params?: Record<string, string | number>) {
  if (typeof window !== "undefined" && typeof window.gtag === "function") {
    window.gtag("event", name, params);
  }
}
