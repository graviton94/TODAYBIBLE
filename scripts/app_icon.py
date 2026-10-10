# 앱 아이콘 (1.2): 도레 「빛이 있으라」 판화 (빛이 한가운데) + 흰 선 라틴 십자 (속은 비어 판화가 비침) · 정중앙.
# 적응형 아이콘 108 단위 = 바탕 (판화, 가장자리까지) + 앞 (십자 선 · 빛무리, 투명) + 단색 (십자 선 벡터).
# 스토어 512 아이콘 · 시작 화면도 같은 그림. 꾸미기 › 앱 아이콘의 다른 셋 (돌판 · 골고다 · 승천) 도 같은 십자, 바탕 판화만 달라요.
# 다시 만들기: python3 -I scripts/app_icon.py
from PIL import Image, ImageDraw, ImageFilter, ImageOps
import numpy as np, os
R='/home/user/todaybible'; RES=R+'/android/app/src/main/res'; OUT=R+'/docs/store/graphics'
SRC=R+'/android/app/src/main/assets/plates/creation.jpg'
BX,BY=362,256          # 판화에서 빛이 터지는 자리 (원본 픽셀)
VIS=470                # 보이는 원 (72 단위) 에 들어갈 원본 폭
U=108; V=72            # 적응형 아이콘 단위
# 십자 (108 단위, 중심 54,54): 높이 · 가로대 폭 · 두께 · 가로대 위치
CH,CW,CT,CA=44.6,28.8,7.2,0.30
LINE=0.95              # 선 굵기 (단위)
BG=(0x17,0x11,0x0C)
def cross_pts(k=1.0, cx=54, cy=54):
    top=cy-CH/2; bot=cy+CH/2; y0=top+CH*CA-CT/2; y1=y0+CT; x0=cx-CT/2; x1=cx+CT/2; l=cx-CW/2; r=cx+CW/2
    return [(x*k,y*k) for x,y in [(x0,top),(x1,top),(x1,y0),(r,y0),(r,y1),(x1,y1),(x1,bot),(x0,bot),(x0,y1),(l,y1),(l,y0),(x0,y0)]]
def background(px, plate='creation', bx=None, by=None, vis=None, gamma=2.2):
    src=Image.open(R+f'/android/app/src/main/assets/plates/{plate}.jpg').convert('L')
    bx=BX if bx is None else bx; by=BY if by is None else by; vis=VIS if vis is None else vis
    s=vis*U/V; k=s/px   # 출력 1px 당 원본 픽셀
    g=src.transform((px,px),Image.AFFINE,(k,0,bx-s/2,0,k,by-s/2),resample=Image.BICUBIC,fill=0)
    a=(np.asarray(g).astype(float)/255)**gamma
    col=ImageOps.colorize(Image.fromarray((a*255).astype('uint8')),(8,6,4),(250,232,190),mid=(70,52,32))
    yy,xx=np.mgrid[0:px,0:px]; d=np.sqrt((xx/px-0.5)**2+(yy/px-0.5)**2)/(0.62*V/U)
    m=np.clip(1-0.95*np.clip(d,0,1.6)**1.6,0.05,1)
    out=np.asarray(col).astype(float)*m[...,None]
    # 십자 둘레 그늘 · 판화 한 톤 낮춤 (선이 떠 보이게)
    return Image.fromarray((out*0.9).astype('uint8'))
def foreground(px, ss=4):
    B=px*ss; k=B/U; L=Image.new('L',(B,B),0)
    pts=cross_pts(k); ImageDraw.Draw(L).line(pts+[pts[0]],fill=255,width=max(1,round(LINE*k)),joint='curve')
    L=L.resize((px,px),Image.LANCZOS)
    glow=L.filter(ImageFilter.GaussianBlur(px*10/768*U/V)).point(lambda v:min(255,int(v*1.3*0.45)))
    shade=L.filter(ImageFilter.GaussianBlur(px*3/768*U/V)).point(lambda v:int(v*0.35))
    im=Image.new('RGBA',(px,px),(0,0,0,0))
    im=Image.alpha_composite(im,Image.merge('RGBA',(*[Image.new('L',(px,px),0)]*3,shade)))
    im=Image.alpha_composite(im,Image.merge('RGBA',(Image.new('L',(px,px),255),Image.new('L',(px,px),244),Image.new('L',(px,px),220),glow)))
    im=Image.alpha_composite(im,Image.merge('RGBA',(Image.new('L',(px,px),252),Image.new('L',(px,px),248),Image.new('L',(px,px),238),L)))
    return im
DPI={'mdpi':1,'hdpi':1.5,'xhdpi':2,'xxhdpi':3,'xxxhdpi':4}
# 아이콘 셋: 이름 뒤붙이 · 판화 · 판화에서 가운데 (원본 픽셀) · 보이는 원에 들어갈 폭 · 감마
def at(plate, fx, fy, fw):
    w,h=Image.open(R+f'/android/app/src/main/assets/plates/{plate}.jpg').size; return dict(plate=plate,bx=fx*w,by=fy*h,vis=fw*w)
VARIANTS={'':dict(plate='creation'),
          '_tablets':dict(at('radiant',0.40,0.27,0.48),gamma=1.3),
          '_golgotha':dict(at('dark_cross',0.57,0.25,0.42),gamma=1.35),
          '_ascension':dict(at('ascension',0.46,0.19,0.40),gamma=1.9)}
for suf,kw in VARIANTS.items():
    for name,m in DPI.items():
        d=f'{RES}/mipmap-{name}'; os.makedirs(d,exist_ok=True); px=int(U*m)
        background(px,**kw).save(f'{d}/ic_launcher_art{suf}.png')
        if suf=='': foreground(px).save(f'{d}/ic_launcher_line.png')
    open(f'{RES}/mipmap-anydpi-v26/ic_launcher{suf}.xml','w').write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 판화 위 흰 선 십자 (scripts/app_icon.py 가 만들어요) -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_art{suf}" />
    <foreground android:drawable="@mipmap/ic_launcher_line" />
    <monochrome android:drawable="@drawable/ic_launcher_cross" />
</adaptive-icon>
''')
# 단색 (테마 아이콘) · 시작 화면: 십자 선 벡터
p=cross_pts(); path='M'+' L'.join(f'{x:.2f},{y:.2f}' for x,y in p)+' Z'
open(f'{RES}/drawable/ic_launcher_cross.xml','w').write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 흰 선 라틴 십자 (속은 비움): 테마 아이콘 · 시작 화면. scripts/app_icon.py 가 만들어요 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:strokeColor="#FCF8EE" android:strokeWidth="{LINE*1.4:.2f}" android:strokeLineJoin="round"
        android:pathData="{path}" />
</vector>
''')
# 스토어 512: 보이는 원 (72) 영역을 정사각으로
big=1536; full=background(big).convert('RGBA'); full=Image.alpha_composite(full,foreground(big))
c=int(big*(U-V)/2/U); full.crop((c,c,big-c,big-c)).resize((512,512),Image.LANCZOS).convert('RGB').save(f'{OUT}/icon_512.png')
print('ok')
