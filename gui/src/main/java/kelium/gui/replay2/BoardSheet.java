package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

import kelium.report.FieldGeometry;
import kelium.report.ReplayRecord;

/**
 * BoardSheet — ПЛАНШЕТ ИГРОКА ЦЕЛИКОМ, как он лежит на столе.
 *
 * <p>Заказ дизайнера 13.08.2026: видеть всё его хозяйство разом — какие здания
 * построены, какие ещё в запасе, а какие уехали к кому-то в трофеи; сколько войск
 * осталось в запасе по родам; модули и какой стороной они лежат; арсенал и
 * контейнеры; планшет хранилища с открытыми ячейками; отложенную рубашкой карту
 * приказа; и карту трофеев, на которой вразнобой валяются снесённые жетоны с
 * напечатанной ценностью и чёрные кубики — сколько обломков даст возврат.
 *
 * <p>Здания рисуются ТЕМИ ЖЕ силуэтами, что на поле ({@link FieldGeometry}), —
 * так планшет и поле читаются как одна игра, а не как две разные программы.
 * Отсутствующее показано пунктиром: подпись говорит, где оно (в запасе или в
 * трофеях).
 */
public final class BoardSheet extends JComponent {

    private static final long serialVersionUID = 1L;

    private final Session session;
    private int seat;

