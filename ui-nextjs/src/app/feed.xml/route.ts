import { getFeedXml } from "@/lib/api";

export async function GET() {
  try {
    const feed = await getFeedXml();
    return new Response(feed.body, {
      status: 200,
      headers: {
        "Content-Type": feed.contentType,
      },
    });
  } catch {
    return new Response("Feed unavailable", {
      status: 503,
      headers: {
        "Content-Type": "text/plain; charset=utf-8",
      },
    });
  }
}
