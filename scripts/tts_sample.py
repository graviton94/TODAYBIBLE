"""오픈 소스 한국어 읽기 목소리(MeloTTS, MIT)로 시편 23편 견본을 만들어요. GitHub Actions 에서 돌려요."""
import csv, os, sys
from melo.api import TTS

out = sys.argv[1] if len(sys.argv) > 1 else "tts_out"
os.makedirs(out, exist_ok=True)
rows = [r for r in csv.reader(open("android/app/src/main/assets/bible/krv/19.tsv", encoding="utf-8"), delimiter="\t") if r[0] == "23"]
text = " ".join(r[2] for r in rows)
model = TTS(language="KR", device="cpu")
spk = model.hps.data.spk2id["KR"]
for speed in (0.85, 1.0):
    model.tts_to_file(text, spk, f"{out}/ps23_{speed}.wav", speed=speed)
print("ok", len(text))
