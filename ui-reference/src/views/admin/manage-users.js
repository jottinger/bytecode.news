import { get, put, del } from "../../api.js";
import { escapeHtml, escapeAttr } from "../../escape.js";
import { renderError } from "../../components/error-display.js";

/** Admin user management - filter by status, suspend/unsuspend/erase/purge/role */
export async function render(container, params, search) {
  const status = search.get("status") || "ACTIVE";

  container.innerHTML = `
    <h2>Manage Users</h2>
    <nav style="display:flex;gap:1rem;margin-bottom:1rem">
      <a href="/admin/users?status=ACTIVE" role="button" ${status === "ACTIVE" ? "" : 'class="outline"'}>Active</a>
      <a href="/admin/users?status=SUSPENDED" role="button" ${status === "SUSPENDED" ? "" : 'class="outline"'}>Suspended</a>
      <a href="/admin/users?status=ERASED" role="button" ${status === "ERASED" ? "" : 'class="outline"'}>Erased</a>
    </nav>
    <div id="user-list" aria-busy="true">Loading...</div>
    <div id="admin-status"></div>
  `;

  const listEl = document.getElementById("user-list");

  try {
    const users = await get(`/admin/users?status=${status}`);

    if (!users || users.length === 0) {
      listEl.innerHTML = `<p>No ${status.toLowerCase()} users.</p>`;
      return;
    }

    const rows = users.map((u) => {
      const actions = buildActions(u, status);
      return `<tr>
        <td>${escapeHtml(u.username)}</td>
        <td>${escapeHtml(u.displayName)}</td>
        <td>${escapeHtml(u.role)}</td>
        <td>${actions}</td>
      </tr>`;
    }).join("");

    listEl.innerHTML = `
      <table>
        <thead><tr><th>Username</th><th>Display Name</th><th>Role</th><th>Actions</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    `;

    attachActionHandlers(listEl);
  } catch (err) {
    renderError(listEl, err);
  }
}

function buildActions(user, status) {
  const btns = [];
  if (status === "ACTIVE") {
    btns.push(`<button class="action-btn outline" data-action="suspend" data-username="${escapeAttr(user.username)}">Suspend</button>`);
    btns.push(`<button class="action-btn outline secondary" data-action="erase" data-username="${escapeAttr(user.username)}">Erase</button>`);
    btns.push(`<select class="role-select" data-username="${escapeAttr(user.username)}">
      <option value="">Change role...</option>
      <option value="USER">USER</option>
      <option value="ADMIN">ADMIN</option>
      <option value="SUPER_ADMIN">SUPER_ADMIN</option>
    </select>`);
  } else if (status === "SUSPENDED") {
    btns.push(`<button class="action-btn outline" data-action="unsuspend" data-username="${escapeAttr(user.username)}">Unsuspend</button>`);
    btns.push(`<button class="action-btn outline secondary" data-action="erase" data-username="${escapeAttr(user.username)}">Erase</button>`);
  } else if (status === "ERASED") {
    btns.push(`<button class="action-btn outline secondary" data-action="purge" data-username="${escapeAttr(user.username)}">Purge Content</button>`);
  }
  return `<div style="display:flex;gap:0.5rem;align-items:center">${btns.join("")}</div>`;
}

function attachActionHandlers(container) {
  const statusEl = document.getElementById("admin-status");

  container.querySelectorAll(".action-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const { action, username } = btn.dataset;
      if (action === "erase" || action === "purge") {
        if (!confirm(`${action === "purge" ? "Purge all content for" : "Erase account"} ${username}?`)) return;
      }
      try {
        if (action === "suspend") await put(`/admin/users/${username}/suspend`);
        else if (action === "unsuspend") await put(`/admin/users/${username}/unsuspend`);
        else if (action === "erase") await del(`/admin/users/${username}`);
        else if (action === "purge") await del(`/admin/users/${username}/purge`);
        window.dispatchEvent(new PopStateEvent("popstate"));
      } catch (err) {
        renderError(statusEl, err);
      }
    });
  });

  container.querySelectorAll(".role-select").forEach((select) => {
    select.addEventListener("change", async () => {
      if (!select.value) return;
      const username = select.dataset.username;
      try {
        await put(`/admin/users/${username}/role`, { newRole: select.value });
        window.dispatchEvent(new PopStateEvent("popstate"));
      } catch (err) {
        renderError(statusEl, err);
      }
    });
  });
}

