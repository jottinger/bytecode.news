import { apiRequest } from "./client";
import { FactoidDetail, FactoidSummary, SpringPage } from "./types";

export function listFactoids(params?: {
  q?: string;
  page?: number;
  size?: number;
}): Promise<SpringPage<FactoidSummary>> {
  const searchParams = new URLSearchParams();
  if (params?.q) searchParams.set("q", params.q);
  if (params?.page !== undefined) searchParams.set("page", String(params.page));
  if (params?.size !== undefined) searchParams.set("size", String(params.size));

  const query = searchParams.toString();
  return apiRequest<SpringPage<FactoidSummary>>(
    `/factoids${query ? `?${query}` : ""}`
  );
}

export function getFactoid(selector: string): Promise<FactoidDetail> {
  return apiRequest<FactoidDetail>(`/factoids/${encodeURIComponent(selector)}`);
}
