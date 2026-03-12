import { ProblemDetail } from "./types";

export class ApiError extends Error {
  status: number;
  detail: string;
  problemDetail?: ProblemDetail;

  constructor(status: number, detail: string, problemDetail?: ProblemDetail) {
    super(detail);
    this.name = "ApiError";
    this.status = status;
    this.detail = detail;
    this.problemDetail = problemDetail;
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
}

function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("auth_token");
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {}
): Promise<T> {
  const { method = "GET", body, auth = false } = options;

  const headers: Record<string, string> = {
    Accept: "application/json",
  };

  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  if (auth) {
    const token = getToken();
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
  }

  const response = await fetch(`/api${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  if (!response.ok) {
    let problemDetail: ProblemDetail | undefined;
    try {
      const errorBody = await response.json();
      if (errorBody.status && errorBody.detail) {
        problemDetail = errorBody as ProblemDetail;
      }
    } catch {
      // Response body was not JSON
    }

    throw new ApiError(
      response.status,
      problemDetail?.detail || `Request failed with status ${response.status}`,
      problemDetail
    );
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }

  return JSON.parse(text) as T;
}
