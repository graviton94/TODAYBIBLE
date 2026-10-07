# 스토어 휴대전화 스크린샷: 1080x1920 (9:16) 종이 바탕 · 위에 한 줄 설명 · 아래 실제 화면 (모서리 둥글게, 금빛 테)
# 다시 만들기: python3 -I scripts/store_frame.py <캡처 폴더 (st_ko_1_today.png …)>
import sys, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
R='/home/user/todaybible'; F=R+'/.cache/'; OUT=R+'/docs/store/graphics'
SRC=sys.argv[1]
LEAF=(0xF7,0xF1,0xE3); INK=(0x2A,0x21,0x19); RUBRIC=(0x8C,0x21,0x17); GILT=(0xA0,0x7B,0x36)
CAP={
 'ko':["하루 한 장, 소리 내어 읽고 옮겨 써요","원고지에 한 글자씩 타자로","인도 목소리와 한 절씩, 교독","개역한글과 영어 성경을 나란히","아침부터 밤까지 말씀으로 기도","쓸수록 드러나는 도레의 판화","나의 서가에 쌓여 가는 성경 한 권"],
 'en':["One chapter a day — read it aloud, write it down","Type it, letter by letter","Read responsively with a narrator","Modern English and Korean side by side","Pray with Scripture, morning to night","Write more, uncover a Doré engraving","Watch your own Bible fill the shelf"],
}
NAMES=['today','type','aloud','reader','prayer','plate','record']
def font(lang, size):
    if lang=='ko': return ImageFont.truetype(F+'SongMyung-Regular.ttf', size)
    f=ImageFont.truetype(F+'EBGaramond[wght].ttf', size)
    try: f.set_variation_by_axes([500])
    except Exception: pass
    return f
def wrap(d, text, f, maxw):
    words=text.split(' '); lines=[]; cur=''
    for w in words:
        t=(cur+' '+w).strip()
        if d.textlength(t,font=f)<=maxw: cur=t
        else: lines.append(cur); cur=w
    lines.append(cur); return lines
for lang in ('ko','en'):
    for i,n in enumerate(NAMES):
        p=f'{SRC}/st_{lang}_{i+1}_{n}.png'
        if not os.path.exists(p) or os.path.getsize(p)==0: print('missing',p); continue
        W,H=1080,1920; im=Image.new('RGB',(W,H),LEAF); d=ImageDraw.Draw(im)
        f=font(lang, 64 if lang=='ko' else 68)
        lines=wrap(d, CAP[lang][i], f, 940)
        y=120 if len(lines)==1 else 80
        for l in lines:
            tw=d.textlength(l,font=f); d.text(((W-tw)/2,y),l,font=f,fill=INK); y+=86
        d.line([(W/2-60,y+28),(W/2+60,y+28)],fill=GILT,width=3)
        shot=Image.open(p).convert('RGB')
        sh=1450; sw=round(shot.width*sh/shot.height); shot=shot.resize((sw,sh),Image.LANCZOS)
        x=(W-sw)//2; top=H-sh-60
        # 그림자 · 둥근 모서리 · 금빛 테
        shadow=Image.new('L',(W,H),0); sd=ImageDraw.Draw(shadow); sd.rounded_rectangle([x+6,top+14,x+sw+6,top+sh+14],radius=44,fill=90)
        shadow=shadow.filter(ImageFilter.GaussianBlur(18)); im.paste((0x5C,0x4E,0x3E),(0,0),shadow)
        mask=Image.new('L',(sw,sh),0); ImageDraw.Draw(mask).rounded_rectangle([0,0,sw-1,sh-1],radius=40,fill=255)
        im.paste(shot,(x,top),mask)
        d=ImageDraw.Draw(im); d.rounded_rectangle([x-3,top-3,x+sw+2,top+sh+2],radius=43,outline=GILT,width=3)
        im.save(f'{OUT}/phone_{lang}_{i+1}.png'); print('ok',lang,i+1)
