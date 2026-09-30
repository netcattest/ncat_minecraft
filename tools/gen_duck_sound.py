import math
import os
import shutil
import struct
import subprocess
import tempfile
import wave


ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUTPUT = os.path.join(ROOT, "src", "main", "resources", "assets", "ncat_minecraft", "sounds", "duck_squeak.ogg")


def main():
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise SystemExit("ffmpeg local não encontrado")
    rate = 32000
    duration = 0.24
    samples = bytearray()
    phase = 0.0
    for index in range(int(rate * duration)):
        t = index / rate
        sweep = math.sin(math.pi * min(t / 0.2, 1.0))
        frequency = 660.0 + 460.0 * sweep + 32.0 * math.sin(2.0 * math.pi * 13.0 * t)
        phase += 2.0 * math.pi * frequency / rate
        attack = min(1.0, t / 0.012)
        release = min(1.0, (duration - t) / 0.065)
        envelope = max(0.0, min(attack, release)) ** 1.4
        voice = math.sin(phase) + 0.22 * math.sin(2.0 * phase) + 0.07 * math.sin(3.0 * phase)
        sample = max(-1.0, min(1.0, voice * envelope * 0.45))
        samples += struct.pack("<h", int(sample * 32767))
    with tempfile.TemporaryDirectory() as folder:
        wav = os.path.join(folder, "duck_squeak.wav")
        with wave.open(wav, "wb") as handle:
            handle.setnchannels(1)
            handle.setsampwidth(2)
            handle.setframerate(rate)
            handle.writeframes(samples)
        subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-i", wav,
                        "-c:a", "libvorbis", "-q:a", "5", OUTPUT], check=True)
    print(OUTPUT)


if __name__ == "__main__":
    main()
