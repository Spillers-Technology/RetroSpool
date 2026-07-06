// Repeatable capture of RetroSpool product media.
//
// RetroSpool is a headless service — there is no GUI yet (the React admin UI is
// planned, Phase 7). So the genuine, screenshottable product surfaces today are:
//   1. the REST API (Test Connection + health), and
//   2. the capture pipeline's output (a spool file -> stored original + rendered PDF).
//
// Every endpoint, field name, enum value, JSON shape and storage-key scheme in the
// scenes below is taken verbatim from the implementation (see
// src/main/java/io/retrospool/...). Host names, UUIDs and report contents are
// realistic mock data — no real host or customer is depicted.
//
// Output: PNGs written to site/assets/ (the GitHub Pages artifact root).
//
// Run:  cd docs/scripts && npm install && npm run capture

import { chromium } from "playwright";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { mkdirSync } from "node:fs";

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, "..", "..", "site", "assets");
mkdirSync(OUT, { recursive: true });

// ---- shared styling ---------------------------------------------------------

const BASE = /* css */ `
  * { box-sizing: border-box; }
  html, body { margin: 0; }
  body { font-family: "Courier New", Courier, monospace; -webkit-font-smoothing: antialiased; }
  .frame { display: inline-block; }
  .win {
    border: 1px solid var(--rule);
    border-radius: 8px;
    overflow: hidden;
    box-shadow: 0 18px 50px rgba(0,0,0,.30);
  }
  .titlebar {
    display: flex; align-items: center; gap: .55rem;
    padding: .55rem .8rem;
    border-bottom: 1px solid var(--rule);
    font-size: .82rem; letter-spacing: .02em;
  }
  .dot { width: .72rem; height: .72rem; border-radius: 50%; display: inline-block; }
  .dot.r { background: #e0605e; }
  .dot.y { background: #e6b94b; }
  .dot.g { background: #4fb06a; }
  .title { margin-left: .5rem; opacity: .8; }
  .badge {
    margin-left: auto; font-size: .68rem; letter-spacing: .1em; text-transform: uppercase;
    padding: .12rem .5rem; border: 1px solid var(--rule); border-radius: 999px;
  }
  pre { margin: 0; white-space: pre; }
  .k { color: var(--key); }
  .s { color: var(--str); }
  .n { color: var(--num); }
  .b { color: var(--bool); }
  .c { color: var(--comment); }
  .prompt { color: var(--accent); }
`;

// dark "terminal" palette (phosphor green) for the API scenes
const TERM = /* css */ `
  --paper:#0a0f0b; --panel:#0c140e; --rule:#1e3a28; --accent:#7dde9a;
  --ink:#bfe9cd; --muted:#4e9a68; --key:#7dde9a; --str:#a6f4be; --num:#e6c07b;
  --bool:#c58af9; --comment:#4e9a68;
`;

// light "greenbar printout" palette for the pipeline scene
const BAR = /* css */ `
  --paper:#eef4ea; --panel:#fdfdf8; --rule:#9dbfa6; --accent:#1c6b3c;
  --ink:#1d2b23; --muted:#43584b; --key:#1c6b3c; --str:#2a6f4e; --num:#8a5a12;
  --bool:#6d3bd0; --comment:#6d8a76;
`;

function page(vars, bg, inner) {
  return `<!doctype html><html><head><meta charset="utf-8"><style>
    :root{${vars}} body{background:${bg}; padding:38px;} ${BASE}
  </style></head><body>${inner}</body></html>`;
}

// ---- scene 1: Test Connection + health (REST API, Phase 1 — shipped) --------

