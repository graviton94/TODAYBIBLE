# Play 스토어 그림: 아이콘 512 · 대표 그래픽 1024x500 (한/영). 앱 아이콘 · 토큰과 같은 색.
# 다시 만들기: python3 -I scripts/store_art.py
from PIL import Image, ImageDraw, ImageFont
import os
R='/home/user/todaybible'; OUT=R+'/docs/store/graphics'; F=R+'/.cache/'
LEATHER=(0x4A,0x19,0x13); GILT=(0xC7,0xA3,0x5D); INK=(0xEB,0xDA,0xB1)
CROSS=[(48,34),(60,34),(57,48),(74,45),(74,57),(57,54),(60,74),(48,74),(51,54),(34,57),(34,45),(51,48)]
def cross(d, cx, cy, size, color):
    # size = 화면에서 108 단위가 차지할 너비
    k=size/108; d.polygon([(cx+(x-54)*k, cy+(y-54)*k) for x,y in CROSS], fill=color)
# 아이콘: 적응형 아이콘과 같게 (108 단위 가운데 72 가 보이는 자리 → 512)
S=4; im=Image.new('RGB',(512*S,512*S),LEATHER); d=ImageDraw.Draw(im)
cross(d, 256*S, 256*S, 512*S*108/72, GILT)
im.resize((512,512),Image.LANCZOS).save(OUT+'/icon_512.png')
def font(name, size, wght=None):
    f=ImageFont.truetype(F+name, size)
    if wght:
        try: f.set_variation_by_axes([wght])
        except Exception: pass
    return f
def feature(title, sub, tfont, sfont, path):
    W,H=1024,500; im=Image.new('RGB',(W*S,H*S),LEATHER); d=ImageDraw.Draw(im)
    # 금박 테 두 줄 (책 표지처럼)
    for inset,w in ((22,3),(32,1.5)):
        d.rectangle([inset*S,inset*S,(W-inset)*S,(H-inset)*S], outline=GILT, width=int(w*S))
    cross(d, 210*S, 250*S, 260*S, GILT)
    tf=font(*tfont); sf=font(*sfont)
    tw=d.textlength(title, font=tf); d.text((380*S, 175*S), title, font=tf, fill=GILT)
    d.text((382*S, 300*S), sub, font=sf, fill=INK)
    im.resize((W,H),Image.LANCZOS).save(path)
feature('하루의 성경', '소리 내어 읽고, 손으로 옮겨 쓰는 성경', ('SongMyung-Regular.ttf', 92*S), ('NotoSerifKR[wght].ttf', 34*S, 500), OUT+'/feature_ko.png')
feature('Bible by Hand', 'Read it aloud. Write it by hand.', ('EBGaramond[wght].ttf', 96*S, 500), ('EBGaramond[wght].ttf', 40*S, 500), OUT+'/feature_en.png')
print(os.listdir(OUT))
