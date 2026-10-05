#!/usr/bin/env python3
"""World English Bible Updated (WEBU, eBible.org `engwebu`) 를 받아 data/bible/web.json 으로 (GitHub Actions 에서 실행).

- 공개 도메인 (저작권 없음). 이름 'World English Bible' 은 상표라 본문을 고치면 그 이름을 쓰지 않아요 → 고치지 않고 그대로.
- WEBU = 고전 WEB 과 같되 하나님의 이름을 'Yahweh' 대신 'LORD' · 'GOD' (대문자), 미국 철자.
- 개신교 66권만 (제2정경은 뺌). 각주(\\f) · 교차 참조(\\x) · 소제목(\\s …) · 시편 표제(\\d) 는 뺀다.
- 권 · 장 수가 정경(Canon)과 다르거나 절 수가 31,102 에서 크게 벗어나면 실패한다.
"""
import io, json, os, re, sys, urllib.request, zipfile

URL = "https://ebible.org/Scriptures/engwebu_usfm.zip"
OUT = os.path.join(os.path.dirname(__file__), "..", "data", "bible")
CODES = ("GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SNG ISA JER LAM EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL "
         "MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL 1TH 2TH 1TI 2TI TIT PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV").split()
CHAPTERS = [50, 40, 27, 36, 34, 24, 21, 4, 31, 24, 22, 25, 29, 36, 10, 13, 10, 42, 150, 31, 12, 8, 66, 52, 5, 48, 12, 14, 3, 9, 1, 4, 7, 3, 3, 3, 2, 14, 4,
            28, 16, 24, 21, 28, 16, 16, 13, 6, 6, 4, 4, 5, 3, 6, 4, 3, 1, 13, 5, 5, 3, 5, 1, 1, 1, 22]
DROP = re.compile(r"^\\(id|ide|h|toc\d?|mt\d?|mte\d?|ms\d?|mr|s\d?|sr|r|sp|d|rem|cl|cd|is\d?|ip|imt\d?|ie)\b")

def clean(s: str) -> str:
    s = re.sub(r"\\f\s.*?\\f\*", "", s, flags=re.S)
    s = re.sub(r"\\fe\s.*?\\fe\*", "", s, flags=re.S)
    s = re.sub(r"\\x\s.*?\\x\*", "", s, flags=re.S)
    s = re.sub(r"\\\+?w\s+([^|\\]*?)(\|[^\\]*)?\\\+?w\*", r"\1", s)     # \w word|strong="…"\w*
    s = re.sub(r"\\\+?[a-z]+\d*\*", "", s)                              # 닫는 표시 (\wj* \add* …)
    s = re.sub(r"\\\+?[a-z]+\d*\s?", "", s)                             # 여는 표시 (\p \q1 \wj \nd …)
    s = s.replace("¶", "")
    return re.sub(r"\s+", " ", s).strip()

def book(text: str):
    lines = [l for l in text.splitlines() if not DROP.match(l.strip())]
    body = "\n".join(lines)
    chapters = []
    for part in re.split(r"\\c\s+\d+", body)[1:]:
        vs = re.split(r"\\v\s+(\d+)\s", part)
        verses = {}
        for i in range(1, len(vs) - 1, 2):
            verses[int(vs[i])] = clean(vs[i + 1])
        n = max(verses) if verses else 0
        chapters.append([verses.get(v, "") for v in range(1, n + 1)])
    return chapters

def main():
    os.makedirs(OUT, exist_ok=True)
    # eBible 은 기본 파이썬 요청을 막아요: 보통 브라우저처럼
    req = urllib.request.Request(URL, headers={"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36", "Accept": "*/*"})
    data = urllib.request.urlopen(req).read()
    z = zipfile.ZipFile(io.BytesIO(data))
    files = {}
    for n in z.namelist():
        if not n.lower().endswith((".usfm", ".sfm")): continue
        t = z.read(n).decode("utf-8-sig")
        m = re.search(r"\\id\s+(\w+)", t)
        if m: files[m.group(1).upper()] = t
    books, total, problems = [], 0, []
    for i, code in enumerate(CODES):
        if code not in files: problems.append(f"{code} 없음"); continue
        ch = book(files[code])
        if len(ch) != CHAPTERS[i]: problems.append(f"{code} 장 수 {len(ch)} ≠ {CHAPTERS[i]}")
        total += sum(len(c) for c in ch)
        books.append({"osis": code, "name": code, "chapters": ch})
    empty = sum(1 for b in books for c in b["chapters"] for v in c if not v)
    summary = {"books": len(books), "chapters": sum(len(b["chapters"]) for b in books), "verses": total, "empty": empty}
    print("web", summary, problems)
    print(f"::notice::web {summary} problems={problems[:12]}")
    if problems or len(books) != 66 or abs(total - 31102) > 200: sys.exit(f"확인 필요: {problems} {summary}")
    json.dump({"id": "web", "module": "engwebu", "books": books}, open(os.path.join(OUT, "web.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    s = json.load(open(os.path.join(OUT, "summary.json"))) if os.path.exists(os.path.join(OUT, "summary.json")) else {}
    s["web"] = summary; json.dump(s, open(os.path.join(OUT, "summary.json"), "w"), indent=1)

if __name__ == "__main__":
    try: main()
    except SystemExit as e:
        print(f"::error::{e}"); raise
    except Exception as e:
        print(f"::error::{type(e).__name__}: {e}"); raise
