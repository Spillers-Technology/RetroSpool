// Capture the real, production-built RetroSpool admin console for GitHub Pages.
//
// `npm run capture` first builds frontend/ using its lockfile, then this script
// serves that Vite production bundle. API calls are intercepted in the browser
// with deterministic, invented fixture data: no live service, credentials, or
// customer information is used.

import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "..", "..");
const frontend = resolve(root, "frontend");
const output = resolve(root, "site", "assets");
const origin = "http://127.0.0.1:4173";
mkdirSync(output, { recursive: true });

const tenantId = "109bb7f2-e379-4ae0-a733-55b04419bc37";
const secondTenantId = "327bc6ee-520c-44ba-97dd-c65d0810ab94";

const operator = {
  username: "operator.demo",
  email: "operator@retrospool.example",
  displayName: "Demo Operator",
  groups: ["retrospool-admins"],
};

const captures = [
  capture("f7346c8a-79c8-4ec5-bcf0-dd52fbaeff9f", "AR_STATEMENTS", "MONTHEND", "PCL", 48213, "SUCCESS", true, "2026-07-12T13:42:18Z"),
  capture("5d6205f6-53c5-489b-9836-e539f8a747a8", "PICK_TICKETS", "ORDERS", "PDF", 194802, "SKIPPED", false, "2026-07-12T13:36:04Z"),
  capture("dcfa2ac6-bd86-4a0a-a6a9-7c32bdfdeba3", "DAILY_SALES", "NIGHTLY", "TEXT", 18642, "SUCCESS", true, "2026-07-12T12:05:39Z"),
  capture("e0901207-7ac7-4be1-a6bf-7c289e91ad7d", "AP_CHECKS", "APRUN", "PCL", 86120, "SUCCESS", true, "2026-07-12T11:54:11Z"),
  capture("8ee97009-a891-4165-b8f4-6341e775eefc", "INVENTORY_AGING", "WEEKLY", "PCL", 128993, "SUCCESS", true, "2026-07-12T10:18:52Z"),
];

const tenants = [
  {
    id: tenantId,
    name: "Northstar Distribution (Demo)",
    host: "ibmi.northstar.example",
    username: "RSPOOL",
    useSsl: true,
    retentionPolicy: "HOLD",
    pollIntervalSeconds: 60,
    createdAt: "2026-06-18T15:04:12Z",
    outputQueues: 3,
    exportDestinations: 2,
    captures: 1284,
  },
  {
    id: secondTenantId,
    name: "Bluebird Manufacturing (Demo)",
    host: "ibmi.bluebird.example",
    username: "SPLREADER",
    useSsl: true,
    retentionPolicy: "HOLD",
    pollIntervalSeconds: 120,
    createdAt: "2026-06-25T18:31:50Z",
    outputQueues: 2,
    exportDestinations: 1,
    captures: 647,
  },
];

const tenantDetail = {
  ...tenants[0],
  port: 9476,
  ibmiPasswordSet: true,
  printerDeviceName: "RSPLDEV",
  ccsid: 37,
  libraryList: ["QGPL", "RPTLIB"],
  updatedAt: "2026-07-10T20:14:31Z",
  outputQueues: [
    { id: "4f516922-770f-4317-9f8b-68c7e5a19cdd", library: "QUSRSYS", queueName: "ARPRINT", retentionPolicy: null },
    { id: "d9d627a0-b2eb-4c56-bdaa-41994d703331", library: "QUSRSYS", queueName: "SHIPDOCS", retentionPolicy: "HOLD" },
    { id: "653117d0-6bf7-452b-a556-f8e80ddda587", library: "RPTLIB", queueName: "DAILYRPT", retentionPolicy: null },
  ],
  exportDestinations: [
    {
      id: "9e05d091-83b5-4076-a238-06008b3d1cc6",
      type: "S3",
      name: "Reporting archive",
      config: { bucket: "retrospool-demo", region: "us-east-1", prefix: "northstar/reports" },
      secretSet: true,
      enabled: true,
    },
    {
      id: "d2bf3441-f287-46df-9569-4db2eb6bb6d3",
      type: "SFTP",
      name: "Document portal",
      config: { host: "sftp.northstar.example", port: 22, remote_path: "/incoming/reports" },
      secretSet: true,
      enabled: true,
    },
  ],
  recentCaptures: captures,
  recentAudit: [
    { id: 4104, eventType: "CAPTURE_CREATED", payload: { spoolFile: "AR_STATEMENTS", format: "PCL", rendered: true }, createdAt: "2026-07-12T13:42:18Z" },
    { id: 4103, eventType: "CONNECTION_TESTED", payload: { outcome: "OK", tls: true }, createdAt: "2026-07-12T13:31:05Z" },
    { id: 4098, eventType: "SUBMISSION_APPROVED", payload: { reviewedBy: "operator.demo" }, createdAt: "2026-07-11T16:08:44Z" },
  ],
};

