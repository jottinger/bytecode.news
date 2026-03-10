import { get } from "../api.js";
import { escapeHtml } from "../escape.js";
import { renderError } from "../components/error-display.js";

/** Single factoid detail view */
export async function render(container, params) {
  const selector = decodeURIComponent(params.selector);

  try {
    const data = await get(`/factoids/${encodeURIComponent(selector)}`);
    const unique = [];
    const seen = new Set();
    for (const attr of data.attributes || []) {
      const type = String(attr?.type || "").toLowerCase();
      if (!type || seen.has(type)) continue;
      seen.add(type);
      unique.push(attr);
    }

    function formatType(type) {
      if (type === "seealso") return "see also";
      return type;
    }

    function detailValue(attr) {
      const type = String(attr?.type || "").toLowerCase();
      const rendered = String(attr?.rendered || "").trim();
      const fallback = escapeHtml(String(attr?.value || ""));
      if (!rendered) return fallback;
      if (type === "tags") {
        return escapeHtml(rendered.replace(/^tags?:\s*/i, ""));
      }
      if (type === "seealso") {
        return escapeHtml(rendered.replace(/^see also:\s*/i, ""));
      }
      return rendered;
    }

    const attrs = unique
      .map((a) => `<dt>${escapeHtml(formatType(String(a.type || "").toLowerCase()))}</dt><dd>${detailValue(a)}</dd>`)
      .join("");

    const date = data.updatedAt ? new Date(data.updatedAt).toLocaleString("en-US") : "";

    container.innerHTML = `
      <article>
        <header>
          <hgroup>
            <h2>${escapeHtml(data.selector)}</h2>
            <p>Updated by ${escapeHtml(data.updatedBy || "unknown")} | ${date} | ${data.accessCount || 0} lookups${data.locked ? " | locked" : ""}</p>
          </hgroup>
        </header>
        <dl>${attrs || "<p>No attributes.</p>"}</dl>
        <footer><a href="/factoids">Back to Knowledge Base</a></footer>
      </article>
    `;
  } catch (err) {
    renderError(container, err);
  }
}
