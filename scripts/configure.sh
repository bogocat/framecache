#!/bin/bash
# Push config to device without typing
# Usage: ./scripts/configure.sh [device-serial]

export PATH=$PATH:~/Library/Android/sdk/platform-tools

DEVICE="${1:-emulator-5554}"

adb -s "$DEVICE" shell am force-stop com.bogocat.framecache
adb -s "$DEVICE" shell am start -n com.bogocat.framecache/.MainActivity \
  --es server_url "https://photos.bogocat.com" \
  --es api_key "g2mErYo5oymcPB83hCbXx3kMJgCKhoD00QdSUhlvk" \
  --es album_ids "82ba6ce3-5b0e-49a4-bad7-3c92e776baf8"

echo "Config pushed to $DEVICE"
