#!/usr/bin/env bash
# RetroSpool one-liner, for those who like to live dangerously:
#
#   curl -fsSL https://raw.githubusercontent.com/Spillers-Technology/RetroSpool/main/quickstart/get.sh | bash
#
# Makes a ./retrospool directory, downloads the quickstart compose file, and starts
# the stack. Prefer to see what you're running first? That's the wiser path — it's
# the two commands in QUICKSTART.md, and this script does nothing more.
set -euo pipefail

command -v docker >/dev/null 2>&1 || {
  echo "RetroSpool needs Docker: https://docs.docker.com/get-docker/" >&2; exit 1;
}
docker compose version >/dev/null 2>&1 || {
  echo "RetroSpool needs Docker Compose v2 (the 'docker compose' plugin)." >&2; exit 1;
}

mkdir -p retrospool && cd retrospool
curl -fsSLO https://raw.githubusercontent.com/Spillers-Technology/RetroSpool/main/quickstart/docker-compose.yml

echo "Starting RetroSpool. First run compiles the PCL->PDF renderer from source"
echo "(a few minutes — a licensing choice, not an accident; see QUICKSTART.md)."
docker compose up -d

printf "Waiting for the API on http://localhost:8080 "
for _ in $(seq 1 90); do
  if curl -fs http://localhost:8080/api/health >/dev/null 2>&1; then
    echo
    echo "RetroSpool is up:  curl http://localhost:8080/api/health"
    echo "Next steps:        https://github.com/Spillers-Technology/RetroSpool/blob/main/QUICKSTART.md"
    exit 0
  fi
  printf "."
  sleep 2
done

echo
echo "Still starting (the renderer compile can take a while). Check on it with:"
echo "  docker compose ps"
echo "  docker compose logs -f retrospool"
