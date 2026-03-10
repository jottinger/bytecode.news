import { get } from "../api.js";
import { escapeHtml } from "../escape.js";
import { renderError } from "../components/error-display.js";

function utcToday() {
  return new Date().toISOString().slice(0, 10);
}

function shiftDay(day, delta) {
  const base = new Date(`${day}T00:00:00.000Z`);
  base.setUTCDate(base.getUTCDate() + delta);
  return base.toISOString().slice(0, 10);
}

function formatTime(value) {
  if (!value) return "";
  return new Date(value).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function contentPreview(item) {
  const sender = item.latestSender ? `${escapeHtml(item.latestSender)} | ` : "";
  const preview = item.latestContentPreview ? escapeHtml(item.latestContentPreview) : "";
  return `${sender}${preview}`;
}

/** Log browser with provenance list + day-based entries */
export async function render(container, params, search) {
  const day = search.get("day") || utcToday();
  let provenance = search.get("provenance");
  const today = utcToday();
  const previousDay = shiftDay(day, -1);
  const nextDay = shiftDay(day, 1);
  const nextDisabled = day >= today;

  try {
    const list = await get("/logs/provenances");
    const provenances = list?.provenances || [];

    if (!provenance && provenances.length > 0) {
      provenance = provenances[0].provenanceUri;
      window.history.replaceState(
        null,
        "",
        `/logs?provenance=${encodeURIComponent(provenance)}&day=${encodeURIComponent(day)}`
      );
    }

    const leftRows =
      provenances.length === 0
        ? "<tr><td colspan='3'>No logs available.</td></tr>"
        : provenances
            .map((item) => {
              const active = item.provenanceUri === provenance;
              const href = `/logs?provenance=${encodeURIComponent(item.provenanceUri)}&day=${encodeURIComponent(day)}`;
              return `
                <tr${active ? " style='background: rgba(255,255,255,0.04)'" : ""}>
                  <td><a href="${href}" class="mono">${escapeHtml(item.provenanceUri)}</a></td>
                  <td class="mono">${item.latestTimestamp ? formatTime(item.latestTimestamp) : ""}</td>
                  <td>${contentPreview(item)}</td>
                </tr>
              `;
            })
            .join("");

    let rightHtml = "<p>Select a provenance to view logs.</p>";
    if (provenance) {
      try {
        const logData = await get(`/logs?provenance=${encodeURIComponent(provenance)}&day=${encodeURIComponent(day)}`);
        const entries = logData?.entries || [];
        if (entries.length === 0) {
          rightHtml = "<p>No entries for this day.</p>";
        } else {
          const rows = entries
            .map((entry) => {
              const ts = entry.timestamp;
              const anchor = `ts-${new Date(ts).getTime()}`;
              const permalink = `/logs?provenance=${encodeURIComponent(provenance)}&day=${encodeURIComponent(day)}#${anchor}`;
              return `
                <tr id="${anchor}">
                  <td class="mono"><a href="${permalink}">${formatTime(ts)}</a></td>
                  <td class="mono">${escapeHtml(entry.sender)}</td>
                  <td>${escapeHtml(entry.content)}</td>
                </tr>
              `;
            })
            .join("");

          rightHtml = `
            <table>
              <thead><tr><th>Time</th><th>Sender</th><th>Content</th></tr></thead>
              <tbody>${rows}</tbody>
            </table>
          `;
        }
      } catch (err) {
        rightHtml = `<div id="logs-error"></div>`;
        container.innerHTML = `
          <h2>Logs</h2>
          <div style="display:grid;grid-template-columns:1fr 2fr;gap:1rem">
            <section>
              <h3>Available Logs</h3>
              <table>
                <thead><tr><th>Provenance</th><th>Time</th><th>Preview</th></tr></thead>
                <tbody>${leftRows}</tbody>
              </table>
            </section>
            <section>
              <h3>${escapeHtml(provenance)}</h3>
              <p>
                <a href="/logs?provenance=${encodeURIComponent(provenance)}&day=${encodeURIComponent(previousDay)}">Previous day</a> |
                ${
                  nextDisabled
                    ? "Next day"
                    : `<a href="/logs?provenance=${encodeURIComponent(provenance)}&day=${encodeURIComponent(nextDay)}">Next day</a>`
                }
                <span class="mono" style="margin-left:0.5rem">${escapeHtml(day)}</span>
              </p>
              <div id="logs-error"></div>
            </section>
          </div>
        `;
        renderError(document.getElementById("logs-error"), err);
        return;
      }
    }

    container.innerHTML = `
      <h2>Logs</h2>
      <div style="display:grid;grid-template-columns:1fr 2fr;gap:1rem">
        <section>
          <h3>Available Logs</h3>
          <table>
            <thead><tr><th>Provenance</th><th>Time</th><th>Preview</th></tr></thead>
            <tbody>${leftRows}</tbody>
          </table>
        </section>
        <section>
          <h3>${provenance ? escapeHtml(provenance) : "No provenance selected"}</h3>
          ${
            provenance
              ? `
            <p>
              <a href="/logs?provenance=${encodeURIComponent(provenance)}&day=${encodeURIComponent(previousDay)}">Previous day</a> |
              ${
                nextDisabled
                  ? "Next day"
                  : `<a href="/logs?provenance=${encodeURIComponent(provenance)}&day=${encodeURIComponent(nextDay)}">Next day</a>`
              }
              <span class="mono" style="margin-left:0.5rem">${escapeHtml(day)}</span>
            </p>
          `
              : ""
          }
          ${rightHtml}
        </section>
      </div>
    `;
  } catch (err) {
    renderError(container, err);
  }
}

