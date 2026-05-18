#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
CASES_FILE="${1:-$(dirname "$0")/rag-eval-cases.jsonl}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

post_ask() {
  local question="$1"
  local body
  body="$(jq -cn --arg question "$question" '{question: $question}')"
  curl -fsS \
    -H 'Content-Type: application/json' \
    -X POST "${BASE_URL}/esProduct/ask" \
    -d "$body"
}

print_failure_debug() {
  local response="$1"
  echo "  response summary:" >&2
  jq -r '
    "  answer: " + (.data.answer // "<missing>"),
    "  sourceProducts:",
    ((.data.sourceProducts // [])[]? | "    - " + (.name // "<missing>") + " | price=" + ((.price // "") | tostring) + " | category=" + (.productCategoryName // ""))
  ' <<<"$response" >&2
}

require_cmd curl
require_cmd jq

if [[ ! -f "$CASES_FILE" ]]; then
  echo "RAG eval cases file not found: ${CASES_FILE}" >&2
  exit 1
fi

if ! curl -fsS "${BASE_URL}/v3/api-docs" >/dev/null; then
  echo "mall-search-modern is not reachable at ${BASE_URL}" >&2
  echo "Start it first: cd mall-search-modern && mvn spring-boot:run" >&2
  exit 1
fi

total=0
passed=0
failed=0

while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" || "${line:0:1}" == "#" ]] && continue

  total=$((total + 1))
  id="$(jq -r '.id' <<<"$line")"
  description="$(jq -r '.description // .id' <<<"$line")"
  question="$(jq -r '.question' <<<"$line")"
  assertion="$(jq -r '.assertion' <<<"$line")"

  if [[ "$id" == "null" || "$question" == "null" || "$assertion" == "null" ]]; then
    echo "FAIL invalid case at line ${total}: each case needs id, question and assertion" >&2
    failed=$((failed + 1))
    continue
  fi

  if ! response="$(post_ask "$question")"; then
    echo "FAIL ${id} - ${description}" >&2
    echo "  request failed for question: ${question}" >&2
    failed=$((failed + 1))
    continue
  fi

  if jq -e "$assertion" >/dev/null <<<"$response"; then
    echo "PASS ${id} - ${description}"
    passed=$((passed + 1))
  else
    echo "FAIL ${id} - ${description}" >&2
    echo "  question: ${question}" >&2
    echo "  assertion: ${assertion}" >&2
    print_failure_debug "$response"
    failed=$((failed + 1))
  fi
done <"$CASES_FILE"

echo "RAG eval summary: ${passed}/${total} passed"

if ((failed > 0)); then
  exit 1
fi
