import { useSearchParams } from "react-router-dom";
import { downloadUrl } from "../api/client";
import { useCaptures, useTenants } from "../api/hooks";
import PageHeader from "../components/PageHeader";
import { Badge, Card, EmptyState, ErrorState, Spinner, formatBytes, formatDate } from "../components/ui";

export default function CapturesPage() {
  const [params, setParams] = useSearchParams();
  const tenants = useTenants();
  const requested = params.get("tenant") ?? undefined;
  const selected = requested ?? tenants.data?.[0]?.id;
  const captures = useCaptures(selected);
  const selectedTenant = tenants.data?.find((t) => t.id === selected);

  return (
    <>
      <PageHeader
        eyebrow="Originals + rendered siblings"
        title="Captures"
        description="Browse tenant-scoped spool artifacts. RetroSpool keeps original bytes as the source of truth and exposes a rendered PDF when available."
        actions={tenants.data && tenants.data.length > 0 ? (
          <label className="block text-[10px] font-bold uppercase tracking-wider text-ink-soft">
            Tenant
            <select
              value={selected}
              onChange={(event) => setParams({ tenant: event.target.value })}
              className="mt-1 block max-w-64 rounded border border-rule bg-paper px-3 py-2 text-sm font-bold normal-case tracking-normal text-ink outline-none focus:border-accent"
            >
              {tenants.data.map((tenant) => <option key={tenant.id} value={tenant.id}>{tenant.name}</option>)}
            </select>
          </label>
        ) : undefined}
      />
      {tenants.isLoading && <Spinner label="Loading tenant index…" />}
      {tenants.error && <ErrorState error={tenants.error} />}
      {tenants.data?.length === 0 && <Card><EmptyState>Create a tenant before browsing captures.</EmptyState></Card>}
      {selected && captures.isLoading && <Spinner label={`Loading ${selectedTenant?.name ?? "tenant"} captures…`} />}
      {captures.error && <ErrorState error={captures.error} />}
      {captures.data?.length === 0 && <Card><EmptyState>No captures have landed for {selectedTenant?.name ?? "this tenant"}.</EmptyState></Card>}
      {captures.data && captures.data.length > 0 && (
        <Card title={`${captures.data.length} artifact${captures.data.length === 1 ? "" : "s"} / ${selectedTenant?.name ?? "tenant"}`}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-xs">
              <thead><tr className="border-b border-rule uppercase tracking-wider text-ink-soft"><th className="pb-2">Report</th><th className="pb-2">Job</th><th className="pb-2">Format</th><th className="pb-2">Size</th><th className="pb-2">Captured</th><th className="pb-2 text-right">Download</th></tr></thead>
              <tbody>{captures.data.map((capture) => (
                <tr key={capture.id} className="border-b border-dashed border-rule/60 last:border-0">
                  <td className="py-3"><div className="max-w-52 truncate font-bold text-ink">{capture.spoolFileName ?? "unnamed"}</div><div className="mt-0.5 text-[10px] text-ink-soft">segment {capture.logicalSegmentIndex} · {capture.sha256.slice(0, 10)}…</div></td>
                  <td className="py-3"><div>{capture.spoolJobName ?? "—"}</div><div className="text-ink-soft">{capture.spoolJobUser ?? "—"}</div></td>
                  <td className="py-3"><Badge tone={capture.renderStatus === "FAILED" ? "red" : capture.renderStatus === "SUCCESS" ? "green" : "gray"}>{capture.detectedFormat}</Badge></td>
                  <td className="py-3">{formatBytes(capture.byteSize)}</td>
                  <td className="py-3 text-ink-soft">{formatDate(capture.capturedAt)}</td>
                  <td className="py-3 text-right"><a href={downloadUrl(selected!, capture.id, "original")} target="_blank" rel="noreferrer" className="font-bold text-accent hover:underline">original</a>{capture.hasRenderedPdf && <> · <a href={downloadUrl(selected!, capture.id, "pdf")} target="_blank" rel="noreferrer" className="font-bold text-accent hover:underline">PDF</a></>}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        </Card>
      )}
    </>
  );
}
