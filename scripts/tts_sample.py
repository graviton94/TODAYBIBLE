"""
낭독 견본: Supertonic 3 (Supertone, OpenRAIL-M · 온디바이스 ONNX) 한국어 목소리 열 가지로 시편 23편.
숨: 절과 절 사이 0.9초, 절 안에서는 이어지는 말끝(~며 · ~고 · ~니 …)에서 0.35초 쉬어요. 빠르기는 조금 느리게.
GitHub Actions 에서 돌려요: python scripts/tts_sample.py <supertonic/py 경로> <assets 경로> <출력 폴더>
"""
import csv, os, re, sys
import numpy as np, soundfile as sf

py, assets, out = sys.argv[1:4]
sys.path.insert(0, py)
from helper import load_text_to_speech, load_voice_style  # noqa: E402

os.makedirs(out, exist_ok=True)
verses = [r[2] for r in csv.reader(open("android/app/src/main/assets/bible/krv/19.tsv", encoding="utf-8"), delimiter="\t") if r[0] == "23"]

# 이어지는 말끝: 여기서 한 번 숨을 쉬어요 (너무 짧은 토막은 붙여서)
CONT = re.compile(r"(며|고|니|되|나|여|서|면|매|요|라|은|는|도)$")
def phrases(v: str):
    out, cur = [], []
    for w in v.split():
        cur.append(w)
        if CONT.search(w) and len("".join(cur)) >= 9:
            out.append(" ".join(cur)); cur = []
    if cur:
        if out and len("".join(cur)) < 5: out[-1] += " " + " ".join(cur)
        else: out.append(" ".join(cur))
    return out

tts = load_text_to_speech(os.path.join(assets, "onnx"), False)
sr = tts.sample_rate
def silence(s): return np.zeros(int(s * sr), dtype=np.float32)
for name in ["M1", "M2", "M3", "M4", "M5", "F1", "F2", "F3", "F4", "F5"]:
    style = load_voice_style([os.path.join(assets, "voice_styles", f"{name}.json")])
    parts = [silence(0.5)]
    for i, v in enumerate(verses):
        for j, p in enumerate(phrases(v)):
            wav, dur = tts(p, "ko", style, 16, 0.88)
            parts.append(wav[0, : int(sr * dur[0].item())].astype(np.float32))
            parts.append(silence(0.35))
        parts[-1] = silence(0.95)
    sf.write(os.path.join(out, f"ps23_{name}.wav"), np.concatenate(parts), sr)
    print("ok", name)
print([phrases(v) for v in verses])
