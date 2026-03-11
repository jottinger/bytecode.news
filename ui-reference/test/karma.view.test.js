import { beforeEach, describe, expect, it, vi } from "vitest";

const getMock = vi.fn();

vi.mock("../src/api.js", () => ({
  get: (...args) => getMock(...args),
}));

describe("karma view", () => {
  beforeEach(() => {
    getMock.mockReset();
    document.body.innerHTML = `<main id="app"></main>`;
  });

  it("omits up/down columns from leaderboard tables", async () => {
    getMock.mockResolvedValue({
      top: [{ subject: "alpha", score: 42, upvotes: 10, downvotes: 2, lastUpdated: "2026-03-09T00:00:00Z" }],
      bottom: [{ subject: "omega", score: -5, upvotes: 1, downvotes: 6, lastUpdated: "2026-03-09T00:00:00Z" }],
    });

    const { render } = await import("../src/views/karma.js");
    const container = document.getElementById("app");
    await render(container, {}, new URLSearchParams("limit=10"));

    expect(container.innerHTML).toContain("<th>Subject</th><th>Score</th><th>Updated</th>");
    expect(container.innerHTML).not.toContain("<th>Up</th>");
    expect(container.innerHTML).not.toContain("<th>Down</th>");
  });
});