    public BoardSheet(Session session, int seat) {
        this.session = session;
        this.seat = seat;
        setOpaque(true);
        setFont(Theme.body());
        session.whenFrameChanged(s -> repaint());
        session.whenRecordChanged(s -> repaint());
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
        // ЩЕЛЧОК ПО СТОПКЕ КАРТ открывает читалку: список карт и разворот выбранной
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                Rectangle was = hotDeck;
                hotDeck = deckAt(e.getPoint());
                setCursor(hotDeck == null ? java.awt.Cursor.getDefaultCursor()
                    : java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                if (was != hotDeck) {
                    repaint();
                }
            }
        });
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                Rectangle r = deckAt(e.getPoint());
                Deck d = r == null ? null : deckSpots.get(r);
                if (d != null) {
                    CardReader.show(javax.swing.SwingUtilities.getWindowAncestor(BoardSheet.this),
                        session.content(), d.kind(),
                        session.record().playerName(seat) + " · " + d.title(),
                        d.ids(), id -> Names.card(session.record(), id));
                }
            }
        });
    }

    /** Кнопка стопки под точкой (или null). */
    private Rectangle deckAt(java.awt.Point p) {
        for (Rectangle r : deckSpots.keySet()) {
            if (r.contains(p)) {
                return r;
            }
        }
        return null;
    }

    public void setSeat(int seat) {
        this.seat = seat;
        repaint();
    }

    public int seat() {
        return seat;
    }

    // ==================== МАСШТАБ ПЛАНШЕТА ====================
    //
    // ПЛАНШЕТ КРУПНЕЕ ОСТАЛЬНОГО ПРИЛОЖЕНИЯ (просьба дизайнера 13.08.2026): его
    // разглядывают подолгу, разбирая партию, а не скользят по нему взглядом.
    // Текст и размеры растут ПО-РАЗНОМУ: подписи набраны мелко и им нужен
    // заметный рост, а жетоны и ячейки читались и так — им хватает четверти.
    // Все размеры в этом классе идут через px() и font(), поэтому масштаб
    // меняется двумя числами, а не полусотней правок по месту.

    private static final double TEXT_K = 1.7;
    private static final double ELEM_K = 1.25;

    /** Размер элемента: та же единица, что во всём приложении, но крупнее. */
    private static int px(int v) {
        return (int) Math.round(Theme.px(v) * ELEM_K);
    }

    private static Font font(int size, int style) {
        return Theme.font((int) Math.round(size * TEXT_K), style);
    }

    private static Font mono(int size, int style) {
        return Theme.mono((int) Math.round(size * TEXT_K), style);
    }

    /** Сколько места заняло содержимое при последней отрисовке. */
    private int contentH;

    @Override
    public Dimension getPreferredSize() {
        // Высота — ПО СОДЕРЖИМОМУ, иначе прокрутка не дотягивается до низа: планшет
        // растёт с числом зданий и карт (замечание дизайнера 13.08.2026).
        return new Dimension(px(980), Math.max(px(560), contentH));
    }

    // ==================== отрисовка ====================
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.bg());
        g.fillRect(0, 0, getWidth(), getHeight());

        ReplayRecord.Frame f = session.frame();
        if (f == null || f.snapshot == null || seat >= f.snapshot.players.size()) {
            g.setFont(Theme.body());
            g.setColor(Theme.ink3());
            g.drawString("планшет появится, когда будет запись", px(16), px(28));
            g.dispose();
            return;
        }
        ReplayRecord.Player p = f.snapshot.players.get(seat);
        ReplayRecord rec = session.record();

        int pad = px(14);
        int y = pad;

        // ---- шапка: плашка места и очки
        String name = rec.playerName(seat);
        java.awt.Font chipFont = font(13, Font.BOLD);
        int chipW = SeatChip.widthFor(g, name, chipFont);
        SeatChip.paintChip(g, seat, name, chipFont, pad, y, chipW, px(22), true);
        g.setFont(Theme.numberBig());
        g.setColor(Theme.points());
        String vp = p.vp.getOrDefault("total", 0) + " ПО";
        g.drawString(vp, getWidth() - pad - g.getFontMetrics().stringWidth(vp),
            y + px(20));
        y += px(34);

        // ---- ДВА СТОЛБЦА ОТ САМОГО ВЕРХА. Здания узкие (четыре места в ряд), и
        // если растянуть их на всю ширину, справа сверху остаётся пустое поле, а
        // всё остальное уезжает вниз и наезжает друг на друга (замечание дизайнера
        // 13.08.2026). СЛЕВА — здания и склад: ячейки склада открываются зданиями и
        // нарисованы прямо под ними, так что это одна тема и делить её между
        // столбцами нельзя. СПРАВА — всё остальное: войска, модули, карты, трофеи.
        int colGap = px(18);
        int leftW = px(330);
        int rightX = pad + leftW + colGap;
        int rightW = getWidth() - pad - rightX;

        int yLeft = paintBuildings(g, f, p, pad, y, leftW);
        yLeft = paintStorage(g, p, pad, yLeft + px(10), leftW);

        int yRight = paintTroops(g, f, p, rightX, y, rightW);
        yRight = paintModulesAndCards(g, p, rightX, yRight + px(10), rightW);
        int cardH = px(180);
        int cardW = px(150);
        yRight += px(6);
        paintSetAside(g, p, rightX, yRight, cardW, cardH);
        paintTrophyCard(g, p, rightX + cardW + colGap, yRight,
            rightW - cardW - colGap, cardH);
        yRight += cardH + px(24);

        // ВЫСОТА СЧИТАЕТСЯ ПО СОДЕРЖИМОМУ: планшет выше окна, и без этого прокрутка
        // не доходила до низа — нижняя часть просто обрезалась.
        int need = Math.max(yLeft, yRight) + pad;
        if (need != contentH) {
            contentH = need;
            javax.swing.SwingUtilities.invokeLater(this::revalidate);
        }
        g.dispose();
    }

    /**
     * ЗДАНИЯ ГРУППАМИ, КАК НА ПЛАНШЕТЕ. Раньше все жетоны шли одной кучей в
     * произвольном порядке, а снесённых не было видно вовсе (замечание дизайнера
     * 13.08.2026). Теперь три группы — военные, добытчики, энергостанции; в каждой
     * ВСЕ здания игрока, включая те, что снесли: их место остаётся пунктиром.
     *
     * <p>Под добытчиками и энергостанциями нарисованы ЯЧЕЙКИ СКЛАДА, которые это
     * здание открывает. Пока здание не построено, оно лежит на планшете и закрывает
     * их собой; построил — ячейки открылись и в них видно, что лежит. Ровно так это
     * работает на столе.
     */
    private int paintBuildings(Graphics2D g, ReplayRecord.Frame f, ReplayRecord.Player p,
                               int x, int y, int w) {
        buildingSpots.clear();
        moduleSpots.clear();
        storeTokenSpots.clear();
        List<ReplayRecord.Tok> all = buildingsOf(f, p.seat);
        int cell = px(64);
        int gap = px(8);
        planCells(p, all);

        y = paintGroup(g, p, "ВОЕННЫЕ ЗДАНИЯ", roster(all,
            slot("command_center", null), slot("barracks", null),
            slot("factory", null), slot("airbase", null)), x, y, w, cell, gap,
            Under.BLUE_MODULE);
        y = paintGroup(g, p, "ДОБЫТЧИКИ", roster(all,
            slot("miner", 1), slot("miner", 2), slot("miner", 3), slot("miner", 4)),
            x, y, w, cell, gap, Under.STORAGE_CELLS);
        y = paintGroup(g, p, "ЭНЕРГОСТАНЦИИ", roster(all,
            slot("power_plant", 1), slot("power_plant", 2), slot("power_plant", 3),
            slot("power_plant", 4)), x, y, w, cell, gap, Under.STORAGE_CELLS);
        return y;
    }

    /**
     * МЕСТО ПОД ЗДАНИЕ на планшете: какое здание сюда кладётся и что с ним сейчас.
     * {@code token} — жетон из записи; {@code null} значит, что здание ни разу не
     * строили, и оно спокойно лежит в личном запасе.
     */
    private record Slot(String type, Integer level, ReplayRecord.Tok token) {
        /** Лежит ли жетон в личном запасе игрока. */
        boolean inStock() {
            return token == null
                || (token.hexId == null && token.alive && token.capturedBy == null);
        }
    }

    private static Slot slot(String type, Integer level) {
        return new Slot(type, level, null);
    }

    /**
     * ПЕЧАТНЫЙ СОСТАВ ЗДАНИЙ игрока, а не «что нашлось в записи». Места рисуются
     * ВСЕ и всегда: у каждого игрока по печатному набору — ЦУ, казарма, завод,
     * авиабаза и по четыре добытчика и энергостанции (замечание дизайнера
     * 13.08.2026: раньше здание, которое ни разу не строили, на планшете просто
     * отсутствовало, и место под него пропадало).
     */
    private static List<Slot> roster(List<ReplayRecord.Tok> all, Slot... printed) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : printed) {
            ReplayRecord.Tok found = null;
            for (ReplayRecord.Tok t : all) {
                boolean sameLevel = s.level() == null
                    ? t.level == null : s.level().equals(t.level);
                if (s.type().equals(t.type) && sameLevel) {
                    found = t;
                    break;
                }
            }
            out.add(new Slot(s.type(), s.level(), found));
        }
        return out;
    }

    /** Что рисуется полосой ПОД жетоном здания в этой группе. */
    private enum Under {
        /** Ничего: между жетоном и подписью пусто. */
        NOTHING,
        /** Ячейки склада, которые открывает это здание. */
        STORAGE_CELLS,
        /** Место под синий модуль сборки — только у военных зданий. */
        BLUE_MODULE
    }

    /** Одна группа зданий с подписью; {@code under} — что идёт полосой под жетоном. */
    private int paintGroup(Graphics2D g, ReplayRecord.Player p, String title,
                           List<Slot> list, int x, int y, int w,
                           int cell, int gap, Under under) {
        if (list.isEmpty()) {
            return y;
        }
        caption(g, title, x, y);
        y += px(12);
        int nameH = px(30);
        int cellsH = under == Under.NOTHING ? 0 : px(24);
        int rowH = cell + nameH + cellsH + px(12);
        int cx = x;
        for (Slot s : list) {
            if (cx + cell > x + w) {
                cx = x;
                y += rowH;
            }
            paintBuildingCell(g, s, p.seat, cx, y, cell);
            buildingSpots.put(new java.awt.Rectangle(cx, y, cell, cell), s);
            if (under == Under.STORAGE_CELLS) {
                paintFreedCells(g, p, s, cx, y + cell + px(3), cell);
            } else if (under == Under.BLUE_MODULE) {
                paintBlueSlot(g, p, s, cx, y + cell + px(3), cell);
            }
            paintBuildingName(g, s, cx, y + cell + cellsH + px(13), cell);
            cx += cell + gap;
        }
        return y + rowH + px(6);
    }

    /**
     * МЕСТО ПОД СИНИЙ МОДУЛЬ У ВОЕННОГО ЗДАНИЯ. Синий накрывает зону сборки, а
     * зона сборки есть у каждого военного здания — поэтому место рисуется у всех
     * четырёх, даже если здание ни разу не строили: на столе оно тоже напечатано
     * и просто пустует.
     */
    private void paintBlueSlot(Graphics2D g, ReplayRecord.Player p, Slot s,
                               int x, int y, int width) {
        ReplayRecord.Module m = p.bluePlaced.get(s.type());
        int side = px(18);
        int sx = x + (width - side) / 2;
        ModuleSlot.paint(g, m, ModuleSlot.blue(), sx, y, side);
        moduleSpots.put(new Rectangle(sx, y, side, side),
            new ModuleSpot(m, false, kelium.gui.GameRecorder
                .buildingName(s.type(), s.level())));
    }

    /** Место под модуль и что на нём лежит — для подсказки под курсором. */
    private record ModuleSpot(ReplayRecord.Module module, boolean red, String slotName) {
    }

    /** Заполняется при отрисовке; читается подсказкой. */
    private final Map<Rectangle, ModuleSpot> moduleSpots = new LinkedHashMap<>();

    /** Места жетонов хранилища: прямоугольник → сторона жетона (null — пусто). */
    private final Map<Rectangle, String> storeTokenSpots = new LinkedHashMap<>();

    /**
     * ЧТО ЛЕЖИТ В КАЖДОЙ ЯЧЕЙКЕ СКЛАДА. Раньше ресурсы рисовались отдельной
     * строкой, а ячейки под зданиями стояли пустыми украшениями (замечание
     * дизайнера 13.08.2026). Теперь запас РАСКЛАДЫВАЕТСЯ по открытым ячейкам:
     * келемий сперва в свои ячейки «K», боеприпасы — в «A», остаток обоих — в
     * универсальные «U». Порядок обхода постоянный, поэтому кубики не прыгают с
     * места на место при листании партии.
     */
    private void planCells(ReplayRecord.Player p, List<ReplayRecord.Tok> all) {
        cellFill.clear();
        startFill = new char[]{0, 0};
        List<char[]> holders = new ArrayList<>();   // ссылка на массив
        List<int[]> idx = new ArrayList<>();        // индекс внутри массива
        List<Character> types = new ArrayList<>();
        // две стартовые универсальные ячейки открыты всегда
        holders.add(startFill);
        idx.add(new int[]{0});
        types.add('U');
        holders.add(startFill);
        idx.add(new int[]{1});
        types.add('U');
        for (Slot s : storageSlots(all)) {
            if (s.inStock()) {
                continue;               // жетон лежит на планшете и закрывает ячейки
            }
            String key = cellKey(s);
            String str = p.storageCells.get(key);
            if (str == null || str.isBlank()) {
                continue;
            }
            char[] arr = new char[str.length()];
            cellFill.put(key, arr);
            for (int i = 0; i < str.length(); i++) {
                holders.add(arr);
                idx.add(new int[]{i});
                types.add(str.charAt(i));
            }
        }
        int k = Math.max(0, p.kelium);
        int a = Math.max(0, p.ammo);
        for (int i = 0; i < types.size() && k > 0; i++) {
            if (types.get(i) == 'K') {
                holders.get(i)[idx.get(i)[0]] = 'K';
                k--;
            }
        }
        for (int i = 0; i < types.size() && a > 0; i++) {
            char t = types.get(i);
            if ((t == 'A' || t == 'B') && holders.get(i)[idx.get(i)[0]] == 0) {
                holders.get(i)[idx.get(i)[0]] = 'A';
                a--;
            }
        }
        for (int i = 0; i < types.size() && (k > 0 || a > 0); i++) {
            if (types.get(i) != 'U' || holders.get(i)[idx.get(i)[0]] != 0) {
                continue;
            }
            holders.get(i)[idx.get(i)[0]] = k > 0 ? 'K' : 'A';
            if (k > 0) {
                k--;
            } else {
                a--;
            }
        }
        // ОБЛОМКИ ИДУТ ПОСЛЕДНИМИ И В ЛЮБУЮ СВОБОДНУЮ ЯЧЕЙКУ. У них нет своего
        // типа ячейки: обломок занимает ровно одну любую — универсальную,
        // келемиевую или боеприпасную. Раскладываем после келемия и боеприпасов,
        // чтобы не занять именную ячейку у того, кому она предназначена.
        int d = Math.max(0, p.debris);
        for (int i = 0; i < types.size() && d > 0; i++) {
            if (holders.get(i)[idx.get(i)[0]] != 0) {
                continue;
            }
            holders.get(i)[idx.get(i)[0]] = 'D';
            d--;
        }
    }

    /** Значок ресурса, лежащего в ячейке склада: код для {@link MarkIcons}. */
    private static String cellIcon(char has) {
        return switch (has) {
            case 'K' -> "KELIUM";
            case 'D' -> "DEBRIS";
            default -> "AMMO";
        };
    }

    /** Цвет значка ресурса в ячейке склада. */
    private static Color cellIconColour(char has) {
        return switch (has) {
            case 'K' -> Theme.kelium();
            case 'D' -> Theme.debris();
            default -> Theme.ink2();
        };
    }

    /** Складские здания игрока в постоянном порядке: добытчики, потом станции. */
    private List<Slot> storageSlots(List<ReplayRecord.Tok> all) {
        List<Slot> out = new ArrayList<>();
        out.addAll(roster(all, slot("miner", 1), slot("miner", 2), slot("miner", 3),
            slot("miner", 4)));
        out.addAll(roster(all, slot("power_plant", 1), slot("power_plant", 2),
            slot("power_plant", 3), slot("power_plant", 4)));
        return out;
    }

    private static String cellKey(Slot s) {
        return ("miner".equals(s.type()) ? "miner-" : "plant-")
            + (s.level() == null ? 1 : s.level());
    }

    /** Что лежит в ячейках каждого складского здания: 'K', 'A' или 0 (пусто). */
    private final Map<String, char[]> cellFill = new LinkedHashMap<>();
    /** Две стартовые универсальные ячейки — они открыты всегда. */
    private char[] startFill = new char[]{0, 0};

    /**
     * ЯЧЕЙКИ СКЛАДА ПОД ЗДАНИЕМ: их открывает именно это здание. Здание ушло с
     * планшета — ячейки открыты и в них лежит запас; лежит в личном запасе —
     * ячейки закрыты им самим (перечёркнуты).
     */
    private void paintFreedCells(Graphics2D g, ReplayRecord.Player p,
                                 Slot s, int x, int y, int width) {
        String key = cellKey(s);
        String cells = p.storageCells.get(key);
        if (cells == null || cells.isBlank()) {
            return;
        }
        boolean open = !s.inStock();
        char[] fill = cellFill.get(key);
        int side = px(16);
        int gap = px(4);
        int cx = x + Math.max(0, (width - cells.length() * (side + gap) + gap) / 2);
        for (int i = 0; i < cells.length(); i++) {
            char type = cells.charAt(i);
            char has = open && fill != null && i < fill.length ? fill[i] : 0;
            g.setColor(open ? Theme.tile() : Theme.alpha(Theme.tile(), 0.5));
            g.fill(new RoundRectangle2D.Double(cx, y, side, side, Theme.R_TILE,
                Theme.R_TILE));
            g.setColor(open ? Theme.border() : Theme.alpha(Theme.border(), 0.6));
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(cx, y, side, side, Theme.R_TILE,
                Theme.R_TILE));
            if (!open) {
                g.setColor(Theme.alpha(Theme.ink3(), 0.7));
                g.drawLine(cx + px(4), y + px(4),
                    cx + side - px(4), y + side - px(4));
            } else if (has != 0) {
                // В ЯЧЕЙКЕ ЛЕЖИТ РЕСУРС — рисуем его, а не только тип ячейки
                MarkIcons.paint(g, cellIcon(has), cx + side / 2.0, y + side / 2.0,
                    side * 0.62, cellIconColour(has));
            } else if (type != 'U') {
                // пустая именная ячейка: еле видно, подо что она
                MarkIcons.paint(g, type == 'K' ? "KELIUM" : "AMMO",
                    cx + side / 2.0, y + side / 2.0, side * 0.45,
                    Theme.alpha(type == 'K' ? Theme.kelium() : Theme.ink2(), 0.28));
            }
            cx += side + gap;
        }
    }

    private final Map<java.awt.Rectangle, Slot> buildingSpots = new LinkedHashMap<>();

    /**
     * МЕСТО ЗДАНИЯ НА ПЛАНШЕТЕ. Рисуется САМ ЖЕТОН, только пока здание лежит
     * В ЛИЧНОМ ЗАПАСЕ — то есть не построено и не уехало к сопернику в трофеи.
     * Ушло с планшета — на его месте остаётся пунктирный силуэт.
     *
     * <p>Раньше было наоборот: жетон рисовался, когда здание СТОИТ НА ПОЛЕ
     * (замечание дизайнера 13.08.2026). Это переворачивало смысл планшета: он
     * показывает не поле, а то, что осталось у игрока на руках.
     */
    private void paintBuildingCell(Graphics2D g, Slot s, int seat, int x, int y, int cell) {
        boolean inStock = s.inStock();
        boolean captured = s.token() != null && s.token().capturedBy != null;
        g.setColor(Theme.tile());
        g.fill(new RoundRectangle2D.Double(x, y, cell, cell, Theme.R_TILE * 2,
            Theme.R_TILE * 2));

        FieldGeometry.Shape sh = FieldGeometry.buildingByCode(s.type());
        AffineTransform at = new AffineTransform();
        double k = cell * 0.72 / Math.max(sh.vbW(), sh.vbH());
        at.translate(x + cell / 2.0, y + cell / 2.0 - px(4));
        at.scale(k, k);
        at.translate(-sh.vbW() / 2.0, -sh.vbH() / 2.0);
        java.awt.Shape path = at.createTransformedShape(sh.path());
        if (inStock) {
            g.setColor(Theme.seat(seat));
            g.fill(path);
            g.setColor(Theme.seatStroke(seat));
            g.setStroke(new BasicStroke(Theme.pxf(1.2)));
            g.draw(path);
        } else {
            // УШЛО С ПЛАНШЕТА — пустое место пунктиром: стоит на поле или в трофеях
            g.setColor(Theme.alpha(captured ? Theme.bad() : Theme.ink3(), 0.9));
            g.setStroke(new BasicStroke(Theme.pxf(1.4), BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND, 0, new float[]{Theme.pxf(4), Theme.pxf(3)}, 0));
            g.draw(path);
        }
        // КОРОТКИЙ КОД НА ЖЕТОНЕ, ПОЛНОЕ ИМЯ — ПОД НИМ
        g.setFont(font(11, Font.BOLD));
        g.setColor(inStock ? Theme.ink() : Theme.ink3());
        String code = kelium.gui.GameRecorder.buildingLabel(s.type(), s.level());
        g.drawString(code, x + px(5), y + cell - px(5));
        if (captured) {
            g.setColor(Theme.bad());
            g.setFont(font(9, Font.BOLD));
            g.drawString("трофей", x + px(5), y + px(11));
        }
    }

    /**
     * Полное имя здания под его местом. Имя ДЛИННЕЕ СВОЕЙ КЛЕТКИ («энергостанция,
     * уровень 3»), поэтому переносится по словам и режется по ширине клетки: без
     * реза слово «энергостанция,» одно налезало на соседний столбец (замечание
     * дизайнера 13.08.2026).
     */
    private void paintBuildingName(Graphics2D g, Slot s, int x, int y, int cell) {
        g.setFont(font(8, Font.PLAIN));
        g.setColor(Theme.ink3());
        String full = kelium.gui.GameRecorder.buildingName(s.type(), s.level());
        int ty = y;
        for (String line : wrapWords(g, full, cell, 3)) {
            g.drawString(clipText(g, line, cell), x, ty);
            ty += px(9);
        }
    }

    /** Обрезать строку по ширине с многоточием. */
    private static String clipText(Graphics2D g, String s, int w) {
        if (g.getFontMetrics().stringWidth(s) <= w) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && g.getFontMetrics().stringWidth(t + "…") > w) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    /**
     * ХРАНИЛИЩЕ: ТОЛЬКО СТАРТОВЫЕ ЯЧЕЙКИ И ИТОГ. Весь остальной запас лежит в
     * ячейках ПОД СВОИМИ ЗДАНИЯМИ выше — рисовать его ещё раз отдельной линией
     * значит показывать одно и то же дважды (замечание дизайнера 13.08.2026).
     */
    private int paintStorage(Graphics2D g, ReplayRecord.Player p, int x, int y, int w) {
        caption(g, "ХРАНИЛИЩЕ", x, y);
        y += px(14);
        int cell = px(22);
        int gap = px(5);
        int cx = x;
        int cy = y;
        g.setFont(font(10, Font.PLAIN));
        g.setColor(Theme.ink3());
        g.drawString("свои ячейки планшета:", x, cy + px(14));
        cx = x + g.getFontMetrics().stringWidth("свои ячейки планшета:") + px(8);
        for (int i = 0; i < startFill.length; i++) {
            g.setColor(Theme.tile());
            g.fill(new RoundRectangle2D.Double(cx, cy, cell, cell, Theme.R_TILE,
                Theme.R_TILE));
            g.setColor(Theme.border());
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(cx, cy, cell, cell, Theme.R_TILE,
                Theme.R_TILE));
            if (startFill[i] != 0) {
                MarkIcons.paint(g, cellIcon(startFill[i]), cx + cell / 2.0,
                    cy + cell / 2.0, cell * 0.62, cellIconColour(startFill[i]));
            }
            cx += cell + gap;
        }
        cy += cell + px(8);
        g.setFont(font(11, Font.PLAIN));
        g.setColor(Theme.ink2());
        g.drawString("занято " + (p.kelium + p.ammo + p.debris) + " из " + p.storeCap
            + "  ·  келемий ≤ " + p.keliumCap + ", боеприпасы ≤ " + p.ammoCap
            + ", обломки в любую ячейку", x, cy + px(10));
        cy += px(16);
        // ОБЛОМКИ ОТДЕЛЬНОЙ СТРОКОЙ: по ячейкам они разложены выше, но пересчитать
        // чёрные кубики глазами по всему планшету тяжело — поэтому ещё и числом.
        int dn = Math.max(0, p.debris);
        for (int i = 0; i < dn && i < 12; i++) {
            MarkIcons.paint(g, "DEBRIS", x + px(9) + i * px(20), cy + px(9), px(16),
                Theme.debris());
        }
        g.setColor(Theme.ink2());
        g.drawString("обломки " + p.debris + " из " + p.debrisCap,
            x + px(9) + Math.min(dn, 12) * px(20) + px(6), cy + px(13));
        cy += px(24);
        cy = paintStorageTokens(g, p, x, cy);
        // контейнеры
        int cn = Math.max(p.containers, 0);
        for (int i = 0; i < cn; i++) {
            MarkIcons.paint(g, "CONTAINER", x + px(9) + i * px(20),
                cy + px(9), px(16), Theme.container());
        }
        g.setColor(Theme.ink2());
        g.drawString("контейнеры " + p.containers
            + (p.containerCap >= 0 ? " из " + p.containerCap : ""),
            x + px(9) + cn * px(20) + px(6), cy + px(13));
        return cy + px(26);
    }

    /**
     * ДВА ЛИЧНЫХ ЖЕТОНА МОДУЛЯ ХРАНИЛИЩА. Их всегда ровно два, приходят только с
     * зелёного трека, и сторона выбирается при установке НАВСЕГДА — поэтому оба
     * места рисуются с самого начала, пустые штрихом. Той же логикой, что места
     * под красный и синий: место видно раньше, чем жетон на нём появится.
     */
    private int paintStorageTokens(Graphics2D g, ReplayRecord.Player p, int x, int y) {
        g.setFont(font(11, Font.PLAIN));
        g.setColor(Theme.ink3());
        g.drawString("жетоны хранилища:", x, y + px(13));
        int side = px(18);
        int sx = x + g.getFontMetrics().stringWidth("жетоны хранилища:") + px(8);
        for (int i = 0; i < 2; i++) {
            String tok = i < p.storageTokens.size() ? p.storageTokens.get(i) : null;
            ModuleSlot.paintStorageToken(g, tok, sx, y, side);
            storeTokenSpots.put(new Rectangle(sx, y, side, side), tok);
            sx += side + px(6);
        }
        return y + side + px(8);
    }

    /** Планшет войск: сколько какого рода на поле и сколько ждёт в запасе. */
    private int paintTroops(Graphics2D g, ReplayRecord.Frame f, ReplayRecord.Player p,
                            int x, int y, int w) {
        caption(g, "ВОЙСКА", x, y);
        y += px(14);
        // ВСЕ ЧЕТЫРЕ ВИДА ВОЙСК ПОКАЗЫВАЕМ ВСЕГДА, и запас берём ПЕЧАТНЫЙ — из
        // записи. Раньше строки собирались только по жетонам, которые успели
        // появиться в партии, а предел выводился как «сколько их всего у игрока»
        // — отсюда и разнобой «0 из 1» у одного и «2 из 2» у другого, хотя в
        // личном запасе у каждого по 4 жетона каждого вида (ошибка, найдена
        // 13.08.2026; в движке предел по виду тоже не проверялся).
        Map<String, int[]> byType = new LinkedHashMap<>();
        for (String type : new String[]{"infantry", "vehicle", "aircraft", "tower"}) {
            byType.put(type, new int[2]);
        }
        for (ReplayRecord.Tok t : f.snapshot.tokens) {
            if (t.building || t.owner != p.seat || !t.alive) {
                continue;
            }
            int[] pair = byType.computeIfAbsent(t.type, k -> new int[2]);
            if (t.hexId != null) {
                pair[0]++;
            } else {
                pair[1]++;
            }
        }
        int row = px(24);
        for (Map.Entry<String, int[]> e : byType.entrySet()) {
            int onField = e.getValue()[0];
            int all = Math.max(session.record().unitStockOf(e.getKey()),
                onField + e.getValue()[1]);
            // в запасе — всё, что не стоит на поле: и созданные жетоны, и ещё не
            // тронутые из личного запаса
            int reserve = all - onField;
            g.setFont(font(12, Font.PLAIN));
            g.setColor(Theme.ink());
            g.drawString(Names.unit(e.getKey()), x, y + px(12));
            // стопка: закрашенные — в запасе, пустые — уже на поле
            int dot = px(9);
            int dx = x + px(96);
            for (int i = 0; i < all && i < 12; i++) {
                boolean inReserve = i < reserve;
                g.setColor(inReserve ? Theme.seat(p.seat) : Theme.alpha(Theme.ink3(), 0.5));
                g.fill(new Ellipse2D.Double(dx + i * (dot + px(3)), y + px(4),
                    dot, dot));
            }
            g.setFont(mono(11, Font.BOLD));
            g.setColor(Theme.ink2());
            g.drawString("в запасе " + reserve + " из " + all,
                x + px(96) + 12 * (dot + px(3)) + px(8), y + px(12));
            // МЕСТО ПОД КРАСНЫЙ МОДУЛЬ — У РОДА, А НЕ У ЗДАНИЯ: красный ложится
            // на вторичный ряд атаки рода войск. Место есть у каждого рода
            // всегда, пустое показано штрихом.
            int side = px(18);
            int mx = x + px(74);
            int my = y + px(2);
            ReplayRecord.Module rm = p.redPlaced.get(e.getKey());
            ModuleSlot.paint(g, rm, ModuleSlot.red(), mx, my, side);
            moduleSpots.put(new Rectangle(mx, my, side, side),
                new ModuleSpot(rm, true, Names.unit(e.getKey())));
            y += row;
        }
        if (byType.isEmpty()) {
            g.setFont(Theme.italic());
            g.setColor(Theme.ink3());
            g.drawString("войск нет", x, y + px(12));
            y += row;
        }
        return y;
    }

    /**
     * МОДУЛИ В ЗАПАСЕ И КАРТЫ. Поставленные жетоны здесь НЕ показываются: они
     * нарисованы там, где лежат, — синие под военными зданиями, красные под
     * родами войск. Здесь остаётся то, чему места на планшете нет: сколько
     * жетонов ещё не разложено.
     */
    private int paintModulesAndCards(Graphics2D g, ReplayRecord.Player p, int x, int y,
                                     int w) {
        caption(g, "МОДУЛИ В ЗАПАСЕ И КАРТЫ", x, y);
        y += px(14);
        int t = px(18);
        int freeRed = Math.max(0, p.redModules - p.redPlaced.size());
        int freeBlue = Math.max(0, p.blueModules - p.bluePlaced.size());
        int cx = x;
        cx = spare(g, cx, y, t, freeRed, ModuleSlot.red(), "красных");
        cx = spare(g, cx, y, t, freeBlue, ModuleSlot.blue(), "синих");
        if (freeRed + freeBlue == 0) {
            g.setFont(Theme.italic());
            g.setColor(Theme.ink3());
            g.drawString(p.redModules + p.blueModules == 0
                ? "модулей пока нет" : "все жетоны разложены по местам", x, y + px(13));
        }
        y += t + px(12);

        // ---- КАРТЫ НА РУКАХ — кнопками-стопками, каждую можно ОТКРЫТЬ И ПРОЧИТАТЬ.
        // Списком прямо здесь их держать нельзя: планшет и так плотный, а карт
        // бывает много. Поэтому здесь только «сколько чего», а щелчок открывает
        // читалку с разворотом карты (просьба дизайнера 13.08.2026).
        deckSpots.clear();
        int bx = x;
        bx = deckButton(g, bx, y, "задания", p.objectiveHand.size(), "objectives",
            p.objectiveHand);
        bx = deckButton(g, bx, y, "арсенал в руке", p.arsenalHand.size(), "arsenal",
            p.arsenalHand);
        bx = deckButton(g, bx, y, "арсенал установлен", p.arsenalInstalled.size(),
            "arsenal", p.arsenalInstalled);
        if (p.superObjective != null) {
            bx = deckButton(g, bx, y, "супер-задание", 1, "super_objectives",
                java.util.List.of(p.superObjective));
        }
        // КОНТЕЙНЕРЫ — только числом, и это честно: движок хранит их СЧЁТОМ, без
        // имён карт, поэтому читать в них нечего.
        // СТРОКА ПЕРЕНОСИТСЯ: она идёт ПОСЛЕ кнопок стопок, и на узком планшете
        // остатка ширины ей не хватало — хвост уезжал за правый край (замечание
        // дизайнера 13.08.2026). Не влезает рядом — уходит на строку ниже.
        g.setFont(font(11, Font.PLAIN));
        g.setColor(Theme.ink3());
        String tail = "контейнеров на руках " + p.containers
            + " — какие именно, запись не хранит";
        int room = x + w - bx - px(8);
        int tx = bx + px(4);
        int tyy = y + px(16);
        if (g.getFontMetrics().stringWidth(tail) > room) {
            tx = x;
            tyy = y + px(38);
        }
        for (String s : wrapWords(g, tail, x + w - tx, 2)) {
            g.drawString(s, tx, tyy);
            tyy += px(13);
        }
        return Math.max(y + px(34), tyy);
    }

    /** Стопка карт: что это, сколько, и куда ведёт щелчок. */
    private record Deck(String kind, java.util.List<String> ids, String title) {
    }

    /** Кнопки стопок и что за ними стоит — заполняется при отрисовке. */
    private final Map<Rectangle, Deck> deckSpots = new LinkedHashMap<>();

    /** Кнопка-стопка карт. Возвращает правый край. */
    private int deckButton(Graphics2D g, int x, int y, String label, int count,
                           String kind, java.util.List<String> ids) {
        g.setFont(font(11, Font.PLAIN));
        String text = label + " " + count;
        int w = g.getFontMetrics().stringWidth(text) + px(18);
        int h = px(22);
        Rectangle r = new Rectangle(x, y, w, h);
        boolean live = count > 0 && !ids.isEmpty();
        boolean over = live && r.equals(hotDeck);
        g.setColor(over ? Theme.hover() : Theme.tile());
        g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height,
            Theme.R_TILE * 2, Theme.R_TILE * 2));
        g.setColor(live ? (over ? Theme.accent() : Theme.border())
            : Theme.alpha(Theme.border(), 0.5));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height,
            Theme.R_TILE * 2, Theme.R_TILE * 2));
        g.setColor(live ? Theme.ink2() : Theme.ink3());
        g.drawString(text, x + px(9), y + h / 2 + px(4));
        if (live) {
            deckSpots.put(r, new Deck(kind, ids, label));
        }
        return x + w + px(6);
    }

    /** Стопка под курсором — чтобы кнопка подсвечивалась. */
    private Rectangle hotDeck;

    /**
     * НЕРАЗЛОЖЕННЫЕ ЖЕТОНЫ ОДНОГО ЦВЕТА: квадратики и число рядом. Какие именно
     * это жетоны, запись не хранит — движок держит запас счётчиком, id появляется
     * только в момент раскладки. Поэтому здесь честно рисуется количество, а не
     * выдуманные имена.
     */
    private int spare(Graphics2D g, int x, int y, int side, int count, Color colour,
                      String label) {
        if (count <= 0) {
            return x;
        }
        for (int i = 0; i < count && i < 4; i++) {
            g.setColor(colour);
            g.fill(new RoundRectangle2D.Double(x, y, side, side, Theme.R_TILE,
                Theme.R_TILE));
            g.setColor(Theme.alpha(Color.BLACK, 0.35));
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(x, y, side, side, Theme.R_TILE,
                Theme.R_TILE));
            x += side + px(4);
        }
        g.setFont(font(11, Font.PLAIN));
        g.setColor(Theme.ink3());
        String text = count + " " + label;
        g.drawString(text, x + px(2), y + px(13));
        return x + g.getFontMetrics().stringWidth(text) + px(14);
    }

    /** Отложенный слепым сбросом приказ — рубашкой вверх, как он и лежит. */
    private void paintSetAside(Graphics2D g, ReplayRecord.Player p, int x, int y,
                               int w, int h) {
        caption(g, "ОТЛОЖЕННЫЙ ПРИКАЗ", x, y);
        int top = y + px(14);
        Color back = orderColour(p.orderColor);
        g.setColor(p.orderSetAside == null ? Theme.tile() : back);
        g.fill(new RoundRectangle2D.Double(x, top, w, h, Theme.R_OVERLAY * 2,
            Theme.R_OVERLAY * 2));
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, top, w, h, Theme.R_OVERLAY * 2,
            Theme.R_OVERLAY * 2));
        g.setFont(font(11, Font.BOLD));
        if (p.orderSetAside == null) {
            g.setColor(Theme.ink3());
            g.drawString("ничего не отложено", x + px(10), top + px(20));
            return;
        }
        // ЛИЦО ПОКАЗЫВАЕМ. На столе карта лежит рубашкой, но разбор партии — не
        // стол: тот, кто её смотрит, должен видеть, ЧТО именно игрок отложил, иначе
        // половина решений в партии необъяснима (решение дизайнера 13.08.2026).
        g.setColor(Color.WHITE);
        g.setFont(font(11, Font.BOLD));
        g.drawString(Names.orderDeck(p.orderColor) + " колода", x + px(10),
            top + px(18));

        // КАРТА ПОПОЛАМ: сверху верхний приказ, снизу нижний — как она и напечатана.
        // Одной строкой пара приказов не помещалась и вылезала за край карты
        // (замечание дизайнера 13.08.2026).
        String[] pair = setAsidePair(p.orderSetAside);
        int half = (h - px(26)) / 2;
        int topY = top + px(26);
        g.setColor(Theme.alpha(Color.WHITE, 0.35));
        g.drawLine(x + px(8), topY + half, x + w - px(8), topY + half);
        drawOrderHalf(g, "верхний", pair[0], x, topY, w, half);
        drawOrderHalf(g, "нижний", pair[1], x, topY + half, w, half);
    }

    /** Половина карты приказа: подпись какая половина и название приказа. */
    private void drawOrderHalf(Graphics2D g, String which, String order,
                               int x, int y, int w, int h) {
        g.setFont(font(9, Font.PLAIN));
        g.setColor(Theme.alpha(Color.WHITE, 0.65));
        g.drawString(which, x + px(10), y + px(13));
        g.setFont(font(12, Font.BOLD));
        g.setColor(Color.WHITE);
        // длинные названия («ИНФРАСТРУКТУРА») переносим, а не режем краем карты
        int ty = y + px(28);
        for (String line : wrapWords(g, order == null ? "не вскрыт" : order,
                w - px(20), 2)) {
            g.drawString(line, x + px(10), ty);
            ty += px(14);
        }
    }

    /** Разбить строку по словам под заданную ширину. */
    private static java.util.List<String> wrapWords(Graphics2D g, String text, int w,
                                                    int maxLines) {
        java.util.List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(probe) > w && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
                if (out.size() == maxLines) {
                    return out;
                }
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0 && out.size() < maxLines) {
            out.add(line.toString());
        }
        return out;
    }

    /** Верхний и нижний приказы отложенной карты (любой может быть null). */
    private String[] setAsidePair(String cardId) {
        if (cardId != null) {
            for (ReplayRecord.OrderPlay op : session.record().orderPlays) {
                if (cardId.equals(op.card)) {
                    return new String[]{Names.order(op.top),
                        op.bottom == null || op.bottom.isBlank() ? null
                            : Names.order(op.bottom)};
                }
            }
        }
        return new String[]{null, null};
    }

    /**
     * Какие приказы напечатаны на отложенной карте. Берём из уже разыгранных карт
     * той же партии: колода за раунды проходит по кругу, и та же карта почти
     * всегда где-то вскрывалась. Не нашлось — честно ничего не пишем.
     */
    private String setAsideOrders(String cardId) {
        if (cardId == null) {
            return null;
        }
        for (ReplayRecord.OrderPlay op : session.record().orderPlays) {
            if (cardId.equals(op.card)) {
                return Names.orderPair(op.top, op.bottom);
            }
        }
        return null;
    }

    /**
     * КАРТА ТРОФЕЕВ. Снесённые жетоны лежат вразнобой, как их и бросают на карту;
     * поворот случайный, но УСТОЙЧИВЫЙ — он привязан к номеру жетона, поэтому при
     * листании партии вперёд-назад они не дёргаются на месте.
     */
    private void paintTrophyCard(Graphics2D g, ReplayRecord.Player p, int x, int y,
                                 int w, int h) {
        caption(g, "КАРТА ТРОФЕЕВ", x, y);
        int top = y + px(14);
        g.setColor(Theme.tile());
        g.fill(new RoundRectangle2D.Double(x, top, w, h, Theme.R_OVERLAY * 2,
            Theme.R_OVERLAY * 2));
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, top, w, h, Theme.R_OVERLAY * 2,
            Theme.R_OVERLAY * 2));

        List<ReplayRecord.TrophyToken> tokens = p.trophyCard;
        int n = tokens.size();
        int cols = Math.max(1, (w - px(20)) / px(58));
        for (int i = 0; i < n; i++) {
            ReplayRecord.TrophyToken t = tokens.get(i);
            int col = i % cols;
            int rowIdx = i / cols;
            double cx = x + px(34) + col * px(58);
            double cy = top + px(34) + rowIdx * px(52);
            if (cy > top + h - px(10)) {
                break;
            }
            // «небрежный» поворот: от номера жетона, поэтому всегда один и тот же
            double angle = ((t.uid * 73) % 60) - 30;
            paintTrophyToken(g, t, cx, cy, px(40), angle);
        }
        // чёрные кубики: сколько обломков даст возврат (флат, 1 за жетон)
        int cubes = Math.max(0, p.trophyTokens);
        int side = px(12);
        int bx = x + px(12);
        int by = top + h - px(22);
        for (int i = 0; i < cubes && i < 14; i++) {
            g.setColor(new Color(0x1A1A1A));
            g.fill(new RoundRectangle2D.Double(bx + i * (side + px(3)), by, side,
                side, px(3), px(3)));
            g.setColor(Theme.alpha(Color.WHITE, 0.25));
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(bx + i * (side + px(3)), by, side,
                side, px(3), px(3)));
        }
        g.setFont(font(11, Font.BOLD));
        g.setColor(Theme.ink2());
        g.drawString("в возврат: " + p.trophyTokens + " ОБЛ", x + w - px(90),
            by + px(11));
        if (n == 0) {
            g.setFont(Theme.italic());
            g.setColor(Theme.ink3());
            g.drawString("пока никого не снесли", x + px(12), top + px(24));
        }
    }

    /** Один трофейный жетон: силуэт бывшего владельца и напечатанная ценность. */
    private void paintTrophyToken(Graphics2D g, ReplayRecord.TrophyToken t, double cx,
                                  double cy, double size, double angleDeg) {
        FieldGeometry.Shape sh = t.building
            ? FieldGeometry.buildingByCode(t.type) : FieldGeometry.unitByCode(t.type);
        AffineTransform at = new AffineTransform();
        double k = size / Math.max(sh.vbW(), sh.vbH());
        at.translate(cx, cy);
        at.rotate(Math.toRadians(angleDeg));
        at.scale(k, k);
        at.translate(-sh.vbW() / 2.0, -sh.vbH() / 2.0);
        java.awt.Shape path = at.createTransformedShape(sh.path());
        // трофейная сторона: приглушённый металл, а не цвет живого места
        g.setColor(Theme.alpha(Theme.trophy(), 0.85));
        g.fill(path);
        g.setColor(Theme.alpha(Theme.seat(t.owner), 0.9));
        g.setStroke(new BasicStroke(Theme.pxf(1.4)));
        g.draw(path);
        g.setFont(mono(12, Font.BOLD));
        String value = String.valueOf(t.value);
        int tw = g.getFontMetrics().stringWidth(value);
        g.setColor(new Color(0x14, 0x14, 0x14));
        g.drawString(value, (float) (cx - tw / 2.0), (float) (cy + px(4)));
    }

    private static Color orderColour(String code) {
        return switch (code == null ? "" : code) {
            case "blue" -> new Color(0x3B82D0);
            case "red" -> new Color(0xC0392B);
            case "green" -> new Color(0x3F9E60);
            case "yellow" -> new Color(0xB08A2E);
            default -> new Color(0x6A5ACD);
        };
    }

    private void caption(Graphics2D g, String text, int x, int y) {
        g.setFont(Theme.caption());
        g.setColor(Theme.ink3());
        g.drawString(text, x, y + px(9));
    }

    /** Здания игрока: и на поле, и в запасе, и уехавшие в трофеи. */
    private List<ReplayRecord.Tok> buildingsOf(ReplayRecord.Frame f, int owner) {
        List<ReplayRecord.Tok> out = new ArrayList<>();
        for (ReplayRecord.Tok t : f.snapshot.tokens) {
            if (t.building && t.owner == owner) {
                out.add(t);
            }
        }
        out.sort((a, b) -> {
            int byType = a.type.compareTo(b.type);
            if (byType != 0) {
                return byType;
            }
            return Integer.compare(a.level == null ? 0 : a.level,
                b.level == null ? 0 : b.level);
        });
        return out;
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        // МЕСТА МОДУЛЕЙ ПРОВЕРЯЮТСЯ ПЕРВЫМИ: место под синий лежит ВНУТРИ
        // прямоугольника здания-соседа по вертикали, и если сперва спросить
        // здание, подсказка модуля не покажется никогда.
        for (Map.Entry<Rectangle, ModuleSpot> en : moduleSpots.entrySet()) {
            if (en.getKey().contains(e.getPoint())) {
                ModuleSpot sp = en.getValue();
                return Ui2.tip(ModuleSlot.describe(sp.module(), sp.red(), sp.slotName()));
            }
        }
        for (Map.Entry<Rectangle, String> en : storeTokenSpots.entrySet()) {
            if (en.getKey().contains(e.getPoint())) {
                return Ui2.tip(ModuleSlot.storageTokenName(en.getValue()));
            }
        }
        // ПОДСКАЗКА ПО МЕСТУ ЗДАНИЯ: главное в ней — ГДЕ здание сейчас, потому что
        // пустое место само по себе этого не говорит (просьба дизайнера 13.08.2026).
        for (Map.Entry<java.awt.Rectangle, Slot> en : buildingSpots.entrySet()) {
            if (!en.getKey().contains(e.getPoint())) {
                continue;
            }
            Slot s = en.getValue();
            ReplayRecord.Tok t = s.token();
            StringBuilder sb = new StringBuilder(
                kelium.gui.GameRecorder.buildingName(s.type(), s.level()));
            sb.append("  (").append(kelium.gui.GameRecorder
                .buildingLabel(s.type(), s.level())).append(')');
            if (t == null) {
                sb.append("\nЛЕЖИТ В ЛИЧНОМ ЗАПАСЕ — ни разу не строили");
            } else if (t.hexId != null && t.alive) {
                sb.append("\nПОСТРОЕНО, стоит на поле — гекс ").append(t.hexId);
                sb.append("\nпрочность ").append(Math.max(0, t.hp - t.damage))
                  .append('/').append(t.hp);
                if (t.energySlots > 0) {
                    sb.append("\nэнергия ").append(t.energyPlaced).append('/')
                      .append(t.energySlots);
                }
            } else if (t.capturedBy != null) {
                sb.append("\nСНЕСЕНО и лежит в трофеях у игрока ")
                  .append(t.capturedBy + 1);
            } else if (!t.alive) {
                sb.append("\nСНЕСЕНО — вернётся в запас на этапе Возврата");
            } else {
                sb.append("\nЛЕЖИТ В ЛИЧНОМ ЗАПАСЕ — можно построить");
            }
            if (!s.inStock() && ("miner".equals(s.type()) || "power_plant".equals(s.type()))) {
                sb.append("\nего ячейки склада ОТКРЫТЫ");
            } else if ("miner".equals(s.type()) || "power_plant".equals(s.type())) {
                sb.append("\nего ячейки склада закрыты — их закрывает сам жетон");
            }
            return Ui2.tip(sb.toString());
        }
        return null;
    }
}
