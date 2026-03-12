import { NextResponse } from "next/server";
import { getBackendBaseUrl } from "@/lib/backend-url";

const SUPPORTED = new Set(["google", "github"]);

export async function GET(
  request: Request,
  context: { params: Promise<{ provider: string }> },
) {
  const { provider } = await context.params;
  if (!SUPPORTED.has(provider)) {
    return new Response("Unsupported provider", { status: 404 });
  }

  const requestUrl = new URL(request.url);
  const origin = requestUrl.searchParams.get("origin") || requestUrl.origin;
  const target = new URL(`${getBackendBaseUrl()}/oauth2/authorization/${provider}`);
  target.searchParams.set("origin", origin);

  return NextResponse.redirect(target);
}
