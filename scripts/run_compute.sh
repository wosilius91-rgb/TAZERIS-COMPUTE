#!/usr/bin/env bash
set -euo pipefail

PROMPT_FILE="${1:-prompt.txt}"
RESULT_FILE="${2:-result.txt}"

rm -rf /tmp/llama-bin /tmp/models /tmp/llama.tar.gz
mkdir -p /tmp/llama-bin /tmp/models

echo "=== FIND LATEST LLAMA UBUNTU X64 ==="

LLAMA_URL="$(
python3 - <<'PY'
import json, urllib.request

req=urllib.request.Request(
    "https://api.github.com/repos/ggml-org/llama.cpp/releases/latest",
    headers={
        "Accept":"application/vnd.github+json",
        "User-Agent":"TAZERIS-COMPUTE"
    }
)

with urllib.request.urlopen(req, timeout=30) as r:
    d=json.load(r)

assets=d.get("assets",[])

matches=[
    a["browser_download_url"]
    for a in assets
    if a.get("name","").endswith("bin-ubuntu-x64.tar.gz")
]

if not matches:
    raise SystemExit("STOP: Ubuntu x64 llama binary asset nerastas")

print(matches[0])
PY
)"

echo "$LLAMA_URL"

curl -L --fail --retry 3 \
  -o /tmp/llama.tar.gz \
  "$LLAMA_URL"

tar -xzf /tmp/llama.tar.gz -C /tmp/llama-bin

LLAMA_CLI="$(find /tmp/llama-bin -type f -name llama-cli | head -1)"

[ -n "$LLAMA_CLI" ] || {
  echo "STOP: llama-cli nerastas pakete"
  find /tmp/llama-bin -maxdepth 3 -type f | head -100
  exit 1
}

chmod +x "$LLAMA_CLI"

echo "=== LLAMA CLI ==="
"$LLAMA_CLI" --version

echo "=== MODEL ==="

curl -L --fail --retry 3 \
  -o /tmp/models/qwen.gguf \
  "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf"

PROMPT="$(cat "$PROMPT_FILE")"

echo "=== COMPUTE ==="

"$LLAMA_CLI" \
  -m /tmp/models/qwen.gguf \
  -t 4 \
  -c 4096 \
  -n 400 \
  --temp 0 \
  -p "$PROMPT" \
  > "$RESULT_FILE"

test -s "$RESULT_FILE"

echo "TAZERIS REMOTE COMPUTE: OK"
