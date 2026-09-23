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
from scipy.signal import butter, fftconvolve, lfilter

SR = 22050
OUT = os.environ.get("SOUND_OUT") or os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res", "raw")
rng = np.random.default_rng(1994)


# ----------------------------------------------------------------- helpers

def loudness_db(x):
    """Loudest 100 ms RMS after a 250 Hz high-pass, in dBFS. Crude, but it
    tracks how loud a short game sound feels far better than its peak does,
    and the high-pass stands in for a tablet speaker, which plays almost
    nothing below 250 Hz (so bass can't make a sound 'loud' on paper only)."""
    b, a = butter(2, 250 / (SR / 2), btype="high")
    y = lfilter(b, a, x)
    win = int(0.1 * SR)
    c = np.concatenate([[0.0], np.cumsum(y * y)])
    if len(y) <= win:
        ms = c[-1] / max(len(y), 1)
    else:
        i = np.arange(0, len(y) - win + 1, win // 4)
        ms = ((c[i + win] - c[i]) / win).max()
    return 10 * np.log10(ms + 1e-20)


def knee_limit(x, knee=0.7, ceiling=0.98):
    """Leaves everything below `knee` alone and bends anything above it
    smoothly toward `ceiling`, so the odd sharp transient can't clip."""
    a = np.abs(x)
    over = a > knee
    y = x.copy()
    w = ceiling - knee
    y[over] = np.sign(x[over]) * (knee + w * np.tanh((a[over] - knee) / w))
    return y


def save(name, data, peak=0.9, level=None):
    """Writes 16-bit mono WAV at the current rate.

    peak:  normalise the peak to this (music and jingles).
    level: normalise loudness_db() to this instead. SoundManager's mix levels
           were tuned by ear against the previous generation of sounds, so
           redesigned sounds are pinned to roughly the loudness of the ones
           they replace. Up to 6 dB of transient overshoot is rounded off by a
           soft-knee limiter; beyond that the gain is capped instead."""
    data = np.asarray(data, dtype=np.float64)
    m = np.max(np.abs(data)) or 1.0
    note = ""
    if level is None:
        data = data / m * peak
    else:
        g = 10 ** ((level - loudness_db(data)) / 20)
        for _ in range(4):              # limiting nudges the loudness; settle it
            g = min(g, 1.96 / m)
            out = knee_limit(data * g)
            g *= 10 ** ((level - loudness_db(out)) / 20)
        data = out
        if abs(loudness_db(data) - level) > 0.3:
            note = f"  (limited; target {level:.1f})"
    pcm = (np.clip(data, -1, 1) * 32767).astype("<i2")
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(f"{name:18s} {SR // 1000:2d} kHz {len(data) / SR:5.2f} s {os.path.getsize(path) // 1024:4d} KB"
          f"  {loudness_db(data):6.1f} dB{note}")


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


def set_rate(sr):
    """Switch the sample rate every helper renders at. Music and crowd beds
    stay at 22.05 kHz (nothing in them reaches 11 kHz); impacts, skates and
    glass render at 44.1 kHz so their crack and sizzle keep the top octave."""
    global SR
    SR = sr


def _nyq(f):
    """Keeps filter corners legal at whichever rate is active."""
    return min(f, 0.45 * SR)


def bandpass(x, lo, hi, order=2):
    hi = _nyq(hi)
    lo = min(lo, hi * 0.8)
    b, a = butter(order, [lo / (SR / 2), hi / (SR / 2)], btype="band")
    return lfilter(b, a, x)


def highpass(x, f, order=2):
    b, a = butter(order, _nyq(f) / (SR / 2), btype="high")
    return lfilter(b, a, x)


def lowpass(x, f, order=2):
    b, a = butter(order, _nyq(f) / (SR / 2), btype="low")
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

def _poly_blep(t, dt):
    """PolyBLEP correction for a unit step at phase 0, t in [0, 1).
    dt (phase increment per sample) may be a scalar or a per-sample array."""
    dt = np.broadcast_to(dt, t.shape)
    y = np.zeros_like(t)
    a = t < dt
    x = t[a] / dt[a]
    y[a] = x + x - x * x - 1.0
    b = t > 1.0 - dt
    x = (t[b] - 1.0) / dt[b]
    y[b] = x * x + x + x + 1.0
    return y


def pulse(freq, n, duty=0.5):
    """Band-limited pulse wave. A naive square at 22 kHz aliases: every
    harmonic past Nyquist folds back as an off-key whine, worst on the high
    lead notes. A PolyBLEP correction on each edge removes almost all of it
    while keeping the hard chiptune edge. The DC of a narrow pulse is removed
    too (the NES output is AC-coupled), so notes don't thump as they start."""
    dt = freq / SR
    ph = (np.arange(n) * dt) % 1.0
    y = np.where(ph < duty, 1.0, -1.0)
    y += _poly_blep(ph, dt)
    y -= _poly_blep((ph - duty) % 1.0, dt)
    return y - (2 * duty - 1)


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


# -------------------------------------------------------------- acoustics

def trim(x, floor_db=-66.0, fade_s=0.03):
    """Drops the silent end of a decaying sound, with a short fade."""
    thr = np.max(np.abs(x)) * 10 ** (floor_db / 20)
    idx = np.nonzero(np.abs(x) > thr)[0]
    y = x[:(idx[-1] + 1) if len(idx) else len(x)].copy()
    f = min(seconds(fade_s), len(y))
    y[len(y) - f:] *= np.linspace(1, 0, f)
    return y


def wrap(x, n):
    """Folds everything past sample n back onto the start, so events placed
    anywhere in [0, n) make a loop with no seam at all."""
    out = x[:n].copy()
    for i in range(n, len(x), n):
        k = min(n, len(x) - i)
        out[:k] += x[i:i + k]
    return out


def loop_filter(fn, x):
    """Runs a filter over a loop as if it had been playing forever, so the
    filter's start-up transient doesn't leave a click at the loop point."""
    return fn(np.concatenate([x, x]))[len(x):]


def periodic_noise(n, lo, hi, tilt=0.0, r=None):
    """Noise that repeats exactly every n samples: a random-phase spectrum
    between lo and hi (soft 24 dB/oct edges), sloped by `tilt` dB/octave."""
    r = r or rng
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = 1.0
    mag = f ** (tilt / 6.02) / np.sqrt(1 + (lo / f) ** 8) / np.sqrt(1 + (f / hi) ** 8)
    mag[0] = 0.0
    x = np.fft.irfft(mag * np.exp(1j * r.uniform(0, 2 * np.pi, len(f))), n)
    return x / (np.max(np.abs(x)) + 1e-12)


def room_ir(rt60=1.4, bright=False, predelay=0.018, seed=7):
    """Synthetic arena impulse response (unit energy): a spray of early
    reflections off the boards and glass, then a diffuse tail in four bands
    whose highs die away faster than its lows."""
    r = np.random.default_rng(seed)
    n = seconds(rt60 * 1.15)
    t = t_axis(n)
    tone = (0.6, 0.8, 0.7, 0.45) if bright else (1.0, 0.65, 0.3, 0.1)
    bands = ((60, 350, 1.0), (350, 1400, 0.85), (1400, 4500, 0.6), (4500, 11000, 0.38))
    tail = np.zeros(n)
    for (lo, hi, k), g in zip(bands, tone):
        tail += bandpass(r.standard_normal(n), lo, hi) * 10 ** (-3 * t / (rt60 * k)) * g
    tail *= 1 - np.exp(-t / 0.025)          # the diffuse field takes a moment to build
    early = np.zeros(n)
    for _ in range(16):
        d = r.uniform(0.003, 0.085)
        early[seconds(d)] += r.choice([-1.0, 1.0]) * r.uniform(0.4, 1.0) * (1 - d / 0.1)
    early = lowpass(early, 5500 if bright else 3000)
    ir = tail / np.sqrt(np.sum(tail ** 2)) + 0.55 * early / np.sqrt(np.sum(early ** 2))
    ir = np.concatenate([np.zeros(seconds(predelay)), ir])
    return ir / np.sqrt(np.sum(ir ** 2))


def add_room(x, wet=0.3, rt60=1.4, bright=False, seed=7):
    """A one-shot sound plus its arena reverb."""
    ir = room_ir(rt60, bright, seed=seed)
    return trim(np.concatenate([x, np.zeros(len(ir) - 1)]) + wet * fftconvolve(x, ir))


def loop_room(x, wet=0.8, rt60=1.8, bright=False, seed=7):
    """Reverb for a loop: circular convolution, so the tail wraps round."""
    ir = room_ir(rt60, bright, seed=seed)
    n = len(x)
    return x + wet * np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(ir, n), n)


