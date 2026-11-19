# Helium SVG kit — hand-written vector primitives (Figma-friendly).
# Every text is a real <text>; every logical object is a named <g id="...">.
import math, random, re
from xml.sax.saxutils import escape
from PIL import ImageFont

FONT = "Google Sans Flex, Noto Sans SC, sans-serif"
GSF = "/home/sandbox/.fonts/GoogleSansFlex.ttf"

C = dict(he447='#4F6BFF', he492='#19C6E6', he501='#2FD6A0', he587='#FFC53D', he668='#FF6B6B',
         glow='#B9A7FF', blush='#FF9ECF', ink='#12131A', ink2='#5A5F73', ink3='#9AA0B4',
         canvas='#F4F5FB', surface='#FFFFFF', mist='#E9ECF7')
TINT = dict(blue='#E6EAFF', lilac='#EEE9FF', mint='#DDF8EE', amber='#FFF2D3', coral='#FFE3E3', pink='#FFE6F3', cyan='#DDF6FB')

import colorsys
def desat(hx, k=.8, lift=.03):
    """v0.2: pull saturation down (k=.8 → −20%) and lift lightness a touch → matte, misty gradients."""
    r, g, b = (int(hx[i:i+2], 16) / 255 for i in (1, 3, 5))
    h, l, s_ = colorsys.rgb_to_hls(r, g, b)
    r, g, b = colorsys.hls_to_rgb(h, min(1, l + lift), s_ * k)
    return '#%02X%02X%02X' % tuple(round(v * 255) for v in (r, g, b))

ACCENT = C['he447']          # the ONE accent colour (v0.2)
ACCENT_TINT = '#ECEEFF'      # accent at ~8% on white: active nav pill, selected states
HAIR = '#E6E8F0'             # 1px hairline on white cards
MIST2 = '#F0F2F8'            # very light mist card / chip fill
STATUS = dict(amber=C['he587'], amber_ink='#8A5A00', amber_tint='#FFF4D9',
              mint=C['he501'], mint_ink='#0E8A63', mint_tint='#E3F7EF')

_RAW = {
    'aurora': dict(base=[(0, C['he447']), (.52, C['glow']), (1, C['he492'])],
                   blobs=[(.0, 1.0, .55, .8, '#3F55FF', .95), (.5, .05, .32, .55, '#CDBEFF', .9),
                          (1.02, .55, .42, .9, '#12C2E8', .95), (.78, 1.05, .3, .5, '#7E8BFF', .6)]),
    'sunrise': dict(base=[(0, C['he587']), (.5, C['he668']), (1, C['blush'])],
                    blobs=[(.0, .0, .5, .8, '#FFC53D', .95), (.55, .75, .45, .7, '#FF6B6B', .9),
                           (1.05, .2, .4, .8, '#FF9ECF', .95), (.95, 1.05, .35, .5, '#FFB3D6', .7)]),
    'lagoon': dict(base=[(0, C['he501']), (1, C['he492'])],
                   blobs=[(.0, .0, .55, .8, '#6BEFC0', .9), (1.0, 1.0, .55, .8, '#12BCE0', .9), (.6, .3, .3, .5, '#A9F5E0', .5)]),
    'lilac': dict(base=[(0, C['glow']), (1, C['blush'])],
                  blobs=[(.0, .2, .5, .8, '#A895FF', .9), (1.0, .9, .5, .8, '#FFA3D2', .9), (.7, .0, .3, .5, '#E6D9FF', .6)]),
}
# v0.2 recipes: saturation −20%, lightness +3%, blobs 1.3× larger and 15% less opaque (bigger, more diffuse mesh)
_LIFT = {'aurora': -.06, 'sunrise': 0, 'lagoon': -.03, 'lilac': -.02}   # white-text heroes sit a touch deeper for AA-ish contrast
RECIPE = {k: dict(base=[(o, desat(c, .8, _LIFT[k])) for o, c in v['base']],
                  blobs=[(cx, cy, rx * 1.3, ry * 1.3, desat(c, .8, _LIFT[k]), op * .85) for cx, cy, rx, ry, c, op in v['blobs']])
          for k, v in _RAW.items()}

# ---------------------------------------------------------------- document
class Doc:
    def __init__(self, w, h, title):
        self.w, self.h, self.title = w, h, title
        self.defs = {}
        self.used = {}
        self.body = []

    def gid(self, name):
        n = self.used.get(name, 0) + 1
        self.used[name] = n
        return name if n == 1 else f"{name}-{n}"

    def add(self, *s):
        self.body.extend(s)

    def svg(self):
        d = "\n".join(self.defs.values())
        b = "\n".join(self.body)
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" '
                f'viewBox="0 0 {self.w} {self.h}" fill="none">\n<title>{escape(self.title)}</title>\n'
                f'<defs>\n{d}\n</defs>\n{b}\n</svg>\n')

    def save(self, path):
        open(path, 'w', encoding='utf-8').write(self.svg())

D = None
def new_doc(w, h, title):
    global D
    D = Doc(w, h, title)
    return D

def defn(key, content):
    if key not in D.defs:
        D.defs[key] = content
    return key

def f(v):
    return f"{v:.2f}".rstrip('0').rstrip('.') if isinstance(v, float) else str(v)

def G(name, inner, x=None, y=None, extra=''):
    t = f' transform="translate({f(x)} {f(y)})"' if x is not None else ''
    inner = "\n".join(inner) if isinstance(inner, (list, tuple)) else inner
    return f'<g id="{D.gid(name)}"{t}{extra}>\n{inner}\n</g>'

# ---------------------------------------------------------------- text
_fc = {}
def _font(size, weight):
    k = (size, weight)
    if k not in _fc:
        ft = ImageFont.truetype(GSF, size=int(size * 8))
        ax = ft.get_variation_axes()
        vals = [a['default'] for a in ax]
        for i, a in enumerate(ax):
            if a['name'] in (b'Weight', 'Weight'):
                vals[i] = weight
            if a['name'] in (b'Optical Size', 'Optical Size'):
                vals[i] = max(1, min(144, size))
        ft.set_variation_by_axes(vals)
        _fc[k] = ft
    return _fc[k]

def is_cjk(ch):
    o = ord(ch)
    return o >= 0x2E80 or ch in '，。、：；！？（）【】“”'

def tw(s, size, weight=400, ls=0):
    w = 0.0
    run = ''
    for ch in s:
        if is_cjk(ch):
            if run:
                w += _font(size, weight).getlength(run) / 8
                run = ''
            w += size
        else:
            run += ch
    if run:
        w += _font(size, weight).getlength(run) / 8
    return w + ls * len(s)

def T(x, y, s, size=16, weight=400, fill=None, anchor='start', opacity=None, ls=None, name=None, num=False):
    fill = fill or C['ink']
    a = [f'x="{f(x)}"', f'y="{f(y)}"', f'font-family="{FONT}"', f'font-size="{size}"', f'font-weight="{weight}"', f'fill="{fill}"']
    if anchor != 'start':
        a.append(f'text-anchor="{anchor}"')
    if opacity is not None:
        a.append(f'fill-opacity="{opacity}"')
    if ls is not None:
        a.append(f'letter-spacing="{ls}"')
    if num:
        a.append('style="font-variant-numeric: tabular-nums; font-variation-settings: \'ROND\' 100"')
    if name:
        a.insert(0, f'id="{D.gid(name)}"')
    return f'<text {" ".join(a)}>{escape(s)}</text>'

