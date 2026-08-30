import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { downloadUrl } from "../api/client";
import {
  useCreateDestination,
  useDeleteDestination,
  useSetDestinationEnabled,
  useTenant,
  useUpdateDestination,
} from "../api/hooks";
import type { DestinationType, ExportDestinationView, TenantDetail } from "../api/types";
import PageHeader from "../components/PageHeader";
import { Badge, Button, Card, EmptyState, ErrorState, Spinner, formatBytes, formatDate } from "../components/ui";

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
  const createDestination = useCreateDestination(tenant.id);
  const updateDestination = useUpdateDestination(tenant.id);
  const setEnabled = useSetDestinationEnabled(tenant.id);
  const deleteDestination = useDeleteDestination(tenant.id);
  const [editor, setEditor] = useState<"new" | string | null>(null);

  const editing = editor && editor !== "new"
    ? tenant.exportDestinations.find((destination) => destination.id === editor) ?? null
    : null;
  const mutationError = createDestination.error
    ?? updateDestination.error
    ?? setEnabled.error
    ?? deleteDestination.error;

  function resetMutations() {
    createDestination.reset();
    updateDestination.reset();
    setEnabled.reset();
    deleteDestination.reset();
  }

  function openEditor(value: "new" | string) {
    resetMutations();
    setEditor(value);
  }

  async function save(form: DestinationFormState) {
    const config = destinationConfig(form);
    const password = form.password.length ? form.password : undefined;
    if (editor === "new") {
      await createDestination.mutateAsync({
        type: form.type,
        name: form.name.trim(),
        config,
        ...(password ? { password } : {}),
      });
    } else if (editing) {
      await updateDestination.mutateAsync({
        id: editing.id,
        request: {
          name: form.name.trim(),
          config,
          ...(password ? { password } : {}),
        },
      });
    }
    setEditor(null);
    // The submitted request (including a plaintext password, when set) stays
    // cached as this mutation's `variables` until reset — it must not linger
    // in memory past the moment the write completed (D-012/D-023: write-only,
    // not "write-only until the next unrelated render").
    resetMutations();
  }

  function remove(destination: ExportDestinationView) {
    const confirmed = window.confirm(
      `Delete “${destination.name}”? Destinations with delivery history cannot be deleted and should be paused instead.`,
    );
    if (!confirmed) return;
    deleteDestination.mutate(destination.id, {
      onSuccess: () => {
        if (editor === destination.id) setEditor(null);
      },
    });
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button onClick={() => openEditor("new")} disabled={editor === "new"}>Add destination</Button>
      </div>

      {editor && (
        <DestinationEditor
          key={editor}
          destination={editing}
          saving={createDestination.isPending || updateDestination.isPending}
          onSave={save}
          onCancel={() => setEditor(null)}
        />
      )}

      {mutationError && <ErrorState error={mutationError} />}

      {!tenant.exportDestinations.length ? (
        <Card><EmptyState>No export destinations configured. Add one to prepare this tenant for export fan-out.</EmptyState></Card>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {tenant.exportDestinations.map((destination) => {
            const toggling = setEnabled.isPending && setEnabled.variables?.id === destination.id;
            const deleting = deleteDestination.isPending && deleteDestination.variables === destination.id;
            return (
              <Card
                key={destination.id}
                title={destination.name}
                actions={(
                  <div className="flex gap-2">
                    <Badge tone={destination.enabled ? "green" : "gray"}>
                      {destination.enabled ? "enabled" : "paused"}
                    </Badge>
                    <Badge>{destination.type}</Badge>
                  </div>
                )}
              >
                <dl className="space-y-2 text-xs">
                  {Object.entries(destination.config ?? {}).map(([key, value]) => (
                    <div key={key} className="flex justify-between gap-5 border-b border-dashed border-rule/50 pb-2">
                      <dt className="text-ink-soft">{formatConfigKey(key)}</dt>
                      <dd className="break-all text-right font-bold text-ink">{String(value)}</dd>
                    </div>
                  ))}
                  <div className="flex justify-between gap-5">
                    <dt className="text-ink-soft">Credential</dt>
                    <dd className={destination.secretSet ? "font-bold text-accent" : "text-ink-soft"}>
                      {destination.secretSet ? "● set" : "○ none"}
                    </dd>
                  </div>
                </dl>
                <div className="mt-4 flex flex-wrap gap-2 border-t border-rule/50 pt-3">
                  <Button variant="ghost" onClick={() => openEditor(destination.id)} disabled={editor === destination.id}>
                    Edit
                  </Button>
                  <Button
                    variant="ghost"
                    disabled={toggling}
                    onClick={() => setEnabled.mutate({ id: destination.id, enabled: !destination.enabled })}
                  >
                    {toggling ? "Saving…" : destination.enabled ? "Pause" : "Enable"}
                  </Button>
                  <Button variant="danger" disabled={deleting} onClick={() => remove(destination)}>
                    {deleting ? "Deleting…" : "Delete"}
                  </Button>
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}

interface DestinationFormState {
  type: DestinationType;
  name: string;
  bucket: string;
  region: string;
  prefix: string;
  endpoint: string;
  pathStyleAccess: boolean;
  host: string;
  port: string;
  username: string;
  remotePath: string;
  hostKeyFingerprint: string;
  implicitTls: boolean;
  password: string;
}

function blankDestination(type: DestinationType = "S3"): DestinationFormState {
  return {
    type,
    name: "",
    bucket: "",
    region: "",
    prefix: "",
    endpoint: "",
    pathStyleAccess: false,
    host: "",
    port: type === "SFTP" ? "22" : type === "FTPS" ? "21" : "",
    username: "",
    remotePath: "",
    hostKeyFingerprint: "",
    implicitTls: false,
    password: "",
  };
}

function destinationForm(destination: ExportDestinationView | null): DestinationFormState {
  if (!destination) return blankDestination();
  const config = destination.config ?? {};
  return {
    ...blankDestination(destination.type),
    name: destination.name,
    bucket: configString(config, "bucket"),
    region: configString(config, "region"),
    prefix: configString(config, "prefix"),
    endpoint: configString(config, "endpoint"),
    pathStyleAccess: configBoolean(config, "pathStyleAccess"),
    host: configString(config, "host"),
    port: configNumber(config, "port", destination.type === "SFTP" ? 22 : 21).toString(),
    username: configString(config, "username"),
    // The fallbacks let an operator repair a pre-V4 row even if it is returned before
    // the migration has run; every save writes only the canonical snake_case keys.
    remotePath: configString(config, "remote_path", "remotePath"),
    hostKeyFingerprint: configString(config, "host_key_fingerprint", "hostKeyFingerprint"),
    implicitTls: configBoolean(config, "implicit_tls", "implicitTls"),
    password: "", // D-012: never hydrate or prefill a secret on edit.
  };
}

function destinationConfig(form: DestinationFormState): Record<string, unknown> {
  if (form.type === "S3") {
    return {
      bucket: form.bucket.trim(),
      region: form.region.trim(),
      ...(form.prefix.trim() ? { prefix: form.prefix.trim() } : {}),
      ...(form.endpoint.trim() ? { endpoint: form.endpoint.trim() } : {}),
      pathStyleAccess: form.pathStyleAccess,
    };
  }
  if (form.type === "SFTP") {
    return {
      host: form.host.trim(),
      port: Number(form.port),
      username: form.username.trim(),
      remote_path: form.remotePath.trim(),
      ...(form.hostKeyFingerprint.trim()
        ? { host_key_fingerprint: form.hostKeyFingerprint.trim() }
        : {}),
    };
  }
  return {
    host: form.host.trim(),
    port: Number(form.port),
    username: form.username.trim(),
    remote_path: form.remotePath.trim(),
    implicit_tls: form.implicitTls,
  };
}

function DestinationEditor({
  destination,
  saving,
  onSave,
  onCancel,
}: {
  destination: ExportDestinationView | null;
  saving: boolean;
  onSave: (form: DestinationFormState) => Promise<void>;
  onCancel: () => void;
}) {
  const [form, setForm] = useState(() => destinationForm(destination));
  const inputClass = "mt-1 w-full rounded border border-rule/70 bg-paper px-3 py-2 text-sm text-ink outline-none focus:border-accent";
  const labelClass = "text-[10px] font-bold uppercase tracking-wider text-ink-soft";

  function set<K extends keyof DestinationFormState>(key: K, value: DestinationFormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function changeType(type: DestinationType) {
    setForm((current) => ({ ...blankDestination(type), name: current.name }));
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void onSave(form).catch(() => undefined);
  }

  return (
    <Card title={destination ? `Edit ${destination.name}` : "Add export destination"}>
      <form className="space-y-4" onSubmit={submit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className={labelClass}>
            Type
            <select
              className={inputClass}
              value={form.type}
              disabled={!!destination}
              onChange={(event) => changeType(event.target.value as DestinationType)}
            >
              <option value="S3">S3</option>
              <option value="SFTP">SFTP</option>
              <option value="FTPS">FTPS</option>
            </select>
          </label>
          <label className={labelClass}>
            Name
            <input className={inputClass} required value={form.name} onChange={(event) => set("name", event.target.value)} />
          </label>
        </div>

        {form.type === "S3" ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <DestinationInput label="Bucket" required value={form.bucket} onChange={(value) => set("bucket", value)} />
            <DestinationInput label="Region" required value={form.region} onChange={(value) => set("region", value)} placeholder="us-east-1" />
            <DestinationInput label="Prefix" value={form.prefix} onChange={(value) => set("prefix", value)} placeholder="reports/" />
            <DestinationInput label="Custom endpoint" value={form.endpoint} onChange={(value) => set("endpoint", value)} placeholder="https://s3.example.com" />
            <label className="flex items-center gap-2 text-xs font-bold text-ink-soft sm:col-span-2">
              <input type="checkbox" checked={form.pathStyleAccess} onChange={(event) => set("pathStyleAccess", event.target.checked)} />
              Use path-style bucket access
            </label>
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            <DestinationInput label="Host" required value={form.host} onChange={(value) => set("host", value)} />
            <DestinationInput label="Port" required type="number" min={1} max={65535} value={form.port} onChange={(value) => set("port", value)} />
            <DestinationInput label="Username" required value={form.username} onChange={(value) => set("username", value)} />
            <DestinationInput label="Remote path" required value={form.remotePath} onChange={(value) => set("remotePath", value)} placeholder="/inbound/reports" />
            {form.type === "SFTP" && (
              <DestinationInput
                label="Host key fingerprint"
                value={form.hostKeyFingerprint}
                onChange={(value) => set("hostKeyFingerprint", value)}
                placeholder="SHA256:…"
              />
            )}
            {form.type === "FTPS" && (
              <label className="flex items-center gap-2 text-xs font-bold text-ink-soft">
                <input type="checkbox" checked={form.implicitTls} onChange={(event) => set("implicitTls", event.target.checked)} />
                Implicit TLS (usually port 990)
              </label>
            )}
            <label className={`${labelClass} sm:col-span-2`}>
              {destination?.secretSet ? "Change password (currently ● set)" : "Password (currently ○ none)"}
              <input
                className={inputClass}
                type="password"
                autoComplete="new-password"
                value={form.password}
                onChange={(event) => set("password", event.target.value)}
                placeholder={destination?.secretSet ? "Leave blank to keep current password" : "Optional write-only password"}
              />
              <span className="mt-1 block normal-case tracking-normal text-ink-soft/80">
                Write-only: this value is submitted once and is never returned or prefilled.
              </span>
            </label>
          </div>
        )}

        <div className="flex gap-2 border-t border-rule/50 pt-4">
          <Button type="submit" disabled={saving}>{saving ? "Saving…" : destination ? "Save changes" : "Add destination"}</Button>
          <Button variant="ghost" onClick={onCancel} disabled={saving}>Cancel</Button>
        </div>
      </form>
    </Card>
  );
}

function DestinationInput({
  label,
  value,
  onChange,
  required = false,
  type = "text",
  placeholder,
  min,
  max,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  type?: "text" | "number";
  placeholder?: string;
  min?: number;
  max?: number;
}) {
  return (
    <label className="text-[10px] font-bold uppercase tracking-wider text-ink-soft">
      {label}
      <input
        className="mt-1 w-full rounded border border-rule/70 bg-paper px-3 py-2 text-sm text-ink outline-none focus:border-accent"
        type={type}
        required={required}
        min={min}
        max={max}
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function configString(config: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    if (typeof config[key] === "string") return config[key] as string;
  }
  return "";
}

function configNumber(config: Record<string, unknown>, key: string, fallback: number): number {
  return typeof config[key] === "number" ? config[key] : fallback;
}

function configBoolean(config: Record<string, unknown>, ...keys: string[]): boolean {
  for (const key of keys) {
    if (typeof config[key] === "boolean") return config[key] as boolean;
  }
  return false;
}

function formatConfigKey(key: string): string {
  return key
    .replaceAll("_", " ")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .toLowerCase();
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