# ---------------------------------------------------------- crowd voices

# First three formants (Hz) of adult vowels.
VOWELS = {
    "ah": (730, 1090, 2440), "eh": (530, 1840, 2480), "oh": (570, 840, 2410),
    "oo": (300, 870, 2240), "ee": (270, 2290, 3010), "uh": (640, 1190, 2390),
}
VOWEL_NAMES = list(VOWELS)

# (speaking f0 range, formant scale): adult men, adult women, kids. It's a
# Timbits crowd, so plenty of parents and a lot of kids.
VOICE_TYPES = (((95, 140), 1.0), ((170, 235), 1.15), ((235, 310), 1.28))


def _saw(freq):
    """Band-limited sawtooth that follows a per-sample frequency array."""
    dt = freq / SR
    ph = np.cumsum(dt) % 1.0
    return 2 * ph - 1 - _poly_blep(ph, dt)


def _formant(x, f, bw):
    """Two-pole resonator with unity gain at its centre frequency."""
    w = 2 * np.pi * min(f, 0.45 * SR) / SR
    r = np.exp(-np.pi * bw / SR)
    g = (1 - r) * np.sqrt(1 - 2 * r * np.cos(2 * w) + r * r)
    return lfilter([g], [1, -2 * r * np.cos(w), r * r], x)


