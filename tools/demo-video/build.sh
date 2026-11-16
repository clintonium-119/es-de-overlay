#!/usr/bin/env bash
# Cuts the demo video from the takes in media/rec. Each line below is one clip: change a time, a speed, a card
# length or a caption here, then run this again. order.txt is the running order of the reel.
set -euo pipefail
source "$(dirname "$0")/lib.sh"; mkdir -p $M/cfr $M/seg $M/out

# The takes at a constant 30 fps, without the one-frame flashes of the game between menu lists (fixed in 1.3.1).
for t in t2-find t34-read t5a-mapdl t5b-map t78-return gameplay; do
  [ $M/cfr/$t.mp4 -nt $M/rec/$t.mkv ] || python3 $T/deflash.py $M/rec/$t.mkv $M/cfr/$t.mp4
done
[ -f $M/cards/c01-title.png ] || $T/cards.sh

# Your gameplay: the walk on the map, the fortress, and the last resume.
seg 01a-raw gameplay 24.33 30.15 1.25
caption 01a 01a-raw "BACK  OPENS THE MENU" 3.4 10
ffmpeg -v error -y -i $M/cfr/t2-find.mp4 -ss 2.8 -frames:v 1 -update 1 $M/seg/fresh-menu.png
still 01b $M/seg/fresh-menu.png 1.0
seg 14a gameplay 33.3 35.5
seg 14b gameplay 58.8 67.32
seg 18a gameplay 73.6 74.4
seg 18b gameplay 74.4 80.2
card 02-title c01-title 3.0
card 03-find c02-find 2.8
seg 04a t2-find 2.9 4.7
seg 04b t2-find 8.55 9.5
seg 04c t2-find 23.1 24.6
seg 04d-raw t2-find 24.6 98.6 18.5
caption 04d 04d-raw "SPED UP" 0 10
seg 04e t2-find 98.86 100.5
card 05-offline c03-offline 2.8
card 06-scroll c04-scroll 3.2
seg 07 t34-read 2.75 9.2 1.12
card 08-jump c05-jump 2.8
seg 09 t34-read 9.2 16.2
card 10-map c06-map 2.6
seg 11a t5a-mapdl 15.5 18.7 1.2
seg 11b t5a-mapdl 19.45 20.3
seg 11c t5a-mapdl 26.9 27.6
seg 11d-raw t5a-mapdl 27.6 41.6 7
caption 11d 11d-raw "SPED UP" 0 10
seg 11e t5a-mapdl 41.98 43.1
seg 12 t5b-map 2.95 13.3 1.18
card 13-back c07-back 2.8
seg 15a t78-return 5.74 9.05
seg 15b t78-return 10.12 12.5
card 16-maps c08-maps 2.8
seg 17 t78-return 12.58 16.7
seg 17b t78-return 17.76 19.4
card 19-other c09-other 2.0
still 20a $DOCS/browser.png 2.6 "SAVE GUIDES FROM GAMEFAQS, IGN AND MORE"
still 20b $DOCS/browser-saving.png 2.4 "SAVE GUIDES FROM GAMEFAQS, IGN AND MORE"
still 20c0 $M/stills/c01-menu.png 1.1 "FOLLOWS YOUR ES-DE THEME" left
i=1; for t in menu-art-book-next menu-iconic menu-techdweeb menu-canvas menu-linear; do still 20c$i $DOCS/$t.png 1.1 "FOLLOWS YOUR ES-DE THEME" left; i=$((i+1)); done
still 20d $M/stills/c2-k-capture.png 3.0 "CAPTURE MEDIA FOR YOUR GAMELIST" left
card 21-end c10-end 4.5

# The reel, and its versions for Discord (under 10 MB) and YouTube (16:9).
join $M/out/es-de-overlay-demo.mp4 $(cat $T/order.txt)
ffmpeg -v error -y -i $M/out/es-de-overlay-demo.mp4 -vf "scale=960:720:flags=lanczos" -c:v libx264 -crf 24 -preset slow -pix_fmt yuv420p -an -movflags +faststart $M/out/es-de-overlay-demo-discord.mp4
ffmpeg -v error -y -i $M/out/es-de-overlay-demo.mp4 -vf "scale=1440:1080:flags=lanczos,pad=1920:1080:240:0:black" -c:v libx264 -crf 17 -preset slow -pix_fmt yuv420p -an -movflags +faststart $M/out/es-de-overlay-demo-youtube.mp4

# The short loop for the home page (docs/assets/demo-loop.mp4) and the README (docs/images/demo.gif).
ffmpeg -v error -y -i $M/seg/01a.mp4 -vf "trim=2.4:4.66,setpts=PTS-STARTPTS" $ENC $M/seg/L1.mp4
ffmpeg -v error -y -i $M/seg/15b.mp4 -vf "trim=0:2.0,setpts=PTS-STARTPTS" $ENC $M/seg/L3.mp4
ffmpeg -v error -y -i $M/seg/18b.mp4 -vf "trim=0:2.6,setpts=PTS-STARTPTS" $ENC $M/seg/L6.mp4
join $M/seg/loop.mp4 L1 15a L3 17 17b 18a L6
ffmpeg -v error -y -i $M/seg/loop.mp4 -vf "scale=960:720:flags=lanczos" -c:v libx264 -crf 23 -preset slow -pix_fmt yuv420p -an -movflags +faststart $M/out/demo-loop.mp4
ffmpeg -v error -y -i $M/seg/loop.mp4 -vf "fps=12,scale=640:480:flags=lanczos,split[a][b];[a]palettegen=max_colors=160:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle" $M/out/demo.gif

for f in $M/out/*; do echo "$(basename $f) $(ffprobe -v error -show_entries format=duration -of csv=p=0 $f) s $(du -h $f | cut -f1)"; done
