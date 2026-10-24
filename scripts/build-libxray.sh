#!/usr/bin/env bash
# Builds the Xray-core native library (libXray) and drops it into both apps.
#
#   scripts/build-libxray.sh android   -> android/app/libs/libXray.aar     (needs Go, Android NDK)
#   scripts/build-libxray.sh ios       -> ios/Frameworks/LibXray.xcframework (needs Go, Xcode, macOS)
#   scripts/build-libxray.sh all
#
# Override the pinned release with LIBXRAY_REF=v26.x.y.
set -euo pipefail

LIBXRAY_REF="${LIBXRAY_REF:-v26.10.21}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/.build/libXray"
TARGET="${1:-all}"

command -v go >/dev/null || { echo "Go is required: https://go.dev/dl/" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 1; }

if [ ! -d "$WORK/.git" ]; then
    git clone --depth 1 --branch "$LIBXRAY_REF" https://github.com/XTLS/libXray.git "$WORK"
else
    git -C "$WORK" fetch --depth 1 origin "refs/tags/$LIBXRAY_REF:refs/tags/$LIBXRAY_REF"
    git -C "$WORK" checkout -q "$LIBXRAY_REF"
fi

build_android() {
    : "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an installed NDK, e.g. \$ANDROID_HOME/ndk/27.2.12479018}"
    (cd "$WORK" && python3 build/main.py android)
    mkdir -p "$ROOT/android/app/libs"
    cp "$WORK/libXray.aar" "$ROOT/android/app/libs/libXray.aar"
    echo "-> android/app/libs/libXray.aar"
}

build_ios() {
    [ "$(uname)" = "Darwin" ] || { echo "The iOS framework can only be built on macOS" >&2; exit 1; }
    (cd "$WORK" && python3 build/main.py apple gomobile)
    rm -rf "$ROOT/ios/Frameworks/LibXray.xcframework"
    mkdir -p "$ROOT/ios/Frameworks"
    cp -R "$WORK/LibXray.xcframework" "$ROOT/ios/Frameworks/"
    echo "-> ios/Frameworks/LibXray.xcframework"
}

case "$TARGET" in
    android) build_android ;;
    ios) build_ios ;;
    all) build_android; build_ios ;;
    *) echo "usage: $0 [android|ios|all]" >&2; exit 2 ;;
esac
