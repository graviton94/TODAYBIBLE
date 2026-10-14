"""효과음 (1.5 C7): 직접 합성 (저작권 걱정 없음).
python3 scripts/mockups/mock_sounds.py          → docs/mockups/sounds/*.wav (44.1kHz, 들어 보기용)
python3 scripts/mockups/mock_sounds.py --app    → android/app/src/main/res/raw/fx_*.wav (32kHz, 앱에 들어가는 것)"""
import math, random, struct, wave, os, sys
APP = '--app' in sys.argv
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SR = 32000 if APP else 44100
OUT = os.path.join(ROOT, 'android/app/src/main/res/raw') if APP else os.path.join(ROOT, 'docs/mockups/sounds')
NAMES = {'1_verse_gold': 'fx_verse', '2_chapter_seal': 'fx_seal', '3_plate_hung': 'fx_plate', '4_medal': 'fx_medal', '5_book_done': 'fx_book'}
os.makedirs(OUT, exist_ok=True)
random.seed(7)


def env(t, a, d):  # 빠른 어택 + 지수 감쇠
    return min(1.0, t / a) * math.exp(-t / d) if a > 0 else math.exp(-t / d)


def bell(f0, dur, decay, partials=((1, 1), (2.0, .5), (2.76, .35), (5.4, .18), (8.9, .08)), a=0.004):
    """종소리: 어긋난 배음 (교회 종 · 작은 종)."""
    n = int(SR * dur); out = [0.0] * n
    for ratio, amp in partials:
        f = f0 * ratio; dd = decay / (1 + (ratio - 1) * 0.6)
        for i in range(n):
            t = i / SR; out[i] += amp * env(t, a, dd) * math.sin(2 * math.pi * f * t)
    return out


def noise(dur, decay, lp=0.15, a=0.001):
    n = int(SR * dur); out = []; y = 0.0
    for i in range(n):
        t = i / SR; y += lp * (random.uniform(-1, 1) - y); out.append(y * env(t, a, decay))
    return out


def mix(*tracks, offsets=None):
    offsets = offsets or [0] * len(tracks)
    n = max(int(o * SR) + len(t) for t, o in zip(tracks, offsets)); out = [0.0] * n
    for t, o in zip(tracks, offsets):
        s = int(o * SR)
        for i, v in enumerate(t): out[s + i] += v
    return out


def save(name, x, peak):
    m = max(abs(v) for v in x) or 1
    fade = int(SR * 0.02)
    if APP: name = NAMES[name]
    with wave.open(f'{OUT}/{name}.wav', 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        frames = bytearray()
        for i, v in enumerate(x):
            g = 1.0 if i < len(x) - fade else (len(x) - i) / fade
            frames += struct.pack('<h', int(max(-1, min(1, v / m * peak * g)) * 32767))
        w.writeframes(bytes(frames))
    print(name, round(len(x) / SR, 2), 's')


# 1 한 절 마침 (금빛이 지나갈 때): 아주 작은 맑은 종 하나, 0.5초
save('1_verse_gold', bell(1568, 0.6, 0.18, partials=((1, 1), (2.0, .25), (3.0, .08))), 0.22)
# 2 장 마침 COMPLETUM 인장: 종이에 도장 찍는 둔탁한 소리 + 낮은 울림
thud = [v * 1.0 for v in noise(0.25, 0.035, lp=0.08)]
low = [0.9 * env(i / SR, 0.002, 0.09) * math.sin(2 * math.pi * 92 * i / SR) for i in range(int(SR * 0.4))]
paper = noise(0.18, 0.05, lp=0.6)
save('2_chapter_seal', mix(thud, low, [v * 0.25 for v in paper], offsets=[0, 0, 0.01]), 0.7)
# 3 판화가 화첩에 걸림: 따뜻한 작은 종 두 번 (5도 위로), 1.6초
save('3_plate_hung', mix(bell(659, 1.6, 0.55), bell(988, 1.4, 0.5), offsets=[0, 0.16]), 0.45)
# 4 메달 · 발자취: 세 음 오르는 종 (도 · 미 · 솔), 1.8초
save('4_medal', mix(bell(784, 1.2, 0.45), bell(988, 1.2, 0.45), bell(1175, 1.5, 0.6), offsets=[0, 0.12, 0.24]), 0.5)
# 5 권 다 쓰기 의식: 낮은 교회 종 한 번, 오래 울림 (3.5초)
save('5_book_done', mix(bell(196, 3.6, 1.4, partials=((0.5, .6), (1, 1), (1.19, .45), (1.5, .35), (2.0, .3), (2.5, .15), (3.0, .1))), noise(0.05, 0.01, lp=0.3)), 0.6)
# 미리 듣기: 다섯을 차례로 한 파일에 (앱용은 만들지 않음)
if APP: print('ok'); sys.exit(0)
seq = []
for nm in ['1_verse_gold', '2_chapter_seal', '3_plate_hung', '4_medal', '5_book_done']:
    with wave.open(f'{OUT}/{nm}.wav') as w: data = w.readframes(w.getnframes())
    seq.append(data + b'\x00\x00' * int(SR * 0.8))
with wave.open(f'{OUT}/0_all_in_order.wav', 'w') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(b''.join(seq))
print('ok')
