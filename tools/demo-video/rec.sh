#!/usr/bin/env bash
# rec.sh <name> <seconds>: records the handheld's screen to media/rec/<name>.mkv in the background, then returns
# once the recording has started. It stops by itself after <seconds>.
R=$(cd "$(dirname "$0")" && pwd)/media/rec; mkdir -p "$R"
nohup scrcpy --no-window --no-audio --no-control --video-bit-rate=16M --record="$R/$1.mkv" --time-limit="$2" > "$R/$1.log" 2>&1 &
sleep 2.5
