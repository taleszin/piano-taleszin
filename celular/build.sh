#!/bin/bash
# Piano Taleszin — versão celular (Android 8+). Build manual: m7s.py -> aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Saída: build/piano-celular.apk (applicationId app.taleszin.piano.celular, convive com o do tablet).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
OUT=build
rm -rf "$OUT"; mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/assets/musicas"

python3 tools/m7s.py compilar musicas "$OUT/assets/musicas" || echo "AVISO: músicas com erro foram puladas"

"$BT/aapt2" compile --dir res -o "$OUT/res/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 35 \
  --rename-manifest-package app.taleszin.piano.celular \
  -A "$OUT/assets" --java "$OUT/gen" -o "$OUT/unsigned.apk" "$OUT/res/res.zip"

javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$JAR" -classpath "$JAR" -d "$OUT/classes" \
  $(find src "$OUT/gen" -name '*.java')

"$BT/d8" --release --min-api 26 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes.dex)

"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
  --ks-key-alias androiddebugkey --key-pass pass:android \
  --out "$OUT/piano-celular.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --min-sdk-version 26 "$OUT/piano-celular.apk"
ls -la "$OUT/piano-celular.apk"
