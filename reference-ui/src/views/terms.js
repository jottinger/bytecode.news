/** Terms of Service page */
export async function render(container) {
  container.innerHTML = `
    <article>
      <h2>Terms of Service</h2>
      <small>Last updated: February 2026</small>

      <h3>Acceptance</h3>
      <p>By using this site, you agree to these terms.
      If you do not agree, please do not use the site.</p>

      <h3>User Accounts</h3>
      <p>You may create an account using a verified email address or a supported third-party login provider.
      You are responsible for maintaining the security of your account credentials.</p>

      <h3>Content</h3>
      <p>All user-submitted content (posts, comments) is licensed under
      <a href="https://creativecommons.org/licenses/by-sa/4.0/" target="_blank" rel="noopener">Creative Commons CC BY-SA 4.0</a>.
      By submitting content, you grant the site a non-exclusive license to display, distribute, and archive it under those terms.</p>
      <p>You must not submit content that is illegal, defamatory, or infringes on the rights of others.
      The site administrators reserve the right to remove any content at their discretion.</p>

      <h3>Conduct</h3>
      <p>Be respectful. Personal attacks, harassment, and spam are not tolerated and may result in account suspension or erasure.</p>

      <h3>Account Erasure</h3>
      <p>You may delete your account at any time from your profile page.
      When you erase your account, your personal data is permanently deleted.
      Your posts and comments are reassigned to an anonymous placeholder to preserve discussion continuity.</p>

      <h3>Availability</h3>
      <p>The site is provided as-is, with no guarantees of uptime or availability.
      We may modify or discontinue the service at any time.</p>

      <h3>Changes</h3>
      <p>We may update these terms. Continued use of the site after changes constitutes acceptance of the updated terms.</p>

      <h3>Contact</h3>
      <p>For questions about these terms, contact the site administrators.</p>
    </article>
  `;
}
