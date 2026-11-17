# Demo video

The scripts that cut the demo video on the site's home page, the GIF in the README, and the longer reel.
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

To change the cut:

- **A clip's start, end or speed, a card's length, a caption:** its line in `build.sh`.
- **The running order:** `order.txt`.
- **A card's words:** `cards.sh`, then delete `media/cards/` so they're drawn again.

## What `media/` needs

- `rec/`: the takes, as scrcpy recorded them (`t2-find`, `t34-read`, `t5a-mapdl`, `t5b-map`, `t78-return`,
  `gameplay`, each `.mkv`).
- `font/vag-bold.otf`: the font of the theme in the footage, copied from the theme's folder
  (`_inc/color-schemes/_default/VAG Rounded Std Bold.otf` in MinUI Menu (Overlay)).
- `stills/`: `c01-menu.png` and `c2-k-capture.png`, screenshots of the main menu and the Capture list.

The other stills come from `docs/images/`.

## Shoot a take

With the handheld on adb and a game launched from ES-DE:

```sh
tools/demo-video/rec.sh my-take 20      # records for 20 seconds
tools/demo-video/nova.sh sleep 1 key 158 sleep 1 hat y 1 sleep 0.5 key 305
```

`nova.sh` sends controller input with `sendevent`; its header lists the codes for a Retroid Pocket Nova. scrcpy
records at a variable frame rate, so `build.sh` first converts each take to 30 fps (`deflash.py`).
