package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;

import kelium.report.ReplayRecord;

/**
 * PlayerStrip — ПОЛОСА ОДНОГО ИГРОКА: двенадцать показателей, читаемых одним
 * взглядом, и одинаково у всех четырёх мест.
 *
 * <p>Чем отличается от зоны игрока 1.0. Там показатели были строками текста вида
 * {@code «Здания: ЦУ@h2_1 [запитан] · ДЗ@h2_1 [запитан] · Э1@h2_1 · …»}, строки не
 * переносились, поэтому у каждой зоны появлялась ГОРИЗОНТАЛЬНАЯ ПРОКРУТКА, а у
 * боковых мест из двенадцати строк было видно три с половиной. Сравнить двух игроков
 * было физически невозможно.
 *
 * <p>Здесь: значок + КРУПНОЕ число + мелкий предел. Полный перечень с гексами —
 * в подсказке (она никуда не делась). Внутренних кодов на экране нет.
 *
 * <p><b>Дельты.</b> Если показатель изменился за последние три шага, рядом
 * появляется бейдж {@code ▲+2} / {@code ▾−1}. Сравнение идёт с кадром на три шага
 * назад — так значение не «залипает» при перемотке ползунком в любую точку.
 */
public final class PlayerStrip extends JComponent {

    private static final long serialVersionUID = 1L;

    /** На сколько шагов назад смотрим, чтобы показать изменение. */
    private static final int DELTA_WINDOW = 3;

    private final Session session;
    private final int seat;
    /** Куда сообщать «показали на плитку»: место и ключ показателя. */
    private BiConsumer<Integer, String> onTile = (s, k) -> { };
    private final Map<String, Rectangle> tileBounds = new LinkedHashMap<>();
    private String hot;

