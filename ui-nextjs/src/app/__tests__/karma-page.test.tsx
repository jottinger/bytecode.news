import { describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";

vi.mock("@/lib/api", () => ({
  getKarmaLeaderboard: vi.fn(),
}));

import KarmaPage from "@/app/karma/page";
import { getKarmaLeaderboard } from "@/lib/api";

const getKarmaLeaderboardMock = vi.mocked(getKarmaLeaderboard);

describe("karma page", () => {
  it("renders unavailable notice when karma API fails", async () => {
    getKarmaLeaderboardMock.mockRejectedValueOnce(new Error("down"));

    const element = await KarmaPage({ searchParams: Promise.resolve({ limit: "10" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("Community Karma Unavailable");
  });

  it("renders leaderboard tables when API succeeds", async () => {
    getKarmaLeaderboardMock.mockResolvedValueOnce({
      top: [{ subject: "java", score: 9, lastUpdated: "2026-03-12" }],
      bottom: [{ subject: "spam", score: -3, lastUpdated: "2026-03-12" }],
      limit: 10,
    });

    const element = await KarmaPage({ searchParams: Promise.resolve({ limit: "10" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("Community Karma");
    expect(html).toContain("Top Karma");
    expect(html).toContain("Bottom Karma");
    expect(html).toContain("java");
    expect(html).toContain("spam");
  });
});
