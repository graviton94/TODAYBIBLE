#!/usr/bin/env python3
"""앱 글꼴: 공개(OFL) 글꼴을 받아 실제로 쓰는 글자만 남겨 res/font 로.

    pip install fonttools && python3 scripts/build_fonts.py

- Noto Serif KR 500 · 700: 개역한글 본문 + 앱 문자열에 나오는 글자 (+ 라틴 기본)
- Song Myung: 표제 한글 (앱 문자열 + 권 이름)
- EB Garamond 500 · 600 · 이탤릭 500: KJV 본문 (라틴 전체)
- Nanum Pen Script: 필사 노트의 내 글씨
- Noto Serif KR 600: 제목 · Cinzel: 라틴 머리글 · Cormorant Garamond: 큰 숫자 (하루의 편지와 같은 체계)
- IM Fell English SC: 머리줄 · 장절 표기, UnifrakturMaguntia: 블랙레터 표제 · 장 번호 (라틴만)
"""
import glob, json, os, sys, urllib.request
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, ".cache"); OUT = os.path.join(ROOT, "android", "app", "src", "main", "res", "font")
SRC = {
    "NotoSerifKR[wght].ttf": "notoserifkr/NotoSerifKR%5Bwght%5D.ttf", "SongMyung-Regular.ttf": "songmyung/SongMyung-Regular.ttf",
    "EBGaramond[wght].ttf": "ebgaramond/EBGaramond%5Bwght%5D.ttf", "EBGaramond-Italic[wght].ttf": "ebgaramond/EBGaramond-Italic%5Bwght%5D.ttf",
    "IMFeENsc28P.ttf": "imfellenglishsc/IMFeENsc28P.ttf", "UnifrakturMaguntia-Book.ttf": "unifrakturmaguntia/UnifrakturMaguntia-Book.ttf",
    "NanumPenScript-Regular.ttf": "nanumpenscript/NanumPenScript-Regular.ttf",
    "Cinzel[wght].ttf": "cinzel/Cinzel%5Bwght%5D.ttf", "CormorantGaramond[wght].ttf": "cormorantgaramond/CormorantGaramond%5Bwght%5D.ttf",
}
LATIN = "".join(chr(c) for c in range(0x20, 0x7F)) + "‘’“”–—…·¶✠❦✝ΑΩÆæ₩€£¥"

def fetch():
    os.makedirs(CACHE, exist_ok=True)
    for name, path in SRC.items():
        p = os.path.join(CACHE, name)
        if not os.path.exists(p): urllib.request.urlretrieve("https://raw.githubusercontent.com/google/fonts/main/ofl/" + path, p)

def korean_text():
    chars = set()
    for f in glob.glob(os.path.join(ROOT, "android", "app", "src", "main", "assets", "bible", "krv", "*.tsv")): chars |= set(open(f, encoding="utf-8").read())
    s = json.load(open(os.path.join(ROOT, "design", "strings.json"), encoding="utf-8"))
    for v in s.values(): chars |= set(v["ko"] if isinstance(v, dict) else v)
    for f in glob.glob(os.path.join(ROOT, "android", "core", "src", "main", "kotlin", "**", "*.kt"), recursive=True): chars |= set(open(f, encoding="utf-8").read())
    for f in glob.glob(os.path.join(ROOT, "android", "app", "src", "main", "java", "**", "*.kt"), recursive=True): chars |= set(open(f, encoding="utf-8").read())
    chars |= set(open(os.path.join(ROOT, "data", "plates.tsv"), encoding="utf-8").read())
    chars |= set("0123456789년월일주시분오전후 ")
    return "".join(sorted(c for c in chars if c >= " " and c not in "\t\n"))

def make(src, out, text, wght=None):
    f = TTFont(os.path.join(CACHE, src))
    if wght is not None and "fvar" in f: f = instancer.instantiateVariableFont(f, {"wght": wght})
    opt = subset.Options(); opt.layout_features = ["*"]; opt.name_IDs = ["*"]; opt.notdef_outline = True
    sub = subset.Subsetter(opt); sub.populate(text=text); sub.subset(f)
    f.save(os.path.join(OUT, out)); return os.path.getsize(os.path.join(OUT, out))

def check():
    """CI: 지금 글꼴이 앱에 나오는 한글을 모두 갖고 있는지 (내려받지 않음)."""
    cm = TTFont(os.path.join(OUT, "serif_kr_medium.ttf")).getBestCmap()
    missing = sorted(c for c in korean_text() if "\uac00" <= c <= "\ud7a3" and ord(c) not in cm)
    if missing: print("글꼴에 없는 글자:", "".join(missing), "→ python3 scripts/build_fonts.py"); sys.exit(1)
    print("fonts ok")

def main():
    fetch(); os.makedirs(OUT, exist_ok=True)
    ko = korean_text() + LATIN
    titles = "".join(sorted(set("".join(v["ko"] for v in json.load(open(os.path.join(ROOT, "design", "strings.json"), encoding="utf-8")).values() if isinstance(v, dict)) + open(os.path.join(ROOT, "android", "core", "src", "main", "kotlin", "io", "github", "graviton94", "todaybible", "core", "Canon.kt"), encoding="utf-8").read() + open(os.path.join(ROOT, "data", "plates.tsv"), encoding="utf-8").read() + "0123456789장편"))) + LATIN
    allLatin = "".join(chr(c) for c in range(0x20, 0x250)) + "‘’“”–—…·¶✠❦✝ΑΩ"
    sizes = {
        "serif_kr_medium.ttf": make("NotoSerifKR[wght].ttf", "serif_kr_medium.ttf", ko, 500),
        "serif_kr_bold.ttf": make("NotoSerifKR[wght].ttf", "serif_kr_bold.ttf", ko, 700),
        "title_kr.ttf": make("SongMyung-Regular.ttf", "title_kr.ttf", titles),
        # 제목 (하루의 편지와 같은 Noto Serif KR 600)
        "serif_kr_semibold.ttf": make("NotoSerifKR[wght].ttf", "serif_kr_semibold.ttf", titles, 600),
        # 머리글 (라틴 대문자) · 큰 숫자
        "caps.ttf": make("Cinzel[wght].ttf", "caps.ttf", allLatin, 600),
        "display.ttf": make("CormorantGaramond[wght].ttf", "display.ttf", allLatin, 600),
        "garamond_medium.ttf": make("EBGaramond[wght].ttf", "garamond_medium.ttf", allLatin, 500),
        "garamond_semibold.ttf": make("EBGaramond[wght].ttf", "garamond_semibold.ttf", allLatin, 600),
        "garamond_italic.ttf": make("EBGaramond-Italic[wght].ttf", "garamond_italic.ttf", allLatin, 500),
        "fell_sc.ttf": make("IMFeENsc28P.ttf", "fell_sc.ttf", allLatin),
        "blackletter.ttf": make("UnifrakturMaguntia-Book.ttf", "blackletter.ttf", allLatin),
        # 필사 노트의 손글씨 (한글 + 라틴)
        "pen.ttf": make("NanumPenScript-Regular.ttf", "pen.ttf", ko + allLatin),
    }
    print(len(ko), "Korean+UI chars;", {k: f"{v/1024:.0f}KB" for k, v in sizes.items()})

if __name__ == "__main__":
    check() if "--check" in sys.argv else main()
