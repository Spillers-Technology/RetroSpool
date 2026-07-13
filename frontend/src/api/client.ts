// Thin fetch wrapper. All admin calls are same-origin; authentication is asserted by
// the Authentik outpost. Spring's readable XSRF-TOKEN cookie is echoed on mutations so
// the upstream browser session cannot be used for cross-site approve/reject requests.
// A 401 is surfaced as an Error whose message begins "401" so the shell and the
// query client can distinguish "signed out" from other failures.

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(`${status} ${message}`);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const csrfToken = readCookie("XSRF-TOKEN");
  const res = await fetch(path, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...(csrfToken && init?.method && !["GET", "HEAD", "OPTIONS"].includes(init.method.toUpperCase())
        ? { "X-XSRF-TOKEN": csrfToken }
        : {}),
      ...init?.headers,
    },
  });

  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      if (body && typeof body.message === "string") message = body.message;
    } catch {
      // non-JSON error body; keep the status text
    }
    throw new ApiError(res.status, message);
  }

  if (res.status === 204) return undefined as T;
  const contentType = res.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) return undefined as T;
  return (await res.json()) as T;
}

function readCookie(name: string): string | null {
  const prefix = `${encodeURIComponent(name)}=`;
  for (const part of document.cookie.split(";")) {
    const cookie = part.trim();
    if (cookie.startsWith(prefix)) return decodeURIComponent(cookie.slice(prefix.length));
  }
  return null;
}

async function requestForm<T>(path: string, form: FormData): Promise<T> {
  // Multipart upload: let the browser set Content-Type (with boundary). The public
  // intake routes are CSRF-exempt (D-007), so no token is echoed here.
  const res = await fetch(path, { method: "POST", body: form, headers: { Accept: "application/json" } });
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      if (body && typeof body.message === "string") message = body.message;
    } catch {
      // keep status text
    }
    throw new ApiError(res.status, message);
  }
  const contentType = res.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) }),
  postForm: <T>(path: string, form: FormData) => requestForm<T>(path, form),
};

/** Direct browser URL for a capture artifact (opened in a new tab, not fetched). */
export function downloadUrl(tenantId: string, captureId: string, variant: "original" | "pdf"): string {
  return `/api/tenants/${tenantId}/captures/${captureId}/download?variant=${variant}`;
}
