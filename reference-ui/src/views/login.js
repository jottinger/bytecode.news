import { post } from "../api.js";
import { setAuth } from "../auth.js";
import { navigate } from "../router.js";
import { renderError } from "../components/error-display.js";

/** OTP two-step login + OIDC provider buttons */
export async function render(container, params, search) {
  const returnPath = search.get("return") || "/";

  container.innerHTML = `
    <h2>Sign In</h2>
    <section>
      <h3>Email (passwordless)</h3>
      <form id="otp-request-form">
        <label for="email">Email address</label>
        <input type="email" id="email" name="email" required placeholder="you@example.com" />
        <button type="submit">Send sign-in code</button>
      </form>
      <form id="otp-verify-form" hidden>
        <label for="code">Enter the 6-digit code sent to your email</label>
        <input type="text" id="code" name="code" required placeholder="123456" pattern="[0-9]{6}" maxlength="6" inputmode="numeric" />
        <button type="submit">Verify</button>
      </form>
      <div id="otp-status"></div>
    </section>
    <hr />
    <section>
      <h3>Or sign in with</h3>
      <div style="display:flex;gap:1rem">
        <a href="/api/oauth2/authorization/google?origin=${encodeURIComponent(window.location.origin)}" role="button" class="outline">Google</a>
        <a href="/api/oauth2/authorization/github?origin=${encodeURIComponent(window.location.origin)}" role="button" class="outline">GitHub</a>
      </div>
    </section>
  `;

  let email = "";

  document.getElementById("otp-request-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const status = document.getElementById("otp-status");
    email = document.getElementById("email").value;
    try {
      await post("/auth/otp/request", { email });
      document.getElementById("otp-request-form").hidden = true;
      document.getElementById("otp-verify-form").hidden = false;
      status.innerHTML = "<p>Code sent. Check your email.</p>";
    } catch (err) {
      renderError(status, err);
    }
  });

  document.getElementById("otp-verify-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const status = document.getElementById("otp-status");
    const code = document.getElementById("code").value;
    try {
      const data = await post("/auth/otp/verify", { email, code });
      setAuth(data.token, data.principal);
      navigate(returnPath);
    } catch (err) {
      renderError(status, err);
    }
  });
}
