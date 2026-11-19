import sys, os
from hel import *
import hel

# Helium SVG v0.3 (built on v0.2) — "收敛不去色": one hero gradient per screen, white/mist cards, single accent (he-447),
# status colours only on dots/tags, glass icons, unified shadow, 8dp grid.
VERSION = 'v0.3'
OUT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
W, H = 412, 892
NAV_Y = 800          # bottom nav (tab roots: Home / 记忆与技能 / 定时与项目)
BAR_Y = 56           # top app bar buttons (40dp, centre y 76)
R_CARD, R_ROW, R_PANEL = 28, 20, 32


def round_btn(ico, name):
    """Top-bar 40dp glass button: white + hairline + unified shadow."""
    return G(name, [f'<circle cx="20" cy="20" r="20" fill="#FFFFFF" filter="{card_shadow()}"/>',
                    f'<circle cx="20" cy="20" r="19.5" stroke="{HAIR}"/>', icon(ico, 8, 8, 24, C['ink'])])


def tag(label, kind='accent', h=24, size=12):
    """Small status / meta tag. Status colours live only here and in dots."""
    fills = {'accent': (ACCENT_TINT, ACCENT, ACCENT), 'amber': (STATUS['amber_tint'], STATUS['amber_ink'], STATUS['amber']),
             'mint': (STATUS['mint_tint'], STATUS['mint_ink'], STATUS['mint']), 'neutral': (MIST2, C['ink2'], None),
             'glass': ('#FFFFFF', C['ink'], None)}
    bg, fg, dot = fills[kind]
    tx = 10 + (12 if dot else 0)
    w = round(tw(label, size, 600) + tx + 10)
    out = [f'<rect width="{w}" height="{h}" rx="{h/2}" fill="{bg}"' + (' fill-opacity=".9"' if kind == 'glass' else '') + '/>']
    if dot:
        out.append(f'<circle cx="15" cy="{h/2}" r="3.5" fill="{dot}"/>')
    out.append(T(tx, h / 2 + size * .36, label, size, 600, fg))
    return out, w


WARN = []
def fit(s_, size, maxw, weight=400, where=''):
    w_ = tw(s_, size, weight)
    if w_ > maxw:
        WARN.append(f'OVERFLOW {where}: "{s_}" {w_:.0f}>{maxw}')
    return s_


def avatar_btn(name='btn-avatar'):
    """v0.3: entry to 我的 — top-right avatar on every tab root."""
    a = lin([(0, desat(C['he492'])), (.55, ACCENT), (1, desat(C['glow']))], 0, 0, 1, 1, key='lg-orb')
    return G(name, [f'<circle cx="20" cy="20" r="20" fill="#FFFFFF" filter="{card_shadow()}"/>', f'<circle cx="20" cy="20" r="19.5" stroke="{HAIR}"/>',
                    icon('person', 8, 8, 24, C['ink2'])])


def hint_row(dot, title, meta, w=364, h=56, name='hint'):
    """v0.3 Home: one-line light hint (max 2 on Home)."""
    fit(title + '   ' + meta, 15, w - 80, 500, name)
    return [light(w, h, R_ROW, name=name),
            f'<circle cx="24" cy="{h/2}" r="4" fill="{dot}"/>',
            T(40, h / 2 + 5.4, title, 15, 600),
            T(40 + tw(title, 15, 600) + 10, h / 2 + 5, meta, 13, 400, C['ink2']),
            icon('chevron-right', w - 36, h / 2 - 9, 18, C['ink3'])]


def flight_hero(w=364, h=160, kicker=False):
    """Flight hero (Aurora) with v0.3 plane illustration on the right."""
    c, cw_ = tag('PVG → HND', 'glass', 32, 13)
    el = [mesh(w, h, R_CARD, 'aurora', 'card-flight')]
    if kicker:
        k, kw = tag('现在最要紧', 'glass', 26, 12)
        el += [G('tag-now', k, 24, 22),
               T(24, 80, '航班 · 周六', 17, 600, '#FFFFFF', name='label'),
               T(24, 102, '东航 MU523 · 已提醒值机', 13, 500, '#FFFFFF', opacity=.8, name='meta'),
               T(22, h - 26, '18:40', 64, 500, '#FFFFFF', ls=-1, num=True, name='time'),
               illo('flight', w - 172, 22, 1.3, 'hero'),
               G('route-chip', c, w - 24 - cw_, h - 56)]
    else:
        el += [T(24, 40, '航班 · 周六', 15, 600, '#FFFFFF', name='label'),
               T(24, 60, '东航 MU523 · 已提醒值机', 12, 500, '#FFFFFF', opacity=.8, name='meta'),
               T(22, 132, '18:40', 64, 500, '#FFFFFF', ls=-1, num=True, name='time'),
               illo('flight', w - 150, 6, 1.05, 'hero'),
               G('route-chip', c, w - 24 - cw_, 112)]
    return el


def illo_card(kind, label, caption, big, meta, w=174, h=168, name='card'):
    """1×1 white bento with a light-tone event illustration top-right."""
    fit(caption, 12, w - 20 - 84, 500, name)
    return [light(w, h, R_CARD, name=name),
            illo(kind, w - 84, 12, .66, 'light'),
            T(20, 36, label, 15, 600, name='label'), T(20, 56, caption, 12, 500, C['ink2'], name='caption'),
            T(18, h - 44, big, 34 if ':' in big else 40, 500, C['ink'], num=True, name='value', ls=-.5),
            T(20, h - 20, meta, 12, 500, C['ink2'], name='meta', num=True)]


# =====================================================================  01 HOME
def s01():
    """v0.3 Home — calm: greeting + ONE 'most urgent' hero + ≤2 hints + orb + composer + nav. Bento moved to 速览."""
    d = new_doc(W, H, f'01 · Home · Helium {VERSION}')
    d.add(background(3, dots=4))
    d.add(status_bar())
    d.add(G('header', [
        T(24, 92, '11月19日 · 周四', 13, 500, C['ink2'], name='date'),
        T(24, 148, '晚上好', 44, 700, name='greeting'),
        T(24, 180, '今天有 3 件事在进行', 17, 400, C['ink2'], name='subtitle'),
        G('avatar', [avatar_btn()], 348, 68)]))
    d.add(G('card-now-flight', flight_hero(364, 208, kicker=True), 24, 212))
    d.add(G('hint-approval', hint_row(STATUS['amber'], '1 件待你确认', '降噪耳机 · ¥1,299', name='hint-1'), 24, 444))
    d.add(G('hint-glance', hint_row(ACCENT, '今日速览已更新', '3 条信息差', name='hint-2'), 24, 508))
    d.add(G('agent-orb', agent_orb(96), (W - 96) / 2, 592))
    d.add(G('composer', composer(364, 56, 'idle'), 24, 720))
    nv, nw = bottom_nav('home')
    d.add(G('bottom-nav', nv, (W - nw) / 2, NAV_Y))
    d.add(gesture_bar())
    d.save(f'{OUT}/01-home.svg')


# =====================================================================  02 CHAT
def browser_preview(w=316, h=124):
    food = lin([(0, desat('#F2B66B', .75)), (1, desat('#C86B45', .75))], 0, 0, 1, 1, key='lg-food')
    out = [f'<rect width="{w}" height="{h}" rx="16" fill="{MIST2}"/>',
           f'<rect x=".5" y=".5" width="{w-1}" height="{h-1}" rx="15.5" stroke="{HAIR}"/>']
    pw = 176; px = (w - pw) / 2
    page = [f'<rect width="{pw}" height="{h-8}" rx="6" fill="#FFFFFF"/>',
            f'<rect x="8" y="8" width="24" height="5" rx="2.5" fill="#D9DCE6"/>',
            f'<rect x="62" y="8" width="56" height="5" rx="2.5" fill="#E8EAF1"/>',
            f'<rect x="146" y="6" width="22" height="9" rx="4.5" fill="{ACCENT}"/>',
            f'<rect x="8" y="22" width="72" height="50" rx="6" fill="{MIST2}"/>',
            f'<rect x="14" y="30" width="54" height="7" rx="3.5" fill="#D9DCE6"/>',
            f'<rect x="14" y="42" width="46" height="4" rx="2" fill="#E3E5ED"/>',
            f'<rect x="14" y="50" width="38" height="4" rx="2" fill="#E3E5ED"/>',
            f'<rect x="14" y="59" width="30" height="8" rx="4" fill="{ACCENT}"/>',
            f'<rect x="8" y="78" width="34" height="34" rx="5" fill="#EEF0F6"/>',
            f'<rect x="46" y="78" width="34" height="34" rx="5" fill="#EEF0F6"/>',
            G('food-photo', [f'<rect width="80" height="50" rx="4" fill="{food}"/>',
                             f'<circle cx="40" cy="27" r="19" fill="#FFFFFF"/>', f'<circle cx="40" cy="27" r="15" fill="#F4ECE1"/>',
                             f'<ellipse cx="36" cy="25" rx="8" ry="5" fill="{desat("#E07A4A", .75)}"/>', f'<circle cx="46" cy="30" r="4" fill="{desat("#7FB069", .75)}"/>',
                             f'<circle cx="34" cy="32" r="3" fill="{desat("#E9C46A", .75)}"/>'], 88, 22),
            f'<rect x="88" y="80" width="62" height="5" rx="2.5" fill="#C9CCD8"/>',
            f'<rect x="88" y="90" width="44" height="4" rx="2" fill="#E3E5ED"/>',
            f'<rect x="88" y="102" width="64" height="4" rx="2" fill="#E8EAF1"/>']
    out.append(G('mock-page-clip', [G('mock-page', page, px, 8)], extra=f' clip-path="{clip_rr(w, h, 16)}"'))
    out.append(G('btn-fullscreen', [f'<circle cx="16" cy="16" r="16" fill="#FFFFFF" filter="{card_shadow()}"/>', icon('expand', 8, 8, 16, C['ink2'])], w - 40, h - 40))
    return out


def wave(x0, x1, y, amp=6, period=28):
    pts = []
    n = 80
    for i in range(n + 1):
        x = x0 + (x1 - x0) * i / n
        pts.append(f"{x:.1f} {y + amp * math.sin((x - x0) / period * 2 * math.pi):.1f}")
    return "M" + " L".join(pts)


