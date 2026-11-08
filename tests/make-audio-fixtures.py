"""Extra decoder fixtures for software playback; requires ffmpeg. No production assets."""
from pathlib import Path
import subprocess
out = Path(__file__).resolve().parents[1] / 'build' / 'fixtures'
out.mkdir(parents=True, exist_ok=True)
formats = [('wav', 'pcm_s16le', 2), ('m4a', 'aac', 2), ('flac', 'flac', 2), ('ogg', 'libvorbis', 2), ('opus', 'libopus', 2), ('24.wav', 'pcm_s24le', 2), ('surround.m4a', 'aac', 6)]
for name, codec, channels in formats:
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=3', '-ac', str(channels), '-ar', '48000', '-c:a', codec, str(out / ('dsp-fixture.' + name))], check=True)
