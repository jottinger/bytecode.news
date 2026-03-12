"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { LoginResponse, setAuth } from "@/lib/client-auth";

interface OtpLoginFormProps {
  returnPath: string;
  otpEnabled: boolean;
  otpFrom: string;
  googleEnabled: boolean;
  githubEnabled: boolean;
}

function normalizeReturnPath(path: string): string {
  return path.startsWith("/") ? path : "/";
}

function toErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return "Authentication failed.";
}

export function OtpLoginForm({
  returnPath,
  otpEnabled,
  otpFrom,
  googleEnabled,
  githubEnabled,
}: OtpLoginFormProps) {
  const router = useRouter();
  const normalizedReturnPath = useMemo(() => normalizeReturnPath(returnPath), [returnPath]);
  const origin = typeof window !== "undefined" ? window.location.origin : "";
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [requested, setRequested] = useState(false);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function requestOtp(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setStatus(null);

    try {
      const response = await fetch("/api/auth/otp/request", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });

      if (!response.ok) {
        throw new Error("Could not request sign-in code.");
      }

      setRequested(true);
      setStatus("Code sent. Check your email.");
    } catch (requestError) {
      setError(toErrorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  async function verifyOtp(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setStatus(null);

    try {
      const response = await fetch("/api/auth/otp/verify", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, code }),
      });

      if (!response.ok) {
        throw new Error("Invalid or expired code.");
      }

      const login = (await response.json()) as LoginResponse;
      setAuth(login);
      setStatus("Signed in.");
      router.push(normalizedReturnPath);
      router.refresh();
    } catch (verifyError) {
      setError(toErrorMessage(verifyError));
    } finally {
      setBusy(false);
    }
  }

  const hasOidc = googleEnabled || githubEnabled;

  if (!otpEnabled && !hasOidc) {
    return (
      <section className="notice">
        <h2>Sign In</h2>
        <p>No authentication methods are currently available.</p>
      </section>
    );
  }

  return (
    <section className="auth-shell">
      <h2 className="auth-title">Sign In</h2>

      {otpEnabled ? (
        <>
          <p className="auth-note">
            We send one-time codes from <strong>{otpFrom}</strong>. Check spam if needed.
          </p>
          {!requested ? (
            <form className="auth-form" onSubmit={requestOtp}>
              <label htmlFor="email" className="auth-label">
                Email address
              </label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                required
                placeholder="you@example.com"
                className="auth-input"
              />
              <button type="submit" className="auth-button" disabled={busy}>
                {busy ? "Sending..." : "Send OTP code"}
              </button>
            </form>
          ) : (
            <form className="auth-form" onSubmit={verifyOtp}>
              <label htmlFor="code" className="auth-label">
                6-digit code
              </label>
              <input
                id="code"
                type="text"
                value={code}
                onChange={(event) => setCode(event.target.value)}
                required
                pattern="[0-9]{6}"
                maxLength={6}
                inputMode="numeric"
                placeholder="123456"
                className="auth-input"
              />
              <button type="submit" className="auth-button" disabled={busy}>
                {busy ? "Verifying..." : "Verify code"}
              </button>
            </form>
          )}
        </>
      ) : null}

      {status ? <p className="auth-status">{status}</p> : null}
      {error ? <p className="auth-error">{error}</p> : null}

      {hasOidc ? (
        <section className="auth-oidc">
          <h3 className="auth-subtitle">Or sign in with</h3>
          <div className="auth-oidc-buttons">
            {googleEnabled ? (
              <a
                className="auth-button secondary"
                href={`/api/oauth2/authorization/google?origin=${encodeURIComponent(origin)}`}
              >
                Google
              </a>
            ) : null}
            {githubEnabled ? (
              <a
                className="auth-button secondary"
                href={`/api/oauth2/authorization/github?origin=${encodeURIComponent(origin)}`}
              >
                GitHub
              </a>
            ) : null}
          </div>
        </section>
      ) : null}
    </section>
  );
}
