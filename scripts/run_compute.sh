#!/usr/bin/env bash
set -euo pipefail

PROMPT_FILE="${1:-prompt.txt}"
RESULT_FILE="${2:-result.txt}"

rm -rf /tmp/llama-bin /tmp/models /tmp/llama.tar.gz
mkdir -p /tmp/llama-bin /tmp/models

echo "=== FIND LATEST LLAMA UBUNTU X64 ==="

LLAMA_URL="$(
python3 - <<'PY2'
import json, urllib.request

headers={
    "Accept":"application/vnd.github+json",
    "User-Agent":"TAZERIS-COMPUTE"
}

found=None

for page in range(1,6):
    req=urllib.request.Request(
        f"https://api.github.com/repos/ggml-org/llama.cpp/releases?per_page=20&page={page}",
        headers=headers
    )

    with urllib.request.urlopen(req, timeout=30) as r:
        releases=json.load(r)

    for rel in releases:
        for a in rel.get("assets",[]):
            name=a.get("name","")

            if (
                name.startswith("llama-")
                and name.endswith("-bin-ubuntu-x64.tar.gz")
                and "openvino" not in name
                and "sycl" not in name
                and "vulkan" not in name
                and "rocm" not in name
            ):
                found=a["browser_download_url"]
                print(found)
                raise SystemExit(0)

raise SystemExit("STOP: Ubuntu x64 CPU llama binary nerastas")
PY2
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
  -n 1000 \
  --temp 0 \
  --single-turn \
  -p "$PROMPT" \
  > "$RESULT_FILE"

test -s "$RESULT_FILE"

echo "TAZERIS REMOTE COMPUTE: OK"
