/**
 * Maps backend operationGroup names to UI slots.
 *
 * To add a new feature view:
 * 1. Ensure the operationGroup appears in /features (backend module on classpath)
 * 2. Create the view file (e.g., src/views/factoids.js) with a render(container, params, search) export
 * 3. Add or update the entry here
 *
 * Only groups with web-browsable content belong here.
 * Chat-only operations (calc, weather, tell, etc.) do not need entries.
 */
export const featureRegistry = {
  factoid: {
    label: "Knowledge Base",
    navPath: "/factoids",
    route: { path: "/factoids", load: () => import("./views/factoids.js") },
    subRoutes: [
      { path: "/factoids/:selector", load: () => import("./views/factoid-detail.js") },
    ],
    sidebar: {
      render: (container) => {
        container.innerHTML = `<p><a href="/factoids">Browse factoids</a></p>`;
      },
    },
  },
  karma: {
    label: "Community",
    navPath: "/karma",
    route: { path: "/karma", load: () => import("./views/karma.js") },
    sidebar: {
      render: (container) => {
        container.innerHTML = `<p><a href="/karma">Karma leaderboard</a></p>`;
      },
    },
  },
};
