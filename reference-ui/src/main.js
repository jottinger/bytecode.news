import { initAuth } from "./auth.js";
import { getFeatures, getCachedFeatures, onFeaturesChange } from "./features.js";
import { renderNav } from "./components/nav.js";
import { startRouter, refreshRoutes } from "./router.js";
import { resetTitle } from "./title.js";

const UI_VERSION = import.meta.env.VITE_UI_VERSION || "dev";
const UI_COMMIT = import.meta.env.VITE_UI_COMMIT || "unknown";
const UI_BRANCH = import.meta.env.VITE_UI_BRANCH || "unknown";
const UI_BUILD_TIME = import.meta.env.VITE_UI_BUILD_TIME || "unknown";

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

  const name = version.name || "nevet";
  const semver = version.version || "dev";
  const commit = version.commit || "unknown";
  const branch = version.branch || "unknown";
  const built = version.buildTime || "unknown";
  return `Backend: ${name} ${semver} | ${commit} (${branch}) | Built ${built}`;
}

function formatFrontendVersion() {
  return `Frontend: reference-ui ${UI_VERSION} | ${UI_COMMIT} (${UI_BRANCH}) | Built ${UI_BUILD_TIME}`;
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
