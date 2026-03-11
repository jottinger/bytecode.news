/** Privacy Policy page */
export async function render(container) {
  container.innerHTML = `
    <article>
      <h2>Privacy Policy</h2>
      <small>Last updated: February 2026</small>

      <h3>Data We Collect</h3>
      <p>We collect only the minimum data necessary to operate the site:</p>
      <ul>
        <li><strong>Email address</strong> - used for account identification and login (OTP delivery). This is the only personal data we store.</li>
        <li><strong>Display name</strong> - a name you choose, shown alongside your content. You can change it at any time.</li>
        <li><strong>Content you submit</strong> - posts and comments you create.</li>
      </ul>

      <h3>Data We Do Not Collect</h3>
      <ul>
        <li>We do not use tracking cookies or analytics services.</li>
        <li>We do not collect browsing behavior, device fingerprints, or location data.</li>
        <li>We do not sell, share, or provide personal data to third parties.</li>
        <li>We do not store passwords - authentication is passwordless (one-time codes or third-party login).</li>
      </ul>

      <h3>Third-Party Login</h3>
      <p>If you sign in with Google or another OIDC provider, we receive your email address and display name from that provider.
      We do not receive or store your password for that service.
      The provider's own privacy policy governs how they handle your data during the login process.</p>

      <h3>Cookies</h3>
      <p>See our <a href="/cookies">Cookie Policy</a> for details on the cookies we use.</p>

      <h3>Data Retention</h3>
      <p>Your account data is retained as long as your account is active.
      When you erase your account, all personal data (email, display name, login records) is permanently deleted.
      Your submitted content is reassigned to an anonymous placeholder.</p>

      <h3>Your Rights (GDPR)</h3>
      <ul>
        <li><strong>Access</strong> - you can export all your data from the Profile page.</li>
        <li><strong>Rectification</strong> - you can update your display name from the Profile page.</li>
        <li><strong>Erasure</strong> - you can permanently delete your account from the Profile page.</li>
        <li><strong>Portability</strong> - the data export provides your data in a standard JSON format.</li>
      </ul>

      <h3>Contact</h3>
      <p>For privacy-related questions or requests, contact the site administrators.</p>
    </article>
  `;
}
