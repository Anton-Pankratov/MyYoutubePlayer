#!/usr/bin/env bash
set -euo pipefail

# Usage: bash tools/capture_player_startup_logs.sh
# Stops with Ctrl+C after reproducing one slow video start.

OUTPUT_DIR="diagnostics"
OUTPUT_FILE="$OUTPUT_DIR/player-startup-logcat.txt"

mkdir -p "$OUTPUT_DIR"
adb logcat -c

echo "Recording player startup diagnostics to $OUTPUT_FILE"
echo "Now open one video, press Play, wait until it starts or fails, then press Ctrl+C."

adb logcat -v threadtime \
  YouTubeInAppPlayer:D \
  chromium:D \
  WebView:D \
  ExoPlayerImpl:D \
  MediaCodec:D \
  *:S | tee "$OUTPUT_FILE"
