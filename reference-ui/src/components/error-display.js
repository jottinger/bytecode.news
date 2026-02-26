/** Render an RFC 9457 ProblemDetail error into a container */
export function renderError(container, err) {
  const title = err.title || "Error";
  const detail = err.detail || err.message || "An unexpected error occurred.";
  const status = err.status ? ` (${err.status})` : "";
  container.innerHTML = `<article><h3>${escapeHtml(title)}${status}</h3><p>${escapeHtml(detail)}</p></article>`;
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}
