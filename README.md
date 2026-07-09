# retrospool
<img width="1448" height="1086" alt="image" src="https://github.com/user-attachments/assets/d3fcd073-db2c-4146-bc3a-f557933b1c43" />

**Your AS/400 reports, as PDFs, over the web.** — [site](https://spillers-technology.github.io/RetroSpool/)

Retrospool captures spool files directly from IBM i (AS/400) output queues — no
printer-session emulation, no TN5250E, no Windows box running an ACS session — and
turns them into PDFs you can pull down or ship to S3 / SFTP / FTPS.

The old world: business reports land in an IBM i **output queue** (`OUTQ`), and a
laptop running a HOD-configured printer session "claims" a device to drain them.
Retrospool replaces that with a headless web service: the host queues spool files in
`*READY` whether or not any device is online, so the service just **polls the queue
via [JTOpen](https://github.com/IBM/JTOpen)**, reads the bytes, and takes it from there.

## Get started

```bash
mkdir retrospool && cd retrospool
curl -fsSLO https://raw.githubusercontent.com/Spillers-Technology/RetroSpool/v0.0.3/quickstart/docker-compose.yml
docker compose up -d
curl http://localhost:8080/api/health
```

That pulls the app from **[ghcr.io/spillers-technology/retrospool](https://github.com/Spillers-Technology/RetroSpool/pkgs/container/retrospool)**
and starts the full stack. Prefer a plain jar (bring your own Java 21)? Every
[release](https://github.com/Spillers-Technology/RetroSpool/releases) ships a zip with
a SHA-256 checksum. **[QUICKSTART.md](QUICKSTART.md)** walks through both paths.

## What it does

```
IBM i OUTQ ─▶ poll (JTOpen) ─▶ sniff format ─▶ split concatenated PCL ─▶ store original
             [poller: planned]                                          └▶ render PCL→PDF
                                                                           (GhostPDL sidecar)
                                                    └▶ export fan-out: S3 / SFTP / FTPS
                                                       [planned]
```

The **middle band** — sniff → split → store → render → dedup, plus S3/MinIO landing
storage — is built and verified end-to-end. The **queue poller** that feeds it and the
**export fan-out** that drains it are the next phases (see [Status](#status)).

## See it work

retrospool is a headless service today (the React admin UI is a later phase), so its
surfaces are the REST API and the capture pipeline's output. Every endpoint, field, and
key scheme shown is taken from the code; host names and report contents are mock data.
Regenerate these with [`docs/scripts/capture-product-media.mjs`](docs/scripts/capture-product-media.mjs).

| Capture pipeline | Test Connection |
|---|---|
| ![PCL rendered to PDF beside its capture record](site/assets/capture-pipeline.png) | ![Test Connection API response](site/assets/test-connection.png) |

![Format detection table](site/assets/format-detection.png)

- **Format detection** — spool files are `DEVTYPE(*USERASCII)` opaque byte streams:
  PCL, PDF, plain text, or mystery bytes. Sniffed from the first 16 bytes.
- **PCL splitting** — concatenated reports are split on `ESC E` printer resets,
  guarded by a preceding form feed to dodge false positives inside binary sections.
- **PCL→PDF rendering** — via GhostPDL (`gpcl6`) running as an isolated sidecar
  container behind a tiny HTTP shim. The AGPL renderer never touches the JVM
  classpath (a Gradle `licenseGate` task enforces that on every build). The original
  `.pcl` is always kept alongside the rendered `.pdf`.
- **Text→PDF** — PDFBox, Courier, portrait/landscape auto-selected by line width.
- **Multi-tenant** — one tenant per company; every query is tenant-scoped, dedup is
  within-tenant only, and a reflection test gate fails the build if a repository
  query forgets its `tenantId`.
- **Idempotent capture** — `unique(tenant_id, sha256, segment_index)` makes restarts
  and re-polls safe by construction.

## Stack

Spring Boot 3 / Java 21 (Temurin) · JTOpen · PostgreSQL 16 + Flyway · PDFBox ·
AWS SDK v2 (S3 landing store) · GhostPDL (sidecar container only) · Docker end-to-end
(the dev machine needs no JDK). The SFTP/FTPS exporters (MINA SSHD, Commons Net) and the
React 18 + Vite admin UI are declared/designed but not yet wired — see Status.

## Build it yourself

```bash
# local dependencies: Postgres 16, MinIO, and the PCL render sidecar
docker compose up -d

# build + unit tests + license gate (containerized Gradle)
docker run --rm -v "$PWD:/app" -w /app -v spool-gradle-cache:/home/gradle/.gradle \
  gradle:8.10.2-jdk21 gradle build --no-daemon

# run
java -jar build/libs/retrospool-0.0.3.jar
```

Integration tests (Testcontainers: real Postgres, MinIO, and the actual gpcl6
sidecar built from source) run via `gradle integrationTest` — see
[docs/decisions.md](docs/decisions.md) D-017 for the docker-in-docker invocation.

## Docs

Architecture, data model, the phased plan, and an append-only decision log live in
[docs/](docs/). Start with [docs/architecture.md](docs/architecture.md).

<a id="status"></a>

## Status

**v0.0.3 — pre-1.0.** Phases 0–3 are **complete and verified**; phases 4–7 are designed
but not yet built.

| Capability | State |
|---|---|
| JTOpen connection layer + Test Connection endpoint | **shipped** (mechanism wired & unit-tested; live signon awaits operator host/creds) |
| Persistence model (Flyway V1+V2, tenant-scoped repos, secrets, isolation gate) | **shipped** |
| Capture pipeline (sniff → split → store → PCL→PDF render → dedup) + S3 landing store | **shipped** (end-to-end tested vs real Postgres/MinIO/GhostPDL) |
| Queue poller (scheduled `*READY` drain + watermark) | planned |
| Export destinations (S3 / SFTP / FTPS fan-out + retry) | planned |
| Admin REST API + OIDC, HOD `.ws` submission/approval, React admin UI | planned |
