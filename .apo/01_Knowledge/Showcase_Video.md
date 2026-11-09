---
note_type: knowledge
template_version: 1
contract_version: 1
knowledge_id: "KNOW-0011"
category: playbooks
title: "Showcase Video"
status: planned
owner: ""
created: '2026-11-09'
updated: '2026-11-09'
reviewed_on: ""
related_notes: []
tags: [apovault, knowledge, playbooks]
---

# Showcase Video

## Body

How to record the two-to-three-minute showcase video on the Retroid Pocket Nova. You play on the device while the computer records. Each shot is its own take, so a retake costs only that shot. The takes are joined at the end.

### Before recording

- Use Shantae (`gbc`). It has a manual, a VGMaps CBZ, StrategyWiki, GameFAQs and IGN guides, and scraped media.
- Start from the MinUI Menu (Custom) theme. Shot 11 switches to Modern, so have Modern installed from ES-DE's theme downloader.
- Keep the Nova on Wi-Fi: shots 7 and 8 download guides.
- Set ES-DE's screensaver to never (UI Settings > Screensaver settings) and set it back afterwards. The Nova's is at 1 minute.
- Have the overlay service running with capture consent (Start Now in its settings).
- Turn the Nova's media volume up. When the media stream is muted or at 0, scrcpy records silence (checked 2026-11-09).
- Nothing personal should be on screen. The shots below don't open Android settings, notifications or account pages.

### Recording with scrcpy (video and sound)

Install it once with `sudo pacman -S scrcpy`. Then record one take per shot from the computer, with the Nova on USB:

```sh
scrcpy --no-window --record=take-01.mp4 --max-size=1280 \
  --video-bit-rate=16M --audio-codec=aac
```

- `--no-window` records without a mirror window; you watch the Nova, not the computer.
- **Sound:**
  - scrcpy takes the Nova's whole audio output, so the Nova's speaker is silent while it records. The sound plays on the computer instead.
  - `--audio-source=playback --audio-dup` would keep the Nova's speaker on, but the Nova's firmware refuses it (`registerAudioPolicy() returned -1`). scrcpy then records without sound.
- `--max-size=1280` matches the screen (1280×960), so nothing is scaled.
- Press Ctrl-C to end a take; scrcpy finishes the file on exit.
- The game is paused while the overlay is open, so menu, guide and viewer shots are silent apart from the game around them.

### Fallback: the Nova's own screenrecord (video only)

Use this when scrcpy isn't available or its audio fails:

```sh
adb shell screenrecord --bit-rate 8M /sdcard/Movies/take-01.mp4
# Ctrl-C to stop; 180 s is the hard maximum
adb pull /sdcard/Movies/take-01.mp4 && adb shell rm /sdcard/Movies/take-01.mp4
```

- These takes are 1280×960 landscape and have no sound.
- A cut made from them is silent. Join only screenrecord takes together, because the join below expects every cut to carry the same streams.

### Cutting and joining

Cut each take to the part you want, with the start and end in seconds. Re-encoding makes the cut land exactly. `loudnorm` evens out the level; the raw capture peaks around −22 dB at half volume:

```sh
ffmpeg -ss 1.0 -to 11.0 -i take-01.mp4 \
  -c:v libx264 -crf 18 -preset slow -pix_fmt yuv420p \
  -af loudnorm=I=-16:TP=-1.5:LRA=11 -c:a aac -b:a 160k -ar 48000 cut-01.mp4
```

List the cuts in `cuts.txt`, one `file 'cut-NN.mp4'` line each, in order. Then join them without re-encoding:

```sh
ffmpeg -f concat -safe 0 -i cuts.txt -c copy -movflags +faststart showcase.mp4
```

For screenrecord takes, replace the `-af … -ar 48000` part with `-an`.

### Shot list (2:55 as listed; trim the waits to land near 2:30)

| # | Shot | Length |
|---|---|---|
| 1 | ES-DE gamelist on Shantae; launch it. | 0:10 |
| 2 | Back opens the menu; D-pad over the rows; B returns to the game. | 0:10 |
| 3 | Capture > Screenshot and Video; play for the 15 s clip; the Saved toast. | 0:25 |
| 4 | Home: the overlay ends the game and ES-DE shows the new screenshot, then plays the clip. | 0:10 |
| 5 | Relaunch; Manual; turn a page; R1 to zoom and pan. | 0:15 |
| 6 | Guides > VGMaps - Shantae.cbz; Y for the maps list; jump to a map; zoom and pan. | 0:20 |
| 7 | Find Guides > StrategyWiki; the progress; it opens; Y for the contents. | 0:25 |
| 8 | Find Guides > Search GameFAQs; pick DEngel's guide; Save Guide; it opens in the viewer. Trim the page loads. | 0:30 |
| 9 | Game Info: scroll the description. | 0:05 |
| 10 | Quit Game: ES-DE is back at once. | 0:05 |
| 11 | ES-DE UI Settings > Theme > Modern; launch Shantae; Back: the menu now matches Modern. | 0:20 |

- **Shot 4 and shot 10 both end the game.** Save in-game before them if the progress matters.
- **Shot 8: GameFAQs may show Cloudflare's "Verify you are human" box.** Tap it yourself and cut the wait from the take.
- **DEngel's guide is a single page**, so it saves without a progress counter. To show "Saving page N of M", use an IGN guide through Search the Web instead.
- **Find Guides and Save Guide replace the guide files Shantae already has.** No extra files are left behind.

### After recording

- Switch the theme back to MinUI Menu (Custom) and set the screensaver back.
- Keep video files out of the repo. Upload the cut, then link it from the README.
- No title cards or music yet; add them in an editor later if wanted.

## References

- `docs/images/`: the screenshot set taken from the same scenes.
- scrcpy's `--help` (5.0.1): the source for the flags above.
- Trial on 2026-11-09 (Nova, Android 13, scrcpy 5.0.1): shot 2 recorded with the scrcpy command above at 1280×960 with an AAC stereo track. A 3-second screenrecord take came out 1280×960 landscape with no sound. The cut-and-join commands produced an exact 11.5 s file from two cuts.
