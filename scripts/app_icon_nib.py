# 앱 아이콘 (1.6): 짙은 가죽 바탕 + 금빛 만년필 펜촉 (펜촉 구멍이 십자, 아래로 갈라진 홈). 시안 A · CALAMUS.
# 적응형 아이콘 108 단위 = 바탕 (가죽 빛무리) + 앞 (펜촉, 투명) + 단색 (펜촉 벡터, 십자 · 홈은 비움).
# 그림은 SVG 로 한 번 그리고 크롬 (헤드리스) 으로 굽고 PIL 로 줄여요. 스토어 512 · 시작 화면도 같은 그림.
# 꾸미기 › 앱 아이콘의 다른 셋 (돌판 · 골고다 · 승천) 은 scripts/app_icon.py 그대로 (판화 + 흰 선 십자).
# 다시 만들기: python3 -I scripts/app_icon_nib.py
import os, subprocess, tempfile, glob
from PIL import Image
R = '/home/user/todaybible'; RES = R + '/android/app/src/main/res'; OUT = R + '/docs/store/graphics'
U = 108; V = 72
DPI = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
NIB = 'M30 20 L78 20 L78 44 Q78 52 64 60 L55.2 94 Q54 97 52.8 94 L44 60 Q30 52 30 44 Z'
RIGHT = 'M54 20 L78 20 L78 44 Q78 52 64 60 L55.2 94 Q54 97 54 96 Z'
# 십자 구멍 + 갈라진 홈 (한 덩어리, 끝은 펜촉 안에서 멈춤)
HOLE = 'M51.2 29 L56.8 29 L56.8 35 L63.5 35 L63.5 40.6 L56.8 40.6 L56.8 51 L55 51 L55 92 L53 92 L53 51 L51.2 51 L51.2 40.6 L44.5 40.6 L44.5 35 L51.2 35 Z'
# 시안 (보이는 원 72 를 108 로 본 그림) → 적응형 108: 72/108 로 줄임. 펜촉 축 (54,58) 을 가운데 (54,54) 로.
K = 0.88 * V / U
FIT = f'translate(54 54) scale({K:.4f}) translate(-54 -58)'

def nib_svg():
    return f'''<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#6E5020"/><stop offset=".22" stop-color="#C7A35D"/>
<stop offset=".42" stop-color="#F2DC9B"/><stop offset=".5" stop-color="#C7A35D"/><stop offset=".78" stop-color="#9A6B1F"/><stop offset="1" stop-color="#5A4018"/></linearGradient>
<mask id="m"><rect x="-50" y="-50" width="208" height="208" fill="#fff"/><path d="{HOLE}" fill="#000"/></mask></defs>
<g transform="{FIT}"><g mask="url(#m)"><path d="{NIB}" fill="url(#g)"/><path d="{RIGHT}" fill="#000" opacity=".14"/>
<path d="M35 26 Q54 31 73 26" fill="none" stroke="#6E5020" stroke-width="1.1" opacity=".55"/><path d="M37 55 Q54 50 71 55" fill="none" stroke="#6E5020" stroke-width="1" opacity=".45"/>
<path d="M30 20 L78 20" stroke="#F2DC9B" stroke-width="1.4" opacity=".8"/></g></g>'''
# 바탕: 시안의 빛무리 (가운데 위쪽이 밝음). 보이는 원 기준 반지름 72% → 108 기준으로 환산
BG = f'''<defs><radialGradient id="b" gradientUnits="userSpaceOnUse" cx="54" cy="{54 - 0.14 * V:.2f}" r="{0.72 * V:.2f}"><stop offset="0" stop-color="#33291F"/><stop offset="1" stop-color="#141009"/></radialGradient></defs>
<rect width="108" height="108" fill="url(#b)"/>'''

CHROME = sorted(glob.glob('/opt/pw-browsers/chromium-*/chrome-linux/chrome'))[-1]
def render(body, px):
    with tempfile.TemporaryDirectory() as d:
        h = os.path.join(d, 'i.html'); o = os.path.join(d, 'o.png')
        open(h, 'w').write(f'<html><body style="margin:0;background:transparent"><svg width="{px}" height="{px}" viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg">{body}</svg></body></html>')
        subprocess.run([CHROME, '--headless', '--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--default-background-color=00000000',
                        f'--window-size={px},{px}', f'--screenshot={o}', 'file://' + h], check=True, capture_output=True)
        return Image.open(o).convert('RGBA').copy()

big = 1728
fg = render(nib_svg(), big); bg = render(BG, big)
for name, m in DPI.items():
    d = f'{RES}/mipmap-{name}'; px = int(U * m)
    bg.resize((px, px), Image.LANCZOS).convert('RGB').save(f'{d}/ic_launcher_nib_bg.png')
    fg.resize((px, px), Image.LANCZOS).save(f'{d}/ic_launcher_nib.png')
open(f'{RES}/mipmap-anydpi-v26/ic_launcher.xml', 'w').write('''<?xml version="1.0" encoding="utf-8"?>
<!-- 가죽 위 금빛 펜촉 (scripts/app_icon_nib.py 가 만들어요) -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_nib_bg" />
    <foreground android:drawable="@mipmap/ic_launcher_nib" />
    <monochrome android:drawable="@drawable/ic_launcher_nib_mono" />
</adaptive-icon>
''')
# 단색 (테마 아이콘) · 시작 화면: 펜촉 벡터 (구멍은 evenOdd 로 비움). 축 (54,58) 기준으로 줄이고 4 위로.
def vector(color, note):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {note} scripts/app_icon_nib.py 가 만들어요 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:pivotX="54" android:pivotY="58" android:scaleX="{K:.4f}" android:scaleY="{K:.4f}" android:translateY="-4">
        <path android:fillColor="{color}" android:fillType="evenOdd" android:pathData="{NIB} {HOLE}" />
    </group>
</vector>
'''
open(f'{RES}/drawable/ic_launcher_nib_mono.xml', 'w').write(vector('#FFFFFFFF', '금빛 펜촉 한 색 (테마 아이콘).'))
open(f'{RES}/drawable/ic_launcher_nib_splash.xml', 'w').write(vector('#FFC7A35D', '금빛 펜촉 한 색 (시작 화면).'))
# 스토어 512: 보이는 원 (72) 영역을 정사각으로
full = Image.alpha_composite(bg, fg); c = int(big * (U - V) / 2 / U)
full.crop((c, c, big - c, big - c)).resize((512, 512), Image.LANCZOS).convert('RGB').save(f'{OUT}/icon_512.png')
print('ok')
