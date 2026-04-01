// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { EditPostForm } from "@/components/edit-post-form";

const principal = {
  id: "user-1",
  username: "writer",
  displayName: "Writer",
  role: "USER",
} as const;

vi.mock("@/components/markdown-preview", () => ({
  MarkdownPreview: ({ source }: { source: string }) => <div data-testid="markdown-preview">{source}</div>,
}));

vi.mock("@/lib/client-auth", () => ({
  getAuthState: () => ({
    principal: { ...principal },
  }),
}));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("EditPostForm", () => {
  it("preserves textarea edits after the initial post load", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === "/api/post-by-id/post-123") {
        return {
          ok: true,
          json: async () => ({
            id: "post-123",
            title: "Draft Title",
            markdownSource: "Original body",
            excerpt: "Summary",
            tags: ["draft"],
            categories: ["writing"],
            publishedAt: null,
            sortOrder: 0,
            status: "DRAFT",
            slug: "2026/04/draft-title",
          }),
        } as Response;
      }
      if (url === "/api/categories") {
        return {
          ok: true,
          json: async () => [{ id: "cat-1", name: "writing" }],
        } as Response;
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<EditPostForm postId="post-123" />);

    const textarea = await screen.findByLabelText("Content (Markdown)");
    await waitFor(() => {
      expect((textarea as HTMLTextAreaElement).value).toBe("Original body");
    });

    fireEvent.change(textarea, { target: { value: "Original body plus edits" } });

    await waitFor(() => {
      expect((textarea as HTMLTextAreaElement).value).toBe("Original body plus edits");
    });

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
