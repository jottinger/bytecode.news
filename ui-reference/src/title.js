import { getCachedFeatures } from "./features.js";

/** Get the site name from features, falling back to a default */
function getSiteName() {
  return getCachedFeatures()?.siteName || "Nevet";
}

/** Update the browser tab title, appending the site name */
export function setTitle(pageTitle) {
  const site = getSiteName();
  document.title = pageTitle ? `${pageTitle} - ${site}` : site;
}

/** Reset the browser title to just the site name */
export function resetTitle() {
  document.title = getSiteName();
}
