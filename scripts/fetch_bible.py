#!/usr/bin/env python3
"""성경 본문 두 벌을 CrossWire SWORD 모듈에서 받아 data/bible/<id>.json 으로 (GitHub Actions 에서 실행).

    pip install pysword && python3 scripts/fetch_bible.py

- KJV (1611, 공개 도메인. 영국은 왕실 특허 — 영국 출시는 보류)
- KorRV 개역한글 (1961, 저작권 보호 기간이 끝난 판 · 이 판만 씀. 개역개정은 쓰지 않음)
KJV 의 번역자 첨가어(<transChange type="added">)는 {중괄호}로, 하나님의 이름(<divineName>)은 대문자로, 단락 표시(¶)는 그대로 남긴다.
주석(<note>) · 제목(<title>) 은 뺀다. 절 수가 정본(31,102절)과 크게 다르면 실패한다.
"""
import json, os, re, sys, urllib.request, zipfile
from pysword.modules import SwordModules

MODULES = {"kjv": "KJV", "krv": "KorRV"}
URL = "https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/{}.zip"
OUT = os.path.join(os.path.dirname(__file__), "..", "data", "bible")

def clean(raw: str) -> str:
    s = re.sub(r"<note\b.*?</note>", "", raw, flags=re.S)
    s = re.sub(r"<title\b.*?</title>", "", s, flags=re.S)
    s = re.sub(r'<milestone[^>]*marker="¶"[^>]*/>', "¶ ", s)
    s = re.sub(r'<transChange[^>]*type="added"[^>]*>(.*?)</transChange>', r"{\1}", s, flags=re.S)
    s = re.sub(r"<divineName>(.*?)</divineName>", lambda m: m.group(1).upper(), s, flags=re.S)
    s = re.sub(r"<[^>]+>", "", s)
    s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("&quot;", '"')
    s = re.sub(r"\s+", " ", s).strip()
    s = s.replace("{ ", "{").replace(" }", "}").replace("{}", "")
    return s

def main():
    os.makedirs(OUT, exist_ok=True)
    summary = {}
    for key, name in MODULES.items():
        z = f"/tmp/{name}.zip"
        urllib.request.urlretrieve(URL.format(name), z)
        mods = SwordModules(z); mods.parse_modules()
        bible = mods.get_bible_from_module(name)
        books, total = [], 0
        for testament, bl in bible.get_structure().get_books().items():
            for b in bl:
                chapters = []
                for c in range(1, b.num_chapters + 1):
                    vs = []
                    for v in range(1, b.chapter_lengths[c - 1] + 1):
                        vs.append(clean(bible.get(books=[b.osis_name], chapters=[c], verses=[v], clean=False)))
                    chapters.append(vs); total += len(vs)
                books.append({"osis": b.osis_name, "name": b.name, "chapters": chapters})
        empty = sum(1 for b in books for ch in b["chapters"] for v in ch if not v)
        summary[key] = {"books": len(books), "chapters": sum(len(b["chapters"]) for b in books), "verses": total, "empty": empty}
        json.dump({"id": key, "module": name, "books": books}, open(os.path.join(OUT, f"{key}.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
        print(key, summary[key])
    json.dump(summary, open(os.path.join(OUT, "summary.json"), "w"), indent=1)
    bad = [k for k, s in summary.items() if s["books"] != 66 or abs(s["verses"] - 31102) > 200]
    if bad: sys.exit(f"절 수 확인 필요: {bad}")

if __name__ == "__main__":
    main()
