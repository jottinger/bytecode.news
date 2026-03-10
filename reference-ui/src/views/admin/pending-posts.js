import { get, put, del } from "../../api.js";
import { escapeHtml } from "../../escape.js";
import { renderError } from "../../components/error-display.js";
import { renderPagination } from "../../components/pagination.js";

/** Admin review queue for draft posts */
export async function render(container, params, search) {
  const page = parseInt(search.get("page") || "0", 10);
  const deleted = search.get("deleted") === "true";
  const modeLabel = deleted ? "Deleted Drafts" : "Pending Posts";

  try {
    const data = await get(`/admin/posts/pending?page=${page}&size=20&deleted=${deleted}`);

    if (!data.posts || data.posts.length === 0) {
      container.innerHTML = `
        <h2>${modeLabel}</h2>
        <p>
          <a href="/admin/posts?deleted=false">Pending</a> |
          <a href="/admin/posts?deleted=true">Deleted</a>
        </p>
        <p>${deleted ? "No soft-deleted drafts." : "No drafts awaiting review."}</p>
      `;
      return;
    }

    const rows = data.posts.map((post) => `
      <tr>
        <td><a href="/edit/${post.id}">${escapeHtml(post.title)}</a></td>
        <td>${escapeHtml(post.authorDisplayName)}</td>
        <td>
          <div style="display:flex;gap:0.5rem">
            ${deleted ? "" : `<button class="approve-btn outline" data-id="${post.id}">Approve</button>`}
            <button class="delete-btn outline secondary" data-id="${post.id}">
              ${deleted ? "Hard Delete" : "Delete"}
            </button>
          </div>
        </td>
      </tr>
    `).join("");

    const pagination = renderPagination(
      data.page,
      data.totalPages,
      `/admin/posts?deleted=${deleted}`
    );

    container.innerHTML = `
      <h2>${modeLabel}</h2>
      <p>
        <a href="/admin/posts?deleted=false">Pending</a> |
        <a href="/admin/posts?deleted=true">Deleted</a>
      </p>
      <p>${data.totalCount} draft${data.totalCount === 1 ? "" : "s"} ${deleted ? "soft-deleted" : "awaiting review"}</p>
      <table>
        <thead><tr><th>Title</th><th>Author</th><th>Actions</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
      ${pagination}
      <div id="admin-status"></div>
    `;

    container.querySelectorAll(".approve-btn").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const status = document.getElementById("admin-status");
        try {
          await put(`/admin/posts/${btn.dataset.id}/approve`, {
            publishedAt: new Date().toISOString(),
          });
          window.dispatchEvent(new PopStateEvent("popstate"));
        } catch (err) {
          renderError(status, err);
        }
      });
    });

    container.querySelectorAll(".delete-btn").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const status = document.getElementById("admin-status");
        const prompt = deleted
          ? "Permanently delete this post? This cannot be undone."
          : "Delete this post?";
        if (!confirm(prompt)) return;
        try {
          await del(`/admin/posts/${btn.dataset.id}?hard=${deleted}`);
          window.dispatchEvent(new PopStateEvent("popstate"));
        } catch (err) {
          renderError(status, err);
        }
      });
    });
  } catch (err) {
    renderError(container, err);
  }
}
