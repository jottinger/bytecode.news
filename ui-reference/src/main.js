import { initAuth } from "./auth.js";
import { getFeatures, getCachedFeatures, onFeaturesChange } from "./features.js";
import { renderNav } from "./components/nav.js";
import { startRouter, refreshRoutes } from "./router.js";
import { resetTitle } from "./title.js";

const UI_COMMIT = import.meta.env.VITE_UI_COMMIT || "unknown";
const UI_BRANCH = import.meta.env.VITE_UI_BRANCH || "unknown";

function applySiteNameFromFeatures() {
  const siteName = getCachedFeatures()?.siteName;
  if (!siteName) return;

  const brandLink = document.querySelector("header a[href='/']");
  if (brandLink) brandLink.textContent = siteName;
  const rssLink = document.querySelector("link[type='application/rss+xml']");
  if (rssLink) rssLink.title = siteName + " RSS";
  resetTitle();
}

function formatBackendVersion(features) {
  const version = features?.version;
  if (!version) return "Backend: unknown";

  const commit = version.commit || "unknown";
  const branch = version.branch || "unknown";
  return `backend: ${commit} (${branch})`;
}

function formatFrontendVersion() {
  return `frontend: ${UI_COMMIT} (${UI_BRANCH})`;
}

function renderBuildStatus() {
  const el = document.getElementById("build-status");
  if (!el) return;

  const backend = formatBackendVersion(getCachedFeatures());
  const frontend = formatFrontendVersion();
  el.textContent = `${backend} || ${frontend}`;
}

/* Bootstrap quickly, then hydrate auth/features in the background */
async function boot() {
  renderNav();
  startRouter();
  renderBuildStatus();

  onFeaturesChange(() => {
    applySiteNameFromFeatures();
    renderBuildStatus();
    renderNav();
    refreshRoutes();
  });

  applySiteNameFromFeatures();

  await initAuth();
  await getFeatures();
}

boot();
