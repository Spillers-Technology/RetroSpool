import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./client";
import type {
  CaptureView,
  Me,
  Stats,
  SubmissionView,
  TenantDetail,
  TenantSummary,
  TestConnectionRequest,
  TestConnectionResult,
} from "./types";

export function useMe() {
  return useQuery({ queryKey: ["me"], queryFn: () => api.get<Me>("/api/me"), retry: false });
}

export function useStats() {
  return useQuery({ queryKey: ["stats"], queryFn: () => api.get<Stats>("/api/stats") });
}

export function useSubmissions(status?: string) {
  const query = status ? `?status=${status}` : "";
  return useQuery({
    queryKey: ["submissions", status ?? "all"],
    queryFn: () => api.get<SubmissionView[]>(`/api/submissions${query}`),
  });
}

export function useApproveSubmission() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.post<TenantSummary>(`/api/submissions/${id}/approve`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["submissions"] });
      qc.invalidateQueries({ queryKey: ["tenants"] });
      qc.invalidateQueries({ queryKey: ["stats"] });
    },
  });
}

export function useRejectSubmission() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.post<SubmissionView>(`/api/submissions/${id}/reject`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["submissions"] });
      qc.invalidateQueries({ queryKey: ["stats"] });
    },
  });
}

export function useTenants() {
  return useQuery({ queryKey: ["tenants"], queryFn: () => api.get<TenantSummary[]>("/api/tenants") });
}

export function useTenant(id: string | undefined) {
  return useQuery({
    queryKey: ["tenant", id],
    queryFn: () => api.get<TenantDetail>(`/api/tenants/${id}`),
    enabled: !!id,
  });
}

export function useCaptures(tenantId: string | undefined) {
  return useQuery({
    queryKey: ["captures", tenantId],
    queryFn: () => api.get<CaptureView[]>(`/api/tenants/${tenantId}/captures`),
    enabled: !!tenantId,
  });
}

export function useTestConnection() {
  return useMutation({
    mutationFn: (req: TestConnectionRequest) =>
      api.post<TestConnectionResult>("/api/connection/test", req),
  });
}
