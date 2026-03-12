import {
  CommentThreadResponse,
  ContentDetail,
  ContentListResponse,
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
    next: { revalidate: 30 },
  });
}

export async function getPostBySlug(slug: string): Promise<ContentDetail> {
  return apiJson<ContentDetail>(`/posts/${slug}`, {
    next: { revalidate: 30 },
  });
}

export async function getCommentsBySlug(slug: string): Promise<CommentThreadResponse> {
  return apiJson<CommentThreadResponse>(`/posts/${slug}/comments`, {
    next: { revalidate: 30 },
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
