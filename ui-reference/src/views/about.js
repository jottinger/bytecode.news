import { get } from "../api.js";
import { escapeHtml } from "../escape.js";
import { renderError } from "../components/error-display.js";
import { setTitle } from "../title.js";

/** Renders the "about" system page from the _pages category */
export async function render(container) {
  try {
    const page = await get("/pages/about");
    setTitle(page.title);
    container.innerHTML = `<article><h2>${escapeHtml(page.title)}</h2>${page.renderedHtml}</article>`;
  } catch (err) {
    if (err.status === 404) {
      container.innerHTML = `<article><h2>About</h2><p>No about page has been created yet.</p></article>`;
    } else {
      renderError(container, err);
    }
  }
}