# ---------------------------------------------------------------- paint servers & filters
def lin(stops, x1=0, y1=0, x2=1, y2=1, key=None):
    key = key or 'lg-' + '-'.join(c.strip('#') + f"{int(o*100)}" for o, c in stops) + f"-{x1}{y1}{x2}{y2}".replace('.', '')
    st = "".join(f'<stop offset="{f(o)}" stop-color="{c}"/>' for o, c in stops)
    defn(key, f'<linearGradient id="{key}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{st}</linearGradient>')
    return f'url(#{key})'

def lin_user(stops, x1, y1, x2, y2, key):
    st = "".join(f'<stop offset="{f(o)}" stop-color="{c}"{"" if len(t)==2 else f" stop-opacity={chr(34)}{t[2]}{chr(34)}"}/>'
                 for t in stops for o, c in [t[:2]])
    defn(key, f'<linearGradient id="{key}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{st}</linearGradient>')
    return f'url(#{key})'

def rad(color, op=1.0, key=None):
    key = key or f"rg-{color.strip('#')}-{int(op*100)}"
    defn(key, f'<radialGradient id="{key}"><stop offset="0" stop-color="{color}" stop-opacity="{op}"/>'
              f'<stop offset="1" stop-color="{color}" stop-opacity="0"/></radialGradient>')
    return f'url(#{key})'

def blur(std):
    key = f"blur-{int(std)}"
    defn(key, f'<filter id="{key}" x="-60%" y="-60%" width="220%" height="220%" color-interpolation-filters="sRGB">'
              f'<feGaussianBlur stdDeviation="{std}"/></filter>')
    return f'url(#{key})'

def shadow(color='#4F6BFF', op=.10, dy=8, std=12):
    key = f"shadow-{color.strip('#')}-{int(op*100)}-{dy}-{std}"
    defn(key, f'<filter id="{key}" x="-30%" y="-30%" width="160%" height="180%" color-interpolation-filters="sRGB">'
              f'<feDropShadow dx="0" dy="{dy}" stdDeviation="{std}" flood-color="{color}" flood-opacity="{op}"/></filter>')
    return f'url(#{key})'

def clip_rr(w, h, r):
    key = f"clip-{f(w)}x{f(h)}r{f(r)}".replace('.', '_')
    defn(key, f'<clipPath id="{key}"><rect width="{f(w)}" height="{f(h)}" rx="{f(r)}"/></clipPath>')
    return f'url(#{key})'

AUR_SUN = None
def aurora_sunrise():
    return lin([(0, desat(C['he492'])), (.3, desat(C['he447'])), (.55, desat(C['glow'])), (.8, desat(C['blush'])), (1, desat(C['he587']))], 0, 0, 1, 1, key='lg-aurora-sunrise')

# ---------------------------------------------------------------- surfaces
def card_shadow():
    """v0.2 unified soft, low card shadow (neutral ink-blue, 6%)."""
    return shadow('#1B2150', .06, 6, 12)

def grain():
    key = 'grain'
    defn(key, '<filter id="grain" x="0" y="0" width="100%" height="100%" color-interpolation-filters="sRGB">'
              '<feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" seed="7" stitchTiles="stitch"/>'
              '<feColorMatrix values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 1.4 -.55"/></filter>')
    return 'url(#grain)'

def mesh(w, h, r, recipe, name='mesh', shadow_color=None, shadow_op=.18, gloss=True, noise=True):
    """Hero gradient surface. v0.2: matte/misty mesh + glass light sweep + top-edge highlight + optional grain."""
    rc = RECIPE[recipe]
    base = lin(rc['base'], 0, .1, 1, .9, key=f'lg-base-{recipe}')
    sc = shadow_color or rc['base'][1][1]
    out = []
    if shadow_op:
        out.append(f'<rect id="{D.gid(name+"-shadow")}" x="8" y="4" width="{f(w-16)}" height="{f(h-4)}" rx="{f(r)}" fill="{sc}" filter="{shadow(sc, shadow_op, 10, 16)}"/>')
    inner = [f'<rect width="{f(w)}" height="{f(h)}" fill="{base}"/>']
    bstd = max(8, round(min(w, h) * .14))
    for i, (cx, cy, rx, ry, col, op) in enumerate(rc['blobs']):
        inner.append(f'<ellipse cx="{f(cx*w)}" cy="{f(cy*h)}" rx="{f(rx*w)}" ry="{f(ry*h)}" fill="{rad(col, round(op, 2))}" filter="{blur(bstd)}"/>')
    # leading-edge soft light ("前沿实、尾部散")
    inner.append(f'<ellipse cx="{f(.78*w)}" cy="{f(-.1*h)}" rx="{f(.55*w)}" ry="{f(.5*h)}" fill="{rad("#FFFFFF", .16)}" filter="{blur(bstd)}"/>')
    if gloss:  # diagonal glass light sweep (Riffs: "glass light sweep")
        sweep = lin_user([(0, '#FFFFFF', 0), (.5, '#FFFFFF', .18), (1, '#FFFFFF', 0)], 0, 0, w * .5, h * .5 + 1, key=f'lg-sweep-{f(w)}x{f(h)}'.replace('.', '_'))
        inner.append(G(name + '-gloss', f'<path d="M{f(w*.38)} 0H{f(w*.62)}L{f(w*.30)} {f(h)}H{f(w*.06)}Z" fill="{sweep}" filter="{blur(6)}"/>'))
    if noise:
        inner.append(G(name + '-grain', f'<rect width="{f(w)}" height="{f(h)}" filter="{grain()}" opacity=".06" style="mix-blend-mode:soft-light"/>'))
    out.append(G(name + '-fill', inner, extra=f' clip-path="{clip_rr(w, h, r)}"'))
    # inner top-edge highlight (glossy rim) + faint rim
    rim = lin_user([(0, '#FFFFFF', .7), (.35, '#FFFFFF', .18), (1, '#FFFFFF', .08)], 0, 0, 0, h, key=f'lg-rim-{f(h)}'.replace('.', '_'))
    out.append(f'<rect id="{D.gid(name+"-rim")}" width="{f(w-1)}" height="{f(h-1)}" x=".5" y=".5" rx="{f(r-.5)}" stroke="{rim}"/>')
    return "\n".join(out)

def light(w, h, r, tint=None, name='surface', blob=(1.0, 0.0), stroke=HAIR, sh=True, op=.35, fill='#FFFFFF'):
    """White / mist card. v0.2: hairline + unified soft shadow; tint (if any) is a faint mist, not a colour wash."""
    out = []
    out.append(f'<rect id="{D.gid(name+"-bg")}" width="{f(w)}" height="{f(h)}" rx="{f(r)}" fill="{fill}"'
               + (f' filter="{card_shadow()}"' if sh else '') + '/>')
    if tint:
        bstd = max(8, round(min(w, h) * .16))
        inner = [f'<ellipse cx="{f(blob[0]*w)}" cy="{f(blob[1]*h)}" rx="{f(.8*w)}" ry="{f(1.0*h)}" fill="{rad(tint, op)}" filter="{blur(bstd)}"/>']
        out.append(G(name + '-tint', inner, extra=f' clip-path="{clip_rr(w, h, r)}"'))
    if stroke:
        sw = stroke[1] if isinstance(stroke, tuple) else 1
        sc = stroke[0] if isinstance(stroke, tuple) else stroke
        out.append(f'<rect x="{sw/2}" y="{sw/2}" width="{f(w-sw)}" height="{f(h-sw)}" rx="{f(r-sw/2)}" stroke="{sc}" stroke-width="{sw}"/>')
    return "\n".join(out)

