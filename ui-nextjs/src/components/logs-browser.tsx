"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { getAuthState, onAuthChange } from "@/lib/client-auth";
import { LogDayResponse, LogProvenanceSummary } from "@/lib/types";

type ProblemLike = { detail?: string; title?: string; message?: string };

function utcToday(): string {
  return new Date().toISOString().slice(0, 10);
}

function shiftDay(day: string, delta: number): string {
  const base = new Date(`${day}T00:00:00.000Z`);
  base.setUTCDate(base.getUTCDate() + delta);
  return base.toISOString().slice(0, 10);
}

function formatTime(value: string): string {
  return new Date(value).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function formatDay(day: string): string {
  const date = new Date(`${day}T00:00:00.000Z`);
  return date.toLocaleDateString("en-US", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
    timeZone: "UTC",
  });
}

function displayProvenance(uri: string): string {
  return uri.replace(/%23/gi, "#");
}

function channelName(uri: string): string {
  const decoded = displayProvenance(uri);
  const hash = decoded.lastIndexOf("#");
  if (hash >= 0) return decoded.substring(hash);
  const slash = decoded.lastIndexOf("/");
  if (slash >= 0) return decoded.substring(slash + 1);
  return decoded;
}

function detailMessage(payload: unknown, fallback: string): string {
  if (payload && typeof payload === "object") {
    const problem = payload as ProblemLike;
    return problem.detail || problem.message || problem.title || fallback;
  }
  return fallback;
}

function logsHref(provenance: string, day: string): string {
  const params = new URLSearchParams({ provenance, day });
  return `/logs?${params.toString()}`;
}

function permalinkHref(provenance: string, day: string, anchor: string): string {
  return `${logsHref(provenance, day)}#${anchor}`;
}

