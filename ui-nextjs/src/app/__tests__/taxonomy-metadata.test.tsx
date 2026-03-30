import { describe, expect, it, vi } from "vitest";

describe("taxonomy metadata", () => {
  it("adds social metadata for category pages", async () => {
    const module = await import("@/app/category/[name]/page");
    const metadata = await module.generateMetadata({
      params: Promise.resolve({ name: "Java" }),
    });

    expect(metadata.title).toBe("Java");
    expect(metadata.openGraph?.title).toBe("Java");
    expect(metadata.twitter?.title).toBe("Java");
    expect(metadata.alternates?.canonical).toBe("https://bytecode.news/category/Java");
    expect(metadata.openGraph?.url).toBe("https://bytecode.news/category/Java");
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

  it("adds social metadata for tag detail pages", async () => {
    const module = await import("@/app/tags/[name]/page");
    const metadata = await module.generateMetadata({
      params: Promise.resolve({ name: "kotlin" }),
    });

    expect(metadata.title).toBe("kotlin");
    expect(metadata.openGraph?.title).toBe("kotlin");
    expect(metadata.twitter?.title).toBe("kotlin");
    expect(metadata.alternates?.canonical).toBe("https://bytecode.news/tags/kotlin");
    expect(metadata.openGraph?.url).toBe("https://bytecode.news/tags/kotlin");
  });

  it("adds social metadata for the tags index", async () => {
    const module = await import("@/app/tags/page");

    expect(module.metadata.title).toBe("Tags");
    expect(module.metadata.openGraph?.title).toBe("Tags");
    expect(module.metadata.twitter?.title).toBe("Tags");
    expect(module.metadata.alternates?.canonical).toBe("https://bytecode.news/tags");
    expect(module.metadata.openGraph?.url).toBe("https://bytecode.news/tags");
  });
});