def glass_icon(name, d=40, shape='circle', color=None, isize=None, on_hero=False, gname='glass-icon'):
    """v0.2 icon tile: neutral glass base (two layers + highlight) + single accent icon. Not a flat colour block."""
    color = color or (('#FFFFFF') if on_hero else ACCENT)
    isize = isize or round(d * .55)
    r = d / 2
    if shape == 'circle':
        sp = lambda fill, extra='': f'<circle cx="{f(r)}" cy="{f(r)}" r="{f(r)}" fill="{fill}"{extra}/>'
    elif shape == 'scallop':
        pth = lobed(r, r, r, 12, .07)
        sp = lambda fill, extra='': f'<path d="{pth}" fill="{fill}"{extra}/>'
    elif shape == 'flower':
        pth = lobed(r, r, r, 8, .1, steps=320)
        sp = lambda fill, extra='': f'<path d="{pth}" fill="{fill}"{extra}/>'
    elif shape == 'pill':
        sp = lambda fill, extra='': f'<rect y="{f(d*.15)}" width="{f(d)}" height="{f(d*.7)}" rx="{f(d*.35)}" fill="{fill}"{extra}/>'
    else:
        sp = lambda fill, extra='': f'<rect width="{f(d)}" height="{f(d)}" rx="{f(d*.3)}" fill="{fill}"{extra}/>'
    out = []
    if on_hero:
        out.append(sp('#FFFFFF', ' fill-opacity=".2"'))
        out.append(sp('none', ' stroke="#FFFFFF" stroke-opacity=".45"'))
    else:
        base = lin([(0, '#FFFFFF'), (1, '#E9ECF5')], 0, 0, 0, 1, key='lg-glass-base')
        out.append(sp(base, f' filter="{shadow("#1B2150", .08, 2, 3)}"'))
        # inner accent glow (second layer)
        out.append(G('glass-glow', f'<circle cx="{f(r)}" cy="{f(r*1.15)}" r="{f(r*.7)}" fill="{rad(color, .16, "rg-glass-acc-"+color.strip("#"))}"/>'))
        hl = lin_user([(0, '#FFFFFF', .95), (.5, '#FFFFFF', .2), (1, '#FFFFFF', 0)], 0, 0, 0, d, key=f'lg-glass-hl-{d}')
        out.append(sp('none', f' stroke="{hl}" stroke-width="1.5"'))
        out.append(sp('none', f' stroke="#DCE0EC" stroke-opacity=".7" stroke-width=".75"'))
        out.append(f'<ellipse cx="{f(r*.75)}" cy="{f(r*.45)}" rx="{f(r*.45)}" ry="{f(r*.2)}" fill="#FFFFFF" fill-opacity=".8" filter="{blur(2)}"/>')
    out.append(icon(name, (d - isize) / 2, (d - isize) / 2, isize, color))
    return G(gname, out)

# ---------------------------------------------------------------- shapes
def lobed(cx, cy, R, n=12, amp=.08, rot=0.0, steps=None):
    steps = steps or n * 24
    pts = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        r = R * (1 - amp + amp * math.cos(n * (t + rot)))
        pts.append((cx + r * math.cos(t - math.pi / 2), cy + r * math.sin(t - math.pi / 2)))
    return "M" + " L".join(f"{x:.2f} {y:.2f}" for x, y in pts) + " Z"

