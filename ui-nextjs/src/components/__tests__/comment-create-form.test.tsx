// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CommentCreateForm } from "@/components/comment-create-form";

const mocks = vi.hoisted(() => ({
  authState: {
    principal: null as { id: string; username: string; displayName: string; role: string } | null,
  },
  refresh: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    refresh: mocks.refresh,
    push: vi.fn(),
  }),
}));

vi.mock("@/lib/client-auth", () => ({
  getAuthState: () => mocks.authState,
}));

afterEach(() => {
  cleanup();
  mocks.refresh.mockReset();
  mocks.authState.principal = null;
  vi.unstubAllGlobals();
});

describe("CommentCreateForm", () => {
  it("posts replies with parentCommentId and refreshes the page", async () => {
    mocks.authState.principal = {
      id: "1",
      username: "testuser",
      displayName: "Test User",
      role: "USER",
    };
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <CommentCreateForm
        year="2026"
        month="03"
        slug="threaded-post"
        parentCommentId="parent-99"
      />,
    );

    fireEvent.change(screen.getByLabelText("Reply"), {
      target: { value: "Nested reply body" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Reply" }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    expect(fetchMock).toHaveBeenCalledWith("/api/posts/2026/03/threaded-post/comments", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({
        markdownSource: "Nested reply body",
        parentCommentId: "parent-99",
      }),
    });
    expect(mocks.refresh).toHaveBeenCalledTimes(1);
  });

  it("renders a sign-in link when no auth token is present", () => {
    render(<CommentCreateForm year="2026" month="03" slug="threaded-post" />);

    const link = screen.getByRole("link", { name: "Sign in to comment" });
    expect(link.getAttribute("href")).toBe("/login?return=/posts/2026/03/threaded-post");
  });
});
