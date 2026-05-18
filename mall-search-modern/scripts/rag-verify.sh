#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

service_started=0
service_pid=""
log_file=""

cleanup() {
  if (( service_started )); then
    if [[ -n "${service_pid}" ]] && kill -0 "${service_pid}" 2>/dev/null; then
      kill "${service_pid}" 2>/dev/null || true
      wait "${service_pid}" 2>/dev/null || true
    fi
  fi
  if [[ -n "${log_file}" && -f "${log_file}" ]]; then
    rm -f "${log_file}"
  fi
}

wait_for_service() {
  local attempts=120
  local delay=1

  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS "${BASE_URL}/v3/api-docs" >/dev/null; then
      return 0
    fi

    if [[ -n "${service_pid}" ]] && ! kill -0 "${service_pid}" 2>/dev/null; then
      echo "mall-search-modern exited before it became ready" >&2
      if [[ -f "${log_file}" ]]; then
        tail -n 80 "${log_file}" >&2 || true
      fi
      exit 1
    fi

    sleep "${delay}"
  done

  echo "Timed out waiting for mall-search-modern at ${BASE_URL}" >&2
  if [[ -f "${log_file}" ]]; then
    tail -n 80 "${log_file}" >&2 || true
  fi
  exit 1
}

run_checks() {
  "${SCRIPT_DIR}/rag-smoke.sh"
  "${SCRIPT_DIR}/rag-eval.sh"
}

trap cleanup EXIT

if curl -fsS "${BASE_URL}/v3/api-docs" >/dev/null; then
  echo "Using existing mall-search-modern at ${BASE_URL}"
  run_checks
  exit 0
fi

log_file="$(mktemp -t mall-search-modern-rag.XXXXXX.log)"
echo "Starting mall-search-modern for RAG verification"
echo "  log: ${log_file}"
(cd "${PROJECT_DIR}" && mvn spring-boot:run >"${log_file}" 2>&1) &
service_pid=$!
service_started=1

wait_for_service
run_checks

echo "RAG verification passed"
