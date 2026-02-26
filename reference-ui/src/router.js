import { isLoggedIn, getPrincipal, onAuthChange } from "./auth.js";
import { hasRole } from "./roles.js";
import { renderNav } from "./components/nav.js";

let routes = [];
const app = () => document.getElementById("app");

/** Define all application routes */
function defineRoutes() {
  routes = [
    { path: "/", load: () => import("./views/home.js") },
    { path: "/posts/:year/:month/:slug", load: () => import("./views/post-detail.js") },
    { path: "/search", load: () => import("./views/search.js") },
    { path: "/login", load: () => import("./views/login.js") },
    { path: "/auth/callback", load: () => import("./views/auth-callback.js") },
    { path: "/submit", load: () => import("./views/submit-post.js"), auth: true },
    { path: "/edit/:id", load: () => import("./views/edit-post.js"), auth: true },
    { path: "/profile", load: () => import("./views/profile.js"), auth: true },
    { path: "/admin/posts", load: () => import("./views/admin/pending-posts.js"), role: "ADMIN" },
    { path: "/admin/users", load: () => import("./views/admin/manage-users.js"), role: "ADMIN" },
    { path: "/admin/categories", load: () => import("./views/admin/manage-categories.js"), role: "ADMIN" },
    { path: "/terms", load: () => import("./views/terms.js") },
    { path: "/privacy", load: () => import("./views/privacy.js") },
    { path: "/cookies", load: () => import("./views/cookies.js") },
  ];
}

/** Match a URL path against a route pattern, extracting params */
function matchRoute(pathname) {
  for (const route of routes) {
    const parts = route.path.split("/");
    const segments = pathname.split("/");
    if (parts.length !== segments.length) continue;

    const params = {};
    let match = true;
    for (let i = 0; i < parts.length; i++) {
      if (parts[i].startsWith(":")) {
        params[parts[i].slice(1)] = decodeURIComponent(segments[i]);
      } else if (parts[i] !== segments[i]) {
        match = false;
        break;
      }
    }
    if (match) return { route, params };
  }
  return null;
}

/** Navigate to a path via pushState */
export function navigate(path) {
  window.history.pushState(null, "", path);
  resolve();
}

/** Resolve the current URL and render the matching view */
async function resolve() {
  const pathname = window.location.pathname;
  const result = matchRoute(pathname);

  if (!result) {
    app().innerHTML = "<article><h2>Not found</h2><p>No page at this path.</p></article>";
    return;
  }

  const { route, params } = result;

  // Route guard: authentication required
  if (route.auth && !isLoggedIn()) {
    navigate("/login?return=" + encodeURIComponent(pathname));
    return;
  }

  // Route guard: role required
  if (route.role) {
    if (!isLoggedIn()) {
      navigate("/login?return=" + encodeURIComponent(pathname));
      return;
    }
    const p = getPrincipal();
    if (!p || !hasRole(p.role, route.role)) {
      app().innerHTML = "<article><h2>Not authorized</h2><p>You do not have permission to view this page.</p></article>";
      return;
    }
  }

  // Show loading state
  app().innerHTML = '<article aria-busy="true">Loading...</article>';

  try {
    const mod = await route.load();
    const search = new URLSearchParams(window.location.search);
    await mod.render(app(), params, search);
  } catch (err) {
    app().innerHTML = `<article><h2>Error</h2><p>${err.detail || err.message || "Something went wrong"}</p></article>`;
  }
}

/** Start the router: listen for navigation events and resolve the initial URL */
export function startRouter() {
  defineRoutes();
  window.addEventListener("popstate", resolve);

  // Intercept link clicks for SPA navigation
  document.addEventListener("click", (e) => {
    const a = e.target.closest("a[href]");
    if (!a) return;
    const url = new URL(a.href, window.location.origin);
    if (url.origin !== window.location.origin) return; // external link
    e.preventDefault();
    navigate(url.pathname + url.search + url.hash);
  });

  // Re-render nav on auth changes
  onAuthChange(() => renderNav());

  resolve();
}
