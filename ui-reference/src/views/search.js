import { get } from "../api.js";
import { escapeAttr } from "../escape.js";
import { renderPostCard } from "../components/post-card.js";
import { renderPagination } from "../components/pagination.js";
import { renderError } from "../components/error-display.js";

/** Full-text search across published posts */
export async function render(container, params, search) {
  const q = search.get("q") || "";
  const page = parseInt(search.get("page") || "0", 10);

  container.innerHTML = `
    <h2>Search Posts</h2>
    <form id="search-form" role="search">
      <input type="search" name="q" placeholder="Search..." value="${escapeAttr(q)}" />
      <button type="submit">Search</button>
    </form>
    <div id="search-results"></div>
  `;

  document.getElementById("search-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const query = formData.get("q");
    window.history.pushState(null, "", "/search?q=" + encodeURIComponent(query));
    window.dispatchEvent(new PopStateEvent("popstate"));
  });

  if (!q) return;

  const results = document.getElementById("search-results");
  results.innerHTML = '<article aria-busy="true">Searching...</article>';

  try {
    const data = await get(`/posts/search?q=${encodeURIComponent(q)}&page=${page}&size=20`);
    if (!data.posts || data.posts.length === 0) {
      results.innerHTML = "<p>No results found.</p>";
      return;
    }
    const cards = data.posts.map(renderPostCard).join("");
    const pagination = renderPagination(data.page, data.totalPages, `/search?q=${encodeURIComponent(q)}`);
    results.innerHTML = `<p>${data.totalCount} result${data.totalCount === 1 ? "" : "s"}</p>${cards}${pagination}`;
  } catch (err) {
    renderError(results, err);
  }
}

