import { get, put, del } from "../../api.js";
import { renderError } from "../../components/error-display.js";
import { renderPagination } from "../../components/pagination.js";

/** Admin review queue for draft posts */
export async function render(container, params, search) {
  const page = parseInt(search.get("page") || "0", 10);

  try {
    const data = await get(`/admin/posts/pending?page=${page}&size=20`);

    if (!data.posts || data.posts.length === 0) {
      container.innerHTML = "<h2>Pending Posts</h2><p>No drafts awaiting review.</p>";
      return;
    }

    const rows = data.posts.map((post) => `
      <tr>
        <td>${escapeHtml(post.title)}</td>
        <td>${escapeHtml(post.authorDisplayName)}</td>
        <td>
          <div style="display:flex;gap:0.5rem">
            <button class="approve-btn outline" data-id="${post.id}">Approve</button>
            <button class="delete-btn outline secondary" data-id="${post.id}">Delete</button>
          </div>
        </td>
      </tr>
    `).join("");

    const pagination = renderPagination(data.page, data.totalPages, "/admin/posts");

    container.innerHTML = `
      <h2>Pending Posts</h2>
      <p>${data.totalCount} draft${data.totalCount === 1 ? "" : "s"} awaiting review</p>
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
        if (!confirm("Delete this post?")) return;
        try {
          await del(`/admin/posts/${btn.dataset.id}`);
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

function escapeHtml(str) {
  if (!str) return "";
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}
