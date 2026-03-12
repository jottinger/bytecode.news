import { getKarmaLeaderboard } from "@/lib/api";
import { KarmaLeaderboardEntry, KarmaLeaderboardResponse } from "@/lib/types";

function renderRows(entries: KarmaLeaderboardEntry[]) {
  if (entries.length === 0) {
    return (
      <tbody>
        <tr>
          <td colSpan={3}>No data.</td>
        </tr>
      </tbody>
    );
  }

  return (
    <tbody>
      {entries.map((entry) => (
        <tr key={`${entry.subject}-${entry.lastUpdated}`}>
          <td>{entry.subject}</td>
          <td>{entry.score}</td>
          <td>{entry.lastUpdated || ""}</td>
        </tr>
      ))}
    </tbody>
  );
}

export default async function KarmaPage({
  searchParams,
}: {
  searchParams?: Promise<{ limit?: string }>;
}) {
  const params = (await searchParams) || {};
  const parsed = Number.parseInt(params.limit || "10", 10);
  const boundedLimit = Number.isFinite(parsed) ? Math.min(Math.max(parsed, 1), 100) : 10;

  let board: KarmaLeaderboardResponse | null = null;
  try {
    board = await getKarmaLeaderboard(boundedLimit);
  } catch {
    return (
      <section className="notice">
        <h2>Community Karma Unavailable</h2>
        <p>The karma API is currently unreachable. Please refresh in a moment.</p>
      </section>
    );
  }

  const hasData = board.top.length > 0 || board.bottom.length > 0;

  return (
    <section className="story-list">
      <header className="factoid-header">
        <h1>Community Karma</h1>
        <form action="/karma" method="get" className="factoid-search">
          <label className="auth-label" htmlFor="karma-limit">
            Rows per leaderboard
          </label>
          <input
            id="karma-limit"
            className="auth-input"
            type="number"
            name="limit"
            min={1}
            max={100}
            defaultValue={boundedLimit}
          />
          <button className="auth-button" type="submit">
            Refresh
          </button>
        </form>
      </header>

      {!hasData ? (
        <section className="notice">
          <p>No karma data yet.</p>
        </section>
      ) : (
        <div className="karma-grid">
          <article className="story-card">
            <h2 className="story-title">Top Karma</h2>
            <table className="factoid-table" role="grid">
              <thead>
                <tr>
                  <th>Subject</th>
                  <th>Score</th>
                  <th>Updated</th>
                </tr>
              </thead>
              {renderRows(board.top)}
            </table>
          </article>
          <article className="story-card">
            <h2 className="story-title">Bottom Karma</h2>
            <table className="factoid-table" role="grid">
              <thead>
                <tr>
                  <th>Subject</th>
                  <th>Score</th>
                  <th>Updated</th>
                </tr>
              </thead>
              {renderRows(board.bottom)}
            </table>
          </article>
        </div>
      )}
    </section>
  );
}