def step_row(label, stt, wave_to=316):
    el = []
    if stt == 'done':
        el.append(f'<circle cx="12" cy="12" r="11" fill="{STATUS["mint_tint"]}"/>')
        el.append(icon('check', 5, 5, 14, STATUS['mint_ink'], 2.4))
        el.append(T(40, 17.5, label, 15, 400, C['ink']))
    elif stt == 'run':
        el.append(f'<circle cx="12" cy="12" r="10" stroke="{MIST2}" stroke-width="3"/>')
        el.append(f'<circle cx="12" cy="12" r="10" stroke="{ACCENT}" stroke-width="3" stroke-linecap="round" stroke-dasharray="40 63" transform="rotate(-90 12 12)"/>')
        el.append(T(40, 17.5, label, 15, 600, C['ink']))
        wg = lin_user([(0, ACCENT, .15), (1, ACCENT, 1)], 128, 0, wave_to, 0, key=f'lg-wave-acc-{wave_to}')
        el.append(f'<path id="{hel.D.gid("wave-progress")}" d="{wave(128, wave_to, 12, 4, 26)}" stroke="{wg}" stroke-width="2.5" stroke-linecap="round"/>')
    else:
        el.append(f'<circle cx="12" cy="12" r="10" stroke="{C["ink3"]}" stroke-width="1.5" stroke-dasharray="3 3"/>')
        el.append(T(40, 17.5, label, 15, 400, C['ink3']))
    return el


def s02():
    d = new_doc(W, H, f'02 · Chat · Live agent · Helium {VERSION}')
    d.add(background(5, dots=3))
    # thinking glow: one soft accent-family haze (no rainbow)
    tg = [f'<ellipse cx="120" cy="0" rx="260" ry="150" fill="{rad(desat(C["he447"]), .26, "rg-think-a")}" filter="{blur(36)}"/>',
          f'<ellipse cx="330" cy="40" rx="200" ry="130" fill="{rad(desat(C["glow"]), .28, "rg-think-b")}" filter="{blur(36)}"/>']
    d.add(G('thinking-glow', tg))
    d.add(status_bar())
    d.add(G('top-app-bar', [G('btn-back', [round_btn('arrow-left', 'btn')], 16, BAR_Y),
                             T(206, 74, 'Helium', 17, 600, C['ink'], 'middle', name='title'),
                             G('status', [f'<circle cx="0" cy="-4" r="3.5" fill="{ACCENT}"/>', T(8, 0, '正在工作', 12, 500, C['ink2'])], 206 - (8 + tw('正在工作', 12, 500)) / 2, 94),
                             G('btn-more', [round_btn('more', 'btn')], 356, BAR_Y)]))

    d.add(T(206, 208, '今天 21:40', 12, 500, C['ink3'], 'middle', name='timestamp', num=True))
    msg = '帮我订周六晚上两人的餐厅'
    bw = round(tw(msg, 16) + 40)
    d.add(G('bubble-user', [f'<rect width="{bw}" height="48" rx="24" fill="{C["ink"]}"/>', T(20, 29.8, msg, 16, 400, '#FFFFFF')], 388 - bw, 232))
    d.add(G('message-agent', [G('agent-avatar', flower_avatar(24), 0, 0), T(36, 18, '好的，我来查附近可订的餐厅。', 16, 400, C['ink'])], 24, 304))

    cw, ch = 364, 432
    g = aurora_sunrise()
    card = [f'<rect id="run-glow" x="16" y="16" width="{cw-32}" height="{ch-16}" rx="32" fill="{g}" opacity=".22" filter="{blur(16)}"/>',
            f'<rect id="run-card-bg" width="{cw}" height="{ch}" rx="{R_PANEL}" fill="#FFFFFF"/>',
            f'<rect id="run-card-stroke" x=".75" y=".75" width="{cw-1.5}" height="{ch-1.5}" rx="{R_PANEL-.75}" stroke="{g}" stroke-width="1.5"/>',
            G('run-header', [f'<circle cx="8" cy="8" r="8" fill="{rad(ACCENT, .5, "rg-pulse")}"/>',
                             f'<circle cx="8" cy="8" r="4.5" fill="{ACCENT}"/>',
                             T(28, 13.5, '正在操作浏览器', 16, 600),
                             T(316, 13.5, '0:42', 13, 500, C['ink2'], 'end', num=True)], 24, 26),
            G('browser-preview', browser_preview(), 24, 64)]
    for i, (s_, stt) in enumerate([('打开预订页面', 'done'), ('选择 19:00 · 2 位', 'done'), ('确认空位', 'run')]):
        card.append(G(f'step-{i+1}-{stt}', step_row(s_, stt), 24, 212 + i * 44))
    card.append(G('btn-takeover', btn(150, 48, '接管', 'tonal', 'hand'), 24, 360))
    card.append(G('btn-pause', btn(150, 48, '暂停', 'outline', 'pause'), 190, 360))
    d.add(G('card-live-run', card, 24, 352))
    d.add(G('composer', composer(364, 56, 'running'), 24, 808))
    d.add(gesture_bar())
    d.save(f'{OUT}/02-chat.svg')


# =====================================================================  03 TIMELINE
def thumb(kind):
    ds = lambda c: desat(c, .7, .02)
    if kind == 'pool':
        return [f'<rect width="48" height="48" rx="12" fill="{lin([(0, ds("#9BC7F2")), (1, ds("#5F86C9"))], 0, 0, 0, 1, key="lg-th-sky")}"/>',
                f'<rect x="6" y="10" width="14" height="24" rx="2" fill="#E9DFD1"/>', f'<rect x="28" y="6" width="14" height="28" rx="2" fill="#DCCFBD"/>',
                f'<rect x="0" y="32" width="48" height="16" fill="{ds("#2EC4D6")}"/>', f'<path d="M0 48h48V36c-8 3-16-3-24 0S8 39 0 36z" fill="{ds("#19A6C2")}"/>']
    if kind == 'room':
        return [f'<rect width="48" height="48" rx="12" fill="{lin([(0, "#F0E9E0"), (1, "#DCD0C1")], 0, 0, 0, 1, key="lg-th-room")}"/>',
                f'<rect x="30" y="8" width="12" height="14" rx="2" fill="#FFFFFF" fill-opacity=".8"/>',
                f'<rect x="6" y="26" width="36" height="14" rx="3" fill="#FFFFFF"/>', f'<rect x="6" y="22" width="12" height="6" rx="3" fill="#F2EEE8"/>',
                f'<rect x="6" y="36" width="36" height="6" rx="2" fill="#BCA58A"/>']
    return [f'<rect width="48" height="48" rx="12" fill="{lin([(0, "#E6D9C8"), (1, "#C2A78E")], 0, 0, 0, 1, key="lg-th-suite")}"/>',
            f'<rect x="4" y="6" width="16" height="18" rx="2" fill="#F6EFE6" fill-opacity=".8"/>',
            f'<rect x="10" y="28" width="34" height="12" rx="3" fill="#FFFFFF"/>', f'<rect x="10" y="36" width="34" height="6" rx="2" fill="#83654F"/>']


def s03():
    d = new_doc(W, H, f'03 · Run timeline · Helium {VERSION}')
    d.add(background(17, dots=4))
    d.add(status_bar())
    d.add(G('top-app-bar', [G('btn-back', [round_btn('arrow-left', 'btn')], 16, BAR_Y), G('btn-more', [round_btn('more', 'btn')], 356, BAR_Y)]))
    d.add(G('header', [T(24, 140, '东京行程规划', 32, 700, name='title'),
                       T(24, 170, '代理执行中 · 已完成 3/6', 15, 400, C['ink2'], name='subtitle', num=True)]))
    # progress — the ONE hero gradient: matte Aurora→Lagoon band with gloss
    pg = lin([(0, desat(C['he447'])), (.6, desat(C['he492'])), (1, desat(C['he501']))], 0, 0, 1, 0, key='lg-progress')
    gl = lin_user([(0, '#FFFFFF', .55), (1, '#FFFFFF', 0)], 0, 0, 0, 7, key='lg-progress-gloss')
    d.add(G('progress', [f'<rect width="364" height="12" rx="6" fill="{MIST2}"/>',
                         f'<rect x=".5" y=".5" width="363" height="11" rx="5.5" stroke="{HAIR}"/>',
                         f'<rect width="182" height="12" rx="6" fill="{pg}" filter="{shadow(desat(C["he447"]), .25, 3, 4)}"/>',
                         f'<rect x="4" y="2" width="174" height="4" rx="2" fill="{gl}"/>'], 24, 192))

    cards = [(232, 88, '搜索航班', '21:02', 'done'),
             (336, 112, '比较酒店价格', '21:10', 'done'),
             (464, 64, '读取日历空档', '21:18', 'done'),
             (544, 64, '生成每日行程', '21:30', 'run'),
             (624, 64, '等待你审批', '', 'wait'),
             (704, 64, '预订机票与酒店', '', 'future')]
    nx = 52
    ys = [y + 32 for (y, *_r) in cards]
    run_y = ys[3]
    d.add(G('rail', [f'<rect x="{nx-1}" y="{ys[0]}" width="2" height="{ys[-1]-ys[0]}" rx="1" fill="#DDE0EA"/>',
                     f'<rect x="{nx-1}" y="{ys[0]}" width="2" height="{run_y-ys[0]}" rx="1" fill="{ACCENT}"/>']))
    nodes = []
    for i, (y, h, title, tm, stt) in enumerate(cards):
        cy = ys[i]
        if stt == 'done':
            nodes.append(G(f'node-{i+1}-done', [f'<circle cx="{nx}" cy="{cy}" r="13" fill="{ACCENT}" filter="{shadow(ACCENT, .22, 2, 3)}"/>',
                                                icon('check', nx - 8, cy - 8, 16, '#FFFFFF', 2.6)]))
        elif stt == 'run':
            nodes.append(G(f'node-{i+1}-running', [f'<rect x="{nx-15}" y="{cy-15}" width="30" height="30" rx="10" fill="#FFFFFF" filter="{shadow(ACCENT, .25, 0, 6)}"/>',
                                                   f'<rect x="{nx-14.25}" y="{cy-14.25}" width="28.5" height="28.5" rx="9.25" stroke="{ACCENT}" stroke-width="1.5"/>',
                                                   f'<rect x="{nx-6}" y="{cy-6}" width="12" height="12" rx="4" fill="{ACCENT}"/>']))
        elif stt == 'wait':
            nodes.append(G(f'node-{i+1}-waiting', [f'<circle cx="{nx}" cy="{cy}" r="15" fill="{C["canvas"]}"/>',
                                                   f'<circle cx="{nx}" cy="{cy}" r="12" fill="#FFFFFF" stroke="{STATUS["amber"]}" stroke-width="2.5"/>']))
        else:
            nodes.append(G(f'node-{i+1}-future', [f'<rect x="{nx-10}" y="{cy-10}" width="20" height="20" rx="6" fill="#D5D9E4"/>']))
    d.add(G('nodes', nodes))

    cx, cw = 96, 292
    for i, (y, h, title, tm, stt) in enumerate(cards):
        el = []
        ty = 38 if h == 64 else 34
        if stt == 'run':
            el.append(light(cw, h, R_ROW, name='step-bg', stroke=(ACCENT, 1.5)))
            el.append(T(20, ty, title, 16, 600, C['ink']))
            c, w_ = tag('进行中', 'accent')
            el.append(G('tag-running', c, cw - 20 - w_, 20))
        elif stt == 'wait':
            el.append(light(cw, h, R_ROW, name='step-bg'))
            el.append(f'<circle cx="24" cy="32" r="4" fill="{STATUS["amber"]}"/>')
            el.append(T(38, ty, title, 16, 600))
            el.append(G('btn-review', btn(80, 36, '去审批', 'primary', size=13), cw - 100, 14))
        elif stt == 'future':
            el.append(f'<rect x=".75" y=".75" width="{cw-1.5}" height="{h-1.5}" rx="{R_ROW-.75}" fill="#FFFFFF" fill-opacity=".45" stroke="#D3D7E3" stroke-width="1.5" stroke-dasharray="5 5"/>')
            el.append(T(20, ty, title, 16, 600, C['ink3']))
            el.append(T(cw - 20, ty - 1, '未开始', 13, 500, C['ink3'], 'end'))
        else:
            el.append(light(cw, h, R_ROW, name='step-bg'))
            el.append(T(20, ty, title, 16, 600))
            el.append(T(cw - 20, ty - 1, tm, 13, 500, C['ink2'], 'end', num=True))
            if i == 0:
                c, w_ = chip('3 个结果', MIST2, 28, 12, 500, C['ink2'], pad=12)
                el.append(G('chip-results', c, 20, 46))
                c2, w2 = chip('最低 ¥2,180', MIST2, 28, 12, 600, C['ink'], pad=12)
                el.append(G('chip-price', c2, 28 + w_, 46))
            if i == 1:
                for k, kind in enumerate(['pool', 'room', 'suite']):
                    el.append(G(f'thumb-hotel-{k+1}', thumb(kind), 20 + k * 56, 48))
                el.append(T(cw - 20, 76, '已比 12 家', 13, 500, C['ink2'], 'end', num=True))
                el.append(T(cw - 20, 94, '均价 ¥1,050/晚', 12, 400, C['ink3'], 'end', num=True))
        d.add(G(f'step-{i+1}-{stt}', el, cx, y))

    ab = [f'<rect width="240" height="64" rx="32" fill="#FFFFFF" filter="{card_shadow()}"/>', f'<rect x=".5" y=".5" width="239" height="63" rx="31.5" stroke="{HAIR}"/>',
          G('btn-pause', btn(104, 48, '暂停', 'tonal', 'pause', 15), 8, 8), G('btn-details', btn(112, 48, '查看详情', 'outline', None, 15), 120, 8)]
    d.add(G('action-bar', ab, 86, 792))
    d.add(gesture_bar())
    d.save(f'{OUT}/03-timeline.svg')


