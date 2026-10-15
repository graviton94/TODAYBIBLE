#!/usr/bin/env python3
"""1.6 관련 구절 · 성경 사전 자료 만들기.

원자료는 ref-data 브랜치 (.github/workflows/fetch-refdata.yml 이 받아 둠) 를 .cache/refdata 에 풀어 두고 돌려요:
  git fetch origin ref-data && mkdir -p .cache/refdata && for f in cross-references.zip easton.xml hitchcock.xml; do git show origin/ref-data:$f > .cache/refdata/$f; done
  (TIPNR 은 raw.githubusercontent.com 에서 .cache/refdata/tipnr.txt 로)

만드는 것 (android/app/src/main/assets/ref/):
  related.tsv  절 → 관련 구절 (OpenBible.info, 표 3 이상, 절마다 상위 8). 장절은 VerseKey 숫자 (BBCCCVVV, 권 1~66), 범위는 a-b.
  names.tsv    사람 · 땅 · 낱말 한 줄씩 (영어): id · 갈래 (P 사람 / L 땅 / T 낱말) · 이름 · 원어 · 한 줄 · 이름 뜻 · 풀이 · 나오는 절
               풀이 = Easton (1897) 에서 그 사람에 맞는 뜻 갈래를 골라 4문장 · 600자 안으로. 풀이 속 장절은 [[@a-b|글]] 링크.
               나오는 절 = STEPBible TIPNR (사람 · 땅) 또는 WEB 본문 찾기 (낱말). 한국어 사전은 1.6.x 에 따로 (names_ko.tsv).
--check: 다시 만들어 지금 파일과 같은지만 봐요.
"""
import collections, html, os, re, sys, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, ".cache", "refdata")
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "ref")
BIBLE = os.path.join(ROOT, "android", "app", "src", "main", "assets", "bible")

OSIS = ["Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam", "1Kgs", "2Kgs", "1Chr", "2Chr", "Ezra", "Neh", "Esth", "Job", "Ps", "Prov",
        "Eccl", "Song", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Joel", "Amos", "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal", "Matt", "Mark",
        "Luke", "John", "Acts", "Rom", "1Cor", "2Cor", "Gal", "Eph", "Phil", "Col", "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm", "Heb", "Jas", "1Pet", "2Pet",
        "1John", "2John", "3John", "Jude", "Rev"]
STEP = ["Gen", "Exo", "Lev", "Num", "Deu", "Jos", "Jdg", "Rut", "1Sa", "2Sa", "1Ki", "2Ki", "1Ch", "2Ch", "Ezr", "Neh", "Est", "Job", "Psa", "Pro", "Ecc", "Sng", "Isa",
        "Jer", "Lam", "Ezk", "Dan", "Hos", "Jol", "Amo", "Oba", "Jon", "Mic", "Nam", "Hab", "Zep", "Hag", "Zec", "Mal", "Mat", "Mrk", "Luk", "Jhn", "Act", "Rom", "1Co",
        "2Co", "Gal", "Eph", "Php", "Col", "1Th", "2Th", "1Ti", "2Ti", "Tit", "Phm", "Heb", "Jas", "1Pe", "2Pe", "1Jn", "2Jn", "3Jn", "Jud", "Rev"]
BOOK = {b: i for i, b in enumerate(OSIS)} | {b: i for i, b in enumerate(STEP)}


def key(b, c, v): return (b + 1) * 1_000_000 + c * 1000 + v


def parse_ref(s):
    """'Gen.14.18' → VerseKey 숫자 (모르는 권이면 None)."""
    p = s.split(".")
    if len(p) != 3 or p[0] not in BOOK: return None
    try: return key(BOOK[p[0]], int(p[1]), int(p[2]))
    except ValueError: return None


def web_text():
    t = {}
    for b in range(66):
        for l in open(os.path.join(BIBLE, "web", "%02d.tsv" % (b + 1)), encoding="utf-8"):
            p = l.rstrip("\n").split("\t", 2)
            if len(p) == 3 and p[2]: t[key(b, int(p[0]), int(p[1]))] = p[2]
    return t


