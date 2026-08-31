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
public final class BoardSheet extends JComponent implements javax.swing.Scrollable {

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

    // ============ ШИРИНА — ПО ЯЩИКУ, А НЕ ПО СЕБЕ ============
    //
    // На листе лежат ПЕЧАТНЫЕ планшеты во всю ширину, и при жёстко заданной
    // ширине лист вылезал за ящик: четвёртая колонка («Вышка», «Центр
    // управления») уезжала за край и её приходилось прокручивать вбок. Пока в
    // ящике не теснее нижнего предела, лист растягивается ровно по нему —
    // боковая прокрутка не нужна вовсе.

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof javax.swing.JViewport vp && vp.getWidth() >= px(560);
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return px(24);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL
            ? visible.height - px(24) : visible.width - px(24);
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

        // ПЛАНШЕТ ВОЙСК — ОТДЕЛЬНЫМ РЯДОМ НА ВСЮ ШИРИНУ (макет дизайнера
        // 20.08.2026). В правой колонке ему было тесно: четыре столбца по роду
        // войск получались шириной в 80 точек, и в них не влезали ни названия
        // («центр управления»), ни строки атак — текст обрезался краем. На
        // печатном планшете эти четыре столбца тоже идут во всю ширину, так что
        // так и правильнее по сути, а не только по месту.
        // ПЕЧАТНЫЕ ПЛАНШЕТЫ — САМИ КОМПОНЕНТЫ СО СТОЛА (просьба дизайнера
        // 25.08.2026: «хочу, чтобы планшет выглядел так же»). Есть картинка
        // своей стороны — показываем её с живым поверх; нет — прежний
        // рисованный вид, он же остаётся источником чисел, которых на печати
        // нет (запас жетонов, цены, скорости).
        int full = getWidth() - pad * 2;
        boolean printed = PrintedBoards.available(p.side);
        if (printed) {
            printedSpots.clear();
            PrintedBoards.paintTroop(g, pad, y, full, p, troopSide(p), printedSpots);
            y += PrintedBoards.troopHeight(p.side, full) + px(8);
            y = paintStockStrip(g, f, p, pad, y, full) + px(10);
        }
        if (!printed) {
            y = paintTroops(g, f, p, pad, y, full) + px(12);
        }

        int yLeft = paintBuildings(g, f, p, pad, y, leftW);
        if (printed) {
            // Планшет хранилища идёт ПОСЛЕ зданий: ячейки на нём открывают
            // именно они, и читается это сверху вниз, как на столе. Ширина —
            // две трети листа: в узкую колонку он ужимался до марки.
            int sw = (int) (full * 0.66);
            PrintedBoards.paintStorage(g, pad, yLeft + px(10), sw, p,
                cellFill, startFill, coveredCells(buildingsOf(f, p.seat)));
            yLeft += px(10) + PrintedBoards.storageHeight(p.side, sw) + px(6);
        }
        yLeft = paintStorage(g, p, pad, yLeft + px(10), leftW);

        int yRight = paintModulesAndCards(g, p, rightX, y, rightW);
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
        // МЕСТО КРУПНЕЕ ЖЕТОНА и само крупнее прежнего: на 18 точках площадка и
        // жетон сливались в один квадратик (просьба дизайнера 15.08.2026).
        int side = px(26);
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

    /** Прямоугольники мест под модули — для прогонщиков и тестов. */
    public java.util.List<Rectangle> moduleSpotsForTest() {
        return java.util.List.copyOf(moduleSpots.keySet());
    }

    /** То же для жетонов, лежащих на ПЕЧАТНОМ планшете. */
    private final Map<Rectangle, Object[]> printedSpots = new LinkedHashMap<>();

