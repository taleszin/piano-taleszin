#!/bin/bash
# Build manual (sem Gradle): aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Saída: build/piano.apk (assinado com a keystore de debug do ~/.android).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-31/android.jar"   # compileSdk 31; minSdk/targetSdk 19 no manifest
# TARGET=19 (tablet, padrão) ou TARGET=34 (celular moderno; Android 14+ recusa target < 23)
TARGET="${TARGET:-19}"
OUT=build
[ "$TARGET" != 19 ] && OUT="build-$TARGET"
rm -rf "$OUT"; mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"
sed "s/android:targetSdkVersion=\"19\"/android:targetSdkVersion=\"$TARGET\"/" AndroidManifest.xml > "$OUT/AndroidManifest.xml"

"$BT/aapt2" compile --dir res -o "$OUT/res/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest "$OUT/AndroidManifest.xml" \
  --min-sdk-version 19 --target-sdk-version "$TARGET" \
  -A assets --java "$OUT/gen" -o "$OUT/unsigned.apk" "$OUT/res/res.zip"

javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$JAR" -classpath "$JAR" -d "$OUT/classes" \
  $(find src "$OUT/gen" -name '*.java')

"$BT/d8" --release --min-api 19 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes.dex)

"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
  --ks-key-alias androiddebugkey --key-pass pass:android \
  --out "$OUT/piano.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --min-sdk-version 19 "$OUT/piano.apk"
ls -la "$OUT/piano.apk"
shasum -a 256 "$OUT/piano.apk"
