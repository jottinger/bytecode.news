import { get } from "../api.js";
import { escapeHtml } from "../escape.js";
import { renderError } from "../components/error-display.js";
import { setTitle } from "../title.js";

/** Renders a system page from the _pages category by slug */
export async function render(container, params) {
  try {
    const page = await get(`/pages/${encodeURIComponent(params.slug)}`);
    setTitle(page.title);
    container.innerHTML = `<article><h2>${escapeHtml(page.title)}</h2>${page.renderedHtml}</article>`;
  } catch (err) {
    if (err.status === 404) {
      setTitle("Not found");
      container.innerHTML = `<article><h2>Not found</h2><p>No page at this path.</p></article>`;
    } else {
      renderError(container, err);
    }
  }
}
