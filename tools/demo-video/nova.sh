#!/usr/bin/env bash
# Controller input and screenshots on the handheld over adb, for scripted takes.
#   nova.sh key 158 | hat y 1 | hold y 1 2 | shot name | sleep 0.5      (any number of these, in order)
# D is the controller's input node: check it with `adb shell getevent -pl`. On the Retroid Pocket Nova, KEY_BACK is
# 158, the printed A 305, B 304, Y 307, X 308, L1/R1 310/311, L2/R2 312/313; the D-pad is the hat (x or y, -1 or 1).
D=${NOVA_INPUT:-/dev/input/event7}
SH=$(cd "$(dirname "$0")" && pwd)/media/shots; mkdir -p "$SH"
while [ $# -gt 0 ]; do
  case $1 in
    key) adb shell "sendevent $D 1 $2 1; sendevent $D 0 0 0; sleep 0.06; sendevent $D 1 $2 0; sendevent $D 0 0 0"; shift 2;;
    hat) a=$([ "$2" = x ] && echo 16 || echo 17); adb shell "sendevent $D 3 $a $3; sendevent $D 0 0 0; sleep 0.06; sendevent $D 3 $a 0; sendevent $D 0 0 0"; shift 3;;
    hold) a=$([ "$2" = x ] && echo 16 || echo 17); adb shell "sendevent $D 3 $a $3; sendevent $D 0 0 0; sleep $4; sendevent $D 3 $a 0; sendevent $D 0 0 0"; shift 4;;
    shot) adb exec-out screencap -p > "$SH/$2.png"; shift 2;;
    sleep) sleep "$2"; shift 2;;
    *) echo "unknown $1"; exit 1;;
  esac
done
