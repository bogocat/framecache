#!/bin/bash
# Launch FrameCache emulator with device profiles
# Usage: ./scripts/emulator.sh [frameo|frameo-portrait|tablet|phone]

export ANDROID_HOME=~/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools
export JAVA_HOME=~/Library/Java/jdk-21.0.10+7/Contents/Home

PROFILE="${1:-frameo}"
shift 2>/dev/null

case "$PROFILE" in
  frameo)
    NAME="framecache-frameo"
    WIDTH=1280
    HEIGHT=800
    DENSITY=213
    DEVICE="10.1in WXGA (Tablet)"
    ;;
  frameo-portrait)
    NAME="framecache-frameo-portrait"
    WIDTH=800
    HEIGHT=1280
    DENSITY=213
    DEVICE="10.1in WXGA (Tablet)"
    ;;
  tablet)
    NAME="framecache-tablet"
    WIDTH=2560
    HEIGHT=1600
    DENSITY=320
    DEVICE="pixel_tablet"
    ;;
  phone)
    NAME="framecache-phone"
    WIDTH=1080
    HEIGHT=2400
    DENSITY=420
    DEVICE="pixel_6"
    ;;
  *)
    echo "Profiles: frameo (1280x800), frameo-portrait (800x1280), tablet, phone"
    exit 1
    ;;
esac

# Create AVD if it doesn't exist
if ! $ANDROID_HOME/emulator/emulator -list-avds 2>/dev/null | grep -q "^${NAME}$"; then
  echo "Creating AVD: $NAME (${WIDTH}x${HEIGHT} @ ${DENSITY}dpi)"
  echo "no" | avdmanager create avd \
    -n "$NAME" \
    -k "system-images;android-35;google_apis;arm64-v8a" \
    -d "$DEVICE" \
    --force 2>/dev/null

  # Override resolution in config
  CFG=~/.android/avd/${NAME}.avd/config.ini
  if [ -f "$CFG" ]; then
    # Remove existing display settings
    grep -v "hw.lcd.width\|hw.lcd.height\|hw.lcd.density" "$CFG" > "${CFG}.tmp"
    mv "${CFG}.tmp" "$CFG"
    # Add our settings
    echo "hw.lcd.width=$WIDTH" >> "$CFG"
    echo "hw.lcd.height=$HEIGHT" >> "$CFG"
    echo "hw.lcd.density=$DENSITY" >> "$CFG"
  fi
fi

echo "Launching $NAME (${WIDTH}x${HEIGHT})"
exec $ANDROID_HOME/emulator/emulator -avd "$NAME" -no-boot-anim -no-audio "$@"