def voice(dur, f0, vowel, size=1.0, shout=0.0, glide=0.0, arch=0.0,
          attack=0.02, release=0.06, r=None):
    """One formant-synthesised voice holding a vowel (a Klatt-style parallel
    formant bank driven by a band-limited glottal sawtooth plus breath).

    size scales the vocal tract (women and kids > 1). shout raises the pitch,
    opens the mouth and adds breath and roughness. glide bends the pitch by
    that many semitones across the note; arch adds a rise-and-fall hump."""
    r = r or rng
    n = max(seconds(dur), 32)
    u = np.linspace(0, 1, n, endpoint=False)
    ts = t_axis(n)
    semis = glide * u + arch * np.sin(np.pi * u)
    semis = semis + 0.25 * np.sin(2 * np.pi * r.uniform(4.5, 6.5) * ts + r.uniform(0, 6.3))
    wander = lowpass(r.standard_normal(n), 9, 1)
    semis = semis + wander / (np.std(wander) + 1e-9) * (0.15 + 0.35 * shout)
    src = _saw(f0 * (1 + 0.35 * shout) * 2 ** (semis / 12))
    if shout < 0.5:
        src = lowpass(src, 1200 + 3000 * shout, 1)   # relaxed voices are darker
    exc = src + highpass(r.uniform(-1, 1, n), 900) * (0.08 + 0.25 * shout)
    f1, f2, f3 = VOWELS[vowel]
    y = (_formant(exc, f1 * size * (1 + 0.18 * shout), 90 + 60 * shout)
         - 0.7 * _formant(exc, f2 * size, 110 + 60 * shout)
         + 0.4 * _formant(exc, f3 * size, 170 + 80 * shout))
    y /= np.max(np.abs(y)) + 1e-9
    return y * adsr(n, attack, 0.05, 0, release, 0.85)


def consonant(r):
    """A short fricative/plosive burst to start a spoken syllable."""
    n = seconds(r.uniform(0.02, 0.05))
    lo = r.choice([1800, 3000, 4500])
    return bandpass(r.uniform(-1, 1, n), lo, lo * 2.2) * adsr(n, 0.005, 0.01, 0, 0.01, 0.6)


def talker(buf, r, span, level, vtype):
    """One fan chatting for `span` seconds of loop time: phrases of 3-10
    syllables with falling intonation, separated by pauses."""
    (lo, hi), size = vtype
    base = r.uniform(lo, hi)
    pos = r.uniform(0, span)
    end = pos + span
    while pos < end:
        syl = int(r.integers(3, 11))
        for k in range(syl):
            d = r.uniform(0.09, 0.22)
            f0 = base * 2 ** ((r.normal(0, 1.3) + 1.5 - 3.0 * k / syl) / 12)
            v = voice(d, f0, r.choice(VOWEL_NAMES), size, glide=r.normal(0, 1),
                      attack=0.015, release=0.04, r=r)
            i = seconds(pos)
            if r.random() < 0.55:
                c = consonant(r)
                put(buf, i, c, 0.2 * level)
                i += len(c) // 2
            put(buf, i, v, level)
            pos += d + r.uniform(0.0, 0.035)
        pos += r.uniform(0.5, 2.8)


def finger_whistle(r):
    d = r.uniform(0.35, 0.9)
    n = seconds(d)
    u = np.linspace(0, 1, n)
    f = r.uniform(2100, 2900) * 2 ** ((2.5 * np.sin(np.pi * u) - 1.5 * u) / 12)
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR)
    return (tone + bandpass(r.uniform(-1, 1, n), 1800, 4000) * 0.15) * adsr(n, 0.04, 0.05, 0, 0.12, 0.9)


def clap(r):
    """A hand clap: two or three micro-bursts within a few ms."""
    n = seconds(0.06)
    x = np.zeros(n)
    for k in range(int(r.integers(2, 4))):
        i = seconds(k * r.uniform(0.002, 0.005))
        x[i:] += r.uniform(-1, 1, n - i) * np.exp(-t_axis(n - i) / 0.004)
    return bandpass(x, r.uniform(700, 1100), r.uniform(2500, 4500))


def put(buf, i, w, g=1.0):
    """Mixes w into buf at sample i, dropping whatever runs off the end."""
    k = min(len(w), len(buf) - i)
    if k > 0:
        buf[i:i + k] += w[:k] * g


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

# Impacts use modal synthesis: a struck object rings at a handful of
# resonant frequencies, each decaying at its own rate. Every sound has a few
# variants (different stick, different panel of boards) that SoundManager
# rotates through so repeated hits don't sound machine-gunned.

def hit_env(n, attack, tau):
    t = t_axis(n)
    return (1 - np.exp(-t / max(attack, 1e-5))) * np.exp(-t / tau)


def burst(n, tau, lo, hi, r):
    """A contact transient: a very short noise impulse, band-limited."""
    return bandpass(r.uniform(-1, 1, n) * decay(n, tau), lo, hi)


def modes(n, freqs, taus, gains, beat=0.0, r=None):
    """Sum of decaying sine modes. beat splits each mode into two slightly
    detuned copies, the slow shimmer of a not-quite-symmetric object."""
    t = t_axis(n)
    out = np.zeros(n)
    for f, tau, g in zip(freqs, taus, gains):
        if f >= 0.45 * SR:
            continue
        env = np.exp(-t / tau)
        if beat:
            d = beat * (r.uniform(0.5, 1.5) if r is not None else 1.0) / 2
            out += g * 0.5 * (np.sin(2 * np.pi * (f - d) * t) + np.sin(2 * np.pi * (f + d) * t)) * env
        else:
            out += g * np.sin(2 * np.pi * f * t) * env
    return out


def thump(n, f_hi, f_lo, k, tau):
    """A low body hit whose pitch drops from f_hi to f_lo as it decays."""
    t = t_axis(n)
    f = f_lo + (f_hi - f_lo) * np.exp(-t * k)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / tau)


def pops(n, r, rate, tau, lo, hi):
    """Sparse random clicks dying away: ice chips, cracking plastic."""
    x = (r.uniform(0, 1, n) < rate / SR) * r.uniform(-1, 1, n) * decay(n, tau)
    return bandpass(x, lo, hi)


