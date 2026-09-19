#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP:-localhost:9092}"
TOPICS_FILE="${1:?uso: create-topics.sh <arquivo-com-lista-de-topicos>, ex: domains/cashback/topics.txt}"

if [ ! -f "$TOPICS_FILE" ]; then
  echo "arquivo não encontrado: $TOPICS_FILE" >&2
  exit 1
fi

while IFS= read -r topic; do
  topic="$(echo "$topic" | xargs)" # trim
  [ -z "$topic" ] && continue
  case "$topic" in \#*) continue ;; esac

  docker exec kafka kafka-topics --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists --topic "$topic" --partitions 3 --replication-factor 1
done < "$TOPICS_FILE"

echo "Tópicos criados a partir de $TOPICS_FILE."
