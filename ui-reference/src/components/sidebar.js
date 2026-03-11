import { isLoggedIn, getPrincipal } from "../auth.js";
import { hasRole } from "../roles.js";
import { escapeHtml } from "../escape.js";
import { getCachedFeatures } from "../features.js";
import { featureRegistry } from "../feature-registry.js";
import { get } from "../api.js";

/** Render the sidebar with navigation, feature links, and CMS-managed content */
export function renderSidebar() {
  const el = document.getElementById("sidebar");
  if (!el) return;

  const features = getCachedFeatures();
  const groups = features?.operationGroups || [];

  el.hidden = false;

  const sections = [];

  // Browse section: search + feature-gated links
  const browseLinks = [];
  browseLinks.push('<li><a href="/search">Search</a></li>');
  browseLinks.push('<li><a href="/">Blog</a></li>');
  browseLinks.push('<li><a href="/logs">Logs</a></li>');
  for (const [group, config] of Object.entries(featureRegistry)) {
    if (groups.includes(group) && config.navPath) {
      browseLinks.push(`<li><a href="${config.navPath}">${escapeHtml(config.label)}</a></li>`);
    }
  }
  sections.push(`<nav><strong>Browse</strong><ul>${browseLinks.join("")}</ul></nav>`);

  // Actions section: submit
  const actionLinks = [];
  if (isLoggedIn() || features?.anonymousSubmission) {
    actionLinks.push('<li><a href="/submit">Submit Post</a></li>');
  }
  if (actionLinks.length > 0) {
    sections.push(`<nav><strong>Actions</strong><ul>${actionLinks.join("")}</ul></nav>`);
  }

  // Admin section
  if (isLoggedIn()) {
    const p = getPrincipal();
    if (p && hasRole(p.role, "ADMIN")) {
      const adminLinks = [
        '<li><a href="/admin/posts">Pending Posts</a></li>',
        '<li><a href="/admin/pages">Content by Category</a></li>',
        '<li><a href="/admin/users">Users</a></li>',
        '<li><a href="/admin/categories">Categories</a></li>',
      ];
      sections.push(`<nav><strong>Admin</strong><ul>${adminLinks.join("")}</ul></nav>`);
    }
  }

  // Placeholder for CMS-managed sidebar content
  sections.push('<div id="sidebar-cms"></div>');

  // About and RSS at the bottom
  sections.push('<nav><ul><li><a href="/about">About this site</a></li><li><a href="/feed.xml">RSS Feed</a></li></ul></nav>');

  el.innerHTML = sections.join("");

  // Load CMS sidebar content asynchronously
  loadSidebarContent();
}

/** Fetches posts from the _sidebar category and renders them as nav links */
async function loadSidebarContent() {
  const container = document.getElementById("sidebar-cms");
  if (!container) return;

  try {
    const data = await get("/posts?category=_sidebar");
    const posts = data.posts || [];
    if (posts.length === 0) return;

    const links = posts
      .map((post) => {
        const path = post.slug.includes("/") ? `/posts/${escapeHtml(post.slug)}` : `/pages/${escapeHtml(post.slug)}`;
        return `<li><a href="${path}">${escapeHtml(post.title)}</a></li>`;
      })
      .join("");
    container.innerHTML = `<nav><ul>${links}</ul></nav>`;
  } catch {
    // No sidebar content available - that's fine
  }
}