# ── 관련 구절 ─────────────────────────────────────────────
def related(have):
    by = collections.defaultdict(list)
    z = zipfile.ZipFile(os.path.join(RAW, "cross-references.zip"))
    name = [n for n in z.namelist() if n.endswith(".txt")][0]
    for l in z.read(name).decode("utf-8").splitlines():
        if l.startswith("From"): continue
        f, t, v = l.split("\t"); v = int(v)
        if v < 3: continue
        a = parse_ref(f); r = [parse_ref(x) for x in t.split("-")]
        if a is None or None in r or a not in have or r[0] not in have: continue
        by[a].append((-v, r[0], r[-1] if len(r) > 1 and r[-1] != r[0] else None))
    lines = []
    for a in sorted(by):
        seen, out = set(), []
        for _, s, e in sorted(by[a]):
            if s in seen: continue
            seen.add(s); out.append(f"{s}-{e}" if e else str(s))
            if len(out) == 8: break
        lines.append(f"{a}\t{','.join(out)}")
    return lines


# ── Easton · Hitchcock ────────────────────────────────────
def strip_points(s):
    """원어에서 강세 · 칸틸레이션 기호만 빼고 모음 부호는 둠."""
    return re.sub("[֑-ֽ֯׀׃]", "", s)


def ccel_entries(path):
    s = open(path, encoding="utf-8").read()
    return re.findall(r"<term[^>]*>(.*?)</term>\s*<def[^>]*>(.*?)</def>", s, re.S)


def easton():
    """headword (소문자) → [(뜻 갈래 글, 그 갈래의 장절 집합)]."""
    out = {}
    for term, body in ccel_entries(os.path.join(RAW, "easton.xml")):
        word = html.unescape(re.sub("<[^>]+>", "", term)).strip()
        body = re.sub(r"<scripCom[^>]*/>", "", body)
        # 장절 태그 → [[@a-b|글]]
        def ref(m):
            o = re.search(r'osisRef="Bible:([^"]+)"', m.group(0)); label = re.sub("<[^>]+>", "", m.group(1))
            if not o: return label
            parts = [parse_ref(x) for x in o.group(1).split("-")]
            if None in parts: return label
            if not re.match(r"^[1-3]?\s?[A-Za-z]", label):
                label = re.sub(r'.*passage="([^"]+)".*', r"\1", m.group(0).split(">")[0])  # 「7」 같은 맨숫자는 장절 전체로
            span = f"{parts[0]}-{parts[-1]}" if len(parts) > 1 and parts[-1] != parts[0] else str(parts[0])
            return f"[[@{span}|{label}]]"
        body = re.sub(r"<scripRef[^>]*>(.*?)</scripRef>", ref, body, flags=re.S)
        body = re.sub(r"<a [^>]*>(.*?)</a>", r"\1", body, flags=re.S)
        text = html.unescape(re.sub("<[^>]+>", " ", body))
        text = re.sub(r"\s+", " ", text).strip()
        senses = re.split(r"\s*\(\d+\.\)\s*", text)
        lead, rest = (senses[0], senses[1:]) if len(senses) > 1 else ("", senses)
        out.setdefault(word.lower(), []).extend((sense, {int(x) for x in re.findall(r"\[\[@(\d+)", sense)}, lead) for sense in rest if sense)
    return out


def hitchcock():
    m = {}
    for term, d in ccel_entries(os.path.join(RAW, "hitchcock.xml")):
        w = html.unescape(re.sub("<[^>]+>", "", term)).strip().lower()
        m.setdefault(w, html.unescape(re.sub("<[^>]+>", "", d)).strip())
    return m


def pick(cand, refs):
    """Easton 의 뜻 갈래 (1.)(2.)… 가운데 장절이 가장 많이 겹치는 것 (같으면 앞의 것)."""
    return max(cand, key=lambda s: (len(s[1] & refs), -cand.index(s)))


ABBR = re.compile(r"(?:\b(?:[A-Z]|[a-z]{1,3}|B\.C|A\.D|i\.e|e\.g|viz|comp|ver|ch|Heb|Gr|Lat|marg|R\.V|A\.V|St|Mt|vol)\.)$")


