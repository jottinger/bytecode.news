import { listPosts } from "@/lib/api";
import { ContentSummary } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function OgPreviewPage() {
  let posts: ContentSummary[] = [];
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    const data = await listPosts(0, 5);
    clearTimeout(timeout);
    posts = data.posts;
  } catch {
    /* backend unavailable */
  }

  const cards = [
    {
      title: "bytecode.news",
      description:
        "ByteCode.News: articles, knowledge, and community context.",
      url: "bytecode.news",
    },
    ...posts.map((p) => ({
      title: p.title,
      description: p.excerpt || "Read the latest content on bytecode.news.",
      url: `bytecode.news/posts/${p.slug}`,
    })),
  ];

  const ogImageUrl = "/opengraph-image";

  return (
    <div
      style={{
        maxWidth: 700,
        margin: "40px auto",
        fontFamily: "system-ui, sans-serif",
        padding: "0 20px",
        backgroundColor: "#f7f9fa",
        minHeight: "100vh",
      }}
    >
      <h1 style={{ fontSize: 24, marginBottom: 8, color: "#0f1419" }}>
        Open Graph Card Preview
      </h1>
      <p style={{ color: "#666", marginBottom: 12, fontSize: 14 }}>
        How shared links appear on Twitter/X, LinkedIn, Slack, Discord, etc.
      </p>
      <p style={{ color: "#999", marginBottom: 32, fontSize: 13 }}>
        OG image: <a href={ogImageUrl} style={{ color: "#1d9bf0" }}>{ogImageUrl}</a>
      </p>

      {cards.map((card, i) => (
        <div
          key={i}
          style={{
            border: "1px solid #e0e0e0",
            borderRadius: 12,
            overflow: "hidden",
            marginBottom: 32,
            backgroundColor: "#fff",
            boxShadow: "0 1px 3px rgba(0,0,0,0.08)",
          }}
        >
          <div
            style={{
              width: "100%",
              aspectRatio: "1200/630",
              backgroundColor: "#1a1917",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              position: "relative",
              overflow: "hidden",
            }}
          >
            <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 4, backgroundColor: "#c8912e" }} />
            <div style={{ width: "50%", height: 2, backgroundColor: "#c8912e", marginBottom: 24 }} />
            <div style={{ fontSize: "clamp(32px, 6vw, 56px)", letterSpacing: "-0.04em", color: "#e8e2d6", fontFamily: "Georgia, serif", display: "flex", alignItems: "center", gap: 10 }}>
              <span>bytecode</span>
              <span style={{ color: "#c8912e", fontSize: "0.75em" }}>.</span>
              <span>news</span>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 16 }}>
              <div style={{ width: 40, height: 1, backgroundColor: "#c8912e", opacity: 0.5 }} />
              <span style={{ fontSize: 10, letterSpacing: "0.2em", textTransform: "uppercase", color: "#a09882", fontFamily: "monospace" }}>
                Programming News &amp; Technical Writing
              </span>
              <div style={{ width: 40, height: 1, backgroundColor: "#c8912e", opacity: 0.5 }} />
            </div>
            <div style={{ width: "50%", height: 2, backgroundColor: "#c8912e", marginTop: 24 }} />
            <div style={{ position: "absolute", bottom: 0, left: 0, right: 0, height: 4, backgroundColor: "#c8912e" }} />
          </div>
          <div style={{ padding: "12px 16px" }}>
            <div style={{ fontSize: 13, color: "#8899a6", textTransform: "lowercase" }}>
              {card.url}
            </div>
            <div style={{ fontSize: 15, fontWeight: 700, color: "#0f1419", marginTop: 2, lineHeight: 1.3 }}>
              {card.title}
            </div>
            <div
              style={{
                fontSize: 14,
                color: "#536471",
                marginTop: 2,
                lineHeight: 1.4,
                display: "-webkit-box",
                WebkitLineClamp: 2,
                WebkitBoxOrient: "vertical" as const,
                overflow: "hidden",
              }}
            >
              {card.description}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
