import { describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";

vi.mock("@/lib/api", () => ({
  listPosts: vi.fn(),
}));

import Home from "@/app/page";
import { listPosts } from "@/lib/api";

const listPostsMock = vi.mocked(listPosts);

describe("home page", () => {
  it("renders unavailable notice when posts API fails", async () => {
    listPostsMock.mockRejectedValueOnce(new Error("down"));

    const element = await Home({ searchParams: Promise.resolve({ page: "0" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("Backend Temporarily Unavailable");
  });

  it("renders posts when API succeeds", async () => {
    listPostsMock.mockResolvedValueOnce({
      posts: [
        {
          id: "01",
          title: "Test Article",
          slug: "2026/03/test-article",
          excerpt: "Short summary",
          authorDisplayName: "dreamreal",
          publishedAt: "2026-03-10T12:00:00Z",
          categories: [],
          tags: ["java"],
        },
      ],
      page: 0,
      totalPages: 1,
      totalCount: 1,
    });

    const element = await Home({ searchParams: Promise.resolve({ page: "0" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("Test Article");
    expect(html).toContain("Short summary");
    expect(html).toContain("java");
  });
});
