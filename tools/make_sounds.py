"""
Generates every sound effect and music loop in app/src/main/res/raw.

All audio is synthesised from scratch (numpy + scipy), so nothing here is
sampled from, or transcribed from, any commercial game. The music is written
in the spirit of late-80s / early-90s console sports games: two square-wave
channels, a triangle bass and a noise drum kit, plus a Hammond-style arena
organ for rally riffs.

Run from the project root:  python tools/make_sounds.py
"""
import os
import wave

import numpy as np
from scipy.signal import butter, lfilter

SR = 22050
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app", "src", "main", "res", "raw")
rng = np.random.default_rng(1994)


# ----------------------------------------------------------------- helpers

def save(name, data, peak=0.9):
    data = np.asarray(data, dtype=np.float64)
    m = np.max(np.abs(data)) or 1.0
    data = data / m * peak
    pcm = (np.clip(data, -1, 1) * 32767).astype("<i2")
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(f"{name:20s} {len(data) / SR:6.2f} s  {os.path.getsize(path) // 1024:5d} KB")


def seconds(n):
    return int(n * SR)


def t_axis(n):
    return np.arange(n) / SR


def adsr(n, a, d, s, r, sus=0.7):
    e = np.zeros(n)
    A, D, R = seconds(a), seconds(d), seconds(r)
    A = min(A, n)
    D = min(D, max(0, n - A))
    R = min(R, max(0, n - A - D))
    S = max(0, n - A - D - R)
    e[:A] = np.linspace(0, 1, A, endpoint=False)
    e[A:A + D] = np.linspace(1, sus, D, endpoint=False)
    e[A + D:A + D + S] = sus
    e[A + D + S:A + D + S + R] = np.linspace(sus, 0, R)
    return e


def decay(n, tau):
    return np.exp(-t_axis(n) / tau)


def bandpass(x, lo, hi, order=2):
    b, a = butter(order, [lo / (SR / 2), hi / (SR / 2)], btype="band")
    return lfilter(b, a, x)


def highpass(x, f, order=2):
    b, a = butter(order, f / (SR / 2), btype="high")
    return lfilter(b, a, x)


def lowpass(x, f, order=2):
    b, a = butter(order, f / (SR / 2), btype="low")
    return lfilter(b, a, x)


def white(n):
    return rng.uniform(-1, 1, n)


