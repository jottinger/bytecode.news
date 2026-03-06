import { setAuth } from "../auth.js";
import { navigate } from "../router.js";

/** Extract JWT from URL fragment after OIDC redirect */
export async function render(container) {
  const hash = window.location.hash;
  const match = hash.match(/token=([^&]+)/);

  if (!match) {
    container.innerHTML = "<article><h2>Authentication failed</h2><p>No token received from provider.</p></article>";
    return;
  }

  const token = match[1];

  try {
    // Decode the JWT payload to get the principal
    const payload = JSON.parse(atob(token.split(".")[1]));
    const principal = {
      id: payload.sub || payload.id,
      username: payload.username,
      displayName: payload.displayName,
      role: payload.role,
    };

    setAuth(token, principal);

    // Clean the URL fragment before navigating away
    window.history.replaceState(null, "", "/");
    navigate("/");
  } catch (err) {
    container.innerHTML = "<article><h2>Authentication failed</h2><p>Could not process authentication token.</p></article>";
  }
}