def rattle(n, r, freqs, tau, delay=0.0, rate=None):
    """Plexiglass shaking in its frame: bright panel modes chopped by the
    panel slapping against its clips a few dozen times a second."""
    t = t_axis(n)
    k = r.uniform(0.92, 1.08)
    taus = [tau * (1 - 0.12 * i) for i in range(len(freqs))]
    ring = modes(n, [f * k for f in freqs], taus, (1.0, 0.8, 0.6, 0.45, 0.3), beat=3.0, r=r)
    chop = np.abs(np.sin(np.pi * (rate or r.uniform(22, 34)) * t + r.uniform(0, 3))) ** 3
    buzz = highpass(r.uniform(-1, 1, n), 2500) * chop * np.exp(-t / (tau * 0.6)) * 0.25
    x = ring * (0.25 + 0.75 * chop) + buzz
    d = seconds(delay)
    return np.concatenate([np.zeros(d), x[:n - d]])


def sfx_shot(v, heavy=False):
    """Stick on puck: a hard snap, the ringing shaft, the rubber puck's tock,
    the stick flexing and the blade skimming the ice. heavy = one-timer, where
    the blade slams the ice as it meets the puck."""
    r = np.random.default_rng(300 + v)
    n = seconds(0.5 if heavy else 0.38)
    k = r.uniform(0.93, 1.07)
    kp = r.uniform(0.9, 1.1)
    p = 1.35 if heavy else 1.0
    x = (burst(n, 0.0009 * p, 900, 16000, r) * 1.3
         + modes(n, [1150 * k, 1720 * k, 2480 * k, 3650 * k, 5200 * k],
                 [0.032, 0.022, 0.014, 0.009, 0.006], [0.55, 0.42, 0.36, 0.24, 0.18])
         + modes(n, [640 * kp, 1580 * kp], [0.009, 0.005], [0.6, 0.25])
         + thump(n, 300 * k, 110, 30, 0.045 * p) * 0.45 * p
         + bandpass(r.uniform(-1, 1, n), 2500, 11000) * hit_env(n, 0.003, 0.05 * p) * 0.3 * p)
    if heavy:
        x += thump(n, 170, 70, 18, 0.08) * 0.5 + burst(n, 0.006, 250, 2000, r) * 0.7
    return trim(soft_clip(x, 1.3))


def sfx_pass(v):
    """A tape-to-tape pass: a lighter tap of the blade, then the puck hissing away."""
    r = np.random.default_rng(400 + v)
    n = seconds(0.28)
    k = r.uniform(0.92, 1.08)
    x = (burst(n, 0.0007, 1200, 12000, r) * 0.7
         + modes(n, [880 * k, 1390 * k, 2150 * k, 3300 * k], [0.02, 0.014, 0.009, 0.005], [0.5, 0.32, 0.22, 0.1])
         + modes(n, [610 * k, 1500 * k], [0.007, 0.004], [0.45, 0.18])
         + thump(n, 190, 115, 40, 0.025) * 0.35
         + bandpass(r.uniform(-1, 1, n), 3500, 12000) * hit_env(n, 0.01, 0.07) * 0.07)
    return trim(x)


def sfx_boards(v):
    """Puck off the dasher boards: a hollow panel boom, the plastic kick-plate
    knocking, and (on some panels) the glass above it rattling."""
    r = np.random.default_rng(500 + v)
    n = seconds(0.75)
    k = r.uniform(0.9, 1.1)
    x = (burst(n, 0.0008, 1000, 8000, r) * 0.5
         + modes(n, [95 * k, 150 * k, 225 * k, 310 * k, 430 * k],
                 [0.12, 0.085, 0.06, 0.045, 0.03], [0.6, 0.6, 0.55, 0.45, 0.3])
         + modes(n, [480 * k, 760 * k, 1130 * k, 1720 * k], [0.03, 0.02, 0.012, 0.008], [0.6, 0.45, 0.3, 0.18])
         + thump(n, 190 * k, 90 * k, 25, 0.05) * 0.4
         + rattle(n, r, [420, 610, 890, 1340, 2050], 0.18, delay=0.006) * (0.18, 0.35, 0.06)[v])
    return trim(soft_clip(x, 1.2))


def sfx_post():
    """Puck off the iron. A steel tube rings with the inharmonic partials of a
    free bar (1 : 2.76 : 5.40 : 8.93 : 13.3), each split into a slow beat
    because the post is not perfectly round; the net frame thunks under it."""
    r = np.random.default_rng(600)
    n = seconds(1.7)
    f0 = 880.0
    x = (burst(n, 0.0006, 1500, 16000, r) * 0.9
         + modes(n, [f0, f0 * 2.756, f0 * 5.404, f0 * 8.933, f0 * 13.34],
                 [0.95, 0.6, 0.32, 0.16, 0.08], [0.55, 1.0, 0.6, 0.3, 0.15], beat=1.6, r=r)
         + modes(n, [650, 1600], [0.008, 0.005], [0.5, 0.2])
         + modes(n, [330, 510], [0.09, 0.06], [0.35, 0.2]))
    return trim(x, -70)


