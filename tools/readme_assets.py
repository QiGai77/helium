from PIL import Image, ImageDraw, ImageFilter, ImageFont
import os
P='design/png/'; OUT='docs/assets/'
F=os.path.expanduser('~/.fonts/')
def font(name,size,wght=None):
    f=ImageFont.truetype(F+name,size)
    try:
        if wght: f.set_variation_by_axes([wght] if name.startswith('Noto') else [f.get_variation_axes()[0]['default'] if False else 0]*0 or None)
    except Exception: pass
    return f
def fvar(name,size,axes):
    f=ImageFont.truetype(F+name,size)
    try:
        ax=f.get_variation_axes(); vals=[]
        for a in ax:
            n=a['name'].decode() if isinstance(a['name'],bytes) else a['name']
            key={'Weight':'wght','Roundness':'ROND','Optical Size':'opsz','Width':'wdth','Slant':'slnt'}.get(n,n); vals.append(axes.get(key, a['default']))
        f.set_variation_by_axes(vals)
    except Exception as e: print('var fail',name,e)
    return f
def mesh(w,h,blobs,base=(244,245,251)):
    im=Image.new('RGB',(w,h),base)
    lay=Image.new('RGBA',(w,h),(0,0,0,0)); d=ImageDraw.Draw(lay)
    for (x,y,r,c,a) in blobs: d.ellipse([x-r,y-r,x+r,y+r],fill=c+(a,))
    lay=lay.filter(ImageFilter.GaussianBlur(min(w,h)//5))
    im.paste(lay,(0,0),lay); return im
def phone(fn,h,crop_h=None):
    im=Image.open(P+fn).convert('RGB')
    if crop_h: im=im.crop((0,0,im.width,crop_h))
    im=im.resize((int(im.width*h/im.height),h),Image.LANCZOS)
    r=int(im.width*0.11)
    m=Image.new('L',im.size,0); ImageDraw.Draw(m).rounded_rectangle([0,0,im.width-1,im.height-1],r,fill=255)
    im.putalpha(m); return im,r
def shadow(base,img,x,y,r,blur=40,off=24,alpha=60):
    s=Image.new('RGBA',base.size,(0,0,0,0)); d=ImageDraw.Draw(s)
    d.rounded_rectangle([x,y+off,x+img.width,y+img.height+off],r,fill=(27,33,80,alpha))
    s=s.filter(ImageFilter.GaussianBlur(blur)); base.alpha_composite(s); base.alpha_composite(img,(x,y))
    # hairline
    d=ImageDraw.Draw(base); d.rounded_rectangle([x,y,x+img.width-1,y+img.height-1],r,outline=(230,232,240,255),width=2)
# ---- banner 2400x1100
W,H=2400,1100
b=mesh(W,H,[(300,200,500,(79,107,255),90),(2100,150,520,(185,167,255),110),(1900,1050,600,(25,198,230),70),(700,1000,450,(255,158,207),60)]).convert('RGBA')
d=ImageDraw.Draw(b)
d.text((150,380),'Helium',font=fvar('GoogleSansFlex.ttf',190,{'wght':700,'ROND':100}),fill=(18,19,26))
d.text((158,610),'个人 AI Agent',font=fvar('NotoSansSC.ttf',56,{'wght':600}),fill=(52,56,74)); d.text((158,690),'记得你，替你动手，花钱前先问你',font=fvar('NotoSansSC.ttf',44,{'wght':400}),fill=(90,95,115))
d.text((160,770),'Android · Jetpack Compose · OpenCode Go / Gemini',font=fvar('GoogleSansFlex.ttf',34,{'wght':450}),fill=(120,125,145))
# pill

ph=[('01-home@2x.png',None),('07-glance@2x.png',1784),('02-chat@2x.png',None)]
xs=[1390,1700,2010]; ys=[200,110,200]; hh=[820,900,820]
order=[0,2,1]
imgs=[phone(f,h,c) for (f,c),h in zip(ph,hh)]
for i in [0,2,1]:
    im,r=imgs[i]; shadow(b,im,xs[i]-im.width//2+60,ys[i],r)
b.convert('RGB').save(OUT+'banner.png',optimize=True)
# ---- showcase: 5 screens equal height
W2=2400; items=[('01-home@2x.png','首页',None),('07-glance@2x.png','速览',1784),('02-chat@2x.png','对话',None),('06-schedule-projects@2x.png','任务',None),('09-me@2x.png','我的',1784)]
h=900; gap=48
ims=[phone(f,h,c) for f,_,c in items]
tw=sum(i.width for i,_ in ims)+gap*(len(ims)-1)
s=mesh(W2,h+260,[(400,600,500,(79,107,255),40),(2000,400,500,(185,167,255),55)]).convert('RGBA')
x=(W2-tw)//2; d=ImageDraw.Draw(s); lf=fvar('NotoSansSC.ttf',40,{'wght':600})
for (im,r),(_,lab,_) in zip(ims,items):
    shadow(s,im,x,60,r,blur=30,off=18,alpha=45)
    tw_=d.textlength(lab,font=lf); d.text((x+(im.width-tw_)/2,60+h+50),lab,font=lf,fill=(52,56,74)); x+=im.width+gap
s.convert('RGB').save(OUT+'showcase.png',optimize=True)
# ---- details row: timeline, approval, glance-custom, models, memory
items=[('03-timeline@2x.png','任务时间线'),('04-approval@2x.png','审批'),('08-glance-custom@2x.png','自定义卡'),('10-models@2x.png','模型与推理'),('05-memory-skills@2x.png','记忆与技能')]
ims=[phone(f,h) for f,_ in items]
tw=sum(i.width for i,_ in ims)+gap*(len(ims)-1)
s=Image.new('RGBA',(W2,h+260),(244,245,251,255)); x=(W2-tw)//2; d=ImageDraw.Draw(s)
for (im,r),(_,lab) in zip(ims,items):
    shadow(s,im,x,60,r,blur=30,off=18,alpha=40)
    tw_=d.textlength(lab,font=lf); d.text((x+(im.width-tw_)/2,60+h+50),lab,font=lf,fill=(52,56,74)); x+=im.width+gap
s.convert('RGB').save(OUT+'screens-more.png',optimize=True)
