# Play 스토어 그림 (1.1 틀): 대표 그래픽 1024x500 (한/영). 판화 한 점이 오른쪽을 채우고 어둠으로 녹아드는 자리에 머리글 · 제목 · 한 줄.
# 아이콘 512 는 scripts/store_icon.py 가 따로.
# 다시 만들기: python3 -I scripts/store_art.py
from PIL import Image, ImageDraw, ImageFont, ImageOps
import os
R='/home/user/todaybible'; OUT=R+'/docs/store/graphics'; F=R+'/.cache/'
DARK=(0x17,0x11,0x0C); INK=(0xEA,0xDF,0xC8); MUTED=(0xA3,0x98,0x86); GILT=(0xC7,0xA3,0x5D)
S=3
def font(name, size, wght=None):
    f=ImageFont.truetype(F+name, size)
    if wght:
        try: f.set_variation_by_axes([wght])
        except Exception: pass
    return f
def feature(caps, title, sub, tfont, sfont, path):
    W,H=1024*S,500*S
    im=Image.new('RGB',(W,H),DARK)
    pl=Image.open(R+'/android/app/src/main/assets/plates/creation.jpg').convert('L')
    pw=int(W*0.62); pl=ImageOps.fit(pl,(pw,H),centering=(0.4,0.3)); pl=ImageOps.colorize(pl,DARK,INK)
    # 왼쪽으로 어둠에 녹아들게
    mask=Image.new('L',(pw,H)); md=ImageDraw.Draw(mask)
    for x in range(pw): md.line([(x,0),(x,H)],fill=int(255*min(1,max(0,(x/pw-0.02)/0.55))**1.4))
    im.paste(pl,(W-pw,0),mask)
    d=ImageDraw.Draw(im); x=72*S
    d.text((x,150*S),caps,font=font('Cinzel[wght].ttf',17*S,600),fill=GILT)
    tf=font(*tfont); d.text((x,186*S),title,font=tf,fill=INK)
    d.line([(x,300*S),(x+64*S,300*S)],fill=GILT,width=S)
    d.text((x,322*S),sub,font=font(*sfont),fill=MUTED)
    im.resize((1024,500),Image.LANCZOS).save(path)
feature('BIBLIA · MANU SCRIPTA','하루의 성경','소리 내어 읽고, 손으로 옮겨 쓰는 성경',('NotoSerifKR[wght].ttf',76*S,600),('NotoSerifKR[wght].ttf',24*S,500),OUT+'/feature_ko.png')
feature('BIBLIA · MANU SCRIPTA','Bible by Hand','Read it aloud. Write it by hand.',('CormorantGaramond[wght].ttf',88*S,600),('EBGaramond[wght].ttf',28*S,500),OUT+'/feature_en.png')
print(sorted(os.listdir(OUT)))
