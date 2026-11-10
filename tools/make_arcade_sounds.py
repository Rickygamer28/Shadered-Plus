#!/usr/bin/env python3
"""
The arcade cabinet's sounds: little chiptune effects made from scratch here (square, pulse, triangle and saw waves,
noise, pitch sweeps and envelopes - no recordings), written as mono Ogg Vorbis into the mod's sounds, with sounds.json.

    python3 tools/make_arcade_sounds.py

Needs numpy and ffmpeg (with libvorbis).
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 22050
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "skyblocks")
OUT = os.path.join(ROOT, "sounds", "arcade")
rng = np.random.default_rng(28)

NOTES = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}


def n(name):
    """A note's frequency: 'C5', 'F#4' ..."""
    pitch, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((NOTES[pitch] + (octave - 4) * 12) / 12)


def t(dur):
    return np.arange(int(SR * dur)) / SR


def osc(freq, dur, wave="square", duty=0.5, vib=0.0, vib_rate=6.0):
    """A wave; freq a number or (start, end) for a sweep (exponential)."""
    tt = t(dur)
    if isinstance(freq, tuple):
        f0, f1 = freq
        f = f0 * (f1 / f0) ** (tt / max(dur, 1e-6))
    else:
        f = np.full_like(tt, float(freq))
    if vib:
        f = f * (1 + vib * np.sin(2 * np.pi * vib_rate * tt))
    phase = np.cumsum(f) / SR
    ph = phase % 1.0
    if wave == "square":
        return np.where(ph < duty, 1.0, -1.0)
    if wave == "tri":
        return 4 * np.abs(ph - 0.5) - 1
    if wave == "saw":
        return 2 * ph - 1
    return np.sin(2 * np.pi * phase)


def noise(dur, cutoff=8000.0, cutoff_end=None, steps=1):
    """White noise through a one-pole low-pass (cutoff sweeping if given); steps > 1 holds each value (crunchier)."""
    count = int(SR * dur)
    x = rng.uniform(-1, 1, count)
    if steps > 1:
        x = np.repeat(x[::steps], steps)[:count]
    cut = np.full(count, cutoff) if cutoff_end is None else cutoff * (cutoff_end / cutoff) ** (np.arange(count) / max(count, 1))
    a = 1 - np.exp(-2 * np.pi * cut / SR)
    y = np.zeros(count)
    acc = 0.0
    for i in range(count):
        acc += a[i] * (x[i] - acc)
        y[i] = acc
    return y / max(1e-6, np.max(np.abs(y)))


def env(sig, attack=0.002, release=None, curve=4.0, hold=0.0):
    """Fade in quickly, then die away (exponentially, curve steeper = quicker)."""
    count = len(sig)
    e = np.ones(count)
    a = int(SR * attack)
    if a > 0:
        e[:a] = np.linspace(0, 1, a)
    h = a + int(SR * hold)
    if h < count:
        rest = np.arange(count - h) / max(count - h, 1)
        e[h:] = np.exp(-curve * rest) * (1 - rest) if release is None else np.exp(-curve * rest)
    return sig * e


def seq(*parts, gap=0.0):
    out = []
    for p in parts:
        out.append(p)
        if gap:
            out.append(np.zeros(int(SR * gap)))
    return np.concatenate(out)


def mix(*layers):
    """(signal, start seconds, gain) layers, summed."""
    length = max(int(SR * s) + len(sig) for sig, s, g in layers)
    out = np.zeros(length)
    for sig, s, g in layers:
        i = int(SR * s)
        out[i:i + len(sig)] += sig * g
    return out


def notes(names, each, wave="square", duty=0.5, last=None, curve=3.0):
    parts = []
    for i, name in enumerate(names):
        d = last if (last and i == len(names) - 1) else each
        parts.append(env(osc(n(name), d, wave, duty), curve=curve))
    return seq(*parts)


def crush(sig, bits=6):
    q = 2 ** (bits - 1)
    return np.round(sig * q) / q


