import { beforeEach, describe, expect, it, vi } from "vitest";
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
    getPageBySlug: vi.fn(),
    getCommentsBySlug: vi.fn(),
  };
});

import PostPage, { generateMetadata } from "@/app/posts/[...slug]/page";
import { ApiError, getCommentsBySlug, getPageBySlug, getPostBySlug } from "@/lib/api";

const getPostBySlugMock = vi.mocked(getPostBySlug);
const getPageBySlugMock = vi.mocked(getPageBySlug);
const getCommentsBySlugMock = vi.mocked(getCommentsBySlug);

describe("post page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

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

  it("resolves non-dated slug via page endpoint", async () => {
    getPageBySlugMock.mockResolvedValueOnce({
      id: "02",
      title: "About",
      slug: "about-2",
      renderedHtml: "<p>About page</p>",
      excerpt: "",
      authorDisplayName: "dreamreal",
      publishedAt: "2026-03-12T12:00:00Z",
      createdAt: "2026-03-12T12:00:00Z",
      updatedAt: "2026-03-12T12:00:00Z",
      commentCount: 0,
      categories: ["_sidebar"],
      tags: [],
    });

    const element = await PostPage({ params: Promise.resolve({ slug: ["about-2"] }) });
    const html = renderToStaticMarkup(element);

    expect(getPostBySlugMock).not.toHaveBeenCalled();
    expect(getCommentsBySlugMock).not.toHaveBeenCalled();
    expect(html).toContain("About");
    expect(html).not.toContain("Comments (");
  });

  it("loads comments for non-dated non-sidebar page", async () => {
    getPageBySlugMock.mockResolvedValueOnce({
      id: "03",
      title: "Article",
      slug: "article-1",
      renderedHtml: "<p>Article body</p>",
      excerpt: "",
      authorDisplayName: "dreamreal",
      publishedAt: "2026-03-12T12:00:00Z",
      createdAt: "2026-03-12T12:00:00Z",
      updatedAt: "2026-03-12T12:00:00Z",
      commentCount: 0,
      categories: ["_pages"],
      tags: [],
    });
    getCommentsBySlugMock.mockResolvedValueOnce({
      postId: "03",
      comments: [],
      totalActiveCount: 0,
    });

    const element = await PostPage({ params: Promise.resolve({ slug: ["article-1"] }) });
    const html = renderToStaticMarkup(element);

    expect(getCommentsBySlugMock).toHaveBeenCalledWith("article-1");
    expect(html).toContain("No comments yet.");
  });

  it("derives metadata from post content", async () => {
    getPostBySlugMock.mockResolvedValueOnce({
      id: "meta-1",
      title: "Metadata Post",
      slug: "2026/03/metadata-post",
      renderedHtml: "<p>Body</p>",
      excerpt: "Metadata excerpt",
      authorDisplayName: "dreamreal",
      publishedAt: "2026-03-12T12:00:00Z",
      createdAt: "2026-03-12T12:00:00Z",
      updatedAt: "2026-03-12T12:00:00Z",
      commentCount: 0,
      categories: [],
      tags: [],
    });

    const metadata = await generateMetadata({
      params: Promise.resolve({ slug: ["2026", "03", "metadata-post"] }),
    });

    expect(metadata.title).toBe("Metadata Post");
    expect(metadata.description).toBe("Metadata excerpt");
    expect(metadata.openGraph?.title).toBe("Metadata Post");
    expect(metadata.twitter?.title).toBe("Metadata Post");
    expect(metadata.alternates?.canonical).toBe(
      "https://bytecode.news/posts/2026/03/metadata-post",
    );
    expect(metadata.openGraph?.url).toBe("https://bytecode.news/posts/2026/03/metadata-post");
    expect(metadata.openGraph?.images).toEqual([
      {
        url: "https://bytecode.news/opengraph-image",
        alt: "bytecode.news",
      },
    ]);
    expect(metadata.twitter?.images).toEqual([
      {
        url: "https://bytecode.news/twitter-image",
        alt: "bytecode.news",
      },
    ]);
  });

  it("uses fallback metadata when post API is unavailable", async () => {
    getPostBySlugMock.mockRejectedValueOnce(new Error("backend down"));

    const metadata = await generateMetadata({
      params: Promise.resolve({ slug: ["2026", "03", "metadata-post"] }),
    });

    expect(metadata.title).toBe("Post Unavailable");
    expect(metadata.description).toBe("The requested post is temporarily unavailable.");
  });
});
