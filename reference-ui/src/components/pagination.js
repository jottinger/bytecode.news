/** Render page navigation controls */
export function renderPagination(page, totalPages, basePath) {
  if (totalPages <= 1) return "";

  const links = [];
  if (page > 0) {
    links.push(`<a href="${basePath}?page=${page - 1}" role="button" class="outline">Previous</a>`);
  }
  links.push(`<span>Page ${page + 1} of ${totalPages}</span>`);
  if (page < totalPages - 1) {
    links.push(`<a href="${basePath}?page=${page + 1}" role="button" class="outline">Next</a>`);
  }

  return `<nav style="display:flex;gap:1rem;align-items:center;justify-content:center">${links.join("")}</nav>`;
}
