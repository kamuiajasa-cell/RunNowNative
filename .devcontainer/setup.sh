#!/usr/bin/env bash
# Menyiapkan Android SDK dan Gradle 8.11.1 di Codespaces (sekali, saat ruang dibuat).
set -euo pipefail
SDK="$HOME/android-sdk"
mkdir -p "$SDK/cmdline-tools" "$HOME/gradle"
cd /tmp
curl -fsSL -o cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q -o cmdtools.zip -d "$SDK/cmdline-tools"
rm -rf "$SDK/cmdline-tools/latest"
mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-36" "build-tools;36.0.0"
curl -fsSL -o gradle.zip https://services.gradle.org/distributions/gradle-8.11.1-bin.zip
unzip -q -o gradle.zip -d "$HOME/gradle"
echo "sdk.dir=$SDK" > "$OLDPWD/local.properties" 2>/dev/null || true
echo "Siap. Jalankan: gradle assembleDebug"
