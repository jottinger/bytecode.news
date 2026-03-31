// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor, act } from "@testing-library/react";

const ADMIN_PRINCIPAL = {
  id: "admin-1",
  username: "admin",
  displayName: "Admin User",
  role: "ADMIN" as const,
};

vi.mock("@/lib/client-auth", () => ({
  getAuthState: vi.fn(() => ({
    principal: ADMIN_PRINCIPAL,
  })),
}));

vi.mock("@/lib/format", () => ({
  formatDate: (d: string) => d,
}));

vi.mock("@/components/highlighted-html", () => ({
  HighlightedHtml: ({ html }: { html: string }) => (
    <div dangerouslySetInnerHTML={{ __html: html }} />
  ),
}));

import { AdminPendingPosts } from "../admin-pending-posts";

function pendingResponse(posts: { id: string; title: string }[]) {
  return {
    posts: posts.map((p) => ({
      ...p,
      excerpt: "",
      authorDisplayName: "Author",
      publishedAt: "2026-01-01",
      categories: [],
      tags: [],
    })),
    page: 0,
    totalPages: 1,
    totalCount: posts.length,
  };
}

describe("AdminPendingPosts", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });

  it("fetches pending posts exactly once on mount (no infinite loop)", async () => {
    const body = pendingResponse([{ id: "1", title: "Test Post" }]);
    fetchSpy.mockResolvedValue({
      ok: true,
      json: async () => body,
    });

    render(<AdminPendingPosts />);

    await waitFor(() => {
      expect(screen.getByText("Test Post")).toBeDefined();
    });

    // Wait a tick to catch any extra fetches that would fire in a loop
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50));
    });

    const pendingCalls = fetchSpy.mock.calls.filter((c: unknown[]) =>
      (c[0] as string).includes("/admin/posts/pending"),
    );
    expect(pendingCalls).toHaveLength(1);
  });

  it("does not re-fetch when component re-renders with same auth state", async () => {
    const body = pendingResponse([{ id: "1", title: "Stable Post" }]);
    fetchSpy.mockResolvedValue({
      ok: true,
      json: async () => body,
    });

    const { rerender } = render(<AdminPendingPosts />);

    await waitFor(() => {
      expect(screen.getByText("Stable Post")).toBeDefined();
    });

    // Force re-renders (getAuthState returns new object refs each time)
    rerender(<AdminPendingPosts />);
    rerender(<AdminPendingPosts />);
    rerender(<AdminPendingPosts />);

    await act(async () => {
      await new Promise((r) => setTimeout(r, 50));
    });

    const pendingCalls = fetchSpy.mock.calls.filter((c: unknown[]) =>
      (c[0] as string).includes("/admin/posts/pending"),
    );
    expect(pendingCalls).toHaveLength(1);
  });
});
