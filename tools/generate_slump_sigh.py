"""Synthesize the slump sound. Requires NumPy, SciPy and FFmpeg."""

from pathlib import Path
import subprocess

import numpy as np
from scipy.signal import butter, sosfilt


RATE = 44100
SECONDS = 1.56
DESTINATION = (
    Path(__file__).resolve().parents[1]
    / "src/main/resources/assets/vergil_chair/sounds/slump_sigh.ogg"
)


def rms(samples):
    return np.sqrt(np.mean(samples ** 2))


def main():
    time = np.arange(round(RATE * SECONDS)) / RATE
    pitch = np.interp(time, [0, .05, .11, .20, .33, .46], [128, 134, 122, 103, 77, 68])
    pitch += 1.5 * np.sin(2 * np.pi * 6.2 * time)
    phase = np.cumsum(2 * np.pi * pitch / RATE)

    # Start with a softly voiced exhale, then let the breath carry the tail.
    harmonics = np.arange(1, 26)[:, None]
    frequency = harmonics * pitch
    formants = .25 + .85 * np.exp(-((frequency - 475) / 235) ** 2)
    formants += .34 * np.exp(-((frequency - 1050) / 315) ** 2)
    voiced = np.sum(
        np.sin(harmonics * phase + .14 * harmonics ** .7)
        * formants / harmonics ** 1.2,
        axis=0,
    )
    voiced /= rms(voiced)

    noise = np.random.default_rng(146).standard_normal(len(time))
    air = sosfilt(butter(2, [160, 1550], fs=RATE, btype="bandpass", output="sos"), noise)
    air += .6 * sosfilt(butter(2, [950, 4150], fs=RATE, btype="bandpass", output="sos"), noise)
    air /= rms(air)

    voice_db = np.interp(
        time,
        [0, .02, .052, .105, .18, .28, .42, .61, .85, 1.04],
        [-94, -32, -4, 0, -5, -19, -48, -76, -96, -100],
    )
    breath_db = np.interp(
        time,
        [0, .02, .052, .105, .18, .30, .58, .87, 1.17, 1.37, 1.48, SECONDS],
        [-94, -34, -17, -15, -13, -15, -19, -26, -34, -45, -61, -94],
    )
    sound = voiced * 10 ** (voice_db / 20) + air * 10 ** (breath_db / 20)
    pcm = (sound * (0.64 * 32767 / np.max(np.abs(sound)))).astype("<i2")

    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
         "-f", "s16le", "-ar", str(RATE), "-ac", "1", "-i", "pipe:0",
         "-c:a", "libvorbis", "-q:a", "4", str(DESTINATION)],
        input=pcm.tobytes(),
        check=True,
    )


if __name__ == "__main__":
    main()
