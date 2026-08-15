package kelium.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import kelium.dataio.ContentLibrary;
import kelium.report.ReplayRecord;
import kelium.rules.Ruleset;

/**
 * ПЛАНШЕТ НАУКИ И ПЛАНШЕТ РЫНКА — вторая вкладка проигрывателя (заказ дизайнера
 * 12.08.2026). Слева три трека науки с фишками игроков и открытыми картами
 * супер-арсенала на вершинах, справа — активная карта рынка со своими двумя
 * предложениями и ячейками под них плюс печатные обмены планшета.
 *
 * <p>Всё, что рисуется, берётся ИЗ ЗАПИСИ ПАРТИИ и из данных правил: шаги,
 * стоимости, ёмкости и призы — из ruleset, названия карт — из карточных наборов.
 * Ничего не додумывается: чего в записи нет, то честно подписано как неизвестное.
 */
public final class BoardsPanel extends JPanel implements javax.swing.Scrollable {

    private static final long serialVersionUID = 1L;

    /** Порядок треков как на планшете: красный, зелёный, синий. */
    private static final String[] TRACKS = {"left", "middle", "right"};
    private static final String[] TRACK_RU = {"красный", "зелёный", "синий"};
    /**
     * Цвет кубика КЕЛЕМИЯ — КЛАССИЧЕСКИЙ ЗЕЛЁНЫЙ. Был бирюзовый, а келемий на
     * поле зелёный, и на планшете рынка он выглядел другим ресурсом (замечание
     * дизайнера 13.08.2026).
     */
    private static final Color KELIUM_CUBE = new Color(0x2E, 0x9E, 0x44);
    private static final Color[] TRACK_COLOR = {
        new Color(0xC0, 0x39, 0x2B), new Color(0x27, 0x8B, 0x3E), new Color(0x2C, 0x62, 0xA8)};

    private Ruleset ruleset;
    private ContentLibrary content;
    private ReplayRecord record;
    private ReplayRecord.Snapshot snap;

    /**
     * РАСЧЁТНЫЙ РАЗМЕР ВЁРСТКИ. Планшет рисуется по абсолютным координатам, и
     * меньше этого его строки начинают наезжать друг на друга: в окне панели
     * доставалось 1440×230, и «шаг 3» садился поверх стоимости и поверх
     * пояснения. Поэтому размер объявлен честно, а панель живёт в прокрутке
     * (см. {@code ReplayGui.scrolled}): мало места — прокручиваем, много —
     * растягиваемся.
     */
    // РАЗМЕР ПОДНЯТ ПОД КРУПНЫЙ НАБОР (13.08.2026). Кегль на планшетах вырос
    // примерно в полтора раза, и в прежние 900×660 строки обменов и подписи призов
    // перестали помещаться: они налезали друг на друга и уезжали за рамку. Эти
    // числа — та ширина и высота, при которых вёрстка заведомо целая; всё, что
    // меньше, панель отдаёт прокрутке, а не сжимает вёрстку до наложений.
    private static final int DESIGN_W = 1180;
    private static final int DESIGN_H = 780;

    public BoardsPanel() {
        setBackground(kelium.gui.replay2.Theme.bg());
        setPreferredSize(new Dimension(DESIGN_W, DESIGN_H));
        setMinimumSize(new Dimension(DESIGN_W, DESIGN_H));
    }

    // ---- Scrollable: растягиваться, когда место есть, и прокручиваться, когда нет ----
    @Override public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override public int getScrollableUnitIncrement(java.awt.Rectangle visible,
                                                    int orientation, int direction) {
        return 24;
    }

    @Override public int getScrollableBlockIncrement(java.awt.Rectangle visible,
                                                     int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL
            ? visible.height - 24 : visible.width - 24;
    }

