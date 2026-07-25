#!/usr/bin/env bash
# Smoke tests de la API de eventos de guía.
# Requiere el entorno arriba: docker compose up -d --build
# Uso: ./smoke-test.sh [host]   (default: http://localhost:8080)

set -u

HOST="${1:-http://localhost:8080}"
RABBIT_API="http://localhost:15672/api"
PASS=0
FAIL=0

check() {
  local nombre="$1" esperado="$2" obtenido="$3" detalle="${4:-}"
  if [ "$obtenido" = "$esperado" ]; then
    PASS=$((PASS + 1))
    echo "  OK   $nombre (HTTP $obtenido)"
  else
    FAIL=$((FAIL + 1))
    echo "  FAIL $nombre — esperado HTTP $esperado, obtenido HTTP $obtenido"
    [ -n "$detalle" ] && echo "       $detalle"
  fi
}

echo "== Smoke tests contra $HOST =="

# 1. Health check
code=$(curl -s -o /tmp/smoke_body -w '%{http_code}' "$HOST/actuator/health")
check "health check" 200 "$code" "$(cat /tmp/smoke_body)"

# 2. Evento válido con idempotency key → 202
code=$(curl -s -o /tmp/smoke_body -w '%{http_code}' -X POST "$HOST/api/v1/eventos/guia" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: smoke-$(date +%s)" \
  -d '{
    "numeroGuia": "GUIA-SMOKE-001",
    "estado": "EN_TRANSITO",
    "fechaEvento": "2026-07-24T18:00:00Z",
    "origen": "TMS",
    "metadata": {"ciudad": "Bogotá"}
  }')
check "evento válido → 202" 202 "$code" "$(cat /tmp/smoke_body)"
grep -q '"eventoId"' /tmp/smoke_body \
  && { PASS=$((PASS + 1)); echo "  OK   la respuesta incluye eventoId"; } \
  || { FAIL=$((FAIL + 1)); echo "  FAIL la respuesta no incluye eventoId"; }

# 3. Evento válido sin idempotency key (header opcional) → 202
code=$(curl -s -o /tmp/smoke_body -w '%{http_code}' -X POST "$HOST/api/v1/eventos/guia" \
  -H "Content-Type: application/json" \
  -d '{
    "numeroGuia": "GUIA-SMOKE-002",
    "estado": "ENTREGADA",
    "fechaEvento": "2026-07-24T18:05:00Z",
    "origen": "APP_MOVIL"
  }')
check "evento sin idempotency key → 202" 202 "$code" "$(cat /tmp/smoke_body)"

# 4. Payload inválido (campos vacíos) → 400 con detalle por campo
code=$(curl -s -o /tmp/smoke_body -w '%{http_code}' -X POST "$HOST/api/v1/eventos/guia" \
  -H "Content-Type: application/json" \
  -d '{"numeroGuia": "", "estado": "", "origen": "TMS"}')
check "payload inválido → 400" 400 "$code" "$(cat /tmp/smoke_body)"

# 5. Body malformado (JSON roto) → 400
code=$(curl -s -o /tmp/smoke_body -w '%{http_code}' -X POST "$HOST/api/v1/eventos/guia" \
  -H "Content-Type: application/json" \
  -d '{esto no es json')
check "JSON malformado → 400" 400 "$code"

# 6. El exchange recibió publicaciones (API de management de RabbitMQ).
# Las estadísticas se actualizan cada ~5s, así que se reintenta unas veces.
publicados=""
for intento in 1 2 3 4; do
  publicados=$(curl -s -u guest:guest "$RABBIT_API/exchanges/%2F/guia.eventos" \
    | grep -o '"publish_in":[0-9]*' | grep -o '[0-9]*')
  [ -n "${publicados:-}" ] && [ "$publicados" -ge 2 ] && break
  sleep 3
done
if [ -n "${publicados:-}" ] && [ "$publicados" -ge 2 ]; then
  PASS=$((PASS + 1))
  echo "  OK   exchange guia.eventos recibió $publicados mensajes"
else
  FAIL=$((FAIL + 1))
  echo "  FAIL exchange guia.eventos sin publicaciones suficientes (publish_in=${publicados:-N/A})"
fi

echo
echo "== Resultado: $PASS OK, $FAIL FAIL =="
[ "$FAIL" -eq 0 ] || exit 1
