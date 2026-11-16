#!/usr/bin/env bash
# Renders the title cards into media/cards: white headline, grey second line, an optional hint pill, on black.
set -euo pipefail
source "$(dirname "$0")/lib.sh"; mkdir -p $M/cards; cd $M/cards
card_png() { # name headline [second line] [hint]
  local cmd=(magick -size 1280x960 xc:black -colorspace sRGB -type TrueColor -font $F -gravity center)
  if [ -n "${3:-}" ]; then cmd+=(-fill white -pointsize 80 -annotate +0-44 "$2" -fill '#a8a8a8' -pointsize 50 -annotate +0+52 "$3"); else cmd+=(-fill white -pointsize 80 -annotate +0+0 "$2"); fi
  if [ -n "${4:-}" ]; then
    local w=$(magick -font $F -pointsize 34 label:"$4" -format '%w' info:); local pw=$((w+90))
    cmd+=( \( -size ${pw}x76 xc:none -fill '#1f2321' -draw "roundrectangle 0,0 $((pw-1)),75 38,38" -fill '#d8dcd9' -font $F -pointsize 34 -gravity center -annotate +0+2 "$4" \) -gravity center -geometry +0+190 -composite )
  fi
  "${cmd[@]}" -type TrueColor PNG24:$1.png
}
card_png c01-title "ES-DE Overlay" "Guides and maps, inside your game."
card_png c02-find "Stuck?" "Find a guide without leaving the game."
card_png c03-offline "Saved on your device." "Read it offline, any time."
card_png c04-scroll "Scroll by line. Skip by section." "" "D-PAD  SCROLL      L2 / R2  SECTION"
card_png c05-jump "Or jump straight to your level." "" "Y  CONTENTS"
card_png c06-map "No map in the guide?" "Download one."
card_png c07-back "Back to the game." "Your place is kept."
card_png c08-maps "Maps keep their place" "and their zoom, too."
card_png c09-other "Other highlights"
card_png c10-end "ES-DE Overlay" "Free and open source for Android" "clintonium-119.github.io/es-de-overlay"
