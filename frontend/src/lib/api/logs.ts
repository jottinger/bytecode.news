import { apiRequest } from "./client";
import { LogDayResponse, LogProvenanceListResponse } from "./types";

export function listLogProvenances(): Promise<LogProvenanceListResponse> {
  return apiRequest<LogProvenanceListResponse>("/logs/provenances", { auth: true });
}

export function getDayLogs(
  provenance: string,
  day: string
): Promise<LogDayResponse> {
  const params = new URLSearchParams({ provenance, day });
  return apiRequest<LogDayResponse>(`/logs?${params.toString()}`, { auth: true });
}

