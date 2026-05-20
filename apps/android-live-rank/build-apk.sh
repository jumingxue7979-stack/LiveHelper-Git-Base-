#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$ROOT_DIR/../.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk}"
BUILD_TOOLS="$SDK_ROOT/build-tools/35.0.0"
ANDROID_JAR="$SDK_ROOT/platforms/android-35/android.jar"
OUT_DIR="$ROOT_DIR/build"
APK_NAME="LiveHelper_AndroidLiveRank_Work4_Report_2026-05-19.apk"
KEYSTORE="$ROOT_DIR/livehelper-android-live-rank-debug.keystore"

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$BUILD_TOOLS:$PATH"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/compiled" "$OUT_DIR/gen" "$OUT_DIR/classes" "$OUT_DIR/dex" "$PROJECT_ROOT/dist"

"$BUILD_TOOLS/aapt2" compile --dir "$ROOT_DIR/app/src/main/res" -o "$OUT_DIR/compiled/resources.zip"

"$BUILD_TOOLS/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT_DIR/AndroidManifest.xml" \
  --java "$OUT_DIR/gen" \
  --min-sdk-version 23 \
  --target-sdk-version 28 \
  -o "$OUT_DIR/base.apk" \
  "$OUT_DIR/compiled/resources.zip"

find "$ROOT_DIR/app/src/main/java" "$OUT_DIR/gen" -name "*.java" > "$OUT_DIR/sources.txt"

"$JAVA_HOME/bin/javac" \
  -encoding UTF-8 \
  -source 8 \
  -target 8 \
  -classpath "$ANDROID_JAR" \
  -d "$OUT_DIR/classes" \
  @"$OUT_DIR/sources.txt"

find "$OUT_DIR/classes" -name "*.class" > "$OUT_DIR/classes-list.txt"

"$BUILD_TOOLS/d8" \
  --min-api 23 \
  --classpath "$ANDROID_JAR" \
  --output "$OUT_DIR/dex" \
  @"$OUT_DIR/classes-list.txt"

cp "$OUT_DIR/base.apk" "$OUT_DIR/unsigned.apk"
(cd "$OUT_DIR/dex" && zip -q "$OUT_DIR/unsigned.apk" classes.dex)

"$BUILD_TOOLS/zipalign" -f 4 "$OUT_DIR/unsigned.apk" "$OUT_DIR/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  "$JAVA_HOME/bin/keytool" \
    -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias livehelper \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Live Helper Android Live Rank,O=Live Helper,C=KR"
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$PROJECT_ROOT/dist/$APK_NAME" \
  "$OUT_DIR/aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose "$PROJECT_ROOT/dist/$APK_NAME"
shasum -a 256 "$PROJECT_ROOT/dist/$APK_NAME"
