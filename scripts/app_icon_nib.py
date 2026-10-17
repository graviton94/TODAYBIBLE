# 앱 아이콘 (1.6): 짙은 가죽 바탕 + 금빛 만년필 펜촉 (펜촉 구멍이 십자, 아래로 갈라진 홈). 시안 A · CALAMUS.
# 적응형 아이콘 108 단위 = 바탕 (가죽 빛무리) + 앞 (펜촉, 투명) + 단색 (펜촉 벡터, 십자 · 홈은 비움).
# 그림은 SVG 로 한 번 그리고 크롬 (헤드리스) 으로 굽고 PIL 로 줄여요. 스토어 512 · 시작 화면도 같은 그림.
# 꾸미기 › 앱 아이콘의 다른 셋 (돌판 · 골고다 · 승천) 은 scripts/app_icon.py 그대로 (판화 + 흰 선 십자).
# 다시 만들기: python3 -I scripts/app_icon_nib.py
import os, subprocess, tempfile, glob
from PIL import Image
R = '/home/user/todaybible'; RES = R + '/android/app/src/main/res'; STORE = R + '/docs/store/graphics'
U = 108; V = 72
DPI = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
# 펜촉은 로컬 좌표 (윗변 y=0, 끝 y=100, 폭 ±25.5) 에서 그리고 PLACE 로 적응형 108 에 옮겨요 (보이는 원 72 · 안전 66 안).
# 끝은 한 덩어리 (홈은 끝 12 앞에서 사라짐), 십자 구멍의 아래 팔이 부드럽게 좁아져 홈이 돼요. 무게 중심이 원 가운데에 오게 조금 아래로.
PX, PY, PK = 54, 30.5, 0.5
OUT='M -22 4 Q 0 0 22 4 C 24 12 25.5 20 25.5 28 C 25.5 44 14 56 8.5 70 C 5 79 2.6 89 1.2 97.5 Q 0 100.6 -1.2 97.5 C -2.6 89 -5 79 -8.5 70 C -14 56 -25.5 44 -25.5 28 C -25.5 20 -24 12 -22 4 Z'
# 십자 구멍: 아래 팔이 부드럽게 좁아져 홈이 되고, 끝 12 앞에서 사라짐 (끝은 한 덩어리)
HOLE=('M -2.7 14.5 Q -2.7 14 -2.2 14 L 2.2 14 Q 2.7 14 2.7 14.5 L 2.7 20.5 L 8 20.5 Q 8.5 20.5 8.5 21 L 8.5 25.4 Q 8.5 25.9 8 25.9 L 2.7 25.9 L 2.7 39 '
      'C 2.7 43 0.75 45 0.75 49 L 0.22 87 L -0.22 87 L -0.75 49 C -0.75 45 -2.7 43 -2.7 39 L -2.7 25.9 L -8 25.9 Q -8.5 25.9 -8.5 25.4 L -8.5 21 Q -8.5 20.5 -8 20.5 L -2.7 20.5 Z')
