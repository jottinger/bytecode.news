import { describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";

vi.mock("@/lib/api", () => ({
  listSearchPosts: vi.fn(),
}));

import SearchPage, { metadata } from "@/app/search/page";
import { listSearchPosts } from "@/lib/api";

const listSearchPostsMock = vi.mocked(listSearchPosts);

describe("search page", () => {
  it("exposes search metadata", () => {
    expect(metadata.title).toBe("Search");
  });

  it("renders prompt state for empty query", async () => {
    const element = await SearchPage({ searchParams: Promise.resolve({ q: "   " }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("Search Posts");
    expect(html).toContain("Enter a search term");
    expect(listSearchPostsMock).not.toHaveBeenCalled();
  });

  it("renders unavailable notice when search API fails", async () => {
    listSearchPostsMock.mockRejectedValueOnce(new Error("down"));

    const element = await SearchPage({ searchParams: Promise.resolve({ q: "java", page: "0" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("Search Temporarily Unavailable");
  });

  it("renders results and pagination links", async () => {
    listSearchPostsMock.mockResolvedValueOnce({
      posts: [
        {
          id: "01",
          title: "JVM Search Result",
          slug: "2026/03/jvm-search-result",
          excerpt: "Search excerpt",
          authorDisplayName: "dreamreal",
          publishedAt: "2026-03-12T12:00:00Z",
          commentCount: 1,
          categories: [],
          tags: ["java"],
        },
      ],
      page: 1,
      totalPages: 3,
      totalCount: 42,
    });

    const element = await SearchPage({ searchParams: Promise.resolve({ q: " java ", page: "1" }) });
    const html = renderToStaticMarkup(element);

    expect(listSearchPostsMock).toHaveBeenCalledWith("java", 1, 20);
    expect(html).toContain("Results for");
    expect(html).toContain("JVM Search Result");
    expect(html).toContain("/search?q=java&amp;page=0");
    expect(html).toContain("/search?q=java&amp;page=2");
  });

  it("renders no-results state", async () => {
    listSearchPostsMock.mockResolvedValueOnce({
      posts: [],
      page: 0,
      totalPages: 0,
      totalCount: 0,
    });

    const element = await SearchPage({ searchParams: Promise.resolve({ q: "notfound", page: "0" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("No results");
    expect(html).toContain('No posts matched');
  });

  it("normalizes invalid page to zero", async () => {
    listSearchPostsMock.mockResolvedValueOnce({
      posts: [],
      page: 0,
      totalPages: 1,
      totalCount: 0,
    });

    await SearchPage({ searchParams: Promise.resolve({ q: "java", page: "-4" }) });

    expect(listSearchPostsMock).toHaveBeenCalledWith("java", 0, 20);
  });

  it("hides previous/next links at pagination bounds", async () => {
    listSearchPostsMock.mockResolvedValueOnce({
      posts: [
        {
          id: "01",
          title: "Only Page Result",
          slug: "2026/03/only-page-result",
          excerpt: "Search excerpt",
          authorDisplayName: "dreamreal",
          publishedAt: "2026-03-12T12:00:00Z",
          commentCount: 0,
          categories: [],
          tags: [],
        },
      ],
      page: 0,
      totalPages: 1,
      totalCount: 1,
    });

    const element = await SearchPage({ searchParams: Promise.resolve({ q: "java", page: "0" }) });
    const html = renderToStaticMarkup(element);

    expect(html).not.toContain("&larr; Previous");
    expect(html).not.toContain("Next &rarr;");
  });
});