def sfx_hit(v):
    """Body check into the boards: chest-on-chest thud, pads crunching and
    cracking, the boards booming and the glass shaking in its frame."""
    r = np.random.default_rng(700 + v)
    n = seconds(0.9)
    k = r.uniform(0.9, 1.1)
    x = (thump(n, 140 * k, 50, 16, 0.09) * 0.8
         + thump(n, 320 * k, 170 * k, 30, 0.04) * 0.5
         + bandpass(r.uniform(-1, 1, n), 300, 2800) * hit_env(n, 0.002, 0.035) * 1.0
         + pops(n, r, 260, 0.06, 1000, 5000) * 0.45
         + modes(n, [85 * k, 135 * k, 200 * k, 285 * k, 400 * k],
                 [0.17, 0.12, 0.085, 0.06, 0.04], [0.6, 0.6, 0.5, 0.4, 0.3])
         + rattle(n, r, [380, 560, 820, 1250, 1900], 0.3, delay=0.012,
                  rate=r.uniform(17, 26)) * (0.55, 0.75, 0.4)[v])
    return trim(soft_clip(x, 1.4))


def sfx_save(v):
    """v0: puck into the pads, a deep foam-and-leather thump.
    v1: glove save, a leather smack and the pocket snapping shut."""
    r = np.random.default_rng(800 + v)
    n = seconds(0.42)
    k = r.uniform(0.92, 1.08)
    if v == 0:
        x = (bandpass(r.uniform(-1, 1, n), 200, 2000) * hit_env(n, 0.001, 0.022)
             + modes(n, [190 * k, 300 * k, 450 * k], [0.05, 0.035, 0.022], [0.5, 0.45, 0.3])
             + modes(n, [700, 1650], [0.006, 0.004], [0.35, 0.12])
             + burst(n, 0.0006, 2000, 8000, r) * 0.2)
    else:
        x = (bandpass(r.uniform(-1, 1, n), 700, 4500) * hit_env(n, 0.0005, 0.007)
             + modes(n, [230 * k, 360 * k], [0.04, 0.025], [0.45, 0.3])
             + modes(n, [680, 1600], [0.006, 0.004], [0.3, 0.1]))
        put(x, seconds(0.05), burst(seconds(0.05), 0.003, 1500, 6000, r), 0.3)
    return trim(x)


def sfx_whistle():
    n = seconds(0.75)
    t = t_axis(n)
    trill = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * (34 + 3 * np.sin(2 * np.pi * 2 * t)) * t))
    tone = (np.sin(2 * np.pi * 2280 * t) + 0.7 * np.sin(2 * np.pi * 2870 * t) + 0.3 * np.sin(2 * np.pi * 4560 * t))
    breath = bandpass(white(n), 1800, 5000) * 0.25
    env = adsr(n, 0.015, 0.05, 0.6, 0.12, 0.95)
    return (tone * trill + breath) * env


def sfx_horn():
    """Goal horn: a bank of overdriven air horns sounding a Bb major chord,
    each settling onto pitch and wobbling slightly against the others, then
    filling the arena."""
    r = np.random.default_rng(1200)
    n = seconds(2.9)
    t = t_axis(n)
    x = np.zeros(n)
    for f, g in ((116.54, 0.5), (233.08, 1.0), (293.66, 0.75), (349.23, 0.7)):
        fr = f * r.uniform(0.997, 1.003) * (1 + 0.03 * np.exp(-t * 7))
        fr = fr * (1 + 0.0015 * np.sin(2 * np.pi * r.uniform(4, 6) * t))
        x += g * _saw(fr)
    # the bell and throat of a horn: resonances that make a saw sound brassy
    y = (_formant(x, 520, 300) + 0.6 * _formant(x, 1150, 400)
         + 0.25 * _formant(x, 2300, 600) + 0.3 * lowpass(x, 3000))
    y = soft_clip(y / np.max(np.abs(y)) * 1.5, 1.6)
    y *= adsr(n, 0.09, 0.15, 0, 0.55, 0.95)
    return add_room(y, 0.45, 2.4, bright=True, seed=21)


def crowd_murmur():
    """The arena between whistles: ~40 fans chatting, the odd call across
    the rink and air handling, all in the arena's reverb. Loops seamlessly."""
    r = np.random.default_rng(2024)
    L = 8.0
    n = seconds(L)
    near = np.zeros(seconds(2 * L + 4))
    far = np.zeros_like(near)
    for _ in range(42):
        vt = VOICE_TYPES[r.choice(3, p=[0.35, 0.4, 0.25])]
        lvl = r.uniform(0.2, 1.0) ** 2
        talker(near if lvl > 0.5 else far, r, L, lvl, vt)
    for _ in range(4):                       # "let's GO!"
        (lo, hi), size = VOICE_TYPES[r.choice(3)]
        f0 = r.uniform(lo, hi)
        i = seconds(r.uniform(0, L))
        for j, (vw, d) in enumerate((("eh", 0.16), ("oh", 0.42))):
            v = voice(d, f0 * (1.12 if j else 1.0), vw, size, shout=0.8, arch=1.5,
                      attack=0.03, release=0.15, r=r)
            put(near, i, v, 0.5)
            i += len(v) + seconds(0.03)
    x = wrap(near, n) + loop_filter(lambda s: lowpass(s, 2200), wrap(far, n))
    x /= np.max(np.abs(x))
    x += periodic_noise(n, 100, 1600, -3, r) * 0.07
    x *= 1 + 0.1 * np.sin(2 * np.pi * 2 * t_axis(n) / L)
    return loop_room(x, 0.9, 1.9, seed=11)


