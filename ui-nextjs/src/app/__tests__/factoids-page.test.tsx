import { describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";

vi.mock("@/lib/api", () => ({
  listFactoids: vi.fn(),
}));

import FactoidsPage from "@/app/factoids/page";
import { listFactoids } from "@/lib/api";

const listFactoidsMock = vi.mocked(listFactoids);

describe("factoids page", () => {
  it("renders unavailable notice when factoid API fails", async () => {
    listFactoidsMock.mockRejectedValueOnce(new Error("down"));

    const element = await FactoidsPage({ searchParams: Promise.resolve({ page: "0" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("Knowledge Base Unavailable");
  });

  it("renders factoid rows when API succeeds", async () => {
    listFactoidsMock.mockResolvedValueOnce({
      factoids: [
        {
          selector: "mvnd",
          locked: false,
          updatedBy: "dreamreal",
          updatedAt: "2026-03-12T10:00:00Z",
          lastAccessedAt: null,
          accessCount: 7,
          text: "Maven daemon for faster local builds.",
          tags: ["java", "build"],
        },
      ],
      page: 0,
      totalPages: 1,
      totalCount: 1,
    });

    const element = await FactoidsPage({ searchParams: Promise.resolve({ page: "0" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("Knowledge Base");
    expect(html).toContain("mvnd");
    expect(html).toContain("dreamreal");
    expect(html).toContain("7 hits");
    expect(html).toContain("/factoids/mvnd?page=0");
    expect(html).toContain("Maven daemon for faster local builds.");
    expect(html).toContain('/tags/java');
    expect(html).toContain('/tags/build');
  });

  it("preserves query and page in factoid detail links", async () => {
    listFactoidsMock.mockResolvedValueOnce({
      factoids: [
        {
          selector: "mvnd",
          locked: false,
          updatedBy: "dreamreal",
          updatedAt: "2026-03-12T10:00:00Z",
          lastAccessedAt: null,
          accessCount: 7,
          text: "Maven daemon for faster local builds.",
          tags: ["java"],
        },
      ],
      page: 2,
      totalPages: 3,
      totalCount: 1,
    });

    const element = await FactoidsPage({ searchParams: Promise.resolve({ page: "2", q: "java" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("/factoids/mvnd?page=2&amp;q=java");
  });
});
