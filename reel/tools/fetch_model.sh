#!/usr/bin/env bash
# Downloads the on-device speech model the app bundles (too big for git: 264 MB).
# It is whisper.cpp's ggml-small-q8_0 model; the checksum below is the exact file the app ships.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=app/src/main/assets/models/speech.bin
SHA=49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f
URL=https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q8_0.bin
if [ -f "$OUT" ] && echo "$SHA  $OUT" | sha256sum -c --status; then echo "speech.bin already present"; exit 0; fi
mkdir -p "$(dirname "$OUT")"
curl -L --fail -o "$OUT.part" "$URL"
echo "$SHA  $OUT.part" | sha256sum -c -
mv "$OUT.part" "$OUT"
echo "saved $OUT"