def echo(sig, delay=0.12, gain=0.35, times=3):
    layers = [(sig, 0, 1.0)]
    for k in range(1, times + 1):
        layers.append((sig, delay * k, gain ** k))
    return mix(*layers)


S = {}

# ---- the cabinet ----
S["blip"] = env(osc(1046, 0.035, "square", 0.25), curve=2)
S["select"] = notes(["C5", "E5", "G5", "C6"], 0.045, "square", 0.25)
S["start"] = notes(["G5", "C6", "E6", "G6", "E6", "G6"], 0.06, "square", 0.25, last=0.25)
S["coin"] = seq(env(osc(n("C6"), 0.05, "square", 0.25), curve=1), env(osc(n("G6"), 0.05, "square", 0.25), curve=1),
                env(osc(n("C7"), 0.22, "square", 0.25), curve=3))
S["gameover"] = seq(notes(["E5", "D5", "C5", "A4", "B4"], 0.16, "tri", curve=1.5),
                    env(osc(n("G#4"), 0.6, "tri", vib=0.012), curve=2.5))
S["win"] = seq(notes(["C5", "E5", "G5", "C6", "E6"], 0.07, "square", 0.25),
               env(mix((osc(n("C6"), 0.5, "square", 0.25), 0, 0.5), (osc(n("G6"), 0.5, "tri"), 0, 0.5)), curve=2.5))
S["levelup"] = notes(["G4", "B4", "D5", "G5", "B5", "D6"], 0.035, "square", 0.5, last=0.15)

# ---- snake ----
S["snake_eat"] = env(osc((520, 1400), 0.07, "square", 0.3), curve=1.5)
S["snake_die"] = mix((env(noise(0.25, 5000, 600)), 0, 0.6), (env(osc((420, 70), 0.4, "square")), 0, 0.5))

# ---- breakout ----
S["paddle"] = env(osc(n("G4"), 0.05, "square"), curve=3)
S["wall"] = env(osc(n("G5"), 0.03, "square", 0.25), curve=3)
S["brick"] = env(osc(n("E6"), 0.06, "square", 0.25), curve=4)
S["lose"] = env(osc((660, 90), 0.55, "tri", vib=0.03, vib_rate=11), curve=1.2)

# ---- tetris ----
S["move"] = env(osc(1568, 0.015, "square", 0.5), curve=2)
S["rotate"] = env(osc((1000, 1400), 0.035, "square", 0.25), curve=2)
S["land"] = mix((env(noise(0.06, 1500), curve=6), 0, 0.6), (env(osc((140, 70), 0.09, "sine"), curve=4), 0, 0.8))
S["line"] = mix((env(osc((700, 1700), 0.14, "square", 0.25), curve=1.5), 0, 0.6),
                (notes(["C6", "E6", "G6"], 0.05, "tri"), 0.08, 0.6))
S["four"] = mix((notes(["C6", "E6", "G6", "C7", "E7", "G7"], 0.045, "square", 0.25, last=0.3), 0, 0.6),
                (env(noise(0.4, 9000, 2000), curve=3), 0, 0.15))
S["drop"] = mix((env(osc((320, 60), 0.08, "square"), curve=2), 0, 0.6), (env(noise(0.07, 2500)), 0, 0.5))

# ---- whisker maze ----
S["chomp_a"] = env(osc((480, 260), 0.06, "tri"), curve=1.5)
S["chomp_b"] = env(osc((300, 520), 0.06, "tri"), curve=1.5)
S["power"] = env(osc((260, 900), 0.45, "square", 0.3, vib=0.06, vib_rate=14), curve=1.2)
S["catch"] = env(osc((180, 2200), 0.28, "square", 0.25), curve=1.2)
S["death"] = seq(env(osc((900, 120), 0.9, "square", 0.5, vib=0.08, vib_rate=9), curve=1.0),
                 env(osc((300, 60), 0.2, "tri"), curve=2))
