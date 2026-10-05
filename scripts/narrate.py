"""
낭독 음원 만들기 (Supertonic 3 · OpenRAIL-M, 온디바이스 ONNX). 한 권 · 한 목소리씩.
절마다 파일 하나 (장_절.m4a, 32kHz 모노 AAC — 앱의 내 목소리 녹음과 같은 결이라 교독 녹음에 그대로 이어 붙음).
숨: 절 안에서는 이어지는 말끝(~며 · ~고 · ~니 …)에서 쉬고, 절과 절 사이 쉼은 앱이 넣어요.
남성(M5)은 조금 더 진중하게: 조금 느리게 만들고 4% 낮춰 재생해 목소리를 낮고 깊게.
빠르게: 구절들을 길이 순으로 묶어 한꺼번에(배치) 만들고, 큰 권은 장 묶음(part)으로 나눠 여러 작업에서 동시에.
python scripts/narrate.py <supertonic/py> <assets> <M5|F5|EN_M3 …> <권 1..66> <첫 장> <끝 장> <part> <출력 폴더>
영어(KJV): 목소리 앞에 EN_ (예: EN_M3) — 본문 kjv, 말 en, 파일 이름 앞머리 en_m3.
python scripts/narrate.py --plan  → 작업 목록 (JSON)
"""
import csv, os, re, subprocess, sys, tempfile

import json, time

if sys.argv[1] == "--plan":
    # 권마다 장을 차례로 묶어 한 묶음에 절이 PART 개 넘지 않게
    PART = 220; jobs = []
    for b in range(1, 67):
        counts = {}
        for r in csv.reader(open(f"android/app/src/main/assets/bible/krv/{b:02d}.tsv", encoding="utf-8"), delimiter="\t"):
            counts[int(r[0])] = counts.get(int(r[0]), 0) + 1
        part, start, n = 1, None, 0
        for ch in sorted(counts):
            if start is None: start = ch
            n += counts[ch]
            if n >= PART:
                jobs.append([b, start, ch, part]); part += 1; start = None; n = 0
        if start is not None: jobs.append([b, start, max(counts), part])
    print(json.dumps([{"voice": v, "book": j[0], "from": j[1], "to": j[2], "part": j[3]} for v in ("M5", "F5") for j in jobs]))
    sys.exit(0)

import numpy as np, soundfile as sf  # noqa: E402

py, assets, voice, book = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
ch_from, ch_to, part, out = int(sys.argv[5]), int(sys.argv[6]), int(sys.argv[7]), sys.argv[8]
sys.path.insert(0, py)
from helper import load_text_to_speech, load_voice_style  # noqa: E402

TUNE = {  # 빠르기, 낮춤(재생 비율), 구절 사이 쉼(초)
    "M5": (0.92, 0.96, 0.42),
    "F5": (0.90, 1.00, 0.35),
    # 영어 (KJV): 견본 (0.88) 결 그대로, 남성은 한국어처럼 조금 낮게
    "EN_M5": (0.88, 0.97, 0.36),
    "EN_F5": (0.88, 1.00, 0.32),
    "WEB_M5": (0.88, 0.97, 0.36),
    "WEB_F5": (0.88, 1.00, 0.32),
}
# 목소리: M5 · F5 (개역한글) · EN_M5 · EN_F5 (KJV) · WEB_M5 · WEB_F5 (World English Bible, 같은 영어 목소리)
web = voice.upper().startswith("WEB_")
english = voice.upper().startswith("EN_") or web
style_id = voice.split("_", 1)[1].upper() if english else voice
tr, lang = ("web", "en") if web else (("kjv", "en") if english else ("krv", "ko"))
speed, deepen, gap = TUNE.get(voice.upper(), (0.92, 1.0, 0.32) if english else (0.9, 1.0, 0.35))
CONT = re.compile(r"(며|고|니|되|나|여|서|면|매|요|라|은|는|도)$")

