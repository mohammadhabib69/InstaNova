#!/bin/bash
set -e

echo "=== InstaNova Device Verification Script ==="

echo "1. Checking connected devices:"
adb devices

echo "2. Building and installing debug APK:"
./gradlew assembleDebug installDebug

echo "3. Launching app:"
adb shell am start -n com.insta.clone/.ui.auth.SplashActivity

echo "4. Capturing logcat:"
adb logcat -d | grep -E 'InstaApi|InstaGlide|AndroidRuntime|FATAL' | tail -n 50

echo "5. Taking screenshot:"
mkdir -p shots
adb exec-out screencap -p > shots/verify_$(date +%s).png

echo "Done verification check."
