import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useApproveSubmission, useRejectSubmission, useSubmissions } from "../api/hooks";
import type { SubmissionStatus, SubmissionView } from "../api/types";
import PageHeader from "../components/PageHeader";
import { Badge, Button, Card, EmptyState, ErrorState, Spinner, formatDate } from "../components/ui";

const FILTERS: Array<{ label: string; value: "ALL" | SubmissionStatus }> = [
  { label: "All", value: "ALL" },
  { label: "Pending", value: "PENDING" },
  { label: "Approved", value: "APPROVED" },
  { label: "Rejected", value: "REJECTED" },
];

export default function SubmissionsPage() {
  const [params, setParams] = useSearchParams();
  const requested = params.get("status")?.toUpperCase();
  const filter = FILTERS.some((item) => item.value === requested) ? requested! : "ALL";
  const submissions = useSubmissions(filter === "ALL" ? undefined : filter);
  const approve = useApproveSubmission();
  const reject = useRejectSubmission();
  const [openId, setOpenId] = useState<string | null>(null);

  const activeMutationId = useMemo(() => {
    const variables = approve.isPending ? approve.variables : reject.isPending ? reject.variables : undefined;
    return typeof variables === "string" ? variables : null;
  }, [approve.isPending, approve.variables, reject.isPending, reject.variables]);

  function setFilter(next: string) {
    setParams(next === "ALL" ? {} : { status: next });
  }

  function approveSubmission(item: SubmissionView) {
    const host = draftText(item.draft, "host") ?? "the submitted host";
    if (window.confirm(`Approve ${host} and create an active tenant?`)) approve.mutate(item.id);
  }

  function rejectSubmission(item: SubmissionView) {
    const host = draftText(item.draft, "host") ?? "this submission";
    if (window.confirm(`Reject ${host}? This decision will be recorded in the audit log.`)) reject.mutate(item.id);
  }

  return (
    <>
      <PageHeader
        eyebrow="Human approval gate"
        title="Submissions"
        description="Landing-page imports remain inert until an operator reviews their parsed settings and explicitly approves or rejects them."
      />

      <div className="mb-5 flex flex-wrap gap-2" role="group" aria-label="Submission status">
        {FILTERS.map((item) => (
          <button
            key={item.value}
            type="button"
            onClick={() => setFilter(item.value)}
            className={`rounded border px-3 py-1.5 text-xs font-bold uppercase tracking-wide ${
              filter === item.value
                ? "border-accent bg-accent text-paper"
                : "border-rule/70 bg-card text-ink-soft hover:border-accent hover:text-accent"
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {(approve.error || reject.error) && <div className="mb-4"><ErrorState error={approve.error ?? reject.error} /></div>}
      {submissions.isLoading && <Spinner label="Loading review queue…" />}
      {submissions.error && <ErrorState error={submissions.error} />}
      {submissions.data && submissions.data.length === 0 && (
        <Card><EmptyState>No submissions match this status.</EmptyState></Card>
      )}
      {submissions.data && submissions.data.length > 0 && (
        <div className="space-y-3">
          {submissions.data.map((item) => {
            const expanded = openId === item.id;
            const busy = activeMutationId === item.id;
            return (
              <Card key={item.id}>
                <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
                  <button type="button" className="min-w-0 text-left" onClick={() => setOpenId(expanded ? null : item.id)}>
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge tone={statusTone(item.status)}>{item.status}</Badge>
                      <span className="truncate text-sm font-bold text-ink">
                        {draftText(item.draft, "name", "sessionName", "session") ?? draftText(item.draft, "host") ?? "Unnamed import"}
                      </span>
                    </div>
                    <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-xs text-ink-soft">
                      <span>{draftText(item.draft, "host") ?? "host missing"}</span>
                      <span>{draftText(item.draft, "username", "user") ?? "user missing"}</span>
                      <span>{formatDate(item.submittedAt)}</span>
                    </div>
                  </button>

                  <div className="flex flex-wrap gap-2 lg:justify-end">
                    <Button variant="ghost" onClick={() => setOpenId(expanded ? null : item.id)}>
                      {expanded ? "Hide draft" : "Inspect"}
                    </Button>
                    {item.status === "PENDING" && (
                      <>
                        <Button disabled={busy} onClick={() => approveSubmission(item)}>{busy ? "Working…" : "Approve"}</Button>
                        <Button disabled={busy} variant="danger" onClick={() => rejectSubmission(item)}>Reject</Button>
                      </>
                    )}
                    {item.resultingTenantId && (
                      <Link to={`/tenants/${item.resultingTenantId}`} className="rounded border border-accent px-3 py-1.5 text-sm font-bold uppercase tracking-wide text-accent">
                        Tenant →
                      </Link>
                    )}
                  </div>
                </div>

                {expanded && (
                  <div className="mt-4 grid gap-4 border-t border-dashed border-rule pt-4 lg:grid-cols-[1fr_auto]">
                    <DraftTable draft={item.draft} />
                    <dl className="min-w-52 space-y-2 text-xs text-ink-soft">
                      <SecretRow label="IBM i password" set={item.hasIbmiPassword} />
                      <SecretRow label="SFTP password" set={item.hasSftpPassword} />
                      {item.reviewedBy && <div><dt>Reviewed by</dt><dd className="font-bold text-ink">{item.reviewedBy}</dd></div>}
                      {item.reviewedAt && <div><dt>Reviewed at</dt><dd className="text-ink">{formatDate(item.reviewedAt)}</dd></div>}
                    </dl>
                  </div>
                )}
              </Card>
            );
          })}
        </div>
      )}
    </>
  );
}

function DraftTable({ draft }: { draft: Record<string, unknown> | null }) {
  const entries = draft ? Object.entries(draft) : [];
  if (!entries.length) return <p className="text-xs text-ink-soft">No readable draft was stored.</p>;
  return (
    <dl className="grid gap-x-5 gap-y-2 text-xs sm:grid-cols-2 xl:grid-cols-3">
      {entries.map(([key, value]) => (
        <div key={key} className="min-w-0">
          <dt className="uppercase tracking-wide text-ink-soft">{key.replaceAll("_", " ")}</dt>
          <dd className="mt-0.5 break-words font-bold text-ink">{displayValue(value)}</dd>
        </div>
      ))}
    </dl>
  );
}

function SecretRow({ label, set }: { label: string; set: boolean }) {
  return <div><dt>{label}</dt><dd className={set ? "font-bold text-accent" : "text-ink-soft"}>{set ? "● securely referenced" : "○ not supplied"}</dd></div>;
}

function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function draftText(draft: Record<string, unknown> | null, ...keys: string[]): string | null {
  for (const key of keys) {
    const value = draft?.[key];
    if (typeof value === "string" && value.trim()) return value;
  }
  return null;
}

function statusTone(status: SubmissionStatus): "green" | "amber" | "red" {
  return status === "APPROVED" ? "green" : status === "PENDING" ? "amber" : "red";
}
