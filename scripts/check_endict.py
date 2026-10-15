#!/usr/bin/env python3
"""영어 성경 사전 검사 (1.6): 검사한 한국어 사전을 옮긴 글.

  python3 scripts/check_endict.py data/dict/en.tsv [batch.json …]

줄마다 id · 이름 · 이름 뜻 · 한 줄 · 풀이 (탭). 검사:
  · id 가 영어 사전 (assets/ref/names.tsv) 과 한국어 사전 (data/dict/ko.tsv) 에 있음 · 이름이 names.tsv 와 같음
  · 풀이 속 큰따옴표 "…" 는 WEB 본문 그대로 (곧은 따옴표 안, 따옴표 · 아포스트로피 모양은 가려 봄)
  · 풀이 속 장절 (Gen 14:18 · Heb 7:1-3 · 1 Sam 3:3) 이 있는 절
  · 한글 없음 · 길이 (한 줄 ≤ 70자, 풀이 40~900자)
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "android", "app", "src", "main", "assets")
EN = ["Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1 Sam", "2 Sam", "1 Kgs", "2 Kgs", "1 Chr", "2 Chr", "Ezra", "Neh", "Esth", "Job", "Ps", "Prov",
      "Eccl", "Song", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Joel", "Amos", "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal", "Matt", "Mark",
      "Luke", "John", "Acts", "Rom", "1 Cor", "2 Cor", "Gal", "Eph", "Phil", "Col", "1 Thess", "2 Thess", "1 Tim", "2 Tim", "Titus", "Phlm", "Heb", "Jas", "1 Pet",
      "2 Pet", "1 John", "2 John", "3 John", "Jude", "Rev"]
BOOK = {b: i for i, b in enumerate(EN)}
REF = re.compile(r"(?:(" + "|".join(sorted(map(re.escape, EN), key=len, reverse=True)) + r")\s+)?(\d+):(\d+)(?:\s*[-–]\s*(\d+)(?::(\d+))?)?")


def key(b, c, v): return (b + 1) * 1_000_000 + c * 1000 + v


def norm(s): return re.sub(r"[“”\"]", "\"", s.replace("’", "'").replace("‘", "'"))


def refs_in(text):
    out = []
    for paren in re.findall(r"\(([^()]*)\)", text):
        book = None
        for m in REF.finditer(paren):
            if m.group(1): book = BOOK[m.group(1)]
            if book is None: continue
            c, v = int(m.group(2)), int(m.group(3))
            e = key(book, int(m.group(4)), int(m.group(5))) if m.group(5) else key(book, c, int(m.group(4))) if m.group(4) else key(book, c, v)
            out.append((key(book, c, v), e, m.group(0)))
    return out


def main(path, batches):
    web = {}
    for b in range(66):
        for l in open(os.path.join(ASSETS, "bible", "web", "%02d.tsv" % (b + 1)), encoding="utf-8"):
            p = l.rstrip("\n").split("\t", 2)
            if len(p) == 3 and p[2]: web[key(b, int(p[0]), int(p[1]))] = p[2]
    allw = norm("\n".join(web.values()))
    en = {l.split("\t")[0]: l.rstrip("\n").split("\t") for l in open(os.path.join(ASSETS, "ref", "names.tsv"), encoding="utf-8") if not l.startswith("#")}
    ko = {l.split("\t")[0] for l in open(os.path.join(ROOT, "data", "dict", "ko.tsv"), encoding="utf-8") if not l.startswith("#")}
    rows = [l.rstrip("\n").split("\t") for l in open(path, encoding="utf-8") if l.strip() and not l.startswith("#")]
    bad, seen = [], set()
    for r in rows:
        if len(r) != 5: bad.append(f"{r[0]}: 칸 {len(r)}개"); continue
        i, name, meaning, line, body = r
        def err(m): bad.append(f"{i}: {m}")
        if i not in en or i not in ko: err("사전에 없는 id"); continue
        if i in seen: err("id 겹침")
        seen.add(i)
        if name != en[i][2]: err(f"이름이 다름 ({name} ≠ {en[i][2]})")
        if re.search("[가-힣]", "".join(r)): err("한글")
        if not line or len(line) > 70: err(f"한 줄 길이 {len(line)}")
        if not 40 <= len(body) <= 900: err(f"풀이 길이 {len(body)}")
        for q in re.findall(r"\"([^\"]+)\"", body):
            if norm(q).strip(" ,.;:!?") not in allw: err(f"인용이 WEB 과 다름: {q[:50]}")
        for s, t, raw in refs_in(body):
            if s not in web or t not in web: err(f"없는 장절: {raw}")
    for b in batches:
        want = [x["id"] for x in json.load(open(b, encoding="utf-8"))]
        have = [r[0] for r in rows if r[0] in set(want)]
        if have != want: bad.append(f"{os.path.basename(b)}: 빠짐 {len(set(want) - set(have))}")
    print(f"{len(rows)}줄 검사, 문제 {len(bad)}개")
    for x in bad: print(" ·", x)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2:]))
