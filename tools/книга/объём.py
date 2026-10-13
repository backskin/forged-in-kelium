# -*- coding: utf-8 -*-
"""ОБЪЁМ ЖЕТОНА (дизайнер 14.10.2026, с. 37: «всем жетонам тень, объём и,
самое главное, толщину»). Под картинкой жетона — торец картона: та же
форма, тёмная, сдвинутая вниз на толщину; под всем — мягкая тень.
Картинка остаётся на месте (левый верхний угол тот же), холст растёт только
вправо и вниз — выноски, посчитанные по жетону, не сдвигаются.
"""
from PIL import Image, ImageFilter


def объём(im, доля=0.065, тень=True):
    im = im.convert("RGBA")
    w, h = im.size
    т = max(3, round(h * доля))
    размытие = max(2, round(h * 0.035))
    поле = т + 3 * размытие
    out = Image.new("RGBA", (w + поле, h + поле), (0, 0, 0, 0))
    альфа = im.getchannel("A")
    if тень:
        с = Image.new("RGBA", out.size, (0, 0, 0, 0))
        чёрн = Image.new("RGBA", im.size, (20, 14, 8, 115))
        чёрн.putalpha(альфа.point(lambda v: v * 115 // 255))
        с.alpha_composite(чёрн, (round(т * 0.7), round(т * 1.6)))
        out.alpha_composite(с.filter(ImageFilter.GaussianBlur(размытие)))
    # торец: цвет кромки жетона, затемнённый, к низу ещё темнее
    кромка = im.convert("RGB").resize((1, 1), Image.BOX).getpixel((0, 0))
    for k in range(т, 0, -1):
        тон = 0.42 - 0.12 * k / т
        слой = Image.new("RGBA", im.size, tuple(round(c * тон) for c in кромка) + (255,))
        слой.putalpha(альфа)
        out.alpha_composite(слой, (0, k))
    out.alpha_composite(im, (0, 0))
    return out
