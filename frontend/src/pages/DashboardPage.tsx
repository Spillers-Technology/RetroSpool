import { Link } from "react-router-dom";
import { useStats } from "../api/hooks";
import type { Me } from "../api/types";
import PageHeader from "../components/PageHeader";
import { Card, ErrorState, Spinner, StatTile } from "../components/ui";

export default function DashboardPage({ operator }: { operator: Me }) {
  const stats = useStats();

  return (
    <>
      <PageHeader
        eyebrow="System overview"
        title={`Good shift, ${operator.displayName}.`}
        description="A quick read on the tenants, review queue, output queues, and reports currently under RetroSpool management."
      />

      {stats.isLoading && <Spinner label="Counting reports…" />}
      {stats.error && <ErrorState error={stats.error} />}
      {stats.data && (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <StatTile label="Active tenants" value={stats.data.tenants} hint="IBM i connections" />
          <StatTile
            label="Awaiting review"
            value={stats.data.pendingSubmissions}
            hint={stats.data.pendingSubmissions ? "Operator action needed" : "Inbox is clear"}
          />
          <StatTile label="Captured reports" value={stats.data.captures} hint="Originals retained" />
          <StatTile label="Output queues" value={stats.data.outputQueues} hint="Configured sources" />
        </div>
      )}

      <div className="mt-7 grid gap-5 lg:grid-cols-[1.3fr_1fr]">
        <Card title="Operator queue">
          <div className="divide-y divide-dashed divide-rule/70">
            <ActionRow
              marker="01"
              title="Review new submissions"
              detail="Inspect parsed settings before promoting a company into an active tenant."
              to="/submissions?status=PENDING"
            />
            <ActionRow
              marker="02"
              title="Browse captured reports"
              detail="Open original spool bytes or their rendered PDF siblings."
              to="/captures"
            />
            <ActionRow
              marker="03"
              title="Validate an IBM i sign-on"
              detail="Test a host and credential pair without storing the password."
              to="/test-connection"
            />
          </div>
        </Card>

        <Card title="Capture contract">
          <dl className="space-y-4 text-sm">
            <Contract term="Tenant boundary" value="Every read carries tenantId" />
            <Contract term="Source of truth" value="Original bytes are always retained" />
            <Contract term="PCL rendering" value="Isolated GhostPDL sidecar" />
            <Contract term="Deduplication" value="SHA-256 + logical segment" />
          </dl>
        </Card>
      </div>
    </>
  );
}

function ActionRow({ marker, title, detail, to }: { marker: string; title: string; detail: string; to: string }) {
  return (
    <Link to={to} className="group grid grid-cols-[2rem_1fr_auto] gap-3 py-4 first:pt-1 last:pb-1">
      <span className="pt-0.5 text-xs font-bold text-accent">{marker}</span>
      <span>
        <span className="block text-sm font-bold text-ink group-hover:text-accent">{title}</span>
        <span className="mt-1 block text-xs leading-5 text-ink-soft">{detail}</span>
      </span>
      <span className="self-center text-xl text-accent transition group-hover:translate-x-1" aria-hidden="true">
        →
      </span>
    </Link>
  );
}

function Contract({ term, value }: { term: string; value: string }) {
  return (
    <div>
      <dt className="text-[10px] font-bold uppercase tracking-widest text-ink-soft">{term}</dt>
      <dd className="mt-1 text-sm text-ink">{value}</dd>
    </div>
  );
}