const submissions = [
  {
    id: "af480be6-6f3e-4666-a60d-cd8a03d37991",
    status: "PENDING",
    draft: {
      name: "Pinecone Supply (Demo)",
      host: "ibmi.pinecone.example",
      username: "RSPOOL",
      use_ssl: true,
      library: "QUSRSYS",
      output_queue: "REPORTS",
      retention_policy: "HOLD",
      poll_interval_seconds: 60,
    },
    hasIbmiPassword: true,
    hasSftpPassword: true,
    submittedAt: "2026-07-12T12:20:04Z",
    reviewedBy: null,
    reviewedAt: null,
    resultingTenantId: null,
  },
  {
    id: "b866eaf1-28f1-4732-957b-d5f6a8f5a60c",
    status: "PENDING",
    draft: {
      name: "Redwood Parts (Demo)",
      host: "ibmi.redwood.example",
      username: "SPOOLWEB",
      use_ssl: true,
      library: "RPTLIB",
      output_queue: "INVOICES",
    },
    hasIbmiPassword: true,
    hasSftpPassword: false,
    submittedAt: "2026-07-12T10:48:29Z",
    reviewedBy: null,
    reviewedAt: null,
    resultingTenantId: null,
  },
  {
    id: "aab9cab2-40ab-46b7-95e1-f42f289b6dbd",
    status: "APPROVED",
    draft: { name: "Northstar Distribution (Demo)", host: "ibmi.northstar.example", username: "RSPOOL" },
    hasIbmiPassword: true,
    hasSftpPassword: true,
    submittedAt: "2026-07-11T14:02:55Z",
    reviewedBy: "operator.demo",
    reviewedAt: "2026-07-11T16:08:44Z",
    resultingTenantId: tenantId,
  },
];

function capture(id, spoolFileName, spoolJobName, detectedFormat, byteSize, renderStatus, hasRenderedPdf, capturedAt) {
  return {
    id,
    spoolFileName,
    spoolJobName,
    spoolJobUser: "RSPOOL",
    detectedFormat,
    logicalSegmentIndex: 0,
    sha256: id.replaceAll("-", "").padEnd(64, "0"),
    byteSize,
    renderStatus,
    hasRenderedPdf,
    createdAt: capturedAt,
    capturedAt,
  };
}

function json(route, body, status = 200) {
  return route.fulfill({
    status,
    contentType: "application/json",
    headers: { "cache-control": "no-store" },
    body: JSON.stringify(body),
  });
}

async function mockAdminApi(context) {
  await context.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;

    if (request.method() === "GET" && path === "/api/me") return json(route, operator);
    if (request.method() === "GET" && path === "/api/stats") {
      return json(route, { tenants: 2, pendingSubmissions: 2, captures: 1931, outputQueues: 5 });
    }
    if (request.method() === "GET" && path === "/api/submissions") {
      const status = url.searchParams.get("status");
      return json(route, status ? submissions.filter((item) => item.status === status) : submissions);
    }
    if (request.method() === "GET" && path === "/api/tenants") return json(route, tenants);
    if (request.method() === "GET" && path === `/api/tenants/${tenantId}`) return json(route, tenantDetail);
    if (request.method() === "GET" && path === `/api/tenants/${secondTenantId}`) {
      return json(route, { ...tenantDetail, ...tenants[1], port: 9476, recentCaptures: captures.slice(0, 2) });
    }
    if (request.method() === "GET" && path === `/api/tenants/${tenantId}/captures`) return json(route, captures);
    if (request.method() === "GET" && path === `/api/tenants/${secondTenantId}/captures`) return json(route, captures.slice(0, 2));
    if (request.method() === "POST" && path === "/api/connection/test") {
      return json(route, { success: true, code: "OK", message: "Sign-on succeeded using SecureAS400 over TLS.", elapsedMillis: 684 });
    }
    if (request.method() === "POST" && /\/api\/submissions\/[^/]+\/(approve|reject)$/.test(path)) {
      return json(route, { ok: true });
    }

    return json(route, { error: "fixture_missing", message: `No media fixture for ${request.method()} ${path}` }, 404);
  });
}

