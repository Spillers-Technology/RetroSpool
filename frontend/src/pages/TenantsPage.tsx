import { Link } from "react-router-dom";
import { useTenants } from "../api/hooks";
import PageHeader from "../components/PageHeader";
import { Badge, Card, EmptyState, ErrorState, Spinner, formatDate } from "../components/ui";

export default function TenantsPage() {
  const tenants = useTenants();
  return (
    <>
      <PageHeader
        eyebrow="Company boundaries"
        title="Tenants"
        description="Each company has an isolated IBM i connection, queue set, destinations, captures, and audit trail."
      />
      {tenants.isLoading && <Spinner label="Loading tenants…" />}
      {tenants.error && <ErrorState error={tenants.error} />}
      {tenants.data?.length === 0 && <Card><EmptyState>No active tenants yet. Approve a submission to create one.</EmptyState></Card>}
      {tenants.data && tenants.data.length > 0 && (
        <div className="grid gap-4 xl:grid-cols-2">
          {tenants.data.map((tenant) => (
            <Link key={tenant.id} to={`/tenants/${tenant.id}`} className="group">
              <Card>
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="truncate text-lg font-bold text-ink group-hover:text-accent">{tenant.name}</h2>
                      {tenant.useSsl && <Badge tone="green">TLS</Badge>}
                    </div>
                    <p className="mt-1 truncate text-sm text-ink-soft">{tenant.username}@{tenant.host}</p>
                  </div>
                  <span className="text-2xl text-accent transition group-hover:translate-x-1">→</span>
                </div>
                <div className="mt-5 grid grid-cols-3 divide-x divide-dashed divide-rule border-y border-dashed border-rule py-3 text-center">
                  <Count value={tenant.outputQueues} label="queues" />
                  <Count value={tenant.exportDestinations} label="exports" />
                  <Count value={tenant.captures} label="captures" />
                </div>
                <div className="mt-3 flex flex-wrap justify-between gap-2 text-[10px] uppercase tracking-wide text-ink-soft">
                  <span>{tenant.retentionPolicy.replaceAll("_", " ")}</span>
                  <span>poll {tenant.pollIntervalSeconds}s</span>
                  <span>since {formatDate(tenant.createdAt)}</span>
                </div>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}

function Count({ value, label }: { value: number; label: string }) {
  return <div><div className="text-xl font-bold text-accent">{value}</div><div className="text-[10px] uppercase tracking-wider text-ink-soft">{label}</div></div>;
}