def trim(text, sentences=4, limit=600):
    """앞에서부터 문장 단위로 4문장 · 600자 안. 링크 안에서는 자르지 않음."""
    out, at = "", 0
    for m in re.finditer(r"[.!?][”’\"]?(?=\s+[“\"(A-Z])", text):
        end = m.end(); piece = text[at:end]
        if text[:end].count("[[") != text[:end].count("]]"): continue
        if ABBR.search(text[:m.start() + 1]): continue
        if len(out) + len(piece) > limit and out: break
        out += piece; at = end; sentences -= 1
        if sentences == 0: break
    if not out: out = text if len(text) <= limit else text[:limit].rsplit(" ", 1)[0] + "…"
    out = out.strip().rstrip(",;:")
    return out + ("." if out and out[-1] not in ".!?”’\")]" else "")


# ── 사람 · 땅 (TIPNR) ────────────────────────────────────
def tipnr():
    rows, cur, cat = [], None, None
    for l in open(os.path.join(RAW, "tipnr.txt"), encoding="utf-8"):
        l = l.rstrip("\n").rstrip("\t")
        if l.startswith("$=========="):
            cat = l.strip("$= \t").split("\t")[0]; cur = None; continue
        if cat not in ("PERSON(s)", "PLACE", "PLACE+PERSON"): continue
        f = l.split("\t")
        if cur is None and "@" in f[0] and "=" in f[0] and not l.startswith(("–", "@")):
            cur = {"uid": f[0], "name": f[0].split("@")[0], "kind": "P" if cat == "PERSON(s)" else "L", "refs": set(), "forms": set(), "orig": "", "brief": "", "short": ""}
            rows.append(cur); continue
        if cur is None: continue
        if l.startswith("– ") and not l.startswith("– Total") and len(f) >= 5:
            for r in f[4].split(";"):
                k = parse_ref(r.strip())
                if k: cur["refs"].add(k)
            for n in re.split(r"[;=,]", f[3]):
                n = re.sub(r"\b(ESV|NIV|KJV|NASB|NRSV)\b", "", n).strip()
                if n and n[0].isupper(): cur["forms"].add(n)
            if not cur["orig"] and "=" in f[2]: cur["orig"] = strip_points(f[2].split("=", 1)[1].strip())
        elif l.startswith("@Brief="): cur["brief"] = l.split("=", 1)[1].strip()
        elif l.startswith("@Short="): cur["short"] = l.split("=", 1)[1].strip()
    return [r for r in rows if r["refs"]]


