/** Cookie Policy page */
export async function render(container) {
  container.innerHTML = `
    <article>
      <h2>Cookie Policy</h2>
      <small>Last updated: February 2026</small>

      <h3>What Cookies We Use</h3>
      <p>This site uses a minimal number of cookies, strictly for functionality:</p>

      <table>
        <thead>
          <tr><th>Cookie</th><th>Purpose</th><th>Duration</th></tr>
        </thead>
        <tbody>
          <tr>
            <td><code>nevet_oidc_origin</code></td>
            <td>Tracks which frontend initiated a third-party login so you are redirected back correctly. Set only during the login flow.</td>
            <td>10 minutes (auto-deleted after login completes)</td>
          </tr>
        </tbody>
      </table>

      <h3>Local Storage</h3>
      <p>We use browser local storage (not cookies) to store your authentication token after login.
      This keeps you signed in between page loads.
      The token expires after 24 hours and is cleared when you log out.</p>

      <h3>No Tracking Cookies</h3>
      <p>We do not currently use any analytics, advertising, or tracking cookies.
      No third-party cookies are set by this site.</p>
      <p>In the future, we may explore sponsorships or other arrangements to help cover hosting costs and compensate contributors.
      If that happens, this policy will be updated before any new cookies are introduced, and we will never sell your personal data.</p>

      <h3>Managing Cookies</h3>
      <p>You can clear cookies and local storage through your browser settings at any time.
      Clearing your local storage will sign you out.</p>
    </article>
  `;
}
