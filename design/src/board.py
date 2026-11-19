# Compose helium-svg-board.png from the 2x renders (v0.3: two rows of screens + component sheet).
from PIL import Image, ImageDraw, ImageFont
import os
P = os.path.join(os.path.dirname(__file__), '..', 'png')
row1 = [('01-home', '01 首页 · 简洁版'), ('02-chat', '02 对话 · 实时代理'), ('03-timeline', '03 任务时间线'),
        ('04-approval', '04 审批'), ('05-memory-skills', '05 记忆与技能（我的 › 二级页）'), ('06-schedule-projects', '06 任务 · 定时与项目')]
row2 = [('07-glance', '07 速览 Glance（长页）'), ('08-glance-custom', '08 速览 · 让 Helium 做一张卡'), ('09-me', '09 我的（长页）'), ('10-models', '10 模型与推理')]
sw, gap, m, top = 824, 64, 96, 220
W = m * 2 + 6 * sw + 5 * gap
comp = Image.open(f'{P}/00-components@2x.png')
cw = W - 2 * m; ch = round(comp.height * cw / comp.width)
ims2 = [Image.open(f'{P}/{s}@2x.png').convert('RGB') for s, _ in row2]
h1 = 1784; h2 = max(i.height for i in ims2)
y2 = top + h1 + 160
yc = y2 + h2 + 200
H = yc + ch + m
board = Image.new('RGB', (W, H), '#EEF0F8')
d = ImageDraw.Draw(board)
f1 = ImageFont.truetype('/home/sandbox/.fonts/NotoSansSC.ttf', 72); f1.set_variation_by_name('Bold')
f2 = ImageFont.truetype('/home/sandbox/.fonts/NotoSansSC.ttf', 34); f2.set_variation_by_name('Medium')
d.text((m, 70), 'Helium · 矢量设计稿 v0.3 (SVG) · 速览 / 我的 / 事件插画', font=f1, fill='#12131A')
d.text((m, 165), '412 dp 宽画板 · 2x 渲染 · 导航：首页 · 对话 · 速览 · 任务 · 每个视口 1 个英雄渐变 · 单一强调色 he-447 · 事件插画 hero / light 两版', font=f2, fill='#5A5F73')

def paste(im, x, y, label):
    mask = Image.new('L', im.size, 0); ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.width - 1, im.height - 1], 56, fill=255)
    board.paste(Image.new('RGB', im.size, '#D7DBEA'), (x + 6, y + 14), mask)
    board.paste(im, (x, y), mask)
    d.text((x + 8, y + im.height + 24), label, font=f2, fill='#12131A')

for i, (s, l) in enumerate(row1):
    paste(Image.open(f'{P}/{s}@2x.png').convert('RGB'), m + i * (sw + gap), top, l)
for i, (im, (s, l)) in enumerate(zip(ims2, row2)):
    paste(im, m + i * (sw + gap), y2, l)
board.paste(comp.convert('RGB').resize((cw, ch), Image.LANCZOS), (m, yc))
out = os.path.join(P, '..', 'helium-svg-board.png')
board.save(out, optimize=True)
sz = os.path.getsize(out)
print(board.size, sz)
if sz > 4 * 1024 * 1024:
    board.save(os.path.join(P, '..', 'helium-svg-board.jpg'), quality=80)
    print('jpg', os.path.getsize(os.path.join(P, '..', 'helium-svg-board.jpg')))
