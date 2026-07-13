# CONTRIBUTING.md

Guidance for working in this repository.

## What this is

**Retrospool** — a Linux-hosted web service that captures spool files directly from
IBM i (AS/400) output queues via **JTOpen** (no printer-session emulation), renders
PCL to PDF via a **GhostPDL sidecar container**, and exports captures to
S3 / SFTP / FTPS. In one sentence: *ACS print session as a web service — spooled
reports show up as PDFs you can pull down.* A React admin UI manages tenants
(one per company), with a standalone HOD `.ws` import + submission flow.

Backend: Spring Boot 3.x + JTOpen, Temurin 21, Gradle (Kotlin DSL), PostgreSQL 16.
Frontend: React 18 + Vite + TypeScript + TanStack Query + Tailwind.

## Documentation is load-bearing — keep it in sync

This project is being built from a detailed solutioning effort. **All architecture,
decisions, schema, and the phased plan live in `docs/` and MUST be kept current.**

When you make a change that affects design, schema, scope, or sequencing:

1. Update the relevant file in `docs/` **in the same change** as the code.
2. If you make or revise a decision, add/amend an entry in
   [docs/decisions.md](docs/decisions.md) (append-only log — supersede, don't delete).
3. If you touch the data model, update [docs/data-model.md](docs/data-model.md).
4. If scope or phase ordering shifts, update [docs/implementation-plan.md](docs/implementation-plan.md).
5. Keep this file's "What this is" accurate.

Treat docs drift as a bug. A PR/commit that changes behavior without updating docs
is incomplete.

## Doc map

- [docs/architecture.md](docs/architecture.md) — system shape, two-surface model, tenant isolation, capture lifecycle.
- [docs/implementation-plan.md](docs/implementation-plan.md) — phased plan, milestones, v0.0.1 definition.
- [docs/data-model.md](docs/data-model.md) — Postgres schema + entity notes.
- [docs/decisions.md](docs/decisions.md) — decision log (what was decided and why; supersede, don't rewrite history).

## Hard constraints (do not relitigate — see docs/decisions.md)

- **No printer-session / TN5250E emulation.** Pure JTOpen against output queues.
- **No Oracle JDK / GraalVM Enterprise / AGPL-linked libs.** Production licenses
  limited to Apache-2.0, MIT, BSD, EPL, MPL, GPL-with-Classpath-Exception, IBM Public
  License (JTOpen), PostgreSQL License. MinIO is used as an external S3 service only
  (never linked).
- **No Ghostscript / GhostPDL on the JVM classpath — ever.** PCL→PDF rendering happens
  in the **`render-sidecar/` container** (unmodified AGPL `gpcl6` behind a tiny HTTP
  shim — the MinIO precedent; D-018 supersedes D-003). Enforced by the `licenseGate`
  Gradle task. Both artifacts kept (`.pcl` original + rendered `.pdf`); a render
  failure never fails the capture.
- **Tenant isolation is mandatory.** Every read/write scoped by `tenant_id`. Dedup is
  within-tenant only. Never cross company data.
- **Secrets are write-only at the boundary.** UI shows `•••• (set)`; values never read back.

## JTOpen non-negotiables

Build every `AS400` through `As400Factory` with `setGuiAvailable(false)` and
`setMustUseSockets(true)` (critical on Linux); `setUseSSL(true)` when configured.

## Build & run

The dev workstation has **no host JDK**; build and run through Docker (Temurin). See
docs/decisions.md D-014.

```bash
# Compile + unit tests
docker run --rm -v "$PWD:/app" -w /app -v spool-gradle-cache:/home/gradle/.gradle \
  gradle:8.10.2-jdk21 gradle build --no-daemon --console=plain

# Local dependencies (Postgres + MinIO + render sidecar on host port 8085)
docker compose up -d        # NOTE: host port 5432 may already be in use

# Integration tests (Testcontainers; needs the Docker socket + envs — see D-017)
docker run --rm \
  -e DOCKER_HOST=unix:///var/run/docker.sock \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v "$PWD:/app" -w /app -v spool-gradle-cache:/home/gradle/.gradle \
  -v /var/run/docker.sock:/var/run/docker.sock \
  gradle:8.10.2-jdk21 gradle integrationTest --no-daemon --console=plain

# Run the app jar against Postgres (DB_URL/DB_USER/DB_PASSWORD env)
java -jar build/libs/retrospool-0.1.0.jar --retrospool.admin.dev-user=local-admin
# or build the production image:
docker build -t retrospool .
```

Endpoints: `GET /api/health`, `GET /actuator/health`, `POST /api/connection/test`
(`{host, username, password, useSsl}` → pass/fail + reason).

## Releasing

1. Bump `version` in `build.gradle.kts`; bump the image tag and git ref pins in
   `quickstart/docker-compose.yml` and the version references in `QUICKSTART.md` /
   `README.md`; update `CHANGELOG.md`. Commit to `main`.
2. Tag and push: `git tag vX.Y.Z && git push origin vX.Y.Z`.
3. The `release` workflow builds in CI (unit tests + `licenseGate`), then:
   - assembles `retrospool-X.Y.Z.zip` (jar, compose file, quickstart doc,
     render-sidecar source, docs) with a `.sha256` checksum and attaches both to the
     GitHub release — creating a **draft** release if the tag doesn't have one yet;
   - publishes the app image to `ghcr.io/spillers-technology/retrospool`
     (`X.Y.Z` + `latest`).
4. Write the release notes, then publish the draft.

To rebuild assets for an existing tag, dispatch the workflow manually:
`gh workflow run release.yml -f tag=vX.Y.Z`.

Only source and the built jar ship in the zip, and only the app image goes to GHCR;
GhostPDL is never distributed as a binary — end users build the sidecar container
from source (D-018, D-020).

## Conventions

- Temp/scratch files go in the session scratchpad, never in the repo.
- Enforce the licensing + tenant-isolation constraints as CI/test gates, not just prose.
