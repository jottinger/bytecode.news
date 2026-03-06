import { get } from "../api.js";
import { escapeHtml } from "../escape.js";
import { renderError } from "../components/error-display.js";

/** Single factoid detail view */
export async function render(container, params) {
  const selector = decodeURIComponent(params.selector);

  try {
    const data = await get(`/factoids/${encodeURIComponent(selector)}`);

    const attrs = (data.attributes || [])
      .map((a) => `<dt>${escapeHtml(a.type)}</dt><dd>${a.rendered || escapeHtml(a.value || "")}</dd>`)
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
