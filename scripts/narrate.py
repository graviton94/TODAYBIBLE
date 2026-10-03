"""
낭독 음원 만들기 (Supertonic 3 · OpenRAIL-M, 온디바이스 ONNX). 한 권 · 한 목소리씩.
절마다 파일 하나 (장_절.m4a, 32kHz 모노 AAC — 앱의 내 목소리 녹음과 같은 결이라 교독 녹음에 그대로 이어 붙음).
숨: 절 안에서는 이어지는 말끝(~며 · ~고 · ~니 …)에서 쉬고, 절과 절 사이 쉼은 앱이 넣어요.
남성(M5)은 조금 더 진중하게: 조금 느리게 만들고 4% 낮춰 재생해 목소리를 낮고 깊게.
python scripts/narrate.py <supertonic/py> <assets> <M5|F5> <권 1..66> <출력 폴더>
"""
import csv, os, re, subprocess, sys, tempfile
import numpy as np, soundfile as sf

py, assets, voice, book, out = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), sys.argv[5]
sys.path.insert(0, py)
from helper import load_text_to_speech, load_voice_style  # noqa: E402

TUNE = {  # 빠르기, 낮춤(재생 비율), 구절 사이 쉼(초)
    "M5": (0.92, 0.96, 0.42),
    "F5": (0.90, 1.00, 0.35),
}
speed, deepen, gap = TUNE.get(voice, (0.9, 1.0, 0.35))
CONT = re.compile(r"(며|고|니|되|나|여|서|면|매|요|라|은|는|도)$")

def phrases(v):
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
    return re.sub(r"[\[\]{}<>]", "", t).strip()

tts = load_text_to_speech(os.path.join(assets, "onnx"), False)
sr = tts.sample_rate
style = load_voice_style([os.path.join(assets, "voice_styles", f"{voice}.json")])
rows = list(csv.reader(open(f"android/app/src/main/assets/bible/krv/{book:02d}.tsv", encoding="utf-8"), delimiter="\t"))
dst = os.path.join(out, f"{voice.lower()}_{book:02d}"); os.makedirs(dst, exist_ok=True)
tmp = tempfile.mkdtemp()
for r in rows:
    ch, v, text = int(r[0]), int(r[1]), plain(r[2])
    if not text: continue
    parts = []
    for p in phrases(text):
        wav, dur = tts(p, "ko", style, 16, speed)
        parts.append(wav[0, : int(sr * dur[0].item())].astype(np.float32))
        parts.append(np.zeros(int(gap * sr), dtype=np.float32))
    a = np.concatenate(parts[:-1] + [np.zeros(int(0.12 * sr), dtype=np.float32)])
    w = os.path.join(tmp, "v.wav"); sf.write(w, a, sr)
    # 낮춤: 표본을 그대로 두고 더 낮은 표본률로 읽으면 느리고 낮아져요 (목소리가 더 깊게)
    flt = f"asetrate={int(sr * deepen)},aresample=32000" if deepen != 1.0 else "aresample=32000"
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", w, "-af", flt, "-ac", "1", "-c:a", "aac", "-b:a", "32k", os.path.join(dst, f"{ch}_{v}.m4a")], check=True)
print("ok", voice, book, len(rows))
