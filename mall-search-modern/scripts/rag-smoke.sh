#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

post_ask() {
  local question="$1"
  curl -fsS \
    -H 'Content-Type: application/json' \
    -X POST "${BASE_URL}/esProduct/ask" \
    -d "{\"question\":\"${question}\"}"
}

get_semantic() {
  local keyword="$1"
  curl -fsS "${BASE_URL}/esProduct/search/semantic?keyword=${keyword}&pageSize=1"
}

assert_jq() {
  local name="$1"
  local json="$2"
  local filter="$3"
  if jq -e "$filter" >/dev/null <<<"$json"; then
    echo "PASS ${name}"
  else
    echo "FAIL ${name}" >&2
    echo "$json" | jq . >&2
    exit 1
  fi
}

require_cmd curl
require_cmd jq

if ! curl -fsS "${BASE_URL}/v3/api-docs" >/dev/null; then
  echo "mall-search-modern is not reachable at ${BASE_URL}" >&2
  echo "Start it first: cd mall-search-modern && mvn spring-boot:run" >&2
  exit 1
fi

off_topic="$(post_ask '今天上海天气怎么样？')"
assert_jq "off-topic questions return no source products" "$off_topic" \
  '.code == 200 and (.data.sourceProducts | length) == 0 and (.data.answer | contains("商城商品库"))'

short_sleeve="$(post_ask '推荐一件夏天穿的男士短袖')"
assert_jq "short-sleeve recommendation uses apparel sources" "$short_sleeve" \
  '.code == 200 and (.data.sourceProducts | length) >= 1 and ([.data.sourceProducts[].name] | any(contains("短袖")))'

camera_phone="$(post_ask '想买一部拍照好的手机，预算4000以内，推荐哪几款？')"
assert_jq "camera phone recommendation filters unrelated and over-budget products" "$camera_phone" \
  '.code == 200
   and (.data.sourceProducts | length) >= 1
   and ([.data.sourceProducts[].price] | all(. <= 4000))
   and ([.data.sourceProducts[].name] | all((contains("手机") or contains("iPhone") or contains("Redmi") or contains("小米"))))
   and ([.data.sourceProducts[].name] | all((contains("热水器") | not)))'

semantic="$(get_semantic '%E6%8E%A8%E8%8D%90%E4%B8%80%E4%BB%B6%E5%A4%8F%E5%A4%A9%E7%A9%BF%E7%9A%84%E7%94%B7%E5%A3%AB%E7%9F%AD%E8%A2%96')"
assert_jq "semantic search response hides nameVector" "$semantic" \
  '.code == 200 and (.data.list | length) >= 1 and (.data.list[0] | has("nameVector") | not)'

echo "RAG smoke checks passed for ${BASE_URL}"
