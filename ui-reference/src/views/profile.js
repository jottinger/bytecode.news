import { get, put, del } from "../api.js";
import { getPrincipal, clearAuth, setPrincipal } from "../auth.js";
import { escapeHtml, escapeAttr } from "../escape.js";
import { navigate } from "../router.js";
import { renderError } from "../components/error-display.js";

/** User profile with display name editing, data export, and account erasure */
export async function render(container) {
  const principal = getPrincipal();

  container.innerHTML = `
    <h2>Profile</h2>
    <article>
      <dl>
        <dt>Username</dt><dd>${escapeHtml(principal.username)}</dd>
        <dt>Display Name</dt><dd id="current-display-name">${escapeHtml(principal.displayName)}</dd>
        <dt>Role</dt><dd>${escapeHtml(principal.role)}</dd>
      </dl>
    </article>

    <section>
      <h3>Edit Display Name</h3>
      <form id="profile-form">
        <label for="display-name">Display Name</label>
        <input type="text" id="display-name" name="displayName" required maxlength="100"
               value="${escapeAttr(principal.displayName)}" />
        <button type="submit">Update</button>
      </form>
      <div id="profile-status"></div>
    </section>

    <section>
      <h3>Data Export</h3>
      <p>Download all your data as JSON (GDPR data portability).</p>
      <button id="export-btn">Export My Data</button>
      <div id="export-status"></div>
    </section>

    <section>
      <h3>Delete Account</h3>
      <p>Permanently erase your account. Your posts and comments will be reassigned to an anonymous placeholder. This cannot be undone.</p>
      <details>
        <summary role="button" class="outline secondary">Show account deletion</summary>
        <p>Type <strong>DELETE</strong> to confirm.</p>
        <form id="delete-form">
          <input type="text" id="confirm-delete" name="confirm" required pattern="DELETE" placeholder="Type DELETE" />
          <button type="submit" class="secondary">Permanently Delete My Account</button>
        </form>
        <div id="delete-status"></div>
      </details>
    </section>
  `;

  document.getElementById("profile-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const status = document.getElementById("profile-status");
    const newName = document.getElementById("display-name").value.trim();
    if (!newName) {
      status.innerHTML = "<p>Display name cannot be empty.</p>";
      return;
    }
    try {
      const result = await put("/auth/profile", { displayName: newName });
      const updated = { ...principal, displayName: result.displayName || newName };
      setPrincipal(updated);
      document.getElementById("current-display-name").textContent = updated.displayName;
      status.innerHTML = "<p>Display name updated.</p>";
    } catch (err) {
      renderError(status, err);
    }
  });

  document.getElementById("export-btn").addEventListener("click", async () => {
    const status = document.getElementById("export-status");
    try {
      const data = await get("/auth/export");
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "nevet-export.json";
      a.click();
      URL.revokeObjectURL(url);
      status.innerHTML = "<p>Download started.</p>";
    } catch (err) {
      renderError(status, err);
    }
  });

  document.getElementById("delete-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const status = document.getElementById("delete-status");
    const confirmValue = document.getElementById("confirm-delete").value;
    if (confirmValue !== "DELETE") {
      status.innerHTML = "<p>Please type DELETE to confirm.</p>";
      return;
    }
    try {
      await del("/auth/account", {});
      clearAuth();
      navigate("/");
    } catch (err) {
      renderError(status, err);
    }
  });
}

