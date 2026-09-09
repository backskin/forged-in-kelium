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
            // Жетон НАКРЫВАЕТ напечатанную цель целиком — так он и лежит на столе.
            int side = (int) Math.round(Math.min(box.width, box.height) * 0.62);
            int sx = box.x + (box.width - side) / 2;
            int sy = box.y + (box.height - side) / 2;
            shade(g, box);
            ModuleSlot.paint(g, m, ModuleSlot.red(), sx, sy, side);
            spots.put(new Rectangle(sx, sy, side, side),
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
        int side = (int) Math.round(Math.min(box.width, box.height) * 0.74);
        int sx = box.x + (box.width - side) / 2;
        int sy = box.y + (box.height - side) / 2;
        shade(g, box);
        ModuleSlot.paint(g, m, ModuleSlot.blue(), sx, sy, side);
        spots.put(new Rectangle(sx, sy, side, side),
            new Object[]{m, Boolean.FALSE,
                kelium.gui.GameRecorder.buildingName(c.building())});
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
                             Map<String, char[]> fill, char[] base, Set<String> covered) {
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
            java.awt.Composite было = g.getComposite();
            g.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, 0.30f));
            g.setColor(java.awt.Color.BLACK);
            AffineTransform тень = new AffineTransform(at);
            тень.preConcatenate(AffineTransform.getTranslateInstance(
                Math.max(1.5, k * 8), Math.max(1.5, k * 8)));
            g.drawImage(tex, тень, null);
            g.setComposite(было);
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
        AffineTransform тень = new AffineTransform();
        тень.translate(Math.max(1.5, k * 6), Math.max(1.5, k * 6));
        g.setColor(Theme.alpha(java.awt.Color.BLACK, 0.28));
        g.fill(тень.createTransformedShape(path));
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