def chip_noise(n, hold=1):
    """NES-style noise: random +-1 values held for `hold` samples."""
    v = rng.choice([-1.0, 1.0], size=n // hold + 1)
    return np.repeat(v, hold)[:n]


def echo(x, delay_s, gain, repeats=3):
    d = seconds(delay_s)
    out = np.copy(x)
    out = np.concatenate([out, np.zeros(d * repeats)])
    for i in range(1, repeats + 1):
        out[i * d:i * d + len(x)] += x * (gain ** i)
    return out


# ------------------------------------------------------------ oscillators

def pulse(freq, n, duty=0.5):
    t = t_axis(n)
    return np.where((t * freq) % 1.0 < duty, 1.0, -1.0)


def triangle(freq, n):
    t = t_axis(n)
    x = 2 * np.abs(2 * ((t * freq) % 1.0) - 1) - 1
    return np.round(x * 7.5) / 7.5  # 4-bit steps like the NES triangle


def organ(freq, n):
    """Drawbar organ: fundamental plus harmonics, slight vibrato and key click."""
    t = t_axis(n)
    vib = 1 + 0.004 * np.sin(2 * np.pi * 6 * t)
    x = np.zeros(n)
    for h, g in ((1, 1.0), (2, 0.6), (3, 0.4), (4, 0.3), (6, 0.15), (8, 0.1)):
        x += g * np.sin(2 * np.pi * freq * h * t * vib)
    click = white(n) * decay(n, 0.004) * 0.3
    return x / 2.5 + click


NOTE_INDEX = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def note_freq(name):
    """'C5', 'F#4', 'Bb3' -> Hz."""
    letter = name[0]
    rest = name[1:]
    semi = NOTE_INDEX[letter]
    if rest.startswith("#"):
        semi += 1
        rest = rest[1:]
    elif rest.startswith("b"):
        semi -= 1
        rest = rest[1:]
    octave = int(rest)
    midi = 12 * (octave + 1) + semi
    return 440.0 * 2 ** ((midi - 69) / 12)


# -------------------------------------------------------------- sequencer

class Song:
    def __init__(self, bpm, bars, beats_per_bar=4):
        self.bpm = bpm
        self.beats = bars * beats_per_bar
        self.n = seconds(self.beats * 60 / bpm)
        self.mix = np.zeros(self.n)

    def sec_per_beat(self):
        return 60 / self.bpm

    def add(self, start_beat, dur_beats, wave, gain, seamless=True):
        i = int(start_beat * self.sec_per_beat() * SR)
        j = i + len(wave)
        if j <= self.n:
            self.mix[i:j] += wave * gain
        elif seamless:
            k = self.n - i
            self.mix[i:] += wave[:k] * gain
            self.mix[:len(wave) - k] += wave[k:] * gain  # wrap the tail for a clean loop
        else:
            self.mix[i:] += wave[:self.n - i] * gain

    def render(self):
        return self.mix


def parse_line(line):
    """'E5:2 G5:2 R:4' -> [(note or None, sixteenths)]"""
    out = []
    for tok in line.split():
        name, dur = tok.split(":")
        out.append((None if name == "R" else name, int(dur)))
    return out


def chip_note(freq, dur_s, duty=0.5, a=0.005, d=0.05, s=0.75, r=0.03):
    n = seconds(dur_s)
    return pulse(freq, n, duty) * adsr(n, a, d, s, r, s)


def add_melody(song, lines, start_bar, duty=0.5, gain=0.22, legato=0.92, octave_shift=0):
    beat = start_bar * 4
    for line in lines:
        for name, six in parse_line(line):
            dur_beats = six / 4
            if name is not None:
                f = note_freq(name) * (2 ** octave_shift)
                w = chip_note(f, dur_beats * song.sec_per_beat() * legato, duty)
                song.add(beat, dur_beats, w, gain)
            beat += dur_beats


CHORDS = {
    "C": ["C", "E", "G"], "G": ["G", "B", "D"], "Am": ["A", "C", "E"], "F": ["F", "A", "C"],
    "Em": ["E", "G", "B"], "Dm": ["D", "F", "A"], "E": ["E", "G#", "B"], "D": ["D", "F#", "A"],
    "Bb": ["Bb", "D", "F"], "Eb": ["Eb", "G", "Bb"],
}


def chord_notes(chord, octave):
    names = CHORDS[chord]
    root = note_freq(names[0] + str(octave))
    out = []
    for nm in names:
        f = note_freq(nm + str(octave))
        if f < root:
            f *= 2
        out.append(f)
    return out


def add_arpeggio(song, chords, start_bar, octave=4, duty=0.25, gain=0.13, pattern=(0, 1, 2, 3)):
    """16th-note arpeggios: pattern indexes into [root, 3rd, 5th, root+8ve]."""
    beat = start_bar * 4
    for chord in chords:
        f = chord_notes(chord, octave)
        f = f + [f[0] * 2]
        for b in range(4):
            for k, idx in enumerate(pattern):
                if idx is None:
                    continue
                w = chip_note(f[idx], song.sec_per_beat() * 0.24, duty, r=0.02)
                song.add(beat + b + k * 0.25, 0.25, w, gain)
        beat += 4


def add_bass(song, chords, start_bar, gain=0.5, driving=False):
    beat = start_bar * 4
    for chord in chords:
        f = chord_notes(chord, 2)
        root, fifth = f[0], f[2]
        if driving:
            seq = [root, root, root, root, fifth, fifth, root, root * 2]
        else:
            seq = [root, root * 2, root, root * 2, fifth, fifth * 2, root, root * 2]
        for k, fr in enumerate(seq):
            n = seconds(song.sec_per_beat() * 0.48)
            w = triangle(fr, n) * adsr(n, 0.004, 0.02, 0.9, 0.02, 0.9)
            song.add(beat + k * 0.5, 0.5, w, gain)
        beat += 4


def drum_kick():
    n = seconds(0.16)
    t = t_axis(n)
    f = 140 * np.exp(-t * 25) + 45
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * decay(n, 0.05)
    click = chip_noise(n, 3) * decay(n, 0.012) * 0.5
    return body * 1.2 + click


def drum_snare():
    n = seconds(0.14)
    return chip_noise(n, 2) * decay(n, 0.035) + np.sin(2 * np.pi * 190 * t_axis(n)) * decay(n, 0.02) * 0.5


def drum_hat(open_=False):
    n = seconds(0.12 if open_ else 0.03)
    return highpass(chip_noise(n, 1), 6000) * decay(n, 0.05 if open_ else 0.01)


def add_drums(song, bars, start_bar, gain=0.35, busy=False):
    beat = start_bar * 4
    kick, snare, hat, ohat = drum_kick(), drum_snare(), drum_hat(), drum_hat(True)
    for bar in range(bars):
        b0 = beat + bar * 4
        song.add(b0 + 0, 0, kick, gain)
        song.add(b0 + 1, 0, snare, gain * 0.8)
        song.add(b0 + 2, 0, kick, gain)
        song.add(b0 + 3, 0, snare, gain * 0.8)
        if busy:
            song.add(b0 + 2.5, 0, kick, gain * 0.7)
        for e in range(8):
            song.add(b0 + e * 0.5, 0, ohat if (e == 7 and bar % 2 == 1) else hat, gain * 0.35)
    return song


def soft_clip(x, drive=1.4):
    return np.tanh(x * drive) / np.tanh(drive)


# ================================================================== MUSIC

def music_menu():
    """'Centre Ice' - bright major-key theme for the menus (160 BPM, 16 bars)."""
    chords = ["C", "G", "Am", "F", "C", "G", "F", "G",
              "Am", "F", "C", "G", "Am", "F", "G", "G"]
    lead = [
        "E5:2 G5:2 C6:4 B5:2 G5:2 E5:4",
        "D5:2 G5:2 B5:4 A5:2 G5:2 D5:4",
        "C5:2 E5:2 A5:4 G5:2 E5:2 C5:4",
        "A5:4 G5:2 F5:2 E5:4 D5:2 C5:2",
        "E5:2 G5:2 C6:4 E6:4 D6:2 C6:2",
        "B5:2 D6:2 G5:4 B5:4 D6:4",
        "C6:4 A5:2 C6:2 F5:4 A5:4",
        "G5:4 F5:2 E5:2 D5:4 B4:2 D5:2",
        "A4:2 C5:2 E5:4 A5:4 G5:2 E5:2",
        "F5:2 A5:2 C6:4 A5:4 F5:4",
        "E5:2 G5:2 C6:4 G5:4 E5:4",
        "D5:2 F#5:2 A5:4 G5:4 D5:4",
        "A5:2 G5:2 E5:4 C5:4 E5:4",
        "F5:2 A5:2 C6:4 D6:4 C6:4",
        "B5:2 D6:2 G6:4 F6:2 D6:2 B5:4",
        "D6:2 B5:2 G5:2 B5:2 D6:8",
    ]
    s = Song(160, 16)
    add_melody(s, lead, 0, duty=0.5, gain=0.24)
    add_melody(s, lead, 0, duty=0.125, gain=0.07, octave_shift=1)  # thin octave shimmer
    add_arpeggio(s, chords, 0, octave=4, duty=0.25, gain=0.12)
    add_bass(s, chords, 0, gain=0.42)
    add_drums(s, 16, 0, gain=0.32)
    return soft_clip(s.render())


def music_game():
    """'Power Play' - driving minor-key loop for play (176 BPM, 16 bars)."""
    chords = ["Am", "Am", "F", "G", "Am", "Am", "F", "E",
              "Am", "C", "F", "G", "Am", "C", "F", "E"]
    lead = [
        "A4:2 A4:2 R:2 A4:2 C5:2 E5:2 A5:4",
        "G5:2 E5:2 C5:4 A4:2 C5:2 E5:4",
        "F5:2 F5:2 R:2 F5:2 A5:2 C6:2 A5:4",
        "G5:4 B5:2 D6:2 B5:4 G5:4",
        "A5:2 A5:2 R:2 E5:2 A5:2 C6:2 E6:4",
        "D6:2 C6:2 A5:4 E5:2 C5:2 A4:4",
        "F5:2 A5:2 C6:4 A5:2 F5:2 A5:4",
        "G#5:2 B5:2 E6:4 B5:2 G#5:2 E5:4",
        "A5:4 E5:2 A5:2 C6:4 B5:2 A5:2",
        "G5:4 E5:2 G5:2 C6:4 B5:2 G5:2",
        "F5:2 A5:2 C6:2 F6:2 E6:4 C6:4",
        "D6:2 B5:2 G5:4 B5:2 D6:2 G6:4",
        "E6:4 C6:2 A5:2 E5:4 C5:2 E5:2",
        "G5:2 C6:2 E6:4 D6:2 C6:2 G5:4",
        "A5:2 C6:2 F6:4 E6:2 D6:2 C6:4",
        "B5:2 G#5:2 E5:4 G#5:2 B5:2 E6:4",
    ]
    s = Song(176, 16)
    add_melody(s, lead, 0, duty=0.25, gain=0.24)
    add_arpeggio(s, chords, 0, octave=4, duty=0.5, gain=0.11, pattern=(None, 1, 2, 3))
    add_bass(s, chords, 0, gain=0.45, driving=True)
    add_drums(s, 16, 0, gain=0.34, busy=True)
    return soft_clip(s.render())


def jingle_goal():
    s = Song(150, 2)
    add_melody(s, ["C5:2 E5:2 G5:2 C6:2 E6:6 R:2", "D6:2 E6:2 G6:12"], 0, duty=0.5, gain=0.3)
    add_melody(s, ["E4:2 G4:2 C5:2 E5:2 G5:6 R:2", "B4:2 C5:2 E5:12"], 0, duty=0.25, gain=0.14)
    add_bass(s, ["C", "C"], 0, gain=0.4)
    n = seconds(0.5)
    roll = chip_noise(n, 2) * np.abs(np.sin(2 * np.pi * 24 * t_axis(n))) * adsr(n, 0.05, 0.1, 0.8, 0.2, 0.8)
    s.add(0, 0, roll, 0.25, seamless=False)
    s.add(2, 0, drum_snare(), 0.4, seamless=False)
    s.add(4, 0, drum_kick(), 0.5, seamless=False)
    s.add(4, 0, drum_hat(True), 0.4, seamless=False)
    x = s.render()
    tail = np.zeros(seconds(0.4))
    return soft_clip(np.concatenate([x, tail]))


def jingle_period():
    s = Song(140, 2)
    add_melody(s, ["G5:4 E5:4 C5:8", "R:16"], 0, duty=0.5, gain=0.3)
    add_melody(s, ["E5:4 C5:4 G4:8", "R:16"], 0, duty=0.25, gain=0.15)
    add_bass(s, ["C", "C"], 0, gain=0.35)
    n = seconds(0.9)
    roll = chip_noise(n, 2) * np.abs(np.sin(2 * np.pi * 20 * t_axis(n))) * adsr(n, 0.02, 0.1, 0.9, 0.3, 0.9)
    s.add(0, 0, roll, 0.2, seamless=False)
    s.add(4, 0, drum_kick(), 0.5, seamless=False)
    return soft_clip(s.render()[: seconds(2.2)])


def jingle_win():
    s = Song(150, 3)
    add_melody(s, ["C5:2 C5:2 C5:2 E5:6 G5:4", "C6:6 B5:2 C6:8", "E6:16"], 0, duty=0.5, gain=0.3)
    add_melody(s, ["E4:2 E4:2 E4:2 G4:6 C5:4", "E5:6 D5:2 E5:8", "G5:16"], 0, duty=0.25, gain=0.16)
    add_arpeggio(s, ["C", "F", "C"], 0, octave=4, duty=0.125, gain=0.08)
    add_bass(s, ["C", "F", "C"], 0, gain=0.4)
    add_drums(s, 3, 0, gain=0.3)
    x = s.render()
    x[-seconds(0.6):] *= np.linspace(1, 0, seconds(0.6))
    return soft_clip(x)


def jingle_lose():
    s = Song(110, 2)
    add_melody(s, ["A4:6 G4:6 F4:4", "E4:16"], 0, duty=0.5, gain=0.3)
    add_melody(s, ["C4:6 Bb3:6 A3:4", "G#3:16"], 0, duty=0.25, gain=0.15)
    add_bass(s, ["Am", "E"], 0, gain=0.35)
    x = s.render()
    x[-seconds(0.8):] *= np.linspace(1, 0, seconds(0.8))
    return soft_clip(x)


def organ_rally():
    """Arena organ call (original riff) followed by two crowd claps."""
    bpm = 150
    spb = 60 / bpm
    seq = [("C5", 0.5), ("E5", 0.5), ("G5", 0.5), ("C6", 0.5), ("E6", 1.0), ("D6", 0.5), ("C6", 0.5), ("G5", 1.5)]
    total = sum(d for _, d in seq) * spb + 1.3
    out = np.zeros(seconds(total))
    beat = 0.0
    for name, d in seq:
        n = seconds(d * spb * 0.95)
        w = organ(note_freq(name), n) * adsr(n, 0.01, 0.03, 0.9, 0.06, 0.9)
        w += organ(note_freq(name) / 2, n) * adsr(n, 0.01, 0.03, 0.9, 0.06, 0.9) * 0.5
        i = seconds(beat * spb)
        out[i:i + n] += w
        beat += d
    # two claps ("clap, clap")
    clap_at = seconds(beat * spb + 0.05)
    for k in range(2):
        n = seconds(0.12)
        c = bandpass(white(n), 900, 4000) * decay(n, 0.03)
        i = clap_at + k * seconds(0.28)
        out[i:i + n] += c * 2.2
    return soft_clip(echo(out, 0.16, 0.28, 2), 1.2)


# ============================================================ SOUND EFFECTS

def sfx_shot():
    n = seconds(0.22)
    t = t_axis(n)
    crack = bandpass(white(n), 1500, 6000) * decay(n, 0.012)
    body = np.sin(2 * np.pi * (170 * np.exp(-t * 30) + 60) * t) * decay(n, 0.05)
    slap = bandpass(white(n), 300, 1200) * decay(n, 0.03)
    return crack * 1.2 + body * 0.9 + slap * 0.6


def sfx_pass():
    n = seconds(0.14)
    t = t_axis(n)
    tick = bandpass(white(n), 2000, 7000) * decay(n, 0.006)
    body = np.sin(2 * np.pi * 260 * t) * decay(n, 0.025)
    return tick + body * 0.6


def sfx_boards():
    n = seconds(0.5)
    t = t_axis(n)
    thump = np.sin(2 * np.pi * (95 * np.exp(-t * 12) + 55) * t) * decay(n, 0.12)
    ring = (np.sin(2 * np.pi * 310 * t) + 0.6 * np.sin(2 * np.pi * 520 * t)) * decay(n, 0.09)
    rattle = bandpass(white(n), 700, 3500) * decay(n, 0.04)
    return thump * 1.1 + ring * 0.35 + rattle * 0.5


def sfx_post():
    n = seconds(0.7)
    t = t_axis(n)
    ping = (np.sin(2 * np.pi * 1880 * t) * decay(n, 0.22) +
            0.7 * np.sin(2 * np.pi * 2830 * t) * decay(n, 0.15) +
            0.4 * np.sin(2 * np.pi * 4120 * t) * decay(n, 0.09))
    click = white(n) * decay(n, 0.004)
    return ping + click * 0.8


def sfx_hit():
    n = seconds(0.55)
    t = t_axis(n)
    thud = np.sin(2 * np.pi * (110 * np.exp(-t * 18) + 40) * t) * decay(n, 0.08)
    crunch = bandpass(white(n), 200, 2500) * decay(n, 0.045)
    glass = highpass(white(n), 3000) * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * t)) * decay(n, 0.14) * 0.5
    return thud * 1.3 + crunch * 0.8 + glass


