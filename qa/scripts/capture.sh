#!/usr/bin/env bash
set -euo pipefail
set -e
mkdir -p qa/screenshots
adb shell wm size 1080x1920
adb shell wm density 420
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 0 || true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.clippilot.android || true
adb shell am start -W -n com.clippilot.android/.MainActivity

# The Pixel Launcher may show a system ANR dialog on cold emulators. Verify
# ClipPilot is truly foreground; do not capture any system overlay as app UI.
ensure_app_visible() {
  for attempt in 1 2 3 4 5 6; do
    sleep 4
    adb shell uiautomator dump /sdcard/qa-health.xml > /dev/null 2>&1 || true
    adb exec-out cat /sdcard/qa-health.xml > /tmp/qa-health.xml || true
    if grep -q 'Novo projeto' /tmp/qa-health.xml; then
      return 0
    fi
    if grep -q 'Pixel Launcher' /tmp/qa-health.xml; then
      echo "Pixel Launcher ANR overlay; choosing Wait, attempt ${attempt}"
      adb shell input tap 540 1085 || true
      sleep 3
    fi
    adb shell am start -W -n com.clippilot.android/.MainActivity || true
  done
  echo "ClipPilot home screen not visible after recovery attempts" >&2
  cat /tmp/qa-health.xml >&2 || true
  return 1
}
ensure_app_visible
adb exec-out screencap -p > qa/screenshots/01-home-real.png
test -s qa/screenshots/01-home-real.png
adb shell uiautomator dump /sdcard/clippilot-ui.xml || true
adb pull /sdcard/clippilot-ui.xml qa/screenshots/01-ui.xml || true
# Tap the real button using its accessibility bounds. This survives changes
# in DPI, status bar height and screen dimensions.
tap_by_label() {
  label="$1"
  adb shell uiautomator dump /sdcard/qa-window.xml > /dev/null
  adb exec-out cat /sdcard/qa-window.xml > /tmp/qa-window.xml
  coordinates=$(python3 -c 'import sys,re,xml.etree.ElementTree as ET; target=sys.argv[1]; nodes=(n for n in ET.parse("/tmp/qa-window.xml").iter("node") if n.get("text")==target or n.get("content-desc")==target); n=next(nodes); a,b,c,d=map(int,re.findall("[0-9]+",n.get("bounds",""))); print((a+c)//2,(b+d)//2)' "$label")
  adb shell input tap $coordinates
  sleep 3
}

tap_by_label "Novo projeto"
adb exec-out screencap -p > qa/screenshots/02-new-project-real.png
test -s qa/screenshots/02-new-project-real.png
adb shell uiautomator dump /sdcard/clippilot-create-ui.xml || true
adb pull /sdcard/clippilot-create-ui.xml qa/screenshots/02-ui.xml || true

# Put three reproducible MP4 files inside the real debug APK sandbox,
# then populate the same SharedPreferences used for imported projects.
# This tests real thumbnail decoding and ExoPlayer playback without
# any fabricated or post-processed UI screenshot.
adb shell run-as com.clippilot.android mkdir -p files shared_prefs
for i in 1 2 3; do
  adb exec-in run-as com.clippilot.android sh -c "cat > files/qa-$i.mp4" < "qa/fixtures/qa-$i.mp4"
done
cat > /tmp/qa-project.xml <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
  <string name="clips">["file:///data/user/0/com.clippilot.android/files/qa-1.mp4","file:///data/user/0/com.clippilot.android/files/qa-2.mp4","file:///data/user/0/com.clippilot.android/files/qa-3.mp4"]</string>
  <string name="name">Maya - Episódio 04</string>
</map>
EOF
adb exec-in run-as com.clippilot.android sh -c 'cat > shared_prefs/clippilot-project.xml' < /tmp/qa-project.xml
adb shell am force-stop com.clippilot.android
adb shell am start -W -n com.clippilot.android/.MainActivity
ensure_app_visible
adb exec-out screencap -p > qa/screenshots/03-home-with-clips-real.png
test -s qa/screenshots/03-home-with-clips-real.png

tap_by_label "Novo projeto"
adb exec-out screencap -p > qa/screenshots/04-project-loaded-real.png
test -s qa/screenshots/04-project-loaded-real.png
# Check that the real app recognizes all three fixture videos.
adb shell uiautomator dump /sdcard/qa-clips-ui.xml >/dev/null
adb exec-out cat /sdcard/qa-clips-ui.xml > qa/screenshots/04-clips-ui.xml
grep -q 'Clipe 3' qa/screenshots/04-clips-ui.xml

# The continue button is sticky. Open actual single-clip playback.
tap_by_label "Continuar →"
sleep 3
adb exec-out screencap -p > qa/screenshots/05-preview-real.png
test -s qa/screenshots/05-preview-real.png

# Run Media3 Transformer with the three actual clips and capture real progress.
tap_by_label "Gerar MP4"
sleep 2
adb exec-out screencap -p > qa/screenshots/06-export-progress-real.png
test -s qa/screenshots/06-export-progress-real.png

# lastExport is written by the app only when the MP4 was validated.
echo "Waiting for verified MP4..."
export_path=""
for attempt in $(seq 1 75); do
  prefs_xml=$(adb exec-out run-as com.clippilot.android cat shared_prefs/clippilot-project.xml 2>/dev/null || true)
  export_path=$(printf '%s' "$prefs_xml" | sed -n 's/.*name="lastExport">\([^<]*\)<.*/\1/p' | tr -d '\r')
  if [ -n "$export_path" ]; then break; fi
  sleep 4
done
if [ -z "$export_path" ]; then
  echo "No verified MP4 produced." >&2
  adb exec-out screencap -p > qa/screenshots/07-export-failed-real.png || true
  adb logcat -d -t 3000 > qa/screenshots/emulator-logcat.txt || true
  exit 1
fi
echo "Completed MP4: $export_path"
adb exec-out screencap -p > qa/screenshots/07-export-completed-real.png
test -s qa/screenshots/07-export-completed-real.png
mkdir -p qa/exports
adb exec-out run-as com.clippilot.android cat "$export_path" > qa/exports/assembled-3clips.mp4
test -s qa/exports/assembled-3clips.mp4
ffprobe -v error -show_entries format=duration:stream=codec_type,codec_name -of json qa/exports/assembled-3clips.mp4 | tee qa/exports/ffprobe.json
python3 -c 'import json,subprocess; d=json.loads(subprocess.check_output(["ffprobe","-v","error","-show_entries","format=duration:stream=codec_type","-of","json","qa/exports/assembled-3clips.mp4"])); seconds=float(d["format"]["duration"]); streamtypes={s["codec_type"] for s in d["streams"]}; print("Duration",seconds,"Streams",streamtypes); assert 5.3<=seconds<=7.2, "Expected three two-second clips"; assert "video" in streamtypes and "audio" in streamtypes'

tap_by_label "Exportações"
adb exec-out screencap -p > qa/screenshots/08-exports-real.png
test -s qa/screenshots/08-exports-real.png
adb logcat -d -t 2000 > qa/screenshots/emulator-logcat.txt || true
file qa/screenshots/*.png