RIGHT='M 0 -1 L 30 -1 L 30 101 L 0 101 Z'
def fg(uid='a', place=f'translate({PX} {PY}) scale({PK})'):
    return f'''<defs>
<linearGradient id="m{uid}" gradientUnits="userSpaceOnUse" x1="-30" y1="0" x2="30" y2="0">
 <stop offset="0" stop-color="#4E3714"/><stop offset=".14" stop-color="#A97D33"/><stop offset=".3" stop-color="#F6E3A6"/><stop offset=".4" stop-color="#E2BF73"/>
 <stop offset=".5" stop-color="#B4873A"/><stop offset=".62" stop-color="#CFA457"/><stop offset=".8" stop-color="#9A6F2A"/><stop offset="1" stop-color="#4A3312"/></linearGradient>
<linearGradient id="v{uid}" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="100">
 <stop offset="0" stop-color="#000" stop-opacity=".38"/><stop offset=".12" stop-color="#000" stop-opacity="0"/><stop offset=".7" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#fff" stop-opacity=".12"/></linearGradient>
<linearGradient id="r{uid}" gradientUnits="userSpaceOnUse" x1="0" y1="14" x2="0" y2="48">
 <stop offset="0" stop-color="#3A2A10"/><stop offset=".72" stop-color="#F6E3A6"/><stop offset=".86" stop-color="#F6E3A6" stop-opacity="0"/><stop offset="1" stop-color="#F6E3A6" stop-opacity="0"/></linearGradient>
<mask id="h{uid}" maskUnits="userSpaceOnUse" x="-40" y="-10" width="80" height="120"><rect x="-40" y="-10" width="80" height="120" fill="#fff"/><path d="{HOLE}" fill="#000"/></mask>
<clipPath id="c{uid}"><path d="{OUT}"/></clipPath>
<filter id="s{uid}" x="-40%" y="-20%" width="180%" height="150%"><feGaussianBlur stdDeviation="3.2"/></filter>
</defs>
<g transform="{place}">
 <path d="{OUT}" fill="#000" opacity=".6" transform="translate(2.4 4)" filter="url(#s{uid})"/>
 <g mask="url(#h{uid})">
  <path d="{OUT}" fill="url(#m{uid})"/>
  <g clip-path="url(#c{uid})">
   <path d="{RIGHT}" fill="#000" opacity=".1"/>
   <rect x="-31" y="-2" width="62" height="104" fill="url(#v{uid})"/>
   <path d="M -21.5 9 Q 0 5 21.5 9" fill="none" stroke="#5E431A" stroke-width=".7" opacity=".55"/>
   <path d="M -21.8 11.4 Q 0 7.4 21.8 11.4" fill="none" stroke="#F6E3A6" stroke-width=".45" opacity=".35"/>
   <path d="M -19.5 47 C -13 52 -8 54 0 54.5 C 8 54 13 52 19.5 47" fill="none" stroke="#5E431A" stroke-width=".7" opacity=".5"/>
   <path d="M -18.6 49.4 C -12.6 54.2 -8 56.2 0 56.7 C 8 56.2 12.6 54.2 18.6 49.4" fill="none" stroke="#F6E3A6" stroke-width=".45" opacity=".3"/>
  </g>
  <path d="{OUT}" fill="none" stroke="#2E200B" stroke-width=".9" opacity=".55"/>
  <path d="M -22 4 Q 0 0 22 4" fill="none" stroke="#FBEFC6" stroke-width="1" opacity=".75"/>
 </g>
 <path d="{HOLE}" fill="none" stroke="url(#r{uid})" stroke-width=".9" opacity=".7"/>
</g>'''
def bg(uid='a'):
    return f'''<defs><radialGradient id="b{uid}" gradientUnits="userSpaceOnUse" cx="54" cy="46" r="52"><stop offset="0" stop-color="#3D3125"/><stop offset=".55" stop-color="#231A12"/><stop offset="1" stop-color="#0F0B07"/></radialGradient>
<filter id="n{uid}"><feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" seed="7"/><feColorMatrix values="0 0 0 0 .5  0 0 0 0 .4  0 0 0 0 .3  0 0 0 .5 0"/></filter></defs>
<rect width="108" height="108" fill="url(#b{uid})"/><rect width="108" height="108" filter="url(#n{uid})" opacity=".18"/>'''

CHROME = sorted(glob.glob('/opt/pw-browsers/chromium-*/chrome-linux/chrome'))[-1]
def render(body, px):
    with tempfile.TemporaryDirectory() as d:
        h = os.path.join(d, 'i.html'); o = os.path.join(d, 'o.png')
        open(h, 'w').write(f'<html><body style="margin:0;background:transparent"><svg width="{px}" height="{px}" viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg">{body}</svg></body></html>')
        subprocess.run([CHROME, '--headless', '--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--default-background-color=00000000',
                        f'--window-size={px},{px}', f'--screenshot={o}', 'file://' + h], check=True, capture_output=True)
        return Image.open(o).convert('RGBA').copy()

big = 1728
fg = render(fg(), big); bg = render(bg(), big)
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
# 단색 (테마 아이콘) · 시작 화면: 펜촉 벡터 (십자 · 홈은 evenOdd 로 비움, 홈이 윤곽 안에서 끝나서 깨끗해요)
def vector(color, note):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {note} scripts/app_icon_nib.py 가 만들어요 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="{PX}" android:translateY="{PY}" android:scaleX="{PK}" android:scaleY="{PK}">
        <path android:fillColor="{color}" android:fillType="evenOdd" android:pathData="{OUT} {HOLE}" />
    </group>
</vector>
'''
open(f'{RES}/drawable/ic_launcher_nib_mono.xml', 'w').write(vector('#FFFFFFFF', '금빛 펜촉 한 색 (테마 아이콘).'))
open(f'{RES}/drawable/ic_launcher_nib_splash.xml', 'w').write(vector('#FFC7A35D', '금빛 펜촉 한 색 (시작 화면).'))
# 스토어 512: 보이는 원 (72) 영역을 정사각으로
full = Image.alpha_composite(bg, fg); c = int(big * (U - V) / 2 / U)
full.crop((c, c, big - c, big - c)).resize((512, 512), Image.LANCZOS).convert('RGB').save(f'{STORE}/icon_512.png')
print('ok')