def sfx_save():
    n = seconds(0.3)
    t = t_axis(n)
    pad = lowpass(white(n), 900) * decay(n, 0.05)
    body = np.sin(2 * np.pi * 140 * t) * decay(n, 0.06)
    return pad * 1.2 + body * 0.7


def sfx_whistle():
    n = seconds(0.75)
    t = t_axis(n)
    trill = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * (34 + 3 * np.sin(2 * np.pi * 2 * t)) * t))
    tone = (np.sin(2 * np.pi * 2280 * t) + 0.7 * np.sin(2 * np.pi * 2870 * t) + 0.3 * np.sin(2 * np.pi * 4560 * t))
    breath = bandpass(white(n), 1800, 5000) * 0.25
    env = adsr(n, 0.015, 0.05, 0.6, 0.12, 0.95)
    return (tone * trill + breath) * env


def sfx_horn():
    n = seconds(2.6)
    t = t_axis(n)
    f = 233.0 * (1 + 0.02 * np.exp(-t * 6))  # slight pitch settle like a real air horn
    x = np.zeros(n)
    for h, g in ((1, 1.0), (2, 0.8), (3, 0.6), (4, 0.45), (5, 0.3), (6, 0.2), (7, 0.12)):
        x += g * np.sin(2 * np.pi * f * h * t + 0.3 * h)
    x += 0.5 * np.sign(np.sin(2 * np.pi * f * 1.5 * t))
    x = lowpass(x, 2600)
    env = adsr(n, 0.06, 0.2, 1.8, 0.5, 0.9)
    return echo(x * env, 0.21, 0.3, 2)


