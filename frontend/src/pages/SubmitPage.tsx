import { useRef, useState, type FormEvent, type ReactNode } from "react";
import { useCreateSubmission, useParseSession, useTestConnection } from "../api/hooks";
import { Badge, Button, ErrorState } from "../components/ui";
import type { ParsedSession, SubmissionRequest } from "../api/types";

interface FormState {
  host: string;
  username: string;
  useSsl: boolean;
  name: string;
  deviceName: string;
  port: string;
  ccsid: string;
  sessionType: string;
  ibmiPassword: string;
}

interface SftpState {
  enabled: boolean;
  name: string;
  host: string;
  port: string;
  username: string;
  remotePath: string;
  hostKeyFingerprint: string;
  password: string;
}

const EMPTY_FORM: FormState = {
  host: "",
  username: "",
  useSsl: true,
  name: "",
  deviceName: "",
  port: "",
  ccsid: "",
  sessionType: "",
  ibmiPassword: "",
};

const EMPTY_SFTP: SftpState = {
  enabled: false,
  name: "",
  host: "",
  port: "22",
  username: "",
  remotePath: "",
  hostKeyFingerprint: "",
  password: "",
};

export default function SubmitPage() {
  const parse = useParseSession();
  const test = useTestConnection();
  const create = useCreateSubmission();

  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [sftp, setSftp] = useState<SftpState>(EMPTY_SFTP);
  const [showForm, setShowForm] = useState(false);
  const [sourceFormat, setSourceFormat] = useState<string | null>(null);
  const [warnings, setWarnings] = useState<string[]>([]);
  const fileInput = useRef<HTMLInputElement>(null);

  function applyParsed(parsed: ParsedSession) {
    setForm({
      host: parsed.host ?? "",
      username: parsed.username ?? "",
      useSsl: parsed.useSsl,
      name: parsed.name ?? "",
      deviceName: parsed.deviceName ?? "",
      port: parsed.port != null ? String(parsed.port) : "",
      ccsid: parsed.ccsid != null ? String(parsed.ccsid) : "",
      sessionType: parsed.sessionType ?? "",
      ibmiPassword: "",
    });
    setSourceFormat(parsed.sourceFormat);
    setWarnings(parsed.warnings);
    setShowForm(true);
  }

  function onFile(file: File | undefined) {
    if (!file) return;
    create.reset();
    parse.reset();
    parse.mutate(file, { onSuccess: applyParsed });
  }

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }
  function setS<K extends keyof SftpState>(key: K, value: SftpState[K]) {
    setSftp((current) => ({ ...current, [key]: value }));
  }

  const canTest = form.host.trim() !== "" && form.username.trim() !== "" && form.ibmiPassword !== "";
  const canSubmit = form.host.trim() !== "" && form.username.trim() !== "" && !create.isPending;

  function runTest() {
    test.reset();
    test.mutate({
      host: form.host.trim(),
      username: form.username.trim(),
      password: form.ibmiPassword,
      useSsl: form.useSsl,
    });
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const request: SubmissionRequest = {
      draft: {
        host: form.host.trim(),
        username: form.username.trim(),
        useSsl: form.useSsl,
        name: form.name.trim() || null,
        deviceName: form.deviceName.trim() || null,
        port: numberOrNull(form.port),
        ccsid: numberOrNull(form.ccsid),
        sessionType: form.sessionType.trim() || null,
      },
      ibmiPassword: form.ibmiPassword || null,
      sftpDestination: sftp.enabled
        ? {
            name: sftp.name.trim() || "SFTP",
            host: sftp.host.trim(),
            port: numberOrNull(sftp.port),
            username: sftp.username.trim(),
            remotePath: sftp.remotePath.trim(),
            hostKeyFingerprint: sftp.hostKeyFingerprint.trim() || null,
            password: sftp.password || null,
          }
        : null,
    };
    create.mutate(request);
  }

  if (create.isSuccess) {
    return (
      <Shell>
        <div className="rounded-lg border border-accent/40 bg-card p-8 text-center shadow-lg">
          <div className="text-4xl">✅</div>
          <h1 className="mt-4 text-2xl font-bold text-accent">Submission received</h1>
          <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-ink-soft">
            Your session has been recorded as a <strong className="text-ink">pending submission</strong>. A
            RetroSpool operator will review and approve it before any queue is polled — nothing activates
            automatically.
          </p>
          <dl className="mx-auto mt-6 grid max-w-sm grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-left text-sm">
            <dt className="text-ink-soft">Reference</dt>
            <dd className="font-mono text-xs text-ink">{create.data.id}</dd>
            <dt className="text-ink-soft">Status</dt>
            <dd><Badge tone="amber">{create.data.status}</Badge></dd>
            <dt className="text-ink-soft">IBM i password</dt>
            <dd className="text-ink">{create.data.ibmiPasswordStored ? "Stored (write-only)" : "Not provided"}</dd>
            <dt className="text-ink-soft">SFTP destination</dt>
            <dd className="text-ink">{create.data.sftpDestinationConfigured ? "Configured" : "None"}</dd>
          </dl>
          <div className="mt-7">
            <Button
              variant="ghost"
              onClick={() => {
                create.reset();
                setForm(EMPTY_FORM);
                setSftp(EMPTY_SFTP);
                setShowForm(false);
                setSourceFormat(null);
                setWarnings([]);
              }}
            >
              Submit another company
            </Button>
          </div>
        </div>
      </Shell>
    );
  }

  return (
    <Shell>
      <form onSubmit={submit} className="space-y-6">
        {/* Step 1 — upload */}
        <Panel step="01" title="Import a session profile" hint="IBM Personal Communications .ws or Host On-Demand session file">
          <p className="text-sm leading-6 text-ink-soft">
            Upload the workstation file your ACS / HOD printer session uses. RetroSpool reads the host, TLS
            setting, and device name from it so you don't have to retype them. The file is parsed in your
            browser's request and never stored.
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <input
              ref={fileInput}
              type="file"
              accept=".ws,.hod,.txt,text/plain,text/html"
              className="hidden"
              onChange={(event) => onFile(event.target.files?.[0])}
            />
            <Button onClick={() => fileInput.current?.click()} disabled={parse.isPending}>
              {parse.isPending ? "Reading…" : "Choose .ws / .hod file"}
            </Button>
            {!showForm && (
              <button
                type="button"
                onClick={() => setShowForm(true)}
                className="text-sm font-bold text-accent underline underline-offset-2"
              >
                or enter details manually
              </button>
            )}
            {sourceFormat && <Badge tone="green">Parsed: {sourceFormat}</Badge>}
          </div>
          {parse.error && <div className="mt-4"><ErrorState error={parse.error} /></div>}
          {warnings.length > 0 && (
            <ul className="mt-4 space-y-1 rounded border border-amber-500/40 bg-amber-500/5 px-4 py-3 text-sm text-amber-700 dark:text-amber-300">
              {warnings.map((w) => (
                <li key={w}>⚠ {w}</li>
              ))}
            </ul>
          )}
        </Panel>

        {showForm && (
          <>
            {/* Step 2 — connection */}
            <Panel step="02" title="IBM i connection" hint="Confirm what we read and add the sign-on user + password">
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="IBM i host" required value={form.host} onChange={(v) => set("host", v)} placeholder="ibmi.example.com" autoComplete="off" />
                <Field label="User profile" required value={form.username} onChange={(v) => set("username", v)} placeholder="RPTUSER" autoComplete="off" />
                <Field label="Password" type="password" value={form.ibmiPassword} onChange={(v) => set("ibmiPassword", v)} placeholder="••••••••" autoComplete="new-password" />
                <div className="flex items-end">
                  <label className="flex w-full cursor-pointer items-start gap-3 rounded border border-rule/60 bg-bar/30 px-3 py-2.5">
                    <input type="checkbox" checked={form.useSsl} onChange={(e) => set("useSsl", e.target.checked)} className="mt-0.5 accent-green-700" />
                    <span>
                      <span className="block text-sm font-bold text-ink">Use SSL/TLS</span>
                      <span className="mt-0.5 block text-xs text-ink-soft">SecureAS400 sign-on through the truststore.</span>
                    </span>
                  </label>
                </div>
                <Field label="Company / session name" value={form.name} onChange={(v) => set("name", v)} placeholder="Payroll Reports" autoComplete="off" />
                <Field label="Device / LU (informational)" value={form.deviceName} onChange={(v) => set("deviceName", v)} placeholder="PRT01" autoComplete="off" />
                <Field label="Port (informational)" value={form.port} onChange={(v) => set("port", v)} placeholder="992" autoComplete="off" />
                <Field label="CCSID (informational)" value={form.ccsid} onChange={(v) => set("ccsid", v)} placeholder="37" autoComplete="off" />
              </div>

              <div className="mt-5 flex flex-wrap items-center gap-3 border-t border-dashed border-rule pt-4">
                <Button variant="ghost" onClick={runTest} disabled={!canTest || test.isPending}>
                  {test.isPending ? "Signing on…" : "Test connection"}
                </Button>
                <span className="text-[11px] uppercase tracking-wide text-ink-soft">Optional — verifies the host and password before you submit.</span>
              </div>
              {test.error && <div className="mt-3"><ErrorState error={test.error} /></div>}
              {test.data && (
                <div className={`mt-3 border-l-4 px-4 py-2 ${test.data.success ? "border-accent" : "border-red-500"}`}>
                  <div className={`text-sm font-bold ${test.data.success ? "text-accent" : "text-red-600 dark:text-red-400"}`}>
                    {test.data.success ? "Sign-on succeeded" : `Sign-on failed — ${test.data.code}`}
                  </div>
                  <p className="mt-1 text-sm text-ink">{test.data.message}</p>
                </div>
              )}
            </Panel>

            {/* Step 3 — optional SFTP */}
            <Panel step="03" title="Downstream SFTP delivery" hint="Optional — where approved captures are forwarded">
              <label className="flex cursor-pointer items-center gap-3">
                <input type="checkbox" checked={sftp.enabled} onChange={(e) => setS("enabled", e.target.checked)} className="accent-green-700" />
                <span className="text-sm font-bold text-ink">Forward captured reports to an SFTP server</span>
              </label>
              {sftp.enabled && (
                <div className="mt-4 grid gap-4 sm:grid-cols-2">
                  <Field label="Destination name" value={sftp.name} onChange={(v) => setS("name", v)} placeholder="Downstream archive" autoComplete="off" />
                  <Field label="SFTP host" value={sftp.host} onChange={(v) => setS("host", v)} placeholder="sftp.partner.com" autoComplete="off" />
                  <Field label="SFTP port" value={sftp.port} onChange={(v) => setS("port", v)} placeholder="22" autoComplete="off" />
                  <Field label="SFTP user" value={sftp.username} onChange={(v) => setS("username", v)} placeholder="retrospool" autoComplete="off" />
                  <Field label="Remote path" value={sftp.remotePath} onChange={(v) => setS("remotePath", v)} placeholder="/inbound/reports" autoComplete="off" />
                  <Field label="Host key fingerprint" value={sftp.hostKeyFingerprint} onChange={(v) => setS("hostKeyFingerprint", v)} placeholder="SHA256:…" autoComplete="off" />
                  <Field label="SFTP password" type="password" value={sftp.password} onChange={(v) => setS("password", v)} placeholder="••••••••" autoComplete="new-password" />
                </div>
              )}
            </Panel>

            {create.error && <ErrorState error={create.error} />}

            <div className="flex flex-col items-start gap-3 border-t border-dashed border-rule pt-5 sm:flex-row sm:items-center sm:justify-between">
              <p className="max-w-md text-xs leading-5 text-ink-soft">
                Submitting creates a <strong className="text-ink">pending</strong> request for operator review.
                Passwords you enter are stored write-only and are never shown again.
              </p>
              <Button type="submit" disabled={!canSubmit}>
                {create.isPending ? "Submitting…" : "Submit for review"}
              </Button>
            </div>
          </>
        )}
      </form>
    </Shell>
  );
}