# ---------------------------------------------------------------- icons (24px grid, 2px stroke, round)
ICON = {
    'glance': '<rect x="3.5" y="3.5" width="17" height="17" rx="5"/><path d="M8 9h8M8 12.5h8M8 16h4.5"/>',
    'tasks': '<path d="M10 6.5h10M10 12h10M10 17.5h7"/><path d="M3.8 6.5l1.6 1.6 2.8-3M3.8 12l1.6 1.6 2.8-3"/><circle cx="5.6" cy="17.5" r="1.6"/>',
    'heart': '<path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.3a4.3 4.3 0 0 1 7.5 2.5C19.5 15.4 12 20 12 20z"/>',
    'repeat': '<path d="M4 10V9a3 3 0 0 1 3-3h12l-3-3M20 14v1a3 3 0 0 1-3 3H5l3 3"/>',
    'link': '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
    'cpu': '<rect x="6" y="6" width="12" height="12" rx="3"/><rect x="9.5" y="9.5" width="5" height="5" rx="1"/><path d="M9 3v3M15 3v3M9 18v3M15 18v3M3 9h3M3 15h3M18 9h3M18 15h3"/>',
    'plug': '<path d="M9 3v5M15 3v5M6.5 8h11v3a5.5 5.5 0 0 1-11 0zM12 16.5V21"/>',
    'server': '<rect x="4" y="4" width="16" height="7" rx="2.5"/><rect x="4" y="13" width="16" height="7" rx="2.5"/><path d="M8 7.5h.01M8 16.5h.01" stroke-width="2.6"/>',
    'memory': '<path d="M12 4.5a7.5 7.5 0 1 0 7.5 7.5"/><path d="M12 8.5a3.5 3.5 0 1 0 3.5 3.5"/><path d="M15.5 3.5l1 2.5 2.5 1-2.5 1-1 2.5-1-2.5-2.5-1 2.5-1z"/>',
    'file': '<path d="M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"/><path d="M14 3v5h5M9 13h6M9 17h4"/>',
    'folder': '<path d="M3.5 7.5A2.5 2.5 0 0 1 6 5h3.5l2 2.5H18a2.5 2.5 0 0 1 2.5 2.5v7.5A2.5 2.5 0 0 1 18 20H6a2.5 2.5 0 0 1-2.5-2.5z"/>',
    'list': '<path d="M9 6.5h11M9 12h11M9 17.5h11M4.5 6.5h.01M4.5 12h.01M4.5 17.5h.01" />',
    'key': '<circle cx="8" cy="15" r="4.5"/><path d="M11.2 11.8L20 3M16.5 6.5l2.5 2.5M14 9l2 2"/>',
    'bell': '<path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
    'palette': '<path d="M12 3.5a8.5 8.5 0 0 0 0 17c1.3 0 1.8-1 1.3-2-.6-1.2.2-2.5 1.5-2.5H17a3.5 3.5 0 0 0 3.5-3.5c0-5-3.8-9-8.5-9z"/><path d="M8 11h.01M11 7.5h.01M15.5 8.5h.01" stroke-width="2.6"/>',
    'globe': '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.4 2.4 3.5 5.2 3.5 8.5s-1.1 6.1-3.5 8.5c-2.4-2.4-3.5-5.2-3.5-8.5S9.6 5.9 12 3.5z"/>',
    'info': '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.8h.01"/>',
    'train': '<rect x="5" y="3.5" width="14" height="14" rx="4"/><path d="M5 11h14M9 21l1.5-3.5M15 21l-1.5-3.5M8.5 14.5h.01M15.5 14.5h.01"/>',
    'chart': '<path d="M4 19.5h16"/><path d="M5 15l4.5-4.5 3 3L19 7"/><path d="M15 7h4v4"/>',
    'search': '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4 4"/>',
    'refresh': '<path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3L19.5 9"/><path d="M19.5 4v5h-5"/>',
    'mic': '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21"/>',
    'plus': '<path d="M12 5v14M5 12h14"/>',
    'check': '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    'arrow-left': '<path d="M19 12H5M11 6l-6 6 6 6"/>',
    'arrow-up': '<path d="M12 19V5M6 11l6-6 6 6"/>',
    'close': '<path d="M6 6l12 12M18 6L6 18"/>',
    'chevron-right': '<path d="M9 6l6 6-6 6"/>',
    'plane': '<path d="M12 2.75c.9 0 1.6.8 1.6 1.8v5.1l7.15 4.1v2l-7.15-2.2v4.6l2.2 1.7v1.6L12 20.5l-3.8 1V19.9l2.2-1.7v-4.6l-7.15 2.2v-2l7.15-4.1v-5.1c0-1 .7-1.8 1.6-1.8z"/>',
    'mail': '<rect x="3" y="5" width="18" height="14" rx="3"/><path d="M4 7.5l8 6 8-6"/>',
    'utensils': '<path d="M5 3v5a2 2 0 0 0 4 0V3M7 10v11M18 21V3c-2.2 1.4-3.5 4-3.5 7.2 0 2 1.3 3.3 3.5 3.3"/>',
    'bag': '<path d="M5 8h14l-1.2 11.2A2 2 0 0 1 15.8 21H8.2a2 2 0 0 1-2-1.8z"/><path d="M9 10V7a3 3 0 0 1 6 0v3"/>',
    'package': '<path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z"/><path d="M4 7.5l8 4.5 8-4.5M12 12v9M8 5.25l8 4.5"/>',
    'sun': '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"/>',
    'cloud-sun': '<path d="M8.5 3.5v1.5M3.6 8.5H5M4.9 4.9l1 1M12.1 4.9l-1 1"/><path d="M5.6 11.3A3.6 3.6 0 0 1 11.7 7.7"/><path d="M8 20h8.5a4 4 0 0 0 .6-7.95A5.2 5.2 0 0 0 7.2 13.3 3.4 3.4 0 0 0 8 20z"/>',
    'chat': '<path d="M4 6.5A3.5 3.5 0 0 1 7.5 3h9A3.5 3.5 0 0 1 20 6.5v7a3.5 3.5 0 0 1-3.5 3.5H10l-5 4v-4.4A3.5 3.5 0 0 1 4 13.5z"/>',
    'sparkle': '<path d="M12 3.5l1.8 5.2a2 2 0 0 0 1.2 1.2l5.2 1.8-5.2 1.8a2 2 0 0 0-1.2 1.2L12 20l-1.8-5.3a2 2 0 0 0-1.2-1.2L3.8 11.7 9 9.9a2 2 0 0 0 1.2-1.2z"/>',
    'home': '<path d="M4 10.5l8-6.5 8 6.5V19a2 2 0 0 1-2 2h-3.5v-6h-5v6H6a2 2 0 0 1-2-2z"/>',
    'clock': '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/>',
    'person': '<circle cx="12" cy="8" r="4"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/>',
    'receipt': '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2z"/><path d="M9 8h6M9 12h6M9 16h3"/>',
    'lock': '<rect x="5" y="10.5" width="14" height="10.5" rx="2.5"/><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"/>',
    'card': '<rect x="3" y="5" width="18" height="14" rx="3"/><path d="M3 10h18M7 15h4"/>',
    'pin': '<path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.3"/>',
    'expand': '<path d="M14 4h6v6M10 20H4v-6M20 4l-6.5 6.5M4 20l6.5-6.5"/>',
    'pause': '<path d="M9 6v12M15 6v12"/>',
    'hand': '<path d="M8 12V5.5a1.5 1.5 0 0 1 3 0V11m0-6.5a1.5 1.5 0 0 1 3 0V11m0-4.5a1.5 1.5 0 0 1 3 0V14a7 7 0 0 1-7 7h-.5a6 6 0 0 1-4.9-2.5L3.5 15a1.6 1.6 0 0 1 2.5-2L8 15"/>',
    'calendar': '<rect x="3.5" y="5" width="17" height="15.5" rx="3"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
    'briefcase': '<rect x="3" y="7" width="18" height="13" rx="3"/><path d="M9 7V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V7M3 12.5h18"/>',
    'more': '<path d="M12 5.5h.01M12 12h.01M12 18.5h.01" stroke-width="3"/>',
    'shield': '<path d="M12 3l7.5 3v5.5c0 4.6-3.2 8.3-7.5 9.5-4.3-1.2-7.5-4.9-7.5-9.5V6z"/><path d="M8.8 12l2.2 2.2 4.2-4.4"/>',
    'stop': '',
    'grid': '<rect x="4" y="4" width="7" height="7" rx="2"/><rect x="13" y="4" width="7" height="7" rx="2"/><rect x="4" y="13" width="7" height="7" rx="2"/><rect x="13" y="13" width="7" height="7" rx="2"/>',
    'truck': '<path d="M3 6.5h11v10H3zM14 10h3.8l3.2 3.2v3.3h-7"/><circle cx="7" cy="17.5" r="1.8"/><circle cx="17" cy="17.5" r="1.8"/>',
}

def icon(name, x, y, size=24, color=None, sw=2, gname=None):
    color = color or C['ink']
    s = size / 24
    if name == 'stop':
        inner = '<rect x="7" y="7" width="10" height="10" rx="2.5" fill="currentColor" stroke="none"/>'
    else:
        inner = ICON[name]
    return (f'<g id="{D.gid(gname or "icon-" + name)}" transform="translate({f(x)} {f(y)}) scale({f(s)})" '
            f'stroke="{color}" color="{color}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round" fill="none">'
            f'{inner.replace("currentColor", color)}</g>')

# ---------------------------------------------------------------- system chrome
def status_bar(color=None, W=412):
    color = color or C['ink']
    sig = "".join(f'<rect x="{f(i*4.5)}" y="{f(13-h)}" width="3" height="{h}" rx="1" fill="{color}"/>' for i, h in enumerate([4, 7, 10, 13]))
    wifi = (f'<path d="M8 13.5L0.4 4.8A11.5 11.5 0 0 1 15.6 4.8Z" fill="{color}"/>')
    bat = (f'<rect x=".5" y=".5" width="23" height="12" rx="3.5" stroke="{color}" stroke-opacity=".45"/>'
           f'<rect x="2.5" y="2.5" width="17" height="8" rx="1.8" fill="{color}"/>'
           f'<rect x="24.5" y="4" width="1.8" height="5" rx=".9" fill="{color}" fill-opacity=".45"/>')
    return G('status-bar', [
        T(24, 30, '9:41', 15, 600, color, name='time'),
        G('signal', sig, W - 98, 15), G('wifi', wifi, W - 75, 15.5), G('battery', bat, W - 51, 16),
    ])

