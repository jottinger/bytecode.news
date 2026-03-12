import { post } from "../api.js";
import { setAuth } from "../auth.js";
import { navigate } from "../router.js";
import { getCachedFeatures } from "../features.js";
import { renderError } from "../components/error-display.js";

/** OTP two-step login + OIDC provider buttons (feature-gated) */
export async function render(container, params, search) {
  const returnPath = search.get("return") || "/";
  const features = getCachedFeatures();

  const hasOtp = !features || features.authentication?.otp !== false;
  const otpFrom = features?.authentication?.otpFrom || "noreply@bytecode.news";
  const hasGoogle = features?.authentication?.oidc?.google === true;
  const hasGithub = features?.authentication?.oidc?.github === true;
  const hasOidc = hasGoogle || hasGithub;

  const otpSection = hasOtp ? `
    <section>
      <h3>Email (passwordless)</h3>
      <form id="otp-request-form">
        <label for="email">Email address</label>
        <input type="email" id="email" name="email" required placeholder="you@example.com" />
        <button type="submit">Send sign-in code</button>
      </form>
      <p><small>Codes are sent from <code>${otpFrom}</code>. Check spam if needed.</small></p>
      <form id="otp-verify-form" hidden>
        <label for="code">Enter the 6-digit code sent to your email</label>
        <input type="text" id="code" name="code" required placeholder="123456" pattern="[0-9]{6}" maxlength="6" inputmode="numeric" />
        <button type="submit">Verify</button>
      </form>
      <div id="otp-status"></div>
    </section>
  ` : "";

  const oidcButtons = [];
  if (hasGoogle) {
    oidcButtons.push(`<a href="/api/oauth2/authorization/google?origin=${encodeURIComponent(window.location.origin)}" role="button" class="outline">Google</a>`);
  }
  if (hasGithub) {
    oidcButtons.push(`<a href="/api/oauth2/authorization/github?origin=${encodeURIComponent(window.location.origin)}" role="button" class="outline">GitHub</a>`);
  }

  const oidcSection = hasOidc ? `
    <hr />
    <section>
      <h3>Or sign in with</h3>
      <div style="display:flex;gap:1rem">
        ${oidcButtons.join("")}
      </div>
    </section>
  ` : "";

  if (!hasOtp && !hasOidc) {
    container.innerHTML = "<article><h2>Sign In</h2><p>No authentication methods are currently available.</p></article>";
    return;
  }

  container.innerHTML = `
    <h2>Sign In</h2>
    ${otpSection}
    ${oidcSection}
  `;

  if (hasOtp) {
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
}
