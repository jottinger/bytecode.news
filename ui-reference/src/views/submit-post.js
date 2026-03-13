import { get, post as apiPost } from "../api.js";
import { getPrincipal } from "../auth.js";
import { escapeHtml } from "../escape.js";
import { createEditor } from "../editor.js";
import { renderError } from "../components/error-display.js";
import { navigate } from "../router.js";

function normalizeTags(payload) {
  const raw = payload && typeof payload === "object" && "tags" in payload ? payload.tags : payload;
  if (Array.isArray(raw)) {
    return raw.map((t) => String(t).trim()).filter(Boolean);
  }
  if (raw && typeof raw === "object") {
    return Object.keys(raw).map((t) => String(t).trim()).filter(Boolean);
  }
  if (typeof raw === "string") {
    return raw.split(",").map((t) => t.trim()).filter(Boolean);
  }
  return [];
}

/** Draft post creation form */
export async function render(container) {
  const principal = getPrincipal();
  const isAuthenticated = Boolean(principal);
  let categories = [];
  try {
    categories = await get("/categories");
  } catch {
    // Categories not available - form still works without them
  }

  const categoryOptions = categories
    .map((c) => `<label><input type="checkbox" name="categoryIds" value="${c.id}" /> ${escapeHtml(c.name)}</label>`)
    .join("");

  container.innerHTML = `
    <h2>Submit a Post</h2>
    <form id="submit-form">
      <label for="title">Title</label>
      <input type="text" id="title" name="title" required />

      <label for="markdownSource">Content (Markdown)</label>
      <textarea id="markdownSource" name="markdownSource"></textarea>

      ${isAuthenticated ? `
      <label for="summary">Summary (optional)</label>
      <textarea id="summary" name="summary" rows="4" placeholder="Optional manual summary for feed/front page excerpts."></textarea>
      ` : ""}

      <label for="tags">Tags (comma-separated)</label>
      <input type="text" id="tags" name="tags" placeholder="java, concurrency, loom" />

      ${categoryOptions ? `<fieldset><legend>Categories</legend>${categoryOptions}</fieldset>` : ""}

      <div style="position:absolute;left:-9999px" aria-hidden="true">
        <input type="text" name="website" tabindex="-1" autocomplete="off" />
      </div>

      <div style="display:flex;gap:0.5rem;flex-wrap:wrap">
        ${isAuthenticated ? `<button type="button" id="derive-summary-btn">Generate Summary</button>` : ""}
        <button type="button" id="suggest-tags-btn">Suggest Tags</button>
        <button type="submit">Submit Draft</button>
      </div>
      <div id="submit-status"></div>
    </form>
  `;

  const editor = createEditor(document.getElementById("markdownSource"));
  const formLoadedAt = Date.now();
  const form = document.getElementById("submit-form");
  const tagsInput = document.getElementById("tags");
  const status = document.getElementById("submit-status");
  const summaryInput = document.getElementById("summary");

  if (isAuthenticated) {
    document.getElementById("derive-summary-btn").addEventListener("click", async () => {
      const fd = new FormData(form);
      const markdownSource = editor.value();
      if (!fd.get("title")?.toString().trim()) {
        status.innerHTML = "<p>Title is required.</p>";
        return;
      }
      if (!markdownSource.trim()) {
        status.innerHTML = "<p>Content is required.</p>";
        return;
      }
      try {
        const result = await apiPost("/posts/derive-summary", {
          title: fd.get("title"),
          markdownSource,
        });
        const summary = String(result?.summary || "").trim();
        if (!summary) {
          status.innerHTML = "<p>No summary could be derived.</p>";
          return;
        }
        summaryInput.value = summary;
        status.innerHTML = "<p>Applied heuristic summary to the form. Review before submitting.</p>";
      } catch (err) {
        renderError(status, err);
      }
    });
  }

  document.getElementById("suggest-tags-btn").addEventListener("click", async () => {
    const fd = new FormData(form);
    const markdownSource = editor.value();
    if (!markdownSource.trim()) {
      status.innerHTML = "<p>Content is required.</p>";
      return;
    }

    const existingTags = tagsInput.value
      ? tagsInput.value.split(",").map((t) => t.trim()).filter(Boolean)
      : [];

    try {
      const suggested = await apiPost("/posts/derive-tags", {
        title: fd.get("title"),
        markdownSource,
        existingTags,
      });
      const normalizedTags = normalizeTags(suggested);
      if (normalizedTags.length === 0) {
        status.innerHTML = "<p>No heuristic tags were suggested.</p>";
        return;
      }
      tagsInput.value = normalizedTags.join(", ");
      status.innerHTML = `<p>Applied ${normalizedTags.length} heuristic tag${normalizedTags.length === 1 ? "" : "s"} to the form. Review before submitting.</p>`;
    } catch (err) {
      renderError(status, err);
    }
  });

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);

    const markdownSource = editor.value();
    if (!markdownSource.trim()) {
      status.innerHTML = "<p>Content is required.</p>";
      return;
    }

    const tags = fd.get("tags")
      ? fd.get("tags").split(",").map((t) => t.trim()).filter(Boolean)
      : [];
    const categoryIds = fd.getAll("categoryIds");

    try {
      const result = await apiPost("/posts", {
        title: fd.get("title"),
        markdownSource,
        ...(isAuthenticated ? { summary: fd.get("summary") } : {}),
        tags,
        categoryIds,
        website: fd.get("website"),
        formLoadedAt,
      });
      status.innerHTML = `<p>Draft submitted. It will be visible once approved.</p>`;
    } catch (err) {
      renderError(status, err);
    }
  });
}