def gesture_bar(W=412, H=892):
    return G('gesture-bar', f'<rect x="{(W-108)/2}" y="{H-14}" width="108" height="4" rx="2" fill="{C["ink"]}" fill-opacity=".85"/>')

def background(seed=1, orbs=(), W=412, H=892, dots=6, top_glow=None):
    rnd = random.Random(seed)
    els = [f'<rect id="canvas" width="{W}" height="{H}" fill="{C["canvas"]}"/>']
    cols = [C['he447'], C['he492'], C['he501'], C['he587'], C['he668'], C['glow'], C['blush']]
    o = []
    for (cx, cy, r, col, op) in orbs:
        o.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{rad(col, op)}" filter="{blur(max(2, r*.25))}"/>')
    d = []
    for i in range(dots):
        x, y = rnd.uniform(8, W - 8), rnd.uniform(60, H - 120)
        r = rnd.choice([1, 1.25, 1.5, 2])
        d.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r}" fill="{rnd.choice([ACCENT, C["ink3"]])}" fill-opacity="{rnd.choice([.18,.25])}"/>')
    els.append(G('bg-orbs', o))
    els.append(G('bg-particles', d))
    return G('background', els)

# ---------------------------------------------------------------- components
def toggle(on=True, color=None, on_sat=False, name='switch'):
    color = color or C['he447']
    if on:
        track = (f'<rect width="52" height="32" rx="16" fill="#12131A" fill-opacity=".28"/>' if on_sat
                 else f'<rect width="52" height="32" rx="16" fill="{color}"/>')
        knob = f'<circle cx="36" cy="16" r="12" fill="#FFFFFF"/>'
        if not on_sat:
            knob += f'<path d="M31.5 16.2l3 3 6-6.2" stroke="{color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>'
    else:
        track = f'<rect x="1" y="1" width="50" height="30" rx="15" fill="{C["mist"]}" stroke="{C["ink3"]}" stroke-width="2"/>'
        knob = f'<circle cx="16" cy="16" r="8" fill="{C["ink3"]}"/>'
    return [track, knob]

def btn(w, h, label, kind='primary', ico=None, size=16):
    fills = {'primary': (C['ink'], '#FFFFFF', None), 'tonal': (MIST2, C['ink'], None),
             'outline': ('#FFFFFF', C['ink'], '#CDD1DE'), 'text': ('none', C['he447'], None),
             'white': ('#FFFFFF', C['ink'], None), 'brand': (C['he447'], '#FFFFFF', None)}
    bg, fg, st = fills[kind]
    out = []
    if kind == 'white':
        out.append(f'<rect width="{w}" height="{h}" rx="{h/2}" fill="#FFFFFF" filter="{card_shadow()}"/>')
        out.append(f'<rect x=".5" y=".5" width="{w-1}" height="{h-1}" rx="{(h-1)/2}" stroke="{HAIR}"/>')
    elif kind == 'outline':
        out.append(f'<rect x=".5" y=".5" width="{w-1}" height="{h-1}" rx="{(h-1)/2}" fill="{bg}" stroke="{st}"/>')
    elif bg != 'none':
        out.append(f'<rect width="{w}" height="{h}" rx="{h/2}" fill="{bg}"/>')
    lw = tw(label, size, 500)
    if ico:
        total = 20 + 8 + lw
        x0 = (w - total) / 2
        out.append(icon(ico, x0, (h - 20) / 2, 20, fg))
        out.append(T(x0 + 28, h / 2 + size * .36, label, size, 600 if kind == 'primary' else 500, fg))
    else:
        out.append(T(w / 2, h / 2 + size * .36, label, size, 500, fg, 'middle'))
    return out

def chip(label, fill, h=40, size=15, weight=500, color=None, pad=18, stroke=None):
    w = round(tw(label, size, weight) + pad * 2)
    out = [f'<rect width="{w}" height="{h}" rx="{h/2}" fill="{fill}"' + (f' stroke="{stroke}" stroke-width="1.5"' if stroke else '') + '/>',
           T(w / 2, h / 2 + size * .36, label, size, weight, color or C['ink'], 'middle')]
    return out, w

def circle_btn(d, fill, ico, icolor, isize=24, name='button', sh=None):
    out = [f'<circle cx="{d/2}" cy="{d/2}" r="{d/2}" fill="{fill}"' + (f' filter="{sh}"' if sh else '') + '/>']
    out.append(icon(ico, (d - isize) / 2, (d - isize) / 2, isize, icolor))
    return out

def composer(w=364, h=56, state='idle', text=None):
    """Input capsule. states: idle / typing / voice / running"""
    sop = {'idle': .22, 'typing': .45, 'voice': .7, 'running': .55}[state]
    g = aurora_sunrise()
    out = [f'<rect id="{D.gid("composer-fill")}" width="{w}" height="{h}" rx="{h/2}" fill="#FFFFFF" filter="{card_shadow()}"/>',
           f'<rect id="{D.gid("composer-hairline")}" x=".5" y=".5" width="{w-1}" height="{h-1}" rx="{(h-1)/2}" stroke="{HAIR}"/>',
           f'<rect id="{D.gid("composer-stroke")}" x=".5" y=".5" width="{w-1}" height="{h-1}" rx="{(h-1)/2}" stroke="{g}" stroke-width="1" stroke-opacity="{sop}"/>']
    out.append(G('btn-attach', circle_btn(40, MIST2, 'plus', C['ink'], 22), 8, (h - 40) / 2))
    ty = h / 2 + 16 * .36
    if state == 'idle':
        out.append(T(60, ty, text or '让 Helium 帮你…', 16, 400, C['ink2'], name='placeholder'))
        out.append(G('btn-skills', circle_btn(40, '#FFFFFF', 'sparkle', ACCENT, 22), w - 96, (h - 40) / 2))
        out.append(G('btn-mic', circle_btn(40, C['ink'], 'mic', '#FFFFFF', 22), w - 48, (h - 40) / 2))
    elif state == 'typing':
        s = text or '帮我订周六晚上两人的餐厅'
        out.append(T(60, ty, s, 16, 400, C['ink'], name='input-text'))
        cx = 60 + tw(s, 16) + 2
        out.append(f'<rect id="{D.gid("caret")}" x="{f(cx)}" y="{h/2-10}" width="2" height="20" rx="1" fill="{C["he447"]}"/>')
        out.append(G('btn-send', circle_btn(40, C['he447'], 'arrow-up', '#FFFFFF', 22), w - 48, (h - 40) / 2))
    elif state == 'voice':
        out.append(T(60, ty, text or '正在聆听…', 16, 400, C['ink2'], name='voice-label'))
        bars = []
        hs = [6, 12, 18, 10, 22, 14, 8, 16, 20, 10, 6, 14, 18, 8]
        wl = ACCENT
        for i, bh in enumerate(hs):
            bars.append(f'<rect x="{i*7}" y="{-bh/2}" width="3.5" height="{bh}" rx="1.75" fill="{wl}" fill-opacity="{.45 + .55*(bh/22):.2f}"/>')
        out.append(G('waveform', bars, 156, h / 2))
        out.append(G('btn-mic-active', circle_btn(40, C['he447'], 'mic', '#FFFFFF', 22), w - 48, (h - 40) / 2))
    else:
        out.append(T(60, ty, text or '继续说…', 16, 400, C['ink2'], name='placeholder'))
        out.append(G('btn-stop', circle_btn(40, C['ink'], 'stop', '#FFFFFF', 22), w - 48, (h - 40) / 2))
    return out