    /**
     * Складские здания, чей жетон ЛЕЖИТ НА ПЛАНШЕТЕ и накрывает свои печатные
     * ячейки. Ключи те же, что у {@link ReplayRecord.Player#storageCells}.
     */
    private java.util.Set<String> coveredCells(List<ReplayRecord.Tok> all) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (Slot s : storageSlots(all)) {
            if (s.inStock()) {
                out.add(cellKey(s));
            }
        }
        return out;
    }

    /**
     * ЗАПАС ЖЕТОНОВ ПО РОДАМ — единственное, чего на печатном планшете войск
     * нет: сколько родов уже на поле, а сколько ещё лежит у игрока. Узкая
     * строка под планшетом, по колонке на род — ровно под своей колонкой печати.
     */
    private int paintStockStrip(Graphics2D g, ReplayRecord.Frame f,
                                ReplayRecord.Player p, int x, int y, int w) {
        String[] types = {"infantry", "vehicle", "aircraft", "tower"};
        Map<String, int[]> byType = new LinkedHashMap<>();
        for (String t : types) {
            byType.put(t, new int[2]);
        }
        for (ReplayRecord.Tok t : f.snapshot.tokens) {
            if (t.building || t.owner != p.seat || !t.alive) {
                continue;
            }
            int[] pair = byType.get(t.type);
            if (pair != null) {
                if (t.hexId != null) {
                    pair[0]++;
                } else {
                    pair[1]++;
                }
            }
        }
        int colW = w / 4;
        int h = px(32);
        for (int i = 0; i < types.length; i++) {
            int[] c = byType.get(types[i]);
            int onField = c[0];
            int all = Math.max(session.record().unitStockOf(types[i]), onField + c[1]);
            int cx = x + i * colW;
            g.setColor(Theme.tile());
            g.fill(new RoundRectangle2D.Double(cx + px(2), y, colW - px(4), h,
                Theme.R_TILE, Theme.R_TILE));
            // Две строки, а не одна: в четверть ширины планшета длинная строка
            // «пехота: на поле 1 · запас 3» не влезала и наезжала на соседнюю.
            g.setFont(font(9, Font.BOLD));
            g.setColor(Theme.ink3());
            String top = Names.unit(types[i]).toUpperCase(java.util.Locale.ROOT);
            g.drawString(top, cx + (colW - g.getFontMetrics().stringWidth(top)) / 2,
                y + px(12));
            g.setFont(mono(10, Font.BOLD));
            g.setColor(Theme.ink2());
            String bot = "на поле " + onField + " · запас " + (all - onField);
            g.drawString(bot, cx + (colW - g.getFontMetrics().stringWidth(bot)) / 2,
                y + h - px(7));
        }
        return y + h;
    }

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
        // ПОД ПЛИТКОЙ — ТОЛЬКО ТО, ЧЕГО НЕТ ВЫШЕ. Род здания уже сказан дважды:
        // заголовком группы («ЭНЕРГОСТАНЦИИ») и подписью на самой плитке
        // («Эн-4»), и третий раз он не влезал — строка обрезалась на
        // «энергостан…». Поэтому у зданий с уровнями остаётся уровень, а полное
        // имя живёт в подсказке под курсором.
        String full = s.level() != null && s.level() > 0 ? "уровень " + s.level()
            : kelium.gui.GameRecorder.buildingName(s.type(), s.level());
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
        int cell = px(26);
        int gap = px(6);
        int cx = x;
        int cy = y;
        // ---- ПЛАНШЕТ ХРАНИЛИЩА ОДНОЙ ПОЛОСОЙ, как он и напечатан: свои ячейки,
        // за разделительной чертой — две ячейки, которые открывает жетон модуля,
        // и ещё за чертой — площадка под свободные кубики энергии (диктовка
        // дизайнера 15.08.2026). До этого на планшете были только свои ячейки, и
        // по нему нельзя было понять, что даёт жетон и куда кладут лишние кубики.
        g.setFont(font(10, Font.PLAIN));
        g.setColor(Theme.ink3());
        g.drawString("свои ячейки:", x, cy + px(16));
        cx = x + g.getFontMetrics().stringWidth("свои ячейки:") + px(8);
        for (int i = 0; i < startFill.length; i++) {
            paintSquareCell(g, cx, cy, cell, true);
            if (startFill[i] != 0) {
                MarkIcons.paint(g, cellIcon(startFill[i]), cx + cell / 2.0,
                    cy + cell / 2.0, cell * 0.62, cellIconColour(startFill[i]));
            }
            cx += cell + gap;
        }

        // ---- ЯЧЕЙКИ ОТ ЖЕТОНОВ МОДУЛЯ: их ровно две, и каждая открывается своим
        // жетоном, положенным стороной «склад». Пока жетон не положен — место
        // нарисовано, но погашено: видно, что открыть ещё можно.
        cx += px(6);
        cx = divider(g, cx, cy, cell);
        int opened = 0;
        for (String tok : p.storageTokens) {
            if (tok != null && !tok.contains("energy")) {
                opened++;
            }
        }
        cellZones.clear();
        for (int i = 0; i < 2; i++) {
            boolean on = i < opened;
            paintSquareCell(g, cx, cy, cell, on);
            if (!on) {
                g.setColor(Theme.alpha(Theme.ink3(), 0.5));
                g.setStroke(new BasicStroke(Theme.pxf(1.2)));
                g.drawLine(cx + px(6), cy + cell - px(6), cx + cell - px(6), cy + px(6));
            }
            cellZones.put(new Rectangle(cx, cy, cell, cell), on
                ? "ЯЧЕЙКА ОТ ЖЕТОНА ХРАНИЛИЩА\n\nОткрыта: жетон положен стороной "
                    + "«склад». Годится под келемий, боеприпасы и обломки."
                : "ЯЧЕЙКА ОТ ЖЕТОНА ХРАНИЛИЩА\n\nПока закрыта. Откроется, когда "
                    + "игрок положит сюда жетон модуля хранилища стороной «склад» "
                    + "(жетоны приходят только с зелёного трека науки).");
            cx += cell + gap;
        }

        // ---- ПЛОЩАДКА ПОД СВОБОДНЫЕ КУБИКИ ЭНЕРГИИ. Кубик жетона хранилища
        // лежит здесь, пока не понадобится: во время смены энергии он ходит
        // отсюда на любое своё здание и обратно, и денег это не стоит.
        cx += px(6);
        cx = divider(g, cx, cy, cell);
        int cubes = 0;
        for (String tok : p.storageTokens) {
            if (tok != null && tok.contains("energy")) {
                cubes++;
            }
        }
        int zoneW = Math.max(cell * 2 + gap, cubes * (cell + gap));
        g.setColor(Theme.alpha(Theme.energy(), Theme.isDark() ? 0.16 : 0.14));
        g.fill(new RoundRectangle2D.Double(cx, cy, zoneW, cell, Theme.R_TILE,
            Theme.R_TILE));
        g.setColor(Theme.alpha(Theme.energy(), 0.75));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10f, new float[]{Theme.pxf(3), Theme.pxf(3)}, 0f));
        g.draw(new RoundRectangle2D.Double(cx, cy, zoneW, cell, Theme.R_TILE,
            Theme.R_TILE));
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < cubes; i++) {
            MarkIcons.paint(g, "ENERGY", cx + gap + i * (cell + gap) + cell / 2.0,
                cy + cell / 2.0, cell * 0.58, Theme.energy());
        }
        cellZones.put(new Rectangle(cx, cy, zoneW, cell),
            "СВОБОДНЫЕ КУБИКИ ЭНЕРГИИ\n\nПлощадка планшета: здесь лежат кубики, "
            + "которые сейчас никого не питают. Во время смены энергии кубик "
            + "ходит отсюда на любое своё здание и обратно, и это не стоит денег.\n\n"
            + (cubes == 0
                ? "Сейчас пусто: кубик даёт жетон модуля хранилища, положенный "
                    + "стороной «энергия»."
                : "Кубиков от жетонов: " + cubes + "."));
        cy += cell + px(10);
        g.setFont(font(11, Font.PLAIN));
        g.setColor(Theme.ink2());
        // ПЕРЕПОЛНЕНИЕ НАЗЫВАЕТСЯ СЛОВАМИ. Склад умеет сжиматься: здание вернулось
        // на планшет и накрыло ячейки, на которых лежали кубики. Само «занято 13
        // из 11» читается как ошибка вида, поэтому причина написана рядом.
        int busy = p.kelium + p.ammo + p.debris;
        g.setColor(busy > p.storeCap ? Theme.bad() : Theme.ink2());
        g.drawString("занято " + busy + " из " + p.storeCap
            + (busy > p.storeCap ? " — сверх места, ячейки закрылись" : "")
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
        // ПРЕДЕЛ НЕ БЫВАЕТ ОТРИЦАТЕЛЬНЫМ: когда склад переполнен, места под
        // обломки просто нет — так и написано, а не «из −2».
        g.drawString("обломки " + p.debris
                + (p.debrisCap < 0 ? " — места нет" : " из " + p.debrisCap),
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

    /** Зоны планшета хранилища и их пояснения — под подсказку курсора. */
    private final Map<Rectangle, String> cellZones = new LinkedHashMap<>();

    /**
     * КВАДРАТНАЯ ЯЧЕЙКА ПОД РЕСУРС — ровно так она напечатана на планшете
     * (просьба дизайнера 15.08.2026: «места под жетоны сделать квадратными»).
     * Погашенная ячейка рисуется бледнее: место есть, но пока не работает.
     */
    private void paintSquareCell(Graphics2D g, int x, int y, int side, boolean active) {
        g.setColor(active ? Theme.tile() : Theme.alpha(Theme.tile(), 0.45));
        g.fill(new RoundRectangle2D.Double(x, y, side, side, Theme.R_TILE, Theme.R_TILE));
        g.setColor(active ? Theme.border() : Theme.alpha(Theme.border(), 0.55));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, y, side, side, Theme.R_TILE, Theme.R_TILE));
    }

    /** Разделительная черта между зонами планшета; возвращает X за ней. */
    private int divider(Graphics2D g, int x, int y, int height) {
        g.setColor(Theme.alpha(Theme.ink3(), 0.8));
        g.setStroke(new BasicStroke(Theme.pxf(2.2)));
        g.drawLine(x, y - px(4), x, y + height + px(4));
        g.setStroke(new BasicStroke(1f));
        return x + px(12);
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
        g.drawString("жетоны хранилища:", x, y + px(17));
        int side = px(26);
        int sx = x + g.getFontMetrics().stringWidth("жетоны хранилища:") + px(8);
        for (int i = 0; i < 2; i++) {
            String tok = i < p.storageTokens.size() ? p.storageTokens.get(i) : null;
            ModuleSlot.paintStorageToken(g, tok, sx, y, side);
            storeTokenSpots.put(new Rectangle(sx, y, side, side), tok);
            sx += side + px(6);
        }
        return y + side + px(8);
    }

    /**
     * ПЛАНШЕТ ВОЙСК — СТОЛБЕЦ НА КАЖДЫЙ РОД, как на печатном планшете (макет
     * дизайнера 20.08.2026).
     *
     * <p>ЗАЧЕМ ПЕРЕРИСОВАН. Прежде здесь были четыре строки со счётчиком запаса
     * — и всё. Ни кто по кому бьёт, ни скорости, ни цены здания: чтобы понять
     * ход бота, приходилось держать планшет в голове или лезть в справочник
     * (просьба дизайнера: «хочется видеть инфографику, кто по кому атакует, и
     * где какие жетоны стоят, чтобы легче считывать статы войск»).
     *
     * <p>ЧТО В СТОЛБЦЕ, сверху вниз — в том же порядке, что на планшете:
     * <ol>
     *   <li>здание, которое этот род производит: имя, ячейки энергии, цена;</li>
     *   <li>род войск: имя, прочность, скорость;</li>
     *   <li>ТАБЛИЦА АТАК: по кому бьёт основным ударом и по кому вторичным;
     *       на вторичный ряд ложится красный модуль, поэтому его место
     *       нарисовано рядом;</li>
     *   <li>жетоны: сколько на поле и сколько в запасе.</li>
     * </ol>
     *
     * <p>ПЕЧАТНЫЕ ЧИСЛА БЕРУТСЯ ИЗ СТОРОНЫ ПЛАНШЕТА ЭТОГО ИГРОКА, а не из
     * общих настроек: стороны разные, и у соседа те же роды бьют по-другому.
     * Читает их {@link kelium.core.TroopSide} — тот же класс, которым правила
     * пользуется движок, чтобы вид и игра не разошлись.
     */
    private int paintTroops(Graphics2D g, ReplayRecord.Frame f, ReplayRecord.Player p,
                            int x, int y, int w) {
        caption(g, "ПЛАНШЕТ ВОЙСК · сторона " + (p.side == null ? "?" : p.side), x, y);
        y += px(16);

        String[] types = {"infantry", "vehicle", "aircraft", "tower"};
        String[] makers = {"barracks", "factory", "airbase", "command_center"};

        // Сколько жетонов рода на поле и сколько всего у игрока.
        Map<String, int[]> byType = new LinkedHashMap<>();
        for (String type : types) {
            byType.put(type, new int[2]);
        }
        for (ReplayRecord.Tok t : f.snapshot.tokens) {
            if (t.building || t.owner != p.seat || !t.alive) {
                continue;
            }
            int[] pair = byType.get(t.type);
            if (pair == null) {
                continue;
            }
            if (t.hexId != null) {
                pair[0]++;
            } else {
                pair[1]++;
            }
        }

        kelium.core.TroopSide troop = troopSide(p);
        int gap = px(6);
        int colW = (w - gap * 3) / 4;
        int colH = px(150);
        for (int i = 0; i < types.length; i++) {
            paintTroopColumn(g, p, types[i], makers[i], troop,
                byType.get(types[i]), x + i * (colW + gap), y, colW, colH);
        }
        return y + colH + px(6);
    }

    /**
     * Сторона планшета войск этого игрока — или null, если правила не загружены.
     *
     * <p>Собирается тем же кодом, что и в партии ({@link
     * kelium.core.PlayerBoard#fromContent}): второй разбор той же таблицы рано
     * или поздно разошёлся бы с движком.
     */
    private kelium.core.TroopSide troopSide(ReplayRecord.Player p) {
        try {
            var entries = session.content().get("boards").entries;
            return kelium.core.PlayerBoard.fromContent(entries,
                p.side == null || p.side.isBlank() ? "A" : p.side, "A").troop;
        } catch (RuntimeException e) {
            // Запись открыта без правил — покажем то, что знаем из самой записи.
            return null;
        }
    }

    /** Один столбец планшета: здание, род, таблица атак, жетоны. */
    private void paintTroopColumn(Graphics2D g, ReplayRecord.Player p, String type,
                                  String maker, kelium.core.TroopSide troop,
                                  int[] counts, int x, int y, int w, int h) {
        java.awt.Shape box = new RoundRectangle2D.Double(x, y, w, h,
            Theme.R_OVERLAY, Theme.R_OVERLAY);
        g.setColor(Theme.tile());
        g.fill(box);
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(box);

        int pad = px(6);
        int ty = y + px(13);
        // ПОДСКАЗКА НА ВЕСЬ СТОЛБЕЦ: на экране остаются только числа, а что такое
        // «вторичный ряд» и почему у вышки он бесплатный — знание разовое.
        cellZones.put(new Rectangle(x, y, w, h),
            Names.unit(type).toUpperCase(java.util.Locale.ROOT) + "\n\nПроизводит "
            + Names.building(maker) + ". Точки слева от цены — ячейки энергии: "
            + "столько кубиков здание просит, чтобы работать.\n\n"
            + (troop == null ? "Правила не загружены — таблица атак недоступна."
                : troop.dualCell()
                    ? "Бой 2.0: универсальная ячейка бьёт по любой цели, но дороже "
                        + "боеприпасами; печатная — только по своей цели и дешевле."
                    : "Основной ряд дешевле боеприпасами, вторичный дороже. Красный "
                        + "модуль кладётся на вторичный ряд и меняет его цель.")
            + "\n\nКружки внизу: закрашенные — жетоны на поле, пустые — в запасе.");

        // ---------- здание-производитель ----------
        g.setFont(font(9, Font.PLAIN));
        g.setColor(Theme.ink3());
        g.drawString(Names.building(maker), x + pad, ty);
        ty += px(11);
        // ЯЧЕЙКИ ЭНЕРГИИ — точками: столько кубиков здание просит, чтобы работать.
        int slots = buildingSlots(maker);
        int dot = px(6);
        for (int i = 0; i < slots; i++) {
            g.setColor(Theme.energy());
            g.fill(new RoundRectangle2D.Double(x + pad + i * (dot + px(2)), ty - px(5),
                dot, dot, px(2), px(2)));
        }
        // ЦЕНЫ ЕСТЬ НЕ У ВСЕХ ЗДАНИЙ: центр управления не строят — он стоит с
        // начала партии, и против него на планшете цены не напечатано. Раньше
        // код спрашивал её у всех четырёх столбцов и падал на четвёртом.
        {
            int price = buildingPrice(troop, maker);
            g.setFont(mono(9, Font.BOLD));
            g.setColor(Theme.ink2());
            g.drawString(price < 0 ? "не строится" : price + " мон",
                x + pad + slots * (dot + px(2)) + px(4), ty);
        }
        ty += px(12);

        // ---------- род войск ----------
        g.setFont(font(12, Font.BOLD));
        g.setColor(Theme.ink());
        g.drawString(Names.unit(type), x + pad, ty);
        ty += px(13);
        g.setFont(mono(9, Font.PLAIN));
        g.setColor(Theme.ink2());
        // ПРОЧНОСТЬ РЯДОМ СО СКОРОСТЬЮ: без неё по планшету нельзя понять, чем
        // кончится обстрел, а именно за этим в него и смотрят.
        int hp = unitHp(type);
        String stat = (hp < 0 ? "" : "прочность " + hp + " · ")
            + "скорость " + (troop == null ? "?"
            : String.valueOf(troop.speed(kelium.core.UnitType.fromCode(type))));
        g.drawString(stat, x + pad, ty);
        ty += px(13);

        // ---------- таблица атак ----------
        g.setFont(font(9, Font.PLAIN));
        g.setColor(Theme.ink3());
        g.drawString("бьёт по", x + pad, ty);
        ty += px(11);
        if (troop == null) {
            g.setFont(Theme.italic());
            g.setColor(Theme.ink3());
            g.drawString("правила не загружены", x + pad, ty);
        } else {
            // ДВЕ СИСТЕМЫ БОЯ ЖИВУТ ОДНОВРЕМЕННО, и планшет обязан показывать ту,
            // по которой играли ИМЕННО ЭТУ партию:
            //   планшеты 1.0.x — печатная ПАРА целей (основной ряд и вторичный);
            //   планшеты 2.0.0 («Бой 2.0», dual_cell) — универсальная ячейка
            //     (любая цель, дороже) плюс ОДНА печатная специализированная.
            // Показывать пару там, где её нет, значит соврать про партию.
            kelium.core.UnitType ut = kelium.core.UnitType.fromCode(type);
            String[] labels;
            kelium.core.Target[] at;
            if (troop.dualCell()) {
                labels = new String[]{"любая", "печатная"};
                at = new kelium.core.Target[]{null, troop.specializedTarget(ut)};
            } else {
                labels = new String[]{"основной", "вторичный"};
                kelium.core.Target[] pair = troop.attacks(ut);
                at = pair == null ? new kelium.core.Target[]{null, null} : pair;
            }
            for (int r = 0; r < 2; r++) {
                g.setFont(mono(8, Font.PLAIN));
                g.setColor(Theme.ink3());
                g.drawString(labels[r], x + pad, ty);
                g.setFont(font(10, Font.BOLD));
                boolean any = troop.dualCell() && r == 0;
                g.setColor(at[r] == null && !any ? Theme.ink3() : Theme.ink());
                // ЦЕЛЬ ПРИЖАТА К ПРАВОМУ КРАЮ СТОЛБЦА, а не отступом от подписи:
                // подписи разной длины, и от фиксированного отступа «вторичный»
                // налезал на название цели.
                String val = any ? "по любым" : at[r] == null ? "нет" : targetName(at[r]);
                g.drawString(val, x + w - pad - g.getFontMetrics().stringWidth(val), ty);
                ty += px(13);
            }
        }

        // ---------- красный модуль: он ложится на вторичный ряд ----------
        int side = px(22);
        int mx = x + w - side - pad;
        int my = y + h - side - px(26);
        ReplayRecord.Module rm = p.redPlaced.get(type);
        ModuleSlot.paint(g, rm, ModuleSlot.red(), mx, my, side);
        moduleSpots.put(new Rectangle(mx, my, side, side),
            new ModuleSpot(rm, true, Names.unit(type)));
        // ПОДПИСЬ У ГНЕЗДА: пустой квадрат сам по себе ничего не говорит, а
        // положенный сюда жетон меняет строку атаки выше.
        g.setFont(mono(8, Font.PLAIN));
        g.setColor(Theme.ink3());
        g.drawString("красный модуль", x + pad, my + side / 2 + px(3));

        // ---------- жетоны: на поле и в запасе ----------
        int onField = counts[0];
        int all = Math.max(session.record().unitStockOf(type), onField + counts[1]);
        int reserve = all - onField;
        int by = y + h - px(10);
        g.setFont(mono(9, Font.BOLD));
        g.setColor(Theme.ink2());
        g.drawString("на поле " + onField + " · запас " + reserve, x + pad, by);
        int pd = px(7);
        int px0 = x + pad;
        int py = by - px(14);
        for (int i = 0; i < all && i < 8; i++) {
            // Закрашенный — стоит на поле, пустой — ждёт в запасе. Так видно
            // «где какие жетоны стоят» без пересчёта поля глазами.
            // РАЗНИЦА НЕ ТОЛЬКО В ЦВЕТЕ: на поле — залитый кружок цвета места, в
            // запасе — пустой контур. Одного цвета мало: он читается хуже в
            // обесцвеченном виде и не различается при дальтонизме.
            boolean placed = i < onField;
            Ellipse2D.Double dotShape =
                new Ellipse2D.Double(px0 + i * (pd + px(2)), py, pd, pd);
            if (placed) {
                g.setColor(Theme.seat(p.seat));
                g.fill(dotShape);
            } else {
                g.setColor(Theme.ink3());
                g.setStroke(new BasicStroke(1f));
                g.draw(dotShape);
            }
        }
    }

    /**
     * Цена стройки здания на этой стороне, или −1, если её нет.
     *
     * <p>Отсутствие цены — не ошибка данных: центр управления стоит у игрока с
     * начала партии и не строится вовсе.
     */
    private static int buildingPrice(kelium.core.TroopSide troop, String building) {
        if (troop == null) {
            return -1;
        }
        try {
            return troop.buildingPrice(building);
        } catch (RuntimeException notPrinted) {
            return -1;
        }
    }

    /** Название цели атаки словами игрока. */
    private static String targetName(kelium.core.Target t) {
        return switch (t) {
            case INFANTRY -> "пехоте";
            case VEHICLE -> "технике";
            case AIRCRAFT -> "авиации";
            case BUILDINGS_TOWERS -> "зданиям";
        };
    }

    /** Сколько ячеек энергии просит здание — из правил, иначе 0. */
    private int buildingSlots(String building) {
        try {
            var entries = session.content().get("boards").entries;
            for (var e : entries) {
                Object bs = e.get("buildings");
                if (bs instanceof Map<?, ?> m && m.get(building) instanceof Map<?, ?> b
                        && b.get("energy_slots") instanceof Number n) {
                    return n.intValue();
                }
            }
        } catch (RuntimeException e) {
            return 0;
        }
        return 0;
    }

    /**
     * Прочность жетона рода войск — из правил, или −1, если правил нет.
     *
     * <p>Живёт не в стороне планшета, а в общей таблице жетонов: прочность у
     * всех сторон одна и та же, меняется только то, что напечатано на планшете.
     */
    private int unitHp(String type) {
        try {
            for (var e : session.content().get("boards").entries) {
                Object us = e.get("units");
                if (us instanceof Map<?, ?> m && m.get(type) instanceof Map<?, ?> u
                        && u.get("hp") instanceof Number n) {
                    return n.intValue();
                }
            }
        } catch (RuntimeException noRules) {
            return -1;
        }
        return -1;
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
        // КНОПКИ ИДУТ РЯДОМ, ПОКА ВЛЕЗАЮТ, дальше — новой строкой. Ширина кнопки
        // зависит от подписи и от числа карт, поэтому в один ряд они помещаются
        // не всегда: кнопка «арсенал установлен» уезжала за правый край листа и
        // обрезалась краем компонента.
        int rowH = px(28);
        int bx = x;
        int by = y;
        for (Object[] btn : deckButtons(p)) {
            String label = (String) btn[0];
            int count = (Integer) btn[1];
            int need = deckButtonWidth(g, label, count);
            if (bx > x && bx + need > x + w) {
                bx = x;
                by += rowH;
            }
            @SuppressWarnings("unchecked")
            java.util.List<String> ids = (java.util.List<String>) btn[3];
            bx = deckButton(g, bx, by, label, count, (String) btn[2], ids);
        }
        if (p.superObjective != null && p.superCells > 0) {
            int need = px(90);
            if (bx > x && bx + need > x + w) {
                bx = x;
                by += rowH;
            }
            bx = superCounter(g, bx, by, p);
        }

        // КОНТЕЙНЕРЫ — только числом, и это честно: движок хранит их СЧЁТОМ, без
        // имён карт, поэтому читать в них нечего.
        g.setFont(font(11, Font.PLAIN));
        g.setColor(Theme.ink3());
        String tail = "контейнеров на руках " + p.containers;
        int tx = bx + px(4);
        int tyy = by + px(16);
        if (g.getFontMetrics().stringWidth(tail) > x + w - tx) {
            tx = x;
            tyy = by + px(38);
        }
        g.drawString(tail, tx, tyy);
        // ПОЧЕМУ ИХ НЕЛЬЗЯ ОТКРЫТЬ — в подсказке, а не на экране: при каждом
        // взгляде это знание не нужно, а место занимает и сбивает ритм строки.
        cellZones.put(new Rectangle(tx, tyy - px(12),
                g.getFontMetrics().stringWidth(tail), px(16)),
            "КОНТЕЙНЕРЫ НА РУКАХ\n\nСколько карт контейнеров игрок держит. Какие "
            + "именно — запись не хранит: движок ведёт их счётом, имя карты "
            + "появляется только в момент вскрытия.");
        return Math.max(by + px(34), tyy + px(6));
    }

    /**
     * Стопки карт этого игрока: подпись, счёт, набор для читалки.
     *
     * <p>Списком, а не четырьмя вызовами подряд: ряд переносится по ширине, и
     * перенос считается ОДНИМ правилом на все кнопки, а не повторяется у каждой.
     */
    private java.util.List<Object[]> deckButtons(ReplayRecord.Player p) {
        java.util.List<Object[]> out = new ArrayList<>();
        out.add(new Object[]{"задания", p.objectiveHand.size(), "objectives", p.objectiveHand});
        out.add(new Object[]{"арсенал в руке", p.arsenalHand.size(), "arsenal", p.arsenalHand});
        out.add(new Object[]{"арсенал установлен", p.arsenalInstalled.size(), "arsenal",
            p.arsenalInstalled});
        if (p.superObjective != null) {
            // ПОДПИСЬ ГОВОРИТ, НА КАКОЙ ПОЛОВИНЕ ИГРОК СТОИТ: до вскрытия это
            // карта в руке, после — планшет супероружия с ячейками и счётчиком.
            out.add(new Object[]{
                p.superCells < 0 ? "супер-задание (1-я часть)"
                    : "супероружие: ячеек " + p.superCells,
                1, "super_objectives", java.util.List.of(p.superObjective)});
        }
        return out;
    }

    /** Ширина кнопки-стопки вместе с отступом до следующей. */
    private int deckButtonWidth(Graphics2D g, String label, int count) {
        g.setFont(font(11, Font.PLAIN));
        return g.getFontMetrics().stringWidth(label + " " + count) + px(18) + px(6);
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

    /** Отложенный приказ — рубашкой вверх, как он и лежит на столе. */
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
        // БЕЗОПАСНОСТЬ РИСУЕТСЯ ОДНИМ БЛОКОМ. У джокера нет верха и низа, и
        // подписи «верхний/нижний» на нём — выдумка (замечание дизайнера
        // 20.08.2026: «верх низ у безопасности почему-то»).
        int topY = top + px(26);
        if (isJoker(p.orderSetAside)) {
            g.setFont(font(9, Font.PLAIN));
            g.setColor(Theme.alpha(Color.WHITE, 0.65));
            g.drawString("приказ", x + px(10), topY + px(13));
            g.setFont(font(12, Font.BOLD));
            g.setColor(Color.WHITE);
            g.drawString("БЕЗОПАСНОСТЬ", x + px(10), topY + px(30));
            g.setFont(font(9, Font.PLAIN));
            g.setColor(Theme.alpha(Color.WHITE, 0.65));
            g.drawString("половин нет", x + px(10), topY + px(46));
            return;
        }
        String[] pair = setAsidePair(p.orderSetAside);
        int half = (h - px(26)) / 2;
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
        for (String line : wrapWords(g, order == null ? "нет" : order,
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

    /**
     * ПРИКАЗЫ ОТЛОЖЕННОЙ КАРТЫ — ИЗ КАТАЛОГА, А НЕ ИЗ РАЗЫГРАННОГО.
     *
     * <p>БАГ-ФИКС 20.08.2026 (дизайнер: «сущий ад с указанием сброшенного
     * приказа… какой-то статус „не вскрыт“ — что это вообще значит для
     * сброшенной под трофеи карты?»).
     *
     * <p>Прежде приказы искались среди УЖЕ РАЗЫГРАННЫХ карт партии: если та же
     * карта где-то вскрывалась, её приказы брались оттуда, а если нет — панель
     * писала «не вскрыт». То есть надпись означала не состояние карты, а «я не
     * нашёл» — и вводила в заблуждение вдвойне: отложенная карта в этом раунде
     * не вскрывается вовсе, у неё нет и не может быть такого состояния.
     *
     * <p>Приказы — свойство САМОЙ КАРТЫ, они напечатаны на ней. Поэтому берём их
     * из каталога приказов: тогда они известны всегда, а «не вскрыт» исчезает.
     *
     * @return {@code {верх, низ}}; низа может не быть. Для карты БЕЗОПАСНОСТЬ
     *         (джокер) возвращается {@code {null, null}} — у неё нет половин, и
     *         рисовать их нельзя.
     */
    private String[] setAsidePair(String cardId) {
        if (cardId == null) {
            return new String[]{null, null};
        }
        try {
            java.util.Map<String, Object> c =
                session.content().get("orders").byId(cardId);
            if (Boolean.TRUE.equals(c.get("joker"))) {
                return new String[]{null, null};
            }
            Object bottom = c.get("bottom");
            return new String[]{Names.order(String.valueOf(c.get("top"))),
                bottom == null ? null : Names.order(String.valueOf(bottom))};
        } catch (RuntimeException e) {
            // Каталог недоступен (запись открыта без правил) — так и скажем.
            return new String[]{null, null};
        }
    }

    /** Карта БЕЗОПАСНОСТЬ (джокер): у неё нет половин. */
    private boolean isJoker(String cardId) {
        if (cardId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(
                session.content().get("orders").byId(cardId).get("joker"));
        } catch (RuntimeException e) {
            return false;
        }
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
        // ПРИЖАТО К ПРАВОМУ КРАЮ КАРТЫ ПО ФАКТИЧЕСКОЙ ШИРИНЕ ТЕКСТА, а не по
        // отступу «на глаз»: от фиксированных 90 точек строка вылезала за край
        // карты и обрезалась на «в возврат: 0 ОБЛ…».
        String back = "в возврат: " + p.trophyTokens + " ОБЛ";
        g.drawString(back, x + w - px(12) - g.getFontMetrics().stringWidth(back),
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
        for (Map.Entry<Rectangle, Object[]> en : printedSpots.entrySet()) {
            if (en.getKey().contains(e.getPoint())) {
                Object[] sp = en.getValue();
                return Ui2.tip(ModuleSlot.describe((ReplayRecord.Module) sp[0],
                    (Boolean) sp[1], String.valueOf(sp[2])));
            }
        }
        for (Map.Entry<Rectangle, String> en : storeTokenSpots.entrySet()) {
            if (en.getKey().contains(e.getPoint())) {
                return Ui2.tip(ModuleSlot.storageTokenName(en.getValue()));
            }
        }
        // Зоны планшета хранилища: ячейки от жетона и площадка свободной энергии.
        for (Map.Entry<Rectangle, String> en : cellZones.entrySet()) {
            if (en.getKey().contains(e.getPoint())) {
                return Ui2.tip(en.getValue());
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

    /**
     * СЧЁТЧИК ЗАПУСКА — вторая половина супер-задания, нарисованная как она
     * лежит на столе: занятые ячейки с символами, которыми за них платят.
     *
     * <p>Раньше на этом месте показывалась «рубашка» — двусторонняя карта с
     * рисунком, которой в правилах больше нет (версия 3.0 её отменила). Показ
     * рубашки не говорил ни-че-го: ни сколько ячеек осталось, ни чем их снимать.
     * Теперь видно ровно то, что решает партию: сколько снятий до победы и какие
     * символы для них нужны.
     */
    private int superCounter(Graphics2D g, int x, int y, ReplayRecord.Player p) {
        int cell = px(22);
        int gap = px(4);
        int n = Math.max(0, p.superCells);
        int w = n * (cell + gap) + px(8);
        // Значки берём из разметки символов действующей версии данных: сама
        // запись хранит формы («квадрат», «круг»), а не картинки.
        kelium.engine.Symbols.Marking m = kelium.engine.Symbols.load(
            kelium.dataio.GameConfig.resolveDataRoot(null), null);
        for (int i = 0; i < n; i++) {
            int cx = x + px(4) + i * (cell + gap);
            g.setColor(Theme.alpha(Theme.accent(), 0.16));
            g.fillRoundRect(cx, y, cell, cell, px(6), px(6));
            g.setColor(Theme.alpha(Theme.ink3(), 0.55));
            g.drawRoundRect(cx, y, cell, cell, px(6), px(6));
            String form = i < p.superCellSymbols.size() ? p.superCellSymbols.get(i) : "";
            String glyph = m == null || form == null || form.isBlank()
                ? "?" : m.glyph(form);
            g.setFont(font(13, Font.BOLD));
            g.setColor(Theme.ink());
            int gw = g.getFontMetrics().stringWidth(glyph);
            g.drawString(glyph, cx + (cell - gw) / 2, y + cell - px(6));
        }
        return x + w;
    }
}
