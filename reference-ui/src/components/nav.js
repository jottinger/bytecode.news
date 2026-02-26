import { isLoggedIn, getPrincipal, clearAuth } from "../auth.js";
import { hasRole } from "../roles.js";
import { navigate } from "../router.js";

/** Render auth-aware navigation links into #nav */
export function renderNav() {
  const el = document.getElementById("nav");
  if (!el) return;

  const links = [];
  links.push('<li><a href="/search">Search</a></li>');

  if (isLoggedIn()) {
    const p = getPrincipal();
    links.push('<li><a href="/submit">Submit Post</a></li>');
    links.push('<li><a href="/profile">' + escapeHtml(p.displayName || p.username) + '</a></li>');

    if (p && hasRole(p.role, "ADMIN")) {
      links.push('<li><a href="/admin/posts">Pending Posts</a></li>');
      links.push('<li><a href="/admin/users">Users</a></li>');
      links.push('<li><a href="/admin/categories">Categories</a></li>');
    }

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
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}
