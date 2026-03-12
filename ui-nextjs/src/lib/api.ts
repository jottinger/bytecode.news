import {
  CommentThreadResponse,
  ContentDetail,
  ContentListResponse,
  FactoidDetailResponse,
  FactoidListResponse,
  KarmaLeaderboardResponse,
  LogDayResponse,
  LogProvenanceListResponse,
  FeaturesResponse,
} from "@/lib/types";

export class ApiError extends Error {
  readonly status: number;
  readonly path: string;

  constructor(status: number, path: string) {
    super(`API ${status} on ${path}`);
    this.status = status;
    this.path = path;
  }
}

function backendBaseUrl(): string {
  const explicit = process.env.API_URL?.trim();
  if (explicit) {
    return explicit.endsWith("/") ? explicit.slice(0, -1) : explicit;
  }

  const scheme = process.env.BACKEND_SCHEME?.trim() || "http";
  const host = process.env.BACKEND_HOST?.trim() || "localhost:8080";
  return `${scheme}://${host}`;
}

const ACCEPT_VERSION = process.env.ACCEPT_VERSION?.trim();

async function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers || {});
  if (ACCEPT_VERSION) {
    headers.set("Accept-Version", ACCEPT_VERSION);
  }

  const response = await fetch(`${backendBaseUrl()}${path}`, {
    ...init,
    headers,
  });

  return response;
}

async function apiJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init?.headers || {}),
    },
  });

  if (!response.ok) {
    throw new ApiError(response.status, path);
  }

  return (await response.json()) as T;
}

export async function getFeatures(): Promise<FeaturesResponse> {
  return apiJson<FeaturesResponse>("/features", {
    cache: "no-store",
  });
}

export async function listPosts(page: number, size = 20): Promise<ContentListResponse> {
  return apiJson<ContentListResponse>(`/posts?page=${page}&size=${size}`, {
    cache: "no-store",
  });
}

export async function listPostsByCategory(
  category: string,
  page: number,
  size = 20,
): Promise<ContentListResponse> {
  return apiJson<ContentListResponse>(
    `/posts?category=${encodeURIComponent(category)}&page=${page}&size=${size}`,
    {
      cache: "no-store",
    },
  );
}

export async function getPostBySlug(slug: string): Promise<ContentDetail> {
  return apiJson<ContentDetail>(`/posts/${slug}`, {
    cache: "no-store",
  });
}

export async function getPageBySlug(slug: string): Promise<ContentDetail> {
  return apiJson<ContentDetail>(`/pages/${encodeURIComponent(slug)}`, {
    cache: "no-store",
  });
}

export async function getCommentsBySlug(slug: string): Promise<CommentThreadResponse> {
  return apiJson<CommentThreadResponse>(`/posts/${slug}/comments`, {
    cache: "no-store",
  });
}

export async function getFeedXml(): Promise<{ body: string; contentType: string }> {
  const response = await apiFetch("/feed.xml", {
    headers: {
      Accept: "application/xml,text/xml;q=0.9,*/*;q=0.8",
    },
    next: { revalidate: 30 },
  });

  if (!response.ok) {
    throw new Error(`API ${response.status} on /feed.xml`);
  }

  return {
    body: await response.text(),
    contentType: response.headers.get("content-type") || "application/xml; charset=utf-8",
  };
}

export async function listFactoids(
  page: number,
  size = 20,
  query?: string,
): Promise<FactoidListResponse> {
  const q = query?.trim();
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (q) {
    params.set("q", q);
  }
  return apiJson<FactoidListResponse>(`/factoids?${params.toString()}`, {
    cache: "no-store",
  });
}

export async function getFactoid(selector: string): Promise<FactoidDetailResponse> {
  return apiJson<FactoidDetailResponse>(`/factoids/${encodeURIComponent(selector)}`, {
    cache: "no-store",
  });
}

export async function getKarmaLeaderboard(limit: number): Promise<KarmaLeaderboardResponse> {
  return apiJson<KarmaLeaderboardResponse>(`/karma/leaderboard?limit=${encodeURIComponent(String(limit))}`, {
    cache: "no-store",
  });
}

export async function getLogProvenances(): Promise<LogProvenanceListResponse> {
  return apiJson<LogProvenanceListResponse>("/logs/provenances", {
    cache: "no-store",
  });
}

export async function getLogsDay(provenance: string, day: string): Promise<LogDayResponse> {
  const params = new URLSearchParams({
    provenance,
    day,
  });
  return apiJson<LogDayResponse>(`/logs?${params.toString()}`, {
    cache: "no-store",
  });
}
