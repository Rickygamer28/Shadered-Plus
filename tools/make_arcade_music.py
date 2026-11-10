#!/usr/bin/env python3
"""
Music for the arcade's fighting game: a loop for the select screen and one for each stage, composed here from scratch
(a seeded tune over a chord progression in each stage's own key, mode and tempo: square-wave lead, triangle bass,
pulse arpeggios, noise drums), written as mono Ogg Vorbis that loops cleanly, with sounds.json (streamed).

    python3 tools/make_arcade_music.py

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

MODES = {
    "minor": [0, 2, 3, 5, 7, 8, 10],
    "dorian": [0, 2, 3, 5, 7, 9, 10],
    "phrygian": [0, 1, 3, 5, 7, 8, 10],
    "major": [0, 2, 4, 5, 7, 9, 11],
    "harmonic": [0, 2, 3, 5, 7, 8, 11],
}


def freq(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def wave_of(kind, f, n, duty=0.5):
    t = np.arange(n) / SR
    ph = (t * f) % 1.0
    if kind == "square":
        return np.where(ph < duty, 1.0, -1.0)
    if kind == "tri":
        return 4 * np.abs(ph - 0.5) - 1
    if kind == "saw":
        return 2 * ph - 1
    return np.sin(2 * np.pi * ph)


def note(kind, midi, dur, duty=0.5, attack=0.004, decay=6.0, sustain=0.5, vib=0.0):
    n = int(SR * dur)
    if vib:
        t = np.arange(n) / SR
        f = freq(midi) * (1 + vib * np.sin(2 * np.pi * 5.5 * t) * np.clip(t / 0.15, 0, 1))
        ph = np.cumsum(f) / SR % 1.0
        sig = np.where(ph < duty, 1.0, -1.0) if kind == "square" else 4 * np.abs(ph - 0.5) - 1
    else:
        sig = wave_of(kind, freq(midi), n, duty)
    t = np.arange(n) / SR
    env = np.minimum(1, t / attack) * (sustain + (1 - sustain) * np.exp(-decay * t))
    rel = min(n, int(SR * 0.012))
    env[-rel:] *= np.linspace(1, 0, rel)
    return sig * env


def drum(kind, rng):
    if kind == "kick":
        n = int(SR * 0.18)
        t = np.arange(n) / SR
        f = 120 * np.exp(-t * 18) + 45
        return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 14)
    if kind == "snare":
        n = int(SR * 0.14)
        t = np.arange(n) / SR
        return (rng.uniform(-1, 1, n) * 0.8 + np.sin(2 * np.pi * 190 * t) * 0.4) * np.exp(-t * 22)
    n = int(SR * 0.04)  # hat
    x = rng.uniform(-1, 1, n)
    x = x - np.concatenate([[0], x[:-1]])  # (brighter)
    return x * np.exp(-np.arange(n) / SR * 90) * 0.5


def place(buf, sig, at):
    i = int(at * SR) % len(buf)
    end = i + len(sig)
    if end <= len(buf):
        buf[i:end] += sig
    else:  # (wraps round to the start: the loop stays seamless)
        k = len(buf) - i
        buf[i:] += sig[:k]
        buf[:end - len(buf)] += sig[k:]


def track(seed, root, mode, bpm, progression, bars=16, style=0):
    """A loop: `bars` bars of 4/4 over the progression (scale degrees), repeated."""
    rng = np.random.default_rng(seed)
    scale = MODES[mode]
    beat = 60.0 / bpm
    length = bars * 4 * beat
    lead = np.zeros(int(SR * length))
    bass = np.zeros_like(lead)
    arp = np.zeros_like(lead)
    drums = np.zeros_like(lead)

    def degree(d, octave=0):
        return root + 12 * (octave + d // 7) + scale[d % 7]

    # a two-bar motif, varied: the tune
    motif = []
    pos = 0.0
    while pos < 8:
        dur = rng.choice([0.5, 0.5, 1.0, 0.25, 0.75] if style != 2 else [1.0, 0.5, 1.5, 2.0])
        dur = min(dur, 8 - pos)
        step = int(rng.choice([0, 1, 2, -1, -2, 4, 3]))
        motif.append((pos, dur, step))
        pos += dur
    for bar in range(bars):
        chord = progression[bar % len(progression)]
        t0 = bar * 4 * beat
        # bass: the root, driving eighths (or long notes for the calm ones)
        if style == 2:
            for b in range(2):
                place(bass, note("tri", degree(chord, -2), 2 * beat * 0.95, decay=1.5, sustain=0.6) * 0.55, t0 + b * 2 * beat)
        else:
            for e in range(8):
                oct_jump = 12 if (style == 1 and e % 2 == 1) else 0
                place(bass, note("tri", degree(chord, -2) + oct_jump, beat * 0.45, decay=4, sustain=0.5) * 0.6, t0 + e * beat / 2)
        # arpeggio: the chord's notes in sixteenths
        tones = [degree(chord, 0), degree(chord + 2, 0), degree(chord + 4, 0), degree(chord + 2, 1)]
        for k in range(16):
            place(arp, note("square", tones[k % 4], beat / 4 * 0.8, duty=0.125, decay=14, sustain=0.1) * 0.18, t0 + k * beat / 4)
        # drums
        if style != 2 or bar % 2 == 1:
            for b in range(4):
                if b % 2 == 0 or (style == 1 and rng.random() < 0.3):
                    place(drums, drum("kick", rng) * 0.9, t0 + b * beat)
                if b % 2 == 1:
                    place(drums, drum("snare", rng) * 0.45, t0 + b * beat)
                for h in range(2):
                    place(drums, drum("hat", rng) * 0.3, t0 + b * beat + h * beat / 2)
        # the tune: the motif over pairs of bars, lifted to the chord (and the second half an answer)
        if bar % 2 == 0:
            base = chord + (2 if bar % 8 >= 4 else 0)
            for (p, d, s) in motif:
                if p >= 8:
                    continue
                mid = degree(base + s, 1 if style != 2 else 0)
                place(lead, note("square", mid, d * beat * 0.9, duty=0.25 if style != 1 else 0.5, decay=3,
                                 sustain=0.55, vib=0.006) * 0.32, t0 + p * beat)
    mix = lead + bass + arp + drums
    mix = mix / max(1e-6, np.max(np.abs(mix))) * 0.8
    return mix


TRACKS = {
    "music_select": dict(seed=11, root=57, mode="dorian", bpm=118, progression=[0, 5, 3, 4]),  # A dorian
    "music_temple": dict(seed=23, root=52, mode="phrygian", bpm=136, progression=[0, 1, 0, 6, 5, 6, 0, 0]),
    "music_rooftop": dict(seed=37, root=55, mode="minor", bpm=150, progression=[0, 5, 2, 6], style=1),
    "music_cavern": dict(seed=41, root=50, mode="minor", bpm=96, progression=[0, 3, 5, 4], style=2),
    "music_forge": dict(seed=59, root=48, mode="harmonic", bpm=144, progression=[0, 0, 5, 4, 0, 0, 3, 4]),
    "music_void": dict(seed=73, root=46, mode="phrygian", bpm=128, progression=[0, 1, 0, 1, 5, 4, 1, 0], style=1),
    "music_harbor": dict(seed=89, root=53, mode="dorian", bpm=112, progression=[0, 3, 6, 4, 0, 3, 5, 4]),
    "music_sakura": dict(seed=97, root=60, mode="major", bpm=126, progression=[0, 5, 3, 4, 0, 5, 1, 4]),
}


def write(name, sig):
    pcm = (np.clip(sig, -1, 1) * 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        path = f.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-ac", "1", "-c:a", "libvorbis", "-q:a", "2",
                    os.path.join(OUT, name + ".ogg")], check=True)
    os.unlink(path)


def main():
    os.makedirs(OUT, exist_ok=True)
    sounds_json = os.path.join(ROOT, "sounds.json")
    with open(sounds_json) as f:
        table = json.load(f)
    for name, spec in TRACKS.items():
        write(name, track(**spec))
        table["arcade." + name] = {"sounds": [{"name": "skyblocks:arcade/" + name, "stream": True}]}
    with open(sounds_json, "w") as f:
        json.dump(table, f, indent=2, sort_keys=True)
        f.write("\n")
    print(len(TRACKS), "tracks")


if __name__ == "__main__":
    main()
