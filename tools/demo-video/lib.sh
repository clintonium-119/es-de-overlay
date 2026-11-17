# Helpers for cutting the demo video. Every clip comes out 1280x960, 30 fps, H.264, no sound, so clips join cleanly.
T=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
M=$T/media                 # recordings, the font and everything built: not in git
DOCS=$T/../../docs/images  # the site's screenshots, used as stills
F=$M/font/vag-bold.otf     # the font of the theme in the footage (see README.md)
ENC="-r 30 -c:v libx264 -crf 15 -preset medium -pix_fmt yuv420p -an"
# seg out take start end [speed]: a piece of a take, from media/cfr
seg() { local sp=${5:-1}; ffmpeg -v error -y -i $M/cfr/$2.mp4 -vf "trim=$3:$4,setpts=(PTS-STARTPTS)/$sp,fps=30,scale=1280:960" $ENC $M/seg/$1.mp4; }
# card out png seconds: a title card, fading in and out
card() { local n=$(awk "BEGIN{print $3-0.25}"); ffmpeg -v error -y -loop 1 -t $3 -i $M/cards/$2.png -vf "fps=30,scale=1280:960,fade=in:st=0:d=0.2,fade=out:st=$n:d=0.25" $ENC $M/seg/$1.mp4; }
# pill "TEXT" out.png: a caption in the style of the theme's hint pill
pill() { local w=$(magick -font $F -pointsize 34 label:"$1" -format '%w' info:); local pw=$((w+90)); magick -size ${pw}x76 xc:none -fill '#1f2321' -draw "roundrectangle 0,0 $((pw-1)),75 38,38" -fill '#e6e9e7' -font $F -pointsize 34 -gravity center -annotate +0+2 "$1" PNG32:$2; }
# still out image seconds [caption] [left]: a still, with an optional caption (left: clear of the menu's hint pill)
still() { local x="(W-w)/2"; [ "${5:-}" = left ] && x=64; if [ -n "${4:-}" ]; then pill "$4" $M/seg/$1-pill.png; ffmpeg -v error -y -loop 1 -t $3 -i "$2" -i $M/seg/$1-pill.png -filter_complex "[0]fps=30,scale=1280:960[b];[b][1]overlay=$x:H-h-64" $ENC $M/seg/$1.mp4; else ffmpeg -v error -y -loop 1 -t $3 -i "$2" -vf "fps=30,scale=1280:960" $ENC $M/seg/$1.mp4; fi; }
# caption out clip "TEXT" from to: a caption over an existing clip between two times
caption() { pill "$3" $M/seg/$1-pill.png; ffmpeg -v error -y -i $M/seg/$2.mp4 -i $M/seg/$1-pill.png -filter_complex "[0][1]overlay=(W-w)/2:H-h-90:enable='between(t,$4,$5)'" $ENC $M/seg/$1.mp4; }
# join out.mp4 clip...: clips from media/seg, in order
join() { local out=$1; shift; : > $M/seg/list.txt; for c in "$@"; do echo "file '$M/seg/$c.mp4'" >> $M/seg/list.txt; done; ffmpeg -v error -y -f concat -safe 0 -i $M/seg/list.txt -vf "fps=30" -c:v libx264 -crf 17 -preset slow -pix_fmt yuv420p -an -movflags +faststart $out; }
