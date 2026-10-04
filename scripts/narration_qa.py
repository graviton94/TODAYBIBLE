"""
낭독 음원 점검 (R2): 1) 장 파일이 빠짐없이 있는지 (목소리마다 1,189장) 2) 무작위 절 N개를 받아
끝 잘림 (마지막 0.1초가 조용한지) · 잡음/찢어짐 (최대 진폭 · 0 근처 비율) · 길이 (글자당 초) 를 재고
3) Whisper 로 받아 적어 본문과 견줘요. 흘림 발음 · 옛말 · 이름은 사람이 읽듯 자연스러우면 괜찮아서 오류율은 참고만,
   걸러 내는 것 (flags) 은 정말 문제인 것만: 길이가 너무 짧음 (끝이 잘렸거나 빠짐) · 찢어짐 · 받아쓰기가 본문의 절반도 안 됨.
결과는 JSON 하나. 영어 목소리 (en_…) 는 KJV 본문 · 영어 받아쓰기로.
python scripts/narration_qa.py <목소리,목소리> <표본 수> <출력.json>   (환경: PUBLIC = R2 공개 주소, LIST = 버킷 목록 파일)
"""
import csv, io, json, os, random, re, subprocess, sys, tempfile, urllib.request, zipfile
import numpy as np

voices, n, out = sys.argv[1].split(","), int(sys.argv[2]), sys.argv[3]
PUBLIC = os.environ["PUBLIC"].rstrip("/")
listed = set(l.split()[-1] for l in open(os.environ["LIST"]) if l.strip())
english = all(v.startswith("en_") for v in voices)
text = {}
for b in range(1, 67):
    for r in csv.reader(open(f"android/app/src/main/assets/bible/{'kjv' if english else 'krv'}/{b:02d}.tsv", encoding="utf-8"), delimiter="\t"):
        if r[2].strip(): text[(b, int(r[0]), int(r[1]))] = re.sub(r"[\[\]{}<>¶]", "", r[2]).strip()
chapters = sorted({(b, c) for (b, c, _) in text})
report = {"missing": {}, "samples": []}
for v in voices:
    miss = [f"{b}:{c}" for (b, c) in chapters if f"{v}_{b:02d}_c{c:03d}.zip" not in listed]
    report["missing"][v] = miss
print({v: len(m) for v, m in report["missing"].items()}, "of", len(chapters), flush=True)

from faster_whisper import WhisperModel  # noqa: E402
model = WhisperModel("small", device="cpu", compute_type="int8")
random.seed(int(os.environ.get("SEED", "7")))
keys = list(text)
# 아직 올라가지 않은 장은 빼고 고름 (낭독을 만드는 중에도 표본 수를 채우게)
have = {v: [k for k in keys if f"{v}_{k[0]:02d}_c{k[1]:03d}.zip" in listed] for v in voices}
def norm(s): return re.sub(r"[^a-z0-9]", "", s.lower()) if english else re.sub(r"[^가-힣0-9]", "", s)
def cer(a, b):
    a, b = norm(a), norm(b)
    d = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        p, d[0] = d[0], i
        for j, y in enumerate(b, 1):
            p, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, p + (x != y))
    return d[len(b)] / max(1, len(a))
cache = {}
def save(): json.dump(report, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
save()
try:
  for k in range(n):
      v = voices[k % len(voices)]
      if not have[v]: continue
      b, c, vs = random.choice(have[v])
      name = f"{v}_{b:02d}_c{c:03d}.zip"
      if name not in listed: continue
      if name not in cache:
          req = urllib.request.Request(f"{PUBLIC}/narration/{name}", headers={"User-Agent": "Mozilla/5.0 (narration-qa)"})
          cache[name] = urllib.request.urlopen(req, timeout=60).read()
      z = zipfile.ZipFile(io.BytesIO(cache[name]))
      m4a = z.read(f"{c}_{vs}.m4a")
      tmp = tempfile.mkdtemp(); src = os.path.join(tmp, "a.m4a"); open(src, "wb").write(m4a)
      pcm = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", src, "-f", "f32le", "-ac", "1", "-ar", "16000", "-"], capture_output=True, check=True).stdout
      a = np.frombuffer(pcm, dtype=np.float32)
      dur = len(a) / 16000
      tail = float(np.sqrt(np.mean(a[-1600:] ** 2))) if len(a) > 1600 else 1.0
      peak = float(np.abs(a).max()) if len(a) else 0.0
      segs, _ = model.transcribe(a, language="en" if english else "ko", beam_size=1)
      heard = " ".join(s.text for s in segs)
      t = text[(b, c, vs)]
      report["samples"].append({"voice": v, "ref": f"{b}:{c}:{vs}", "sec": round(dur, 2), "sec_per_char": round(dur / max(1, len(norm(t))), 3),
                                "tail_rms": round(tail, 4), "peak": round(peak, 3), "cer": round(cer(t, heard), 3), "text": t, "heard": heard.strip()})
      print(report["samples"][-1]["ref"], v, report["samples"][-1]["cer"], flush=True)
  # 정말 문제인 것만: 같은 목소리의 글자당 길이 가운데값보다 한참 짧음 · 찢어짐 · 받아쓰기가 본문 절반에도 못 미침
  for v in voices:
      s = [x for x in report["samples"] if x["voice"] == v]
      if not s: continue
      med = sorted(x["sec_per_char"] for x in s)[len(s) // 2]
      for x in s:
          why = []
          if x["sec_per_char"] < med * 0.6: why.append("too short")
          if x["peak"] > 0.98: why.append("clipping")
          if len(norm(x["heard"])) < len(norm(x["text"])) * 0.5: why.append("much less heard than written")
          if why: report.setdefault("flags", []).append({"voice": v, "ref": x["ref"], "why": why})
except Exception as e:
    import traceback; report["error"] = traceback.format_exc()
save()
