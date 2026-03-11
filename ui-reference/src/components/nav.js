import { isLoggedIn, getPrincipal, clearAuth } from "../auth.js";
import { navigate } from "../router.js";
import { escapeHtml } from "../escape.js";
import { renderSidebar } from "./sidebar.js";

/** Render minimal top nav: user identity + login/logout */
export function renderNav() {
  const el = document.getElementById("nav");
  if (!el) return;

  const links = [];

  if (isLoggedIn()) {
    const p = getPrincipal();
    links.push('<li><a href="/profile">' + escapeHtml(p.displayName || p.username) + "</a></li>");
    links.push('<li><a href="#" id="logout-link">Log out</a></li>');
  } else {
    links.push('<li><a href="/login">Log in</a></li>');
  }

  el.innerHTML = links.join("");

  const logoutLink = document.getElementById("logout-link");
  if (logoutLink) {
    logoutLink.addEventListener("click", (e) => {
      e.preventDefault();
      clearAuth();
      navigate("/");
    });
  }

  // Re-render sidebar since auth state affects its content
  renderSidebar();
}
