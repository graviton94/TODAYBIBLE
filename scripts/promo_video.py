"""홍보 영상 (1.7): 실제 앱 화면 (스토어 캡처 st_*) · 도레 판화 · 앱의 낭독 음원 (시편 23편) · 직접 합성한 바탕음 · 앱 효과음.
가로 1920x1080 (Play 홍보 동영상 · YouTube) 과 세로 1080x1920 (쇼츠 · 릴스 · 카카오톡), 한국어 · 영어.

  python3 -I scripts/promo_video.py <캡처 폴더 (st_ko_1_intro.png …)>   → docs/store/promo/promo_{ko,en}_{16x9,9x16}.mp4

소리: 낭독은 앱에 든 기도문 음원 (Supertonic 3, OpenRAIL-M · 앱 크레딧과 같음), 바탕음 · 종소리는 직접 합성 (저작권 없음).
"""
import os, sys, math, subprocess, wave, struct
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps, ImageEnhance, ImageFilter

R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
F = R + '/.cache/'
A = R + '/android/app/src/main'
OUT = R + '/docs/store/promo'
SRC = sys.argv[1] if len(sys.argv) > 1 else R + '/.shots'
FPS = 30
SR = 44100
DARK = (0x17, 0x11, 0x0C); INK = (0xEA, 0xDF, 0xC8); MUTED = (0xA3, 0x98, 0x86); GILT = (0xC7, 0xA3, 0x5D)
PAPER = (0xF4, 0xEF, 0xE6); PINK = (0x1F, 0x1A, 0x15); PGILT = (0x9A, 0x6B, 0x1F); HAIR = (0xD6, 0xCF, 0xC2)


def font(name, size, wght=None):
    f = ImageFont.truetype(F + name, size)
    if wght:
        try: f.set_variation_by_axes([wght])
        except Exception: pass
    return f


TEXT = {
    'ko': dict(caps='BIBLIA · MANU SCRIPTA', title='하루의 성경', tag='손으로 옮겨 쓰는, 역사 속의 성경',
               caps2=['HODIE', 'SCRIBERE · MANU · VOCE', 'HISTORIA', 'CONCORDANTIA', 'ORATIO'],
               lines=['하루 한 장, 오늘 쓸 자리', '세 필사본: 타자 · 손글씨 · 낭독', '판화마다 그림 · 장면 · 역사', '관련 구절 · 성경 사전을 그 자리에서', '기도문마다 낭독, 들으며 기도해요'],
               end='Google Play에서 만나요', serif=('NotoSerifKR[wght].ttf', 600), body=('NotoSerifKR[wght].ttf', 500), vo='ko_m5'),
    'en': dict(caps='BIBLIA · MANU SCRIPTA', title='Bible by Hand', tag='Copy the Bible by hand, with history beside you',
               caps2=['HODIE', 'SCRIBERE · MANU · VOCE', 'HISTORIA', 'CONCORDANTIA', 'ORATIO'],
               lines=['One chapter a day', 'Three copies: typed, handwritten, read aloud', 'Every engraving tells its history', 'Related verses and a dictionary, right there', 'Every prayer narrated'],
               end='Get it on Google Play', serif=('CormorantGaramond[wght].ttf', 600), body=('CormorantGaramond[wght].ttf', 500), vo='en_m5'),
}

# 장면 (초): 시작 · 길이 · 화면들
SCENES = [
    ('open', 0.0, 4.6, None),
    ('shot', 4.6, 4.4, ['today']),
    ('shot', 9.0, 8.2, ['type', 'hand', 'aloud']),
    ('shot', 17.2, 4.6, ['story']),
    ('shot', 21.8, 4.4, ['related']),
    ('shot', 26.2, 4.6, ['prayer']),
    ('end', 30.8, 4.4, None),
]
DUR = 35.2
NAMES = ['intro', 'today', 'type', 'hand', 'aloud', 'story', 'related', 'prayer']


def ease(t): t = max(0.0, min(1.0, t)); return t * t * (3 - 2 * t)


def capture(lang, name):
    i = NAMES.index(name) + 1
    p = f'{SRC}/st_{lang}_{i}_{name}.png'
    return Image.open(p).convert('RGB') if os.path.exists(p) else None


def phone(img, h):
    """캡처를 둥근 모서리 · 가는 테 · 옅은 그림자의 휴대폰 화면으로."""
    w = round(img.width * h / img.height); s = img.resize((w, h), Image.LANCZOS)
    pad = 40; out = Image.new('RGBA', (w + pad * 2, h + pad * 2), (0, 0, 0, 0))
    sh = Image.new('L', out.size, 0); ImageDraw.Draw(sh).rounded_rectangle([pad, pad + 12, pad + w, pad + h + 12], radius=44, fill=110)
    out.putalpha(sh.filter(ImageFilter.GaussianBlur(22)))
    m = Image.new('L', (w, h), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, w - 1, h - 1], radius=40, fill=255)
    out.paste(s, (pad, pad), m)
    ImageDraw.Draw(out).rounded_rectangle([pad - 2, pad - 2, pad + w + 1, pad + h + 1], radius=42, outline=(70, 62, 52, 255), width=3)
    return out


