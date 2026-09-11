package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;

import kelium.report.ReplayRecord;
import kelium.report.Textures;

/**
 * ПЕЧАТНЫЕ ПЛАНШЕТЫ ИГРОКА — те самые, что лежат на столе.
 *
 * <p>Просьба дизайнера 25.08.2026: «мне очень нравится, как выглядит планшет,
 * хочу, чтобы ты сделал такой же». Поэтому здесь не рисованная копия, а САМА
 * картинка компонента из типографии, а поверх неё кладётся живое: кубики склада
 * в напечатанные ячейки, жетоны модулей в свои рамки. Куда именно класть —
 * говорит {@link BoardAnchors} (координаты сняты с этой же картинки).
 *
 * <p>Нет картинки своей стороны — {@link #available} отвечает «нет», и планшет
 * показывается прежним рисованным видом {@link BoardSheet}. Печать не источник
 * правил: где она расходится с движком, играется движок.
 */
final class PrintedBoards {

    private PrintedBoards() {
    }

    /**
     * ПЛАНШЕТ ВЫБИРАЕТСЯ ПО ЦВЕТУ ИГРОКА, А НЕ ПО СТОРОНЕ.
     *
     * <p>Сторон «А» и «Б» больше нет (решение дизайнера 09.09.2026): асимметрия
     * планшетами упразднена. Вместо неё у каждого игрока СВОЙ планшет своего
     * цвета — с его портретом и его палитрой, чтобы за столом было видно, где
     * чьё хозяйство. Печать на всех четырёх одна и та же.
     *
     * <p>Старые планшеты сторон остаются запасным вариантом: по ним читаются
     * записи прошлых партий, где цветных планшетов ещё не было.
     */
    private static String цвет(int seat) {
        return "p" + (ПЛАНШЕТ_ПО_ЦВЕТУ[kelium.report.FieldGeometry.seatColor(seat)]);
    }

    /**
     * ЦВЕТОВОЕ ГНЕЗДО → НОМЕР ПЛАНШЕТА В ПАПКЕ.
     *
     * <p>Нумерация у двух наборов печати РАЗНАЯ, и это не описка, а факт: у
     * жетонов войск {@code p1} синий, {@code p2} красный, {@code p3} зелёный,
     * {@code p4} жёлтый; у планшетов игроков {@code p1} красный, {@code p2}
     * зелёный, {@code p3} синий, {@code p4} песочный. Связывать их по номеру
     * нельзя — тогда у синего игрока был бы красный планшет. Связываем по
     * ЦВЕТУ: гнездо 0 (синее) берёт планшет p3 и так далее.
     */
    private static final int[] ПЛАНШЕТ_ПО_ЦВЕТУ = {3, 1, 2, 4};

    /** Есть ли печатный планшет обоих видов для этого игрока. */
    static boolean available(int seat) {
        return troopArt(seat) != null && storageArt(seat) != null;
    }

    private static BufferedImage troopArt(int seat) {
        return Textures.board("troop-" + цвет(seat), "troop-A");
    }

    private static BufferedImage storageArt(int seat) {
        return Textures.board("storage-" + цвет(seat), "storage-A");
    }

    /** Высота планшета войск при такой ширине (0 — картинки нет). */
    static int troopHeight(int seat, int width) {
        return height(troopArt(seat), width);
    }

    /** Высота планшета хранилища при такой ширине (0 — картинки нет). */
    static int storageHeight(int seat, int width) {
        return height(storageArt(seat), width);
    }

    /** Якоря планшета войск этого игрока (с откатом на планшет стороны А). */
    private static java.util.List<BoardAnchors.Column> troopCols(int seat) {
        var cols = BoardAnchors.troop(цвет(seat));
        return cols.isEmpty() ? BoardAnchors.troop("A") : cols;
    }

    /** Якоря планшета хранилища этого игрока (с откатом на планшет стороны А). */
    private static java.util.List<BoardAnchors.Cell> storageCells(int seat) {
        var cells = BoardAnchors.storage(цвет(seat));
        return cells.isEmpty() ? BoardAnchors.storage("A") : cells;
    }

    private static int height(BufferedImage art, int width) {
        return art == null ? 0 : (int) Math.round(width * art.getHeight()
            / (double) art.getWidth());
    }

    // ==================== планшет войск ====================

    /**
     * ПЛАНШЕТ ВОЙСК с жетонами модулей в напечатанных рамках.
     *
     * <p>В рамке спец-атаки лежит красный жетон, если игрок его туда положил;
     * пусто — там показана цель, по которой род бьёт ПО ДВИЖКУ. Это важнее
     * напечатанного значка: играется движок, и если художник ещё не перерисовал
     * ячейку после смены правил, игрок увидит настоящее правило, а не старое.
     */
    static void paintTroop(Graphics2D g, int x, int y, int width,
                           ReplayRecord.Player p, kelium.core.TroopSide troop,
                           Map<Rectangle, Object[]> spots) {
        BufferedImage art = troopArt(p.seat);
        if (art == null) {
            return;
        }
        double k = width / (double) art.getWidth();
        int h = (int) Math.round(art.getHeight() * k);
        g.drawImage(art, x, y, width, h, null);
        for (BoardAnchors.Column c : troopCols(p.seat)) {
            paintTroopAttack(g, x, y, k, c, p, troop, spots);
            paintTroopAssembly(g, x, y, k, c, p, spots);
        }
    }

