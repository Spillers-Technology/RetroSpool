// Mirrors io.retrospool.web.AdminDtos + MeController.MeResponse.

export interface Me {
  username: string;
  email: string | null;
  displayName: string;
  groups: string[];
}

export interface Stats {
  tenants: number;
  pendingSubmissions: number;
  captures: number;
  outputQueues: number;
}

export type SubmissionStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface SubmissionView {
  id: string;
  status: SubmissionStatus;
  draft: Record<string, unknown> | null;
  hasIbmiPassword: boolean;
  hasSftpPassword: boolean;
  submittedAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  resultingTenantId: string | null;
}

export interface TenantSummary {
  id: string;
  name: string;
  host: string;
  username: string;
  useSsl: boolean;
  retentionPolicy: string;
  pollIntervalSeconds: number;
  createdAt: string;
  outputQueues: number;
  exportDestinations: number;
  captures: number;
}

export interface OutputQueueView {
  id: string;
  library: string;
  queueName: string;
  retentionPolicy: string | null;
}

export interface ExportDestinationView {
  id: string;
  type: "S3" | "SFTP" | "FTPS";
  name: string;
  config: Record<string, unknown> | null;
  secretSet: boolean;
  enabled: boolean;
}

export type DetectedFormat = "PDF" | "PCL" | "TEXT" | "UNKNOWN";
export type RenderStatus = "SKIPPED" | "SUCCESS" | "FAILED";

export interface CaptureView {
  id: string;
  spoolFileName: string | null;
  spoolJobName: string | null;
  spoolJobUser: string | null;
  detectedFormat: DetectedFormat;
  logicalSegmentIndex: number;
  sha256: string;
  byteSize: number;
  renderStatus: RenderStatus;
  hasRenderedPdf: boolean;
  createdAt: string | null;
  capturedAt: string;
}

export interface AuditEventView {
  id: number;
  eventType: string;
  payload: Record<string, unknown> | null;
  createdAt: string;
}

export interface TenantDetail {
  id: string;
  name: string;
  host: string;
  port: number;
  username: string;
  useSsl: boolean;
  ibmiPasswordSet: boolean;
  printerDeviceName: string | null;
  ccsid: number | null;
  libraryList: string[];
  retentionPolicy: string;
  pollIntervalSeconds: number;
  createdAt: string;
  updatedAt: string;
  outputQueues: OutputQueueView[];
  exportDestinations: ExportDestinationView[];
  recentCaptures: CaptureView[];
  recentAudit: AuditEventView[];
}

// Public submission intake (D-007). Mirrors io.retrospool.submission.ParsedDraft
// and io.retrospool.api.IntakeDtos.

export interface ParsedSession {
  host: string | null;
  port: number | null;
  useSsl: boolean;
  username: string | null;
  name: string | null;
  deviceName: string | null;
  ccsid: number | null;
  sessionType: string | null;
  sourceFormat: string;
  warnings: string[];
}

export interface DraftInput {
  host: string;
  port?: number | null;
  useSsl: boolean;
  username: string;
  name?: string | null;
  deviceName?: string | null;
  ccsid?: number | null;
  sessionType?: string | null;
}

export interface SftpDestinationInput {
  name: string;
  host: string;
  port?: number | null;
  username: string;
  remotePath: string;
  hostKeyFingerprint?: string | null;
  password?: string | null;
}

export interface SubmissionRequest {
  draft: DraftInput;
  ibmiPassword?: string | null;
  sftpDestination?: SftpDestinationInput | null;
}

export interface SubmissionCreatedResponse {
  id: string;
  status: string;
  ibmiPasswordStored: boolean;
  sftpDestinationConfigured: boolean;
}

export interface TestConnectionRequest {
  host: string;
  username: string;
  password: string;
  useSsl: boolean;
}

export interface TestConnectionResult {
  success: boolean;
  code: string;
  message: string;
  elapsedMillis: number;
}
