# -*- coding: utf-8 -*-
"""Иконка «произвести боеприпасы» — монохромная, линией, как «все иконки-62»,
но с сундучком боеприпасов (как «все иконки-2») вместо кубика (Влад 03.10.2026).

Геометрия снята с иконки 62: холст 402, цвет линии #4C5467, заливка #FAFAFA,
обводка 11 px, плюс 109..211 × 95..197 с плечом 46 px.
python tools/icon_produce_ammo.py <выход.png>
"""
import math
import sys
from PIL import Image, ImageDraw

S = 4                         # суперсэмплинг
W = 402
INK = (76, 84, 103, 255)
FILL = (250, 250, 250, 255)
LW = 11


def p(x, y):
    return (x * S, y * S)


def poly(d, pts, fill=FILL):
    q = [p(*t) for t in pts]
    d.polygon(q, fill=fill)
    d.line(q + [q[0], q[1]], fill=INK, width=LW * S, joint='curve')
    for x, y in q:                                  # круглые углы
        r = LW * S / 2
        d.ellipse((x - r, y - r, x + r, y + r), fill=INK)


def line(d, pts, w=LW):
    q = [p(*t) for t in pts]
    d.line(q, fill=INK, width=int(w * S), joint='curve')
    r = w * S / 2
    for x, y in (q[0], q[-1]):
        d.ellipse((x - r, y - r, x + r, y + r), fill=INK)


def add(*v):
    return (sum(t[0] for t in v), sum(t[1] for t in v))


def mul(v, k):
    return (v[0] * k, v[1] * k)


def нарисовать():
    im = Image.new('RGBA', (W * S, W * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # ПЛЮС — как на иконке 62
    c, half, arm = (160, 146), 51, 23.5
    плюс = [(c[0] - arm, c[1] - half), (c[0] + arm, c[1] - half), (c[0] + arm, c[1] - arm),
            (c[0] + half, c[1] - arm), (c[0] + half, c[1] + arm), (c[0] + arm, c[1] + arm),
            (c[0] + arm, c[1] + half), (c[0] - arm, c[1] + half), (c[0] - arm, c[1] + arm),
            (c[0] - half, c[1] + arm), (c[0] - half, c[1] - arm), (c[0] - arm, c[1] - arm)]
    плюс = [(x + 5.5, y + 5.5) for x, y in плюс]    # внешний край 109..211 при обводке 11
    poly(d, плюс)
    # СУНДУЧОК: длинная сторона влево-вниз, глубина вправо, крышка, ручка
    k30 = math.cos(math.radians(30))
    F = (248, 306)                                   # передний нижний угол
    a = mul((-k30, -0.5), 88)                       # длинная сторона
    b = mul((k30, -0.5), 60)                         # глубина
    up = (0, -80)
    lid = (0, 24)                                    # высота крышки
    over = 5                                         # свес крышки
    oa, ob = mul((-k30, -0.5), over / 1), mul((k30, -0.5), over / 1)
    # корпус: две видимые грани
    poly(d, [F, add(F, a), add(F, a, up, lid), add(F, up, lid)])
    poly(d, [F, add(F, up, lid), add(F, b, up, lid), add(F, b)])
    # крышка: чуть шире корпуса (свес), своя призма
    Fl = add(F, up, lid, (0, 0), mul((k30, 0.5), over * 1.4))
    Fl = (F[0], F[1] + up[1] + lid[1] + over * 0.7)
    A, B = add(a, mul((-k30, -0.5), over * 2)), add(b, mul((k30, -0.5), over * 2))
    Fl = add(Fl, mul((k30, 0.5), over), mul((-k30, 0.5), over))
    Fl = (F[0], Fl[1])
    h = (0, -lid[1] - over)
    poly(d, [Fl, add(Fl, A), add(Fl, A, h), add(Fl, h)])
    poly(d, [Fl, add(Fl, h), add(Fl, B, h), add(Fl, B)])
    top = add(Fl, h)
    poly(d, [top, add(top, A), add(top, A, B), add(top, B)])
    # ручка: скоба вдоль длинной стороны, по середине глубины
    m = add(top, mul(B, 0.5))
    n1, n2 = add(m, mul(A, 0.25)), add(m, mul(A, 0.75))
    hh = (0, -22)
    # трубка ручки: широкая линия цвета обводки, внутри — заливка, чтобы ручка
    # закрывала задний край крышки, а не сливалась с ним
    скоба = [n1, add(n1, hh), add(n2, hh), n2]
    line(d, скоба, w=LW * 2.6)
    q = [p(*t) for t in скоба]
    d.line(q, fill=FILL, width=int(LW * 0.6 * S), joint='curve')
    # патрон на длинной грани: острым носом к переднему углу
    def грань(u, v):                                 # u — вдоль длинной стороны, v — вверх
        return add(F, mul(a, u), (0, -(up[1] * -1 - lid[1]) * v))
    vc, vh = 0.47, 0.20
    u0, u1 = 0.36, 0.11                              # основание носа и острие
    верх = [грань(u0 + (u1 - u0) * t, vc + vh * math.cos(t * math.pi / 2) ** 0.7)
            for t in [i / 12 for i in range(13)]]
    низ = [грань(u0 + (u1 - u0) * t, vc - vh * math.cos(t * math.pi / 2) ** 0.7)
           for t in [i / 12 for i in range(13)]]
    d.polygon([p(*t) for t in верх + низ[::-1]], fill=INK)
    # гильза — пустая, обводкой, как на сундучке «все иконки-2»; нос — сплошной
    гильза = [грань(0.42, vc - vh * 0.92), грань(0.42, vc + vh * 0.92),
              грань(0.74, vc + vh * 0.92), грань(0.74, vc - vh * 0.92)]
    q = [p(*t) for t in гильза]
    d.line(q + [q[0], q[1]], fill=INK, width=int(LW * 0.75 * S), joint='curve')
    im = im.resize((W, W), Image.LANCZOS)
    # группа по центру рамки иконки 62 (108..295 × 95..310)
    x0, y0, x1, y1 = im.getbbox()
    out = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    out.alpha_composite(im, (round(201.5 - (x0 + x1) / 2), round(202.5 - (y0 + y1) / 2)))
    return out


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else 'произвести боеприпасы.png'
    нарисовать().save(out)
    print(out)