def phrases(v):
    if english:  # 영어: 문장부호에서 숨, 너무 짧은 토막은 붙여서
        res = []
        for p in re.split(r"(?<=[,;:.?!])\s+", v):
            if res and len(p) < 12: res[-1] += " " + p
            elif res and len(res[-1]) < 12: res[-1] += " " + p
            else: res.append(p)
        return [p for p in res if p.strip()]
    res, cur = [], []
    for w in v.split():
        cur.append(w)
        if (CONT.search(w.rstrip(",.;:?!")) and len("".join(cur)) >= 9) or w[-1:] in ",;:.?!":
            res.append(" ".join(cur)); cur = []
    if cur:
        if res and len("".join(cur)) < 5: res[-1] += " " + " ".join(cur)
        else: res.append(" ".join(cur))
    return res

def plain(t):  # 앱의 Markup.plain 과 같게: 꾸밈 표시 지우기
    t = re.sub(r"[\[\]{}<>¶]", "", t)
    # KJV: 대문자로 쓴 하나님의 이름 (LORD · GOD · JESUS …) 은 글자를 하나씩 읽지 않게 첫 글자만 크게
    if english: t = re.sub(r"\b([A-Z])([A-Z]{1,})\b", lambda m: m.group(1) + m.group(2).lower(), t)
    return re.sub(r"\s+", " ", t).strip()

tts = load_text_to_speech(os.path.join(assets, "onnx"), False)
sr = tts.sample_rate
style_path = os.path.join(assets, "voice_styles", f"{style_id}.json")
rows = [r for r in csv.reader(open(f"android/app/src/main/assets/bible/{tr}/{book:02d}.tsv", encoding="utf-8"), delimiter="\t") if ch_from <= int(r[0]) <= ch_to]
dst = os.path.join(out, f"{voice.lower()}_{book:02d}_{part}"); os.makedirs(dst, exist_ok=True)
# 모든 구절을 한 줄로 펼쳐 길이 순으로 묶어 만들기
items = []  # (절 번호표, 구절 순서, 글)
for r in rows:
    text = plain(r[2])
    for i, p in enumerate(phrases(text)): items.append(((int(r[0]), int(r[1])), i, p))
order = sorted(range(len(items)), key=lambda k: len(items[k][2]))
audio = {}
B = 24; t0 = time.time()
styles = {}
for s0 in range(0, len(order), B):
    idx = order[s0:s0 + B]
    n = len(idx)
    if n not in styles: styles[n] = load_voice_style([style_path] * n)
    wav, dur = tts.batch([items[k][2] for k in idx], [lang] * n, styles[n], 10, speed)
    for j, k in enumerate(idx):
        audio[k] = wav[j, : int(sr * dur[j].item())].astype(np.float32)
print(f"synth {len(items)} phrases in {time.time() - t0:.0f}s", flush=True)
tmp = tempfile.mkdtemp()
by_verse = {}
for k, (key, i, _) in enumerate(items): by_verse.setdefault(key, []).append((i, k))
for (ch, v), parts in by_verse.items():
    seq = []
    for _, k in sorted(parts):
        seq += [audio[k], np.zeros(int(gap * sr), dtype=np.float32)]
    a = np.concatenate(seq[:-1] + [np.zeros(int(0.12 * sr), dtype=np.float32)])
    w = os.path.join(tmp, "v.wav"); sf.write(w, a, sr)
    # 낮춤: 표본을 그대로 두고 더 낮은 표본률로 읽으면 느리고 낮아져요 (목소리가 더 깊게)
    flt = f"asetrate={int(sr * deepen)},aresample=32000" if deepen != 1.0 else "aresample=32000"
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", w, "-af", flt, "-ac", "1", "-c:a", "aac", "-b:a", "32k", os.path.join(dst, f"{ch}_{v}.m4a")], check=True)
print("ok", voice, book, ch_from, ch_to, len(by_verse), f"{time.time() - t0:.0f}s")