# =====================================================================  04 APPROVAL
def clip_top(w, h, r):
    key = f'clip-top-{w}x{h}r{r}'
    defn(key, f'<clipPath id="{key}"><path d="M0 {r}A{r} {r} 0 0 1 {r} 0H{w-r}A{r} {r} 0 0 1 {w} {r}V{h}H0Z"/></clipPath>')
    return f'url(#{key})'


def approval_card(cw):
    card = [f'<rect width="{cw}" height="440" rx="{R_PANEL}" fill="#FFFFFF" filter="{shadow("#1B2150", .08, 12, 20)}"/>',
            G('approval-hero', [mesh(cw, 216, R_PANEL, 'sunrise', 'approval-hero', shadow_op=0)], extra=f' clip-path="{clip_top(cw, 176, R_PANEL)}"'),
            f'<rect x=".5" y=".5" width="{cw-1}" height="439" rx="{R_PANEL-.5}" stroke="{HAIR}" stroke-opacity=".7"/>']
    w_ = round(25 + tw('待审批', 13, 700) + 11)
    # DESIGN §4 张力: the only sharp-ish corner is the pending tag (8dp)
    card.append(G('tag-pending', [f'<rect width="{w_}" height="28" rx="8" fill="#FFFFFF" fill-opacity=".9"/>',
                                  f'<circle cx="15" cy="14" r="3.5" fill="{STATUS["amber"]}"/>',
                                  T(25, 18.7, '待审批', 13, 700, STATUS['amber_ink'])], 24, 24))
    card += [T(24, 96, '需要你确认', 15, 500, C['ink'], opacity=.75, name='kicker'),
             T(24, 132, '降噪耳机 · 午夜蓝', 26, 700, C['ink'], name='title'),
             T(24, 156, '来自任务：比价购物 · 已比 6 家', 12, 500, C['ink'], opacity=.65, name='source')]

    def row(y, l, r, big=False):
        return [T(24, y, l, 15, 600 if big else 400, C['ink'] if big else C['ink2']),
                T(cw - 24, y + (2 if big else 0), r, 30 if big else 15, 600 if big else 500, C['ink'], 'end', num=True)]
    card += [G('row-item', row(214, '商品', '¥1,299')), G('row-shipping', row(246, '运费', '¥0')),
             f'<rect x="24" y="264" width="{cw-48}" height="1" fill="{HAIR}"/>',
             G('row-total', row(306, '合计', '¥1,299', True)),
             f'<rect x="24" y="328" width="{cw-48}" height="1" fill="{HAIR}"/>',
             G('row-card', [T(24, 362, '付款卡', 15, 400, C['ink2']), icon('card', cw - 24 - tw('•••• 4821', 15, 500) - 28, 346, 20, C['ink2']),
                            T(cw - 24, 362, '•••• 4821', 15, 500, C['ink'], 'end', num=True)]),
             G('row-address', [T(24, 394, '送至', 15, 400, C['ink2']), T(cw - 24, 394, '家 · 上海市浦东新区', 15, 500, C['ink'], 'end')]),
             G('row-eta', [T(24, 420, '预计 11月21日 送达', 12, 400, C['ink3'], num=True)])]
    return card


def s04():
    d = new_doc(W, H, f'04 · Approval · Helium {VERSION}')
    d.add(background(9, dots=3))
    d.add(status_bar())
    pc, pw = tag('1 / 3', 'neutral', 28, 12)
    d.add(G('top-app-bar', [G('btn-close', [round_btn('close', 'btn')], 16, BAR_Y),
                             T(206, 82, '待你确认', 17, 600, C['ink'], 'middle', name='title'),
                             G('pager', pc, 388 - pw, 62)]))
    d.add(G('stack-back-2', [light(268, 72, R_PANEL, name='appr-back-2', fill='#EEF0F6', sh=False)], 72, 116))
    d.add(G('stack-back-1', [light(300, 72, R_PANEL, name='appr-back-1', fill='#F8F9FC'),
                             T(24, 32, '机票改签 · ¥260', 13, 500, C['ink2'], num=True)], 56, 140))
    d.add(G('card-approval', approval_card(332), 40, 196))
    d.add(G('btn-approve', btn(332, 56, '批准并支付 ¥1,299', 'primary', 'lock', 17), 40, 672))
    d.add(G('btn-reject', btn(332, 56, '拒绝', 'outline', None, 17), 40, 744))
    sl = '未经你同意，Helium 不会付款'
    sw = tw(sl, 13, 500)
    d.add(G('safety-note', [icon('shield', (W - sw - 24) / 2, 824, 18, C['ink2']), T((W - sw - 24) / 2 + 24, 837.5, sl, 13, 500, C['ink2'])]))
    d.add(gesture_bar())
    d.save(f'{OUT}/04-approval.svg')


# =====================================================================  05 MEMORY & SKILLS
def segmented(active=0, labels=('全部', '记忆', '技能')):
    out = [f'<rect width="236" height="48" rx="24" fill="#EAECF3"/>',
           f'<rect x="{4 + active*76}" y="4" width="76" height="40" rx="20" fill="#FFFFFF" filter="{card_shadow()}"/>']
    for i, l in enumerate(labels):
        out.append(T(42 + i * 76, 30, l, 15, 600 if i == active else 500, C['ink'] if i == active else C['ink2'], 'middle'))
    return out


def skill_card(nm, ic, shp, on, meta, running=False, h=160):
    if running:   # the one hero on this screen: only the skill that is working glows (DESIGN §1 惰性)
        el = [mesh(174, h, R_CARD, 'aurora', 'skill-bg'),
              G(f'shape-{shp}', [glass_icon(ic, 48, shp, on_hero=True, isize=24)], 20, 20)]
        c, w_ = tag('进行中', 'glass')
        el.append(G('tag-running', c, 154 - w_, 32))
        fg = '#FFFFFF'
    else:
        el = [light(174, h, R_CARD, name='skill-bg', tint=ACCENT_TINT, blob=(1, 0), op=.9),
              G(f'shape-{shp}', [glass_icon(ic, 48, shp, isize=24)], 20, 20)]
        fg = C['ink']
    el.append(T(20, h - 40, nm, 17, 700, fg, name='name'))
    el.append(T(20, h - 20, meta, 12, 500, fg if running else C['ink2'], name='meta', num=True, opacity=.85 if running else None))
    el.append(G('switch-' + ('on' if on else 'off'), toggle(on, on_sat=running), 102, h - 52))
    return el