def crowd_roar():
    """Excited crowd: 130 overlapping shouts, whistles and applause. Played
    as a loop under the murmur and faded in as the play heats up."""
    r = np.random.default_rng(31)
    L = 8.0
    n = seconds(L)
    buf = np.zeros(seconds(L + 3))
    for _ in range(130):
        (lo, hi), size = VOICE_TYPES[r.choice(3, p=[0.35, 0.35, 0.3])]
        v = voice(r.uniform(0.5, 2.2), r.uniform(lo, hi), r.choice(["ah", "ah", "oh", "eh", "uh", "ee"]),
                  size, shout=r.uniform(0.6, 1.0), glide=r.normal(0, 1.5), arch=r.uniform(0.5, 3),
                  attack=r.uniform(0.05, 0.2), release=r.uniform(0.2, 0.5), r=r)
        put(buf, seconds(r.uniform(0, L)), v, r.uniform(0.3, 1.0))
    for _ in range(5):
        put(buf, seconds(r.uniform(0, L)), finger_whistle(r), r.uniform(0.15, 0.3))
    x = wrap(buf, n)
    x /= np.max(np.abs(x))
    claps = np.zeros(n + seconds(0.1))
    for _ in range(400):
        put(claps, seconds(r.uniform(0, L)), clap(r), r.uniform(0.3, 1.0))
    claps = wrap(claps, n)
    x += claps / np.max(np.abs(claps)) * 0.2
    x += periodic_noise(n, 250, 3500, -2, r) * 0.18
    return loop_room(x, 0.7, 1.9, bright=True, seed=13)


def crowd_cheer():
    """Goal! Everyone is on their feet within a third of a second, yelling,
    whistling and then applauding, fading as the roar loop takes over."""
    r = np.random.default_rng(55)
    n = seconds(4.0)
    buf = np.zeros(n)
    for _ in range(170):
        (lo, hi), size = VOICE_TYPES[r.choice(3, p=[0.35, 0.35, 0.3])]
        v = voice(r.uniform(1.0, 2.8), r.uniform(lo, hi), r.choice(["ah", "eh", "ah", "oh", "ee"]), size,
                  shout=r.uniform(0.75, 1.0), glide=r.uniform(-2, 0.5), arch=r.uniform(1.5, 4),
                  attack=r.uniform(0.04, 0.12), release=r.uniform(0.4, 0.9), r=r)
        put(buf, seconds(r.gamma(2.0, 0.08)), v, r.uniform(0.3, 1.0))
    for _ in range(8):
        put(buf, seconds(r.uniform(0.3, 2.5)), finger_whistle(r), r.uniform(0.2, 0.35))
    buf /= np.max(np.abs(buf))
    claps = np.zeros(n)
    for _ in range(1100):
        put(claps, seconds(0.5 + r.uniform(0, 1) ** 0.7 * 3.3), clap(r), r.uniform(0.3, 1.0))
    t = t_axis(n)
    bed = bandpass(r.uniform(-1, 1, n), 250, 4000) * np.minimum(1, t / 0.12)
    x = buf + claps / np.max(np.abs(claps)) * 0.35 + bed * 0.12
    x *= np.clip((4.0 - t) / 1.4, 0, 1)
    return add_room(x, 0.5, 2.0, bright=True, seed=17)


def crowd_gasp():
    """A near miss: a sharp collective breath, then a rising-and-falling 'ooooh'."""
    r = np.random.default_rng(71)
    n = seconds(1.6)
    buf = np.zeros(n)
    put(buf, 0, highpass(r.uniform(-1, 1, seconds(0.12)), 1500) * adsr(seconds(0.12), 0.02, 0.04, 0, 0.06, 0.6), 0.15)
    for _ in range(110):
        (lo, hi), size = VOICE_TYPES[r.choice(3, p=[0.35, 0.35, 0.3])]
        v = voice(r.uniform(0.6, 1.1), r.uniform(lo, hi), r.choice(["oo", "oh", "oh", "uh"]), size,
                  shout=r.uniform(0.3, 0.6), glide=r.uniform(-6, -3), arch=r.uniform(1, 2.5),
                  attack=0.07, release=0.35, r=r)
        put(buf, seconds(0.04 + r.gamma(2.0, 0.035)), v, r.uniform(0.3, 1.0))
    return add_room(buf, 0.5, 1.8, seed=19)


def wind_loop():
    """Outdoor pond: wind in gusts, from a low rumble up to a faint hiss
    through the trees. Every layer is periodic, so it loops perfectly."""
    r = np.random.default_rng(88)
    L = 8.0
    n = seconds(L)
    t = t_axis(n)
    gust = np.clip(0.55 + 0.25 * np.sin(2 * np.pi * t / L + 1) + 0.15 * np.sin(2 * np.pi * 3 * t / L + 2)
                   + 0.08 * np.sin(2 * np.pi * 7 * t / L), 0.1, None)
    return (periodic_noise(n, 30, 420, -4, r) * gust
            + 0.7 * periodic_noise(n, 300, 2200, -3, r) * gust ** 2
            + 0.12 * periodic_noise(n, 2500, 7000, 0, r) * gust ** 3)


def room_tail(bright):
    """Just the arena's reverb, played quietly after big indoor impacts
    (SoundManager skips it on the outdoor pond)."""
    return trim(room_ir(1.3, bright, seed=5 if bright else 6))


