import { describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";

vi.mock("@/components/logs-browser", () => ({
  LogsBrowser: () => <section>Logs Browser</section>,
}));

import LogsPage from "@/app/logs/page";

describe("logs page", () => {
  it("renders logs browser shell", () => {
    const element = LogsPage();
    const html = renderToStaticMarkup(element);
    expect(html).toContain("Logs Browser");
  });
});
