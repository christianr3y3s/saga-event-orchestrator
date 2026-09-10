#!/usr/bin/env bash
set -euo pipefail

DOMAIN="${1:?uso: run-domain.sh <cashback|nfe|...>}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

BASE="$ROOT/engine/saga-orchestrator-rust/base.properties"
DOMAIN_PROPS="$ROOT/domains/$DOMAIN/orchestrator.$DOMAIN.properties"

if [ ! -f "$DOMAIN_PROPS" ]; then
  echo "não encontrei $DOMAIN_PROPS -- domínio '$DOMAIN' existe em domains/?" >&2
  exit 1
fi

MERGED="$(mktemp)"
cat "$BASE" "$DOMAIN_PROPS" > "$MERGED"

echo "Rodando o orquestrador para o domínio '$DOMAIN'"
echo "  base:    $BASE"
echo "  domínio: $DOMAIN_PROPS"
echo "  combinado em: $MERGED"
echo

cd "$ROOT/engine/saga-orchestrator-rust"
ORCH_CONFIG="$MERGED" cargo run