def sfx_skate(v):
    """One stride: the blade edge carving the ice. A band of noise sweeps as
    the push loads the edge (up on even variants, down on odd), with edge
    chatter and a grainy crunch of ice chips on top."""
    r = np.random.default_rng(900 + v)
    dur = r.uniform(0.2, 0.28)
    n = seconds(dur)
    t = t_axis(n)
    u = t / dur
    centre = (0.5 + 2.0 * u) if v % 2 == 0 else (2.8 - 1.8 * u)
    x = np.zeros(n)
    for i, (lo, hi) in enumerate(((1200, 2200), (2200, 3800), (3800, 6200), (6200, 10000), (10000, 15000))):
        x += bandpass(r.uniform(-1, 1, n), lo, hi) * np.exp(-0.5 * ((i - centre) / 0.9) ** 2)
    env = np.sin(np.pi * u ** 0.6) ** 1.2
    x *= 1 + 0.25 * np.sin(2 * np.pi * r.uniform(45, 70) * t)
    chips = pops(n, r, 1400, 10.0, 3000, 14000)
    x = x / np.max(np.abs(x)) + chips / np.max(np.abs(chips)) * 0.35
    return trim(x * env + burst(n, 0.0005, 3000, 14000, r) * 0.25)


def sfx_faceoff():
    """The puck smacks the ice, then the two centres' sticks clash for it."""
    r = np.random.default_rng(1000)
    n = seconds(0.36)
    x = (modes(n, [820, 1930], [0.012, 0.006], [0.6, 0.25])
         + thump(n, 180, 115, 45, 0.02) * 0.4
         + burst(n, 0.0005, 1500, 12000, r) * 0.4)
    for at, g in ((0.065, 0.8), (0.1, 0.55)):
        m = seconds(0.12)
        k = r.uniform(0.93, 1.07)
        c = (modes(m, [1300 * k, 2100 * k, 3150 * k, 4600 * k], [0.02, 0.013, 0.008, 0.005], [0.5, 0.35, 0.22, 0.12])
             + burst(m, 0.0006, 1500, 14000, r) * 0.6)
        put(x, seconds(at), c, g)
    return trim(x)


def sfx_pickup():
    """Puck settling onto the tape of a blade."""
    r = np.random.default_rng(1100)
    n = seconds(0.12)
    return trim(modes(n, [520, 1180, 2300], [0.012, 0.007, 0.004], [0.5, 0.25, 0.1])
                + burst(n, 0.0005, 1500, 9000, r) * 0.3)


def sfx_click():
    n = seconds(0.09)
    t = t_axis(n)
    f = 880 * np.exp(-t * 6)
    return np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * decay(n, 0.03)


def sfx_penalty():
    n = seconds(0.9)
    t = t_axis(n)
    trill1 = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * 38 * t))
    tone = (np.sin(2 * np.pi * 2650 * t) + 0.6 * np.sin(2 * np.pi * 3400 * t)) * trill1
    env1 = adsr(seconds(0.35), 0.01, 0.04, 0.6, 0.08, 0.9)
    whistle1 = np.zeros(n)
    whistle1[:len(env1)] = tone[:len(env1)] * env1

    start2 = seconds(0.4)
    rem = n - start2
    t2 = t_axis(rem)
    horn = (np.sin(2 * np.pi * 311.13 * t2) + 0.7 * np.sin(2 * np.pi * 466.16 * t2)) * adsr(rem, 0.02, 0.1, 0.4, 0.15, 0.8)
    whistle1[start2:] += horn * 0.7
    return whistle1


def sfx_fire():
    """Combustion whoosh with sub-bass surge and crackle for ON FIRE state."""
    n = seconds(1.3)
    t = t_axis(n)
    # Low frequency sweep: 60Hz -> 180Hz -> 45Hz
    freq = 65 + 130 * np.exp(-((t - 0.25) ** 2) / 0.06)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    bass = np.sin(phase) + 0.5 * np.sin(phase * 2)
    bass *= adsr(n, 0.08, 0.35, 0.4, 0.45, 0.85)

    # Filtered whoosh noise
    nz = white(n)
    whoosh = bandpass(nz, 280, 2400) * adsr(n, 0.05, 0.25, 0.4, 0.5, 0.75)

    # Crackle bursts
    crackle = (rng.uniform(0, 1, n) > 0.985).astype(np.float64) * white(n) * 1.5
    crackle = bandpass(crackle, 1200, 7000) * decay(n, 0.5)

    out = bass * 0.9 + whoosh * 0.85 + crackle * 0.4
    return echo(out, 0.08, 0.3, repeats=2)


def sfx_deke():
    """Quick lateral skate bite on ice + puck toe-drag snap."""
    n = seconds(0.35)
    t = t_axis(n)
    # Quick ice carve slice
    carve = bandpass(white(n), 1200, 6500) * adsr(n, 0.02, 0.08, 0.1, 0.12, 0.5)
    # Wood stick toe-drag click
    click_n = seconds(0.04)
    t_c = t_axis(click_n)
    click = np.sin(2 * np.pi * 1400 * t_c) * np.exp(-t_c / 0.008)
    carve[:len(click)] += click * 1.2
    return carve