    public PlayerStrip(Session session, int seat) {
        this.session = session;
        this.seat = seat;
        setOpaque(true);
        setFont(f(13, java.awt.Font.PLAIN));
        ToolTipManager.sharedInstance().registerComponent(this);
        session.whenFrameChanged(s -> repaint());
        session.whenRecordChanged(s -> repaint());
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                String was = hot;
                hot = boardButton().contains(e.getPoint()) ? "board" : keyAt(e.getPoint());
                setCursor(hot == null ? Cursor.getDefaultCursor()
                    : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                if (was != hot) {
                    repaint();
                }
            }
        });
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hot = null;
                repaint();
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // КНОПКА ПЛАНШЕТА в правом верхнем углу полосы — открывает всё
                // хозяйство игрока отдельным окном (просьба дизайнера 13.08.2026)
                if (boardButton().contains(e.getPoint())) {
                    BoardWindow.show(PlayerStrip.this.session, PlayerStrip.this.seat,
                        javax.swing.SwingUtilities.getWindowAncestor(PlayerStrip.this));
                    return;
                }
                String key = keyAt(e.getPoint());
                onTile.accept(seat, key);       // null = щёлкнули по полосе целиком
            }
        });
    }

    public void setOnTile(BiConsumer<Integer, String> listener) {
        this.onTile = listener;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(260), Theme.px(Theme.H_STRIP));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(Theme.px(180), Theme.px(Theme.H_STRIP_TIGHT));
    }

    // ==================== показатели ====================
    /**
     * Плитка: ключ, код значка, цвет значка, значение и предел.
     *
     * <p>Значок — КОД для {@link MarkIcons}, а не символ шрифта: ◇ ◈ ◆ ♦ ▣ в
     * системном шрифте Windows рисуются пустыми квадратами.
     */
    private record Tile(String key, String icon, Color colour, int value, int cap,
                        String tip) {
    }

    private List<Tile> tiles(ReplayRecord.Player p) {
        List<Tile> out = new ArrayList<>();
        out.add(new Tile("coin", "COIN", Theme.ink2(), p.coin, -1,
            "Монеты на руках."));
        out.add(new Tile("kelium", "KELIUM", Theme.kelium(), p.kelium, p.keliumCap,
            "Келемий на складе и предел его хранения."));
        out.add(new Tile("ammo", "AMMO", Theme.ink2(), p.ammo, p.ammoCap,
            "Боеприпасы и предел их хранения."));
        out.add(new Tile("debris", "DEBRIS", Theme.debris(), p.debris, p.debrisCap,
            "Обломки: чёрные кубики склада, ими платят за шаги науки."));
        out.add(new Tile("containers", "CONTAINER", Theme.container(), p.containers,
            p.containerCap, "Контейнеры на складе."));
        return out;
    }

    // ==================== отрисовка ====================
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        tileBounds.clear();

        ReplayRecord.Frame f = session.frame();
        ReplayRecord.Player p = player(f);
        boolean active = f != null && f.snapshot != null && f.snapshot.active != null
            && f.snapshot.active == seat;

        int w = getWidth();
        int h = getHeight();
        g.setColor(Theme.bg());
        g.fillRect(0, 0, w, h);
        // Подложка: у активного игрока — лёгкая заливка цветом места и полоска слева.
        // В 1.0 активная зона красилась целиком и спорила с полем за внимание.
        g.setColor(Theme.panel());
        g.fill(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, Theme.R_PANEL * 2,
            Theme.R_PANEL * 2));
        if (active) {
            g.setColor(Theme.seatWash(seat, 0.10));
            g.fill(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, Theme.R_PANEL * 2,
                Theme.R_PANEL * 2));
        }
        g.setColor(active ? Theme.seat(seat) : Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, Theme.R_PANEL * 2,
            Theme.R_PANEL * 2));
        if (active) {
            g.setColor(Theme.seat(seat));
            g.fill(new RoundRectangle2D.Double(1, Theme.px(6), Theme.px(3), h - Theme.px(12),
                Theme.px(3), Theme.px(3)));
        }
        if (p == null) {
            paintSkeleton(g, w, h);
            g.dispose();
            return;
        }

        int pad = Theme.px(8);
        int x = pad + Theme.px(4);
        int y = pad + Theme.px(2);

        // ---- спарклайн победных очков ФОНОМ: «кто разгоняется» видно без графика
        paintSparkline(g, w, h);

        // ---- шапка: ПЛАШКА цвета места с именем бота, победные очки, дельта.
        // Плашка, а не цветной текст: краска должна быть видна пятном и совпадать
        // с цветом жетона на поле (замечание дизайнера 13.08.2026).
        int vp = p.vp.getOrDefault("total", 0);
        String bot = (seat + 1) + " · " + (seat < session.record().seatLabels.size()
            ? session.record().seatLabels.get(seat) : "бот");
        java.awt.Font chipFont = f(12, java.awt.Font.BOLD);
        // место оставляем и под очки справа, и под кнопку личной зоны рядом
        int chipW = Math.min(w - Theme.px(180),
            SeatChip.widthFor(g, bot, chipFont));
        String shown = bot;
        while (shown.length() > 4
                && SeatChip.widthFor(g, shown, chipFont) > chipW) {
            shown = shown.substring(0, shown.length() - 1);
        }
        if (!shown.equals(bot)) {
            shown = shown + "…";
        }
        SeatChip.paintChip(g, seat, shown, chipFont, x, y, chipW, Theme.px(18), true);

        g.setFont(num(26));
        String vps = String.valueOf(vp);
        int vpw = g.getFontMetrics().stringWidth(vps);
        g.setColor(Theme.points());
        g.drawString(vps, w - pad - Theme.px(26) - vpw, y + Theme.px(16));
        g.setFont(f(11, java.awt.Font.BOLD));
        g.setColor(Theme.ink3());
        g.drawString("ПО", w - pad - Theme.px(22), y + Theme.px(16));
        int dvp = delta("vp");
        if (dvp != 0) {
            badge(g, dvp, w - pad - Theme.px(26) - vpw - Theme.px(34), y + Theme.px(6));
        }

        // ЖЕТОНЫ ПОБЕДНЫХ ОЧКОВ — ЗВЁЗДЫ, которые уже лежат перед игроком.
        // Число «ПО» выше — это ВЕСЬ счёт, включая то, что будет посчитано по
        // столу только в конце партии и пока может ещё пропасть. Звёзды же
        // получены и не отнимаются, поэтому показываются отдельно: иначе по
        // экрану не отличить прочное очко от предварительного.
        int stars = 0;
        for (String key : kelium.engine.Scoring.STAR_TOKEN_SOURCES) {
            stars += p.vp.getOrDefault(key, 0);
        }
        if (stars > 0) {
            String st = String.valueOf(stars);
            g.setFont(f(11, java.awt.Font.BOLD));
            int stw = g.getFontMetrics().stringWidth(st);
            double sx = w - pad - Theme.px(14) - stw;
            paintStar(g, sx - Theme.px(6), y + Theme.px(28), Theme.px(9));
            g.setColor(Theme.points());
            g.drawString(st, (int) sx + Theme.px(2), y + Theme.px(31));
        }

        // ---- КНОПКА «ЛИЧНАЯ ЗОНА» — сразу за плашкой игрока, с подписью.
        // Раньше это был безымянный значок в правом верхнем углу: на кнопку он не
        // походил вовсе и вдобавок налезал на победные очки (замечание дизайнера
        // 13.08.2026).
        boardX = x + chipW + Theme.px(8);
        boardY = y;
        g.setFont(f(11, java.awt.Font.PLAIN));
        boardW = g.getFontMetrics().stringWidth(BOARD_LABEL) + Theme.px(22);
        Rectangle bb = boardButton();
        boolean overBoard = "board".equals(hot);
        g.setColor(overBoard ? Theme.hover() : Theme.tile());
        g.fill(new RoundRectangle2D.Double(bb.x, bb.y, bb.width, bb.height,
            Theme.R_TILE, Theme.R_TILE));
        g.setColor(overBoard ? Theme.accent() : Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(bb.x, bb.y, bb.width, bb.height,
            Theme.R_TILE, Theme.R_TILE));
        // значок планшета слева от подписи
        int ix = bb.x + Theme.px(5);
        g.setColor(Theme.ink3());
        for (int i = 0; i < 3; i++) {
            int ly = bb.y + Theme.px(6) + i * Theme.px(3);
            g.drawLine(ix, ly, ix + Theme.px(8) - i, ly);
        }
        g.setColor(overBoard ? Theme.ink() : Theme.ink2());
        g.drawString(BOARD_LABEL, bb.x + Theme.px(17),
            bb.y + bb.height / 2 + Theme.px(4));

        // ---- РАСКЛАД ИГРОКА строкой под именем: колода приказов и стороны его
        // двух планшетов (просьба дизайнера 14.08.2026). Это не украшение: колода
        // приказов — главная асимметрия партии, а стороны планшетов определяют,
        // чем игрок бьёт и сколько может хранить. Раньше всё это было видно
        // только в окне настройки ДО партии, а во время разбора — нигде.
        //
        // ШРИФТ КРУПНЕЕ И С ВОЗДУХОМ (замечание дизайнера 14.08.2026: строка
        // была мелкой и лепилась к шапке без отступа). Плюс ЦВЕТНАЯ ТОЧКА перед
        // названием колоды — печатный цвет колоды виден с ходу, а не только
        // словом в подсказке ({@link Names#orderDeckColour}).
        g.setFont(f(12, Font.PLAIN));
        int lineY = y + Theme.px(34);
        int lx = x;
        if (p.orderColor != null && !p.orderColor.isBlank()) {
            int dotR = Theme.px(4);
            g.setColor(Names.orderDeckColour(p.orderColor));
            g.fillOval(lx, lineY - dotR - Theme.px(2), dotR * 2, dotR * 2);
            lx += dotR * 2 + Theme.px(6);
            g.setColor(Theme.ink2());
            String deckText = "приказы " + Names.orderDeck(p.orderColor);
            g.drawString(deckText, lx, lineY);
            lx += g.getFontMetrics().stringWidth(deckText) + Theme.px(14);
        }
        String rest = setupLineBoards(p);
        if (!rest.isEmpty()) {
            g.setColor(Theme.ink3());
            g.drawString(rest, lx, lineY);
        }

        // ---- плитки показателей
        int ty = y + Theme.px(44);
        int tileH = Theme.px(30);
        List<Tile> list = tiles(p);
        int gap = Theme.px(6);
        int tileW = Math.max(Theme.px(40), (w - 2 * pad - (list.size() - 1) * gap) / list.size());
        int tx = pad;
        for (Tile t : list) {
            Rectangle r = new Rectangle(tx, ty, tileW, tileH);
            tileBounds.put(t.key(), r);
            paintTile(g, t, r);
            tx += tileW + gap;
        }

        // ---- третья строка: наука, здания, войска, карты, приказы, супер-задание
        if (h >= Theme.px(Theme.H_STRIP - 8)) {
            detailsTop = ty + tileH + Theme.px(6);
            paintDetails(g, p, f, pad, detailsTop, w - 2 * pad);
        } else {
            detailsTop = Integer.MAX_VALUE;
        }
        g.dispose();
    }

    /** Бледный скелет: видно, где что появится, когда запись загрузится. */
    private void paintSkeleton(Graphics2D g, int w, int h) {
        g.setColor(Theme.alpha(Theme.tile(), 0.7));
        int pad = Theme.px(8);
        g.fill(new RoundRectangle2D.Double(pad, pad, w * 0.45, Theme.px(14),
            Theme.R_TILE, Theme.R_TILE));
        for (int i = 0; i < 5; i++) {
            double tw = (w - 2 * pad - 4 * Theme.px(6)) / 5.0;
            g.fill(new RoundRectangle2D.Double(pad + i * (tw + Theme.px(6)),
                pad + Theme.px(22), tw, Theme.px(28), Theme.R_TILE * 2, Theme.R_TILE * 2));
        }
    }

    /**
     * ЖЕТОН ПОБЕДНОГО ОЧКА — деревянная ЗОЛОТАЯ ПЯТИКОНЕЧНАЯ ЗВЕЗДА. Рисуется
     * как есть, а не буквой «★» из шрифта: звезда должна выглядеть жетоном со
     * стола, и её форма не должна зависеть от того, какой шрифт нашёлся в
     * системе.
     *
     * @param cx центр звезды, {@code r} — радиус описанной окружности
     */
    private static void paintStar(java.awt.Graphics2D g, double cx, double cy, double r) {
        java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
        for (int i = 0; i < 10; i++) {
            // Луч и впадина чередуются; впадина — 0,42 радиуса: при более
            // глубокой звезда на девяти пикселях рассыпается в иголки.
            double rr = i % 2 == 0 ? r : r * 0.42;
            double a = Math.toRadians(-90 + i * 36);
            double x = cx + rr * Math.cos(a);
            double y = cy + rr * Math.sin(a);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        g.setColor(new java.awt.Color(0xE8, 0xB3, 0x2A));
        g.fill(path);
        g.setColor(new java.awt.Color(0x8A, 0x63, 0x08));
        g.setStroke(new java.awt.BasicStroke(1f));
        g.draw(path);
    }

    /** Размер значка ресурса в плитке: прежние 11 пикселей плюс половина. */
    private static int iconSize() {
        return Theme.px(17);
    }

    private void paintTile(Graphics2D g, Tile t, Rectangle r) {
        final double ICON_SIZE = iconSize();
        final int ICON_CX = Theme.px(12);
        final int NUM_X = Theme.px(23);
        boolean over = t.key().equals(hot);
        g.setColor(over ? Theme.hover() : Theme.tile());
        g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height,
            Theme.R_TILE * 2, Theme.R_TILE * 2));
        // ЗНАЧОК РЕСУРСА КРУПНЕЕ НА ПОЛОВИНУ (просьба дизайнера 16.08.2026):
        // 11 пикселей мелковато — ресурс в плитке узнают по значку, а не по
        // числу. Центр значка и левый край числа сдвинуты вместе с ним, иначе
        // разросшийся значок налезает на цифру.
        MarkIcons.paint(g, t.icon(), r.x + ICON_CX, r.y + r.height / 2.0,
            ICON_SIZE, t.colour());

        g.setFont(num(19));
        String v = String.valueOf(t.value());
        g.setColor(Theme.ink());
        g.drawString(v, r.x + NUM_X, r.y + r.height - Theme.px(8));
        if (t.cap() >= 0) {
            g.setFont(f(10, Font.PLAIN));
            g.setColor(Theme.ink3());
            g.drawString("/" + t.cap(),
                r.x + NUM_X + g.getFontMetrics(num(19)).stringWidth(v) + 1,
                r.y + r.height - Theme.px(8));
        }
        int d = delta(t.key());
        if (d != 0) {
            badge(g, d, r.x + r.width - Theme.px(26), r.y + Theme.px(2));
        }
    }

    /** Бейдж изменения: зелёный треугольник вверх, красный вниз, и число. */
    private void badge(Graphics2D g, int d, int x, int y) {
        String s = Names.delta(d);
        g.setFont(f(10, Font.BOLD));
        int w = g.getFontMetrics().stringWidth(s) + Theme.px(14);
        int h = Theme.px(13);
        Color c = d > 0 ? Theme.good() : Theme.bad();
        g.setColor(Theme.alpha(c, 0.20));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        MarkIcons.paint(g, d > 0 ? "UP" : "DOWN", x + Theme.px(6), y + h / 2.0,
            Theme.px(7), c);
        g.setColor(c);
        g.drawString(s, x + Theme.px(11), y + h - Theme.px(3));
    }

    /**
     * ОДИН ПОКАЗАТЕЛЬ НИЖНЕЙ СВОДКИ: своя ширина и умение нарисоваться в точке.
     *
     * <p>Раньше строки складывались «по месту»: каждый следующий значок ставился
     * от предыдущего плюс фиксированный отступ. Пока полоса широкая, это работало,
     * а на четырёх игроков в узком окне хвост строки просто уезжал за край и
     * обрезался — числа было не прочесть (замечание дизайнера 15.08.2026).
     */
    private interface Item {
        /** Своя ширина показателя без отступов. */
        int width(Graphics2D g);

        /** Нарисовать в точке (x, y) — левый верхний угол своей клетки. */
        void draw(Graphics2D g, int x, int y);

        /** Ключ для подсказки (null — подсказки нет). */
        String key();
    }

    /**
     * РАЗЛОЖИТЬ РЯД ПОКАЗАТЕЛЕЙ ПО ШИРИНЕ. Сначала считаются собственные ширины,
     * потом остаток делится на отступы: просторно — держим полный отступ, тесно —
     * ужимаем до минимума. Что и после этого не влезло, переносится в следующий
     * ряд, а не рисуется за краем.
     *
     * @return сколько показателей уместилось
     */
    private int layoutRow(Graphics2D g, java.util.List<Item> items, int x, int y,
                          int w, int lineH) {
        int minGap = Theme.px(10);
        int maxGap = Theme.px(26);
        int fit = 0;
        int natural = 0;
        for (Item it : items) {
            int iw = it.width(g);
            int need = natural + iw + (fit == 0 ? 0 : minGap);
            if (fit > 0 && need > w) {
                break;
            }
            natural += iw + (fit == 0 ? 0 : minGap);
            fit++;
        }
        if (fit == 0) {
            return 0;
        }
        int own = 0;
        for (int i = 0; i < fit; i++) {
            own += items.get(i).width(g);
        }
        int gaps = fit - 1;
        int gap = gaps == 0 ? 0
            : Math.max(minGap, Math.min(maxGap, (w - own) / gaps));
        int cx = x;
        for (int i = 0; i < fit; i++) {
            Item it = items.get(i);
            int iw = it.width(g);
            it.draw(g, cx, y);
            if (it.key() != null) {
                detailSpots.put(it.key(),
                    new Rectangle(cx - Theme.px(4), y, iw + Theme.px(8), lineH));
            }
            cx += iw + gap;
        }
        return fit;
    }

    /** Показатель «значок + число»: они читаются как одно целое и стоят вплотную. */
    private Item markItem(String key, String icon, String value, Color colour) {
        int tie = Theme.px(9);
        int mark = Theme.px(15);
        return new Item() {
            @Override public int width(Graphics2D g) {
                return mark + tie - Theme.px(6) + g.getFontMetrics().stringWidth(value);
            }

            @Override public void draw(Graphics2D g, int x, int y) {
                MarkIcons.paint(g, icon, x, y + Theme.px(8), mark, colour);
                g.setColor(colour);
                g.drawString(value, x + tie, y + Theme.px(12));
            }

            @Override public String key() {
                return key;
            }
        };
    }

    private void paintDetails(Graphics2D g, ReplayRecord.Player p, ReplayRecord.Frame f,
                              int x, int y, int w) {
        g.setFont(f(12, Font.PLAIN));
        int lineH = Theme.px(18);

        detailSpots.clear();

        int[] b = buildings(f, p.seat);
        int[] u = units(f, p.seat);
        int played = p.orderPlayed.size();

        // ВСЕ ПОКАЗАТЕЛИ ОДНИМ СПИСКОМ, в порядке важности. Раскладывает их
        // layoutRow: что не влезло в первый ряд — уходит во второй, что не влезло
        // и туда — не рисуется вовсе, а не вылезает за край полосы.
        java.util.List<Item> items = new ArrayList<>();
        items.add(techItem(p));
        items.add(markItem("buildings", "BUILDING", b[0] + "·" + b[1] + "·" + b[2],
            Theme.ink2()));
        items.add(markItem("units", "UNIT", u[0] + "/" + u[1], Theme.ink2()));
        items.add(markItem("objectives", "CARD",
            String.valueOf(p.objectiveHand.size()), Theme.ink3()));
        items.add(markItem("arsenal", "ARSENAL",
            p.arsenalHand.size() + "+" + p.arsenalInstalled.size(), Theme.ink3()));
        items.add(ordersItem(played));
        if (p.superObjective != null) {
            items.add(markItem("super", "SUPER", String.valueOf(p.superProgress),
                p.superComplete ? Theme.points() : Theme.ink3()));
        }

        int done = layoutRow(g, items, x, y, w, lineH);
        // Второй ряд — только если высота полосы его позволяет.
        if (done < items.size() && getHeight() >= Theme.px(Theme.H_STRIP)) {
            layoutRow(g, items.subList(done, items.size()), x, y + lineH, w, lineH);
        }
    }

    /** Наука: подпись и три микро-шкалы цветами треков. */
    private Item techItem(ReplayRecord.Player p) {
        String[] tracks = {"left", "middle", "right"};
        Color[] colours = {new Color(0xC0392B), new Color(0x278B3E), new Color(0x2C62A8)};
        return new Item() {
            @Override public int width(Graphics2D g) {
                return g.getFontMetrics().stringWidth("наука") + Theme.px(6)
                    + 3 * Theme.px(38) - Theme.px(8);
            }

            @Override public void draw(Graphics2D g, int x, int y) {
                g.setColor(Theme.ink3());
                g.drawString("наука", x, y + Theme.px(10));
                int sx = x + g.getFontMetrics().stringWidth("наука") + Theme.px(6);
                for (int i = 0; i < 3; i++) {
                    int steps = p.tech.getOrDefault(tracks[i], 0);
                    for (int k = 0; k < 4; k++) {
                        g.setColor(k < steps ? colours[i]
                            : Theme.alpha(Theme.ink3(), 0.35));
                        g.fillRect(sx + k * Theme.px(8), y + Theme.px(2), Theme.px(6),
                            Theme.px(12));
                    }
                    sx += Theme.px(38);
                }
            }

            @Override public String key() {
                return "tech";
            }
        };
    }

    /** Приказы круга: четыре кружка, залитые — сыгранные. */
    private Item ordersItem(int played) {
        return new Item() {
            @Override public int width(Graphics2D g) {
                return 3 * Theme.px(14) + Theme.px(11);
            }

            @Override public void draw(Graphics2D g, int x, int y) {
                for (int i = 0; i < 4; i++) {
                    MarkIcons.paint(g, i < played ? "ORDER_DONE" : "ORDER_LEFT",
                        x + i * Theme.px(14), y + Theme.px(8), Theme.px(11),
                        i < played ? Theme.seatInk(seat) : Theme.ink3());
                }
            }

            @Override public String key() {
                return "orders";
            }
        };
    }

    /** Спарклайн победных очков по раундам — еле заметно, фоном. */
    private void paintSparkline(Graphics2D g, int w, int h) {
        int[] series = session.vpByRound().length > seat ? session.vpByRound()[seat] : null;
        if (series == null || series.length < 2) {
            return;
        }
        int max = 1;
        for (int v : series) {
            max = Math.max(max, v);
        }
        Path2D p = new Path2D.Double();
        double x0 = Theme.px(8);
        double x1 = w - Theme.px(8);
        double y0 = h - Theme.px(4);
        double hh = Theme.px(22);
        for (int i = 0; i < series.length; i++) {
            double px = x0 + (x1 - x0) * i / (double) (series.length - 1);
            double py = y0 - hh * series[i] / max;
            if (i == 0) {
                p.moveTo(px, py);
            } else {
                p.lineTo(px, py);
            }
        }
        p.lineTo(x1, y0);
        p.lineTo(x0, y0);
        p.closePath();
        g.setColor(Theme.alpha(Theme.points(), 0.10));
        g.fill(p);
    }

    // ==================== счёт ====================
    private ReplayRecord.Player player(ReplayRecord.Frame f) {
        if (f == null || f.snapshot == null || seat >= f.snapshot.players.size()) {
            return null;
        }
        return f.snapshot.players.get(seat);
    }

    /** Изменение показателя за последние {@link #DELTA_WINDOW} шагов. */
    private int delta(String key) {
        ReplayRecord.Player now = player(session.frame());
        ReplayRecord.Player was = player(session.frame(session.cursor() - DELTA_WINDOW));
        if (now == null || was == null || session.cursor() < 1) {
            return 0;
        }
        return value(now, key) - value(was, key);
    }

    private static int value(ReplayRecord.Player p, String key) {
        return switch (key) {
            case "coin" -> p.coin;
            case "kelium" -> p.kelium;
            case "ammo" -> p.ammo;
            case "debris" -> p.debris;
            case "containers" -> p.containers;
            case "vp" -> p.vp.getOrDefault("total", 0);
            default -> 0;
        };
    }

    /** Здания по смыслу: ЦУ, добытчики+станции, боевые. */
    private int[] buildings(ReplayRecord.Frame f, int owner) {
        int cu = 0;
        int eco = 0;
        int mil = 0;
        if (f != null && f.snapshot != null) {
            for (ReplayRecord.Tok t : f.snapshot.tokens) {
                if (!t.building || t.owner != owner || !t.alive || t.hexId == null) {
                    continue;
                }
                String code = Names.buildingCode(t.type);
                if ("ЦУ".equals(code)) {
                    cu++;
                } else if ("Д".equals(code) || "Э".equals(code)) {
                    eco++;
                } else {
                    mil++;
                }
            }
        }
        return new int[]{cu, eco, mil};
    }

    /** Войска: на поле и в резерве. */
    private int[] units(ReplayRecord.Frame f, int owner) {
        int field = 0;
        int reserve = 0;
        if (f != null && f.snapshot != null) {
            for (ReplayRecord.Tok t : f.snapshot.tokens) {
                if (t.building || t.owner != owner || !t.alive) {
                    continue;
                }
                if (t.hexId != null) {
                    field++;
                } else {
                    reserve++;
                }
            }
        }
        return new int[]{field, reserve};
    }

    // ==================== подсказки ====================
    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        ReplayRecord.Frame f = session.frame();
        ReplayRecord.Player p = player(f);
        if (p == null) {
            return null;
        }
        if (boardButton().contains(e.getPoint())) {
            return Ui2.tip("Открыть ПЛАНШЕТ игрока отдельным окном: здания, войска в "
                + "запасе, хранилище, модули, арсенал, отложенный приказ и карта "
                + "трофеев.");
        }
        String key = keyAt(e.getPoint());
        if (key != null) {
            for (Tile t : tiles(p)) {
                if (t.key().equals(key)) {
                    return Ui2.tip(t.tip() + "\n\nЩелчок — как этот показатель менялся "
                        + "за партию.");
                }
            }
        }
        // ПОДСКАЗКА НА КАЖДЫЙ ПОКАЗАТЕЛЬ ОТДЕЛЬНО. Раньше на всю нижнюю сводку
        // выдавалась одна общая простыня со всеми девятью строками: чтобы узнать,
        // что значит один значок, приходилось читать легенду целиком (замечание
        // дизайнера 13.08.2026). Теперь каждый значок со своим числом отвечает сам
        // за себя, а общая легенда осталась на пустом месте строки.
        for (Map.Entry<String, Rectangle> en : detailSpots.entrySet()) {
            if (en.getValue().contains(e.getPoint())) {
                return Ui2.tip(detailTip(en.getKey(), p, f));
            }
        }
        // ОБЩЕЙ ПРОСТЫНИ БОЛЬШЕ НЕТ (решение дизайнера 13.08.2026): на пустом месте
        // сводки подсказка не выскакивает вовсе, чтобы не перекрывать соседей.
        // Полная легенда осталась в справочнике — она собирается из LEGEND.
        if (e.getY() >= detailsTop) {
            return null;
        }
        // подсказка по полосе целиком — здесь и живёт полный перечень с гексами
        StringBuilder sb = new StringBuilder();
        sb.append(session.record().playerName(seat)).append(", сторона ").append(p.side);
        sb.append("\n\nЗдания: ").append(describeBuildings(f, seat));
        sb.append("\nВойска: ").append(describeUnits(f, seat));
        sb.append("\nСклад: занято ").append(p.kelium + p.ammo).append(" из ")
          .append(p.storeCap).append(" ячеек");
        if (!p.objectiveHand.isEmpty()) {
            sb.append("\nЗадания в руке: ").append(cards(p.objectiveHand));
        }
        if (!p.arsenalInstalled.isEmpty()) {
            sb.append("\nАрсенал установлен: ").append(cards(p.arsenalInstalled));
        }
        if (p.mandateArsenalCard != null) {
            sb.append("\nПод Мандатом совета (sa8): карта ").append(p.mandateArsenalCard);
        } else if (p.mandateContainers > 0) {
            sb.append("\nПод Мандатом совета (sa8): ").append(p.mandateContainers)
              .append(" мест[а] под контейнеры");
        }
        sb.append("\nОчки: ").append(describeVp(p));
        sb.append("\n\nЩелчок — подробный планшет игрока.");
        return Ui2.tip(sb.toString());
    }

    /** Где начинаются мелкие строки сводки — считается при отрисовке. */
    private int detailsTop = Integer.MAX_VALUE;

    /** Место каждого показателя нижней сводки — под свою подсказку. */
    private final Map<String, Rectangle> detailSpots = new LinkedHashMap<>();

    /**
     * Подсказка ОДНОГО показателя сводки: что это, что значит и сколько сейчас.
     * Текст объяснения берётся из общей легенды — расходиться им нельзя.
     */
    private String detailTip(String key, ReplayRecord.Player p, ReplayRecord.Frame f) {
        String what = "";
        for (String[] row : LEGEND) {
            if (row[0].equals(key)) {
                what = row[2];
                break;
            }
        }
        int[] b = buildings(f, seat);
        int[] u = units(f, seat);
        String head = switch (key) {
            case "tech" -> "НАУКА";
            case "buildings" -> "ЗДАНИЯ НА ПОЛЕ";
            case "units" -> "ВОЙСКА";
            case "objectives" -> "ЗАДАНИЯ В РУКЕ";
            case "arsenal" -> "АРСЕНАЛ";
            case "orders" -> "ПРИКАЗЫ КРУГА";
            case "super" -> "СУПЕР-ЗАДАНИЕ";
            default -> key;
        };
        String now = switch (key) {
            case "tech" -> "Сейчас: красный " + p.tech.getOrDefault("left", 0)
                + ", зелёный " + p.tech.getOrDefault("middle", 0)
                + ", синий " + p.tech.getOrDefault("right", 0) + " из 4.";
            case "buildings" -> "Сейчас: ЦУ " + b[0] + ", хозяйственных " + b[1]
                + ", военных " + b[2] + ".";
            case "units" -> "Сейчас: на поле " + u[0] + ", в запасе " + u[1] + ".";
            case "objectives" -> "Сейчас в руке: " + p.objectiveHand.size() + ".";
            case "arsenal" -> "Сейчас: в руке " + p.arsenalHand.size()
                + ", установлено " + p.arsenalInstalled.size() + "."
                + (p.mandateArsenalCard != null
                    ? " Под Мандатом (sa8): " + p.mandateArsenalCard + "."
                    : p.mandateContainers > 0
                        ? " Под Мандатом (sa8): " + p.mandateContainers + " места под контейнеры."
                        : "");
            case "orders" -> "Сейчас сыграно " + p.orderPlayed.size() + " из 4.";
            case "super" -> p.superComplete ? "Задание ЗАКРЫТО — потому значок золотой."
                : "Продвижение: " + p.superProgress + ".";
            default -> "";
        };
        return head + "\n\n" + what + (now.isEmpty() ? "" : "\n\n" + now);
    }

    /**
     * ЛЕГЕНДА — ЕДИНЫЙ ТЕКСТ. Одна и та же расшифровка нужна в двух местах:
     * подсказкой над самой полосой и статьёй «Полоса игрока» в справочнике
     * ({@link HelpBook}). Держать её в двух видах нельзя — разойдутся, и одна из
     * них начнёт врать. Поэтому строки лежат здесь, а живые числа игрока
     * подставляются в {@link #legend}.
     *
     * <p>Столбцы: опознавательное имя строки · как она выглядит (с примерными
     * числами, как в легенде дизайнера) · что означает.
     */
    private static final String[][] LEGEND = {
        // ЗНАКОВ-КАРТИНОК В ТЕКСТЕ НЕТ. Квадратики и стрелки писались символами
        // Юникода, а в шрифте интерфейса их нет — в подсказке они выходили
        // перечёркнутыми прямоугольниками (замечание дизайнера 13.08.2026).
        // Поэтому всё, что нарисовано, описано СЛОВАМИ.
        {"tech", "наука · три шкалы по четыре деления",
            "три трека науки: красный, зелёный, синий. Закрашенных делений "
            + "столько, сколько шагов пройдено (из четырёх)."},
        {"buildings", "значок дома 1·5·0",
            "здания НА ПОЛЕ через точку: ЦУ · хозяйственные (добытчики и "
            + "энергостанции) · военные (казармы, заводы, авиабазы)."},
        {"units", "значок войска 1/0", "войска на поле / в запасе."},
        {"objectives", "значок карты 1", "заданий в руке."},
        {"arsenal", "значок арсенала 0+1",
            "карт арсенала в руке + уже установлено на планшет."},
        {"orders", "четыре кружка",
            "приказы этого круга: закрашенные сыграны, пустые ещё в руке."},
        {"super", "звезда 2",
            "продвижение по супер-заданию; золотая — задание закрыто."},
        {"wash", "бледная заливка под всей полосой",
            "победные очки по раундам: видно, кто разгоняется."},
        {"delta", "стрелка вверх или вниз с числом у плитки",
            "насколько показатель изменился на этом шаге."},
    };

    /** Легенда мелких строк для справочника: пары «как выглядит → что означает». */
    static List<String[]> legendRows() {
        List<String[]> out = new ArrayList<>();
        for (String[] r : LEGEND) {
            out.add(new String[]{r[1], r[2]});
        }
        return out;
    }

    private String cards(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            out.add("«" + Names.card(session.record(), id) + "»");
        }
        return String.join(", ", out);
    }

    private String describeBuildings(ReplayRecord.Frame f, int owner) {
        List<String> out = new ArrayList<>();
        if (f != null && f.snapshot != null) {
            for (ReplayRecord.Tok t : f.snapshot.tokens) {
                if (!t.building || t.owner != owner || t.hexId == null || !t.alive) {
                    continue;
                }
                StringBuilder sb = new StringBuilder(Names.building(t.type));
                if (t.level != null) {
                    sb.append(" №").append(t.level);
                }
                sb.append(" на ").append(t.hexId);
                if (t.damage > 0) {
                    sb.append(" (урон ").append(t.damage).append(')');
                }
                if (t.energySlots > 0) {
                    sb.append(t.energyPlaced >= t.energySlots ? " [запитан]" : " [без энергии]");
                }
                out.add(sb.toString());
            }
        }
        return out.isEmpty() ? "на поле пусто" : String.join(", ", out);
    }

    private String describeUnits(ReplayRecord.Frame f, int owner) {
        Map<String, Integer> field = new LinkedHashMap<>();
        Map<String, Integer> reserve = new LinkedHashMap<>();
        if (f != null && f.snapshot != null) {
            for (ReplayRecord.Tok t : f.snapshot.tokens) {
                if (t.building || t.owner != owner || !t.alive) {
                    continue;
                }
                (t.hexId != null ? field : reserve).merge(Names.unit(t.type), 1, Integer::sum);
            }
        }
        return "на поле — " + join(field) + "; в резерве — " + join(reserve);
    }

    private static String join(Map<String, Integer> m) {
        if (m.isEmpty()) {
            return "никого";
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            out.add(e.getKey() + " " + e.getValue());
        }
        return String.join(", ", out);
    }

    private static String describeVp(ReplayRecord.Player p) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : p.vp.entrySet()) {
            if ("total".equals(e.getKey()) || e.getValue() == 0) {
                continue;
            }
            parts.add(Names.vp(e.getKey()) + " " + e.getValue());
        }
        return p.vp.getOrDefault("total", 0)
            + (parts.isEmpty() ? "" : " = " + String.join(" + ", parts));
    }

    /** Кнопка «планшет» в правом верхнем углу полосы. */
    /** Подпись на кнопке личной зоны — она же в подсказке. */
    private static final String BOARD_LABEL = "Личная зона";
    /** Место кнопки считается при отрисовке: оно зависит от ширины плашки игрока. */
    private int boardX = -1;
    private int boardY;
    private int boardW;

    private Rectangle boardButton() {
        if (boardX < 0) {
            return new Rectangle(-100, -100, 0, 0);   // ещё не рисовали
        }
        return new Rectangle(boardX, boardY, boardW, Theme.px(18));
    }

    /** Ключ плитки под точкой (или null, если щёлкнули мимо плиток). */
    private String keyAt(Point p) {
        for (Map.Entry<String, Rectangle> e : tileBounds.entrySet()) {
            if (e.getValue().contains(p)) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * СТОРОНЫ ОБОИХ ПЛАНШЕТОВ ОДНОЙ СТРОКОЙ (без колоды — ту рисуют отдельно,
     * с цветной точкой, см. {@link #paintComponent}).
     *
     * <p>Ничего не выдумывает: чего в записи нет — того в строке нет. Старые
     * записи не несли стороны хранилища, и на них строка честно короче.
     */
    private static String setupLineBoards(ReplayRecord.Player p) {
        java.util.List<String> bits = new java.util.ArrayList<>();
        if (p.side != null && !p.side.isBlank()) {
            bits.add("войска " + p.side);
        }
        if (p.storageSide != null && !p.storageSide.isBlank()) {
            bits.add("склад " + p.storageSide);
        }
        return String.join(" · ", bits);
    }

    /**
     * ШРИФТ ПОЛОСЫ — общий {@link Theme#font} с надбавкой {@link Theme#STRIP_TEXT}.
     * Все кегли здесь идут через него, поэтому надбавка правится одним числом и
     * соотношения внутри полосы не разъезжаются.
     */
    private static java.awt.Font f(double size, int style) {
        return Theme.font(size * Theme.STRIP_TEXT, style);
    }

    /** То же для чисел: широкое полужирное начертание. */
    private static java.awt.Font num(double size) {
        return Theme.mono(size * Theme.STRIP_TEXT, java.awt.Font.BOLD);
    }

    private static String clip(Graphics2D g, String s, int width) {
        if (s == null) {
            return "";
        }
        if (g.getFontMetrics().stringWidth(s) <= width) {
            return s;
        }
        int n = s.length();
        while (n > 1 && g.getFontMetrics().stringWidth(s.substring(0, n) + "…") > width) {
            n--;
        }
        return s.substring(0, n) + "…";
    }
}