# 낱말 (VERBVM): 성막 · 제사 · 절기 · 도량형 · 무리 등. Easton 표제어와 WEB 본문에서 찾을 꼴.
TERMS = {  # Easton 표제어: WEB 에서 찾을 꼴
    "Ark": ["ark of the covenant", "ark of Yahweh's covenant", "ark of God's covenant", "ark of the testimony", "ark of God", "ark of Yahweh", "ark of acacia wood"],
    "Altar": ["altar", "altars"], "Ephod": ["ephod"], "Tabernacle": ["tabernacle"], "Passover": ["passover"], "Sabbath": ["sabbath", "sabbaths"],
    "Tithe": ["tithe", "tithes", "tenth of all"], "Covenant": ["covenant"], "Circumcision": ["circumcision", "circumcised", "circumcise"], "Manna": ["manna"],
    "Mercy-seat": ["mercy seat"], "Atonement": ["atonement"], "Pentecost": ["pentecost"], "Pharisees": ["pharisee", "pharisees"], "Sadducees": ["sadducee", "sadducees"],
    "Synagogue": ["synagogue", "synagogues"], "Sanhedrim": ["sanhedrin", "council"], "Shekel": ["shekel", "shekels"], "Talent": ["talent", "talents"],
    "Cubit": ["cubit", "cubits"], "Ephah": ["ephah"], "Omer": ["omer"], "Penny": ["denarius", "denarii"], "Phylacteries": ["phylacteries"],
    "Publican": ["tax collector", "tax collectors"], "Scribes": ["scribe", "scribes"], "Levite": ["levite", "levites"], "Priest": ["priest", "priests"],
    "High priest": ["high priest"], "Prophet": ["prophet", "prophets"], "Urim": ["urim"], "Shewbread": ["show bread", "bread of the presence"],
    "Candlestick": ["lampstand", "lampstands"], "Laver": ["basin"], "Incense": ["incense"], "Burnt offering": ["burnt offering", "burnt offerings"],
    "Peace offerings": ["peace offering", "peace offerings"], "Sin-offering": ["sin offering"], "Trespass offering": ["trespass offering"],
    "Meat-offering": ["meal offering", "meal offerings"], "Drink-offering": ["drink offering", "drink offerings"], "First-fruits": ["first fruits"],
    "Feast": ["feast of tabernacles", "feast of booths", "feast of weeks", "feast of unleavened bread"], "Jubilee": ["jubilee"], "Nazarite": ["nazirite"],
    "Anoint": ["anoint", "anointed"], "Messiah": ["messiah", "christ"], "Baptism, Christian": ["baptism", "baptize", "baptized"], "Lord’s Supper": ["lord's supper"],
    "Apostle": ["apostle", "apostles"], "Elder": ["elders"], "Bishop": ["overseer", "overseers"], "Cherub": ["cherub", "cherubim"], "Seraphim": ["seraphim"],
    "Angel": ["angel", "angels"], "Satan": ["satan"], "Devil": ["devil"], "Hell": ["hell", "gehenna"], "Sheol": ["sheol"], "Hades": ["hades"], "Paradise": ["paradise"],
    "Leprosy": ["leprosy", "leper", "lepers"], "Hyssop": ["hyssop"], "Frankincense": ["frankincense"], "Myrrh": ["myrrh"], "Spikenard": ["nard"],
    "Mustard": ["mustard"], "Fig": ["fig tree", "figs"], "Olive": ["olive", "olives"], "Vine": ["vine", "vineyard"], "Threshing": ["threshing floor"],
    "Winnow": ["winnow", "winnowing"], "Sackcloth": ["sackcloth"], "Ashes": ["ashes"], "Fast": ["fasting", "fasted"], "Selah": ["selah"], "Hallelujah": ["hallelujah"],
    "Amen": ["amen"], "Hosanna": ["hosanna"], "Abba": ["abba"], "Raca": ["raca"], "Mammon": ["mammon"], "Corban": ["corban"], "Golgotha": ["golgotha"],
    "Cross": ["cross"], "Crucifixion": ["crucify", "crucified"], "Tetrarch": ["tetrarch"], "Centurion": ["centurion"], "Legion": ["legion"],
    "Praetorium": ["praetorium"], "Gentiles": ["gentile", "gentiles"], "Samaritans": ["samaritan", "samaritans"], "Proselyte": ["proselyte", "proselytes"],
    "Herodians": ["herodians"], "Zealots": ["zealot"], "Wise men": ["wise men"], "Bread": ["bread"], "Wine": ["wine"], "Leaven": ["leaven", "yeast"],
    "Salt": ["salt"], "Trumpets": ["trumpet", "trumpets"], "Harp": ["harp", "harps", "lyre"], "Lot": ["cast lots", "by lot"], "Cistern": ["cistern"],
    "Tent": ["tent", "tents"], "Camel": ["camel", "camels"], "Ass": ["donkey"], "Shepherd": ["shepherd", "shepherds"], "Sling": ["sling"],
    "Chariot": ["chariot", "chariots"], "Mantle": ["mantle"], "Sandals": ["sandal", "sandals"], "Seal": ["signet"], "Roll": ["scroll"],
    "Parable": ["parable", "parables"], "Gospel": ["good news"], "Grace": ["grace"], "Redemption": ["redeem", "redeemed", "redemption"],
    "Repentance": ["repent", "repentance"], "Justification": ["justified", "justify"], "Sanctification": ["sanctify", "sanctified"], "Faith": ["faith"],
    "Righteousness": ["righteousness"],
}
# 한 표제어에 여러 이야기가 이어 쓰인 것: 이 글부터 (Ark = 노아의 방주 · 갈대 상자 · 언약궤)
TERM_FROM = {"Ark": "The sacred ark"}
# 찾는 꼴을 늘려 처음 나오는 절이 바뀌어도 id 는 그대로 (한국어 · 영어 사전이 id 로 이어져 있어요)
KEEP_ID = {"Ark": "ark_of_the_covenant_2025022_t"}
# 절마다 낱말 칩이 너무 흔하면 (몇 백 절) 시끄러워요: 사람 · 땅을 먼저, 낱말은 남는 자리에.


