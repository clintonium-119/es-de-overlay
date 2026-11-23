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
# 20e: the Game Switcher, from its promo's takes: ES-DE's list, Back, the strip across to Super Mario World, then the
# pick to the game at its own speed. The caption sits above the strip and goes as the pick is made; ES-DE's Last
# Played titles around its launch screen are painted black.
for t in sw-open sw-pick; do [ $M/cfr/$t.mp4 -nt $M/rec/$t.mkv ] || python3 $T/deflash.py $M/rec/$t.mkv $M/cfr/$t.mp4; done
SWITCH="SWITCH GAMES FROM ES-DE"
seg 20e1 sw-open 0.95 1.85
seg 20e2-raw sw-open 4.50 5.20
caption 20e2 20e2-raw "$SWITCH" 0 9 235
seg 20e3-raw sw-open 6.90 7.42
caption 20e3 20e3-raw "$SWITCH" 0 9 235
seg 20e4-raw sw-open 8.53 9.05
caption 20e4 20e4-raw "$SWITCH" 0 9 235
seg 20e5-raw sw-open 10.13 10.65
caption 20e5 20e5-raw "$SWITCH" 0 9 235
seg 20e6-raw sw-pick 3.80 9.45
hide 20e6-in 20e6-raw 0.65 0.67 0:0:1280:960
hide 20e6-hid 20e6-in 0.68 3.61 40:60:184:740 40:60:640:104 40:689:640:111
caption 20e6 20e6-hid "$SWITCH" 0 0.64 235
join $M/seg/20e.mp4 20e1 20e2 20e3 20e4 20e5 20e6
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

# The Game Switcher's promo, cut the same way: its takes, its clips, and order-switcher.txt as its running order.
# What the device does runs at its own speed: the switcher opening, a pick up to the game, the way back. What's cut
# is waiting: holds on a still screen between presses, with a dissolve where other time is left out. Cards and clips
# meet through black.
for t in sw-open sw-pick sw-return sw-far sw-one; do
  [ $M/cfr/$t.mp4 -nt $M/rec/$t.mkv ] || python3 $T/deflash.py $M/rec/$t.mkv $M/cfr/$t.mp4
done
[ -f $M/cards/s01-title.png ] || $T/cards.sh
ALL=0:0:1280:960                                     # the whole picture
LIST="40:60:184:740 40:60:640:104 40:689:640:111"    # ES-DE's Last Played titles around its launch screen's panel

card s01-title s01-title 3.2
# 2: ES-DE's list, Back, the strip, and across five games, about a second each.
seg s02a-raw sw-open 0 1.95
caption s02a-cap s02a-raw "BACK  OPENS IT IN ES-DE" 0.2 9
soft s02a s02a-cap 0.2 0
seg s02b sw-open 4.50 5.85
seg s02c sw-open 6.90 7.95
seg s02d sw-open 8.53 9.58
seg s02e sw-open 10.13 11.18
seg s02f-raw sw-open 11.77 13.20
soft s02f s02f-raw 0 0.25
card s03-pick s03-pick 2.3
# 4: the pick, whole: the switcher, ES-DE's launch screen, RetroArch, the game where the picture was.
seg s04-raw sw-pick 3.46 11.60
hide s04-in s04-raw 0.99 1.01 $ALL
hide s04-hid s04-in 1.02 3.95 $LIST
caption s04-cap s04-hid "STARTS FROM THE EMULATOR'S AUTO SAVE STATE IF CONFIGURED" 4.43 9
soft s04 s04-cap 0.2 0.25
card s05-leave s05-leave 2.4
# 6: a step on the map, then Home and the switcher back; the wait between the two is left out.
seg s06a-raw sw-return 2.90 4.80
soft s06a s06a-raw 0.2 0
seg s06b-raw sw-return 6.30 9.45
hide s06b-hid s06b-raw 0.59 0.71 $ALL
soft s06b s06b-hid 0 0.25
blend s06 s06a s06b 0.25
card s09-layouts s09-layouts 2.0
# 10: the two layouts. The strip, its caption above the strip so the pictures in it show, across to Tekken 6; then
# a dissolve to the same game a screen, and two more games.
CAROUSEL="RECENT GAMES CAROUSEL"
seg s10s1-raw sw-far 3.43 4.33
caption s10s1-cap s10s1-raw "$CAROUSEL" 0 9 235
soft s10s1 s10s1-cap 0.2 0
seg s10s2-raw sw-far 4.67 5.42
caption s10s2 s10s2-raw "$CAROUSEL" 0 9 235
seg s10s3-raw sw-far 5.90 7.10
caption s10s3 s10s3-raw "$CAROUSEL" 0 9 235
seg s10a-raw sw-one 3.95 5.45
caption s10a s10a-raw "SINGLE GAME FULL SCREEN" 0 9
blend s10m s10s3 s10a 0.35
seg s10b-raw sw-one 6.27 7.67
caption s10b s10b-raw "SINGLE GAME FULL SCREEN" 0 9
seg s10c-raw sw-one 8.60 10.20
caption s10c-cap s10c-raw "SINGLE GAME FULL SCREEN" 0 9
soft s10c s10c-cap 0 0.25
card s12-end c10-end 4.5

join $M/out/es-de-overlay-switcher.mp4 $(cat $T/order-switcher.txt)
ffmpeg -v error -y -i $M/out/es-de-overlay-switcher.mp4 -vf "scale=960:720:flags=lanczos" -c:v libx264 -crf 24 -preset slow -pix_fmt yuv420p -an -movflags +faststart $M/out/es-de-overlay-switcher-discord.mp4
ffmpeg -v error -y -i $M/out/es-de-overlay-switcher.mp4 -vf "scale=1440:1080:flags=lanczos,pad=1920:1080:240:0:black" -c:v libx264 -crf 17 -preset slow -pix_fmt yuv420p -an -movflags +faststart $M/out/es-de-overlay-switcher-youtube.mp4

# Its loop for the home page (docs/assets/switcher-loop.mp4): the open, the pick and the return, without cards.
seg SL1 sw-open 0.95 1.95
seg SL2 sw-open 4.50 5.30
seg SL3 sw-open 6.90 7.60
seg SL4 sw-open 8.53 9.23
seg SL5 sw-open 10.13 10.83
ffmpeg -v error -y -i $M/seg/s04-hid.mp4 -vf "trim=0.2:5.3,setpts=PTS-STARTPTS" $ENC $M/seg/SL6.mp4
seg SL7a sw-return 3.20 4.80
blend SL7 SL7a s06b-hid 0.25
join $M/seg/switcher-loop.mp4 SL1 SL2 SL3 SL4 SL5 SL6 SL7
ffmpeg -v error -y -i $M/seg/switcher-loop.mp4 -vf "scale=960:720:flags=lanczos" -c:v libx264 -crf 23 -preset slow -pix_fmt yuv420p -an -movflags +faststart $M/out/switcher-loop.mp4

for f in $M/out/*; do echo "$(basename $f) $(ffprobe -v error -show_entries format=duration -of csv=p=0 $f) s $(du -h $f | cut -f1)"; done
