#!/bin/sh
# Boot the headless Revline emulator and leave the shop tappable.
# Nested KVM on this host faults inside kvm_arch_vcpu_create, and
# -accel on then sits forever after the bluetooth line. Software
# translation (-accel off) is the boot that actually reaches the desktop.
set -eu
cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$SDK/platform-tools:$SDK/emulator:$SDK/cmdline-tools/latest/bin:$PATH"

IMAGE="system-images;android-34;google_apis;x86_64"
AVD=revline

if [ ! -x "$SDK/emulator/emulator" ] || [ ! -d "$SDK/system-images/android-34/google_apis/x86_64" ]; then
    echo "The emulator or the Android 34 google_apis x86_64 image is not installed under $SDK"
    echo "sdkmanager --install emulator \"$IMAGE\""
    exit 1
fi

if [ ! -f "$HOME/.android/avd/$AVD.avd/config.ini" ]; then
    echo no | avdmanager create avd -n "$AVD" -k "$IMAGE" -d pixel_6 --force
    python3 - "$HOME/.android/avd/$AVD.avd/config.ini" << 'PY'
import sys
path = sys.argv[1]
text = open(path).read().splitlines()
wanted = {
    "hw.lcd.width": "1080",
    "hw.lcd.height": "2400",
    "hw.lcd.density": "420",
    "hw.ramSize": "2048",
    "hw.gpu.enabled": "yes",
    "hw.gpu.mode": "swiftshader_indirect",
    "hw.keyboard": "yes",
    "hw.mainKeys": "no",
}
seen = set()
out = []
for line in text:
    key = line.split("=", 1)[0].strip() if "=" in line else ""
    if key in wanted:
        out.append(f"{key} = {wanted[key]}")
        seen.add(key)
    else:
        out.append(line)
for key, value in wanted.items():
    if key not in seen:
        out.append(f"{key} = {value}")
open(path, "w").write("\n".join(out) + "\n")
PY
fi

if ! adb devices | grep -q 'emulator-.*device'; then
    nohup emulator -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot \
        -gpu swiftshader_indirect -accel off -memory 1536 -cores 2 \
        > /tmp/revline-emulator.log 2>&1 &
    echo "starting, log at /tmp/revline-emulator.log"
    i=0
    while [ "$i" -lt 90 ]; do
        if adb shell getprop sys.boot_completed 2>/dev/null | grep -q 1; then
            break
        fi
        i=$((i + 1))
        sleep 5
    done
    if ! adb shell getprop sys.boot_completed 2>/dev/null | grep -q 1; then
        echo "the guest did not finish booting; see /tmp/revline-emulator.log"
        exit 1
    fi
fi

# The software guest is slow enough that System UI posts an ANR over the shop.
# The navigation bar also sits on top of the tab row and eats the taps.
adb shell settings put global hide_error_dialogs 1
adb shell settings put system screen_off_timeout 2147483647
adb shell settings put global stay_on_while_plugged_in 3
adb shell svc power stayon true >/dev/null 2>&1 || true
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell settings put secure navigation_mode 0 || true
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton >/dev/null 2>&1 || true
adb shell cmd overlay disable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1 || true
adb shell settings put global policy_control 'immersive.navigation=*'

echo "online: $(adb devices | awk '/emulator-/{print $1}')"
echo "screenshot: adb emu screenrecord screenshot /tmp/revline.png"
echo "give that command a couple of seconds between calls; a tight loop returns the same frame"
