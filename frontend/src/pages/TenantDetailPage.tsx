import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { downloadUrl } from "../api/client";
import { useTenant } from "../api/hooks";
import type { TenantDetail } from "../api/types";
import PageHeader from "../components/PageHeader";
import { Badge, Card, EmptyState, ErrorState, Spinner, formatBytes, formatDate } from "../components/ui";

type Tab = "connection" | "queues" | "destinations" | "captures" | "audit";
const TABS: Array<{ id: Tab; label: string }> = [
  { id: "connection", label: "Connection" },
  { id: "queues", label: "Queues" },
  { id: "destinations", label: "Destinations" },
  { id: "captures", label: "Captures" },
  { id: "audit", label: "Audit" },
];

export default function TenantDetailPage() {
  const { tenantId } = useParams();
  const tenant = useTenant(tenantId);
  const [tab, setTab] = useState<Tab>("connection");

  if (tenant.isLoading) return <Spinner label="Opening tenant folder…" />;
  if (tenant.error) return <ErrorState error={tenant.error} />;
  if (!tenant.data) return null;
  const data = tenant.data;

  return (
    <>
      <PageHeader
        eyebrow="Tenant detail"
        title={data.name}
        description={`${data.username}@${data.host}:${data.port} · ${data.useSsl ? "TLS secured" : "unencrypted transport"}`}
        actions={<Link to="/tenants" className="text-xs font-bold uppercase tracking-wide text-accent">← All tenants</Link>}
      />
      <div className="mb-5 flex gap-1 overflow-x-auto border-b border-rule" role="tablist">
        {TABS.map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={tab === item.id}
            onClick={() => setTab(item.id)}
            className={`shrink-0 border-b-2 px-3 py-2 text-xs font-bold uppercase tracking-wide ${
              tab === item.id ? "border-accent text-accent" : "border-transparent text-ink-soft hover:text-ink"
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>
      {tab === "connection" && <ConnectionTab tenant={data} />}
      {tab === "queues" && <QueuesTab tenant={data} />}
      {tab === "destinations" && <DestinationsTab tenant={data} />}
      {tab === "captures" && <CapturesTab tenant={data} />}
      {tab === "audit" && <AuditTab tenant={data} />}
    </>
  );
}

function ConnectionTab({ tenant }: { tenant: TenantDetail }) {
  return (
    <div className="grid gap-5 lg:grid-cols-2">
      <Card title="IBM i sign-on">
        <dl className="grid gap-4 text-sm sm:grid-cols-2">
          <Field label="Host" value={`${tenant.host}:${tenant.port}`} />
          <Field label="User" value={tenant.username} />
          <Field label="Transport" value={tenant.useSsl ? "SecureAS400 / TLS" : "AS400 / plain"} />
          <Field label="Credential" value={tenant.ibmiPasswordSet ? "● secret reference set" : "○ not configured"} accent={tenant.ibmiPasswordSet} />
          <Field label="Printer device" value={tenant.printerDeviceName ?? "automatic"} />
          <Field label="CCSID" value={tenant.ccsid?.toString() ?? "system default"} />
        </dl>
      </Card>
      <Card title="Capture policy">
        <dl className="grid gap-4 text-sm sm:grid-cols-2">
          <Field label="Retention" value={tenant.retentionPolicy.replaceAll("_", " ")} />
          <Field label="Poll interval" value={`${tenant.pollIntervalSeconds} seconds`} />
          <Field label="Library list" value={tenant.libraryList.length ? tenant.libraryList.join(", ") : "system default"} />
          <Field label="Created" value={formatDate(tenant.createdAt)} />
          <Field label="Last updated" value={formatDate(tenant.updatedAt)} />
        </dl>
      </Card>
    </div>
  );
}

function QueuesTab({ tenant }: { tenant: TenantDetail }) {
  if (!tenant.outputQueues.length) return <Card><EmptyState>No output queues configured.</EmptyState></Card>;
  return (
    <Card title={`${tenant.outputQueues.length} output queue${tenant.outputQueues.length === 1 ? "" : "s"}`}>
      <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr className="border-b border-rule text-[10px] uppercase tracking-wider text-ink-soft"><th className="pb-2">Library</th><th className="pb-2">Queue</th><th className="pb-2">Retention override</th></tr></thead><tbody>{tenant.outputQueues.map((q) => <tr key={q.id} className="border-b border-dashed border-rule/60 last:border-0"><td className="py-3 font-bold">{q.library}</td><td className="py-3 text-accent">{q.queueName}</td><td className="py-3 text-ink-soft">{q.retentionPolicy?.replaceAll("_", " ") ?? "tenant default"}</td></tr>)}</tbody></table></div>
    </Card>
  );
}

function DestinationsTab({ tenant }: { tenant: TenantDetail }) {
  if (!tenant.exportDestinations.length) return <Card><EmptyState>No export destinations configured.</EmptyState></Card>;
  return <div className="grid gap-4 lg:grid-cols-2">{tenant.exportDestinations.map((d) => <Card key={d.id} title={d.name} actions={<div className="flex gap-2"><Badge tone={d.enabled ? "green" : "gray"}>{d.enabled ? "enabled" : "paused"}</Badge><Badge>{d.type}</Badge></div>}><dl className="space-y-2 text-xs">{Object.entries(d.config ?? {}).map(([key, value]) => <div key={key} className="flex justify-between gap-5 border-b border-dashed border-rule/50 pb-2"><dt className="text-ink-soft">{key}</dt><dd className="break-all text-right font-bold text-ink">{String(value)}</dd></div>)}<div className="flex justify-between gap-5"><dt className="text-ink-soft">Credential</dt><dd className={d.secretSet ? "font-bold text-accent" : "text-ink-soft"}>{d.secretSet ? "● set" : "○ none"}</dd></div></dl></Card>)}</div>;
}

function CapturesTab({ tenant }: { tenant: TenantDetail }) {
  if (!tenant.recentCaptures.length) return <Card><EmptyState>No captures have landed for this tenant.</EmptyState></Card>;
  return <Card title="Recent captures" actions={<Link className="text-xs font-bold text-accent" to={`/captures?tenant=${tenant.id}`}>View all →</Link>}><div className="overflow-x-auto"><table className="w-full min-w-[700px] text-left text-xs"><thead><tr className="border-b border-rule uppercase tracking-wider text-ink-soft"><th className="pb-2">Spool file</th><th className="pb-2">Format</th><th className="pb-2">Size</th><th className="pb-2">Captured</th><th className="pb-2 text-right">Artifacts</th></tr></thead><tbody>{tenant.recentCaptures.map((c) => <tr key={c.id} className="border-b border-dashed border-rule/60 last:border-0"><td className="py-3"><div className="font-bold text-ink">{c.spoolFileName ?? "unnamed"}</div><div className="mt-0.5 text-ink-soft">{c.spoolJobUser}/{c.spoolJobName}</div></td><td className="py-3"><Badge tone={c.renderStatus === "FAILED" ? "red" : "green"}>{c.detectedFormat}</Badge></td><td className="py-3">{formatBytes(c.byteSize)}</td><td className="py-3 text-ink-soft">{formatDate(c.capturedAt)}</td><td className="py-3 text-right"><a className="font-bold text-accent" href={downloadUrl(tenant.id, c.id, "original")} target="_blank" rel="noreferrer">original</a>{c.hasRenderedPdf && <> · <a className="font-bold text-accent" href={downloadUrl(tenant.id, c.id, "pdf")} target="_blank" rel="noreferrer">PDF</a></>}</td></tr>)}</tbody></table></div></Card>;
}

function AuditTab({ tenant }: { tenant: TenantDetail }) {
  if (!tenant.recentAudit.length) return <Card><EmptyState>No audit events recorded.</EmptyState></Card>;
  return <div className="relative ml-2 border-l border-dashed border-rule pl-6">{tenant.recentAudit.map((event) => <div key={event.id} className="relative mb-4 rounded border border-rule/60 bg-card p-4 before:absolute before:-left-[1.8rem] before:top-5 before:h-2 before:w-2 before:rounded-full before:bg-accent"><div className="flex flex-wrap justify-between gap-2"><span className="text-sm font-bold text-ink">{event.eventType}</span><time className="text-xs text-ink-soft">{formatDate(event.createdAt)}</time></div>{event.payload && <pre className="mt-3 overflow-x-auto whitespace-pre-wrap text-[11px] text-ink-soft">{JSON.stringify(event.payload, null, 2)}</pre>}</div>)}</div>;
}

function Field({ label, value, accent = false }: { label: string; value: string; accent?: boolean }) {
  return <div><dt className="text-[10px] font-bold uppercase tracking-wider text-ink-soft">{label}</dt><dd className={`mt-1 break-words ${accent ? "font-bold text-accent" : "text-ink"}`}>{value}</dd></div>;
}