async function waitForPreview(server, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (server.exitCode !== null) throw new Error(`Vite preview exited early with code ${server.exitCode}`);
    try {
      const response = await fetch(origin);
      if (response.ok) return;
    } catch {
      // Preview is still starting.
    }
    await delay(250);
  }
  throw new Error(`Timed out waiting for ${origin}`);
}

async function stopPreview(server) {
  if (!server || server.exitCode !== null) return;
  server.kill("SIGTERM");
  await Promise.race([once(server, "exit"), delay(5_000)]);
  if (server.exitCode === null) server.kill("SIGKILL");
}

function delay(ms) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, ms));
}

async function captureView(scene) {
  const errors = [];
  // Give every scene a fresh browser process. Reusing Chromium across several
  // high-DPI screenshots can leave a stale compositor tile behind on some
  // headless macOS hosts (most visible over the translucent left sidebar).
  const browser = await chromium.launch({ args: ["--disable-gpu"] });
  const context = await browser.newContext({
    viewport: scene.viewport,
    deviceScaleFactor: scene.scale ?? 1.5,
    colorScheme: "light",
    reducedMotion: "reduce",
    locale: "en-US",
    timezoneId: "America/Indiana/Indianapolis",
    serviceWorkers: "block",
  });
  await mockAdminApi(context);
  const page = await context.newPage();
  page.on("pageerror", (error) => errors.push(`pageerror: ${error.message}`));
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(`console.error: ${message.text()}`);
  });

  try {
    await page.goto(`${origin}${scene.path}`, { waitUntil: "networkidle" });
    await page.locator("h1").first().waitFor({ state: "visible" });
    await page.addStyleTag({
      content: "*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important}",
    });
    if (scene.prepare) await scene.prepare(page);
    await page.evaluate(() => document.fonts.ready);
    await delay(150);

    if (errors.length) throw new Error(`${scene.name} emitted browser errors:\n${errors.join("\n")}`);
    await page.screenshot({ path: resolve(output, `${scene.name}.png`), fullPage: false });
    console.log(`  ✓ ${scene.name}.png (${scene.viewport.width}×${scene.viewport.height})`);
  } finally {
    await context.close();
    await browser.close();
  }
}

const scenes = [
  { name: "admin-dashboard", path: "/dashboard", viewport: { width: 1440, height: 900 } },
  {
    name: "admin-submissions",
    path: "/submissions?status=PENDING",
    viewport: { width: 1440, height: 960 },
    prepare: async (page) => {
      await page.getByRole("button", { name: "Inspect" }).first().click();
      await page.getByText("securely referenced").first().waitFor();
    },
  },
  { name: "admin-captures", path: `/captures?tenant=${tenantId}`, viewport: { width: 1440, height: 900 } },
  {
    name: "admin-test-connection",
    path: "/test-connection",
    viewport: { width: 1440, height: 900 },
    prepare: async (page) => {
      await page.getByLabel("IBM i host").fill("ibmi.demo.example");
      await page.getByLabel("User profile").fill("RSPOOL");
      await page.getByLabel("Password").fill("demo-password-not-a-secret");
      await page.getByRole("button", { name: "Test connection" }).click();
      await page.getByText("Sign-on succeeded", { exact: true }).waitFor();
    },
  },
  { name: "admin-mobile", path: "/dashboard", viewport: { width: 430, height: 932 }, scale: 2 },
];

const preview = spawn("npm", ["run", "preview", "--", "--host", "127.0.0.1", "--port", "4173", "--strictPort"], {
  cwd: frontend,
  env: { ...process.env, NO_COLOR: "1" },
  stdio: ["ignore", "pipe", "pipe"],
});
let previewLog = "";
preview.stdout.on("data", (chunk) => { previewLog += chunk.toString(); });
preview.stderr.on("data", (chunk) => { previewLog += chunk.toString(); });

try {
  await waitForPreview(preview);
  console.log("Capturing the production RetroSpool console with deterministic demo fixtures:");
  for (const scene of scenes) await captureView(scene);
  console.log(`\nWrote ${scenes.length} real-app screenshots to ${output}`);
} catch (error) {
  if (previewLog.trim()) console.error(`\nVite preview output:\n${previewLog.trim()}`);
  throw error;
} finally {
  await stopPreview(preview);
}