S["bonus"] = notes(["E6", "B6", "E7"], 0.05, "square", 0.25, last=0.2)
S["maze_start"] = notes(["C5", "C6", "G5", "E5", "C6", "G5", "E5", "F5", "G5", "A5", "B5", "C6"], 0.08, "square", 0.25, last=0.3)

# ---- pong ----
S["pong_hit"] = env(osc(480, 0.045, "square"), curve=2)
S["pong_wall"] = env(osc(240, 0.045, "square"), curve=2)
S["pong_score"] = seq(env(osc(n("C6"), 0.08, "square", 0.25), curve=1), env(osc(n("G6"), 0.16, "square", 0.25), curve=2))
S["pong_lose"] = seq(env(osc(n("E4"), 0.1, "square"), curve=1), env(osc(n("A3"), 0.25, "square"), curve=2))

# ---- frogger ----
S["hop"] = env(osc((300, 760), 0.05, "square", 0.25), curve=1.5)
S["splash"] = mix((env(noise(0.4, 6000, 400), curve=2.5), 0, 0.7), (env(osc((900, 200), 0.2, "sine")), 0, 0.3))
S["squash"] = mix((env(noise(0.12, 2500, 300, steps=3), curve=3), 0, 0.8), (env(osc((110, 45), 0.18, "square")), 0, 0.5))
S["home"] = notes(["E5", "G5", "B5", "E6"], 0.06, "square", 0.25, last=0.2)

# ---- minesweeper ----
S["reveal"] = env(osc(900, 0.025, "tri"), curve=3)
S["flood"] = env(osc((500, 1300), 0.12, "tri"), curve=1.5)
S["flag"] = seq(env(osc(1200, 0.02, "square", 0.25), curve=2), env(osc(1700, 0.04, "square", 0.25), curve=2))
S["boom"] = echo(mix((env(noise(0.9, 4000, 150, steps=2), curve=2.5), 0, 0.9), (env(osc((90, 30), 0.8, "sine"), curve=2), 0, 0.8)),
                 0.15, 0.3, 2)

# ---- showdown ----
S["hit_light"] = mix((env(noise(0.05, 7000, 2000), curve=5), 0, 0.7), (env(osc((220, 120), 0.07, "sine"), curve=4), 0, 0.8))
S["hit_heavy"] = mix((env(noise(0.16, 4000, 500, steps=2), curve=4), 0, 0.8), (env(osc((150, 50), 0.22, "sine"), curve=3), 0, 1.0),
                     (env(osc((300, 90), 0.08, "square"), curve=4), 0, 0.25))
S["block"] = mix((env(noise(0.04, 9000), curve=6), 0, 0.5), (env(osc(1500, 0.05, "square", 0.25), curve=5), 0, 0.35),
                 (env(osc(2250, 0.04, "tri"), curve=5), 0, 0.3))
S["whoosh"] = env(noise(0.14, 600, 3500), attack=0.03, curve=3)
S["counter"] = mix((env(osc((2400, 500), 0.12, "saw"), curve=2), 0, 0.5), (env(noise(0.06, 9000), curve=5), 0, 0.4))
S["launch"] = mix((env(osc((70, 340), 0.22, "sine"), curve=2), 0, 0.9), (env(noise(0.12, 3000, 6000), curve=3), 0, 0.4))
S["screw"] = env(osc((500, 900), 0.28, "tri", vib=0.2, vib_rate=30), curve=1.5)
S["wall_splat"] = mix((env(noise(0.35, 3000, 200, steps=2), curve=3), 0, 0.9), (env(osc((80, 35), 0.35, "sine"), curve=2.5), 0, 1.0),
                      (env(osc((240, 120), 0.1, "square"), curve=3), 0.02, 0.3))
S["throw"] = mix((env(osc((120, 60), 0.12, "sine"), curve=3), 0, 0.8), (env(noise(0.25, 2000, 200), curve=3), 0.12, 0.7),
                 (env(osc((90, 40), 0.25, "sine"), curve=2.5), 0.12, 0.9))
