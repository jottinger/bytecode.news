"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ApiError } from "@/lib/api/client";
import { getDayLogs, listLogProvenances } from "@/lib/api/logs";
import {
  LogDayResponse,
  LogProvenanceListResponse,
  LogProvenanceSummary,
} from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

function utcToday(): string {
  return new Date().toISOString().slice(0, 10);
}

function shiftDay(day: string, delta: number): string {
  const base = new Date(`${day}T00:00:00.000Z`);
  base.setUTCDate(base.getUTCDate() + delta);
  return base.toISOString().slice(0, 10);
}

function formatTime(timestamp: string | null): string {
  if (!timestamp) return "";
  return new Date(timestamp).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export default function LogsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const initialProvenance = searchParams.get("provenance");
  const initialDay = searchParams.get("day") ?? utcToday();

  const [listData, setListData] = useState<LogProvenanceListResponse | null>(null);
  const [logsData, setLogsData] = useState<LogDayResponse | null>(null);
  const [selectedProvenance, setSelectedProvenance] = useState<string | null>(
    initialProvenance
  );
  const [day, setDay] = useState(initialDay);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingLogs, setLoadingLogs] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const today = useMemo(() => utcToday(), []);
  const nextDisabled = day >= today;

  const updateQuery = useCallback(
    (provenance: string | null, newDay: string) => {
      const params = new URLSearchParams();
      if (provenance) params.set("provenance", provenance);
      params.set("day", newDay);
      router.replace(`${pathname}?${params.toString()}`);
    },
    [pathname, router]
  );

  useEffect(() => {
    let cancelled = false;
    async function loadProvenances() {
      setLoadingList(true);
      setError(null);
      try {
        const data = await listLogProvenances();
        if (cancelled) return;
        setListData(data);
        if (!selectedProvenance && data.provenances.length > 0) {
          const first = data.provenances[0].provenanceUri;
          setSelectedProvenance(first);
          updateQuery(first, day);
        }
      } catch (e) {
        if (cancelled) return;
        const detail =
          e instanceof ApiError ? e.detail : "Failed to load log provenances.";
        setError(detail);
      } finally {
        if (!cancelled) setLoadingList(false);
      }
    }
    loadProvenances();
    return () => {
      cancelled = true;
    };
  }, [day, selectedProvenance, updateQuery]);

  useEffect(() => {
    if (!selectedProvenance) return;
    let cancelled = false;
    async function loadLogs() {
      setLoadingLogs(true);
      setError(null);
      try {
        const data = await getDayLogs(selectedProvenance, day);
        if (cancelled) return;
        setLogsData(data);
        updateQuery(selectedProvenance, day);
      } catch (e) {
        if (cancelled) return;
        const detail = e instanceof ApiError ? e.detail : "Failed to load logs.";
        setError(detail);
        setLogsData(null);
      } finally {
        if (!cancelled) setLoadingLogs(false);
      }
    }
    loadLogs();
    return () => {
      cancelled = true;
    };
  }, [day, selectedProvenance, updateQuery]);

  const provenances = listData?.provenances ?? [];

  return (
    <div className="container max-w-screen-xl py-8">
      <h1 className="mb-6 text-3xl font-bold">Logs</h1>
      {error && (
        <p className="mb-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_2fr]">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Available Logs</CardTitle>
          </CardHeader>
          <CardContent>
            {loadingList ? (
              <p className="text-sm text-muted-foreground">Loading...</p>
            ) : provenances.length === 0 ? (
              <p className="text-sm text-muted-foreground">No logs available.</p>
            ) : (
              <div className="space-y-2">
                {provenances.map((item: LogProvenanceSummary) => {
                  const active = item.provenanceUri === selectedProvenance;
                  return (
                    <button
                      key={item.provenanceUri}
                      type="button"
                      className={`w-full rounded-md border px-3 py-2 text-left text-sm transition-colors ${
                        active
                          ? "border-primary bg-primary/10"
                          : "border-border hover:bg-muted/50"
                      }`}
                      onClick={() => setSelectedProvenance(item.provenanceUri)}
                    >
                      <div className="font-mono text-xs text-muted-foreground">
                        {item.provenanceUri}
                      </div>
                      <div className="mt-1 grid grid-cols-[70px_1fr] gap-2 text-xs">
                        <div>
                          {item.latestTimestamp ? (
                            <Link
                              href={`${pathname}?provenance=${encodeURIComponent(
                                item.provenanceUri
                              )}&day=${item.latestTimestamp.slice(0, 10)}`}
                              className="text-primary hover:underline"
                              onClick={(event) => event.stopPropagation()}
                            >
                              {formatTime(item.latestTimestamp)}
                            </Link>
                          ) : (
                            ""
                          )}
                        </div>
                        <div className="truncate text-muted-foreground">
                          {item.latestSender ? `${item.latestSender} | ` : ""}
                          {item.latestContentPreview ?? ""}
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <CardTitle className="text-lg">
                {selectedProvenance ?? "Select a provenance"}
              </CardTitle>
              <div className="flex items-center gap-2">
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setDay((value) => shiftDay(value, -1))}
                >
                  Previous day
                </Button>
                <span className="text-sm font-medium">{day}</span>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={nextDisabled}
                  onClick={() => setDay((value) => shiftDay(value, 1))}
                >
                  Next day
                </Button>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            {!selectedProvenance ? (
              <p className="text-sm text-muted-foreground">
                Select a provenance to view logs.
              </p>
            ) : loadingLogs ? (
              <p className="text-sm text-muted-foreground">Loading log entries...</p>
            ) : !logsData || logsData.entries.length === 0 ? (
              <p className="text-sm text-muted-foreground">No entries for this day.</p>
            ) : (
              <div className="space-y-2">
                {logsData.entries.map((entry) => {
                  const ts = entry.timestamp;
                  const anchor = `ts-${new Date(ts).getTime()}`;
                  const link = `${pathname}?provenance=${encodeURIComponent(
                    selectedProvenance
                  )}&day=${day}#${anchor}`;
                  return (
                    <div
                      key={`${entry.timestamp}-${entry.sender}-${entry.content.slice(0, 16)}`}
                      id={anchor}
                      className="grid grid-cols-[90px_120px_1fr] gap-3 rounded border border-border px-3 py-2 text-sm"
                    >
                      <Link href={link} className="font-mono text-primary hover:underline">
                        {formatTime(ts)}
                      </Link>
                      <span className="truncate font-mono">{entry.sender}</span>
                      <span className="whitespace-pre-wrap break-words">{entry.content}</span>
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