def main(check=False):
    web = web_text()
    files = {}
    files["related.tsv"] = "# 관련 구절 · OpenBible.info (CC BY) · 표 3 이상 · 절마다 상위 8 · scripts/build_refdata.py\n" + "\n".join(related(set(web))) + "\n"

    ea, hi = easton(), hitchcock()
    rows = []
    for t in tipnr():
        forms = [t["name"]] + sorted(t["forms"] - {t["name"]})
        cand = next((ea[f.lower()] for f in forms if f.lower() in ea), [])
        body = ""
        if cand:
            best = pick(cand, t["refs"])
            if len(cand) == 1 or best[1] & t["refs"]: body = trim(best[0])
        if len(re.sub(r"\[\[@[^|]*\|[^\]]*]]", "", body).strip(" .,;()")) < 30: body = ""  # 장절만 있는 풀이는 버리고 TIPNR 한 줄로
        meaning = next((hi[f.lower()] for f in forms if f.lower() in hi), "")
        for _ in range(2):  # 「same as Zachariah」 → 그 이름의 뜻
            m = re.match(r"(?:the )?same as ([A-Za-z-]+)", meaning)
            if m: meaning = hi.get(m.group(1).lower(), "")
        rows.append([t["kind"], t["name"], t["orig"], t["brief"] or t["short"], meaning, body or t["short"], sorted(t["refs"]), None])
    # 낱말: WEB 본문에서 찾기
    for head, pats in TERMS.items():
        cand = ea.get(head.lower())
        if not cand: print("no Easton entry:", head, file=sys.stderr); continue
        rx = re.compile(r"\b(" + "|".join(re.escape(p) for p in pats) + r")\b", re.I)
        refs = sorted(k for k, v in web.items() if rx.search(v.replace("’", "'")))
        if not refs: print("no verse:", head, file=sys.stderr); continue
        name = {"Ark": "Ark of the covenant", "Candlestick": "Lampstand", "Meat-offering": "Meal offering", "Baptism, Christian": "Baptism", "Sanhedrim": "Sanhedrin",
                "Penny": "Denarius", "Publican": "Tax collector", "Laver": "Basin", "Shewbread": "Showbread", "Spikenard": "Nard", "Nazarite": "Nazirite", "Ass": "Donkey",
                "Roll": "Scroll", "Fast": "Fasting", "Fig": "Fig tree", "Lot": "Lots", "Cherub": "Cherubim", "Urim": "Urim and Thummim", "Feast": "Feasts"}.get(head, head.split(",")[0].replace("-", " ").replace("’", "'"))
        text = pick(cand, set(refs))[0]
        if head in TERM_FROM: text = text[text.index(TERM_FROM[head]):]
        rows.append(["T", name, "", "", "", trim(text), refs, KEEP_ID.get(head)])
    # id: 이름_처음 장절 (같은 이름 사람이 여럿이어도 겹치지 않게)
    out, ids = [], set()
    # 영어 풀이 (1.6): 검사한 한국어 사전을 옮긴 data/dict/en.tsv (검사 scripts/check_endict.py) 가 있으면 그것을, 없으면 Easton
    en = {}
    src_en = os.path.join(ROOT, "data", "dict", "en.tsv")
    if os.path.exists(src_en):
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        import check_endict as E
        def link_en(paren):
            book = [None]
            def one(m):
                if m.group(1): book[0] = E.BOOK[m.group(1)]
                if book[0] is None: return m.group(0)
                c, v = int(m.group(2)), int(m.group(3)); a = E.key(book[0], c, v)
                e = E.key(book[0], int(m.group(4)), int(m.group(5))) if m.group(5) else E.key(book[0], c, int(m.group(4))) if m.group(4) else a
                return f"[[@{a}-{e}|{m.group(0)}]]" if e != a else f"[[@{a}|{m.group(0)}]]"
            return "(" + E.REF.sub(one, paren.group(1)) + ")"
        for l in open(src_en, encoding="utf-8"):
            if not l.strip() or l.startswith("#"): continue
            r = l.rstrip("\n").split("\t")
            en[r[0]] = (r[2], r[3], re.sub(r"\(([^()]*)\)", link_en, r[4]))
    for kind, name, orig, brief, meaning, body, refs, keep in sorted(rows, key=lambda r: (r[1].lower(), r[6][0])):
        i = keep or re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_") + "_" + str(refs[0]) + ("_t" if kind == "T" else "")
        if i in en: meaning, brief, body = en[i]
        assert i not in ids, i; ids.add(i)
        clean = lambda s: s.replace("\t", " ").replace("\n", " ").strip()
        out.append("\t".join([i, kind, clean(name), clean(orig), clean(brief), clean(meaning), clean(body), " ".join(map(str, refs))]))
    files["names.tsv"] = ("# 사전 (영어): id · 갈래 P/L/T · 이름 · 원어 · 한 줄 · 이름 뜻 · 풀이 · 나오는 절 (VerseKey)\n"
                          "# 풀이 data/dict/en.tsv (한국어 사전을 옮긴 글), 없는 몇 항목만 Easton's Bible Dictionary (1897, 공개 도메인) · 이름 뜻 Hitchcock (1869, 공개 도메인) · 사람 · 땅과 나오는 절 STEPBible TIPNR (CC BY 4.0) · scripts/build_refdata.py\n"
                          + "\n".join(out) + "\n")

    # 한국어 사전 (1.6.x): data/dict/ko.tsv (우리가 쓰고 scripts/check_kodict.py 로 검사한 글) → 풀이 속 (창 14:18) 을 링크로
    src = os.path.join(ROOT, "data", "dict", "ko.tsv")
    if os.path.exists(src):
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        import check_kodict as K
        def link(paren):
            book = [None]
            def one(m):
                if m.group(1): book[0] = K.BOOK[m.group(1)]
                if book[0] is None: return m.group(0)
                c, v = int(m.group(2)), int(m.group(3)); a = K.key(book[0], c, v)
                e = K.key(book[0], int(m.group(4)), int(m.group(5))) if m.group(5) else K.key(book[0], c, int(m.group(4))) if m.group(4) else a
                return f"[[@{a}-{e}|{m.group(0)}]]" if e != a else f"[[@{a}|{m.group(0)}]]"
            return "(" + K.REF.sub(one, paren.group(1)) + ")"
        ko = []
        for l in open(src, encoding="utf-8"):
            if not l.strip() or l.startswith("#"): continue
            r = l.rstrip("\n").split("\t")
            r[4] = re.sub(r"\(([^()]*)\)", link, r[4]); ko.append("\t".join(r))
        files["names_ko.tsv"] = "# 사전 (한국어): id · 이름 · 이름 뜻 · 한 줄 · 풀이. 원본 data/dict/ko.tsv (검사 scripts/check_kodict.py) · scripts/build_refdata.py\n" + "\n".join(ko) + "\n"

    os.makedirs(OUT, exist_ok=True)
    bad = []
    for n, s in files.items():
        p = os.path.join(OUT, n)
        if check:
            if not os.path.exists(p) or open(p, encoding="utf-8").read() != s: bad.append(n)
        else:
            open(p, "w", encoding="utf-8").write(s); print(n, len(s.encode()) // 1024, "KB", s.count("\n"), "lines")
    if bad: sys.exit("out of date: " + ", ".join(bad))


if __name__ == "__main__":
    main("--check" in sys.argv)
