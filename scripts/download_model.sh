#!/usr/bin/env bash
#
# CountAI model downloader.
# Downloads the official quantized SSD-MobileNetV1 COCO object-detection
# model (300x300, uint8, ~4 MB) from TensorFlow Hub and installs it as
# app/src/main/assets/detect.tflite
#
# The file is VERIFIED to be a real TFLite flatbuffer (magic bytes "TFL3")
# before it is installed. Run from anywhere:
#   bash scripts/download_model.sh
#
# NOTE: the old download.tensorflow.org tar.gz URLs now return 403, so we
# use the stable TF Hub redirect URLs (they resolve to a fresh signed URL
# on every run).
#
set -euo pipefail

MODEL_URLS=(
  "https://tfhub.dev/tensorflow/lite-model/ssd_mobilenet_v1/1/metadata/2?lite-format=tflite"
  "https://tfhub.dev/tensorflow/lite-model/efficientdet/lite0/detection/metadata/1?lite-format=tflite"
)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ASSETS_DIR="$SCRIPT_DIR/../app/src/main/assets"
mkdir -p "$ASSETS_DIR"

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

# Portable TFLite magic check: bytes 4..7 of a flatbuffer must be "TFL3".
is_tflite() {
  [ -f "$1" ] || return 1
  [ "$(stat -c%s "$1")" -gt 1000000 ] || return 1
  [ "$(od -A n -t x1 -j 4 -N 4 "$1" | tr -d ' \n')" = "54464c33" ]
}

for url in "${MODEL_URLS[@]}"; do
  echo "==> Trying: $url"
  if curl -fSL --retry 3 --connect-timeout 20 -o "$TMPDIR/model.bin" "$url"; then
    CANDIDATE=""
    if is_tflite "$TMPDIR/model.bin"; then
      CANDIDATE="$TMPDIR/model.bin"          # direct .tflite download
    elif tar -tzf "$TMPDIR/model.bin" >/dev/null 2>&1; then
      tar -xzf "$TMPDIR/model.bin" -C "$TMPDIR"
      CANDIDATE="$(find "$TMPDIR" -name '*.tflite' | head -n 1)"
    fi
    if [ -n "$CANDIDATE" ] && is_tflite "$CANDIDATE"; then
      cp "$CANDIDATE" "$ASSETS_DIR/detect.tflite"
      echo "==> OK: installed $ASSETS_DIR/detect.tflite ($(stat -c%s "$ASSETS_DIR/detect.tflite") bytes, verified TFLite flatbuffer)"
      exit 0
    else
      echo "    !! not a valid .tflite, trying next URL"
    fi
  else
    echo "    !! download failed, trying next URL"
  fi
done

echo "==> FAILED: could not download a verified model" >&2
exit 1
