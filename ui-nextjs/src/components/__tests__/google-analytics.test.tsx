// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { GoogleAnalytics } from "@/components/google-analytics";

vi.mock("next/script", () => ({
  default: ({
    id,
    src,
    children,
  }: {
    id?: string;
    src?: string;
    children?: string;
  }) => <script id={id} src={src}>{children}</script>,
}));

describe("GoogleAnalytics", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("renders the configured Google Analytics tag scripts", () => {
    vi.stubEnv("NEXT_PUBLIC_GA_MEASUREMENT_ID", "G-TEST123");

    const html = renderToStaticMarkup(<GoogleAnalytics />);

    expect(html).toContain('id="google-analytics-loader"');
    expect(html).toContain('src="https://www.googletagmanager.com/gtag/js?id=G-TEST123"');
    expect(html).toContain("window.dataLayer = window.dataLayer || [];");
    expect(html).toContain("gtag('config', 'G-TEST123');");
  });

  it("falls back to the bytecode.news measurement id", () => {
    vi.stubEnv("NEXT_PUBLIC_GA_MEASUREMENT_ID", "");

    const html = renderToStaticMarkup(<GoogleAnalytics />);

    expect(html).toBe("");
  });
});
