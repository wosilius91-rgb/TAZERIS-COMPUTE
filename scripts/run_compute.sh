#!/usr/bin/env bash
set -euo pipefail

PROMPT_FILE="${1:-prompt.txt}"
RESULT_FILE="${2:-result.txt}"

git clone --depth 1 https://github.com/ggml-org/llama.cpp.git /tmp/llama.cpp
cmake -S /tmp/llama.cpp -B /tmp/llama.cpp/build \
  -DCMAKE_BUILD_TYPE=Release \
  -DLLAMA_BUILD_SERVER=OFF \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF
cmake --build /tmp/llama.cpp/build --target llama-cli -j4

mkdir -p /tmp/models

curl -L --fail --retry 3 \
  -o /tmp/models/qwen.gguf \
  "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf"

PROMPT="$(cat "$PROMPT_FILE")"

/tmp/llama.cpp/build/bin/llama-cli \
  -m /tmp/models/qwen.gguf \
  -t 4 \
  -c 4096 \
  -n 1400 \
  --temp 0 \
  -p "$PROMPT" \
  > "$RESULT_FILE"

test -s "$RESULT_FILE"