    private static void paintTroopAttack(Graphics2D g, int x, int y, double k,
                                         BoardAnchors.Column c, ReplayRecord.Player p,
                                         kelium.core.TroopSide troop,
                                         Map<Rectangle, Object[]> spots) {
        ReplayRecord.Module m = p.redPlaced.get(c.unit());
        Rectangle box = scale(x, y, k, c.ax(), c.ay(), c.aw(), c.ah());
        if (m != null) {
            // Жетон НАКРЫВАЕТ напечатанную ячейку ЦЕЛИКОМ — так он и лежит на
            // столе. Прежде он рисовался в 0,62 ячейки, и дизайнер это отбил
            // (09.09.2026): «жетоны красные и синие нихуя не закрывают собой
            // полностью ячейку, они милипиздрические какие-то».
            ModuleSlot.paintOnPrint(g, m, ModuleSlot.red(), box.x, box.y,
                box.width, box.height, c.unit());
            spots.put(new Rectangle(box),
                new Object[]{m, Boolean.TRUE, Names.unit(c.unit())});
            return;
        }
        if (troop == null || !troop.dualCell()) {
            return;
        }
        kelium.core.Target t = troop.specializedTarget(kelium.core.UnitType.fromCode(c.unit()));
        if (t == null) {
            return;
        }
        // Настоящая цель — узкой плашкой у нижней кромки рамки: не закрывает
        // печатный рисунок, но говорит, по кому род бьёт на самом деле.
        chip(g, box, "→ " + targetName(t), Theme.accent());
    }

    private static void paintTroopAssembly(Graphics2D g, int x, int y, double k,
                                           BoardAnchors.Column c, ReplayRecord.Player p,
                                           Map<Rectangle, Object[]> spots) {
        ReplayRecord.Module m = p.bluePlaced.get(c.building());
        if (m == null) {
            return;
        }
        Rectangle box = scale(x, y, k, c.bx(), c.by(), c.bw(), c.bh());
        // Рамка Сборки ВЫТЯНУТАЯ, и синий жетон нарисован таким же — кладём его
        // в рамку целиком, по её форме.
        ModuleSlot.paintOnPrint(g, m, ModuleSlot.blue(), box.x, box.y,
            box.width, box.height);
        spots.put(new Rectangle(box),
            new Object[]{m, Boolean.FALSE,
                kelium.gui.GameRecorder.buildingName(c.building())});
    }

    // ==================== сцепка планшетов ====================

    /**
     * СЦЕПКА ДВУХ ПЛАНШЕТОВ — как они лежат на столе.
     *
     * <p>Заказ дизайнера 09.09.2026: «планшет хранилища ВСЕГДА вставляется
     * СЛЕВА от планшета войск, слегка находя на него, таким образом он
     * полностью вставляется углом в „углубление“ слева у планшета».
     *
     * <p>И это не украшение, а замер: на левой кромке планшета войск напечатана
     * V-образная выемка, а у планшета хранилища справа — выступ ровно того же
     * угла. Совмещаем вершину выемки с вершиной выступа: планшеты садятся друг в
     * друга без зазора и без нахлёста печати.
     *
     * <p>Над планшетом войск отведена полоса под ЖЕТОНЫ ВОЕННЫХ ЗДАНИЙ: на
     * печати их места подписаны сверху («Казармы», «Завод», «Авиабаза», «Центр
     * Управления»), и жетон каждого ложится над своей подписью.
     *
     * <p>ОБА ПЛАНШЕТА ОДНОЙ ВЫСОТЫ. Это замер дизайнера: «высота планшета
     * хранилища ПОЛНОСТЬЮ совпадает по высоте с планшетом войск». А в экспорте
     * они разного разрешения — 634 точки у войск против 802 у хранилища, — и
     * если брать пиксель за пиксель, хранилище выходит на четверть выше и
     * читается как гигант. Поэтому картинка хранилища ужимается так, чтобы её
     * высота стала ровно высотой планшета войск.
     *
     * <p>Все размеры — в пикселях печати ПЛАНШЕТА ВОЙСК; рисуя, их умножают на
     * масштаб. Размеры хранилища сюда переведены через {@link #хрМасштаб}.
     *
     * @param ширина  вся сцепка вместе с полосой зданий
     * @param хрX хрY  левый верхний угол планшета хранилища
     * @param хрМасштаб пиксель печати хранилища в пикселях печати войск
     * @param войX войY то же для планшета войск
     * @param зданияH высота полосы зданий сверху
     */
    record Сцепка(double ширина, double высота, double хрX, double хрY,
                  double хрМасштаб, double войX, double войY, double зданияH) {
    }

    /**
     * РОСТ ЖЕТОНА ВОЕННОГО ЗДАНИЯ НАД ПЛАНШЕТОМ — по росту рамки Сборки.
     *
     * <p>Раньше здесь стояло число в пикселях печати, снятое со старого
     * планшета. Художник перерисовал планшет крупнее, и жетон рядом с ним стал
     * мелким. Рамка Сборки — напечатанная мера того же планшета, и она
     * меняется вместе с ним.
     */
    private static double зданиеH(int seat) {
        var cols = troopCols(seat);
        return cols.isEmpty() ? 215 : cols.get(0).bh();
    }

    /** Зазор между полосой зданий и кромкой планшета войск. */
    private static final double ЗДАНИЕ_ЗАЗОР = 18;

    private static final Map<BufferedImage, double[]> КРОМКИ =
        new java.util.WeakHashMap<>();

    /** Как сложены планшеты этого игрока ({@code null} — картинок нет). */
    static Сцепка сцепка(int seat) {
        BufferedImage вой = troopArt(seat);
        BufferedImage хр = storageArt(seat);
        if (вой == null || хр == null) {
            return null;
        }
        double[] выем = выемка(вой);
        double[] угол = уголХранилища(хр);
        // РАВНАЯ ВЫСОТА — а не равный пиксель: см. про замер в шапке записи.
        double f = вой.getHeight() / (double) хр.getHeight();
        double полоса = зданиеH(seat) + ЗДАНИЕ_ЗАЗОР;
        double войX = угол[0] * f - выем[0];
        double войY = полоса + угол[1] * f - выем[1];
        return new Сцепка(войX + вой.getWidth(),
            Math.max(полоса + хр.getHeight() * f, войY + вой.getHeight()),
            0, полоса, f, войX, войY, полоса);
    }

