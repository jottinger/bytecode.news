import { initAuth } from "./auth.js";
import { renderNav } from "./components/nav.js";
import { startRouter } from "./router.js";

/* Bootstrap: restore session, render navigation, start routing */
async function boot() {
  await initAuth();
  renderNav();
  startRouter();
}

boot();