    @Override public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof javax.swing.JViewport v && v.getWidth() >= DESIGN_W;
    }

    @Override public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof javax.swing.JViewport v && v.getHeight() >= DESIGN_H;
    }

    /** Подключить правила и карточные наборы той версии, в которой сыграна партия. */
    public void setRules(Ruleset ruleset, ContentLibrary content) {
        this.ruleset = ruleset;
        this.content = content;
        repaint();
    }

    /** Показать состояние на выбранном кадре. */
    public void show(ReplayRecord rec, ReplayRecord.Snapshot s) {
        this.record = rec;
        this.snap = s;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (snap == null) {
            g.setColor(ink3());
            g.drawString("партия не загружена", 16, 28);
            return;
        }
        int w = getWidth();
        int h = getHeight();
        int split = Math.max(360, (int) (w * 0.58));
        paintScience(g, 12, 12, split - 24, h - 24);
        paintMarket(g, split, 12, w - split - 12, h - 24);
    }

    // ==================== ЦВЕТА ПЛАНШЕТОВ ====================
    //
    // ПЛАНШЕТЫ ЖИВУТ В ТЕМЕ ПРИЛОЖЕНИЯ. Раньше вся эта вкладка была нарисована
    // жёстко: белая бумага, серые чернила, светло-серые строки — и в тёмной теме
    // посреди тёмного окна светилось белое пятно (замечание дизайнера 14.08.2026).
    // Теперь бумага, чернила и линии берутся из темы, а СМЫСЛОВЫЕ цвета — цвета
    // треков, келемий, монета, боеприпас — остаются как есть: они опознавательные,
    // такие же на столе, и от темы зависеть не должны.

    static Color paper() {
        return kelium.gui.replay2.Theme.panel();
    }

    static Color row() {
        return kelium.gui.replay2.Theme.tile();
    }

    static Color emptyCell() {
        return kelium.gui.replay2.Theme.alpha(kelium.gui.replay2.Theme.ink3(), 0.15);
    }

    static Color ink() {
        return kelium.gui.replay2.Theme.ink();
    }

    static Color ink2() {
        return kelium.gui.replay2.Theme.ink2();
    }

    static Color ink3() {
        return kelium.gui.replay2.Theme.ink3();
    }

    static Color line() {
        return kelium.gui.replay2.Theme.border();
    }

    /**
     * ПОДКРАШЕННАЯ БУМАГА КАРТЫ: на светлой теме — бледный оттенок акцента, на
     * тёмной — он же, но подмешанный в тёмную панель. Печатать белую карточку на
     * тёмном фоне нельзя, а оставлять её вовсе без оттенка — потеряется, какого
     * рода эта карта.
     */
    static Color wash(Color accent) {
        return kelium.gui.replay2.Theme.isDark()
            ? mix(paper(), accent, 0.20)
            : mix(Color.WHITE, accent, 0.07);
    }

    static Color mix(Color a, Color b, double k) {
        return new Color(
            (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
            (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }

    // ==================== НАБОР ТЕКСТА НА ПЛАНШЕТАХ ====================
    //
    // ВЕСЬ ТЕКСТ ЗДЕСЬ КРУПНЫЙ. Планшеты рисовались кеглем 9–11 в клетках высотой
    // под сотню пикселей: места полно, а прочесть нечего (замечание дизайнера
    // 13.08.2026 — «сделать прям очень видными»). Поэтому размер не задаётся
    // числом на месте, а проходит через эти три метода:
    //   bold  — заголовки и всё, что должно бросаться в глаза;
    //   plain — обычные строки;
    //   note  — пояснения, самым лёгким начертанием: рядом с полужирным заголовком
    //           абзац не должен спорить с ним по весу.
    // МЕЛКОГО НЕТ ВОВСЕ: любой размер поднимается минимум до TEXT_MIN, поэтому
    // прежние 9,5 и 10 превращаются в такой же крупный текст, как остальное.

    private static final float TEXT_K = 1.55f;
    private static final float TEXT_MIN = 15f;

    private static float sz(double pt) {
        return (float) Math.max(TEXT_MIN, pt * TEXT_K);
    }

    private Font bold(double pt) {
        return kelium.gui.replay2.Theme.font(Math.round(sz(pt)), Font.BOLD);
    }

    private Font plain(double pt) {
        return kelium.gui.replay2.Theme.font(Math.round(sz(pt)), Font.PLAIN);
    }

    private Font note(double pt) {
        return kelium.gui.replay2.Theme.note(Math.round(sz(pt)));
    }

    // ==================== ПЛАНШЕТ НАУКИ ====================

    private void paintScience(Graphics2D g, int x, int y, int w, int h) {
        frame(g, x, y, w, h, "ПЛАНШЕТ НАУКИ");

        int[] cost = ints("tech.step_cost_trophy", new int[]{1, 2, 3, 4});
        int[] vp = ints("tech.step_vp_cumulative", new int[]{1, 1, 2, 3});
        int[] cap = ints("tech.step_capacity", new int[]{3, 2, 2, 1});
        int steps = Math.max(1, Math.min(cost.length, Math.min(vp.length, cap.length)));
        int players = record == null ? snap.players.size() : record.players;

        int colW = (w - 40) / 3;
        int cardH = 112;
        int top = y + 44;
        int gridTop = top + cardH + 8;
        // ПАМЯТКА ПО ОБМЕНАМ НАУЧНОГО ОТДЕЛА занимает нижнюю полосу планшета:
        // это ПОСТОЯННЫЕ обмены трофеев, и на планшете их не было вовсе
        // (замечание дизайнера 13.08.2026).
        int exchH = 84 + 27 * scienceRates().size();
        // Строк на одну больше числа шагов: снизу СТАРТОВАЯ ЗОНА (шаг 0), где
        // стоят кубики игроков до первого шага (уточнение дизайнера 12.08.2026).
        // Полоса под треками (86) держит приз первого шага в ДВЕ строки и подпись
        // под ними: прежних 46 хватало на один мелкий ряд, а теперь их два крупных.
        int rowH = Math.max(30, (h - (gridTop - y) - 72 - exchH) / (steps + 1));

        for (int t = 0; t < 3; t++) {
            int cx = x + 20 + t * colW;
            int cw = colW - 12;
            // ---- открытая карта супер-арсенала на вершине трека ----
            String saId = snap.superArsenalOffer.get(TRACKS[t]);
            paintSuperArsenalCard(g, cx, top, cw, cardH, saId, TRACK_COLOR[t],
                peakHolder(TRACKS[t], steps));

            // ---- шаги сверху вниз: вершина (шаг steps) наверху ----
            for (int i = steps - 1; i >= 0; i--) {
                int row = steps - 1 - i;
                int ry = gridTop + row * rowH;
                int step = i + 1;
                g.setColor(row());
                g.fillRoundRect(cx, ry, cw, rowH - 6, 8, 8);
                g.setColor(TRACK_COLOR[t]);
                g.setStroke(new BasicStroke(step == steps ? 2.4f : 1.2f));
                g.drawRoundRect(cx, ry, cw, rowH - 6, 8, 8);

                g.setFont(bold(11));
                g.setColor(ink());
                g.drawString("шаг " + step + (step == steps ? " · вершина" : ""), cx + 10, ry + 22);
                g.setFont(plain(10));
                g.setColor(ink2());
                g.drawString(cost[i] + " ТРФ", cx + 10, ry + 44);
                // ЗОЛОТАЯ ЗВЕЗДА С ПЛЮСОМ — сколько ПО даёт шаг (просьба
                // дизайнера 12.08.2026): +1, +1, +2, +3 по шагам.
                star(g, cx + 80, ry + 39, 11.0, vp[i]);

                // ---- ЯЧЕЙКИ ШАГА: их число ограничено, часть открыта только на
                // большом составе. В открытой ячейке стоит ОБЪЁМНЫЙ КУБИК цвета
                // игрока; в закрытой — еле видная пометка «4И» / «3+И».
                List<Integer> here = seatsOnStep(TRACKS[t], step);
                List<Integer> mins = cellMins(i, cap[i]);
                int cube = Math.min(26, rowH - 14);
                int slot = cube + 6;
                int px = cx + cw - 8 - mins.size() * slot;
                for (int cell = 0; cell < mins.size(); cell++) {
                    int sx = px + cell * slot;
                    int sy = ry + (rowH - 6 - cube) / 2;
                    boolean open = players >= mins.get(cell);
                    cellFrame(g, sx, sy, cube, open);
                    if (!open) {
                        // еле видная подпись: с какого состава ячейка открывается
                        g.setFont(getFont().deriveFont(Font.BOLD, cube * 0.42f));
                        g.setColor(new Color(0x88, 0x88, 0x88, 90));
                        String tag = mins.get(cell) >= 4 ? "4И" : "3+И";
                        int tw = g.getFontMetrics().stringWidth(tag);
                        g.drawString(tag, sx + (cube - tw) / 2, sy + cube / 2 + 4);
                    } else if (cell < here.size()) {
                        cube(g, sx + 2, sy + 2, cube - 4, FieldView.seatColor(here.get(cell)));
                    }
                }
            }

            // ---- СТАРТОВАЯ ЗОНА (шаг 0): кубики, ещё не пошедшие по треку ----
            int sy0 = gridTop + steps * rowH;
            g.setColor(row());
            g.fillRoundRect(cx, sy0, cw, rowH - 6, 8, 8);
            g.setColor(line());
            g.setStroke(new BasicStroke(1.0f));
            g.drawRoundRect(cx, sy0, cw, rowH - 6, 8, 8);
            g.setFont(bold(10.5));
            g.setColor(ink3());
            g.drawString("старт", cx + 10, sy0 + 22);
            List<Integer> atStart = seatsAtStart(TRACKS[t]);
            int cube0 = Math.min(18, rowH - 12);
            int px0 = cx + cw - 8 - atStart.size() * (cube0 + 4);
            for (int seat : atStart) {
                cube(g, px0, sy0 + (rowH - 6 - cube0) / 2, cube0, FieldView.seatColor(seat));
                px0 += cube0 + 4;
            }
            // ---- призы первого шага ----
            // ПРИЗ ПЕРВОГО ШАГА — ВНУТРИ СВОЕЙ КОЛОНКИ, двумя строками. Одной
            // строкой с названием цвета он вылезал в соседнюю колонку: кегль
            // вырос вдвое, а ширина колонки осталась прежней.
            g.setFont(plain(10));
            g.setColor(ink2());
            int py = y + h - exchH - 52;
            String prize = prizeText(TRACKS[t]);
            int cut = prize.indexOf(", 2-му");
            if (cut > 0) {
                g.drawString(clip(g, prize.substring(0, cut), cw), cx + 2, py);
                g.drawString(clip(g, prize.substring(cut + 2), cw), cx + 2, py + 20);
            } else {
                g.drawString(clip(g, prize, cw), cx + 2, py);
            }
        }
        g.setFont(note(10));
        g.setColor(ink3());
        // Строка КОРОЧЕ прежней: со сменой шрифта на Tektur (он шире системного)
        // прежняя не влезала в рамку планшета и обрезалась на правом краю.
        g.drawString(clip(g, "кубик на трек ОДИН · пунктирная ячейка — "
            + "только на большом составе", w - 40), x + 20, y + h - exchH - 12);

        // ---- ПОСТОЯННЫЕ ОБМЕНЫ НАУЧНОГО ОТДЕЛА ----
        int ey = y + h - exchH + 30;
        // Заголовок и уточнение — ДВУМЯ строками: одной строкой уточнение
        // обрезалось правой рамкой планшета, как только окно чуть уже.
        g.setFont(bold(11));
        g.setColor(ink());
        g.drawString("ПОСТОЯННЫЕ ОБМЕНЫ НАУЧНОГО ОТДЕЛА", x + 20, ey);
        g.setFont(note(10));
        g.setColor(ink3());
        g.drawString("каждый — не больше 1 раза за действие", x + 20, ey + 20);
        ey += 22;
        for (Object[] parts : scienceRates()) {
            ey += 27;
            line(g, x + 20, ey, sz(11), parts);
        }
    }

    /** Постоянные обмены трофеев из правил ({@code tech.science_exchanges}). */
    private List<Object[]> scienceRates() {
        List<Object[]> out = new ArrayList<>();
        // та же скидка за пару, что и на рынке, и так же — ОДНОЙ строкой с курсом
        int pair = intRule("tech.pair_bonus_coin");
        Object o = rget("tech.science_exchanges", null);
        if (o instanceof List<?> list) {
            for (Object e : list) {
                if (e instanceof Map<?, ?> m) {
                    if (pair > 0 && "trophy_to_coin".equals(String.valueOf(m.get("id")))) {
                        out.add(new Object[]{"1 / 2", "@trophy", "трофея   →",
                            "1 / " + (2 + pair), "@coin", "монеты"});
                    } else {
                        out.add(scienceParts(m));
                    }
                }
            }
        }
        if (out.isEmpty()) {
            out.add(new Object[]{"обмены научного отдела не заданы в правилах"});
        }
        return out;
    }

    private static Object[] scienceParts(Map<?, ?> m) {
        String give = amount(m.get("give_trophy"));
        return switch (String.valueOf(m.get("id"))) {
            // ТОЛЬКО ОДИНОЧНЫЙ КУРС. В данных он записан диапазоном («1–2 трофея →
            // 1–2 монеты»), и на планшете это прямо противоречило скидке за пару,
            // которая идёт отдельной строкой ниже: там за два трофея дают три
            // монеты, а не две (замечание дизайнера 13.08.2026). Диапазон убран,
            // пара живёт своей строкой.
            case "trophy_to_coin" -> new Object[]{first(m.get("give_trophy")), "@trophy",
                "трофей   →", first(m.get("get_coin")), "@coin", "монета"};
            case "move_module" -> new Object[]{give, "@trophy", "трофей   →", "@module",
                "переставить свой модуль на планшете"};
            case "draw_arsenal" -> new Object[]{give, "@trophy", "трофея   →", "@card",
                "взять 2 карты арсенала, оставить 1"};
            case "gild_module" -> new Object[]{give, "@trophy", "трофея   →", "@module",
                "позолотить модуль"};
            default -> new Object[]{give, "@trophy", "трофея   →", "обмен не описан"};
        };
    }

    /**
     * Кто стоит на этом шаге, В ПОРЯДКЕ ПРИХОДА. Порядок берём из записи
     * ({@code techOccupancy}) — он важен: приз шага 1 достаётся первому полностью,
     * второму вполовину. Старые записи такого поля не имеют, тогда падаем на
     * прежний способ «у кого нужный шаг», где порядок неизвестен.
     */
    private List<Integer> seatsOnStep(String track, int step) {
        List<List<Integer>> occ = snap.techOccupancy.get(track);
        if (occ != null && step - 1 < occ.size()) {
            return new ArrayList<>(occ.get(step - 1));
        }
        List<Integer> out = new ArrayList<>();
        for (ReplayRecord.Player p : snap.players) {
            if (p.tech.getOrDefault(track, 0) == step) {
                out.add(p.seat);
            }
        }
        return out;
    }

    /** Чьи кубики ещё в стартовой зоне трека (шаг 0). */
    private List<Integer> seatsAtStart(String track) {
        List<Integer> out = new ArrayList<>();
        for (ReplayRecord.Player p : snap.players) {
            if (p.tech.getOrDefault(track, 0) <= 0) {
                out.add(p.seat);
            }
        }
        return out;
    }

    /**
     * Минимальный состав для каждой ячейки шага. Берётся из правил
     * ({@code tech.step_cells}); нет такого ключа — все ячейки считаем открытыми
     * с двух игроков, чтобы старые версии правил рисовались как прежде.
     */
    private List<Integer> cellMins(int stepIndex, int fallbackCount) {
        Object raw = rget("tech.step_cells", null);
        if (raw instanceof List<?> steps && stepIndex < steps.size()
                && steps.get(stepIndex) instanceof List<?> cells) {
            List<Integer> out = new ArrayList<>();
            for (Object o : cells) {
                out.add(o instanceof Number n ? n.intValue() : 2);
            }
            return out;
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < Math.max(1, fallbackCount); i++) {
            out.add(2);
        }
        return out;
    }

    /** Рамка ячейки: открытая — сплошная, закрытая — пунктир и бледнее. */
    private void cellFrame(Graphics2D g, int x, int y, int size, boolean open) {
        g.setColor(open ? new Color(0xFF, 0xFF, 0xFF) : emptyCell());
        g.fillRoundRect(x, y, size, size, 4, 4);
        g.setColor(open ? ink2() : line());
        g.setStroke(open ? new BasicStroke(1.4f)
            : new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{3f, 3f}, 0f));
        g.drawRoundRect(x, y, size, size, 4, 4);
    }

    /**
     * ОБЪЁМНЫЙ КУБИК — как настоящий кубик на планшете: лицевая грань, светлая
     * верхняя и тёмная боковая. Так фишки читаются объёмными, а не плоскими
     * кружками (просьба дизайнера 12.08.2026).
     */
    static void cube(Graphics2D g, int x, int y, int size, Color colour) {
        int d = Math.max(3, size / 4);      // глубина «объёма»
        int face = size - d;
        // верхняя грань
        g.setColor(colour.brighter());
        g.fillPolygon(new int[]{x, x + d, x + size, x + face},
            new int[]{y + d, y, y, y + d}, 4);
        // правая боковая
        g.setColor(colour.darker());
        g.fillPolygon(new int[]{x + face, x + size, x + size, x + face},
            new int[]{y + d, y, y + face, y + size}, 4);
        // лицо
        g.setColor(colour);
        g.fillRect(x, y + d, face, face);
        g.setColor(new Color(0x22, 0x22, 0x22, 160));
        g.setStroke(new BasicStroke(1.0f));
        g.drawRect(x, y + d, face, face);
    }

    // ==================== ИКОНКИ РЕСУРСОВ ====================
    //
    // Обмены на планшетах раньше были строчками из внутренних ключей
    // («kelium_to_ammo: per_kelium_ammo 2») — дизайнеру это ребус. Теперь каждая
    // строка обмена собирается из СЛОВ и ЗНАЧКОВ: что отдал — что получил
    // (просьба дизайнера 13.08.2026).

    private static final Color COIN_FACE = new Color(0xE8, 0xB3, 0x2A);
    private static final Color AMMO_FACE = new Color(0x6E, 0x78, 0x86);
    /** Подложка значка боеприпаса — красный квадрат, патрон на нём чёрный. */
    private static final Color AMMO_TILE = new Color(0xD1, 0x2B, 0x2B);
    private static final Color TROPHY_FACE = new Color(0x8C, 0x3B, 0x86);
    private static final Color ENERGY_FACE = new Color(0xFF, 0xC4, 0x00);
    private static final Color MODULE_FACE = new Color(0xC8, 0x3B, 0x3B);

    /**
     * СТРОКА ИЗ СЛОВ И ЗНАЧКОВ. Кусок, начинающийся с {@code @}, рисуется
     * иконкой; остальное — обычный текст. Возвращает правый край строки.
     */
    private int line(Graphics2D g, int x, int baseline, float size, Object... parts) {
        int cur = x;
        int d = Math.round(size * 1.15f);
        for (Object part : parts) {
            String s = String.valueOf(part);
            if (s.startsWith("@")) {
                icon(g, s.substring(1), cur, baseline - d + Math.round(size * 0.20f), d);
                cur += d + 4;
            } else {
                g.setFont(kelium.gui.replay2.Theme.font(Math.round(size), Font.PLAIN));
                g.setColor(ink());
                g.drawString(s, cur, baseline);
                cur += g.getFontMetrics().stringWidth(s) + 6;
            }
        }
        return cur;
    }

    /** Значок ресурса размером {@code d} с левым верхним углом в (x, y). */
    private void icon(Graphics2D g, String kind, int x, int y, int d) {
        switch (kind) {
            case "kelium" -> cube(g, x, y, d, KELIUM_CUBE);
            case "energy" -> cube(g, x, y, d, ENERGY_FACE);
            case "module" -> cube(g, x, y, d, MODULE_FACE);
            case "coin" -> {
                g.setColor(COIN_FACE);
                g.fillOval(x, y, d, d);
                g.setColor(COIN_FACE.darker());
                g.setStroke(new BasicStroke(1.4f));
                g.drawOval(x, y, d, d);
                g.drawOval(x + d / 4, y + d / 4, d / 2, d / 2);
            }
            case "ammo" -> {
                // ЧЁРНЫЙ ПАТРОН НА КРАСНОМ КВАДРАТЕ — как и в зоне игрока
                // (просьба дизайнера 13.08.2026): значок один и тот же везде.
                g.setColor(AMMO_TILE);
                g.fillRoundRect(x, y, d, d, Math.max(2, d / 4), Math.max(2, d / 4));
                g.setColor(AMMO_TILE.darker());
                g.setStroke(new BasicStroke(1.2f));
                g.drawRoundRect(x, y, d, d, Math.max(2, d / 4), Math.max(2, d / 4));
                int tip = Math.max(2, (int) (d * 0.30));
                int bw = Math.max(3, (int) (d * 0.28));
                int bx = x + (d - bw) / 2;
                int by = y + (int) (d * 0.14);
                int bh = (int) (d * 0.72);
                g.setColor(new Color(0x14, 0x14, 0x14));
                g.fillPolygon(
                    new int[]{bx + bw / 2, bx + bw, bx + bw, bx, bx},
                    new int[]{by, by + tip, by + bh, by + bh, by + tip}, 5);
            }
            case "card" -> {
                g.setColor(Color.WHITE);
                g.fillRoundRect(x + 1, y, d - 2, d, 3, 3);
                g.setColor(ink2());
                g.setStroke(new BasicStroke(1.2f));
                g.drawRoundRect(x + 1, y, d - 2, d, 3, 3);
                g.setColor(ink3());
                g.fillRect(x + 4, y + d / 3, d - 8, 1);
                g.fillRect(x + 4, y + d * 2 / 3, d - 8, 1);
            }
            case "trophy" -> {
                // щиток трофея — так он и выглядит на карте трофеев
                java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
                p.moveTo(x, y);
                p.lineTo(x + d, y);
                p.lineTo(x + d, y + d * 0.55);
                p.lineTo(x + d / 2.0, y + d);
                p.lineTo(x, y + d * 0.55);
                p.closePath();
                g.setColor(TROPHY_FACE);
                g.fill(p);
                g.setColor(TROPHY_FACE.darker());
                g.setStroke(new BasicStroke(1.2f));
                g.draw(p);
            }
            default -> {
                g.setColor(line());
                g.fillRoundRect(x, y, d, d, 3, 3);
            }
        }
    }

    /** Первое (одиночное) значение: из {@code [1, 2]} — «1», из {@code 2} — «2». */
    private static String first(Object o) {
        if (o instanceof List<?> l && !l.isEmpty()) {
            return String.valueOf(l.get(0));
        }
        return o == null ? "?" : String.valueOf(o);
    }

    /** Число или диапазон из правил: {@code 2} → «2», {@code [1, 2]} → «1–2». */
    private static String amount(Object o) {
        if (o instanceof List<?> l && !l.isEmpty()) {
            Object a = l.get(0);
            Object b = l.get(l.size() - 1);
            return String.valueOf(a).equals(String.valueOf(b))
                ? String.valueOf(a) : a + "–" + b;
        }
        return o == null ? "?" : String.valueOf(o);
    }

    /** Золотая звезда с числом победных очков внутри («+2»). */
    private void star(Graphics2D g, int cx, int cy, double r, int vp) {
        java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double rr = i % 2 == 0 ? r : r * 0.45;
            double a = Math.toRadians(-90 + i * 36);
            double px = cx + rr * Math.cos(a);
            double py = cy + rr * Math.sin(a);
            if (i == 0) {
                p.moveTo(px, py);
            } else {
                p.lineTo(px, py);
            }
        }
        p.closePath();
        g.setColor(new Color(0xF2, 0xC0, 0x2E));
        g.fill(p);
        g.setColor(new Color(0x8A, 0x63, 0x00));
        g.setStroke(new BasicStroke(1.0f));
        g.draw(p);
        g.setFont(getFont().deriveFont(Font.BOLD, (float) (r * 1.15)));
        // ЧИСЛО ПИШЕТСЯ НА БУМАГЕ ПЛАНШЕТА, а не на звезде: тёмно-коричневым по
        // тёмной теме его было не видно вовсе (замечание дизайнера 14.08.2026).
        g.setColor(ink());
        String tag = "+" + vp;
        g.drawString(tag, cx + (int) r + 2, cy + (int) (r * 0.55));
    }

    private void drawChip(Graphics2D g, int x, int y, int d, int seat) {
        g.setColor(FieldView.seatColor(seat));
        g.fillOval(x, y, d, d);
        g.setColor(FieldView.seatStroke(seat));
        g.setStroke(new BasicStroke(1.4f));
        g.drawOval(x, y, d, d);
        g.setColor(Color.WHITE);
        g.setFont(getFont().deriveFont(Font.BOLD, d * 0.62f));
        String s = String.valueOf(seat + 1);
        int tw = g.getFontMetrics().stringWidth(s);
        g.drawString(s, x + (d - tw) / 2, y + d - Math.max(3, d / 4));
    }

    /** Место игрока, стоящего на вершине трека (−1 — вершина свободна). */
    private int peakHolder(String track, int steps) {
        for (ReplayRecord.Player p : snap.players) {
            if (p.tech.getOrDefault(track, 0) >= steps) {
                return p.seat;
            }
        }
        return -1;
    }

    private void paintSuperArsenalCard(Graphics2D g, int x, int y, int w, int h,
                                       String id, Color accent, int takenBy) {
        g.setColor(wash(accent));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(accent);
        g.setStroke(new BasicStroke(2.2f));
        g.drawRoundRect(x, y, w, h, 10, 10);
        g.setFont(bold(10));
        g.setColor(accent.darker());
        g.drawString("СУПЕР-АРСЕНАЛ", x + 10, y + 22);
        Map<String, Object> card = id == null ? null : find("super_arsenal", id);
        if (card == null) {
            g.setFont(note(10.5));
            if (id == null && takenBy >= 0) {
                // карту снимают с вершины, когда игрок на неё встал
                g.setColor(FieldView.seatStroke(takenBy));
                g.drawString("забрал игрок " + (takenBy + 1), x + 10, y + 46);
            } else {
                g.setColor(ink3());
                g.drawString(id == null ? "карта не выложена" : id, x + 10, y + 46);
            }
            return;
        }
        // название карты супер-арсенала — тем же плакатным шрифтом
        g.setFont(kelium.gui.replay2.Theme.display(20));
        g.setColor(ink());
        g.drawString(clip(g, String.valueOf(card.get("name")), w - 16), x + 10, y + 48);
        g.setFont(note(9.5));
        g.setColor(ink2());
        wrap(g, String.valueOf(card.getOrDefault("label", "")), x + 10, y + 70, w - 20, 19, 2);
    }

    private String prizeText(String track) {
        Object o = ruleset == null ? null : ruleset.get("tech.step1_prize", null);
        if (!(o instanceof Map<?, ?> m) || !(m.get(track) instanceof Map<?, ?> pr)) {
            return "приз шага 1 не задан";
        }
        return "1-му " + resText(pr.get("first")) + ", 2-му " + resText(pr.get("second"));
    }

    private static String resText(Object o) {
        if (!(o instanceof Map<?, ?> m) || m.isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getValue()).append(' ').append(resRu(String.valueOf(e.getKey())));
        }
        return sb.toString();
    }

    private static String resRu(String key) {
        return switch (key) {
            case "ammo" -> "БПР";
            case "kelium" -> "КЕЛ";
            case "coin" -> "МОН";
            case "trophy" -> "ТРФ";
            default -> key;
        };
    }

    // ==================== ПЛАНШЕТ РЫНКА ====================

    private void paintMarket(Graphics2D g, int x, int y, int w, int h) {
        frame(g, x, y, w, h, "ПЛАНШЕТ РЫНКА");
        int cellCost = ((Number) rget("market.cell_cost_kelium", 1)).intValue();
        int players = record == null ? snap.players.size() : record.players;

        int cardH = Math.min(290, h / 2);
        paintMarketCard(g, x + 14, y + 44, w - 28, cardH, cellCost, players);

        int ey = y + 44 + cardH + 24;
        // Заголовок и уточнение — ДВУМЯ строками: одной они уезжали за правый
        // край планшета, кегль-то вырос вдвое.
        g.setFont(bold(11));
        g.setColor(ink());
        g.drawString("ПЕЧАТНЫЕ ОБМЕНЫ ПЛАНШЕТА", x + 14, ey);
        g.setFont(note(10));
        g.setColor(ink3());
        g.drawString("1 КЕЛ за обмен, сколько угодно раз", x + 14, ey + 20);
        ey += 26;
        for (Object[] parts : printedRates()) {
            ey += 27;
            line(g, x + 20, ey, sz(11), parts);
        }
        ey += 27;
        g.setFont(note(10));
        g.setColor(ink3());
        wrap(g, "Ячейки предложений РАСХОДУЮТСЯ: занял — предложение для остальных "
            + "сузилось. Кубик келемия в ячейке и цветная метка рядом показывают, "
            + "кто её занял. Вторая ячейка открыта только при 3–4 игроках.",
            x + 20, ey, w - 40, 21, 4);
    }

    private void paintMarketCard(Graphics2D g, int x, int y, int w, int h,
                                 int cellCost, int players) {
        Map<String, Object> card = snap.market == null ? null : find("market", snap.market);
        g.setColor(wash(new Color(0x2C, 0x62, 0xA8)));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(new Color(0x2C, 0x62, 0xA8));
        g.setStroke(new BasicStroke(2.4f));
        g.drawRoundRect(x, y, w, h, 12, 12);
        g.setFont(bold(10));
        g.setColor(new Color(0x2C, 0x62, 0xA8));
        g.drawString("АКТИВНАЯ КАРТА РЫНКА · раунд " + snap.round, x + 12, y + 24);
        if (card == null) {
            g.setFont(note(11));
            g.setColor(ink3());
            g.drawString(snap.market == null ? "карта рынка ещё не открыта" : snap.market,
                x + 12, y + 52);
            return;
        }
        // НАЗВАНИЕ КАРТЫ — плакатным широким полужирным (решение дизайнера 13.08.2026)
        g.setFont(kelium.gui.replay2.Theme.display(24));
        g.setColor(ink());
        g.drawString(clip(g, String.valueOf(card.get("name")), w - 20), x + 12, y + 54);

        int offY = y + 66;
        int offH = (h - 76) / 2;
        paintOffer(g, x + 10, offY, w - 20, offH - 6, card.get("left"), "ЛЕВОЕ",
            cellCost, players);
        paintOffer(g, x + 10, offY + offH + 4, w - 20, offH - 6, card.get("right"), "ПРАВОЕ",
            cellCost, players);
    }

    private void paintOffer(Graphics2D g, int x, int y, int w, int h, Object offer,
                           String side, int cellCost, int players) {
        g.setColor(paper());
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(line());
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(x, y, w, h, 8, 8);
        g.setFont(bold(9.5));
        g.setColor(ink3());
        g.drawString(side + " ПРЕДЛОЖЕНИЕ", x + 10, y + 22);
        if (!(offer instanceof Map<?, ?> off)) {
            g.setFont(note(10));
            g.drawString("нет", x + 10, y + 48);
            return;
        }
        g.setFont(bold(12));
        g.setColor(ink());
        g.drawString(clip(g, String.valueOf(off.get("name")), w - 140), x + 10, y + 48);
        g.setFont(note(10.5));
        g.setColor(ink2());
        // ПОДПИСЬ ПРЕДЛОЖЕНИЯ ПО-РУССКИ: в данных она английская («free Combat»),
        // и на планшете так и висела (замечание дизайнера 13.08.2026).
        Object label = off.get("label");
        wrap(g, label == null ? "" : kelium.gui.replay2.Names.offer(String.valueOf(label)),
            x + 10, y + 70, w - 140, 21, 2);

        // ЯЧЕЙКИ ПРЕДЛОЖЕНИЯ: вторая открыта только при 3–4 игроках. Занятая
        // ячейка держит ОБЪЁМНЫЙ КУБИК КЕЛЕМИЯ, а рядом — цветная метка игрока,
        // который его туда поставил (просьба дизайнера 12.08.2026).
        int sideIdx = "ПРАВОЕ".equals(side) ? 1 : 0;
        int[] cells = snap.marketCells == null || snap.marketCells.length <= sideIdx
            ? new int[]{-1, -1} : snap.marketCells[sideIdx];
        int d = Math.min(34, h - 30);
        int cx = x + w - 14 - 2 * (d + 8);
        for (int i = 0; i < 2; i++) {
            boolean live = i == 0 || players >= 3;
            int cy = y + (h - d) / 2;
            g.setColor(live ? wash(new Color(0x2C, 0x62, 0xA8)) : emptyCell());
            g.fillRoundRect(cx, cy, d, d, 4, 4);
            g.setColor(live ? new Color(0x2C, 0x62, 0xA8) : line());
            g.setStroke(live ? new BasicStroke(1.8f)
                : new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{3f, 3f}, 0f));
            g.drawRoundRect(cx, cy, d, d, 4, 4);

            int seat = i < cells.length ? cells[i] : -1;
            if (live && seat >= 0) {
                // кубик келемия — бирюзовый, как келемий на поле
                cube(g, cx + 3, cy + 3, d - 6, KELIUM_CUBE);
                // метка игрока: цветной уголок у ячейки
                g.setColor(FieldView.seatColor(seat));
                g.fillOval(cx + d - 7, cy - 4, 10, 10);
                g.setColor(FieldView.seatStroke(seat));
                g.setStroke(new BasicStroke(1.2f));
                g.drawOval(cx + d - 7, cy - 4, 10, 10);
            } else {
                g.setFont(kelium.gui.replay2.Theme.font(Math.max(11, d / 2), Font.BOLD));
                g.setColor(live ? new Color(0x55, 0x77, 0xAA) : new Color(0x99, 0x99, 0x99, 120));
                String tag = live ? cellCost + "К" : "3+И";
                int tw = g.getFontMetrics().stringWidth(tag);
                g.drawString(tag, cx + (d - tw) / 2, cy + d / 2 + 4);
            }
            cx += d + 8;
        }
    }

    private List<Object[]> printedRates() {
        List<Object[]> out = new ArrayList<>();
        // СКИДКА ЗА ПАРУ — В ТОЙ ЖЕ СТРОКЕ, что и базовый курс: «1 / 2 келемия →
        // 3 / 7 монет». Отдельной строкой ниже она читалась как ещё один, пятый
        // обмен, а это один и тот же обмен с двумя ценами (просьба дизайнера
        // 13.08.2026).
        int coinRate = rateOf("kelium_to_coin", "per_kelium_coin");
        int pair = intRule("market.pair_bonus_coin");
        Object o = rget("market.base_exchanges", null);
        if (o instanceof List<?> list) {
            for (Object e : list) {
                if (e instanceof Map<?, ?> m) {
                    if (pair > 0 && coinRate > 0
                            && "kelium_to_coin".equals(String.valueOf(m.get("id")))) {
                        out.add(new Object[]{"1 / 2", "@kelium", "келемия   →",
                            coinRate + " / " + (2 * coinRate + pair), "@coin", "монет"});
                    } else {
                        out.add(rateParts(m));
                    }
                }
            }
        }
        if (out.isEmpty()) {
            out.add(new Object[]{"обмены планшета не заданы в правилах"});
        }
        return out;
    }

    /** Целое из правил (0, если ключа нет). */
    private int intRule(String key) {
        Object o = rget(key, null);
        return o instanceof Number n ? n.intValue() : 0;
    }

    /** Курс печатного обмена из {@code market.base_exchanges} (0 — не нашёлся). */
    private int rateOf(String id, String field) {
        Object o = rget("market.base_exchanges", null);
        if (o instanceof List<?> list) {
            for (Object e : list) {
                if (e instanceof Map<?, ?> m && id.equals(String.valueOf(m.get("id")))
                        && m.get(field) instanceof Number n) {
                    return n.intValue();
                }
            }
        }
        return 0;
    }

    /** Обмен рынка словами и значками: что отдал → что получил. */
    private static Object[] rateParts(Map<?, ?> m) {
        String id = String.valueOf(m.get("id"));
        return switch (id) {
            case "kelium_to_coin" -> new Object[]{"1", "@kelium", "келемий   →",
                amount(m.get("per_kelium_coin")), "@coin", "монеты"};
            case "kelium_to_ammo" -> new Object[]{"1", "@kelium", "келемий   →",
                amount(m.get("per_kelium_ammo")), "@ammo", "боеприпаса"};
            case "kelium_to_objective" -> new Object[]{"1", "@kelium", "келемий   →",
                amount(m.get("per_kelium_cards")), "@card", "карты заданий"};
            case "kelium_to_energy" -> new Object[]{"1", "@kelium", "келемий   →", "1",
                "@energy", "энергия в ячейку своего здания"};
            default -> new Object[]{"обмен не описан"};
        };
    }

    // ==================== мелкая утварь ====================

    private void frame(Graphics2D g, int x, int y, int w, int h, String title) {
        g.setColor(paper());
        g.fillRoundRect(x, y, w, h, 14, 14);
        g.setColor(ink3());
        g.setStroke(new BasicStroke(1.4f));
        g.drawRoundRect(x, y, w, h, 14, 14);
        g.setFont(bold(12));
        g.setColor(ink());
        g.drawString(title, x + 12, y + 30);
    }

    private int[] ints(String key, int[] fallback) {
        Object o = rget(key, null);
        if (o instanceof List<?> list && !list.isEmpty()) {
            int[] out = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                out[i] = list.get(i) instanceof Number n ? n.intValue() : 0;
            }
            return out;
        }
        return fallback;
    }

    private Object rget(String key, Object fallback) {
        return ruleset == null ? fallback : ruleset.get(key, fallback);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> find(String set, String id) {
        if (content == null || id == null) {
            return null;
        }
        try {
            return content.get(set).find(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String clip(Graphics2D g, String s, int width) {
        if (s == null) {
            return "";
        }
        if (g.getFontMetrics().stringWidth(s) <= width) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() > 1 && g.getFontMetrics().stringWidth(sb + "…") > width) {
            sb.setLength(sb.length() - 1);
        }
        return sb + "…";
    }

    /** Перенос текста по словам: не более {@code maxLines} строк. */
    static void wrap(Graphics2D g, String text, int x, int y, int width, int lineH,
                     int maxLines) {
        if (text == null || text.isBlank() || "null".equals(text)) {
            return;
        }
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        int lines = 0;
        int cy = y;
        for (String word : words) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(probe) > width && line.length() > 0) {
                g.drawString(line.toString(), x, cy);
                cy += lineH;
                if (++lines >= maxLines) {
                    return;
                }
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0) {
            g.drawString(clip(g, line.toString(), width), x, cy);
        }
    }
}