export function LogsBrowser() {
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams();
  const [authVersion, setAuthVersion] = useState(0);
  const auth = useMemo(() => getAuthState(), [authVersion]);
  const isAdmin = auth.principal?.role === "ADMIN" || auth.principal?.role === "SUPER_ADMIN";
  const [includeAllProtocols, setIncludeAllProtocols] = useState(false);

  const day = search.get("day") || utcToday();
  const provenanceFromUrl = search.get("provenance") || "";

  const [allProvenances, setAllProvenances] = useState<LogProvenanceSummary[]>([]);
  const [loadingProvenances, setLoadingProvenances] = useState(true);
  const [provenanceError, setProvenanceError] = useState<string | null>(null);

  const filteredProvenances = useMemo(
    () =>
      includeAllProtocols && isAdmin
        ? allProvenances
        : allProvenances.filter((item) => item.protocol.toLowerCase() === "irc"),
    [allProvenances, includeAllProtocols, isAdmin],
  );

  const selectedProvenance =
    filteredProvenances.find((item) => item.provenanceUri === provenanceFromUrl)?.provenanceUri ||
    filteredProvenances[0]?.provenanceUri ||
    "";

  const [logData, setLogData] = useState<LogDayResponse | null>(null);
  const [loadingLogs, setLoadingLogs] = useState(false);
  const [logError, setLogError] = useState<string | null>(null);

  useEffect(() => onAuthChange(() => setAuthVersion((v) => v + 1)), []);

  useEffect(() => {
    async function loadProvenances() {
      setLoadingProvenances(true);
      setProvenanceError(null);
      try {
        const response = await fetch("/api/logs/provenances", {
          credentials: "include",
          cache: "no-store",
        });
        const payload = (await response.json()) as unknown;
        if (!response.ok) {
          throw new Error(detailMessage(payload, "Could not load provenances."));
        }
        const provenances = (payload as { provenances?: LogProvenanceSummary[] }).provenances || [];
        setAllProvenances(provenances);
      } catch (error) {
        setProvenanceError(error instanceof Error ? error.message : "Could not load provenances.");
      } finally {
        setLoadingProvenances(false);
      }
    }
    void loadProvenances();
  }, [auth.principal]);

  useEffect(() => {
    if (!selectedProvenance) return;
    if (provenanceFromUrl === selectedProvenance) return;
    const params = new URLSearchParams(search.toString());
    params.set("provenance", selectedProvenance);
    params.set("day", day);
    router.replace(`${pathname}?${params.toString()}`);
  }, [selectedProvenance, provenanceFromUrl, day, router, pathname, search]);

  useEffect(() => {
    if (!selectedProvenance) {
      setLogData(null);
      setLogError(null);
      return;
    }

    async function loadLogs() {
      setLoadingLogs(true);
      setLogError(null);
      try {
        const response = await fetch(
          `/api/logs?provenance=${encodeURIComponent(selectedProvenance)}&day=${encodeURIComponent(day)}`,
          {
            credentials: "include",
            cache: "no-store",
          },
        );
        const payload = (await response.json()) as unknown;
        if (!response.ok) {
          throw new Error(detailMessage(payload, "Could not load logs for this day."));
        }
        setLogData(payload as LogDayResponse);
      } catch (error) {
        setLogData(null);
        setLogError(error instanceof Error ? error.message : "Could not load logs for this day.");
      } finally {
        setLoadingLogs(false);
      }
    }

    void loadLogs();
  }, [selectedProvenance, day, auth.principal]);

  const today = utcToday();
  const previousDay = shiftDay(day, -1);
  const nextDay = shiftDay(day, 1);
  const nextDisabled = day >= today;

  if (provenanceError) {
    return (
      <div className="container max-w-4xl mx-auto py-16 px-6">
        <div className="border-t-2 border-amber pt-6 animate-fade-up">
          <p className="section-label text-amber mb-3">Notice</p>
          <h1 className="font-display text-2xl mb-4">Logs Unavailable</h1>
          <p className="text-muted-foreground">{provenanceError}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="container max-w-screen-xl mx-auto py-10 px-6">
      <div className="animate-fade-up">
        <div className="border-t-2 border-amber pt-6 mb-2 animate-rule-draw" />
        <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-2 mb-8">
          <div>
            <p className="section-label text-amber mb-2">Community</p>
            <h1 className="font-display text-3xl md:text-4xl tracking-tight">
              Chat Logs
            </h1>
          </div>
          <p className="dateline text-muted-foreground/50">
            {formatDay(day)}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-[260px_1fr] gap-8">
        {/* Sidebar: channels */}
        <aside className="animate-fade-up" style={{ animationDelay: "0.1s" }}>
          <p className="section-label text-muted-foreground/60 mb-3">Channels</p>

          {isAdmin ? (
            <label className="flex items-center gap-2 mb-4 font-mono text-xs text-muted-foreground cursor-pointer hover:text-foreground transition-colors">
              <input
                type="checkbox"
                checked={includeAllProtocols}
                onChange={(event) => setIncludeAllProtocols(event.target.checked)}
                className="accent-amber"
              />
              Show all protocols
            </label>
          ) : null}

          {loadingProvenances ? (
            <div className="space-y-2">
              {[1, 2, 3].map((i) => (
                <div
                  key={i}
                  className="h-10 bg-muted/50 animate-pulse"
                />
              ))}
            </div>
          ) : filteredProvenances.length === 0 ? (
            <p className="text-sm text-muted-foreground">No channels available.</p>
          ) : (
            <nav className="space-y-1">
              {filteredProvenances.map((item) => {
                const active = item.provenanceUri === selectedProvenance;
                return (
                  <Link
                    key={item.provenanceUri}
                    href={logsHref(item.provenanceUri, day)}
                    className={`block px-3 py-2.5 font-mono text-xs transition-colors border-l-2 ${
                      active
                        ? "border-amber text-amber bg-amber/5"
                        : "border-transparent text-muted-foreground hover:text-foreground hover:border-border"
                    }`}
                  >
                    <span className="block truncate">
                      {channelName(item.provenanceUri)}
                    </span>
                    {item.latestContentPreview ? (
                      <span className="block text-[0.65rem] text-muted-foreground/50 truncate mt-0.5">
                        {item.latestSender}: {item.latestContentPreview}
                      </span>
                    ) : null}
                  </Link>
                );
              })}
            </nav>
          )}
        </aside>

        {/* Main: log entries */}
        <div className="animate-fade-up min-w-0" style={{ animationDelay: "0.15s" }}>
          {/* Day navigation */}
          {selectedProvenance ? (
            <div className="flex items-center justify-between border-b border-border/40 pb-3 mb-6">
              <Link
                href={logsHref(selectedProvenance, previousDay)}
                className="font-mono text-xs uppercase tracking-widest text-amber hover:text-foreground transition-colors"
              >
                &larr; Previous
              </Link>
              <span className="font-mono text-xs uppercase tracking-wider text-muted-foreground/60">
                {day}
              </span>
              {nextDisabled ? (
                <span className="font-mono text-xs uppercase tracking-widest text-muted-foreground/30">
                  Next &rarr;
                </span>
              ) : (
                <Link
                  href={logsHref(selectedProvenance, nextDay)}
                  className="font-mono text-xs uppercase tracking-widest text-amber hover:text-foreground transition-colors"
                >
                  Next &rarr;
                </Link>
              )}
            </div>
          ) : null}

          {/* Log content */}
          {!selectedProvenance ? (
            <div className="py-12 text-center">
              <p className="text-muted-foreground/50 font-mono text-sm">
                Select a channel to view logs.
              </p>
            </div>
          ) : loadingLogs ? (
            <div className="space-y-3">
              {[1, 2, 3, 4, 5].map((i) => (
                <div key={i} className="flex gap-4">
                  <div className="w-16 h-4 bg-muted/50 animate-pulse shrink-0" />
                  <div className="w-20 h-4 bg-muted/50 animate-pulse shrink-0" />
                  <div className="flex-1 h-4 bg-muted/50 animate-pulse" />
                </div>
              ))}
            </div>
          ) : logError ? (
            <div className="border-l-2 border-destructive pl-4 py-2">
              <p className="text-sm text-destructive">{logError}</p>
            </div>
          ) : !logData || logData.entries.length === 0 ? (
            <div className="py-12 text-center border-t border-border/20">
              <p className="text-muted-foreground/50 font-mono text-sm">
                No entries for this day.
              </p>
            </div>
          ) : (
            <div className="space-y-0">
              {logData.entries.map((entry, index) => {
                const anchor = `ts-${new Date(entry.timestamp).getTime()}`;
                return (
                  <div
                    key={`${entry.timestamp}-${entry.sender}-${entry.content.slice(0, 24)}`}
                    id={anchor}
                    className={`group flex gap-3 py-1.5 px-2 -mx-2 hover:bg-muted/30 transition-colors ${
                      index === 0 ? "" : ""
                    }`}
                  >
                    <Link
                      href={permalinkHref(selectedProvenance, day, anchor)}
                      className="font-mono text-[0.7rem] text-muted-foreground/40 hover:text-amber shrink-0 tabular-nums leading-relaxed transition-colors"
                    >
                      {formatTime(entry.timestamp)}
                    </Link>
                    <span className="font-mono text-[0.7rem] font-medium text-amber/70 shrink-0 min-w-[6rem] truncate leading-relaxed">
                      {entry.sender}
                    </span>
                    <span className="text-sm text-foreground/90 leading-relaxed break-words min-w-0">
                      {entry.content}
                    </span>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
