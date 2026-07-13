import { NavLink, Navigate, Route, Routes } from "react-router-dom";
import { ApiError } from "./api/client";
import { useMe } from "./api/hooks";
import { ErrorState, Spinner } from "./components/ui";
import CapturesPage from "./pages/CapturesPage";
import DashboardPage from "./pages/DashboardPage";
import SubmissionsPage from "./pages/SubmissionsPage";
import TenantDetailPage from "./pages/TenantDetailPage";
import TenantsPage from "./pages/TenantsPage";
import TestConnectionPage from "./pages/TestConnectionPage";

const NAV_ITEMS = [
  { to: "/dashboard", label: "Dashboard", mark: "01" },
  { to: "/submissions", label: "Submissions", mark: "02" },
  { to: "/tenants", label: "Tenants", mark: "03" },
  { to: "/captures", label: "Captures", mark: "04" },
  { to: "/test-connection", label: "Test connection", mark: "05" },
];

function Shell() {
  const me = useMe();

  if (me.isLoading) {
    return (
      <main className="mx-auto flex min-h-screen max-w-lg items-center px-6">
        <Spinner label="Reading operator card…" />
      </main>
    );
  }

  if (me.error) {
    const signedOut = me.error instanceof ApiError && me.error.status === 401;
    return (
      <main className="mx-auto flex min-h-screen max-w-xl items-center px-6">
        <section className="w-full rounded-lg border border-rule bg-card p-6 shadow-lg">
          <div className="mb-4 text-xs font-bold uppercase tracking-[0.22em] text-ink-soft">
            RetroSpool / operator access
          </div>
          <h1 className="mb-3 text-2xl font-bold text-accent">
            {signedOut ? "Sign-in required" : "Console unavailable"}
          </h1>
          {signedOut ? (
            <p className="text-sm leading-6 text-ink-soft">
              The admin console expects an authenticated identity from the Authentik proxy. Sign in through the
              protected RetroSpool address, then reload this page.
            </p>
          ) : (
            <ErrorState error={me.error} />
          )}
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="mt-5 rounded border border-accent px-3 py-2 text-sm font-bold uppercase tracking-wide text-accent hover:bg-accent/10"
          >
            Reload console
          </button>
        </section>
      </main>
    );
  }

  const operator = me.data!;
  return (
    <div className="min-h-screen md:grid md:grid-cols-[15.5rem_minmax(0,1fr)]">
      <aside className="border-b border-rule/70 bg-paper/90 backdrop-blur md:sticky md:top-0 md:h-screen md:border-b-0 md:border-r">
        <div className="flex items-center justify-between gap-4 px-5 py-5 md:block md:px-6 md:py-7">
          <div>
            <NavLink to="/dashboard" className="text-2xl font-bold lowercase tracking-tight text-accent">
              retrospool<span className="animate-pulse">_</span>
            </NavLink>
            <p className="mt-1 text-[10px] font-bold uppercase tracking-[0.2em] text-ink-soft">admin console / 0.1</p>
          </div>
          <div className="sprocket hidden h-14 w-3 opacity-70 md:block" aria-hidden="true" />
        </div>

        <nav className="flex gap-1 overflow-x-auto border-t border-rule/40 px-3 py-3 md:block md:border-t-0 md:px-3" aria-label="Primary">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex shrink-0 items-center gap-3 rounded px-3 py-2.5 text-sm transition md:mb-1 ${
                  isActive ? "bg-accent text-paper shadow-sm" : "text-ink-soft hover:bg-bar/80 hover:text-ink"
                }`
              }
            >
              <span className="text-[10px] font-bold opacity-70">{item.mark}</span>
              <span className="font-bold">{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="hidden px-6 md:absolute md:bottom-6 md:block md:w-[15.5rem]">
          <div className="border-t border-dashed border-rule pt-4">
            <div className="truncate text-xs font-bold text-ink">{operator.displayName}</div>
            <div className="mt-1 truncate text-[10px] text-ink-soft">{operator.email ?? operator.username}</div>
            <div className="mt-2 text-[9px] uppercase tracking-widest text-accent">● authenticated</div>
          </div>
        </div>
      </aside>

      <main className="min-w-0 px-4 py-6 sm:px-7 md:px-10 md:py-9 xl:px-14">
        <div className="mx-auto max-w-7xl">
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage operator={operator} />} />
            <Route path="/submissions" element={<SubmissionsPage />} />
            <Route path="/tenants" element={<TenantsPage />} />
            <Route path="/tenants/:tenantId" element={<TenantDetailPage />} />
            <Route path="/captures" element={<CapturesPage />} />
            <Route path="/test-connection" element={<TestConnectionPage />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </div>
      </main>
    </div>
  );
}

function NotFound() {
  return (
    <div className="py-24 text-center">
      <div className="text-5xl font-bold text-accent">404</div>
      <p className="mt-4 text-ink-soft">That stack of greenbar paper is empty.</p>
      <NavLink to="/dashboard" className="mt-5 inline-block text-sm font-bold text-accent underline">
        Return to dashboard
      </NavLink>
    </div>
  );
}

export default function App() {
  return <Shell />;
}
