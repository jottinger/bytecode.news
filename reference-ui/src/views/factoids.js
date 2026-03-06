import { get } from "../api.js";
import { escapeHtml } from "../escape.js";
import { renderError } from "../components/error-display.js";

/** Factoid browsing and search */
export async function render(container, params, search) {
  const query = search?.get("q") || "";
  const page = parseInt(search?.get("page") || "0", 10);

  container.innerHTML = `
    <h2>Knowledge Base</h2>
    <form id="factoid-search" role="search">
      <input type="search" name="q" placeholder="Search factoids..." value="${escapeHtml(query)}" />
      <button type="submit">Search</button>
    </form>
    <div id="factoid-list" aria-busy="true">Loading...</div>
  `;

  document.getElementById("factoid-search").addEventListener("submit", (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const q = fd.get("q") || "";
    const params = q ? `?q=${encodeURIComponent(q)}` : "";
    window.history.pushState(null, "", `/factoids${params}`);
    window.dispatchEvent(new PopStateEvent("popstate"));
  });

  try {
    const url = query
      ? `/factoids?q=${encodeURIComponent(query)}&page=${page}&size=20`
      : `/factoids?page=${page}&size=20`;
    const data = await get(url);
    const list = document.getElementById("factoid-list");
    const factoids = data.factoids || [];

    if (factoids.length === 0) {
      list.innerHTML = query
        ? `<p>No factoids matching "${escapeHtml(query)}".</p>`
        : "<p>No factoids yet.</p>";
      return;
    }

    const rows = factoids
      .map((f) => {
        const date = f.updatedAt ? new Date(f.updatedAt).toLocaleDateString("en-US") : "";
        return `<tr>
          <td><a href="/factoids/${encodeURIComponent(f.selector)}">${escapeHtml(f.selector)}</a></td>
          <td>${escapeHtml(f.updatedBy || "")}</td>
          <td>${date}</td>
          <td>${f.accessCount || 0}</td>
        </tr>`;
      })
      .join("");

    const prevDisabled = page <= 0 ? "disabled" : "";
    const nextDisabled = page + 1 >= data.totalPages ? "disabled" : "";
    const pageBase = query ? `/factoids?q=${encodeURIComponent(query)}&` : "/factoids?";

    list.innerHTML = `
      <table role="grid">
        <thead><tr><th>Name</th><th>Updated by</th><th>Updated</th><th>Hits</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <a href="${pageBase}page=${page - 1}" role="button" class="outline" ${prevDisabled}>Previous</a>
        <small>Page ${page + 1} of ${data.totalPages}</small>
        <a href="${pageBase}page=${page + 1}" role="button" class="outline" ${nextDisabled}>Next</a>
      </div>
    `;
  } catch (err) {
    renderError(document.getElementById("factoid-list"), err);
  }
}