def agent_orb(d=96, name='agent-orb', active=False):
    """v0.2: idle orb is quiet (惰性) — frosted glass sphere + single-accent scallop; only glows when active."""
    r = d / 2
    a = lin([(0, desat(C['he492'])), (.55, ACCENT), (1, desat(C['glow']))], 0, 0, 1, 1, key='lg-orb')
    glass = lin([(0, '#FFFFFF'), (1, '#EEF0F8')], 0, 0, 0, 1, key='lg-orb-glass')
    hl = lin_user([(0, '#FFFFFF', 1), (.5, '#FFFFFF', .2), (1, '#FFFFFF', 0)], 0, 0, 0, d, key=f'lg-orb-rim-{d}')
    out = [f'<circle cx="{r}" cy="{r}" r="{r}" fill="{glass}" filter="{shadow("#1B2150" if not active else ACCENT, .08 if not active else .22, 8, 14)}"/>',
           f'<circle cx="{r}" cy="{r}" r="{r-.75}" stroke="{hl}" stroke-width="1.5"/>',
           f'<circle cx="{r}" cy="{r}" r="{r-.5}" stroke="#DCE0EC" stroke-opacity=".6"/>',
           f'<circle cx="{r}" cy="{r*1.08}" r="{r*.62}" fill="{rad(ACCENT, .14 if not active else .3, "rg-orb-halo")}"/>',
           f'<path id="{D.gid("agent-scallop")}" d="{lobed(r, r, r*.56, 12, .07)}" fill="{a}"/>',
           f'<circle cx="{r*.84}" cy="{r*.8}" r="{r*.26}" fill="{rad("#FFFFFF", .75, "rg-orb-hl")}"/>']
    return out

def flower_avatar(d=24, name='agent-avatar'):
    r = d / 2
    a = lin([(0, desat(C['he492'])), (.55, ACCENT), (1, desat(C['glow']))], 0, 0, 1, 1, key='lg-orb')
    return [f'<path d="{lobed(r, r, r, 12, .08)}" fill="{a}"/>', f'<circle cx="{r*.8}" cy="{r*.75}" r="{r*.35}" fill="{rad("#FFFFFF", .7, "rg-orb-hl")}"/>']

def toggle_g(on, color=None, on_sat=False, name='switch', x=0, y=0):
    return G(name, toggle(on, color, on_sat), x, y)

def bottom_nav(active='home', items=('home', 'chat', 'glance', 'tasks')):
    """v0.3: 首页 · 对话 · 速览 · 任务. white bar + hairline; active = accent-tint pill + icon + label. active=None → no selection."""
    labels = {'home': '首页', 'chat': '对话', 'glance': '速览', 'tasks': '任务', 'sparkle': '技能', 'clock': '定时', 'person': '我的'}
    slots = []
    for it in items:
        slots.append(56 if it != active else 24 + 24 + 6 + tw(labels[it], 14, 600) + 2)
    if active is None:
        slots = [64] * len(items)
    w = 16 + sum(slots) + 4 * (len(items) - 1)
    out = [f'<rect width="{f(w)}" height="64" rx="32" fill="#FFFFFF" filter="{card_shadow()}"/>',
           f'<rect x=".5" y=".5" width="{f(w-1)}" height="63" rx="31.5" stroke="{HAIR}"/>']
    x = 8
    for it, sw_ in zip(items, slots):
        inner = []
        if it == active:
            inner.append(f'<rect width="{f(sw_)}" height="48" rx="24" fill="{ACCENT_TINT}"/>')
            inner.append(icon(it, 12, 12, 24, ACCENT))
            inner.append(T(42, 29, labels[it], 14, 600, ACCENT))
        else:
            inner.append(icon(it, (sw_ - 24) / 2, 12, 24, C['ink2']))
        out.append(G(f'nav-{it}' + ('-active' if it == active else ''), inner, x, 8))
        x += sw_ + 4
    return out, w


# ---------------------------------------------------------------- v0.3 event illustrations
# Hand-drawn-ish 2.5D vector spots on a 120×96 grid. Two tones:
#   hero  — white at 92/75/55% + ink shade 20% + highlight; sits on a hero gradient
#   light — accent-tinted mist (#EEF1FF→#D9DFFF) + accent details; sits on a white card
ILLOS = ['flight', 'train', 'sunny', 'cloudy', 'rain', 'package', 'truck', 'hotel', 'dining', 'calendar', 'chart']
ILLO_LABEL = dict(flight='航班', train='高铁', sunny='晴', cloudy='多云', rain='雨', package='包裹', truck='货车',
                  hotel='酒店', dining='餐厅', calendar='日程', chart='价格走势')

def _ipal(tone):
    if tone == 'hero':
        return dict(face=('#FFFFFF', .95, '#FFFFFF', .7), top=('#FFFFFF', .8), side=('#FFFFFF', .48),
                    det=('#1B2150', .22), soft=('#FFFFFF', .32), hl=('#FFFFFF', 1), shadow=('#1B2150', .14), ray=('#FFFFFF', .85))
    return dict(face=('#F3F5FF', 1, '#D6DCFF', 1), top=('#E6EAFF', 1), side=('#C3CCFF', 1),
                det=(ACCENT, .7), soft=(ACCENT, .12), hl=('#FFFFFF', .95), shadow=(ACCENT, .14), ray=(ACCENT, .42))

def _fill(p, k):
    c, o = p[k][0], p[k][1]
    return f'fill="{c}" fill-opacity="{o}"'

def _stroke(p, k, w=2):
    c, o = p[k][0], p[k][1]
    return f'stroke="{c}" stroke-opacity="{o}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"'

def _face(tone):
    p = _ipal(tone)
    c1, o1, c2, o2 = p['face']
    key = f'lg-illo-face-{tone}'
    defn(key, f'<linearGradient id="{key}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{c1}" stop-opacity="{o1}"/>'
              f'<stop offset="1" stop-color="{c2}" stop-opacity="{o2}"/></linearGradient>')
    return f'fill="url(#{key})"'

def _cloud(cx, cy, s, attrs):
    return (f'<g {attrs}><circle cx="{f(cx-13*s)}" cy="{f(cy)}" r="{f(10*s)}"/><circle cx="{f(cx+1*s)}" cy="{f(cy-7*s)}" r="{f(14*s)}"/>'
            f'<circle cx="{f(cx+15*s)}" cy="{f(cy-1*s)}" r="{f(10*s)}"/><rect x="{f(cx-23*s)}" y="{f(cy-2*s)}" width="{f(48*s)}" height="{f(12*s)}" rx="{f(6*s)}"/></g>')

