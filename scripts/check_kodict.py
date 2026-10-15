#!/usr/bin/env python3
"""한국어 성경 사전 검사 (1.6.x).

  python3 scripts/check_kodict.py data/dict/ko.tsv [batch.json …]

줄마다 id · 이름 · 이름 뜻 · 한 줄 · 풀이 (탭). 검사:
  · id 가 영어 사전 (assets/ref/names.tsv) 에 있음, 겹치지 않음
  · 이름이 개역한글 본문에 있음 (사람 · 땅은 그 항목이 나오는 절 가운데 하나에)
  · 풀이 속 큰따옴표 "…" 는 개역한글 본문 그대로
  · 풀이 속 장절 (창 14:18 · 히 7:1-3 · 시 110:4; 히 5:6) 이 있는 절
  · 길이 (한 줄 ≤ 30자, 풀이 40~450자) · 해요체 (문장 끝 "요.")
batch.json 을 주면 그 차례 · 개수와도 맞춰 봐요. 틀린 것이 있으면 목록을 찍고 1 로 끝나요.
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "android", "app", "src", "main", "assets")
KO = ["창", "출", "레", "민", "신", "수", "삿", "룻", "삼상", "삼하", "왕상", "왕하", "대상", "대하", "스", "느", "에", "욥", "시", "잠", "전", "아", "사", "렘", "애", "겔", "단",
      "호", "욜", "암", "옵", "욘", "미", "나", "합", "습", "학", "슥", "말", "마", "막", "눅", "요", "행", "롬", "고전", "고후", "갈", "엡", "빌", "골", "살전", "살후", "딤전", "딤후", "딛", "몬",
      "히", "약", "벧전", "벧후", "요일", "요이", "요삼", "유", "계"]
BOOK = {b: i for i, b in enumerate(KO)}
# (창 14:18) · (히 7:1-3) · (시 110:4; 히 5:6) · (14:20) 같은 장절. 권이 빠지면 앞의 권.
REF = re.compile(r"(?:(" + "|".join(sorted(map(re.escape, KO), key=len, reverse=True)) + r")\s*)?(\d+):(\d+)(?:\s*[-–~]\s*(\d+)(?::(\d+))?)?")


def key(b, c, v): return (b + 1) * 1_000_000 + c * 1000 + v


def krv():
    t = {}
    for b in range(66):
        for l in open(os.path.join(ASSETS, "bible", "krv", "%02d.tsv" % (b + 1)), encoding="utf-8"):
            p = l.rstrip("\n").split("\t", 2)
            if len(p) == 3 and p[2]: t[key(b, int(p[0]), int(p[1]))] = p[2]
    return t


def refs_in(text):
    """풀이 속 장절 → [(시작 key, 끝 key, 원문)]. 괄호 안만 봐요."""
    out = []
    for paren in re.findall(r"\(([^()]*)\)", text):
        book = None
        for m in REF.finditer(paren):
            if m.group(1): book = BOOK[m.group(1)]
            if book is None: continue
            c, v = int(m.group(2)), int(m.group(3))
            if m.group(5): e = key(book, int(m.group(4)), int(m.group(5)))
            elif m.group(4): e = key(book, c, int(m.group(4)))
            else: e = key(book, c, v)
            out.append((key(book, c, v), e, m.group(0)))
    return out


def main(path, batches):
    bible = krv(); allt = "\n".join(bible.values())
    en = {l.split("\t")[0]: l.rstrip("\n").split("\t") for l in open(os.path.join(ASSETS, "ref", "names.tsv"), encoding="utf-8") if not l.startswith("#")}
    rows = [l.rstrip("\n").split("\t") for l in open(path, encoding="utf-8") if l.strip() and not l.startswith("#")]
    bad, seen = [], set()
    for r in rows:
        if len(r) != 5: bad.append(f"{r[0]}: 칸 {len(r)}개"); continue
        i, name, meaning, line, body = r
        def err(m): bad.append(f"{i} {name}: {m}")
        if i not in en: err("영어 사전에 없는 id"); continue
        if i in seen: err("id 겹침")
        seen.add(i)
        e = en[i]; refs = [int(x) for x in e[7].split()]
        if not name: err("이름 없음")
        elif e[1] != "T":
            # 이름 없이 나오는 사람 (「엘리바스의 아내」 처럼) 은 낱말마다 그 절들에 있으면 돼요
            near = " ".join(bible.get(k, "") for k in refs)
            # 성경이 이름을 밝히지 않은 사람 (TIPNR id 가 a_ · daughter_of_ … 로 시작: a_wife_of_ezekiel) 은 「에스겔의 아내」 처럼 풀어 쓴 이름이라 넘어가요
            if not i.startswith(("a_", "daughter_of_", "son_of_", "wife_of_", "mother_of_", "father_of_", "husband_of_", "sister_of_", "brother_of_")) and not any(name in bible.get(k, "") for k in refs) and not all(w.rstrip("의") in near for w in name.split()): err("이름이 나오는 절에 없음")
        elif name not in allt and name[:-1] not in allt: err("낱말이 본문에 없음")  # 「못 박음」 처럼 본문의 말을 명사로 바꾼 것은 끝 한 글자까지 봐 줘요
        if not line or len(line) > 30: err(f"한 줄 길이 {len(line)}")
        if not 40 <= len(body) <= 450: err(f"풀이 길이 {len(body)}")  # 드문 이름은 1~2문장 (50자 안팎)
        if "\t" in body: err("탭")
        for q in re.findall(r"\"([^\"]+)\"|“([^”]+)”", body):
            q = q[0] or q[1]
            if q not in allt: err(f"인용이 본문과 다름: {q[:40]}")
        for s, t, raw in refs_in(body):
            if s not in bible or t not in bible: err(f"없는 장절: {raw}")
        ends = [x for x in re.split(r"(?<=[.!?])\s+", body.strip()) if x]
        # 문장 끝 장절 괄호 "(삼하 3:2)." 는 떼고 봐요
        tails = [re.sub(r"\s*\([^()]*\)\s*(?=[.!?]$)", "", x) for x in ends]
        odd = [x for x in tails if not x.rstrip("\"”’").endswith(("요.", "요?", "요!"))]
        if odd: err("해요체 아닌 문장: " + odd[0][-25:])
    for b in batches:
        want = [x["id"] for x in json.load(open(b, encoding="utf-8"))]
        have = [r[0] for r in rows if r[0] in set(want)]
        if have != want: bad.append(f"{os.path.basename(b)}: 빠짐 {len(set(want) - set(have))} · 차례 {'맞음' if [w for w in want if w in have] == have else '다름'}")
    print(f"{len(rows)}줄 검사, 문제 {len(bad)}개")
    for x in bad: print(" ·", x)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2:]))
