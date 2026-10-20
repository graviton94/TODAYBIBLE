"""
기도문 낭독 음원 (Supertonic 3 · OpenRAIL-M): assets/prayers.json 의 기도문을 줄마다 파일 하나로.
교독에서 인도 줄만 틀고 내 줄은 비워 둘 수 있게 줄마다 나눠요. 앱에 함께 넣어요 (내려받기 없음).
목소리는 성경 낭독과 같은 M5 · F5, 말은 앱 언어 (ko · en). 기도라서 성경 낭독보다 조금 더 천천히.
python scripts/narrate_prayers.py <supertonic/py> <assets> <out>
→ <out>/ko_m5/morning_1.m4a … (32kHz 모노 AAC 32k)
"""
import json, os, re, subprocess, sys, tempfile
import numpy as np, soundfile as sf

py, assets, out = sys.argv[1], sys.argv[2], sys.argv[3]
sys.path.insert(0, py)
from helper import load_text_to_speech, load_voice_style  # noqa: E402

# 빠르기, 낮춤(재생 비율), 쉼표 쉼(초): 성경 낭독 (narrate.py) 보다 조금 느리게
TUNE = {"M5": (0.86, 0.96, 0.30), "F5": (0.85, 1.00, 0.26)}
prayers = json.load(open("android/app/src/main/assets/prayers.json", encoding="utf-8"))
tts = load_text_to_speech(os.path.join(assets, "onnx"), False)
sr = tts.sample_rate
tmp = tempfile.mkdtemp()
for lang in ("ko", "en"):
    for style in ("M5", "F5"):
        speed, deepen, gap = TUNE[style]
        st = load_voice_style([os.path.join(assets, "voice_styles", f"{style}.json")])
        dst = os.path.join(out, f"{lang}_{style.lower()}"); os.makedirs(dst, exist_ok=True)
        for pid, p in prayers.items():
            for n, line in enumerate(p[lang], 1):
                # 한 줄 안에서는 쉼표 · 쌍반점에서 숨
                parts = [x for x in re.split(r"(?<=[,;])\s+", line) if x.strip()]
                seq = []
                for x in parts:
                    wav, dur = tts.batch([x], [lang], st, 10, speed)
                    seq += [wav[0, : int(sr * dur[0].item())].astype(np.float32), np.zeros(int(gap * sr), dtype=np.float32)]
                a = np.concatenate(seq[:-1] + [np.zeros(int(0.15 * sr), dtype=np.float32)])
                w = os.path.join(tmp, "l.wav"); sf.write(w, a, sr)
                flt = f"asetrate={int(sr * deepen)},aresample=32000" if deepen != 1.0 else "aresample=32000"
                subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", w, "-af", flt, "-ac", "1", "-c:a", "aac", "-b:a", "32k", os.path.join(dst, f"{pid}_{n}.m4a")], check=True)
        print("ok", lang, style, flush=True)
