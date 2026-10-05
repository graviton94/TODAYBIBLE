#!/usr/bin/env python3
"""디자인 토큰 · 문자열 · 본문 자산을 만든다.

    python3 scripts/generate.py           # 쓰기
    python3 scripts/generate.py --check   # 커밋된 결과와 다르면 실패 (CI)

- design/tokens.json  → android/app/.../design/Tokens.kt
- design/strings.json → android/app/src/main/res/values{,-ko}/strings.xml
- data/bible/<id>.json → android/app/src/main/assets/bible/<id>/<NN>.tsv (권마다 "장\\t절\\t본문")
"""
import json, os, sys
from xml.sax.saxutils import escape

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "android", "app", "src", "main")
PKG = os.path.join(APP, "java", "io", "github", "graviton94", "todaybible", "design")

def kt_color(hexs):
    h = hexs.lstrip("#")
    if len(h) == 6: h = "FF" + h
    else: h = h[6:8] + h[0:6]
    return f"Color(0x{h.upper()})"

def tokens():
    t = json.load(open(os.path.join(ROOT, "design", "tokens.json"), encoding="utf-8"))
    L = ["// 자동 생성: scripts/generate.py (design/tokens.json). 손으로 고치지 말 것.",
         "package io.github.graviton94.todaybible.design", "",
         "import androidx.compose.ui.graphics.Color", "import androidx.compose.ui.unit.dp", "import androidx.compose.ui.unit.sp", "",
         "/** 한 테마의 재료 색. */",
         "data class Palette(" + ", ".join(f"val {k}: Color" for k in t["color"]["light"]) + ")", "",
         "object Tokens {"]
    for mode in t["color"]:
        c = t["color"][mode]
        L.append(f"    val {mode} = Palette(" + ", ".join(f"{k} = {kt_color(v)}" for k, v in c.items()) + ")")
    for group, unit in (("space", "dp"), ("radius", "dp"), ("stroke", "dp"), ("size", "dp"), ("text", "sp")):
        L.append(f"    object {group.capitalize()} {{")
        for k, v in t[group].items(): L.append(f"        val {k} = {v}.{unit}")
        L.append("    }")
    L.append("    /** 표지 가죽 (나의 성경 꾸미기). */")
    L.append("    object Covers {")
    for k, v in t.get("covers", {}).items(): L.append(f"        val {k} = {kt_color(v)}")
    L.append("    }")
    L.append("    object Motion {")
    for k, v in t["motion"].items(): L.append(f"        const val {k} = {v}" + ("f" if isinstance(v, float) else ""))
    L.append("    }")
    for group in ("ratio", "alpha", "leading", "tracking", "px"):
        L.append(f"    object {group.capitalize()} {{")
        for k, v in t[group].items(): L.append(f"        const val {k} = {float(v)}f")
        L.append("    }")
    L.append("}")
    return {os.path.join(PKG, "Tokens.kt"): "\n".join(L) + "\n"}

def strings():
    s = json.load(open(os.path.join(ROOT, "design", "strings.json"), encoding="utf-8"))
    out = {}
    for lang, folder in (("en", "values"), ("ko", "values-ko")):
        L = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- 자동 생성: scripts/generate.py (design/strings.json) -->", "<resources>"]
        for k, v in s.items():
            text = v[lang] if isinstance(v, dict) else v
            text = escape(text).replace("'", "\\'").replace("\n", "\\n")
            L.append(f'    <string name="{k}">{text}</string>')
        L.append("</resources>")
        out[os.path.join(APP, "res", folder, "strings.xml")] = "\n".join(L) + "\n"
    return out

def bible():
    out = {}
    for tr in ("krv", "kjv", "web"):
        d = json.load(open(os.path.join(ROOT, "data", "bible", f"{tr}.json"), encoding="utf-8"))
        for i, b in enumerate(d["books"]):
            lines = []
            for c, ch in enumerate(b["chapters"], 1):
                # 장 끝의 빈 절(번역에 없는 마지막 절)은 버림
                while ch and not ch[-1]: ch = ch[:-1]
                for v, text in enumerate(ch, 1): lines.append(f"{c}\t{v}\t{text}")
            out[os.path.join(APP, "assets", "bible", tr, f"{i + 1:02d}.tsv")] = "\n".join(lines) + "\n"
    return out

def main():
    check = "--check" in sys.argv
    files = {**tokens(), **strings(), **bible()}
    bad = []
    for path, text in files.items():
        old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        if old == text: continue
        if check: bad.append(os.path.relpath(path, ROOT)); continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        open(path, "w", encoding="utf-8").write(text)
    if bad: sys.exit("생성 결과가 다릅니다. python3 scripts/generate.py 를 실행하세요:\n  " + "\n  ".join(bad[:10]))
    print(f"ok ({len(files)} files)")

if __name__ == "__main__":
    main()