function Shell({ children }: { children: ReactNode }) {
  return (
    <main className="min-h-screen bg-paper">
      <div className="mx-auto max-w-3xl px-5 py-10 md:py-14">
        <header className="mb-8">
          <div className="text-2xl font-bold lowercase tracking-tight text-accent">
            retrospool<span className="animate-pulse">_</span>
          </div>
          <p className="mt-1 text-[10px] font-bold uppercase tracking-[0.2em] text-ink-soft">
            new company submission
          </p>
          <h1 className="mt-5 text-3xl font-bold tracking-tight text-ink">Set up spool capture for your IBM i</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-ink-soft">
            Import your ACS / HOD printer-session file, confirm the connection, and submit it for review. An
            operator approves before RetroSpool touches any output queue.
          </p>
        </header>
        {children}
      </div>
    </main>
  );
}

function Panel({ step, title, hint, children }: { step: string; title: string; hint: string; children: ReactNode }) {
  return (
    <section className="rounded-lg border border-rule/60 bg-card shadow-sm">
      <header className="flex items-baseline gap-3 border-b border-rule/50 px-5 py-3">
        <span className="text-[11px] font-bold text-accent">{step}</span>
        <div>
          <h2 className="text-sm font-bold uppercase tracking-wide text-ink">{title}</h2>
          <p className="text-[11px] text-ink-soft">{hint}</p>
        </div>
      </header>
      <div className="p-5">{children}</div>
    </section>
  );
}

function Field({
  label,
  value,
  onChange,
  placeholder,
  type = "text",
  required = false,
  autoComplete,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: string;
  required?: boolean;
  autoComplete?: string;
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[10px] font-bold uppercase tracking-wider text-ink-soft">
        {label}
        {required && <span className="text-accent"> *</span>}
      </span>
      <input
        type={type}
        value={value}
        required={required}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        autoComplete={autoComplete}
        className="w-full rounded border border-rule bg-paper/80 px-3 py-2.5 text-sm text-ink outline-none placeholder:text-ink-soft/50 focus:border-accent focus:ring-1 focus:ring-accent"
      />
    </label>
  );
}

function numberOrNull(value: string): number | null {
  const trimmed = value.trim();
  if (trimmed === "") return null;
  const n = Number(trimmed);
  return Number.isFinite(n) ? n : null;
}
