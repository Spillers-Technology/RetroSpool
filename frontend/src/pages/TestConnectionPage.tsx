import { useState, type FormEvent } from "react";
import { useTestConnection } from "../api/hooks";
import PageHeader from "../components/PageHeader";
import { Badge, Button, Card, ErrorState } from "../components/ui";

export default function TestConnectionPage() {
  const test = useTestConnection();
  const [form, setForm] = useState({ host: "", username: "", password: "", useSsl: true });

  function submit(event: FormEvent) {
    event.preventDefault();
    test.reset();
    test.mutate(form, {
      onSettled: () => setForm((current) => ({ ...current, password: "" })),
    });
  }

  return (
    <>
      <PageHeader
        eyebrow="Ephemeral credential check"
        title="Test IBM i connection"
        description="Perform a synchronous sign-on without persisting the password. The mutable password buffer is scrubbed by the backend after every attempt."
      />
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_0.85fr]">
        <Card title="Sign-on parameters">
          <form onSubmit={submit} className="space-y-4">
            <Input label="IBM i host" value={form.host} onChange={(host) => setForm({ ...form, host })} placeholder="iseries.example.com" autoComplete="url" />
            <Input label="User profile" value={form.username} onChange={(username) => setForm({ ...form, username })} placeholder="RSPOOL" autoComplete="username" />
            <Input label="Password" type="password" value={form.password} onChange={(password) => setForm({ ...form, password })} placeholder="••••••••••••" autoComplete="current-password" />
            <label className="flex cursor-pointer items-start gap-3 rounded border border-rule/60 bg-bar/30 px-3 py-3">
              <input type="checkbox" checked={form.useSsl} onChange={(event) => setForm({ ...form, useSsl: event.target.checked })} className="mt-0.5 accent-green-700" />
              <span><span className="block text-sm font-bold text-ink">Use SSL/TLS</span><span className="mt-0.5 block text-xs text-ink-soft">SecureAS400 sign-on through the configured JVM truststore.</span></span>
            </label>
            <div className="flex items-center justify-between gap-3 border-t border-dashed border-rule pt-4">
              <span className="text-[10px] uppercase tracking-wide text-ink-soft">Nothing is saved by this test.</span>
              <Button type="submit" disabled={test.isPending || !form.host || !form.username || !form.password}>{test.isPending ? "Signing on…" : "Test connection"}</Button>
            </div>
          </form>
        </Card>

        <div>
          {!test.data && !test.error && (
            <Card title="Result"><div className="py-12 text-center"><div className="text-3xl text-rule">◇</div><p className="mt-3 text-sm text-ink-soft">The sign-on result will appear here.</p></div></Card>
          )}
          {test.error && <ErrorState error={test.error} />}
          {test.data && (
            <Card title="Result" actions={<Badge tone={test.data.success ? "green" : "red"}>{test.data.code}</Badge>}>
              <div className={`border-l-4 px-4 py-2 ${test.data.success ? "border-accent" : "border-red-500"}`}>
                <div className={`text-lg font-bold ${test.data.success ? "text-accent" : "text-red-600 dark:text-red-400"}`}>{test.data.success ? "Sign-on succeeded" : "Sign-on failed"}</div>
                <p className="mt-2 text-sm leading-6 text-ink">{test.data.message}</p>
                <p className="mt-4 text-[10px] uppercase tracking-widest text-ink-soft">Round trip: {test.data.elapsedMillis} ms</p>
              </div>
            </Card>
          )}
          <div className="mt-5 rounded border border-dashed border-rule bg-card/50 p-4 text-xs leading-5 text-ink-soft">
            <strong className="text-ink">Security note:</strong> this form sends credentials only to RetroSpool over the current connection. Production should be served behind HTTPS and the configured Authentik forward-auth proxy.
          </div>
        </div>
      </div>
    </>
  );
}

function Input({ label, value, onChange, placeholder, type = "text", autoComplete }: { label: string; value: string; onChange: (value: string) => void; placeholder: string; type?: string; autoComplete: string }) {
  return <label className="block"><span className="mb-1.5 block text-[10px] font-bold uppercase tracking-wider text-ink-soft">{label}</span><input required type={type} value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} autoComplete={autoComplete} className="w-full rounded border border-rule bg-paper/80 px-3 py-2.5 text-sm text-ink outline-none placeholder:text-ink-soft/50 focus:border-accent focus:ring-1 focus:ring-accent" /></label>;
}