def s05():
    d = new_doc(W, H, f'05 · Memory & Skills · Helium {VERSION}')
    d.add(background(11, dots=4))
    d.add(status_bar())
    d.add(G('btn-back', [round_btn('arrow-left', 'btn')], 16, BAR_Y))
    d.add(G('segmented', segmented(0), 88, BAR_Y))
    d.add(G('header', [T(24, 152, 'Helium', 32, 700, name='title-1'), T(24, 192, '记得的事', 32, 700, name='title-2'),
                       G('btn-manage', btn(72, 36, '管理', 'tonal', None, 13), 316, 166)]))
    mems = ['喜欢靠窗座位', '不吃香菜', '周五健身', '妈妈生日 3 月']
    x, y = 24, 216
    for i, m in enumerate(mems):
        c, w_ = chip(m, '#FFFFFF', 40, 15, 500, pad=18, stroke=HAIR)
        c[0] = c[0].replace('stroke-width="1.5"', f'stroke-width="1" filter="{card_shadow()}"')
        if x + w_ > 388:
            x, y = 24, y + 48
        d.add(G(f'memory-chip-{i+1}', c, x, y))
        x += w_ + 8
    c, w_ = chip('+ 添加', 'none', 40, 15, 500, C['ink2'], pad=18, stroke='#C9CDDA')
    c[0] = c[0].replace('stroke-width="1.5"', 'stroke-width="1" stroke-dasharray="4 3"')
    if x + w_ > 388:
        x, y = 24, y + 48
    d.add(G('memory-add', c, x, y))

    d.add(G('section-skills', [T(24, 360, '技能', 22, 700), T(388, 359, '全部 8 个', 13, 500, C['ink2'], 'end', num=True)]))
    sk = [('订餐厅', 'utensils', 'scallop', True, '本月用了 3 次', False), ('订机票', 'plane', 'flower', True, '东京行程 · 3/6', True),
          ('比价购物', 'bag', 'pill', True, '已比 6 家', False), ('整理邮件', 'mail', 'square', False, '已关闭', False)]
    for i, (nm, ic, shp, on, meta, run) in enumerate(sk):
        cx_, cy_ = 24 + (i % 2) * 190, 384 + (i // 2) * 176
        d.add(G(f'skill-card-{["restaurant","flight","shopping","mail"][i]}', skill_card(nm, ic, shp, on, meta, run), cx_, cy_))
    nv, nw = bottom_nav(None)   # v0.3: 我的 › 记忆与技能 — secondary page, no tab selected
    d.add(G('bottom-nav', nv, (W - nw) / 2, NAV_Y))
    d.add(gesture_bar())
    d.save(f'{OUT}/05-memory-skills.svg')


# =====================================================================  06 SCHEDULE & PROJECTS
def task_row(t1, t2, ic, on=True):
    return [light(364, 64, R_ROW, name='row-bg'),
            G('task-icon', [glass_icon(ic, 40, 'circle', isize=22)], 12, 12),
            T(68, 28, t1, 16, 600, name='title'),
            T(68, 48, t2, 13, 400, C['ink2'], name='when', num=True),
            G('switch-' + ('on' if on else 'off'), toggle(on), 300, 16)]


def s06():
    d = new_doc(W, H, f'06 · Schedule & Projects · Helium {VERSION}')
    d.add(background(13, dots=4))
    d.add(status_bar())
    d.add(G('header', [T(24, 112, '任务', 32, 700, name='title'), T(24 + tw('任务', 32, 700) + 10, 111, '定时与项目', 15, 500, C['ink2'], name='kicker'),
                       G('btn-new-schedule', [round_btn('plus', 'btn')], 300, 68), G('avatar', [avatar_btn()], 348, 68)]))
    d.add(G('section-schedule', [T(24, 164, '定时任务', 17, 700), T(388, 164, '11月16日 – 22日', 13, 500, C['ink2'], 'end', num=True)]))
    days = '一二三四五六日'
    wk = [f'<rect width="364" height="56" rx="28" fill="#FFFFFF" filter="{card_shadow()}"/>', f'<rect x=".5" y=".5" width="363" height="55" rx="27.5" stroke="{HAIR}"/>']
    for i, ch_ in enumerate(days):
        cx_ = 6 + 22 + i * (364 - 12 - 44) / 6
        if ch_ == '四':
            wk.append(f'<circle cx="{cx_:.1f}" cy="28" r="22" fill="{ACCENT}" filter="{shadow(ACCENT, .25, 3, 4)}"/>')
            wk.append(T(cx_, 33.5, ch_, 15, 600, '#FFFFFF', 'middle'))
        else:
            wk.append(T(cx_, 33.5, ch_, 15, 500, C['ink'], 'middle'))
        if ch_ in '一六':
            wk.append(f'<circle cx="{cx_:.1f}" cy="45" r="2" fill="{ACCENT}" fill-opacity=".55"/>')
    d.add(G('week-strip', wk, 24, 180))
    tasks = [('每日早报', '每天 08:00', 'sun'), ('每周账单检查', '每周一 09:30', 'receipt'), ('航班值机提醒', '周六 06:00 · 仅一次', 'plane')]
    for i, (t1, t2, ic) in enumerate(tasks):
        d.add(G(f'task-row-{i+1}', task_row(t1, t2, ic), 24, 252 + i * 72))

    d.add(G('section-projects', [T(24, 496, '项目', 17, 700), G('btn-new-project', [icon('plus', 0, -14, 18, ACCENT), T(22, 0, '新建', 13, 600, ACCENT)], 388 - 22 - tw('新建', 13, 600), 496)]))
    c, w_ = tag('进行中', 'mint')
    back2 = [light(364, 96, R_CARD, name='proj-bg', fill='#F7F8FB'),
             G('icon', [glass_icon('briefcase', 32, 'circle', isize=18)], 20, 14),
             T(64, 36, '求职', 17, 700), G('status-tag', c, 344 - w_, 18)]
    d.add(G('project-job', back2, 24, 512))
    c, w_ = tag('周六出发', 'neutral')
    back1 = [light(364, 96, R_CARD, name='proj-bg'),
             G('icon', [glass_icon('plane', 32, 'circle', isize=18)], 20, 14),
             T(64, 36, '东京旅行', 17, 700), G('status-tag', c, 344 - w_, 18)]
    d.add(G('project-tokyo', back1, 24, 568))
    r_, circ = 28, 2 * math.pi * 28
    ring = [f'<circle cx="36" cy="36" r="{r_}" stroke="#FFFFFF" stroke-opacity=".28" stroke-width="6"/>',
            f'<circle cx="36" cy="36" r="{r_}" stroke="#FFFFFF" stroke-width="6" stroke-linecap="round" stroke-dasharray="{circ*.65:.1f} {circ:.1f}" transform="rotate(-90 36 36)"/>',
            T(36, 41.5, '65%', 16, 600, '#FFFFFF', 'middle', num=True)]
    front = [mesh(364, 160, R_CARD, 'aurora', 'proj-bg'),
             G('icon', [glass_icon('home', 32, 'circle', on_hero=True, isize=18)], 20, 20),
             T(64, 42, '搬家计划', 20, 700, '#FFFFFF', name='title'),
             T(20, 116, '12 项待办', 15, 600, '#FFFFFF', name='todo', num=True),
             T(20, 138, '下一步：预约搬家公司 · 周日', 12, 500, '#FFFFFF', opacity=.85, name='next'),
             G('progress-ring-65', ring, 272, 60)]
    d.add(G('project-move', front, 24, 624))
    nv, nw = bottom_nav('tasks')
    d.add(G('bottom-nav', nv, (W - nw) / 2, NAV_Y))
    d.add(gesture_bar())
    d.save(f'{OUT}/06-schedule-projects.svg')


# =====================================================================  00 COMPONENTS & TOKENS
def s00():
    CW, CH = 1680, 2640
    d = new_doc(CW, CH, f'00 · Components & Tokens · Helium {VERSION}')
    d.add(G('background', [f'<rect id="canvas" width="{CW}" height="{CH}" fill="{C["canvas"]}"/>']))
    d.add(G('sheet-header', [T(48, 88, 'Helium · 组件与 Token', 44, 700),
                             T(48, 124, f'{VERSION} · 画板 412×892 dp · 8dp 网格 · Google Sans Flex + Noto Sans SC · 收敛配色：一屏一个英雄渐变', 16, 400, C['ink2'])]))
    COL = [48, 456, 864, 1272]

    def sec(x, y, title, sub=None):
        out = [T(x, y, title, 22, 700)]
        if sub:
            out.append(T(x, y + 22, sub, 13, 400, C['ink2']))
        return G('section-title', out)

    # ---- column 1: colour + accent rules, mesh, radius
    x, y = COL[0], 184
    d.add(sec(x, y, '色板 · 强调色使用规则', '中性为底 · 单一强调色 · 状态色只做点和标签'))
    # accent hero swatch
    d.add(G('swatch-accent', [f'<rect width="176" height="88" rx="20" fill="{ACCENT}"/>',
                              T(16, 30, 'Accent · he-447', 15, 700, '#FFFFFF'), T(16, 50, ACCENT, 12, 500, '#FFFFFF', opacity=.85),
                              f'<rect x="184" width="176" height="88" rx="20" fill="{ACCENT_TINT}"/>',
                              T(200, 30, 'Accent tint', 15, 700, ACCENT), T(200, 50, f'{ACCENT_TINT} · 选中底', 12, 500, ACCENT),
                              T(0, 112, '唯一强调色：选中 / 进度 / 开关 / 链接 / 图标描边', 13, 500, C['ink']),
                              T(0, 132, '主按钮用 ink 深色，不用强调色大面积铺底', 12, 400, C['ink2'])], x, y + 44))
    y2 = y + 200
    nt = [('ink', C['ink']), ('ink-2', C['ink2']), ('ink-3', C['ink3']), ('canvas', C['canvas']), ('surface', '#FFFFFF'), ('mist-2', MIST2), ('hairline', HAIR)]
    d.add(T(x, y2, '中性 · 大面积底色与文字', 13, 600, C['ink2']))
    for i, (n, col) in enumerate(nt):
        cx_ = x + i * 52
        d.add(G(f'swatch-{n}', [f'<rect x=".5" y=".5" width="43" height="43" rx="12" fill="{col}" stroke="#DDE1EE"/>', T(0, 60, n, 10, 600), T(0, 74, col, 9, 400, C['ink2'])], cx_, y2 + 12))
    y3 = y2 + 112
    d.add(T(x, y3, '状态色 · 仅小圆点 / 标签', 13, 600, C['ink2']))
    for i, (k, lab) in enumerate([('amber', '待审批'), ('mint', '完成'), ('accent', '进行中'), ('neutral', '未开始')]):
        c, w_ = tag(lab, k)
        d.add(G(f'status-{k}', c + [T(0, 44, {'amber': STATUS['amber'], 'mint': STATUS['mint'], 'accent': ACCENT, 'neutral': C['ink3']}[k], 10, 500, C['ink2'])], x + i * 92, y3 + 12))
    y4 = y3 + 80
    d.add(T(x, y4, '谱线色 · 只活在英雄渐变内部（饱和度 −20%）', 13, 600, C['ink2']))
    for i, (n, col) in enumerate([('he-447', C['he447']), ('he-492', C['he492']), ('he-501', C['he501']), ('he-587', C['he587']), ('he-668', C['he668']), ('glow', C['glow']), ('blush', C['blush'])]):
        dc = desat(col, .8, 0)
        d.add(G(f'spectral-{n}', [f'<rect width="44" height="28" rx="8" fill="{col}"/>', f'<rect y="28" width="44" height="20" rx="0" fill="{dc}"/>',
                                  f'<rect x=".5" y=".5" width="43" height="47" rx="8" stroke="#FFFFFF" stroke-opacity=".6"/>',
                                  T(0, 64, n, 10, 600), T(0, 77, dc, 9, 400, C['ink2'])], x + i * 52, y4 + 12))
    d.add(G('spectral-legend', [T(0, 0, '上：v0.1 原色　下：v0.2 雾化色', 11, 500, C['ink3'])], x, y4 + 108))
    y5 = y4 + 156
    d.add(sec(x, y5, 'Mesh 英雄渐变 · 雾面 + 光泽', '每屏最多 1 个 · 大而散的色块 · 光带 + 顶边高光 + 6% 颗粒'))
    for i, (n, label) in enumerate([('aurora', 'Aurora · 航班 / 当前项目'), ('sunrise', 'Sunrise · 审批卡头部'), ('lagoon', 'Lagoon · 完成态（备用）'), ('lilac', 'Lilac · 记忆（备用）')]):
        cx_, cy_ = x + (i % 2) * 184, y5 + 48 + (i // 2) * 128
        d.add(G(f'gradient-{n}', [mesh(172, 92, 20, n, f'mesh-{n}', shadow_op=.14), T(0, 112, label, 12, 500, C['ink2'])], cx_, cy_))
    y6 = y5 + 336
    d.add(sec(x, y6, '圆角 Token'))
    for i, (n, r, use) in enumerate([('sm', 12, '缩略图'), ('lg', 20, '行 / 步骤卡'), ('card', 28, 'bento 卡'), ('panel', 32, '审批 / Live'), ('xxl', 48, 'sheet'), ('full', 999, '按钮 / chip')]):
        cx_, cy_ = x + (i % 3) * 124, y6 + 24 + (i // 3) * 132
        rr = min(r, 48)
        d.add(G(f'radius-{n}', [f'<rect x=".5" y=".5" width="111" height="95" rx="{rr}" fill="#FFFFFF" stroke="{HAIR}" filter="{card_shadow()}"/>',
                                 T(56, 46, f'{n} · {"胶囊" if r == 999 else r}', 14, 600, C['ink'], 'middle'),
                                 T(56, 66, use, 11, 500, C['ink2'], 'middle')], cx_, cy_))

    # ---- column 2: type, buttons, chips, switches, nodes, icons
    x, y = COL[1], 184
    d.add(sec(x, y, '字体层级 (sp)', '标题 ink 700 · 正文 ink 400 · 辅文 ink-2 · 占位 ink-3'))
    ty = [('Display 44 · Bold', 44, 700, '晚上好', C['ink']), ('Headline 32 · Bold', 32, 700, '东京行程规划', C['ink']), ('Title 17–22 · Bold', 22, 700, '技能', C['ink']),
          ('Body 15–16 · Regular', 16, 400, '好的，我来查附近可订的餐厅。', C['ink']), ('Label 13 · Medium · ink-2', 13, 500, '已完成 3/6 · 21:02', C['ink2']),
          ('Caption 12 · ink-3', 12, 400, '预计 11月21日 送达', C['ink3'])]
    yy = y + 40
    for n, sz, wt, sample, col in ty:
        yy += sz + 12
        d.add(G(f'type-{n.split()[0].lower()}', [T(0, 0, sample, sz, wt, col, num=True), T(364, -2, n, 11, 500, C['ink3'], 'end')], x, yy))
    yy += 16
    d.add(G('type-number', [T(0, 52, '18:40', 56, 500, num=True, ls=-1), T(364, 30, 'Number · Flex 500', 11, 500, C['ink3'], 'end'),
                            T(364, 46, 'ROND 100 · tabular-nums', 11, 500, C['ink3'], 'end')], x, yy))
    y = 624
    d.add(sec(x, y, '按钮 · 高度 56 / 48 / 36'))
    d.add(G('button-primary', btn(176, 56, '批准并支付', 'primary', 'lock'), x, y + 24))
    d.add(G('button-outline', btn(176, 56, '拒绝', 'outline'), x + 188, y + 24))
    d.add(G('button-tonal', btn(176, 48, '接管', 'tonal', 'hand'), x, y + 92))
    d.add(G('button-white', btn(176, 48, '新建定时任务', 'white', 'plus', 15), x + 188, y + 92))
    d.add(G('button-small', btn(80, 36, '去审批', 'primary', size=13), x, y + 152))
    d.add(G('button-small-tonal', btn(72, 36, '管理', 'tonal', size=13), x + 92, y + 152))
    d.add(G('button-text', btn(80, 36, '新建', 'text', 'plus', 13), x + 176, y + 152))
    d.add(G('button-icon', [round_btn('arrow-left', 'btn')], x + 280, y + 150))
    d.add(G('button-icon-2', [round_btn('more', 'btn')], x + 324, y + 150))
    y = 856
    d.add(sec(x, y, 'Chip / 标签'))
    c1, w1 = chip('喜欢靠窗座位', '#FFFFFF', 40, 15, 500, pad=18, stroke=HAIR)
    c1[0] = c1[0].replace('stroke-width="1.5"', f'stroke-width="1" filter="{card_shadow()}"')
    d.add(G('chip-memory', c1, x, y + 24))
    c2, w2 = chip('+ 添加', 'none', 40, 15, 500, C['ink2'], pad=18, stroke='#C9CDDA')
    c2[0] = c2[0].replace('stroke-width="1.5"', 'stroke-width="1" stroke-dasharray="4 3"')
    d.add(G('chip-add', c2, x + w1 + 8, y + 24))
    c3, w3 = chip('3 个结果', MIST2, 28, 12, 500, C['ink2'], pad=12)
    d.add(G('chip-result', c3, x, y + 80))
    c4, w4 = chip('最低 ¥2,180', MIST2, 28, 12, 600, pad=12)
    d.add(G('chip-price', c4, x + w3 + 8, y + 80))
    d.add(G('tag-pending', [f'<rect width="68" height="28" rx="8" fill="#FFFFFF" stroke="{HAIR}"/>', f'<circle cx="15" cy="14" r="3.5" fill="{STATUS["amber"]}"/>',
                            T(25, 18.7, '待审批', 13, 700, STATUS['amber_ink'])], x + w3 + w4 + 16, y + 80))
    y = 1000
    d.add(sec(x, y, '开关 / 分段控件'))
    d.add(toggle_g(True, name='switch-on', x=x, y=y + 24))
    d.add(toggle_g(False, name='switch-off', x=x + 68, y=y + 24))
    d.add(G('switch-on-hero', [f'<rect x="-8" y="-8" width="68" height="48" rx="24" fill="{lin(RECIPE["aurora"]["base"], 0, 0, 1, 0, key="lg-aur-h")}"/>'] + toggle(True, on_sat=True), x + 152, y + 24))
    d.add(T(x + 230, y + 46, '英雄卡上用半透明轨道', 12, 500, C['ink2']))
    d.add(G('segmented', segmented(0), x, y + 76))
    y = 1176
    d.add(sec(x, y, '时间线节点（形状语义）'))
    a = lin([(0, desat(C['he492'])), (.55, ACCENT), (1, desat(C['glow']))], 0, 0, 1, 1, key='lg-orb')
    nodes = [('完成 ●', [f'<circle cx="20" cy="20" r="13" fill="{ACCENT}"/>', icon('check', 12, 12, 16, '#FFFFFF', 2.6)]),
             ('Agent ✿', [f'<path d="{lobed(20, 20, 16, 12, .08)}" fill="{a}"/>']),
             ('执行中 ▢', [f'<rect x="5" y="5" width="30" height="30" rx="10" fill="#FFFFFF" stroke="{ACCENT}" stroke-width="1.5"/>', f'<rect x="14" y="14" width="12" height="12" rx="4" fill="{ACCENT}"/>']),
             ('等待你 ○', [f'<circle cx="20" cy="20" r="12" fill="#FFFFFF" stroke="{STATUS["amber"]}" stroke-width="2.5"/>']),
             ('未来 ■', [f'<rect x="10" y="10" width="20" height="20" rx="6" fill="#D5D9E4"/>'])]
    for i, (n, el) in enumerate(nodes):
        d.add(G(f'node-{["done","agent","running","waiting","future"][i]}', el + [T(20, 60, n, 12, 500, C['ink2'], 'middle')], x + i * 76, y + 24))
    y = 1304
    d.add(sec(x, y, '图标 · 玻璃底 + 单一强调色', '双层：渐变玻璃底 + 强调色晕 + 顶部高光 · 24px / 2px 描边'))
    for i, (n, shp) in enumerate([('utensils', 'scallop'), ('plane', 'flower'), ('bag', 'pill'), ('mail', 'square'), ('cloud-sun', 'circle'), ('package', 'square'), ('receipt', 'square')]):
        d.add(G(f'glass-icon-{n}', [glass_icon(n, 44, shp, isize=22)], x + i * 52, y + 48))
    names = ['plus', 'mic', 'sparkle', 'check', 'arrow-left', 'close', 'more', 'home', 'chat', 'clock', 'person', 'lock', 'card', 'shield', 'hand', 'pause']
    for i, n in enumerate(names):
        cx_, cy_ = x + (i % 8) * 46, y + 112 + (i // 8) * 44
        d.add(G(f'icon-tile-{n}', [icon(n, 8, 8, 24, C['ink'])], cx_, cy_))

    # ---- column 3: composer, nav, orb, bento, wallet
    x, y = COL[2], 184
    d.add(sec(x, y, '输入胶囊 Composer · 4 态', '1px 渐变描边（雾化）：空闲 22% · 输入 45% · 语音 70% · 运行 55%'))
    for i, (stt, lab) in enumerate([('idle', '空闲'), ('typing', '输入中'), ('voice', '语音'), ('running', 'Agent 运行中')]):
        yy = y + 72 + i * 88
        d.add(G(f'composer-{stt}', [T(0, -8, lab, 12, 600, C['ink2']), G('composer', composer(364, 56, stt))], x, yy))
    y = 624
    d.add(sec(x, y, '底部导航 · v0.3', '首页 · 对话 · 速览 · 任务；「我的」走右上角头像'))
    nv, nw = bottom_nav('home')
    d.add(G('bottom-nav', nv, x, y + 44))
    y = 760
    d.add(sec(x, y, 'Agent 光球 / 头像', '闲置 = 玻璃静止；工作中 = 才发光'))
    d.add(G('agent-orb-idle', agent_orb(88), x, y + 44))
    d.add(G('agent-orb-active', agent_orb(88, active=True), x + 112, y + 44))
    d.add(G('agent-avatar', flower_avatar(40), x + 232, y + 68))
    d.add(G('agent-avatar-sm', flower_avatar(24), x + 288, y + 76))
    d.add(T(x, y + 152, '闲置', 12, 500, C['ink2']))
    d.add(T(x + 112, y + 152, '工作中', 12, 500, C['ink2']))
    y = 944
    d.add(sec(x, y, 'Bento 卡 · 1 英雄 + 白卡'))
    fc = flight_hero(364, 160)
    d.add(G('bento-2x1-hero', fc, x, y + 24))
    wc = [light(174, 160, R_CARD, name='card-weather'), T(20, 36, '天气', 15, 600), T(20, 56, '上海 · 多云转晴', 12, 500, C['ink2']),
          G('weather-icon', [glass_icon('cloud-sun', 40, 'circle', isize=22)], 114, 20),
          T(18, 120, '22°', 44, 500, num=True), T(20, 146, '最高 24° · 最低 16°', 12, 500, C['ink2'], num=True)]
    d.add(G('bento-1x1-white', wc, x, y + 200))
    d.add(G('skill-card', skill_card('订餐厅', 'utensils', 'scallop', True, '本月用了 3 次'), x + 190, y + 200))
    y = 1400
    d.add(sec(x, y, '钱包叠卡 · 白 / 雾'))
    sw_ = 364
    st = [G('stack-back-2', light(sw_ - 32, 64, 22, name='b2', sh=False, fill='#EEF0F6'), 16, 0),
          G('stack-back-1', light(sw_ - 16, 64, 24, name='b1', sh=False, fill='#F6F7FB'), 8, 12),
          G('stack-front', [light(sw_, 88, R_CARD, name='front'),
                            f'<rect x="{sw_/2-16}" y="8" width="32" height="4" rx="2" fill="{C["ink3"]}" fill-opacity=".4"/>',
                            G('pkg-icon', [glass_icon('package', 40, 'square', isize=22)], 16, 28),
                            T(68, 45, '包裹 · 明天到达', 15, 600), T(68, 66, '2 件 · 已到达转运中心', 12, 500, C['ink2'])], 0, 24)]
    d.add(G('wallet-stack', st, x, y + 32))

    # ---- column 4: approval, live run, list row, bubbles, rules
    x, y = COL[3], 184
    d.add(sec(x, y, '审批卡 Approval', 'Sunrise 只在头部 · 金额 / 收货地 / 付款方式同屏'))
    d.add(G('card-approval', approval_card(360), x, y + 56))
    y = 744
    d.add(sec(x, y, 'Live Run 步骤行'))
    rows = [G(f'step-{stt}', step_row(s_, stt, 340), 0, 40 + i * 44) for i, (s_, stt) in enumerate([('打开预订页面', 'done'), ('确认空位', 'run'), ('提交预订', 'todo')])]
    d.add(G('live-run-steps', rows, x, y))
    y = 936
    d.add(sec(x, y, '列表行 · 定时任务'))
    d.add(G('list-row-schedule', task_row('每日早报', '每天 08:00', 'sun'), x, y + 24))
    y = 1056
    d.add(sec(x, y, '用户气泡 / Agent 消息'))
    msg = '帮我订周六晚上两人的餐厅'
    bw = round(tw(msg, 16) + 40)
    d.add(G('bubble-user', [f'<rect width="{bw}" height="48" rx="24" fill="{C["ink"]}"/>', T(20, 29.8, msg, 16, 400, '#FFFFFF')], x + 364 - bw, y + 24))
    d.add(G('message-agent', [G('agent-avatar', flower_avatar(24)), T(36, 18, '好的，我来查附近可订的餐厅。', 16)], x, y + 92))
    y = 1216
    d.add(sec(x, y, '阴影 · 描边 · 规则'))
    notes = ['卡片：1px 描边 #E6E8F0 + 阴影 dy 6 · blur 12 · #1B2150 6%',
             '英雄卡：内缩彩色投影 dy 10 · blur 16 · 主色 18%',
             '英雄卡质感：顶边高光描边 + 斜向光带 22% + 颗粒 6%',
             '每屏 1 个英雄渐变；其余白卡 / 雾卡（#F0F2F8）',
             '强调色 he-447 只给：选中、进度、开关、图标、链接',
             '琥珀 / 薄荷 / 珊瑚只用于 ≤24dp 的点、标签、徽标',
             '背景：不放彩色光斑；粒子 ≤6 个、18–25% 透明',
             '间距 8dp：页边 24 · 卡间 16 · 卡内边 20/24',
             '圆角：行 20 · 卡 28 · 面板 32 · 按钮/chip 胶囊',
             '数字一律 tabular-nums；Flex 大数字 ROND 100',
             '直角张力：仅「待审批」标签用 8dp 圆角']
    d.add(G('rules', [T(0, 40 + i * 26, '· ' + n, 13, 400, C['ink2'] if i not in (3, 4, 5) else C['ink']) for i, n in enumerate(notes)], x, y))

    # =================== v0.3 additions
    x, y = 48, 1688
    d.add(f'<rect x="48" y="{y-56}" width="1584" height="1" fill="{HAIR}"/>')
    d.add(sec(x, y, 'v0.3 · 事件插画 Event illustrations', '120×96 网格 · 2.5D 柔和 · hero 版 = 白色半透明 + 高光，融进英雄渐变右侧 / 底部；light 版 = 强调色雾面，放白卡 · 无第三方 logo / emoji'))
    tw_, gap = 132, 13.2
    for i, k in enumerate(ILLOS):
        cx_ = x + i * (tw_ + gap)
        d.add(G(f'illo-tile-hero-{k}', [mesh(tw_, 120, 20, 'aurora', 'illo-bg', shadow_op=.1, noise=False), illo(k, 3, 12, .95, 'hero'),
                                         T(0, 144, ILLO_LABEL[k], 13, 600), T(0, 162, f'illo-{k}', 11, 500, C['ink3'])], cx_, y + 48))
        d.add(G(f'illo-tile-light-{k}', [light(tw_, 120, 20, name='illo-bg'), illo(k, 3, 12, .95, 'light')], cx_, y + 236))
    d.add(T(x, y + 384, '用法：hero 版只在当屏唯一的英雄渐变上；白卡上用 light 版并缩到 0.56–0.66×；插画不承载信息，信息永远在文字里。天气按状态切换 sunny / cloudy / rain；快递 = package（到达）/ truck（运输中）；股票 / 价格 = chart。', 13, 500, C['ink2']))

    y = 2128
    x = COL[0]
    d.add(sec(x, y, '底部导航 · 三个根页', '选中 = 强调色浅底胶囊 + 图标 + 文字'))
    for i, a in enumerate(['home', 'glance', 'tasks']):
        nv, nw = bottom_nav(a)
        d.add(G(f'bottom-nav-{a}', nv, x, y + 44 + i * 80))
    nv, nw = bottom_nav(None)
    d.add(G('bottom-nav-none', nv, x, y + 284))
    d.add(T(x, y + 372, '二级页（记忆与技能）：无选中态', 12, 500, C['ink2']))
    d.add(G('avatar-entry', [avatar_btn(), T(52, 25, '头像 = 「我的」入口（各根页右上角）', 12, 500, C['ink2'])], x, y + 396))

    x = COL[1]
    d.add(sec(x, y, '来源 chip · 速览筛选', '纯文字 + 线性图标，不用平台 logo'))
    cx_ = x
    rowy = y + 44
    for i, (lab, ic) in enumerate(GLANCE_SRC):
        c, w_ = source_chip(lab, i == 0, ic)
        if cx_ + w_ > x + 380:
            cx_, rowy = x, rowy + 44
        d.add(G(f'source-chip-{i}', c, cx_, rowy)); cx_ += w_ + 6
    d.add(T(x, y + 160, 'AI 生成角标', 13, 600, C['ink2']))
    b, bw = ai_badge()
    d.add(G('ai-badge-light', b, x, y + 172))
    d.add(G('ai-badge-hero', [f'<rect x="-10" y="-8" width="{bw+20}" height="38" rx="19" fill="{lin(RECIPE["aurora"]["base"], 0, 0, 1, 0, key="lg-aur-h")}"/>'] + ai_badge(on_hero=True)[0], x + bw + 32, y + 172))
    d.add(T(x, y + 236, 'Home 轻量提示行（最多 2 行）', 13, 600, C['ink2']))
    d.add(G('hint-row', hint_row(STATUS['amber'], '1 件待你确认', '降噪耳机 · ¥1,299', name='hint'), x, y + 248))
    d.add(G('hint-row-2', hint_row(ACCENT, '今日速览已更新', '3 条信息差', name='hint'), x, y + 312))

    x = COL[2]
    d.add(sec(x, y, '设置列表行 · 我的', '玻璃图标 36 · 单行 56 / 双行 64 / 三行 82 · 状态用标签'))
    el, gh = set_group('账户', ME_GROUPS[0][1])
    d.add(G('settings-group', el, x, y + 48))

    x = COL[3]
    d.add(sec(x, y, '自定义卡 · 速览', '虚线入口 + AI 生成卡（角标「AI 生成 · 可编辑」）'))
    d.add(G('card-add-custom', add_card(), x, y + 44))
    d.add(G('card-ai-generated', ai_stock_card(), x, y + 144))
    d.add(G('card-train', illo_card('train', '高铁', '周日出发', '09:12', 'G7012 · 虹桥 → 杭州东', name='card-train'), x + 190, y + 144))
    d.save(f'{OUT}/00-components.svg')


# =====================================================================  07 GLANCE (long scroll frame)
GL_H = 1744
GLANCE_SRC = [('全部', None), ('X', None), ('记忆', 'memory'), ('邮件', 'mail'), ('网页', 'globe')]


def glance_top(d, active=0):
    d.add(G('header', [T(24, 92, '11月19日 · 周四', 13, 500, C['ink2'], name='date'),
                       T(24, 132, '今日速览', 32, 700, name='title'),
                       T(24, 158, '从 X、记忆、邮件和网页为你整理 · 08:00 更新', 13, 400, C['ink2'], name='subtitle', num=True),
                       G('avatar', [avatar_btn()], 348, 68)]))
    x = 24
    chips = []
    for i, (lab, ic) in enumerate(GLANCE_SRC):
        c, w_ = source_chip(lab, i == active, ic)
        chips.append(G(f'chip-source-{["all","x","memory","mail","web"][i]}', c, x, 0))
        x += w_ + 6
    if x - 6 > 388:
        WARN.append(f'OVERFLOW glance chips {x}')
    d.add(G('source-filter', chips, 0, 176))


def glance_hero(w=364):
    items = [('多家模型 API 降价，长上下文降幅最大', 'X · @llm_pricing · 2 小时前', '与你相关：Helium 按 token 计费'),
             ('手机 Agent 的「审批」交互成讨论热点', '网页 · 设计周刊 · 今早', '与你相关：你在做审批卡设计'),
             ('周六东京有小雨，出发当天建议带伞', '邮件 + 记忆 · 刚刚', '与你相关：周六 MU523 出行')]
    h = 64 + 3 * 76 + 8
    c, cw_ = tag('3 条信息差', 'glass', 26, 12)
    el = [mesh(w, h, R_CARD, 'aurora', 'card-insights'),
          G('icon', [glass_icon('sparkle', 32, 'flower', on_hero=True, isize=18)], 20, 18),
          T(62, 40, '今日要点', 17, 700, '#FFFFFF', name='title'),
          G('tag-count', c, w - 20 - cw_, 21)]
    for i, (t1, t2, t3) in enumerate(items):
        y = 64 + i * 76
        fit(t1, 15, w - 56 - 20, 600, 'insight')
        it = [f'<rect x="0" y="0" width="{w-40}" height="1" fill="#FFFFFF" fill-opacity=".22"/>',
              f'<circle cx="12" cy="26" r="12" fill="#FFFFFF" fill-opacity=".22"/>', T(12, 30.5, str(i + 1), 13, 700, '#FFFFFF', 'middle', num=True),
              T(36, 31, t1, 15, 600, '#FFFFFF', name='headline'),
              T(36, 51, t2, 12, 500, '#FFFFFF', opacity=.75, name='source'),
              T(36, 68, t3, 12, 500, '#FFFFFF', opacity=.92, name='why')]
        el.append(G(f'insight-{i+1}', it, 20, y))
    return el, h


def x_card(w=364):
    body = ['手机上的 Agent 真正难的不是对话，', '而是让人放心交出付款和发消息：', '每一步都要可见、可暂停、可撤回。']
    el = [light(w, 236, R_CARD, name='card-x'),
          G('author-avatar', [f'<circle cx="20" cy="20" r="20" fill="{MIST2}"/>', f'<circle cx="20" cy="20" r="19.5" stroke="{HAIR}"/>',
                              T(20, 25, 'AL', 14, 700, C['ink2'], 'middle')], 20, 20),
          T(72, 37, 'Agent Lab', 15, 600, name='author'),
          T(72, 56, '@agentlab_dev · 3 小时前', 12, 400, C['ink2'], name='handle', num=True)]
    c, cw_ = tag('X', 'neutral', 24, 12)
    el.append(G('source-tag', c, w - 20 - cw_, 26))
    for i, l in enumerate(body):
        fit(l, 15, w - 40, 400, 'x-body')
        el.append(T(20, 92 + i * 22, l, 15, 400, name='body'))
    x = 20
    for ic, n in [('heart', '2.4k'), ('repeat', '860'), ('chat', '132')]:
        el.append(G(f'stat-{ic}', [icon(ic, 0, 0, 16, C['ink2']), T(22, 13, n, 13, 500, C['ink2'], num=True)], x, 158))
        x += 22 + tw(n, 13, 500) + 20
    why = '推给你：你在设计 Agent 审批流程 · 来自记忆'
    fit(why, 12, w - 24 - 40, 500, 'x-why')
    el.append(G('why-strip', [f'<rect width="{w-24}" height="40" rx="14" fill="{MIST2}"/>', icon('sparkle', 12, 12, 16, ACCENT),
                              T(36, 24.5, why, 12, 500, C['ink'])], 12, 184))
    return el


def budget_card(w=174, h=168):
    return [light(w, h, R_CARD, name='card-budget'),
            T(20, 36, '本月预算', 15, 600, name='label'), T(20, 56, '剩余', 12, 500, C['ink2'], name='caption'),
            G('budget-icon', [glass_icon('receipt', 40, 'square', isize=20)], w - 60, 20),
            T(20, h - 50, '¥2,340', 32, 500, ls=-.5, num=True, name='amount'),
            G('budget-bar', [f'<rect width="{w-40}" height="6" rx="3" fill="{MIST2}"/>', f'<rect width="{round((w-40)*.61)}" height="6" rx="3" fill="{ACCENT}"/>'], 20, h - 38),
            T(20, h - 16, '已用 61% · 共 ¥6,000', 12, 500, C['ink2'], name='meta', num=True)]


def ai_stock_card(w=174, h=168):
    b, bw = ai_badge()
    c, cw_ = tag('+2.1%', 'mint', 22, 11)
    spark = 'M0 30L12 26L22 28L34 16L46 20L58 8L74 2'
    ag = lin_user([(0, ACCENT, .18), (1, ACCENT, 0)], 0, 0, 0, 36, key='lg-spark-area')
    sx = (w - 44) / 74
    return [light(w, h, R_CARD, name='card-ai-stock', stroke=(desat(ACCENT, .6, .25), 1)),
            G('ai-badge', b, 16, 16),
            T(20, 60, '英伟达 · NVDA', 15, 600, name='label'),
            T(18, 94, '$186.42', 28, 500, ls=-.5, num=True, name='price'),
            G('sparkline', [f'<g transform="scale({f(sx)} .8)"><path d="{spark}V36H0Z" fill="{ag}"/>'
                            f'<path d="{spark}" stroke="{ACCENT}" stroke-width="2.2" vector-effect="non-scaling-stroke" stroke-linecap="round" stroke-linejoin="round"/></g>',
                            f'<circle cx="{f(74*sx)}" cy="1.6" r="3.5" fill="#FFFFFF" stroke="{ACCENT}" stroke-width="2"/>'], 20, 106),
            G('tag-delta', c, 20, 136),
            T(20 + cw_ + 8, 151, '每小时刷新', 11, 500, C['ink3'], name='meta')]


def wallet_packages(sw=364):
    return [G('stack-back-2', light(sw - 32, 64, 22, name='pkg-back-2', sh=False, fill='#EEF0F6'), 16, 0),
            G('stack-back-1', light(sw - 16, 64, 24, name='pkg-back-1', sh=False, fill='#F6F7FB'), 8, 12),
            G('stack-front', [light(sw, 88, R_CARD, name='pkg-front'),
                              f'<rect x="{sw/2-16}" y="8" width="32" height="4" rx="2" fill="{C["ink3"]}" fill-opacity=".4"/>',
                              illo('package', 10, 22, .56, 'light'),
                              T(88, 45, '包裹 · 明天到达', 15, 600, name='title'),
                              T(88, 66, '2 件 · 已到达转运中心', 12, 500, C['ink2'], name='meta'),
                              icon('chevron-right', sw - 36, 40, 18, C['ink3'])], 0, 24)]


def add_card(w=364, h=80):
    return [f'<rect x=".75" y=".75" width="{w-1.5}" height="{h-1.5}" rx="{R_CARD-.75}" fill="#FFFFFF" fill-opacity=".5" stroke="#C3C9DA" stroke-width="1.5" stroke-dasharray="6 5"/>',
            G('plus', [f'<circle cx="20" cy="20" r="20" fill="{ACCENT_TINT}"/>', icon('plus', 9, 9, 22, ACCENT)], 20, (h - 40) / 2),
            T(72, h / 2 - 3, '让 Helium 做一张卡', 16, 600, name='title'),
            T(72, h / 2 + 17, '说一句话，生成一张属于你的卡', 12, 400, C['ink2'], name='caption')]


def s07():
    d = new_doc(W, GL_H, f'07 · Glance 速览 · Helium {VERSION}')
    d.add(background(21, H=GL_H, dots=6))
    d.add(status_bar())
    glance_top(d, 0)
    hc, hh = glance_hero()
    d.add(G('card-insights', hc, 24, 232))
    y = 232 + hh + 40
    d.add(G('section-x', [T(24, y, 'X 热帖', 17, 700), T(388, y, '更多', 13, 600, ACCENT, 'end')]))
    d.add(G('card-x-post', x_card(), 24, y + 16))
    y = y + 16 + 236 + 40
    d.add(G('section-cards', [T(24, y, '我的卡片', 17, 700), T(388, y, '编辑', 13, 600, ACCENT, 'end')]))
    y += 16
    d.add(G('card-flight', flight_hero(364, 160), 24, y)); y += 176
    d.add(G('card-train', illo_card('train', '高铁', '周日出发', '09:12', 'G7012 · 虹桥 → 杭州东', name='card-train'), 24, y))
    d.add(G('card-weather', illo_card('cloudy', '天气', '上海 · 多云', '22°', '最高 24° · 最低 16°', name='card-weather'), 214, y)); y += 184
    d.add(G('card-budget', budget_card(), 24, y))
    d.add(G('card-ai-nvda', ai_stock_card(), 214, y)); y += 184
    d.add(G('wallet-packages', wallet_packages(), 24, y)); y += 128
    d.add(G('card-add-custom', add_card(), 24, y)); y += 80
    nav_y = GL_H - 92
    if y + 16 > nav_y:
        WARN.append(f'07 content {y} hits nav {nav_y}')
    nv, nw = bottom_nav('glance')
    d.add(G('bottom-nav', nv, (W - nw) / 2, nav_y))
    d.add(gesture_bar(H=GL_H))
    d.save(f'{OUT}/07-glance.svg')


# =====================================================================  08 GLANCE · CUSTOM CARD SHEET
def size_opt(label, kind, active):
    w, h = 112, 76
    gl = {'1x1': (14, 14), '2x1': (32, 14), '2x2': (32, 32)}[kind]
    gx = (w - gl[0]) / 2
    out = [f'<rect x="{.75 if active else .5}" y="{.75 if active else .5}" width="{w-(1.5 if active else 1)}" height="{h-(1.5 if active else 1)}" rx="18" '
           f'fill="{ACCENT_TINT if active else "#FFFFFF"}" stroke="{ACCENT if active else HAIR}" stroke-width="{1.5 if active else 1}"/>',
           f'<rect x="{gx}" y="{44-gl[1]-6}" width="{gl[0]}" height="{gl[1]}" rx="4" fill="{ACCENT if active else "#D5D9E4"}"/>',
           T(w / 2, 62, label, 13, 600 if active else 500, ACCENT if active else C['ink'], 'middle')]
    return out


def s08():
    d = new_doc(W, H, f'08 · Glance · 自定义卡 · Helium {VERSION}')
    d.add(background(21, dots=0))
    d.add(status_bar())
    glance_top(d, 0)
    hc, hh = glance_hero()
    d.add(G('card-insights', hc, 24, 232))
    d.add(G('scrim', f'<rect width="{W}" height="{H}" fill="#12131A" fill-opacity=".38"/>'))
    Y = 72
    sh = [f'<path d="M0 {32}A32 32 0 0 1 32 0H{W-32}A32 32 0 0 1 {W} 32V{H-Y}H0Z" fill="#FFFFFF" filter="{shadow("#1B2150", .12, -4, 16)}"/>',
          f'<rect x="{W/2-18}" y="10" width="36" height="4" rx="2" fill="{C["ink3"]}" fill-opacity=".5"/>',
          T(24, 56, '让 Helium 做一张卡', 22, 700, name='title'),
          T(24, 80, '用一句话描述，Helium 生成并按时刷新', 13, 400, C['ink2'], name='subtitle'),
          G('btn-close', [round_btn('close', 'btn')], W - 56, 28)]
    l1, l2 = '每天早上给我看 X 上关于', 'AI agent 的 3 条热帖'
    sh.append(G('prompt-input', [f'<rect width="364" height="104" rx="20" fill="{MIST2}"/>',
                                 f'<rect x=".75" y=".75" width="362.5" height="102.5" rx="19.25" stroke="{ACCENT}" stroke-width="1.5"/>',
                                 T(20, 34, l1, 16, 400), T(20, 58, l2, 16, 400),
                                 f'<rect x="{f(20 + tw(l2, 16) + 2)}" y="44" width="2" height="19" rx="1" fill="{ACCENT}"/>',
                                 G('btn-regenerate', btn(96, 32, '重新生成', 'white', 'refresh', 12), 252, 62)], 24, 100))
    # preview
    sh.append(T(24, 236, '预览', 13, 600, C['ink2'], name='label-preview'))
    b, bw = ai_badge()
    rows = [('手机 Agent 的审批该放在哪一步？', '2.4k'), ('开源浏览器代理新版：多标签并行', '1.8k'), ('把定时任务交给 Agent 的 5 个坑', '960')]
    pv = [light(364, 188, R_CARD, name='preview'),
          G('icon', [glass_icon('glance', 36, 'square', isize=20)], 20, 18),
          T(66, 34, 'X · AI Agent 热帖', 15, 600, name='title'),
          T(66, 52, '每天 08:00 更新 · 3 条', 12, 400, C['ink2'], name='meta', num=True),
          G('ai-badge', b, 344 - bw, 25)]
    for i, (t, n) in enumerate(rows):
        fit(t, 14, 364 - 44 - 70, 500, 'preview-row')
        pv.append(G(f'row-{i+1}', [f'<rect y="-23" width="324" height="1" fill="{HAIR}"/>' if i else '',
                                    T(0, 0, str(i + 1), 13, 700, ACCENT, num=True), T(20, 0, t, 14, 500),
                                    icon('heart', 324 - 14 - tw(n, 12, 500) - 6, -11, 14, C['ink3']),
                                    T(324, 0, n, 12, 500, C['ink2'], 'end', num=True)], 20, 92 + i * 36))
    sh.append(G('card-preview', pv, 24, 248))
    sh.append(T(24, 474, '尺寸', 13, 600, C['ink2'], name='label-size'))
    for i, (lab, k) in enumerate([('1×1 小', '1x1'), ('2×1 宽', '2x1'), ('2×2 大', '2x2')]):
        sh.append(G(f'size-{k}', size_opt(lab, k, k == '2x1'), 24 + i * 126, 486))
    sh.append(T(24, 600, '刷新频率', 13, 600, C['ink2'], name='label-refresh'))
    x = 24
    for i, lab in enumerate(['每天 08:00', '每 4 小时', '每小时', '仅手动']):
        c, w_ = source_chip(lab, i == 0, h=36, size=13)
        sh.append(G(f'chip-refresh-{i+1}', c, x, 612)); x += w_ + 8
    if x - 8 > 388:
        WARN.append(f'OVERFLOW refresh chips {x}')
    sh.append(G('source-note', [icon('search', 0, 0, 16, C['ink3']), T(24, 12.5, '来源：X 搜索 · 按互动量排序 · 添加后可随时编辑', 12, 400, C['ink2'])], 24, 666))
    sh.append(G('btn-add', btn(364, 56, '添加到速览', 'primary', 'plus', 17), 24, 712))
    d.add(G('sheet-custom-card', sh, 0, Y))
    d.add(gesture_bar())
    d.save(f'{OUT}/08-glance-custom.svg')


# =====================================================================  09 ME
def set_row(ic, title, sub=None, right=None, sub2=None, w=364, kind='chev', name='row'):
    h = 56 if not sub else (64 if not sub2 else 82)
    el = [G('icon', [glass_icon(ic, 36, 'circle', isize=19)], 14, (h - 36) / 2)]
    if sub:
        el.append(T(62, 27 if not sub2 else 28, title, 15, 600, name='title'))
        el.append(T(62, 46 if not sub2 else 48, sub, 12, 400, C['ink2'], name='sub'))
        if sub2:
            el.append(T(62, 66, sub2, 12, 400, C['ink3'], name='sub-2'))
    else:
        el.append(T(62, h / 2 + 5.4, title, 15, 600, name='title'))
    rx = w - 36
    if kind == 'tag':
        c, cw_ = tag(right, 'mint')
        el.append(G('tag-status', c, rx - 4 - cw_, (h - 24) / 2))
    elif right:
        el.append(T(rx - 4, h / 2 + 4.6, right, 13, 500, C['ink2'], 'end', num=True, name='value'))
    el.append(icon('chevron-right', w - 32, (h - 18) / 2, 18, C['ink3']))
    lim = (rx - 4 - (tw(right, 13, 500) + 12 if right else 0)) - 62
    for s_, sz in ((title, 15), (sub, 12), (sub2, 12)):
        if s_:
            fit(s_, sz, lim, 500, 'row ' + title)
    return el, h


def set_group(title, rows, w=364, name='group'):
    built = [set_row(*r, w=w) if isinstance(r, tuple) else set_row(**r, w=w) for r in rows]
    gh = sum(h for _, h in built)
    el = [T(4, 0, title, 13, 600, C['ink2'], name='group-title'), G('group-card', [light(w, gh, R_ROW + 4, name='group')], 0, 12)]
    y = 12
    for i, (r, h) in enumerate(built):
        if i:
            el.append(f'<rect x="62" y="{y}" width="{w-62}" height="1" fill="{HAIR}"/>')
        el.append(G(f'row-{i+1}', r, 0, y))
        y += h
    return el, y


ME_GROUPS = [
    ('账户', [('person', '个人资料', '姓名 · 头像 · 邮箱'),
              dict(ic='link', title='Google 账号', sub='连接 Gmail · 日历 · 云端硬盘', right='已连接', kind='tag'),
              dict(ic='cpu', title='模型与推理', sub='默认模型 OpenCode Go', sub2='可选 Gemini API（需 API Key）')]),
    ('能力', [('plug', '连接器', None, '6 个'), ('sparkle', '技能', None, '8 个'), ('server', 'MCP', None, '2 个服务器')]),
    ('记忆与文件', [('memory', '记忆', None, '126 条'), ('file', '系统文件', '偏好、规则与人设文档'), ('folder', '项目空间', None, '3 个')]),
    ('安全', [('shield', '审批规则', '付款、发消息前需你确认'), ('list', '权限与审计', '近 7 天 42 次操作'), ('key', '凭据保险箱', None, '3 项')]),
    ('通用', [('bell', '通知', None, '开'), ('palette', '外观', None, '跟随系统'), ('globe', '语言', None, '简体中文'), ('info', '关于', None, 'v0.3')]),
]


def s09():
    # measure height first
    tmp = new_doc(W, 10, 'tmp')
    hs = [set_group(t, r)[1] for t, r in ME_GROUPS]
    MH = 272 + sum(hs) + 28 * len(hs) + 56 + 40 + 24
    d = new_doc(W, MH, f'09 · 我的 · Helium {VERSION}')
    d.add(background(23, H=MH, dots=4))
    d.add(status_bar())
    d.add(G('top-app-bar', [G('btn-back', [round_btn('arrow-left', 'btn')], 16, BAR_Y), T(206, 82, '我的', 17, 600, C['ink'], 'middle', name='title')]))
    c, cw_ = tag('Helium Pro', 'glass', 26, 12)
    pf = [mesh(364, 120, R_CARD, 'aurora', 'card-profile'),
          G('avatar', [f'<circle cx="32" cy="32" r="32" fill="#FFFFFF" fill-opacity=".24"/>', f'<circle cx="32" cy="32" r="31.5" stroke="#FFFFFF" stroke-opacity=".5"/>',
                       icon('person', 16, 16, 32, '#FFFFFF')], 20, 28),
          T(100, 52, '林夏', 22, 700, '#FFFFFF', name='name'),
          T(100, 74, 'linxia@example.com', 13, 500, '#FFFFFF', opacity=.8, name='email'),
          G('plan-tag', c, 100, 84),
          T(326, 101, '管理套餐', 12, 600, '#FFFFFF', 'end', name='plan-link'),
          icon('chevron-right', 328, 89, 16, '#FFFFFF')]
    d.add(G('card-profile', pf, 24, 120))
    y = 280
    for (t, r) in ME_GROUPS:
        el, gh = set_group(t, r)
        d.add(G(f'group-{t}', el, 24, y))
        y += gh + 28
    d.add(G('btn-signout', btn(364, 48, '退出登录', 'outline', None, 15), 24, y))
    d.add(gesture_bar(H=MH))
    d.save(f'{OUT}/09-me.svg')


# =====================================================================  10 MODELS
def provider_card(name, ic, tags, line, models, action=None, selected=False, w=364):
    # wrap model chips
    chips, x, yy = [], 0, 0
    for m in models:
        c, w_ = chip(m, MIST2, 28, 12, 500, C['ink'], pad=12)
        if x + w_ > w - 40:
            x, yy = 0, yy + 36
        chips.append(G('model-chip', c, x, yy)); x += w_ + 8
    h = 112 + yy + 28 + (84 if action else 20)
    el = [light(w, h, R_CARD, name='provider', stroke=(ACCENT, 1.5) if selected else HAIR),
          G('icon', [glass_icon(ic, 40, 'circle', isize=22)], 20, 20),
          T(72, 38, name, 17, 700, name='name')]
    tx = 72
    for lab, kind in tags:
        c, w_ = tag(lab, kind, 22, 11)
        el.append(G('tag', c, tx, 48)); tx += w_ + 6
    if selected:
        el.append(G('radio-on', [f'<circle cx="12" cy="12" r="12" fill="{ACCENT}"/>', icon('check', 5, 5, 14, '#FFFFFF', 2.6)], w - 44, 24))
    else:
        el.append(G('radio-off', [f'<circle cx="12" cy="12" r="11" stroke="#C9CDDA" stroke-width="2"/>'], w - 44, 24))
    fit(line, 13, w - 40, 400, name)
    el.append(T(20, 98, line, 13, 400, C['ink2'], name='line'))
    el.append(G('models', chips, 20, 112))
    if action:
        lab, kind, ico = action
        el.append(G('btn-action', btn(w - 40, 48, lab, kind, ico, 15), 20, h - 68))
    return el, h


def s10():
    d = new_doc(W, H, f'10 · 模型与推理 · Helium {VERSION}')
    d.add(background(25, dots=0))
    d.add(status_bar())
    d.add(G('top-app-bar', [G('btn-back', [round_btn('arrow-left', 'btn')], 16, BAR_Y), T(206, 82, '模型与推理', 17, 600, C['ink'], 'middle', name='title')]))
    d.add(T(24, 128, '选择 Helium 思考与执行时调用的模型', 15, 400, C['ink2'], name='intro'))
    y = 144
    cards = [dict(name='OpenCode Go', ic='cpu', tags=[('默认', 'accent'), ('已启用', 'mint')], line='API Key 接入 · 已配置 · 对话与任务默认使用',
                  models=['GLM-5.3', 'Kimi K3', 'DeepSeek V4 Pro', 'Qwen3.8 Max'], selected=True),
             dict(name='Gemini API', ic='sparkle', tags=[('未配置', 'neutral')], line='使用 Google AI Studio 的 API Key',
                  models=['Gemini'], action=('填入你的 Gemini API Key', 'outline', 'key')),
             dict(name='自带 API Key', ic='server', tags=[('OpenAI 兼容', 'neutral')], line='填写 Base URL、API Key 与模型名',
                  models=['任意 OpenAI 兼容模型'], action=('添加', 'tonal', 'plus'))]
    for i, cd in enumerate(cards):
        el, h = provider_card(**cd)
        d.add(G(f'provider-{["opencode-go","gemini-api","byok"][i]}', el, 24, y))
        y += h + 12
    sl = 'API Key 存放在凭据保险箱，只用于调用对应模型'
    d.add(G('vault-note', [icon('lock', 0, 0, 16, C['ink2']), T(22, 12.5, sl, 12, 500, C['ink2'])], (W - 22 - tw(sl, 12, 500)) / 2, y + 4))
    if y + 24 > H - 24:
        WARN.append(f'10 overflow {y}')
    d.add(gesture_bar())
    d.save(f'{OUT}/10-models.svg')


if __name__ == '__main__':
    which = sys.argv[1:] or ['00', '01', '02', '03', '04', '05', '06', '07', '08', '09', '10']
    for w in which:
        globals()['s' + w]()
    print('built', which)
    for w_ in WARN:
        print(w_)
