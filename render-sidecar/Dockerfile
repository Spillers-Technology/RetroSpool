# PCL->PDF render sidecar (docs/decisions.md D-018).
#
# GhostPDL (gpcl6) is AGPL — it runs here as an external network service and is NEVER
# linked into the JVM (same posture as MinIO, D-002). Unmodified upstream source, built
# from the pinned release tarball because distro packages ship gs but not gpcl6.
# Source: https://github.com/ArtifexSoftware/ghostpdl-downloads

FROM debian:bookworm-slim AS build

ARG GHOSTPDL_VERSION=10.03.1
ARG GHOSTPDL_TAG=gs10031

RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/ghostpdl.tar.gz \
        "https://github.com/ArtifexSoftware/ghostpdl-downloads/releases/download/${GHOSTPDL_TAG}/ghostpdl-${GHOSTPDL_VERSION}.tar.gz" \
    && mkdir /src \
    && tar -xzf /tmp/ghostpdl.tar.gz -C /src --strip-components=1 \
    && rm /tmp/ghostpdl.tar.gz

WORKDIR /src
RUN ./configure --without-x && make -j"$(nproc)" gpcl6 && strip bin/gpcl6

FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends python3 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --no-create-home render

COPY --from=build /src/bin/gpcl6 /usr/local/bin/gpcl6
COPY server.py /opt/render/server.py

USER render
EXPOSE 8080
ENTRYPOINT ["python3", "/opt/render/server.py"]
