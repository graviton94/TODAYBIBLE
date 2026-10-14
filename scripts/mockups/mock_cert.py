"""필사 완성 증서 시안 (1.5 C5): 앱 글꼴 · 판화로 그린 두 가지 안. 출력: docs/mockups/"""
import sys, random
from PIL import Image, ImageDraw, ImageFont, ImageOps, ImageFilter, ImageEnhance

R = '/home/user/TODAYBIBLE/android/app/src/main'
OUT = '/home/user/TODAYBIBLE/docs/mockups'
F = lambda n, s: ImageFont.truetype(f'{R}/res/font/{n}.ttf', s)

W, H = 1240, 1754  # A4 @150dpi
GILT = (176, 137, 64); GILT_TEXT = (150, 112, 44); INK = (31, 26, 21); SOFT = (110, 98, 84)
PAPER = (243, 238, 229)
D_LEAF = (22, 18, 14); D_INK = (236, 228, 214); D_GILT = (201, 164, 92); D_SOFT = (160, 148, 130)


def grain(img, line, dark=False):
    d = ImageDraw.Draw(img, 'RGBA')
    a = 10 if not dark else 14
    for y in range(0, img.height, 6): d.line([(0, y), (img.width, y)], fill=line + (a,), width=1)
    for x in range(30, img.width, 58): d.line([(x, 0), (x, img.height)], fill=line + (a // 2,), width=1)
    # 가장자리 그늘
    v = Image.new('L', img.size, 0); vd = ImageDraw.Draw(v)
    vd.ellipse([-W * 0.25, -H * 0.15, W * 1.25, H * 1.15], fill=255)
    v = v.filter(ImageFilter.GaussianBlur(160))
    shade = Image.new('RGB', img.size, (0, 0, 0))
    img.paste(Image.composite(img, Image.blend(img, shade, 0.22 if dark else 0.10), v))


def center(d, y, text, font, fill, tracking=0):
    if tracking:
        widths = [d.textlength(ch, font=font) for ch in text]
        total = sum(widths) + tracking * (len(text) - 1)
        x = (W - total) / 2
        for ch, w in zip(text, widths): d.text((x, y), ch, font=font, fill=fill); x += w + tracking
        return
    w = d.textlength(text, font=font); d.text(((W - w) / 2, y), text, font=font, fill=fill)


def plate(id_, box, sepia=True):
    im = Image.open(f'{R}/assets/plates/{id_}.jpg').convert('L')
    im = ImageOps.fit(im, (box[2] - box[0], box[3] - box[1]), centering=(0.5, 0.35))
    if sepia: im = ImageOps.colorize(ImageEnhance.Contrast(im).enhance(1.05), (28, 22, 16), (238, 228, 208))
    return im.convert('RGB')


def seal(img, cx, cy, r, color, bg, lines=('COMPLETUM', 'MMXXVII')):
    d = ImageDraw.Draw(img)
    layer = Image.new('RGBA', (r * 2 + 40, r * 2 + 40), (0, 0, 0, 0)); ld = ImageDraw.Draw(layer)
    c = r + 20
    ld.ellipse([c - r, c - r, c + r, c + r], outline=color, width=5)
    ld.ellipse([c - r + 12, c - r + 12, c + r - 12, c + r - 12], outline=color, width=2)
    f1 = F('caps', int(r * 0.205)); f2 = F('caps', int(r * 0.17))
    for i, (t, f) in enumerate(zip(lines, (f1, f2))):
        w = ld.textlength(t, font=f); ld.text((c - w / 2, c - r * 0.30 + i * r * 0.36), t, font=f, fill=color)
    layer = layer.rotate(-12, resample=Image.BICUBIC)
    img.paste(layer, (cx - c, cy - c), layer)


def rule(d, y, x0, x1, color, w=2):
    d.line([(x0, y), (x1, y)], fill=color, width=w)


def method_bar(d, y, parts, colors, label_font, fill, soft, x0=260, x1=W - 260):
    total = sum(v for _, v in parts); x = x0
    for (name, v), col in zip(parts, colors):
        w = (x1 - x0) * v / total
        d.rectangle([x, y, x + w - 6, y + 10], fill=col)
        t = f'{name} {round(v * 100 / total)}%'
        d.text((x, y + 22), t, font=label_font, fill=fill)
        x += w


def variant_a():
    """A · 종이: 권 하나 다 쓰기 (창세기). 위에 그 권의 판화 창, 금빛 두 줄 테."""
    img = Image.new('RGB', (W, H), PAPER); grain(img, INK)
    d = ImageDraw.Draw(img)
    m = 70
    d.rectangle([m, m, W - m, H - m], outline=GILT, width=4)
    d.rectangle([m + 16, m + 16, W - m - 16, H - m - 16], outline=GILT, width=1)
    # 판화 창 (위가 둥근 아치)
    bx = [250, 170, W - 250, 720]
    p = plate('creation', bx)
    mask = Image.new('L', p.size, 0); md = ImageDraw.Draw(mask)
    pw = p.size[0]; md.rectangle([0, pw // 2, pw, p.size[1]], fill=255); md.ellipse([0, 0, pw, pw], fill=255)
    img.paste(p, (bx[0], bx[1]), mask)
    d.arc([bx[0] - 10, bx[1] - 10, bx[2] + 10, bx[1] + pw + 10], 180, 360, fill=GILT, width=3)
    d.line([(bx[0] - 10, bx[1] + pw // 2), (bx[0] - 10, bx[3] + 10)], fill=GILT, width=3)
    d.line([(bx[2] + 10, bx[1] + pw // 2), (bx[2] + 10, bx[3] + 10)], fill=GILT, width=3)
    d.line([(bx[0] - 10, bx[3] + 10), (bx[2] + 10, bx[3] + 10)], fill=GILT, width=3)
    center(d, 735, '〈빛이 있으라〉 귀스타브 도레 · 1866', F('serif_kr_medium', 24), SOFT)
    center(d, 815, 'CERTIFICATIO · LIBER GENESIS', F('caps', 30), GILT_TEXT, tracking=6)
    center(d, 860, '필사 완성 증서', F('serif_kr_semibold', 74), INK)
    rule(d, 965, 470, W - 470, GILT, 2)
    body = F('serif_kr_medium', 36)
    for i, l in enumerate(['김은혜 님은', '창세기 50장 1,533절을', '2026년 10월 2일부터 2027년 3월 14일까지', '소리 내어 읽고, 치고, 손으로 써서', '한 자 한 자 옮겨 적었습니다.']):
        center(d, 1000 + i * 62, l, body, INK)
    method_bar(d, 1340, [('낭독', 0.34), ('타자', 0.51), ('손글씨', 0.15)], [GILT, INK, SOFT], F('serif_kr_medium', 24), SOFT, INK)
    center(d, 1420, '“태초에 하나님이 천지를 창조하시니라”  창세기 1:1', F('serif_kr_medium', 28), SOFT)
    d.text((170, 1530), '2027. 3. 14.', font=F('display', 40), fill=INK)
    d.text((170, 1585), '하루의 성경 · BIBLE BY HAND', font=F('serif_kr_medium', 22), fill=SOFT)
    seal(img, W - 260, 1585, 100, GILT, PAPER, ('COMPLETUM', 'GENESIS'))
    return img


def variant_b():
    """B · 흑단 금박: 66권 다 쓰기. 판화 넷이 모서리에, 가운데는 글만."""
    img = Image.new('RGB', (W, H), D_LEAF); grain(img, D_INK, dark=True)
    d = ImageDraw.Draw(img)
    m = 60
    d.rectangle([m, m, W - m, H - m], outline=D_GILT, width=3)
    d.rectangle([m + 14, m + 14, W - m - 14, H - m - 14], outline=D_GILT, width=1)
    # 위 띠: 판화 넷 (창조 · 홍해 · 십자가 · 승천) 세피아, 금선으로 나눔
    ids = ['creation', 'moses_sea', 'cross', 'ascension']
    try:
        import os
        avail = [i for i in ids if os.path.exists(f'{R}/assets/plates/{i}.jpg')]
        if len(avail) < 4:
            allp = sorted(os.listdir(f'{R}/assets/plates')); avail = (avail + [a[:-4] for a in allp if a.endswith('.jpg')])[:4]
    except Exception: avail = ids
    top, h = 130, 330; x0, x1 = 130, W - 130; gap = 14; cw = (x1 - x0 - gap * 3) // 4
    for i, pid in enumerate(avail):
        bx = [x0 + i * (cw + gap), top, x0 + i * (cw + gap) + cw, top + h]
        img.paste(ImageEnhance.Brightness(plate(pid, bx)).enhance(0.85), (bx[0], bx[1]))
        d.rectangle(bx, outline=D_GILT, width=2)
    center(d, 540, 'CERTIFICATIO · BIBLIA SACRA', F('caps', 30), D_GILT, tracking=6)
    center(d, 590, '성경 66권 필사 완성', F('serif_kr_semibold', 72), D_INK)
    rule(d, 700, 470, W - 470, D_GILT, 2)
    body = F('serif_kr_medium', 36)
    for i, l in enumerate(['김은혜 님은', '구약 39권 · 신약 27권, 1,189장 30,929절을', '2026년 10월 2일부터 2029년 12월 24일까지', '날마다 한 장씩 옮겨 적어', '자기 손으로 성경 한 권을 완성하였습니다.']):
        center(d, 750 + i * 64, l, body, D_INK)
    method_bar(d, 1110, [('낭독', 0.30), ('타자', 0.48), ('손글씨', 0.22)], [D_GILT, D_INK, D_SOFT], F('serif_kr_medium', 24), D_SOFT, D_INK)
    # 숫자 셋
    nums = [('1,184', '쓴 날'), ('156', '화첩의 판화'), ('412 m', '두루마리 길이')]
    for i, (n, lab) in enumerate(nums):
        cx = W // 2 + (i - 1) * 360
        w = d.textlength(n, font=F('display', 64)); d.text((cx - w / 2, 1200), n, font=F('display', 64), fill=D_GILT)
        w = d.textlength(lab, font=F('serif_kr_medium', 24)); d.text((cx - w / 2, 1285), lab, font=F('serif_kr_medium', 24), fill=D_SOFT)
    center(d, 1385, '“주의 말씀은 내 발에 등이요 내 길에 빛이니이다”  시편 119:105', F('serif_kr_medium', 28), D_SOFT)
    center(d, 1440, '사랑하는 어머니께 바칩니다', F('serif_kr_medium', 26), D_SOFT)
    d.text((150, 1540), '2029. 12. 24.', font=F('display', 40), fill=D_INK)
    d.text((150, 1595), '하루의 성경 · BIBLE BY HAND', font=F('serif_kr_medium', 22), fill=D_SOFT)
    seal(img, W - 260, 1570, 115, D_GILT, D_LEAF, ('COMPLETUM', 'OMNIA'))
    return img


def card(src, path):
    """나누기 카드 (세로 1080×1350) 로 줄인 모습: 증서 위쪽을 잘라 카드 틀에."""
    c = ImageOps.fit(src, (1080, 1350), centering=(0.5, 0.42))
    c.save(path, optimize=True)


import os
os.makedirs(OUT, exist_ok=True)
a = variant_a(); a.save(f'{OUT}/certificate_A_book.png', optimize=True)
b = variant_b(); b.save(f'{OUT}/certificate_B_bible.png', optimize=True)
# 한눈에 비교 (둘 나란히)
both = Image.new('RGB', (W + 40 + W, H), (200, 196, 188)); both.paste(a, (0, 0)); both.paste(b, (W + 40, 0))
both.resize((both.width // 2, both.height // 2), Image.LANCZOS).save(f'{OUT}/certificate_compare.png', optimize=True)
print('ok')
