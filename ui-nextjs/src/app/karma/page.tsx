import { getKarmaLeaderboard } from "@/lib/api";
import { KarmaLeaderboardResponse } from "@/lib/types";
import { KarmaBoard } from "@/components/karma-board";

export default async function KarmaPage({
  searchParams,
}: {
  searchParams?: Promise<{ limit?: string; view?: string }>;
}) {
  const params = (await searchParams) || {};
  const parsed = Number.parseInt(params.limit || "25", 10);
  const initialView = params.view === "bottom" ? "bottom" : "top";
  const boundedLimit = Number.isFinite(parsed)
    ? Math.min(Math.max(parsed, 1), 100)
    : 25;

  let board: KarmaLeaderboardResponse | null = null;
  try {
    board = await getKarmaLeaderboard(boundedLimit);
  } catch {
    return (
      <section className="py-8 md:py-12">
        <div className="max-w-3xl mx-auto px-6 md:px-8">
          <div className="notice">
            <h2 className="font-display text-xl">Karma Unavailable</h2>
            <p className="text-muted-foreground mt-2">
              The karma service is currently unreachable. Please refresh in a
              moment.
            </p>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="py-8 md:py-12">
      <div className="max-w-3xl mx-auto px-6 md:px-8">
        <header className="mb-10">
          <div className="border-t-2 border-amber animate-rule-draw" />
          <div className="pt-6 pb-8 border-b border-border/40">
            <p className="section-label text-amber mb-3">Community Ledger</p>
            <h1
              className="font-display text-foreground leading-tight"
              style={{
                fontSize: "clamp(2rem, 5vw, 3rem)",
                letterSpacing: "-0.025em",
              }}
            >
              Karma
            </h1>
            <p className="text-muted-foreground/60 text-sm mt-3 leading-relaxed max-w-lg">
              The collective reputation of subjects across every surface where
              Nevet operates — IRC, Discord, Slack, and beyond. Earned one vote
              at a time.
            </p>
          </div>
        </header>

        <form
          action="/karma"
          method="get"
          className="flex gap-3 items-center mb-10"
        >
          <label
            className="section-label text-muted-foreground/60"
            htmlFor="karma-limit"
          >
            Show
          </label>
          <input
            id="karma-limit"
            className="border border-border bg-background text-foreground px-3 py-1.5 text-sm w-16 text-center focus:outline-none focus:border-amber transition-colors"
            style={{
              borderRadius: 0,
              fontFamily: "var(--font-mono)",
              fontVariantNumeric: "tabular-nums",
            }}
            type="number"
            name="limit"
            min={1}
            max={100}
            defaultValue={boundedLimit}
          />
          <label className="section-label text-muted-foreground/60">
            per board
          </label>
          <button
            className="px-4 py-1.5 text-white cursor-pointer text-sm transition-colors hover:opacity-90 ml-1"
            style={{
              background: "var(--amber)",
              border: "1px solid var(--amber)",
              fontFamily: "var(--font-mono)",
              fontSize: "0.75rem",
              fontWeight: 600,
              textTransform: "uppercase",
              letterSpacing: "0.08em",
            }}
            type="submit"
          >
            Update
          </button>
        </form>

        <KarmaBoard top={board.top} bottom={board.bottom} initialView={initialView} />
      </div>
    </section>
  );
}
