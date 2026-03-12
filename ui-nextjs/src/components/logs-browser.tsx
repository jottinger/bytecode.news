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

function displayProvenance(uri: string): string {
  return uri.replace(/%23/gi, "#");
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
  const [logError, setLogError] = useState<string | null>(null);

  useEffect(() => onAuthChange(() => setAuthVersion((v) => v + 1)), []);

  useEffect(() => {
    async function loadProvenances() {
      setLoadingProvenances(true);
      setProvenanceError(null);
      try {
        const response = await fetch("/api/logs/provenances", {
          headers: auth.token ? { Authorization: `Bearer ${auth.token}` } : undefined,
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
  }, [auth.token]);

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
      setLogError(null);
      try {
        const response = await fetch(
          `/api/logs?provenance=${encodeURIComponent(selectedProvenance)}&day=${encodeURIComponent(day)}`,
          {
            headers: auth.token ? { Authorization: `Bearer ${auth.token}` } : undefined,
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
      }
    }

    void loadLogs();
  }, [selectedProvenance, day, auth.token]);

  const today = utcToday();
  const previousDay = shiftDay(day, -1);
  const nextDay = shiftDay(day, 1);
  const nextDisabled = day >= today;
  const selectedMeta = filteredProvenances.find((item) => item.provenanceUri === selectedProvenance);

  if (provenanceError) {
    return (
      <section className="notice">
        <h2>Logs Unavailable</h2>
        <p>{provenanceError}</p>
      </section>
    );
  }

  return (
    <section className="logs-layout">
      <aside className="logs-left story-card">
        <h2 className="story-title">Logs</h2>
        {isAdmin ? (
          <label className="meta logs-protocol-toggle">
            <input
              type="checkbox"
              checked={includeAllProtocols}
              onChange={(event) => setIncludeAllProtocols(event.target.checked)}
            />{" "}
            All protocols
          </label>
        ) : null}
        {loadingProvenances ? (
          <p>Loading...</p>
        ) : filteredProvenances.length === 0 ? (
          <p>No logs available.</p>
        ) : (
          <ul className="logs-provenance-list">
            {filteredProvenances.map((item) => (
              <li key={item.provenanceUri}>
                <Link
                  className={
                    item.provenanceUri === selectedProvenance ? "logs-provenance-link active" : "logs-provenance-link"
                  }
                  href={logsHref(item.provenanceUri, day)}
                >
                  {displayProvenance(item.provenanceUri)}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </aside>

      <article className="logs-right story-card">
        <header className="logs-header">
          <h2 className="story-title">Daily Log View</h2>
          {selectedProvenance ? <p className="meta">{displayProvenance(selectedProvenance)}</p> : null}
          {selectedMeta ? (
            <p className="meta">
              Latest: {selectedMeta.latestTimestamp ? formatTime(selectedMeta.latestTimestamp) : "n/a"}
              {selectedMeta.latestSender ? ` | ${selectedMeta.latestSender}` : ""}
            </p>
          ) : null}
          {selectedProvenance ? (
            <p className="logs-day-controls">
              <Link className="pagelink" href={logsHref(selectedProvenance, previousDay)}>
                Previous day
              </Link>
              <span className="meta">{day}</span>
              {nextDisabled ? (
                <span className="meta">Next day</span>
              ) : (
                <Link className="pagelink" href={logsHref(selectedProvenance, nextDay)}>
                  Next day
                </Link>
              )}
            </p>
          ) : null}
        </header>

        {!selectedProvenance ? (
          <p>Select a provenance to view logs.</p>
        ) : logError ? (
          <p>{logError}</p>
        ) : !logData || logData.entries.length === 0 ? (
          <p>No entries for this day.</p>
        ) : (
          <table className="factoid-table logs-table" role="grid">
            <thead>
              <tr>
                <th>Time</th>
                <th>Sender</th>
                <th>Content</th>
              </tr>
            </thead>
            <tbody>
              {logData.entries.map((entry) => {
                const anchor = `ts-${new Date(entry.timestamp).getTime()}`;
                return (
                  <tr id={anchor} key={`${entry.timestamp}-${entry.sender}-${entry.content.slice(0, 24)}`}>
                    <td className="meta">
                      <Link href={permalinkHref(selectedProvenance, day, anchor)}>
                        {formatTime(entry.timestamp)}
                      </Link>
                    </td>
                    <td className="meta">{entry.sender}</td>
                    <td>{entry.content}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </article>
    </section>
  );
}
