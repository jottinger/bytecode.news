import { describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";

const notFoundSentinel = new Error("NOT_FOUND");

vi.mock("next/navigation", () => ({
  notFound: () => {
    throw notFoundSentinel;
  },
  useRouter: () => ({
    refresh: vi.fn(),
    push: vi.fn(),
  }),
}));

vi.mock("@/lib/api", () => {
  class ApiError extends Error {
    readonly status: number;
    readonly path: string;

    constructor(status: number, path: string) {
      super(`API ${status} on ${path}`);
      this.status = status;
      this.path = path;
    }
  }

  return {
    ApiError,
    getPostBySlug: vi.fn(),
    getCommentsBySlug: vi.fn(),
  };
});

import PostPage from "@/app/posts/[...slug]/page";
import { ApiError, getCommentsBySlug, getPostBySlug } from "@/lib/api";

const getPostBySlugMock = vi.mocked(getPostBySlug);
const getCommentsBySlugMock = vi.mocked(getCommentsBySlug);

describe("post page", () => {
  it("renders unavailable notice when post API is down", async () => {
    getPostBySlugMock.mockRejectedValueOnce(new ApiError(503, "/posts/a"));

    const element = await PostPage({ params: Promise.resolve({ slug: ["2026", "03", "a"] }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("Post Unavailable");
  });

  it("throws notFound for 404 post", async () => {
    getPostBySlugMock.mockRejectedValueOnce(new ApiError(404, "/posts/missing"));

    await expect(
      PostPage({ params: Promise.resolve({ slug: ["2026", "03", "missing"] }) }),
    ).rejects.toBe(notFoundSentinel);
  });

  it("renders post and comments-unavailable fallback", async () => {
    getPostBySlugMock.mockResolvedValueOnce({
      id: "01",
      title: "Renderable Post",
      slug: "2026/03/renderable-post",
      renderedHtml: "<p>Hello content</p>",
      excerpt: "",
      authorDisplayName: "dreamreal",
      publishedAt: "2026-03-10T12:00:00Z",
      createdAt: "2026-03-10T12:00:00Z",
      updatedAt: "2026-03-10T12:00:00Z",
      commentCount: 0,
      categories: [],
      tags: ["kotlin"],
    });
    getCommentsBySlugMock.mockRejectedValueOnce(new Error("comments down"));

    const element = await PostPage({
      params: Promise.resolve({ slug: ["2026", "03", "renderable-post"] }),
    });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("Renderable Post");
    expect(html).toContain("Hello content");
    expect(html).toContain("Comments are temporarily unavailable");
  });
});
