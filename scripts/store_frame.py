# 스토어 휴대전화 스크린샷 (1.1 틀): 1080x1920. 위에 금빛 머리글 (Cinzel) · 한 줄 설명, 아래 실제 화면 (가는 테, 그림자 없음).
# 판화 화면 (여는 화면 · 장 여는 화면 · 화첩) 은 어두운 바탕, 나머지는 종이 바탕.
# 다시 만들기: python3 -I scripts/store_frame.py <캡처 폴더 (st_ko_1_intro.png …)>
import sys, os
from PIL import Image, ImageDraw, ImageFont
R=os.path.dirname(os.path.dirname(os.path.abspath(__file__))); F=R+'/.cache/'; OUT=R+'/docs/store/graphics'
SRC=sys.argv[1]
LIGHT=dict(bg=(0xF4,0xEF,0xE6), ink=(0x1F,0x1A,0x15), gilt=(0x9A,0x6B,0x1F), hair=(0xD6,0xCF,0xC2))
DARK=dict(bg=(0x17,0x11,0x0C), ink=(0xEA,0xDF,0xC8), gilt=(0xC7,0xA3,0x5D), hair=(0x3A,0x32,0x2A))
# 1.7: 세 필사본 (타자본 · 손글씨본 · 낭독본) · 역사 속의 성경 · 관련 구절 · 새 기도문
NAMES=['intro','today','type','hand','aloud','story','related','prayer']
DARKS={'intro','story'}
CAPS=['BIBLIA · MANU SCRIPTA','HODIE','SCRIBERE','MANU','RESPONSORIUM','HISTORIA','CONCORDANTIA','ORATIO']
CAP={
 'ko':["하루 한 장, 판화와 함께 여는 말씀","오늘 쓸 자리와 기도를 한눈에","타자본: 한 글자씩 원고지에","손글씨본: 손으로 쓰는 나만의 성경","낭독본: 인도 목소리와 한 절씩 교독","판화마다 그림 · 장면 · 역사 이야기","관련 구절 · 성경 사전을 그 자리에서","기도문마다 낭독, 들으며 기도해요"],
 'en':["One chapter a day, opened with an engraving","Today's place and prayer at a glance","Typed copy: letter by letter","Handwritten copy: your Bible in your hand","Read-aloud copy: take turns with a narrator","Every engraving tells its history","Related verses and a dictionary, right there","Every prayer narrated: listen and pray"],
}
def var(name, size, w):
    f=ImageFont.truetype(F+name, size)
    try: f.set_variation_by_axes([w])
    except Exception: pass
    return f
def wrap(d, text, f, maxw):
    words=text.split(' '); lines=[]; cur=''
    for w in words:
        t=(cur+' '+w).strip()
        if d.textlength(t,font=f)<=maxw: cur=t
        else: lines.append(cur); cur=w
    lines.append(cur); return lines
os.makedirs(OUT, exist_ok=True)
for f in os.listdir(OUT):
    if f.startswith('phone_'): os.remove(os.path.join(OUT,f))
for lang in ('ko','en'):
    for i,n in enumerate(NAMES):
        p=f'{SRC}/st_{lang}_{i+1}_{n}.png'
        if not os.path.exists(p) or os.path.getsize(p)==0: print('missing',p); continue
        P=DARK if n in DARKS else LIGHT
        W,H=1080,1920; im=Image.new('RGB',(W,H),P['bg']); d=ImageDraw.Draw(im)
        cf=var('Cinzel[wght].ttf',30,600); cw=d.textlength(CAPS[i],font=cf)
        d.text(((W-cw)/2,96),CAPS[i],font=cf,fill=P['gilt'])
        f=var('NotoSerifKR[wght].ttf',58,600) if lang=='ko' else var('CormorantGaramond[wght].ttf',68,600)
        y=156
        for l in wrap(d, CAP[lang][i], f, 940):
            tw=d.textlength(l,font=f); d.text(((W-tw)/2,y),l,font=f,fill=P['ink']); y+=84
        shot=Image.open(p).convert('RGB')
        sh=1500 if y<260 else 1440; sw=round(shot.width*sh/shot.height); shot=shot.resize((sw,sh),Image.LANCZOS)
        x=(W-sw)//2; top=H-sh-56
        mask=Image.new('L',(sw,sh),0); ImageDraw.Draw(mask).rounded_rectangle([0,0,sw-1,sh-1],radius=28,fill=255)
        im.paste(shot,(x,top),mask)
        d=ImageDraw.Draw(im); d.rounded_rectangle([x-2,top-2,x+sw+1,top+sh+1],radius=30,outline=P['hair'],width=2)
        im.save(f'{OUT}/phone_{lang}_{i+1}.png'); print('ok',lang,i+1)