def illo(kind, x=0, y=0, scale=1.0, tone='hero', name=None):
    p = _ipal(tone)
    F, FT, FS, FD, FSO, FH = _face(tone), _fill(p, 'top'), _fill(p, 'side'), _fill(p, 'det'), _fill(p, 'soft'), _fill(p, 'hl')
    sh = f'<ellipse cx="60" cy="90" rx="38" ry="4.5" fill="{p["shadow"][0]}" fill-opacity="{p["shadow"][1]}" filter="{blur(3)}"/>'
    glint = lambda cx, cy, rx, ry: f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" {FH} filter="{blur(1.5)}"/>'
    o = []
    if kind == 'flight':
        o.append(f'<path d="M4 64h18M10 72h12M0 56h10" {_stroke(p, "soft", 3)}/>')
        o.append(_cloud(98, 82, .6, FSO))
        pl = [f'<path d="M56 44L46 18Q45 14 49 14H56L76 44Z" {FS}/>',
              f'<path d="M24 44L16 26Q15 23 18 23H25L36 44Z" {FS}/>',
              f'<path d="M22 42H96C108 42 116 47 116 51C116 55 108 60 96 60H26C18 60 14 56 14 51C14 46 17 42 22 42Z" {F}/>',
              f'<path d="M16 55C20 59 24 60 28 60H96C105 60 112 57.5 114.5 55Z" {FS}/>',
              ''.join(f'<circle cx="{40+i*8}" cy="48.5" r="1.9" {FD}/>' for i in range(7)),
              f'<path d="M103 45.5C107.5 45.8 110.5 47.6 112.2 50H103Z" {FD}/>',
              f'<path d="M54 54L42 84Q41 88 45 88H52L78 54Z" {F}/>',
              f'<path d="M54 54L49 66H54L66 54Z" {FT}/>',
              f'<path d="M24 54L18 66H24L36 54Z" {FT}/>',
              f'<path d="M27 44.6H92" {_stroke(p, "hl", 1.6)}/>', glint(98, 45, 5, 1.4)]
        o.append(f'<g transform="rotate(-14 60 50)">{"".join(pl)}</g>')
    elif kind == 'train':
        o.append(f'<path d="M0 82H120" {_stroke(p, "soft", 2.5)}/>')
        o.append(''.join(f'<path d="M{8+i*14} 79v6" {_stroke(p, "soft", 2)}/>' for i in range(8)))
        o.append(f'<path d="M0 38h10M2 52h6" {_stroke(p, "soft", 3)}/>')
        o.append(f'<path d="M12 30H74C90 30 103 37 112 48L106 48C98 41 88 38 78 38H8Z" {FT}/>')
        o.append(f'<path d="M8 38H78C96 38 110 50 116 64Q118 72 111 72H8Q4 72 4 68V42Q4 38 8 38Z" {F}/>')
        o.append(f'<path d="M4 62H116Q117 72 111 72H8Q4 72 4 68Z" {FS}/>')
        o.append(f'<rect x="12" y="45" width="66" height="9" rx="4.5" {FD}/>')
        o.append(f'<path d="M86 43C97 45 106 52 110.5 59H95C93 52 90.5 47 86 43Z" {FD}/>')
        o.append(''.join(f'<rect x="{12+i*17}" y="46.5" width="13" height="6" rx="3" {FH} fill-opacity=".35"/>' for i in range(4)))
        o.append(f'<path d="M8 58H112" {_stroke(p, "det", 1.5)}/>')
        o.append(f'<path d="M14 40.6H80" {_stroke(p, "hl", 1.6)}/>' + glint(98, 47, 4, 1.4))
        o.append(f'<rect x="18" y="72" width="16" height="6" rx="3" {FS}/><rect x="80" y="72" width="16" height="6" rx="3" {FS}/>')
    elif kind in ('sunny', 'cloudy'):
        cx, cy, r = (60, 46, 22) if kind == 'sunny' else (78, 34, 16)
        o.append(f'<circle cx="{cx}" cy="{cy}" r="{r*1.75}" fill="{rad(p["hl"][0], .35 if tone == "hero" else .5, "rg-illo-glow-"+tone)}"/>')
        for i in range(8):
            a = i * math.pi / 4
            r1, r2 = r + 7, r + (13 if kind == 'sunny' else 10)
            o.append(f'<path d="M{f(cx+r1*math.cos(a))} {f(cy+r1*math.sin(a))}L{f(cx+r2*math.cos(a))} {f(cy+r2*math.sin(a))}" {_stroke(p, "ray", 4)}/>')
        o.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" {F}/>')
        o.append(f'<path d="M{cx+r} {cy}A{r} {r} 0 0 1 {cx-r*.6} {cy+r*.8}A{r*1.1} {r*1.1} 0 0 0 {cx+r*.85} {cy-r*.45}Z" {FS}/>')
        o.append(glint(cx - r * .35, cy - r * .45, r * .32, r * .18))
        if kind == 'cloudy':
            o.append(_cloud(55, 66, 1.45, FS))
            o.append(_cloud(50, 62, 1.45, F))
            o.append(f'<path d="M30 58.5Q34 50 42 51" {_stroke(p, "hl", 2)}/>')
    elif kind == 'rain':
        o.append(_cloud(64, 38, 1.55, FS))
        o.append(_cloud(58, 34, 1.55, F))
        o.append(f'<path d="M37 30Q41 21 50 22" {_stroke(p, "hl", 2)}/>')
        for i, (dx, dy) in enumerate([(34, 60), (52, 66), (70, 60), (88, 66)]):
            o.append(f'<path d="M{dx} {dy}l-4 10" {_stroke(p, "ray", 4)}/>')
        o.append(f'<path d="M44 82l-2 4M80 82l-2 4" {_stroke(p, "soft", 3)}/>')
    elif kind == 'package':
        o.append(sh)
        o.append(f'<path d="M20 32L60 50V86L20 68Z" {F}/>')
        o.append(f'<path d="M60 50L100 32V68L60 86Z" {FS}/>')
        o.append(f'<path d="M60 14L100 32L60 50L20 32Z" {FT}/>')
        o.append(f'<path d="M38 24L46 20.4L86 38.4L78 42Z" {FSO}/><path d="M78 42L86 38.4V74.4L78 78Z" {FSO}/>')
        o.append(f'<path d="M27 50L43 57.2V66L27 58.8Z" {FH} fill-opacity=".55"/>')
        o.append(f'<path d="M30 54.5L39 58.6" {_stroke(p, "det", 1.6)}/>')
        o.append(f'<path d="M22 33.4L58 49.6" {_stroke(p, "hl", 1.6)}/>')
    elif kind == 'truck':
        o.append(sh)
        o.append(f'<path d="M0 40h8M2 52h8M4 64h6" {_stroke(p, "soft", 3)}/>')
        o.append(f'<path d="M14 24L22 16H84L76 24Z" {FT}/>')
        o.append(f'<rect x="14" y="24" width="62" height="48" rx="6" {F}/>')
        o.append(f'<path d="M76 24L84 16V64L76 72Z" {FS}/>')
        o.append(f'<path d="M76 36H98Q102 36 104.5 40L112 54V72H76Z" {F}/>')
        o.append(f'<path d="M82 41H97L103 52H82Z" {FD}/>')
        o.append(f'<rect x="76" y="64" width="36" height="8" rx="2" {FS}/><rect x="14" y="64" width="62" height="8" rx="2" {FS}/>')
        o.append(f'<rect x="24" y="36" width="42" height="5" rx="2.5" {FSO}/><rect x="24" y="46" width="28" height="5" rx="2.5" {FSO}/>')
        for wx in (34, 94):
            o.append(f'<circle cx="{wx}" cy="74" r="9" {FD} fill-opacity="{.5 if tone == "hero" else .85}"/><circle cx="{wx}" cy="74" r="4" {FT}/>')
        o.append(f'<path d="M20 26.6H70" {_stroke(p, "hl", 1.6)}/>')
    elif kind == 'hotel':
        o.append(sh)
        o.append(f'<circle cx="20" cy="76" r="10" {FSO}/><circle cx="104" cy="80" r="7" {FSO}/>')
        o.append(f'<path d="M30 18L48 8H90L72 18Z" {FT}/>')
        o.append(f'<path d="M72 18L90 8V78L72 88Z" {FS}/>')
        o.append(f'<rect x="30" y="18" width="42" height="70" rx="3" {F}/>')
        for r_ in range(5):
            for c_ in range(3):
                lit = (r_, c_) in ((1, 2), (3, 0))
                o.append(f'<rect x="{36+c_*11.5}" y="{25+r_*10.5}" width="7" height="6" rx="1.5" {FH if lit else FD}/>')
        for r_ in range(5):
            o.append(f'<path d="M77 {f(25+r_*10.5-1.5)}L85 {f(21+r_*10.5-1.5)}" {_stroke(p, "soft", 3)}/>')
        o.append(f'<rect x="44" y="76" width="14" height="12" rx="3" {FD}/>')
        o.append(f'<path d="M33 20.5V84" {_stroke(p, "hl", 1.4)}/>')
    elif kind == 'dining':
        o.append(f'<path d="M50 20q4-5 0-10M60 22q4-5 0-10M70 20q4-5 0-10" {_stroke(p, "soft", 2.5)}/>')
        o.append(f'<ellipse cx="60" cy="62" rx="36" ry="15" {FS}/>')
        o.append(f'<ellipse cx="60" cy="58" rx="36" ry="15" {F}/>')
        o.append(f'<ellipse cx="60" cy="58" rx="25" ry="9.5" {FT}/>')
        o.append(f'<path d="M44 56C44 44 76 44 76 56Z" {F}/><path d="M60 56C66 56 74 55 76 56C76 50 72 47 67 46Z" {FS}/>')
        o.append(f'<circle cx="60" cy="42" r="2.5" {F}/>' + glint(52, 49, 4, 1.6))
        o.append(f'<path d="M10 18v14q0 5 5 5t5-5V18M15 18v12" {_stroke(p, "ray", 2.6)}/><rect x="13" y="36" width="4.5" height="46" rx="2.25" {F}/>')
        o.append(f'<path d="M106 16C112 22 112 42 107 48V80A2.3 2.3 0 0 1 102.4 80V16Z" {F}/>')
    elif kind == 'calendar':
        o.append(sh)
        o.append(f'<rect x="30" y="16" width="72" height="66" rx="10" {FS}/>')
        o.append(f'<rect x="20" y="22" width="76" height="66" rx="10" {F}/>')
        o.append(f'<path d="M30 22H86A10 10 0 0 1 96 32V38H20V32A10 10 0 0 1 30 22Z" {FT}/>')
        o.append(f'<rect x="36" y="14" width="5" height="14" rx="2.5" {FD}/><rect x="75" y="14" width="5" height="14" rx="2.5" {FD}/>')
        for j in range(3):
            for i in range(4):
                cx_, cy_ = 32 + i * 15, 46 + j * 12
                if (i, j) == (2, 1):
                    o.append(f'<rect x="{cx_-2}" y="{cy_-2}" width="12" height="10" rx="4" {FD} fill-opacity="{.6 if tone=="hero" else .9}"/>')
                else:
                    o.append(f'<rect x="{cx_}" y="{cy_}" width="8" height="6" rx="2" {FD} fill-opacity=".3"/>')
        o.append(f'<path d="M26 24.6H90" {_stroke(p, "hl", 1.6)}/>')
    elif kind == 'chart':
        o.append(f'<rect x="10" y="14" width="100" height="72" rx="14" {FSO}/>')
        o.append(f'<rect x="10.75" y="14.75" width="98.5" height="70.5" rx="13.25" {_stroke(p, "top", 1.5)}/>')
        o.append(f'<path d="M20 70H100" {_stroke(p, "soft", 1.5)} stroke-dasharray="3 4"/>')
        o.append(f'<path d="M20 66L36 58L50 62L64 44L78 50L96 28V78H20Z" {FT} fill-opacity="{.32 if tone=="hero" else .7}"/>')
        o.append(f'<path d="M20 66L36 58L50 62L64 44L78 50L96 28" {_stroke(p, "hl" if tone == "hero" else "det", 3.2)}/>')
        o.append(f'<circle cx="96" cy="28" r="9" fill="{rad(p["hl"][0], .7, "rg-illo-dot-"+tone)}"/><circle cx="96" cy="28" r="4.5" {F}/>')
        o.append(f'<circle cx="96" cy="28" r="4.5" {_stroke(p, "det", 1.4)}/>')
    t = f' transform="translate({f(x)} {f(y)}) scale({f(scale)})"'
    body = re.sub(r'fill-opacity="[^"]*"(\s[^<>]*?fill-opacity=")', r'\1', "".join(o))   # later opacity wins (no duplicate attrs)
    return f'<g id="{D.gid(name or "illo-" + kind)}"{t}>' + body + '</g>'

