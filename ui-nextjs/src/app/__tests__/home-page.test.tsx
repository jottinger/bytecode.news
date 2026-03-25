import { describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";

vi.mock("@/lib/api", () => ({
  listPosts: vi.fn(),
}));

import Home, { metadata } from "@/app/page";
import { listPosts } from "@/lib/api";

const listPostsMock = vi.mocked(listPosts);

describe("home page", () => {
  it("exposes production homepage metadata", () => {
    expect(metadata.title).toBe("bytecode.news");
    expect(metadata.description).not.toContain("Next.js SSR UI");
  });

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
          commentCount: 3,
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
    expect(html).toContain("3 comments");
    expect(listPostsMock).toHaveBeenCalledWith(0, 9);
  });

  it("links masthead to home and shows comment counts beyond lead card", async () => {
    listPostsMock.mockResolvedValueOnce({
      posts: [
        {
          id: "01",
          title: "Lead",
          slug: "2026/03/lead",
          excerpt: "Lead summary",
          authorDisplayName: "dreamreal",
          publishedAt: "2026-03-10T12:00:00Z",
          commentCount: 1,
          categories: [],
          tags: [],
        },
        {
          id: "02",
          title: "Secondary",
          slug: "2026/03/secondary",
          excerpt: "Secondary summary",
          authorDisplayName: "dreamreal",
          publishedAt: "2026-03-10T13:00:00Z",
          commentCount: 2,
          categories: [],
          tags: [],
        },
        {
          id: "03",
          title: "Brief",
          slug: "2026/03/brief",
          excerpt: "Brief summary",
          authorDisplayName: "dreamreal",
          publishedAt: "2026-03-10T14:00:00Z",
          commentCount: 3,
          categories: [],
          tags: [],
        },
      ],
      page: 0,
      totalPages: 1,
      totalCount: 3,
    });

    const element = await Home({ searchParams: Promise.resolve({ page: "0" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain('aria-label="Go to front page"');
    expect(html).toContain('href="/"');
    expect(html).toContain("1 comment");
    expect(html).toContain("2 comments");
    expect(html).toContain("3 comments");
  });

  it("shows next link when additional pages exist", async () => {
    listPostsMock.mockResolvedValueOnce({
      posts: [
        {
          id: "01",
          title: "Test Article",
          slug: "2026/03/test-article",
          excerpt: "Short summary",
          authorDisplayName: "dreamreal",
          publishedAt: "2026-03-10T12:00:00Z",
          commentCount: 0,
          categories: [],
          tags: [],
        },
      ],
      page: 0,
      totalPages: 2,
      totalCount: 10,
    });

    const element = await Home({ searchParams: Promise.resolve({ page: "0" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("Next");
  });

  it("uses uniform brief layout on page 2+", async () => {
    listPostsMock.mockResolvedValueOnce({
      posts: [
        {
          id: "01",
          title: "Archive Article",
          slug: "2026/03/archive-article",
          excerpt: "Archive summary",
          authorDisplayName: "dreamreal",
          publishedAt: "2026-03-10T12:00:00Z",
          commentCount: 0,
          categories: [],
          tags: [],
        },
      ],
      page: 1,
      totalPages: 3,
      totalCount: 25,
    });

    const element = await Home({ searchParams: Promise.resolve({ page: "1" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("Archive Article");
    expect(html).not.toContain("Read article");
  });
});
