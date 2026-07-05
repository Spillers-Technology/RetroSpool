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

## What it does

```
IBM i OUTQ ─▶ poll (JTOpen) ─▶ sniff format ─▶ split concatenated PCL ─▶ store original
                                                                        └▶ render PCL→PDF
                                                                           (GhostPDL sidecar)
                                                    └▶ export fan-out: S3 / SFTP / FTPS
```

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
AWS SDK v2 · GhostPDL (sidecar container only) · React 18 + Vite (admin UI, planned) ·
Docker end-to-end (the dev machine needs no JDK).

## Run it

```bash
# local dependencies: Postgres 16, MinIO, and the PCL render sidecar
docker compose up -d

# build + unit tests + license gate (containerized Gradle)
docker run --rm -v "$PWD:/app" -w /app -v spool-gradle-cache:/home/gradle/.gradle \
  gradle:8.10.2-jdk21 gradle build --no-daemon

# run
java -jar build/libs/retrospool-0.0.2.jar
```

Integration tests (Testcontainers: real Postgres, MinIO, and the actual gpcl6
sidecar built from source) run via `gradle integrationTest` — see
[docs/decisions.md](docs/decisions.md) D-017 for the docker-in-docker invocation.

## Docs

Architecture, data model, the phased plan, and an append-only decision log live in
[docs/](docs/). Start with [docs/architecture.md](docs/architecture.md).

## Status

Phases 0–3 complete: connection layer (SSL signon via `SecureAS400`, Test Connection
endpoint), full persistence model, and the capture pipeline with rendering — all
verified against real Postgres/MinIO/GhostPDL in CI-style integration tests. Next up:
the queue poller (needs a live host), export destinations, admin API, and the React UI.
