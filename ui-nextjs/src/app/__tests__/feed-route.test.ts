import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api", () => ({
  getFeedXml: vi.fn(),
}));

import { GET } from "@/app/feed.xml/route";
import { getFeedXml } from "@/lib/api";

const getFeedXmlMock = vi.mocked(getFeedXml);

describe("feed.xml route", () => {
  it("returns backend feed body when available", async () => {
    getFeedXmlMock.mockResolvedValueOnce({
      body: "<rss></rss>",
      contentType: "application/xml",
    });

    const response = await GET();
    const body = await response.text();

    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Type")).toContain("application/xml");
    expect(body).toBe("<rss></rss>");
  });

  it("returns 503 fallback when backend feed fails", async () => {
    getFeedXmlMock.mockRejectedValueOnce(new Error("down"));

    const response = await GET();
    const body = await response.text();

    expect(response.status).toBe(503);
    expect(body).toContain("Feed unavailable");
  });
});