S["ko"] = echo(mix((env(noise(1.0, 5000, 120, steps=2), curve=2.2), 0, 0.9), (env(osc((70, 28), 1.0, "sine"), curve=1.8), 0, 1.0),
                   (env(osc((600, 120), 0.3, "saw"), curve=2), 0, 0.25)), 0.22, 0.35, 3)
gong = sum(env(osc(f, 1.4, "sine"), curve=c) * g for f, c, g in [(196, 2.0, 0.6), (267, 2.4, 0.4), (411, 3.0, 0.35), (563, 3.6, 0.25), (782, 4.5, 0.15)])
S["round"] = mix((gong, 0, 1.0), (env(noise(0.05, 4000), curve=6), 0, 0.3))
S["fight"] = mix(*[(env(osc(n(x), 0.35, "saw"), curve=2.5), 0, 0.3) for x in ["C4", "G4", "C5", "E5"]],
                 (env(noise(0.08, 6000), curve=5), 0, 0.3))
S["rage"] = mix((env(osc((40, 90), 0.7, "saw", vib=0.04, vib_rate=8), attack=0.2, curve=2), 0, 0.6),
                (env(noise(0.7, 300, 2500), attack=0.3, curve=2), 0, 0.5))
S["crush"] = mix((env(osc(310, 0.3, "square", 0.3), curve=3), 0, 0.35), (env(osc(463, 0.3, "square", 0.4), curve=3.5), 0, 0.3),
                 (env(noise(0.1, 8000), curve=5), 0, 0.4), (env(osc(1240, 0.25, "tri"), curve=4), 0, 0.2))
S["ki"] = env(osc((180, 900), 0.7, "sine", vib=0.05, vib_rate=18), attack=0.1, curve=1.2) * 0.8 + \
    env(osc((360, 1800), 0.7, "tri"), attack=0.1, curve=1.5) * 0.2
S["parry"] = mix((env(osc(2100, 0.35, "sine"), curve=3), 0, 0.6), (env(osc(3150, 0.3, "sine"), curve=4), 0, 0.35),
                 (env(noise(0.03, 9000), curve=6), 0, 0.3))
S["thud"] = mix((env(noise(0.08, 900), curve=4), 0, 0.7), (env(osc((90, 50), 0.1, "sine"), curve=3), 0, 0.7))
crackle = noise(0.3, 9000) * (rng.uniform(0, 1, int(SR * 0.3)) > 0.82)
S["electric"] = mix((env(crackle, curve=2), 0, 0.8), (env(osc((1600, 700), 0.25, "saw", vib=0.1, vib_rate=60), curve=2), 0, 0.3))
S["step"] = env(noise(0.045, 1200), curve=5)


def write(name, sig):
    sig = np.asarray(sig, dtype=np.float64)
    peak = np.max(np.abs(sig))
    if peak > 0:
        sig = sig / peak * 0.85
    sig = crush(sig, 8)
    fade = min(len(sig), int(SR * 0.004))  # (no click at the end)
    if fade:
        sig[-fade:] *= np.linspace(1, 0, fade)
    pcm = (np.clip(sig, -1, 1) * 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        path = f.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-ac", "1", "-c:a", "libvorbis", "-q:a", "4",
                    os.path.join(OUT, name + ".ogg")], check=True)
    os.unlink(path)


def main():
    os.makedirs(OUT, exist_ok=True)
    sounds_json = os.path.join(ROOT, "sounds.json")
    table = {}
    if os.path.exists(sounds_json):
        with open(sounds_json) as f:
            table = json.load(f)
    for name, sig in S.items():
        write(name, sig)
        table["arcade." + name] = {"sounds": [{"name": "skyblocks:arcade/" + name, "stream": False}]}
    with open(sounds_json, "w") as f:
        json.dump(table, f, indent=2, sort_keys=True)
        f.write("\n")
    print(len(S), "sounds")


if __name__ == "__main__":
    main()
