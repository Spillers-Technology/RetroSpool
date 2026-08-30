import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./client";
import type {
  CaptureView,
  CreateDestinationRequest,
  ExportDestinationView,
  Me,
  ParsedSession,
  Stats,
  SubmissionCreatedResponse,
  SubmissionRequest,
  SubmissionView,
  TenantDetail,
  TenantSummary,
  TestConnectionRequest,
  TestConnectionResult,
  UpdateDestinationRequest,
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

function invalidateDestinationQueries(qc: ReturnType<typeof useQueryClient>, tenantId: string) {
  qc.invalidateQueries({ queryKey: ["tenant", tenantId] });
  qc.invalidateQueries({ queryKey: ["tenants"] });
}

export function useCreateDestination(tenantId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateDestinationRequest) =>
      api.post<ExportDestinationView>(`/api/tenants/${tenantId}/destinations`, request),
    onSuccess: () => invalidateDestinationQueries(qc, tenantId),
  });
}

export function useUpdateDestination(tenantId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateDestinationRequest }) =>
      api.put<ExportDestinationView>(`/api/tenants/${tenantId}/destinations/${id}`, request),
    onSuccess: () => invalidateDestinationQueries(qc, tenantId),
  });
}

export function useSetDestinationEnabled(tenantId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      api.patch<ExportDestinationView>(`/api/tenants/${tenantId}/destinations/${id}/enabled`, { enabled }),
    onSuccess: () => invalidateDestinationQueries(qc, tenantId),
  });
}

export function useDeleteDestination(tenantId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete(`/api/tenants/${tenantId}/destinations/${id}`),
    onSuccess: () => invalidateDestinationQueries(qc, tenantId),
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

// --- Public submission intake (D-007), anonymous surface -----------------------

export function useParseSession() {
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append("file", file);
      return api.postForm<ParsedSession>("/api/submissions/parse", form);
    },
  });
}

export function useCreateSubmission() {
  return useMutation({
    mutationFn: (req: SubmissionRequest) =>
      api.post<SubmissionCreatedResponse>("/api/submissions", req),
  });
}