def plate_img(id_):
    for e in ('webp', 'jpg'):
        p = f'{A}/assets/plates/{id_}.{e}'
        if os.path.exists(p): return Image.open(p).convert('L')
    raise FileNotFoundError(id_)


def sepia(g, lo=DARK, hi=INK): return ImageOps.colorize(ImageEnhance.Contrast(g).enhance(1.05), lo, hi).convert('RGB')


def text_center(d, cx, y, s, f, fill):
    w = d.textlength(s, font=f); d.text((cx - w / 2, y), s, font=f, fill=fill)


def wrap(d, text, f, maxw):
    words = text.split(' '); lines = []; cur = ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if d.textlength(t, font=f) <= maxw: cur = t
        else: lines.append(cur); cur = w
    lines.append(cur); return lines


class Film:
    def __init__(self, lang, W, H):
        self.lang, self.W, self.H = lang, W, H
        self.T = TEXT[lang]; self.vert = H > W
        self.creation = plate_img('creation')
        ph = int(H * (0.72 if self.vert else 0.86))
        self.shots = {n: (phone(c, ph) if (c := capture(lang, n)) is not None else None) for n in NAMES[1:]}
        self.icon = Image.open(R + '/docs/store/graphics/icon_512.png').convert('RGBA')
        k = 1.0 if not self.vert else 0.9
        self.fcaps = font('Cinzel[wght].ttf', int(30 * k), 600)
        self.ftitle = font(self.T['serif'][0], int((96 if lang == 'ko' else 110) * k), self.T['serif'][1])
        self.fline = font(self.T['serif'][0], int((54 if lang == 'ko' else 62) * k), self.T['serif'][1])
        self.fbody = font(self.T['body'][0], int((34 if lang == 'ko' else 40) * k), self.T['body'][1])

    def engraving(self, g, t, dim=0.0):
        """판화 한 점을 화면 가득, 천천히 다가가며 (t 0..1)."""
        W, H = self.W, self.H; sc = 1.12 - 0.08 * ease(t)
        base = ImageOps.fit(g, (int(W * sc), int(H * sc)), centering=(0.5, 0.35))
        x = (base.width - W) // 2; y = (base.height - H) // 2
        im = sepia(base.crop((x, y, x + W, y + H)))
        if dim: im = Image.blend(im, Image.new('RGB', im.size, DARK), dim)
        return im

    def frame(self, t):
        W, H = self.W, self.H; T = self.T
        for kind, st, du, names in SCENES:
            if st <= t < st + du: break
        u = (t - st) / du
        if kind == 'open':
            im = self.engraving(self.creation, u, dim=0.25)
            g = Image.new('L', (W, H), 0); gd = ImageDraw.Draw(g)
            for yy in range(H): gd.line([(0, yy), (W, yy)], fill=int(255 * max(0, (yy / H - 0.35) / 0.65) ** 1.3))
            im.paste(Image.new('RGB', (W, H), DARK), (0, 0), g)
            d = ImageDraw.Draw(im); a = ease((t - 0.6) / 1.2)
            col = lambda c: tuple(int(DARK[i] + (c[i] - DARK[i]) * a) for i in range(3))
            y0 = int(H * (0.62 if self.vert else 0.56))
            text_center(d, W / 2, y0, T['caps'], self.fcaps, col(GILT))
            text_center(d, W / 2, y0 + 56, T['title'], self.ftitle, col(INK))
            text_center(d, W / 2, y0 + 56 + self.ftitle.size + 40, T['tag'], self.fbody, col(MUTED))
            return self.fade(im, t, st, du, first=True)
        if kind == 'end':
            im = self.engraving(self.creation, 1.0, dim=0.82)
            d = ImageDraw.Draw(im)
            ic = self.icon.resize((int(220 * (1.1 if self.vert else 1)),) * 2, Image.LANCZOS)
            m = Image.new('L', ic.size, 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, ic.width - 1, ic.height - 1], radius=int(ic.width * 0.22), fill=255)
            cy = int(H * 0.36)
            im.paste(ic, ((W - ic.width) // 2, cy - ic.height // 2), m)
            text_center(d, W / 2, cy + ic.height // 2 + 40, T['title'], self.ftitle, INK)
            text_center(d, W / 2, cy + ic.height // 2 + 90 + self.ftitle.size, T['end'], self.fbody, GILT)
            text_center(d, W / 2, cy + ic.height // 2 + 170 + self.ftitle.size, T['caps'], self.fcaps, MUTED)
            return self.fade(im, t, st, du, last=True)
        # 앱 화면 장면: 종이 바탕 · 금빛 머리글 · 한 줄 · 휴대폰
        idx = [s for s in SCENES if s[0] == 'shot'].index((kind, st, du, names))
        im = Image.new('RGB', (W, H), PAPER); d = ImageDraw.Draw(im)
        grain = Image.new('L', (W, H), 0); gd = ImageDraw.Draw(grain)
        for yy in range(0, H, 6): gd.line([(0, yy), (W, yy)], fill=14)
        im.paste(Image.new('RGB', (W, H), PINK), (0, 0), grain)
        n = len(names); seg = 1.0 / n; k = min(int(u / seg), n - 1); lu = (u - k * seg) / seg
        cur = self.shots.get(names[k]); nxt = self.shots.get(names[k + 1]) if k + 1 < n else None
        if self.vert:
            text_center(d, W / 2, 120, T['caps2'][idx], self.fcaps, PGILT)
            yy = 176
            for ln in wrap(d, T['lines'][idx], self.fline, W - 120): text_center(d, W / 2, yy, ln, self.fline, PINK); yy += int(self.fline.size * 1.35)
            px, py = W // 2, yy + 20
        else:
            x0 = int(W * 0.08)
            d.text((x0, int(H * 0.34)), T['caps2'][idx], font=self.fcaps, fill=PGILT)
            yy = int(H * 0.34) + 60
            for ln in wrap(d, T['lines'][idx], self.fline, int(W * 0.44)): d.text((x0, yy), ln, font=self.fline, fill=PINK); yy += int(self.fline.size * 1.35)
            d.line([(x0, yy + 24), (x0 + 90, yy + 24)], fill=PGILT, width=3)
            px, py = int(W * 0.72), int(H * 0.5)
        def put(p, alpha, dy):
            if p is None or alpha <= 0: return
            lay = p.copy()
            if alpha < 1: lay.putalpha(lay.getchannel('A').point(lambda v: int(v * alpha)))
            x = px - lay.width // 2; y = (py if self.vert else py - lay.height // 2) + dy
            im.paste(lay, (x, y), lay)
        rise = int(40 * (1 - ease(u * du / 0.6))) if k == 0 else 0
        if nxt is not None and lu > 0.82:
            a = ease((lu - 0.82) / 0.18); put(cur, 1 - a, 0); put(nxt, a, 0)
        else: put(cur, 1.0, rise)
        return self.fade(im, t, st, du)

    def fade(self, im, t, st, du, first=False, last=False):
        a = 1.0
        if first: a = min(a, ease(t / 0.8))
        if last: a = min(a, ease((st + du - t) / 1.0))
        if not first and t - st < 0.35: a = min(a, ease((t - st) / 0.35) * 0.6 + 0.4)
        if a >= 1: return im
        return Image.blend(Image.new('RGB', im.size, DARK), im, a)


def soundtrack(lang, path):
    """바탕음 (낮은 화음이 천천히 숨 쉬듯) + 시편 23편 낭독 세 줄 + 종소리."""
    n = int(DUR * SR); tt = np.arange(n) / SR
    pad = np.zeros(n)
    for f, a in ((110.0, .5), (164.8, .32), (220.0, .28), (277.2, .12), (329.6, .1)):
        lfo = 0.6 + 0.4 * np.sin(2 * np.pi * (0.07 + f / 10000) * tt + f)
        pad += a * lfo * (np.sin(2 * np.pi * f * tt) + 0.15 * np.sin(2 * np.pi * 2 * f * tt))
    env = np.minimum(1, tt / 3.0) * np.minimum(1, (DUR - tt) / 3.0)
    mix = 0.05 * pad / np.abs(pad).max() * env
    def load(p):
        raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', p, '-f', 's16le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True, check=True).stdout
        return np.frombuffer(raw, dtype='<i2').astype(np.float64) / 32768
    def add(x, at, gain):
        s = int(at * SR); e = min(n, s + len(x)); mix[s:e] += gain * x[:e - s]
    vo = A + '/assets/prayer/' + TEXT[lang]['vo']
    for i, at in ((1, 1.0), (2, 9.6), (6, 22.6)):
        p = f'{vo}/shepherd_{i}.m4a'
        if os.path.exists(p): add(load(p), at, 0.9)
    for fx, at, g in (('fx_plate', 9.0, 0.22), ('fx_verse', 17.2, 0.2), ('fx_book', 30.8, 0.3)):
        p = f'{A}/res/raw/{fx}.wav'
        if os.path.exists(p): add(load(p), at, g)
    mix = np.clip(mix, -0.98, 0.98)
    with wave.open(path, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((mix * 32767).astype('<i2').tobytes())


def render(lang, W, H, path):
    film = Film(lang, W, H)
    wav = path + '.wav'; soundtrack(lang, wav)
    cmd = ['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-', '-i', wav,
           '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '160k', '-shortest', '-movflags', '+faststart', path]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for i in range(int(DUR * FPS)): p.stdin.write(film.frame(i / FPS).tobytes())
    p.stdin.close(); p.wait(); os.remove(wav)
    print('ok', path, os.path.getsize(path) // 1024, 'KB')


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    only = sys.argv[2:]  # 예: ko 16x9
    for lang in ('ko', 'en'):
        for tag, (W, H) in (('16x9', (1920, 1080)), ('9x16', (1080, 1920))):
            langs = [x for x in only if x in TEXT]; tags = [x for x in only if 'x' in x]
            if (langs and lang not in langs) or (tags and tag not in tags): continue
            render(lang, W, H, f'{OUT}/promo_{lang}_{tag}.mp4')
