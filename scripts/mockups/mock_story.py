"""역사 속의 성경 화면 시안 (판화 크게 보기 + 이야기). 출력: docs/mockups/history_story_phone.png"""
from PIL import Image, ImageDraw, ImageFont, ImageOps, ImageEnhance

R = '/home/user/TODAYBIBLE/android/app/src/main'
F = lambda n, s: ImageFont.truetype(f'{R}/res/font/{n}.ttf', s)
W, H = 1080, 2400
LEAF = (22, 18, 14); INK = (236, 228, 214); GILT = (201, 164, 92); SOFT = (160, 148, 130); HAIR = (70, 62, 52)
img = Image.new('RGB', (W, H), LEAF); d = ImageDraw.Draw(img)
PAD = 72


def wrap(text, font, width):
    """한국어: 어절 단위로 줄바꿈."""
    words = text.split(' '); lines = []; cur = ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if d.textlength(t, font=font) <= width: cur = t
        else: lines.append(cur); cur = w
    if cur: lines.append(cur)
    return lines


def para(y, text, font, fill, lh):
    for l in wrap(text, font, W - 2 * PAD): d.text((PAD, y), l, font=font, fill=fill); y += lh
    return y


# 위: 판화 (세피아, 아래로 어둠에 녹음)
pl = Image.open(f'{R}/assets/plates/creation.jpg').convert('L')
pl = ImageOps.fit(pl, (W, 980), centering=(0.5, 0.3))
pl = ImageOps.colorize(ImageEnhance.Contrast(pl).enhance(1.05), (28, 22, 16), (238, 228, 208)).convert('RGB')
img.paste(pl, (0, 0))
fade = Image.new('L', (W, 980), 0); fd = ImageDraw.Draw(fade)
for y in range(980): fd.line([(0, y), (W, y)], fill=int(255 * min(1, max(0, (y - 300) / 560)) ** 1.15))
img.paste(Image.new('RGB', (W, 980), LEAF), (0, 0), fade)
d.line([(PAD, 140), (PAD + 44, 140)], fill=INK, width=4); d.line([(PAD, 140), (PAD + 18, 122)], fill=INK, width=4); d.line([(PAD, 140), (PAD + 18, 158)], fill=INK, width=4)
d.text((PAD, 560), 'GENESIS · I', font=F('caps', 30), fill=GILT)
d.text((PAD, 605), '빛이 있으라', font=F('serif_kr_semibold', 76), fill=INK)
d.text((PAD, 712), '귀스타브 도레 · 『성경』 판화, 1866', font=F('serif_kr_medium', 30), fill=SOFT)

y = 860
sections = [
    ('PICTURA', '그림', '귀스타브 도레 (1832–1883), 『성경』(La Sainte Bible, 프랑스 투르의 알프레드 맘 출판사, 1866). 목판화: 도레가 나무판 위에 바로 그리면 판각공들이 그 선을 새겼어요.'),
    ('SCAENA', '장면', '“하나님이 가라사대 빛이 있으라 하시매 빛이 있었고” (창세기 1:3). 도레는 사람도, 하나님의 모습도 그리지 않고 구름을 가르고 쏟아지는 빛만 그렸어요.'),
    ('HISTORIA', '역사', '도레의 성경은 큰 판형 두 권에 판화 241점으로 나왔고, 곧 영어판이 나오는 등 여러 나라 말로 옮겨졌어요. 사진 인쇄가 아직 흔하지 않던 때라, 19세기 많은 가정이 이 판화로 성경 장면을 처음 보았어요.'),
]
for i, (lat, ko, body) in enumerate(sections):
    d.line([(PAD, y), (W - PAD, y)], fill=INK if i == 0 else HAIR, width=2)
    y += 26
    d.text((PAD, y), lat, font=F('caps', 30), fill=INK)
    lw = d.textlength(lat, font=F('caps', 30))
    d.text((PAD + lw + 22, y + 2), ko, font=F('serif_kr_medium', 30), fill=SOFT)
    r = ['I', 'II', 'III'][i]; rw = d.textlength(r, font=F('display', 40)); d.text((W - PAD - rw, y - 6), r, font=F('display', 40), fill=GILT)
    y += 70
    y = para(y, body, F('serif_kr_medium', 36), INK, 62) + 34

# 아래: 출처 · 이 장 쓰기
d.text((PAD, y + 10), '출처: Wikimedia Commons · 공개 도메인', font=F('serif_kr_medium', 26), fill=SOFT)
by = H - 250
d.rectangle([PAD, by, W - PAD, by + 120], fill=INK)
t = '이 장 옮겨 쓰기 · 창세기 1장'; tw = d.textlength(t, font=F('serif_kr_medium', 38))
d.text(((W - tw) / 2, by + 36), t, font=F('serif_kr_medium', 38), fill=LEAF)
img.save('/home/user/TODAYBIBLE/docs/mockups/history_story_phone.png', optimize=True)
print('ok', y)