    /**
     * ВЕРШИНА ВЫЕМКИ на левой кромке планшета войск — самая правая точка левого
     * края в его средней части. Считается по НЕПРОЗРАЧНОСТИ картинки: форму
     * задаёт художник, и списать её числами в код значило бы завести второй
     * источник правды, который разойдётся с печатью при первой же перерисовке.
     */
    private static double[] выемка(BufferedImage art) {
        return КРОМКИ.computeIfAbsent(art, картинка -> {
            int w = картинка.getWidth();
            int h = картинка.getHeight();
            int предел = Math.max(4, w / 8);
            double лучшийX = 0;
            double лучшийY = h / 2.0;
            for (int y = h / 4; y < h * 3 / 4; y++) {
                for (int x = 0; x < предел; x++) {
                    if ((картинка.getRGB(x, y) >>> 24) > 16) {
                        if (x > лучшийX) {
                            лучшийX = x;
                            лучшийY = y;
                        }
                        break;
                    }
                }
            }
            return new double[]{лучшийX, лучшийY};
        });
    }

    /**
     * ГРАНИЦЫ КРАСКИ на картинке жетона: {@code [x, y, ширина, высота]}
     * непрозрачной части. По ним жетон и ставят на место — прозрачное поле
     * вокруг силуэта к размеру жетона отношения не имеет.
     */
    private static double[] краскаЖетона(BufferedImage art) {
        return КРОМКИ.computeIfAbsent(art, картинка -> {
            int w = картинка.getWidth();
            int h = картинка.getHeight();
            int x0 = w;
            int y0 = h;
            int x1 = -1;
            int y1 = -1;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((картинка.getRGB(x, y) >>> 24) > 16) {
                        if (x < x0) {
                            x0 = x;
                        }
                        if (x > x1) {
                            x1 = x;
                        }
                        if (y < y0) {
                            y0 = y;
                        }
                        if (y > y1) {
                            y1 = y;
                        }
                    }
                }
            }
            if (x1 < 0) {
                return new double[]{0, 0, w, h};
            }
            return new double[]{x0, y0, x1 - x0 + 1, y1 - y0 + 1};
        });
    }

    /** Вершина выступа планшета хранилища: середина его правой кромки. */
    private static double[] уголХранилища(BufferedImage art) {
        return КРОМКИ.computeIfAbsent(art, картинка -> {
            int w = картинка.getWidth();
            int h = картинка.getHeight();
            int первый = -1;
            int последний = -1;
            for (int y = 0; y < h; y++) {
                if ((картинка.getRGB(w - 1, y) >>> 24) > 16) {
                    if (первый < 0) {
                        первый = y;
                    }
                    последний = y;
                }
            }
            double cy = первый < 0 ? h / 2.0 : (первый + последний) / 2.0;
            return new double[]{w, cy};
        });
    }

    /**
     * ОБА ПЛАНШЕТА И ЖЕТОНЫ ВОЕННЫХ ЗДАНИЙ — одной сцепкой.
     *
     * @param k       масштаб: экранных точек на пиксель печати
     * @param вЗапасе коды военных зданий, чьи жетоны ещё лежат на планшете
     */
    static void paintPair(Graphics2D g, int x, int y, double k, Сцепка с,
                          ReplayRecord.Player p, kelium.core.TroopSide troop,
                          Map<String, char[]> fill, char[] base, Set<String> covered,
                          Set<String> вЗапасе, Map<String, int[]> запас,
                          Map<Rectangle, Object[]> spots,
                          Map<Rectangle, String> storeSpots) {
        BufferedImage хр = storageArt(p.seat);
        BufferedImage вой = troopArt(p.seat);
        if (с == null || хр == null || вой == null) {
            return;
        }
        paintStorage(g, (int) Math.round(x + с.хрX() * k), (int) Math.round(y + с.хрY() * k),
            (int) Math.round(хр.getWidth() * с.хрМасштаб() * k), p,
            fill, base, covered, storeSpots);
        int войX = (int) Math.round(x + с.войX() * k);
        int войY = (int) Math.round(y + с.войY() * k);
        paintTroop(g, войX, войY, (int) Math.round(вой.getWidth() * k), p, troop, spots);
        военныеЗдания(g, войX, войY, k, p, вЗапасе, spots);
        картыВПазах(g, войX, войY, k, p);
        // Блок запаса стоит В ТОЙ ЖЕ ПОЛОСЕ, что и жетоны военных зданий над
        // планшетом войск: она начинается у самого верха сцепки и кончается там,
        // где начинаются планшеты. Отсюда и «вровень по высоте».
        запасВойск(g, (int) Math.round(x + с.хрX() * k), y,
            (int) Math.round(хр.getWidth() * с.хрМасштаб() * k),
            (int) Math.round((с.зданияH() - ЗДАНИЕ_ЗАЗОР) * k), запас, p.seat);
    }

    /**
     * ЗАПАС ЖЕТОНОВ ВОЙСК — ЧЕТЫРЬМЯ СТОПКАМИ НАД ПЛАНШЕТОМ ХРАНИЛИЩА.
     *
     * <p>Заказ дизайнера 11.09.2026: убрать из-под планшета подписи «на поле 1 ·
     * запас 3» и показать САМ ЖЕТОН войска картинкой, а числом — только сколько
     * их в запасе. Четыре стопки во всю ширину зоны над хранилищем, ростом вровень
     * с жетонами военных зданий над планшетом войск: полоса одна и та же.
     *
     * <p>Запас кончился — жетон гасится прозрачностью: на столе это видно по
     * пустому месту, здесь по тусклой картинке.
     *
     * @param запас род → {@code [на поле, в запасе]}
     */
    private static void запасВойск(Graphics2D g, int x, int y, int width, int height,
                                   Map<String, int[]> запас, int seat) {
        if (запас == null || запас.isEmpty() || width <= 0 || height <= 0) {
            return;
        }
        String[] роды = {"infantry", "vehicle", "aircraft", "tower"};
        int colW = width / роды.length;
        for (int i = 0; i < роды.length; i++) {
            int[] c = запас.getOrDefault(роды[i], new int[]{0, 0});
            int вЗапасе = c[1];
            int всего = c[0] + c[1];
            int cx = x + i * colW + colW / 2;
            java.awt.Font шрифт = Theme.font(Math.max(9, height / 7), Font.BOLD);
            g.setFont(шрифт);
            int строка = g.getFontMetrics().getHeight();
            int картинкаH = Math.max(8, height - строка * 2 - 2);
            BufferedImage tex = Textures.unit(роды[i], seat);
            java.awt.Composite было = g.getComposite();
            if (вЗапасе == 0) {
                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.28f));
            }
            if (tex != null) {
                double доля = tex.getWidth() / (double) tex.getHeight();
                int th = картинкаH;
                int tw = (int) Math.round(th * доля);
                if (tw > colW - 4) {
                    tw = colW - 4;
                    th = (int) Math.round(tw / доля);
                }
                g.drawImage(tex, cx - tw / 2, y + (картинкаH - th) / 2, tw, th, null);
            }
            g.setComposite(было);
            g.setColor(вЗапасе == 0 ? Theme.ink3() : Theme.ink2());
            String имя = Names.unit(роды[i]);
            g.drawString(имя, cx - g.getFontMetrics().stringWidth(имя) / 2,
                y + картинкаH + строка - 2);
            String счёт = вЗапасе + " из " + всего;
            g.setFont(Theme.mono(Math.max(9, height / 7), Font.BOLD));
            g.setColor(вЗапасе == 0 ? Theme.ink3() : Theme.ink());
            g.drawString(счёт, cx - g.getFontMetrics().stringWidth(счёт) / 2,
                y + картинкаH + строка * 2 - 2);
        }
    }

    /**
     * КАРТЫ В ТРИ ПАЗА ВНИЗУ ПЛАНШЕТА ВОЙСК.
     *
     * <p>Заказ дизайнера 11.09.2026: «надо эксплуатировать три ячейки под карты
     * арсенала и карты контейнеров правильным образом, прямо непосредственно
     * размещая их там визуально» — и убрать текст «арсенал в руке / установлен /
     * контейнеров».
     *
     * <p>Паз занимают тремя способами, и у каждого своя рамка в маске:
     * УСТАНОВЛЕННАЯ карта арсенала вставлена в паз и видна лицом
     * ({@code arsenal_open}); карта В РУКЕ лежит рубашкой и торчит из паза
     * дальше ({@code arsenal_back}); КОНТЕЙНЕРЫ кладутся по два в паз
     * ({@code container}).
     *
     * <p>Порядок раскладки — как на столе: сперва установленные, потом то, что в
     * руке, потом контейнеры; каждая занятая ячейка выбывает.
     */
    private static void картыВПазах(Graphics2D g, int войX, int войY, double k,
                                    ReplayRecord.Player p) {
        var вставлено = BoardAnchors.cardSlots(цвет(p.seat), "arsenal_open");
        var рубашкой = BoardAnchors.cardSlots(цвет(p.seat), "arsenal_back");
        var подКонтейнер = BoardAnchors.cardSlots(цвет(p.seat), "container");
        if (вставлено.isEmpty()) {
            return;
        }
        int паз = 0;
        BufferedImage лицоНет = Textures.card("deck_arsenal", "deck");
        for (String id : p.arsenalInstalled) {
            if (паз >= вставлено.size()) {
                break;
            }
            картаВПаз(g, войX, войY, k, вставлено.get(паз), лицоНет);
            паз++;
        }
        for (int i = 0; i < p.arsenalHand.size() && паз < рубашкой.size(); i++) {
            картаВПаз(g, войX, войY, k, рубашкой.get(паз), лицоНет);
            паз++;
        }
        // КОНТЕЙНЕРЫ: по два в паз, поэтому рамок шесть — берём те, что
        // относятся к ещё свободным пазам.
        BufferedImage конт = Textures.card("deck_containers", "deck");
        int положено = 0;
        for (int i = паз * 2; i < подКонтейнер.size() && положено < p.containers; i++) {
            картаВПаз(g, войX, войY, k, подКонтейнер.get(i), конт);
            положено++;
        }
    }

    /**
     * ОДНА КАРТА В СВОЙ ПАЗ: рамка задана в пикселях печати планшета.
     *
     * <p>Карта НЕ РАСТЯГИВАЕТСЯ под рамку. Рамки нарисованы дизайнером в
     * натуральную величину карты, и у вставленной карты рамка НИЖЕ самой карты:
     * та наполовину ушла в паз. Поэтому карта кладётся во всю свою ширину,
     * прижатой верхом, и обрезается краем паза — ровно как торчит из кармана.
     */
    private static void картаВПаз(Graphics2D g, int войX, int войY, double k,
                                  int[] рамка, BufferedImage картинка) {
        Rectangle box = scale(войX, войY, k, рамка[0], рамка[1], рамка[2], рамка[3]);
        if (картинка != null) {
            int h = (int) Math.round(box.width
                * картинка.getHeight() / (double) картинка.getWidth());
            java.awt.Shape было = g.getClip();
            g.clip(box);
            g.drawImage(картинка, box.x, box.y, box.width, Math.max(h, box.height), null);
            g.setClip(было);
            return;
        }
        // Печати нет — рисуем саму карту: паз не должен выглядеть пустым, когда
        // в нём что-то лежит.
        g.setColor(Theme.tile());
        g.fill(new RoundRectangle2D.Double(box.x, box.y, box.width, box.height,
            box.width * 0.08, box.width * 0.08));
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(Math.max(1f, (float) (box.width * 0.02))));
        g.draw(new RoundRectangle2D.Double(box.x, box.y, box.width, box.height,
            box.width * 0.08, box.width * 0.08));
    }

    /**
     * ЖЕТОНЫ ВОЕННЫХ ЗДАНИЙ НАД ПЛАНШЕТОМ ВОЙСК — каждый над своей подписью.
     *
     * <p>Заказ дизайнера 09.09.2026: «военные здания надо располагать НАД
     * планшетом войск на соответствующих местах (подписано на планшете
     * сверху)». Прежде они шли отдельной группой «ВОЕННЫЕ ЗДАНИЯ» ниже и с
     * колонками своего рода войск не были связаны ничем, кроме порядка.
     *
     * <p>Жетон лежит, пока здание В ЗАПАСЕ. Построил — жетон уехал на поле, и
     * место над подписью пустует: там просто печать, ничего не подрисовываем.
     */
    private static void военныеЗдания(Graphics2D g, int войX, int войY, double k,
                                      ReplayRecord.Player p, Set<String> вЗапасе,
                                      Map<Rectangle, Object[]> spots) {
        if (вЗапасе == null || вЗапасе.isEmpty()) {
            return;
        }
        for (BoardAnchors.Column c : troopCols(p.seat)) {
            if (!вЗапасе.contains(c.building())) {
                continue;
            }
            жетонЗдания(g, войX + c.labelCx() * k, войY - ЗДАНИЕ_ЗАЗОР * k,
                зданиеH(p.seat) * k, c.building(), p.seat, spots);
        }
    }

    /**
     * ЖЕТОН ЗДАНИЯ НА СВОБОДНОМ МЕСТЕ: серединой по {@code cx}, нижней кромкой
     * по {@code низ}, ростом в {@code высота}. Под жетоном — торец картонки.
     */
    private static void жетонЗдания(Graphics2D g, double cx, double низ, double высота,
                                    String code, int seat,
                                    Map<Rectangle, Object[]> spots) {
        var найдено = Textures.found(code, 0, seat);
        if (найдено == null || найдено.image() == null) {
            return;
        }
        BufferedImage tex = найдено.image();
        // МЕРА — ПО КРАСКЕ, А НЕ ПО ФАЙЛУ. У картинок жетонов вокруг силуэта
        // остаётся прозрачное поле, и если считать по размеру файла, жетон
        // висит над кромкой планшета с зазором вместо того, чтобы лежать
        // вплотную.
        double[] краска = краскаЖетона(tex);
        double k = высота / краска[3];
        double ш = краска[2] * k;
        AffineTransform at = new AffineTransform();
        at.translate(cx - ш / 2 - краска[0] * k, низ - высота - краска[1] * k);
        at.scale(k, k);
        double d = Math.max(1.5, высота * 0.045);
        kelium.report.ТеньЖетона.блок(g,
            kelium.report.ТеньЖетона.силуэт(tex,
                kelium.report.ТеньЖетона.краска(Theme.seatStroke(seat))),
            at, d, d);
        g.drawImage(tex, at, null);
        if (spots != null) {
            spots.put(new Rectangle((int) Math.round(cx - ш / 2),
                    (int) Math.round(низ - высота),
                    (int) Math.round(ш), (int) Math.round(высота)),
                new Object[]{null, Boolean.FALSE,
                    kelium.gui.GameRecorder.buildingName(code)});
        }
    }

    // ==================== планшет хранилища ====================

    /**
     * ПЛАНШЕТ ХРАНИЛИЩА с кубиками в напечатанных ячейках.
     *
     * <p>{@code fill} — что лежит в ячейках каждого складского здания
     * («miner-3» → массив 'K'/'A'/'D'/0), {@code base} — две центральные ячейки,
     * открытые всегда, {@code covered} — здания, чей жетон лежит на планшете и
     * СВОИМИ БОКАМИ накрывает эти ячейки (значит, они не в игре).
     */
    static void paintStorage(Graphics2D g, int x, int y, int width, ReplayRecord.Player p,
                             Map<String, char[]> fill, char[] base, Set<String> covered,
                             Map<Rectangle, String> storeSpots) {
        BufferedImage art = storageArt(p.seat);
        if (art == null) {
            return;
        }
        double k = width / (double) art.getWidth();
        int h = (int) Math.round(art.getHeight() * k);
        g.drawImage(art, x, y, width, h, null);
        // Рамки ячеек КАЖДОГО складского здания: по ним ляжет сам жетон, если он
        // ещё на планшете (см. ниже, жетонПоверхЯчеек).
        Map<String, java.util.List<Rectangle>> зоны = new java.util.LinkedHashMap<>();
        int seen = 0;
        int lastLevel = -1;
        String lastGroup = "";
        for (BoardAnchors.Cell c : storageCells(p.seat)) {
            if (!c.group().equals(lastGroup) || c.level() != lastLevel) {
                lastGroup = c.group();
                lastLevel = c.level();
                seen = 0;
            }
            Rectangle box = scale(x, y, k, c.x(), c.y(), c.w(), c.h());
            char has;
            boolean open;
            if ("base".equals(c.group())) {
                open = true;
                has = base != null && seen < base.length ? base[seen] : 0;
            } else {
                String key = ("miner".equals(c.group()) ? "miner-" : "plant-") + c.level();
                open = !covered.contains(key);
                char[] arr = fill.get(key);
                has = open && arr != null && seen < arr.length ? arr[seen] : 0;
            }
            seen++;
            if (!open) {
                // ЯЧЕЙКА НАКРЫТА СВОИМ ЖЕТОНОМ — и жетон мы сейчас на неё и
                // положим, поэтому здесь только запоминаем рамки. Раньше на
                // месте накрытых ячеек стоял серый крестик, а сам жетон
                // рисовался ОТДЕЛЬНОЙ группой ниже: одно и то же здание было на
                // листе дважды (жалоба дизайнера 02.09.2026).
                String key = ("miner".equals(c.group()) ? "miner-" : "plant-") + c.level();
                зоны.computeIfAbsent(key, ключ -> new java.util.ArrayList<>()).add(box);
            } else if (has != 0) {
                cube(g, box, has);
            }
        }
        // КРЫЛЬЯ ПЛАНШЕТА: жетон занимает КРЫЛО целиком, а не только свои
        // ячейки, — так он и лежит на столе (пример дизайнера 09.09.2026).
        Rectangle лист = new Rectangle(x, y, width, h);
        java.util.List<Крыло> крылья = крылья(лист);
        for (var e : зоны.entrySet()) {
            жетонНаКрыле(g, e.getKey(), e.getValue(), p.seat, крылья);
        }
        жетоныХранилища(g, x, y, k, p, storeSpots);
    }

    /**
     * ДВА ЖЕТОНА ХРАНИЛИЩА — В НАПЕЧАТАННЫЕ МЕСТА.
     *
     * <p>Заказ дизайнера 09.09.2026: «жетоны модулей хранилища вставляются в
     * планшет хранилища» — и приложена маска с двумя большими квадратами со
     * значком склада слева и справа от середины. Прежде эти два жетона лежали
     * отдельной строкой под планшетом, то есть на столе им места не было вовсе.
     *
     * <p>Пустое место НЕ обводится: оно уже напечатано на планшете, и рисовать
     * поверх печати свою рамку нельзя.
     */
    private static void жетоныХранилища(Graphics2D g, int x, int y, double k,
                                        ReplayRecord.Player p,
                                        Map<Rectangle, String> storeSpots) {
        var места = BoardAnchors.stores(цвет(p.seat));
        if (места.isEmpty()) {
            места = BoardAnchors.stores("A");
        }
        for (int i = 0; i < места.size(); i++) {
            String tok = i < p.storageTokens.size() ? p.storageTokens.get(i) : null;
            if (tok == null) {
                continue;
            }
            int[] b = места.get(i);
            Rectangle box = scale(x, y, k, b[0], b[1], b[2], b[3]);
            int side = Math.min(box.width, box.height);
            int sx = box.x + (box.width - side) / 2;
            int sy = box.y + (box.height - side) / 2;
            ModuleSlot.paintStorageToken(g, tok, sx, sy, side);
            if (storeSpots != null) {
                storeSpots.put(new Rectangle(sx, sy, side, side), tok);
            }
        }
    }

    /** Уровень здания из ключа вида {@code miner-3}; {@code null} — без уровня. */
    private static Integer уровеньИз(String key) {
        int i = key.indexOf('-');
        if (i < 0) {
            return null;
        }
        try {
            return Integer.valueOf(key.substring(i + 1));
        } catch (NumberFormatException неЧисло) {
            return null;
        }
    }

    /**
     * Крыло планшета: куда и как ложится жетон одного уровня.
     *
     * @param крx     середина внешней кромки крыла
     * @param крy     то же по вертикали
     * @param нx      нормаль к кромке, направленная вглубь планшета
     * @param нy      то же по вертикали
     * @param угол    поворот жетона: широкой стороной к кромке
     * @param длина   длина кромки — сколько места вдоль
     * @param ширина  ширина жетона: почти вся кромка
     */
    private record Крыло(double крx, double крy, double нx, double нy, double угол,
                         double длина, double ширина) {
    }

    /**
     * ВОСЕМЬ КРЫЛЬЕВ ПЛАНШЕТА ХРАНИЛИЩА — по одному на каждое складское здание.
     *
     * <p>Печать разложена шестиугольником: у верхней и нижней кромок по два
     * крыла (добытчик слева, энергостанция справа), у каждой из четырёх
     * наклонных — по одному. Жетон лежит на своём крыле ЦЕЛИКОМ: накрывает и
     * ячейки, и подпись уровня, и достаёт почти до кромки — так эта картонка и
     * лежит на столе (пример дизайнера 09.09.2026).
     *
     * <p>Шестиугольник задан долями от прямоугольника картинки: печать одна на
     * все четыре цвета, и мерить её каждый раз незачем. Глубина крыла — полоса
     * от кромки до внутреннего шестиугольника, тоже снята с печати.
     */
    private static java.util.List<Крыло> крылья(Rectangle лист) {
        double[][] доли = {{0.1657, 0}, {0.8357, 0}, {1, 0.5},
            {0.8357, 1}, {0.1657, 1}, {0, 0.5}};
        double[][] в = new double[6][2];
        for (int i = 0; i < 6; i++) {
            в[i][0] = лист.getMinX() + доли[i][0] * лист.width;
            в[i][1] = лист.getMinY() + доли[i][1] * лист.height;
        }
        // РАЗМЕР ЖЕТОНА СЧИТАЕТСЯ ОТ ДЛИНЫ КРОМКИ, А НЕ ОТ ВЫСОТЫ ПЛАНШЕТА.
        //
        // Дизайнер прислал маску 09.09.2026: чёрное — планшет, красное — жетоны.
        // По ней видно главное: жетон занимает почти всю доступную кромку (её
        // половину там, где крыльев два), и тогда ОБА зазора — и сбоку, и до
        // внутренней черты печати — закрываются сами, потому что у картонки
        // ровно те же пропорции, что у крыла. Раньше я задавал глубину долей
        // высоты планшета, жетон выходил уже кромки, и зазор приходилось
        // «лечить» то сдвигом внутрь, то сдвигом к середине — от этого он
        // только переезжал с места на место.
        double вдоль = 0.96;
        double цx = лист.getCenterX();
        double цy = лист.getCenterY();
        java.util.List<Крыло> out = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double[] a = в[i];
            double[] b = в[(i + 1) % 6];
            // Верхняя и нижняя кромки держат ПО ДВА крыла, наклонные — по одному.
            int частей = Math.abs(a[1] - b[1]) < лист.height * 0.01 ? 2 : 1;
            for (int ч = 0; ч < частей; ч++) {
                double t0 = ч / (double) частей;
                double t1 = (ч + 1) / (double) частей;
                double x0 = a[0] + (b[0] - a[0]) * t0;
                double y0 = a[1] + (b[1] - a[1]) * t0;
                double x1 = a[0] + (b[0] - a[0]) * t1;
                double y1 = a[1] + (b[1] - a[1]) * t1;
                double сx = (x0 + x1) / 2;
                double сy = (y0 + y1) / 2;
                double длина = Math.hypot(x1 - x0, y1 - y0);
                // ВГЛУБЬ — ПО НОРМАЛИ К КРОМКЕ, А НЕ К СЕРЕДИНЕ ПЛАНШЕТА. С
                // направлением «на середину» жетон съезжал вдоль кромки: у
                // верхнего крыла на полсотни пикселей вправо, и печать из-под
                // него выглядывала (замечание дизайнера 09.09.2026: «жетоны
                // смещены и не на своих местах»). Нормаль ставит картонку ровно
                // в полосу своего крыла.
                double нx = -(y1 - y0) / длина;
                double нy = (x1 - x0) / длина;
                if (нx * (цx - сx) + нy * (цy - сy) < 0) {
                    нx = -нx;
                    нy = -нy;
                }
                double угол = Math.atan2(y1 - y0, x1 - x0);
                // ШИРОКАЯ СТОРОНА КАРТОНКИ — К КРОМКЕ. У жетона добытчика и
                // станции силуэт трапеции: длинное основание внизу картинки. На
                // столе оно лежит по кромке планшета, наружу, а узкий край
                // смотрит в середину. Поэтому поворот берётся на все 360°: если
                // после разворота основание смотрит внутрь, добавляем полоборота
                // (замечание дизайнера 09.09.2026: «они крутятся на все 360»).
                if (-Math.sin(угол) * -нx + Math.cos(угол) * -нy < 0) {
                    угол += Math.PI;
                }
                out.add(new Крыло(сx, сy, нx, нy, угол, длина, длина * вдоль));
            }
        }
        return out;
    }

    /**
     * ЖЕТОН СКЛАДСКОГО ЗДАНИЯ НА СВОЁМ КРЫЛЕ ПЛАНШЕТА.
     *
     * <p>Пока здание не построено, его жетон лежит на планшете и закрывает
     * собой всё крыло: и печатные ячейки, и подпись уровня. Крыло выбирается по
     * ячейкам — то, в которое они попали.
     *
     * <p>Размер — во всю полосу крыла: картонка достаёт почти до кромки, а по
     * длине садится с небольшими полями. Никаких подгонок под ячейки: они
     * оказываются под жетоном сами.
     */
    private static void жетонНаКрыле(Graphics2D g, String key,
                                     java.util.List<Rectangle> ячейки, int seat,
                                     java.util.List<Крыло> крылья) {
        if (ячейки.isEmpty() || крылья.isEmpty()) {
            return;
        }
        double cx = 0;
        double cy = 0;
        for (Rectangle r : ячейки) {
            cx += r.getCenterX();
            cy += r.getCenterY();
        }
        cx /= ячейки.size();
        cy /= ячейки.size();
        Крыло своё = крылья.get(0);
        double ближе = Double.MAX_VALUE;
        for (Крыло к : крылья) {
            // Сравниваем с СЕРЕДИНОЙ ПОЛОСЫ крыла: там и лежат его ячейки.
            double d = Math.hypot(к.крx() + к.нx() * к.ширина() / 4 - cx,
                к.крy() + к.нy() * к.ширина() / 4 - cy);
            if (d < ближе) {
                ближе = d;
                своё = к;
            }
        }

        String code = key.startsWith("miner") ? "miner" : "power_plant";
        int уровень = уровеньИз(key);
        var найдено = Textures.found(code, уровень, seat);
        if (найдено != null && найдено.image() != null) {
            BufferedImage tex = найдено.image();
            double k = масштабВКрыло(tex.getWidth(), tex.getHeight(), своё);
            double[] c = центрВКрыле(своё, tex.getHeight() * k / УМЕНЬШЕНИЕ);
            AffineTransform at = new AffineTransform();
            at.translate(c[0], c[1]);
            at.rotate(своё.угол());
            at.scale(k, k);
            at.translate(-tex.getWidth() / 2.0, -tex.getHeight() / 2.0);
            // ТОЛЩИНА КАРТОНКИ, А НЕ ТЕНЬ ПОД НЕЙ. Прежде под жетон клалась
            // полупрозрачная чёрная копия со сдвигом — дизайнер забраковал
            // (09.09.2026): «тень не протягивается вектором от крайней точки
            // текстуры, получается плоская картинка на фоне плоской тени».
            // Теперь силуэт протягивается вдоль вектора и красится в цвет
            // обводки места: это виден торец картонки.
            double d = Math.max(1.5, k * 8);
            kelium.report.ТеньЖетона.блок(g,
                kelium.report.ТеньЖетона.силуэт(tex,
                    kelium.report.ТеньЖетона.краска(Theme.seatStroke(seat))),
                at, d, d);
            g.drawImage(tex, at, null);
            return;
        }
        kelium.report.FieldGeometry.Shape sh;
        try {
            sh = kelium.report.FieldGeometry.buildingByCode(code);
        } catch (RuntimeException e) {
            return;
        }
        double k = масштабВКрыло(sh.vbW(), sh.vbH(), своё);
        double[] c = центрВКрыле(своё, sh.vbH() * k / УМЕНЬШЕНИЕ);
        AffineTransform at = new AffineTransform();
        at.translate(c[0], c[1]);
        at.rotate(своё.угол());
        at.scale(k, k);
        at.translate(-sh.vbW() / 2.0, -sh.vbH() / 2.0);
        java.awt.Shape path = at.createTransformedShape(sh.path());
        double торец = Math.max(1.5, k * 6);
        g.setColor(Theme.seatStroke(seat));
        kelium.report.ТеньЖетона.блок(g, path, торец, торец);
        g.setColor(Theme.seat(seat));
        g.fill(path);
        g.setColor(Theme.seatStroke(seat));
        g.setStroke(new BasicStroke(Math.max(1.2f, (float) (k * 3))));
        g.draw(path);
    }

    /**
     * МАСШТАБ ЖЕТОНА ПОД КРЫЛО: во всю глубину полосы, если по длине влезает.
     * Полей по длине оставляем чуть — картонка не упирается в соседнее крыло.
     */
    private static double масштабВКрыло(double texW, double texH, Крыло крыло) {
        return крыло.ширина() * УМЕНЬШЕНИЕ / texW;
    }

    /**
     * НА СТОЛЬКО ЖЕТОН МЕНЬШЕ СВОЕГО КРЫЛА. Ровно во всю кромку он ложился чуть
     * крупновато (замечание дизайнера 09.09.2026: «получилось почти идеально, но
     * слегка большеватые, уменьши от центра на 5%»). Уменьшение идёт ОТ СЕРЕДИНЫ
     * жетона: место, куда он лёг, не меняется, вокруг него просто появляется
     * тонкий воздух.
     */
    private static final double УМЕНЬШЕНИЕ = 0.95;

    /**
     * СЕРЕДИНА ЖЕТОНА В КРЫЛЕ: широкой стороной вплотную к кромке планшета.
     *
     * @param высота глубина жетона после масштабирования
     */
    private static double[] центрВКрыле(Крыло крыло, double высота) {
        double вглубь = высота / 2;
        return new double[]{крыло.крx() + крыло.нx() * вглубь,
            крыло.крy() + крыло.нy() * вглубь};
    }

    /** Кубик ресурса в напечатанной ячейке: тем же значком, что и везде. */
    private static void cube(Graphics2D g, Rectangle box, char has) {
        // КУБИК КРУПНЕЕ НА ПЯТУЮ (заказ дизайнера 09.09.2026): в печатной ячейке
        // он сидел мелко, и ячейка читалась как пустая рамка со значком внутри.
        double s = Math.min(box.width, box.height) * 0.79;
        double cx = box.x + box.width / 2.0;
        double cy = box.y + box.height / 2.0;
        // БЕЛОГО КРУЖКА ПОД КУБИКОМ НЕТ (замечание дизайнера 09.09.2026: «что
        // за кружочек под кубиком?»). Он подкладывался под плоский значок, чтобы
        // тот читался на печати; объёмному кубику подложка не нужна — он и так
        // виден, а кружок выглядел как ещё одна деталь печати.
        MarkIcons.paint(g, switch (has) {
            case 'K' -> "KELIUM";
            case 'D' -> "TROPHY";
            default -> "AMMO";
        }, cx, cy, s, switch (has) {
            case 'K' -> Theme.kelium();
            case 'D' -> Theme.trophy();
            default -> Theme.ink2();
        });
    }

    // ==================== мелочи рисования ====================

    private static Rectangle scale(int x, int y, double k, int bx, int by, int bw, int bh) {
        return new Rectangle(x + (int) Math.round(bx * k), y + (int) Math.round(by * k),
            (int) Math.round(bw * k), (int) Math.round(bh * k));
    }

    /** Лёгкая подложка под жетон: печать под ним всё равно не читается. */
    private static void shade(Graphics2D g, Rectangle box) {
        g.setColor(Theme.alpha(Theme.paper(), 0.72));
        g.fill(new RoundRectangle2D.Double(box.x, box.y, box.width, box.height,
            box.width * 0.16, box.width * 0.16));
    }

    /** Узкая плашка с подписью у нижней кромки рамки. */
    private static void chip(Graphics2D g, Rectangle box, String text, Color colour) {
        g.setFont(Theme.font(Math.max(8, (int) (box.height * 0.13)), Font.BOLD));
        var fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        int pad = Math.max(3, box.width / 28);
        int w = Math.min(box.width - pad * 2, tw + pad * 3);
        int h = fm.getHeight() + pad;
        int cx = box.x + (box.width - w) / 2;
        int cy = box.y + box.height - h - pad;
        g.setColor(Theme.alpha(Theme.paper(), 0.92));
        g.fill(new RoundRectangle2D.Double(cx, cy, w, h, h * 0.5, h * 0.5));
        g.setColor(Theme.alpha(colour, 0.75));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Double(cx, cy, w, h, h * 0.5, h * 0.5));
        g.setColor(colour);
        g.drawString(text, cx + (w - tw) / 2, cy + h - pad / 2 - fm.getDescent());
    }

    private static String targetName(kelium.core.Target t) {
        return switch (t) {
            case INFANTRY -> "пехота";
            case VEHICLE -> "техника";
            case AIRCRAFT -> "авиация";
            case BUILDINGS_TOWERS -> "здания";
        };
    }
}
