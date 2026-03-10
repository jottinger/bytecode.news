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
    const selectedMeta = provenances.find((item) => item.provenanceUri === provenance);
    const provenanceOptions =
      provenances.length === 0
        ? '<option value="">No logs available</option>'
        : provenances
            .map((item) => {
              const selected = item.provenanceUri === provenance ? " selected" : "";
              return `<option value="${escapeHtml(item.provenanceUri)}"${selected}>${escapeHtml(item.provenanceUri)}</option>`;
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
          <section>
            <p>
              <label for="provenance-select"><strong>Provenance:</strong></label>
              <select id="provenance-select" style="min-width: 30rem; max-width: 100%">
                ${provenanceOptions}
              </select>
            </p>
            <p class="mono">
              Latest: ${selectedMeta?.latestTimestamp ? formatTime(selectedMeta.latestTimestamp) : "n/a"}
              ${selectedMeta?.latestSender ? ` | ${escapeHtml(selectedMeta.latestSender)}` : ""}
            </p>
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
        `;
        const select = container.querySelector("#provenance-select");
        if (select) {
          select.addEventListener("change", (event) => {
            const value = event.target.value;
            if (!value) return;
            window.history.pushState(
              null,
              "",
              `/logs?provenance=${encodeURIComponent(value)}&day=${encodeURIComponent(day)}`
            );
            window.dispatchEvent(new PopStateEvent("popstate"));
          });
        }
        renderError(document.getElementById("logs-error"), err);
        return;
      }
    }

    container.innerHTML = `
      <h2>Logs</h2>
      <section>
        <p>
          <label for="provenance-select"><strong>Provenance:</strong></label>
          <select id="provenance-select" style="min-width: 30rem; max-width: 100%">
            ${provenanceOptions}
          </select>
        </p>
        <p class="mono">
          Latest: ${selectedMeta?.latestTimestamp ? formatTime(selectedMeta.latestTimestamp) : "n/a"}
          ${selectedMeta?.latestSender ? ` | ${escapeHtml(selectedMeta.latestSender)}` : ""}
        </p>
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
    `;

    const select = container.querySelector("#provenance-select");
    if (select) {
      select.addEventListener("change", (event) => {
        const value = event.target.value;
        if (!value) return;
        window.history.pushState(
          null,
          "",
          `/logs?provenance=${encodeURIComponent(value)}&day=${encodeURIComponent(day)}`
        );
        window.dispatchEvent(new PopStateEvent("popstate"));
      });
    }
  } catch (err) {
    renderError(container, err);
  }
}
