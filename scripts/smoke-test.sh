#!/usr/bin/env bash
# 通过 Next.js 代理（默认 http://localhost:3000）走一遍核心流程：
# 登录 → 上传 → 提交 A（模拟 GENERATE 失败）→ 提交 B（成功）→ 轮询到终态 → 用户隔离检查。
# 依赖：curl、python3。用法：scripts/smoke-test.sh [base_url]
set -euo pipefail
BASE=${1:-http://localhost:3000}
DIR=$(cd "$(dirname "$0")/.." && pwd)
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }

echo "== 模板列表"; curl -sf "$BASE/api/templates" | json '", ".join(t["title"] for t in d["items"])'
TOKEN=$(curl -sf -X POST "$BASE/api/session" -H 'Content-Type: application/json' -d '{"nickname":"smoke"}' | json 'd["token"]')
AUTH=(-H "Authorization: Bearer $TOKEN")

upload() { curl -sf "${AUTH[@]}" -F templateId=demo-postcard -F "file=@$DIR/samples/$1" "$BASE/api/assets" | json 'd["id"]'; }
A1=$(upload sample-1.jpg); A2=$(upload sample-2.png)
echo "== 非法文件: $(curl -s "${AUTH[@]}" -F templateId=demo-postcard -F "file=@$DIR/samples/not-an-image.png" "$BASE/api/assets" -w ' HTTP %{http_code}')"

curl -sf -X PUT "$BASE/api/dev/simulation" -H 'Content-Type: application/json' -d '{"failStage":"GENERATE","stepDelayMs":1500}' >/dev/null
submit() { curl -sf -X POST "$BASE/api/tasks" "${AUTH[@]}" -H 'Content-Type: application/json' -d "{\"templateId\":\"demo-postcard\",\"assetIds\":[$A1,$A2]}" | json 'd["id"]'; }
TA=$(submit)
curl -sf -X PUT "$BASE/api/dev/simulation" -H 'Content-Type: application/json' -d '{"failStage":"NONE"}' >/dev/null
TB=$(submit)
echo "== 已受理任务 A=#$TA（预期失败） B=#$TB（预期成功）"

for i in $(seq 1 30); do
  SA=$(curl -sf "${AUTH[@]}" "$BASE/api/tasks/$TA" | json 'd["status"]+"/"+str(d.get("stage"))')
  SB=$(curl -sf "${AUTH[@]}" "$BASE/api/tasks/$TB" | json 'd["status"]+"/"+str(d.get("stage"))')
  echo "  t=${i}s  A=$SA  B=$SB"
  [[ $SA == FAILED* && $SB == SUCCEEDED* ]] && break
  sleep 1
done
curl -sf "${AUTH[@]}" "$BASE/api/tasks/$TA" | json '"A 失败阶段=%s 原因=%s" % (d["failedStage"], d["errorMessage"])'
RESULT=$(curl -sf "${AUTH[@]}" "$BASE/api/tasks/$TB" | json 'd["resultUrl"]')
echo "B 结果=$RESULT  HTTP $(curl -s -o /dev/null -w '%{http_code} %{content_type}' "$BASE$RESULT")"

OTHER=$(curl -sf -X POST "$BASE/api/session" -H 'Content-Type: application/json' -d '{"nickname":"smoke-other"}' | json 'd["token"]')
echo "== 其他用户读取任务 A: HTTP $(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $OTHER" "$BASE/api/tasks/$TA")（预期 404）"
