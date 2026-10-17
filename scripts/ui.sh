#!/bin/bash
# InstaNova device-UI helper.
#   ./scripts/ui.sh dump [name]           -> pull hierarchy, print id/bounds/text rows
#   ./scripts/ui.sh shot <name>           -> screencap to shots/current/<name>.png
#   ./scripts/ui.sh tap <x> <y>           -> tap
#   ./scripts/ui.sh tapid <id>            -> tap the centre of a view by resource-id suffix
#   ./scripts/ui.sh tapdes <text>         -> tap the centre of the first node whose text/desc equals/contains
#   ./scripts/ui.sh swipe <x1> <y1> <x2> <y2> [ms]
#   ./scripts/ui.sh text <str>            -> type text (spaces as %s)
#   ./scripts/ui.sh key <keyevent>
#   ./scripts/ui.sh logs                  -> filtered logcat tail
#   ./scripts/ui.sh top                   -> foreground activity
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
ROOT=/Users/mohammadhabib/Programming/InstaNova
PKG=com.insta.nova
mkdir -p "$ROOT/shots/current"

cmd="$1"; shift

dump_xml() {
  adb shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1
  adb pull /sdcard/window_dump.xml "$ROOT/shots/current/window_dump.xml" >/dev/null 2>&1
}

case "$cmd" in
  dump)
    dump_xml
    python3 - "$ROOT/shots/current/window_dump.xml" "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
path, name = sys.argv[1], (sys.argv[2] if len(sys.argv)>2 else '')
t = ET.parse(path); r = t.getroot()
out = []
for n in r.iter('node'):
    rid = n.get('resource-id','') or ''
    txt = n.get('text','') or ''
    des = n.get('content-desc','') or ''
    b = n.get('bounds','')
    cls = n.get('class','').split('.')[-1]
    if not (rid or txt or des): continue
    out.append(f"{rid.replace('com.insta.nova:id/','#'):28} {cls:16} {b:24} txt={txt!r} desc={des!r}")
print("\n".join(out))
PY
    ;;
  shot)
    adb exec-out screencap -p > "$ROOT/shots/current/$1.png"
    echo "shots/current/$1.png"
    ;;
  tap)
    adb shell input tap "$1" "$2"; sleep "${3:-1}"
    ;;
  tapid)
    dump_xml
    coords=$(python3 - "$ROOT/shots/current/window_dump.xml" "$1" <<'PY'
import sys, re, xml.etree.ElementTree as ET
t = ET.parse(sys.argv[1]); key = sys.argv[2]
for n in t.getroot().iter('node'):
    rid = (n.get('resource-id','') or '')
    if rid.endswith(key):
        b = re.findall(r'\d+', n.get('bounds',''))
        if len(b) == 4:
            print(f"{(int(b[0])+int(b[2]))//2} {(int(b[1])+int(b[3]))//2} {rid.split('/')[-1]}")
            break
else:
    print("NOTFOUND")
PY
)
    echo "$coords"
    [ "$coords" != "NOTFOUND" ] && adb shell input tap $coords && sleep 1
    ;;
  tapdes)
    dump_xml
    coords=$(python3 - "$ROOT/shots/current/window_dump.xml" "$1" <<'PY'
import sys, re, xml.etree.ElementTree as ET
t = ET.parse(sys.argv[1]); key = sys.argv[2].lower()
best = None
for n in t.getroot().iter('node'):
    txt = (n.get('text','') or ''); des = (n.get('content-desc','') or '')
    if txt.lower() == key or des.lower() == key:
        best = n; break
    if best is None and (key in txt.lower() or key in des.lower()):
        best = n
if best is None:
    print("NOTFOUND")
else:
    b = re.findall(r'\d+', best.get('bounds',''))
    print(f"{(int(b[0])+int(b[2]))//2} {(int(b[1])+int(b[3]))//2} {best.get('text','')!r}{best.get('content-desc','')!r}")
PY
)
    echo "$coords"
    xy=$(echo "$coords" | cut -d' ' -f1,2)
    [ "$coords" != "NOTFOUND" ] && adb shell input tap $xy && sleep 1
    ;;
  swipe)
    adb shell input swipe "$1" "$2" "$3" "$4" "${5:-300}"; sleep 1
    ;;
  drag)
    # reliable vertical drag: ui.sh drag <x> <y1> <y2> [steps]
    x="$1"; y1="$2"; y2="$3"; steps="${4:-10}"
    adb shell input motionevent DOWN "$x" "$y1"
    span=$(( y1 - y2 )); i=1
    while [ $i -le $steps ]; do
      ty=$(( y1 - span * i / steps ))
      adb shell input motionevent MOVE "$x" "$ty"
      sleep 0.03
      i=$(( i + 1 ))
    done
    adb shell input motionevent UP "$x" "$y2"
    sleep 1
    ;;
  fling)
    # ui.sh fling <x> <y1> <y2> [times]
    times="${4:-1}"
    for n in $(seq 1 "$times"); do
      adb shell input motionevent DOWN "$1" "$2"
      adb shell input motionevent MOVE "$1" "$(( $2 - 200 ))"
      adb shell input motionevent MOVE "$1" "$(( $2 - 500 ))"
      adb shell input motionevent MOVE "$1" "$3"
      adb shell input motionevent UP "$1" "$3"
      sleep 0.4
    done
    sleep 1
    ;;
  text)
    adb shell input text "$1"; sleep 1
    ;;
  key)
    adb shell input keyevent "$1"; sleep 1
    ;;
  logs)
    adb logcat -d -s InstaApi:V InstaGlide:V AndroidRuntime:E ActivityManager:I 2>/dev/null \
      | grep -E 'InstaApi|InstaGlide|FATAL|AndroidRuntime|Exception' | tail -${1:-40}
    ;;
  top)
    adb shell dumpsys activity activities | grep -E 'topResumedActivity' | head -2
    ;;
  *)
    echo "usage: ui.sh dump|shot|tap|tapid|tapdes|swipe|text|key|logs|top"; exit 1;;
esac
