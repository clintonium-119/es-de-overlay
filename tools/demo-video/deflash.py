#!/usr/bin/env python3
"""deflash.py in.mkv out.mp4: converts a scrcpy recording to 30 fps, leaving out any frame that was on screen
for under 60 ms after a big picture change (the game showing for a frame between two menu lists)."""
import re, subprocess, sys
src, out = sys.argv[1:3]
log = subprocess.run(["ffmpeg", "-v", "error", "-i", src, "-vf", "select='gte(scene,0)',metadata=print:file=-", "-an", "-f", "null", "-"],
                     capture_output=True, text=True).stdout
pts = [float(x) for x in re.findall(r"pts_time:([0-9.]+)", log)]
score = [float(x) for x in re.findall(r"scene_score=([0-9.]+)", log)]
# ffmpeg scores a frame by the smaller of its change and the previous one's, so the first flash frame scores high
# and the frames after it low. A flash lasts a frame or three: after a big change, leave out every frame of the next
# 60 ms but the last. Where there was no flash, those frames were all the same picture anyway.
drop = []
for i in range(1, len(pts)):
    if score[i] > 0.3 and pts[i] not in drop:
        run = [t for t in pts[i:] if t - pts[i] < 0.06]
        drop += run[:-1]
keep = "*".join(f"not(between(t,{t - 0.002:.3f},{t + 0.002:.3f}))" for t in drop) or "1"
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, "-vf", f"select='{keep}',fps=30", "-c:v", "libx264", "-crf", "12",
                "-preset", "fast", "-pix_fmt", "yuv420p", out], check=True)
print(f"{src.split('/')[-1]}: {len(pts)} frames, dropped {len(drop)} at {[round(t, 2) for t in drop]}")
