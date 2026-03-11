import { get } from "../../api.js";
import { escapeHtml } from "../../escape.js";
import { renderError } from "../../components/error-display.js";
import { renderPagination } from "../../components/pagination.js";

/** Admin content browser: list posts filtered by category */
export async function render(container, params, search) {
  const page = parseInt(search.get("page") || "0", 10);
  const selectedCategory = search.get("category") || "";

  try {
    const categories = await get("/categories");

    const options = categories
      .map(
        (c) =>
          `<option value="${escapeHtml(c.name)}"${c.name === selectedCategory ? " selected" : ""}>${escapeHtml(c.name)}</option>`,
      )
      .join("");

    let postsHtml = "";
    if (selectedCategory) {
      const data = await get(
        `/posts?category=${encodeURIComponent(selectedCategory)}&page=${page}&size=20`,
      );
      const posts = data.posts || [];

      if (posts.length === 0) {
        postsHtml = "<p>No posts in this category.</p>";
      } else {
        const rows = posts
          .map(
            (post) => `
          <tr>
            <td><a href="/edit/${post.id}">${escapeHtml(post.title)}</a></td>
            <td>${escapeHtml(post.authorDisplayName)}</td>
            <td>${escapeHtml(post.slug)}</td>
          </tr>
        `,
          )
          .join("");

        const pagination = renderPagination(
          data.page,
          data.totalPages,
          `/admin/pages?category=${encodeURIComponent(selectedCategory)}`,
        );

        postsHtml = `
          <p>${data.totalCount} post${data.totalCount === 1 ? "" : "s"}</p>
          <table>
            <thead><tr><th>Title</th><th>Author</th><th>Slug</th></tr></thead>
            <tbody>${rows}</tbody>
          </table>
          ${pagination}
        `;
      }
    } else {
      postsHtml = "<p>Select a category to view its posts.</p>";
    }

    container.innerHTML = `
      <h2>Content by Category</h2>
      <label for="category-select">Category</label>
      <select id="category-select">
        <option value="">-- Select category --</option>
        ${options}
      </select>
      ${postsHtml}
    `;

    document.getElementById("category-select").addEventListener("change", (e) => {
      const val = e.target.value;
      if (val) {
        window.history.pushState(null, "", `/admin/pages?category=${encodeURIComponent(val)}`);
      } else {
        window.history.pushState(null, "", "/admin/pages");
      }
      window.dispatchEvent(new PopStateEvent("popstate"));
    });
  } catch (err) {
    renderError(container, err);
  }
}
