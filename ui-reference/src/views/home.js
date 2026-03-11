import { get } from "../api.js";
import { renderPostCard } from "../components/post-card.js";
import { renderPagination } from "../components/pagination.js";
import { renderError } from "../components/error-display.js";

/** Published post listing with pagination */
export async function render(container, params, search) {
  const page = parseInt(search.get("page") || "0", 10);

  try {
    const data = await get(`/posts?page=${page}&size=20`);
    if (!data.posts || data.posts.length === 0) {
      container.innerHTML = "<article><p>No posts yet.</p></article>";
      return;
    }
    const cards = data.posts.map(renderPostCard).join("");
    const pagination = renderPagination(data.page, data.totalPages, "/");
    container.innerHTML = `
      <hgroup><h2>Latest Posts</h2><p>${data.totalCount} post${data.totalCount === 1 ? "" : "s"}</p></hgroup>
      ${cards}
      ${pagination}
    `;
  } catch (err) {
    renderError(container, err);
  }
}
