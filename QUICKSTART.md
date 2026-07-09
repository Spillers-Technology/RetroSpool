# RetroSpool Quickstart

**Your AS/400 reports, as PDFs, over the web.** RetroSpool talks directly to your
IBM i output queues — no printer-session emulation, no dedicated Windows box babysitting
an ACS session — and turns spool files into PDFs.

Two ways to run it. If you have Docker, use Docker — it's one file and one command.

> **Where the project is today (v0.0.3, pre-1.0):** the capture engine — format
> sniffing, PCL splitting, PCL→PDF rendering, tenant-scoped storage — is built and
> verified end-to-end, and you can test-connect to your IBM i right now. The scheduled
> queue poller, export fan-out (S3/SFTP/FTPS), and the web admin UI are the next
> phases — see the [roadmap](README.md#status). Kick the tires now; don't point it at
> production quite yet.

---

## Option 1 — Docker (recommended)

You need: [Docker](https://docs.docker.com/get-docker/) with Compose. That's it.

```bash
mkdir retrospool && cd retrospool
curl -fsSLO https://raw.githubusercontent.com/Spillers-Technology/RetroSpool/v0.0.3/quickstart/docker-compose.yml
docker compose up -d
```

This pulls RetroSpool from `ghcr.io/spillers-technology/retrospool` and starts
everything it needs (PostgreSQL, object storage, the PDF renderer) — all internal,
nothing exposed except the API on port 8080.

Like to live dangerously? The same thing as one line, with a health-check wait and
friendlier output ([read it first](quickstart/get.sh), it's short):

```bash
curl -fsSL https://raw.githubusercontent.com/Spillers-Technology/RetroSpool/main/quickstart/get.sh | bash
```

> ☕ **The first start takes a few minutes** — it compiles the PCL→PDF renderer
> (GhostPDL) from source on your machine. That's a deliberate licensing choice, not an
> accident: the renderer is AGPL, so RetroSpool ships it as source and never as a
> binary. Every start after the first is instant.

### Say hello

```bash
curl http://localhost:8080/api/health
```

### Test-connect to your IBM i

```bash
curl -X POST http://localhost:8080/api/connection/test \
  -H "Content-Type: application/json" \
  -d '{"host": "your-ibmi-host", "username": "MYUSER", "password": "...", "useSsl": true}'
```

You get back a pass/fail with the actual reason (bad credentials vs. unreachable host
vs. TLS trouble) — the same signon path the capture engine uses.

Stop everything with `docker compose down` (add `-v` to also wipe the data).

---

## Option 2 — Run the jar

Prefer to run it as a plain Java service? Each [release](https://github.com/Spillers-Technology/RetroSpool/releases)
ships `retrospool-x.y.z.zip` with the runnable jar inside.

**You need Java 21.** Any OpenJDK 21 build works; we build, test, and ship on
**[Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)** — free,
open source (GPLv2 + Classpath Exception), no strings attached. We deliberately don't
use or recommend the Oracle-branded JDK, whose license has commercial-use terms you'd
have to read a lawyer's opinion on. Temurin means you never have to think about it.

```bash
# 1. Download the zip and its checksum from the releases page, then verify:
sha256sum -c retrospool-0.0.3.zip.sha256        # Linux
shasum -a 256 -c retrospool-0.0.3.zip.sha256    # macOS
# Windows (PowerShell): compare the two outputs
#   Get-FileHash retrospool-0.0.3.zip; Get-Content retrospool-0.0.3.zip.sha256

# 2. Unzip. The bundle includes a docker-compose.yml for the supporting services
#    (PostgreSQL, MinIO, and the PDF renderer built from the bundled source):
cd retrospool-0.0.3
docker compose up -d

# 3. Run it:
java -jar retrospool-0.0.3.jar
```

The app reads its configuration from environment variables — `DB_URL`, `DB_USER`,
`DB_PASSWORD`, `RENDER_URL`, and the `STORAGE_*` family. The defaults match the
bundled compose file, so with it running you can start the jar with no configuration
at all.

---

## What's in the box

| Piece | What it does |
|---|---|
| `ghcr.io/spillers-technology/retrospool` | The service: REST API + capture engine (Spring Boot, Java 21) |
| PostgreSQL 16 | Capture records, tenants, audit trail (schema managed by Flyway) |
| MinIO | S3-compatible landing store for the captured spool files |
| Render sidecar | GhostPDL `gpcl6` behind a tiny HTTP shim — turns PCL into PDF |

Questions, ideas, something broken? [Open an issue](https://github.com/Spillers-Technology/RetroSpool/issues).