const sceneConnection = page(
  TERM,
  "radial-gradient(120% 120% at 20% 0%, #10231a 0%, #060a07 60%)",
  /* html */ `
  <div class="frame"><div class="win" style="width:1000px; background:var(--panel); color:var(--ink)">
    <div class="titlebar" style="background:#0c140e; color:var(--ink)">
      <span class="dot r"></span><span class="dot y"></span><span class="dot g"></span>
      <span class="title">retrospool — Test Connection</span>
      <span class="badge" style="color:var(--accent)">API · shipped</span>
    </div>
    <div style="padding:1.1rem 1.3rem; font-size:.9rem; line-height:1.65">
<pre><span class="prompt">$</span> curl -sS -X POST https://retrospool.internal<span class="k">/api/connection/test</span> \\
    -H <span class="s">'content-type: application/json'</span> \\
    -d <span class="s">'{"host":"ISERIES.acme.example","username":"RSPOOL",
        "password":"••••••••","useSsl":true}'</span>

<span class="c"># SecureAS400 signon over TLS · GuiAvailable=false · MustUseSockets=true</span>
{
  <span class="k">"success"</span>: <span class="b">true</span>,
  <span class="k">"code"</span>: <span class="s">"OK"</span>,
  <span class="k">"message"</span>: <span class="s">"Signon succeeded (SecureAS400, TLS)"</span>,
  <span class="k">"elapsedMillis"</span>: <span class="n">842</span>
}

<span class="prompt">$</span> curl -sS https://retrospool.internal<span class="k">/api/health</span>
{ <span class="k">"status"</span>: <span class="s">"UP"</span>, <span class="k">"service"</span>: <span class="s">"retrospool"</span>, <span class="k">"time"</span>: <span class="s">"2026-07-05T14:22:07Z"</span> }</pre>
      <div style="margin-top:1.1rem; padding-top:.9rem; border-top:1px dashed var(--rule); color:var(--muted); font-size:.8rem">
        code &rarr; <span class="s">OK</span> · INVALID_CREDENTIALS · SECURITY_ERROR · CONNECTIVITY_ERROR · ERROR &nbsp;·&nbsp;
        raw credentials are never persisted — the password array is scrubbed after signon.
      </div>
    </div>
  </div></div>`
);

// ---- scene 2: capture pipeline output (Phase 3 — shipped) -------------------

const reportLines = [
  "  ACME DISTRIBUTION INC              STATEMENT OF ACCOUNT",
  "  OUTQ: PRT01            JOB: MONTHEND/RSPOOL/482913",
  "  ------------------------------------------------------------",
  "  ACCOUNT   0042-118        AS OF 2026-06-30        PAGE 1",
  "",
  "  INVOICE    DATE        DUE         AMOUNT      BALANCE",
  "  IN-88213   06-02-2026  07-02-2026   1,204.50    1,204.50",
  "  IN-88291   06-11-2026  07-11-2026     318.00    1,522.50",
  "  IN-88344   06-19-2026  07-19-2026   2,940.75    4,463.25",
  "  CR-01120   06-22-2026  --           -150.00     4,313.25",
  "  ------------------------------------------------------------",
  "  CURRENT      30 DAYS     60 DAYS     90+ DAYS    TOTAL DUE",
  "  4,313.25         0.00        0.00        0.00     4,313.25",
  "",
  "  REMIT TO: LOCKBOX 7741  ·  TERMS NET 30  ·  FORM S/AR-STMT",
].join("\n");

const scenePipeline = page(
  BAR,
  "repeating-linear-gradient(#dcedd8 0 26px, #eef4ea 26px 52px)",
  /* html */ `
  <div class="frame"><div class="win" style="width:1120px; background:var(--panel); color:var(--ink)">
    <div class="titlebar" style="background:#e7f1e3; color:var(--ink)">
      <span class="dot r"></span><span class="dot y"></span><span class="dot g"></span>
      <span class="title">retrospool — capture pipeline</span>
      <span class="badge" style="color:var(--accent)">PCL → PDF · shipped</span>
    </div>
    <div style="padding:.7rem 1.1rem; border-bottom:1px dashed var(--rule); font-size:.78rem; color:var(--muted); letter-spacing:.02em">
      OUTQ bytes &nbsp;▸&nbsp; <b style="color:var(--accent)">sniff</b> &nbsp;▸&nbsp; <b style="color:var(--accent)">split</b> (ESC E) &nbsp;▸&nbsp; store original <code>.pcl</code> &nbsp;▸&nbsp; <b style="color:var(--accent)">render</b> (GhostPDL sidecar) &nbsp;▸&nbsp; <code>.pdf</code> sibling
    </div>
    <div style="display:grid; grid-template-columns: 1.02fr 1fr; gap:0">
      <!-- rendered PDF preview -->
      <div style="padding:1.1rem 1.2rem; border-right:1px dashed var(--rule)">
        <div style="font-size:.72rem; text-transform:uppercase; letter-spacing:.12em; color:var(--muted); margin-bottom:.5rem">rendered .pdf</div>
        <div style="background:#fff; border:1px solid var(--rule); box-shadow:0 6px 18px rgba(0,0,0,.12); padding:.9rem 1rem">
          <pre style="font-size:.66rem; line-height:1.5; color:#1a1a1a">${reportLines}</pre>
        </div>
      </div>
      <!-- capture record -->
      <div style="padding:1.1rem 1.2rem">
        <div style="font-size:.72rem; text-transform:uppercase; letter-spacing:.12em; color:var(--muted); margin-bottom:.5rem">capture record</div>
<pre style="font-size:.78rem; line-height:1.62">{
  <span class="k">"detectedFormat"</span>:      <span class="s">"PCL"</span>,
  <span class="k">"logicalSegmentIndex"</span>: <span class="n">0</span>,
  <span class="k">"sha256"</span>:              <span class="s">"9f2a…c7e1"</span>,
  <span class="k">"byteSize"</span>:            <span class="n">48213</span>,
  <span class="k">"spoolJobName"</span>:        <span class="s">"MONTHEND"</span>,
  <span class="k">"spoolFileName"</span>:       <span class="s">"AR_STATEMENTS"</span>,
  <span class="k">"storageKey"</span>:
    <span class="s">"6a1e…/2026/07/05/b2f1c8e4-0.pcl"</span>,
  <span class="k">"renderedStorageKey"</span>:
    <span class="s">"6a1e…/2026/07/05/b2f1c8e4-0.pcl.pdf"</span>,
  <span class="k">"renderStatus"</span>:        <span class="s">"SUCCESS"</span>
}</pre>
        <div style="margin-top:.9rem; padding-top:.7rem; border-top:1px dashed var(--rule); font-size:.76rem; color:var(--muted)">
          <span class="c"># audit_event</span> &nbsp; <span class="s">CAPTURE_CREATED</span> &nbsp;·&nbsp; tenant-scoped &nbsp;·&nbsp;
          dedup <code>unique(tenant_id, sha256, segment)</code>
        </div>
      </div>
    </div>
  </div></div>`
);

