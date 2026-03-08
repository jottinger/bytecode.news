import { initAuth } from "./auth.js";
import { getFeatures, getCachedFeatures, onFeaturesChange } from "./features.js";
import { renderNav } from "./components/nav.js";
import { startRouter, refreshRoutes } from "./router.js";
import { resetTitle } from "./title.js";

function applySiteNameFromFeatures() {
  const siteName = getCachedFeatures()?.siteName;
  if (!siteName) return;

  const brandLink = document.querySelector("header a[href='/']");
  if (brandLink) brandLink.textContent = siteName;
  const rssLink = document.querySelector("link[type='application/rss+xml']");
  if (rssLink) rssLink.title = siteName + " RSS";
  resetTitle();
}

/* Bootstrap quickly, then hydrate auth/features in the background */
async function boot() {
  renderNav();
  startRouter();

  onFeaturesChange(() => {
    applySiteNameFromFeatures();
    renderNav();
    refreshRoutes();
  });

  applySiteNameFromFeatures();

  await initAuth();
  await getFeatures();
}

boot();
