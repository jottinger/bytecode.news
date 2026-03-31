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
      <div className="container max-w-lg mx-auto py-16 px-6">
        <div className="border-t-2 border-amber pt-6 animate-fade-up">
          <p className="section-label text-amber mb-3">Notice</p>
          <h1 className="font-display text-2xl mb-4">Sign In Unavailable</h1>
          <p className="text-muted-foreground">
            No authentication methods are currently available.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="container max-w-lg mx-auto py-16 px-6">
      <div className="animate-fade-up">
        <div className="border-t-2 border-amber pt-6 mb-8 animate-rule-draw" />

        <p className="section-label text-amber mb-3">Subscriber Access</p>
        <h1 className="font-display text-3xl md:text-4xl tracking-tight mb-2">
          Sign In
        </h1>
        <p className="text-muted-foreground/60 text-sm border-b border-border/40 pb-6 mb-8">
          Access your account to submit articles and manage your profile.
        </p>

        {otpEnabled ? (
          <div className="animate-fade-up" style={{ animationDelay: "0.1s" }}>
            <p className="text-muted-foreground text-sm mb-6">
              We&apos;ll send a one-time code from{" "}
              <span className="font-mono text-xs text-foreground/80">{otpFrom}</span>.
              Check your spam folder if needed.
            </p>

            {!requested ? (
              <form onSubmit={requestOtp} className="space-y-4">
                <div>
                  <label
                    htmlFor="email"
                    className="section-label text-muted-foreground block mb-2"
                  >
                    Email Address
                  </label>
                  <input
                    id="email"
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    required
                    placeholder="you@example.com"
                    className="w-full border border-border bg-background text-foreground px-4 py-3 font-sans text-base focus:outline-none focus:border-amber transition-colors"
                  />
                </div>
                <button
                  type="submit"
                  disabled={busy}
                  className="w-full bg-amber text-white font-mono text-xs font-semibold uppercase tracking-widest py-3 px-4 border border-amber hover:bg-amber/90 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {busy ? "Sending..." : "Send Sign-In Code"}
                </button>
              </form>
            ) : (
              <form onSubmit={verifyOtp} className="space-y-4">
                <div>
                  <label
                    htmlFor="code"
                    className="section-label text-muted-foreground block mb-2"
                  >
                    6-Digit Code
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
                    className="w-full border border-border bg-background text-foreground px-4 py-3 font-mono text-2xl text-center tracking-[0.4em] focus:outline-none focus:border-amber transition-colors"
                  />
                </div>
                <button
                  type="submit"
                  disabled={busy}
                  className="w-full bg-amber text-white font-mono text-xs font-semibold uppercase tracking-widest py-3 px-4 border border-amber hover:bg-amber/90 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {busy ? "Verifying..." : "Verify Code"}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setRequested(false);
                    setCode("");
                    setStatus(null);
                    setError(null);
                  }}
                  className="w-full font-mono text-xs text-muted-foreground uppercase tracking-widest py-2 hover:text-amber transition-colors"
                >
                  Use a different email
                </button>
              </form>
            )}
          </div>
        ) : null}

        {status ? (
          <div className="mt-6 border-l-2 border-amber pl-4 py-2 animate-fade-in">
            <p className="text-sm text-foreground">{status}</p>
          </div>
        ) : null}

        {error ? (
          <div className="mt-6 border-l-2 border-destructive pl-4 py-2 animate-fade-in">
            <p className="text-sm text-destructive">{error}</p>
          </div>
        ) : null}

        {hasOidc ? (
          <div
            className="mt-8 pt-6 border-t border-border/40 animate-fade-up"
            style={{ animationDelay: "0.2s" }}
          >
            <p className="section-label text-muted-foreground/60 mb-4">
              Or continue with
            </p>
            <div className="flex gap-3">
              {googleEnabled ? (
                <a
                  href={`/api/oauth2/authorization/google?origin=${encodeURIComponent(origin)}`}
                  className="flex-1 text-center border border-border py-3 px-4 font-mono text-xs font-semibold uppercase tracking-widest text-foreground hover:border-amber hover:text-amber transition-colors"
                >
                  Google
                </a>
              ) : null}
              {githubEnabled ? (
                <a
                  href={`/api/oauth2/authorization/github?origin=${encodeURIComponent(origin)}`}
                  className="flex-1 text-center border border-border py-3 px-4 font-mono text-xs font-semibold uppercase tracking-widest text-foreground hover:border-amber hover:text-amber transition-colors"
                >
                  GitHub
                </a>
              ) : null}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}