def sfx_crowd_loop():
    n = seconds(6.0)
    fade = seconds(0.5)
    total = n + fade
    t = t_axis(total)
    base = bandpass(white(total), 150, 1800)
    base = base / np.max(np.abs(base))
    swell = 0.75 + 0.25 * np.sin(2 * np.pi * t / 6.0) * np.sin(2 * np.pi * t / 2.3)
    x = base * swell
    # scattered shouts
    for _ in range(45):
        i = rng.integers(0, total - seconds(0.35))
        m = seconds(rng.uniform(0.08, 0.3))
        f = rng.uniform(250, 700)
        shout = np.sin(2 * np.pi * f * t_axis(m) * (1 + 0.1 * np.sin(2 * np.pi * 7 * t_axis(m)))) * adsr(m, 0.02, 0.05, 0.6, 0.05, 0.6)
        x[i:i + m] += shout * rng.uniform(0.08, 0.2)
    # crossfade the overhang into the start so the loop point is seamless
    ramp = np.linspace(0, 1, fade)
    out = x[:n].copy()
    out[:fade] = out[:fade] * ramp + x[n:n + fade] * (1 - ramp)
    return out


def sfx_cheer():
    n = seconds(3.2)
    t = t_axis(n)
    roar = bandpass(white(n), 200, 2500)
    roar = roar / np.max(np.abs(roar)) * adsr(n, 0.35, 0.5, 1.4, 0.9, 0.75)
    for _ in range(60):
        i = rng.integers(0, n - seconds(0.3))
        m = seconds(rng.uniform(0.1, 0.35))
        f = rng.uniform(300, 900)
        shout = np.sin(2 * np.pi * f * t_axis(m)) * adsr(m, 0.02, 0.05, 0.6, 0.05, 0.6)
        roar[i:i + m] += shout * rng.uniform(0.05, 0.14)
    return roar