// ---- scene 3: format detection (documented behaviour, Phase 3) --------------

const sceneSniff = page(
  TERM,
  "radial-gradient(120% 120% at 80% 0%, #10231a 0%, #060a07 60%)",
  /* html */ `
  <div class="frame"><div class="win" style="width:960px; background:var(--panel); color:var(--ink)">
    <div class="titlebar" style="background:#0c140e; color:var(--ink)">
      <span class="dot r"></span><span class="dot y"></span><span class="dot g"></span>
      <span class="title">retrospool — format detection</span>
      <span class="badge" style="color:var(--accent)">first 16 bytes</span>
    </div>
    <div style="padding:1.1rem 1.3rem; font-size:.86rem; line-height:1.85">
<pre>  signature                       format
  <span class="c">───────────────────────────────────────────</span>
  <span class="s">%PDF-</span>                           <span class="b">PDF</span>    <span class="c">→ passthrough (SKIPPED)</span>
  <span class="n">1B 45</span>  <span class="c">(ESC E)</span>              <span class="b">PCL</span>    <span class="c">→ render .pdf sibling</span>
  <span class="n">1B 25</span>  <span class="c">(ESC %)</span>              <span class="b">PCL</span>    <span class="c">XL / PJL</span>
  <span class="n">1B 26</span>  <span class="c">(ESC &amp;)</span>              <span class="b">PCL</span>
  printable ASCII + whitespace    <span class="b">TEXT</span>   <span class="c">→ PDFBox (Courier)</span>
  otherwise                       <span class="b">UNKNOWN</span> <span class="c">→ store .bin</span></pre>
      <div style="margin-top:1rem; padding-top:.85rem; border-top:1px dashed var(--rule); color:var(--muted); font-size:.8rem">
        concatenated PCL is split on <code>ESC E</code> — but only when preceded by a form feed
        (<code>0x0C</code>) or at offset 0, to dodge false positives inside binary sections.
        The original bytes are always kept; render failure never fails the capture.
      </div>
    </div>
  </div></div>`
);

// ---- render ----------------------------------------------------------------

const scenes = [
  { name: "test-connection", html: sceneConnection },
  { name: "capture-pipeline", html: scenePipeline },
  { name: "format-detection", html: sceneSniff },
];

const browser = await chromium.launch();
const ctx = await browser.newContext({ deviceScaleFactor: 2 });
const pageObj = await ctx.newPage();

for (const scene of scenes) {
  await pageObj.setContent(scene.html, { waitUntil: "networkidle" });
  const el = await pageObj.$(".frame");
  const target = resolve(OUT, `${scene.name}.png`);
  await el.screenshot({ path: target });
  console.log(`  ✓ ${scene.name}.png`);
}

await browser.close();
console.log(`\nWrote ${scenes.length} images to ${OUT}`);
