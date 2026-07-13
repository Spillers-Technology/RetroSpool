import type { ReactNode } from "react";

export function Card({ title, children, actions }: { title?: ReactNode; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="rounded-lg border border-rule/60 bg-card backdrop-blur-sm shadow-sm">
      {(title || actions) && (
        <header className="flex items-center justify-between gap-3 border-b border-rule/50 px-4 py-3">
          {title && <h2 className="text-sm font-bold uppercase tracking-wide text-ink-soft">{title}</h2>}
          {actions}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  );
}

export function StatTile({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="rounded-lg border border-rule/60 bg-card px-4 py-3">
      <div className="text-3xl font-bold tabular-nums text-accent">{value}</div>
      <div className="mt-1 text-xs font-bold uppercase tracking-wide text-ink-soft">{label}</div>
      {hint && <div className="mt-0.5 text-[11px] text-ink-soft/80">{hint}</div>}
    </div>
  );
}

const BADGE_TONES: Record<string, string> = {
  green: "border-accent/40 text-accent",
  amber: "border-amber-500/50 text-amber-600 dark:text-amber-400",
  red: "border-red-500/50 text-red-600 dark:text-red-400",
  gray: "border-rule/60 text-ink-soft",
};

export function Badge({ children, tone = "gray" }: { children: ReactNode; tone?: keyof typeof BADGE_TONES | string }) {
  const cls = BADGE_TONES[tone] ?? BADGE_TONES.gray;
  return (
    <span className={`inline-block rounded border px-1.5 py-0.5 text-[11px] font-bold uppercase tracking-wide ${cls}`}>
      {children}
    </span>
  );
}

export function Button({
  children,
  onClick,
  disabled,
  variant = "primary",
  type = "button",
}: {
  children: ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  variant?: "primary" | "ghost" | "danger";
  type?: "button" | "submit";
}) {
  const styles: Record<string, string> = {
    primary: "border-accent bg-accent/10 text-accent hover:bg-accent/20",
    ghost: "border-rule/60 text-ink-soft hover:bg-bar/60",
    danger: "border-red-500/50 text-red-600 hover:bg-red-500/10 dark:text-red-400",
  };
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`rounded border px-3 py-1.5 text-sm font-bold uppercase tracking-wide transition disabled:cursor-not-allowed disabled:opacity-40 ${styles[variant]}`}
    >
      {children}
    </button>
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 py-8 text-sm text-ink-soft" role="status">
      <span className="inline-block h-3 w-3 animate-pulse rounded-full bg-accent" />
      {label ?? "Loading…"}
    </div>
  );
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="py-10 text-center text-sm text-ink-soft">{children}</div>;
}

export function ErrorState({ error }: { error: unknown }) {
  const message = error instanceof Error ? error.message : String(error);
  return (
    <div className="rounded border border-red-500/40 bg-red-500/5 px-4 py-3 text-sm text-red-600 dark:text-red-400">
      {message}
    </div>
  );
}

export function formatDate(value: string | null): string {
  if (!value) return "—";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString();
}

export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  const units = ["KB", "MB", "GB"];
  let value = n / 1024;
  let i = 0;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i++;
  }
  return `${value.toFixed(1)} ${units[i]}`;
}
