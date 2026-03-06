import { initAuth } from "./auth.js";
import { getFeatures, getCachedFeatures } from "./features.js";
import { renderNav } from "./components/nav.js";
import { startRouter } from "./router.js";
import { resetTitle } from "./title.js";

/* Bootstrap: restore session, load features, render navigation, start routing */
async function boot() {
  await initAuth();
  await getFeatures();

  // Apply site name from features to header and title
  const siteName = getCachedFeatures()?.siteName;
  if (siteName) {
    const brandLink = document.querySelector("header a[href='/']");
    if (brandLink) brandLink.textContent = siteName;
    const rssLink = document.querySelector("link[type='application/rss+xml']");
    if (rssLink) rssLink.title = siteName + " RSS";
    resetTitle();
  }

  renderNav();
  startRouter();
}

boot();
