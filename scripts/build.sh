#!/usr/bin/env bash
# Build the app inside the pinned Nix dev shell.
set -euo pipefail

cd "$(dirname "$0")/.."

variant="${1:-debug}"
case "$variant" in
  debug)   task="assembleDebug" ;;
  release) task="assembleRelease" ;;
  *)       echo "usage: $0 [debug|release]" >&2; exit 2 ;;
esac

if command -v gradle >/dev/null 2>&1 && [ -n "${ANDROID_HOME:-}" ]; then
  gradle ":app:$task" --console=plain
else
  nix develop --command gradle ":app:$task" --console=plain
fi

echo
echo "APK: app/build/outputs/apk/$variant/"