def sfx_skate(seed):
    r = np.random.default_rng(seed)
    n = seconds(0.16)
    t = t_axis(n)
    x = bandpass(r.uniform(-1, 1, n), 1800, 6500) * adsr(n, 0.02, 0.04, 0.5, 0.08, 0.6)
    x *= 1 + 0.4 * np.sin(2 * np.pi * (60 + 20 * seed) * t)
    return x


def sfx_faceoff():
    n = seconds(0.18)
    t = t_axis(n)
    drop = np.sin(2 * np.pi * (220 * np.exp(-t * 40) + 90) * t) * decay(n, 0.03)
    tick = highpass(white(n), 3000) * decay(n, 0.005)
    return drop + tick * 0.6


def sfx_pickup():
    n = seconds(0.06)
    return highpass(white(n), 3500) * decay(n, 0.006) + np.sin(2 * np.pi * 500 * t_axis(n)) * decay(n, 0.01) * 0.4


def sfx_click():
    n = seconds(0.09)
    t = t_axis(n)
    f = 880 * np.exp(-t * 6)
    return np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * decay(n, 0.03)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    save("music_menu.wav", music_menu(), 0.8)
    save("music_game.wav", music_game(), 0.8)
    save("jingle_goal.wav", jingle_goal(), 0.85)
    save("jingle_period.wav", jingle_period(), 0.8)
    save("jingle_win.wav", jingle_win(), 0.85)
    save("jingle_lose.wav", jingle_lose(), 0.8)
    save("organ_rally.wav", organ_rally(), 0.8)
    save("puck_hit.wav", sfx_shot(), 0.95)
    save("pass.wav", sfx_pass(), 0.8)
    save("wall_bounce.wav", sfx_boards(), 0.9)
    save("post.wav", sfx_post(), 0.9)
    save("body_hit.wav", sfx_hit(), 0.95)
    save("save.wav", sfx_save(), 0.85)
    save("whistle.wav", sfx_whistle(), 0.85)
    save("horn.wav", sfx_horn(), 0.95)
    save("goal.wav", sfx_horn(), 0.95)  # legacy name, same horn
    save("crowd_loop.wav", sfx_crowd_loop(), 0.8)
    save("cheer.wav", sfx_cheer(), 0.9)
    save("skate1.wav", sfx_skate(1), 0.7)
    save("skate2.wav", sfx_skate(2), 0.7)
    save("faceoff.wav", sfx_faceoff(), 0.8)
    save("pickup.wav", sfx_pickup(), 0.6)
    save("button_click.wav", sfx_click(), 0.7)
