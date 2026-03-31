// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import type { CommentNode } from "@/lib/types";
import { CommentThread } from "@/components/comment-thread";

const mocks = vi.hoisted(() => ({
  authState: {
    principal: null as
      | {
          id: string;
          username: string;
          displayName: string;
          role: "USER" | "ADMIN" | "SUPER_ADMIN";
        }
      | null,
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

vi.mock("@/components/highlighted-html", () => ({
  HighlightedHtml: ({ html, className }: { html: string; className?: string }) => (
    <div className={className} dangerouslySetInnerHTML={{ __html: html }} />
  ),
}));

vi.mock("@/components/comment-create-form", () => ({
  CommentCreateForm: ({
    parentCommentId,
    onCancel,
  }: {
    parentCommentId?: string;
    onCancel?: () => void;
  }) => (
    <div data-testid={`reply-form-${parentCommentId ?? "root"}`}>
      <span>Reply form for {parentCommentId ?? "root"}</span>
      {onCancel ? <button onClick={onCancel}>Cancel reply</button> : null}
    </div>
  ),
}));

vi.mock("@/components/ui/dropdown-menu", () => ({
  DropdownMenu: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DropdownMenuTrigger: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DropdownMenuContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DropdownMenuItem: ({
    children,
    onClick,
    disabled,
    className,
  }: {
    children: React.ReactNode;
    onClick?: () => void;
    disabled?: boolean;
    className?: string;
  }) => (
    <button type="button" onClick={onClick} disabled={disabled} className={className}>
      {children}
    </button>
  ),
}));

afterEach(() => {
  cleanup();
  mocks.refresh.mockReset();
  mocks.authState.principal = null;
});

function makeComment(overrides: Partial<CommentNode> = {}): CommentNode {
  return {
    id: "comment-1",
    authorId: "user-1",
    authorDisplayName: "alice",
    renderedHtml: "<p>Root comment</p>",
    markdownSource: "Root comment",
    createdAt: "2026-03-20T12:00:00Z",
    updatedAt: "2026-03-20T12:00:00Z",
    deleted: false,
    editable: false,
    children: [],
    ...overrides,
  };
}

describe("CommentThread", () => {
  it("renders nested comment threads recursively", () => {
    const comments = [
      makeComment({
        id: "root",
        renderedHtml: "<p>Parent comment</p>",
        children: [
          makeComment({
            id: "child",
            authorId: "user-2",
            authorDisplayName: "bob",
            renderedHtml: "<p>Child reply</p>",
          }),
        ],
      }),
    ];

    render(<CommentThread comments={comments} year="2026" month="03" slug="threaded-post" />);

    expect(screen.getByText("Parent comment")).toBeTruthy();
    expect(screen.getByText("Child reply")).toBeTruthy();
    expect(screen.getByText(/alice at /)).toBeTruthy();
    expect(screen.getByText(/bob at /)).toBeTruthy();
  });

  it("shows a nested reply form when reply is clicked", () => {

    mocks.authState.principal = {
      id: "user-9",
      username: "user9",
      displayName: "User Nine",
      role: "USER",
    };

    render(
      <CommentThread
        comments={[makeComment({ id: "comment-42" })]}
        year="2026"
        month="03"
        slug="threaded-post"
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /reply/i }));

    expect(screen.getByTestId("reply-form-comment-42")).toBeTruthy();
    expect(screen.getByText("Reply form for comment-42")).toBeTruthy();
  });

  it("shows edit for the author and remove for admins", () => {
    const comment = makeComment({
      id: "comment-7",
      authorId: "user-7",
      editable: true,
    });


    mocks.authState.principal = {
      id: "user-7",
      username: "author",
      displayName: "Author",
      role: "USER",
    };

    const { rerender } = render(
      <CommentThread comments={[comment]} year="2026" month="03" slug="threaded-post" />,
    );

    expect(screen.getByRole("button", { name: /reply/i })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Edit comment" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Remove comment" })).toBeNull();

    mocks.authState.principal = {
      id: "admin-1",
      username: "admin",
      displayName: "Admin",
      role: "ADMIN",
    };

    rerender(<CommentThread comments={[comment]} year="2026" month="03" slug="threaded-post" />);

    expect(screen.getByRole("button", { name: "Edit comment" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Remove comment" })).toBeTruthy();
  });

  it("hides interactive controls for unauthenticated users", () => {
    render(
      <CommentThread
        comments={[makeComment({ editable: true })]}
        year="2026"
        month="03"
        slug="threaded-post"
      />,
    );

    const article = screen.getByText("Root comment").closest("article");
    expect(article).toBeTruthy();
    expect(within(article as HTMLElement).queryByRole("button", { name: /reply/i })).toBeNull();
    expect(
      within(article as HTMLElement).queryByRole("button", { name: /comment actions/i }),
    ).toBeNull();
    expect(screen.queryByRole("button", { name: "Edit comment" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Remove comment" })).toBeNull();
  });
});