def ai_badge(label='AI 生成 · 可编辑', on_hero=False, h=22, size=11):
    """v0.3: marks AI-generated custom cards."""
    w = round(tw(label, size, 600) + 8 + 14 + 10)
    bg = ('#FFFFFF', .22) if on_hero else (ACCENT_TINT, 1)
    fg = '#FFFFFF' if on_hero else ACCENT
    return [f'<rect width="{w}" height="{h}" rx="{h/2}" fill="{bg[0]}" fill-opacity="{bg[1]}"/>',
            icon('sparkle', 7, (h - 13) / 2, 13, fg, 2.2), T(24, h / 2 + size * .36, label, size, 600, fg)], w

def source_chip(label, active=False, ico=None, h=36, size=14):
    """v0.3: Glance source filter (全部 / X / 记忆 / 邮件 / 网页). Text only — no third-party logos."""
    iw = 22 if ico else 0
    w = round(tw(label, size, 600 if active else 500) + 28 + iw)
    out = [f'<rect width="{w}" height="{h}" rx="{h/2}" fill="{ACCENT_TINT if active else "#FFFFFF"}"/>']
    if not active:
        out.append(f'<rect x=".5" y=".5" width="{w-1}" height="{h-1}" rx="{(h-1)/2}" stroke="{HAIR}"/>')
    col = ACCENT if active else C['ink']
    if ico:
        out.append(icon(ico, 12, (h - 16) / 2, 16, ACCENT if active else C['ink2']))
    out.append(T(14 + iw, h / 2 + size * .36, label, size, 600 if active else 500, col))
    return out, w
