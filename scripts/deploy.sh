#!/bin/bash
# Build and deploy to emulator or device
# Usage: ./scripts/deploy.sh [device-serial]

export ANDROID_HOME=~/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
export JAVA_HOME=~/Library/Java/jdk-21.0.10+7/Contents/Home

DEVICE="${1:-emulator-5554}"
APK="app/build/outputs/apk/debug/app-debug.apk"

echo "Building..."
./gradlew --no-daemon assembleDebug 2>&1 | tail -1

if [ $? -ne 0 ]; then
  echo "Build failed"
  exit 1
fi

echo "Installing to $DEVICE..."
adb -s "$DEVICE" install -r "$APK"

echo "Launching..."
adb -s "$DEVICE" shell am force-stop com.bogocat.framecache
adb -s "$DEVICE" shell am start -n com.bogocat.framecache/.MainActivity

echo "Done"