def sfx_glass():
    """Shattering plexiglass: violent impact crunch + cascading crystal shards."""
    n = seconds(1.1)
    t = t_axis(n)
    # 1. Heavy low body/glass impact thud
    thud = np.sin(2 * np.pi * 95 * t) * np.exp(-t / 0.09) * 0.8
    thud += np.sin(2 * np.pi * 160 * t) * np.exp(-t / 0.07) * 0.5
    # 2. Explosive fracture transient (wideband noise crunch)
    crunch = bandpass(white(n), 1500, 8500) * adsr(n, 0.005, 0.12, 0.2, 0.35, 0.4) * 1.3
    # 3. Crystal harmonic ring
    ring = np.zeros(n)
    for freq in [2850, 4200, 6100, 8400]:
        ring += np.sin(2 * np.pi * freq * t) * np.exp(-t / 0.22) * 0.18
    # 4. Cascading tinkling glass shards falling onto ice
    shards = np.zeros(n)
    shard_times = [0.08, 0.14, 0.19, 0.27, 0.33, 0.42, 0.52, 0.65, 0.78]
    for st in shard_times:
        idx = seconds(st)
        dur = seconds(0.05)
        if idx + dur < n:
            sfreq = 3500 + rng.uniform(500, 5500)
            t_s = t_axis(dur)
            ping = np.sin(2 * np.pi * sfreq * t_s) * np.exp(-t_s / 0.012)
            shards[idx:idx + dur] += ping * rng.uniform(0.15, 0.35)
    out = thud + crunch + ring + shards
    return echo(out, 0.06, 0.25, repeats=2)


def sfx_pad_stack():
    """Two-pad stack sprawl: heavy leather slap on ice + sliding friction."""
    n = seconds(0.55)
    t = t_axis(n)
    # Heavy leather pad slap
    slap = np.sin(2 * np.pi * 140 * t) * np.exp(-t / 0.08) * 0.7
    slap_crunch = bandpass(white(n), 400, 2500) * adsr(n, 0.01, 0.06, 0.05, 0.1, 0.4)
    # Ice slide friction
    slide = bandpass(white(n), 800, 5000) * adsr(n, 0.04, 0.18, 0.15, 0.18, 0.6) * 0.8
    return slap + slap_crunch + slide


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)

    # Music, jingles, the horn, crowd beds and ambience: nothing in them needs
    # the octave above 11 kHz, so 22.05 kHz keeps the APK small.
    set_rate(22050)
    save("music_menu.wav", music_menu(), 0.8)
    save("music_game.wav", music_game(), 0.8)
    save("jingle_goal.wav", jingle_goal(), 0.85)
    save("jingle_period.wav", jingle_period(), 0.8)
    save("jingle_win.wav", jingle_win(), 0.85)
    save("jingle_lose.wav", jingle_lose(), 0.8)
    save("organ_rally.wav", organ_rally(), 0.8)
    # Loudness targets are loudness_db() values. For reference, the previous
    # generation measured: horn -10.2, crowd -13.0, cheer -11.4, gasp -13.0,
    # shot -19.9, one-timer -20.9, pass -22.1, pickup -23.4, boards -19.1,
    # post -10.0, hit -20.2, save -20.0, skate -17.3, faceoff -21.5. The old
    # impacts kept most of their energy below 250 Hz, i.e. they were
    # nearly inaudible on a tablet speaker; the new ones are placed 3-5 dB
    # hotter on that scale, but carry less bass so they sound about as loud
    # as before on headphones.
    horn = sfx_horn()
    save("horn.wav", horn, level=-10.0)
    save("goal.wav", horn, level=-10.0)  # legacy name, same horn
    save("crowd_loop.wav", crowd_murmur(), level=-14.0)
    save("crowd_roar.wav", crowd_roar(), level=-12.0)
    save("cheer.wav", crowd_cheer(), level=-11.0)
    save("gasp.wav", crowd_gasp(), level=-12.5)
    save("wind_loop.wav", wind_loop(), level=-17.0)
    save("tail_bright.wav", room_tail(True), level=-17.0)
    save("tail_dark.wav", room_tail(False), level=-17.0)
    save("whistle.wav", sfx_whistle(), 0.85)
    save("penalty.wav", sfx_penalty(), 0.85)
    save("button_click.wav", sfx_click(), 0.7)
    save("fire.wav", sfx_fire(), 0.9)
    save("deke.wav", sfx_deke(), 0.85)
    save("glass.wav", sfx_glass(), 0.95)
    save("pad_stack.wav", sfx_pad_stack(), 0.85)

    # Impacts and skates at 44.1 kHz: the snap of a stick and the sizzle of
    # a blade live in the top octave.
    set_rate(44100)
    for v, name in enumerate(("puck_hit", "puck_hit_2", "puck_hit_3")):
        save(name + ".wav", sfx_shot(v), level=-16.0)
    save("one_timer.wav", sfx_shot(3, heavy=True), level=-15.0)
    for v, name in enumerate(("pass", "pass_2", "pass_3")):
        save(name + ".wav", sfx_pass(v), level=-20.0)
    for v, name in enumerate(("wall_bounce", "wall_bounce_2", "wall_bounce_3")):
        save(name + ".wav", sfx_boards(v), level=-16.0)
    save("post.wav", sfx_post(), level=-11.0)
    for v, name in enumerate(("body_hit", "body_hit_2", "body_hit_3")):
        save(name + ".wav", sfx_hit(v), level=-16.0)
    save("save.wav", sfx_save(0), level=-17.0)
    save("save_2.wav", sfx_save(1), level=-17.0)
    for v in range(4):
        save(f"skate{v + 1}.wav", sfx_skate(v), level=-18.5)
    save("faceoff.wav", sfx_faceoff(), level=-18.0)
    save("pickup.wav", sfx_pickup(), level=-23.0)

