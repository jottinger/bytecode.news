import { get } from "../api.js";
import { escapeHtml } from "../escape.js";
import { renderError } from "../components/error-display.js";

/** Karma leaderboard view */
export async function render(container, params, search) {
  const limit = parseInt(search?.get("limit") || "10", 10);
  const boundedLimit = Number.isFinite(limit) ? Math.min(Math.max(limit, 1), 100) : 10;

  container.innerHTML = `
    <h2>Community Karma</h2>
    <form id="karma-limit-form">
      <label for="karma-limit">Rows per leaderboard</label>
      <input id="karma-limit" name="limit" type="number" min="1" max="100" value="${boundedLimit}" />
      <button type="submit">Refresh</button>
    </form>
    <div id="karma-board" aria-busy="true">Loading...</div>
  `;

  document.getElementById("karma-limit-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const nextLimit = parseInt(fd.get("limit"), 10);
    const safe = Number.isFinite(nextLimit) ? Math.min(Math.max(nextLimit, 1), 100) : 10;
    window.history.pushState(null, "", `/karma?limit=${safe}`);
    window.dispatchEvent(new PopStateEvent("popstate"));
  });

  const board = document.getElementById("karma-board");
  try {
    const data = await get(`/karma/leaderboard?limit=${boundedLimit}`);
    const top = data?.top || [];
    const bottom = data?.bottom || [];
    if (top.length === 0 && bottom.length === 0) {
      board.innerHTML = "<p>No karma data yet.</p>";
      return;
    }

    board.innerHTML = `
      <article>
        <h3>Top Karma</h3>
        ${renderTable(top)}
      </article>
      <article>
        <h3>Bottom Karma</h3>
        ${renderTable(bottom)}
      </article>
    `;
  } catch (err) {
    renderError(board, err);
  }
}

function renderTable(entries) {
  if (!entries.length) {
    return "<p>No data.</p>";
  }
  const rows = entries
    .map((entry) => {
      const updated = entry.lastUpdated ? escapeHtml(entry.lastUpdated) : "";
      return `<tr>
        <td>${escapeHtml(entry.subject)}</td>
        <td>${entry.score}</td>
        <td>${updated}</td>
      </tr>`;
    })
    .join("");

  return `
    <table role="grid">
      <thead>
        <tr><th>Subject</th><th>Score</th><th>Updated</th></tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
  `;
}
