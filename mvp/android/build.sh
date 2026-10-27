#!/bin/sh
# 리캐치 시제품 APK 빌드 (Linux/macOS). 결과: app/build/outputs/apk/debug/app-debug.apk
# 필요: JDK 17+, Android SDK(ANDROID_HOME, platforms;android-35), Gradle 8.9+ (GRADLE 또는 PATH)
set -e
cd "$(dirname "$0")"
: "${ANDROID_HOME:?ANDROID_HOME을 Android SDK 경로로 지정하세요}"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
"${GRADLE:-gradle}" --no-daemon assembleDebug "$@"
