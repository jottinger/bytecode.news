"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { KarmaLeaderboardEntry } from "@/lib/types";
import { formatDate } from "@/lib/format";

function ScoreBar({ score, max }: { score: number; max: number }) {
  const pct = max !== 0 ? Math.abs(score) / Math.abs(max) : 0;
  const isPositive = score >= 0;

  return (
    <div className="flex items-center gap-2 min-w-0">
      <span
        className="font-mono text-sm tabular-nums shrink-0"
        style={{
          color: isPositive ? "var(--amber)" : "var(--destructive)",
          fontWeight: 600,
          minWidth: "3.5rem",
          textAlign: "right",
        }}
      >
        {isPositive ? "+" : ""}
        {score}
      </span>
      <div
        className="flex-1 h-1.5 rounded-full overflow-hidden"
        style={{ background: "var(--border)" }}
      >
        <div
          className="h-full rounded-full transition-all duration-500"
          style={{
            width: `${Math.max(pct * 100, 2)}%`,
            background: isPositive ? "var(--amber)" : "var(--destructive)",
            opacity: 0.7 + pct * 0.3,
          }}
        />
      </div>
    </div>
  );
}

export function KarmaBoard({
  top,
  bottom,
  initialView,
}: {
  top: KarmaLeaderboardEntry[];
  bottom: KarmaLeaderboardEntry[];
  initialView: "top" | "bottom";
}) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [view, setView] = useState<"top" | "bottom">(initialView);

  function switchView(v: "top" | "bottom") {
    setView(v);
    const params = new URLSearchParams(searchParams.toString());
    params.set("view", v);
    router.replace(`/karma?${params.toString()}`, { scroll: false });
  }
  const entries =
    view === "top"
      ? top.filter((e) => e.score >= 0)
      : bottom.filter((e) => e.score <= 0);
  const maxScore =
    entries.length > 0
      ? Math.max(...entries.map((e) => Math.abs(e.score)))
      : 1;

  return (
    <div>
      <div className="flex gap-0 mb-8">
        <button
          onClick={() => switchView("top")}
          className="px-4 py-2 cursor-pointer text-sm transition-colors"
          style={{
            fontFamily: "var(--font-mono)",
            fontSize: "0.75rem",
            fontWeight: 600,
            textTransform: "uppercase",
            letterSpacing: "0.08em",
            background: view === "top" ? "var(--amber)" : "transparent",
            color: view === "top" ? "white" : "var(--amber)",
            border: "1px solid var(--amber)",
            borderRight: "none",
          }}
        >
          Top Karma
        </button>
        <button
          onClick={() => switchView("bottom")}
          className="px-4 py-2 cursor-pointer text-sm transition-colors"
          style={{
            fontFamily: "var(--font-mono)",
            fontSize: "0.75rem",
            fontWeight: 600,
            textTransform: "uppercase",
            letterSpacing: "0.08em",
            background: view === "bottom" ? "var(--amber)" : "transparent",
            color: view === "bottom" ? "white" : "var(--amber)",
            border: "1px solid var(--amber)",
          }}
        >
          Bottom Karma
        </button>
      </div>

      {entries.length === 0 ? (
        <div className="py-12 text-center">
          <p className="section-label text-muted-foreground/50 mb-3">
            No karma recorded yet
          </p>
          <p className="text-muted-foreground/40 text-sm">
            Karma accumulates as the community votes across all connected
            channels.
          </p>
        </div>
      ) : (
        <div className="animate-fade-in" key={view}>
          {entries.map((entry, i) => (
            <div
              key={`${entry.subject}-${entry.lastUpdated}`}
              className="animate-fade-up"
              style={{ animationDelay: `${i * 0.04}s` }}
            >
              <div
                className={`py-3.5 ${i > 0 ? "border-t border-border/30" : ""}`}
              >
                <div className="flex items-baseline justify-between gap-4 mb-1.5">
                  <div className="flex items-baseline gap-3 min-w-0">
                    <span
                      className="text-muted-foreground/30 font-mono text-xs tabular-nums shrink-0"
                      style={{ minWidth: "1.5rem", textAlign: "right" }}
                    >
                      {i + 1}
                    </span>
                    <h3 className="headline-brief text-foreground min-w-0 truncate">
                      {entry.subject}
                    </h3>
                  </div>
                  <time className="byline text-muted-foreground/40 shrink-0">
                    {formatDate(entry.lastUpdated)}
                  </time>
                </div>
                <div className="pl-[calc(1.5rem+0.75rem)]">
                  <ScoreBar score={entry.score} max={maxScore} />
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
