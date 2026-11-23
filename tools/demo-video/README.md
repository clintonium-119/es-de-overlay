# Demo video

The scripts that cut the demo video on the site's home page, the GIF in the README, the longer reel, and the
Game Switcher's own promo video and loop.
Recordings and everything built from them live in `media/`, which git ignores: the footage is large and is of a
commercial game, and the font is the theme's own.

## Cut it again

You need `ffmpeg`, ImageMagick (`magick`) and Python 3.

```sh
tools/demo-video/build.sh
```

It reads the takes in `media/rec/` and writes to `media/out/`:

| File | For |
| --- | --- |
| `es-de-overlay-demo.mp4` | the reel, 1280x960 |
| `es-de-overlay-demo-youtube.mp4` | YouTube, 1080p with black sides |
| `es-de-overlay-demo-discord.mp4` | Discord, 960x720 and under 10 MB |
| `demo-loop.mp4` | the home page: copy to `docs/assets/demo-loop.mp4` |
| `demo.gif` | the README: copy to `docs/images/demo.gif` |
| `es-de-overlay-switcher.mp4` | the Game Switcher's promo, 1280x960 |
| `es-de-overlay-switcher-youtube.mp4` | YouTube, 1080p with black sides |
| `es-de-overlay-switcher-discord.mp4` | Discord, 960x720 and under 10 MB |
| `switcher-loop.mp4` | the home page: copy to `docs/assets/switcher-loop.mp4` |

To change the cut:

- **A clip's start, end or speed, a card's length, a caption:** its line in `build.sh`.
- **The running order:** `order.txt` for the reel, `order-switcher.txt` for the switcher's promo.
- **A list of games that shouldn't show:** a `hide` line in `build.sh` paints parts of a clip black between two times.
- **How a clip meets a card:** `soft` fades a clip in from black and out to it, as the cards do.
- **Time left out inside a clip:** `blend` dissolves one piece into the next, so the gap shows as one.
- **A card's words:** `cards.sh`, then delete `media/cards/` so they're drawn again.

## What `media/` needs

- `rec/`: the takes, as scrcpy recorded them (`t2-find`, `t34-read`, `t5a-mapdl`, `t5b-map`, `t78-return`,
  `gameplay`, each `.mkv`), and the switcher's: `sw-open` (the strip opening over ES-DE and the D-pad across five
  games), `sw-pick` (a pick of a RetroArch game), `sw-return` (a move in the game, then Home, and the switcher
  back), `sw-far` (the strip across three games, then a pick of a PPSSPP game; the promo uses the strip) and `sw-one` (One Game a Screen with the detail line on).
- `font/vag-bold.otf`: the font of the theme in the footage, copied from the theme's folder
  (`_inc/color-schemes/_default/VAG Rounded Std Bold.otf` in MinUI Menu (Overlay)).
- `stills/`: `c01-menu.png` and `c2-k-capture.png`, screenshots of the main menu and the Capture list, and
  `sw-settings.png`, the app's Game Switcher settings with every line ticked.

The other stills come from `docs/images/`.

## Shoot a take

With the handheld on adb and a game launched from ES-DE:

```sh
tools/demo-video/rec.sh my-take 20      # records for 20 seconds
tools/demo-video/nova.sh sleep 1 key 158 sleep 1 hat y 1 sleep 0.5 key 305
```

`nova.sh` sends controller input with `sendevent`; its header lists the codes for a Retroid Pocket Nova (Home
is 102). scrcpy records at a variable frame rate, so `build.sh` first converts each take to 30 fps (`deflash.py`).

The switcher's takes were shot with RetroArch's shader off (a 60 fps recording of a 120 Hz shader comes out dark),
the switcher set to show five games, and ES-DE on a system's list for `sw-open`. ES-DE's launch screen shows its
Last Played list around its panel, and the list shows for a few frames on the way back from a game, which is why
`build.sh` paints those parts black.

In the switcher's promo, what the device does runs at its own speed: the switcher opening, the first pick from the
press to the game, and Home back to the switcher. Waiting is what's cut: the switcher's picture doesn't move
between presses, so a hold there can be any length; a dissolve covers other time left out.
