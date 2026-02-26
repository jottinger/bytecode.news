import { get, post as apiPost, del } from "../../api.js";
import { renderError } from "../../components/error-display.js";

/** Admin category management - list, create, delete */
export async function render(container) {
  container.innerHTML = `
    <h2>Manage Categories</h2>
    <form id="create-category-form">
      <div style="display:flex;gap:1rem;align-items:end">
        <div style="flex:1">
          <label for="cat-name">New Category</label>
          <input type="text" id="cat-name" name="name" required placeholder="Category name" />
        </div>
        <div style="flex:1">
          <label for="cat-parent">Parent (optional)</label>
          <select id="cat-parent" name="parentId">
            <option value="">(top-level)</option>
          </select>
        </div>
        <button type="submit">Create</button>
      </div>
    </form>
    <div id="category-status"></div>
    <div id="category-list" aria-busy="true">Loading...</div>
  `;

  await loadCategories();

  document.getElementById("create-category-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const status = document.getElementById("category-status");
    const fd = new FormData(e.target);
    try {
      await apiPost("/admin/categories", {
        name: fd.get("name"),
        parentId: fd.get("parentId") || null,
      });
      await loadCategories();
      e.target.reset();
      status.innerHTML = "<p>Category created.</p>";
    } catch (err) {
      renderError(status, err);
    }
  });
}

async function loadCategories() {
  const listEl = document.getElementById("category-list");
  const parentSelect = document.getElementById("cat-parent");

  try {
    const categories = await get("/categories");

    // Populate parent dropdown
    parentSelect.innerHTML = '<option value="">(top-level)</option>' +
      categories.map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join("");

    if (!categories || categories.length === 0) {
      listEl.innerHTML = "<p>No categories yet.</p>";
      return;
    }

    const rows = categories.map((c) => `
      <tr>
        <td>${escapeHtml(c.name)}</td>
        <td>${escapeHtml(c.parentName || "(top-level)")}</td>
        <td><button class="delete-cat-btn outline secondary" data-id="${c.id}">Delete</button></td>
      </tr>
    `).join("");

    listEl.innerHTML = `
      <table>
        <thead><tr><th>Name</th><th>Parent</th><th>Actions</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    `;

    listEl.querySelectorAll(".delete-cat-btn").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const status = document.getElementById("category-status");
        if (!confirm("Delete this category?")) return;
        try {
          await del(`/admin/categories/${btn.dataset.id}`);
          await loadCategories();
          status.innerHTML = "<p>Category deleted.</p>";
        } catch (err) {
          renderError(status, err);
        }
      });
    });
  } catch (err) {
    renderError(listEl, err);
  }
}

function escapeHtml(str) {
  if (!str) return "";
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}
