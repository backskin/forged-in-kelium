package kelium.gui.replay2;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.Scrollable;

import kelium.report.ReplayRecord;

/**
 * ПолосаПланшетов — ЛЕНТА С ПЕЧАТНЫМИ ПЛАНШЕТАМИ ИГРОКА.
 *
 * <p>Заказ дизайнера 11.09.2026: «отдельно необходимо сделать ленту
 * пролистывания полосы с планшетами; уметь зумить содержимое — не помещается на
 * экране». Раньше сцепка планшетов рисовалась прямо в {@link BoardSheet} и
 * растягивалась по ширине листа: чтобы разглядеть печать, приходилось растить
 * окно, а прокрутка вбок двигала весь лист вместе с картами и модулями.
 *
 * <p>Теперь это отдельная полоса со своей горизонтальной прокруткой и своим
 * увеличением. На ней лежит всё, что на столе лежит рядом друг с другом:
 *
 * <ul>
 *   <li>сцепка планшетов — хранилище слева, углом в выемку планшета войск;</li>
 *   <li>жетоны военных зданий и запас войск над ними;</li>
 *   <li>карты арсенала и контейнеров в трёх пазах внизу планшета войск;</li>
 *   <li>стопка заданий СПРАВА от планшета войск, ростом с него;</li>
 *   <li>карта трофеев СТРОГО ПОД планшетом хранилища, в его ширину.</li>
 * </ul>
 *
 * <p>Увеличение считается от ОДНОЙ базовой ширины: при 100% сцепка занимает
 * {@link #БАЗА} точек, дальше всё умножается. Поэтому «сделать крупнее» не
 * ломает раскладку — она вся выражена долями печатных пикселей.
 */
public final class ПолосаПланшетов extends JComponent implements Scrollable {

    private static final long serialVersionUID = 1L;

    /** Ширина сцепки планшетов при увеличении 100%. */
    private static final int БАЗА = 1180;

    private static final double МИН_ЗУМ = 0.4;
    private static final double МАКС_ЗУМ = 3.0;

    private final Session session;
    private final BoardSheet лист;
    private int seat;
    private double зум = 1.0;
    private Runnable приЗуме;

    /** Места жетонов модулей на печати: рамка → {жетон, красный ли, имя места}. */
    private final Map<Rectangle, Object[]> модули = new LinkedHashMap<>();

    /** Места жетонов хранилища: рамка → сторона жетона. */
    private final Map<Rectangle, String> жетоныХранилища = new LinkedHashMap<>();

    /** Карты в пазах: рамка → код карты. По ним идёт переворот щелчком. */
    private final Map<Rectangle, String> карты = new LinkedHashMap<>();

    /** Вставленные карты арсенала: они уже лежат лицом, их не переворачивают. */
    private final Map<Rectangle, String> вставленные = new LinkedHashMap<>();

    /** Стопка карт: что это, что открывать читалкой и как назвать. */
    private record Стопка(String вид, java.util.List<String> карты, String имя) {
    }

    /** Стопки карт на ленте: рамка → стопка. */
    private final Map<Rectangle, Стопка> стопки = new LinkedHashMap<>();

    /** Какие карты игрок перевернул лицом — живёт, пока открыт планшет. */
    private final Set<String> вскрытые = new LinkedHashSet<>();

    /**
     * ПОДСКАЗКИ ПО ВСЕМУ ОСТАЛЬНОМУ: рамка → что это. Спрашиваются ПОСЛЕДНИМИ,
     * после жетонов и карт, потому что это крупные области — сами планшеты и
     * колонки запаса, — внутри которых лежит всё мелкое.
     */
    private final Map<Rectangle, String> подсказки = new LinkedHashMap<>();

    /** Откуда потащили ленту рукой ({@code null} — не тащат). */
    private java.awt.Point откуда;

    /** Тащили ли на самом деле: после таскания щелчок не считается. */
    private boolean тащили;

    /** Перевёрнута ли отложенная карта приказа под планшетом хранилища. */
    private boolean приказВскрыт;

    /** Где она лежит — по ней и щёлкают. */
    private Rectangle местоПриказа;

    public ПолосаПланшетов(Session session, int seat, BoardSheet лист) {
        this.session = session;
        this.лист = лист;
        this.seat = seat;
        setOpaque(true);
        session.whenFrameChanged(s -> repaint());
        session.whenRecordChanged(s -> {
            вскрытые.clear();
            revalidate();
            repaint();
        });
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (тащили) {
                    тащили = false;     // это было таскание, а не щелчок
                    return;
                }
                щелчок(e.getPoint());
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                откуда = e.getPoint();
                тащили = false;
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                откуда = null;
                setCursor(java.awt.Cursor.getDefaultCursor());
            }
        });
        // ТАСКАНИЕ РУКОЙ. При увеличении лента больше окна, и возить её
        // полосами прокрутки неудобно — за неё берутся и тянут, как за лист
        // бумаги на столе (заказ дизайнера 11.09.2026).
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (откуда == null || !(getParent() instanceof javax.swing.JViewport vp)) {
                    return;
                }
                тащили = true;
                setCursor(java.awt.Cursor.getPredefinedCursor(
                    java.awt.Cursor.MOVE_CURSOR));
                Rectangle вид = vp.getViewRect();
                вид.x += откуда.x - e.getX();
                вид.y += откуда.y - e.getY();
                scrollRectToVisible(вид);
            }
        });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                if (откуда != null) {
                    return;             // тащим — курсор уже свой
                }
                boolean рука = найти(карты, e.getPoint()) != null
                    || найти(стопки, e.getPoint()) != null
                    || найти(модули, e.getPoint()) != null
                    || (местоПриказа != null && местоПриказа.contains(e.getPoint()));
                setCursor(рука
                    ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                    : java.awt.Cursor.getDefaultCursor());
            }
        });
        // КОЛЕСО С CTRL — УВЕЛИЧЕНИЕ. Без Ctrl колесо отдаётся полосе прокрутки:
        // иначе лента перехватывала бы обычную прокрутку листа.
        addMouseWheelListener(e -> {
            if (!e.isControlDown()) {
                getParent().dispatchEvent(javax.swing.SwingUtilities.convertMouseEvent(
                    ПолосаПланшетов.this, e, getParent()));
                return;
            }
            зум(зум * (e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1));
        });
    }

    public void setSeat(int seat) {
        this.seat = seat;
        вскрытые.clear();
        revalidate();
        repaint();
    }

    public int seat() {
        return seat;
    }

    /** Текущее увеличение: 1.0 — сто процентов. */
    public double зум() {
        return зум;
    }

    /** Поставить увеличение; за пределы разумного не пускаем. */
    public void зум(double v) {
        double было = зум;
        зум = Math.max(МИН_ЗУМ, Math.min(МАКС_ЗУМ, v));
        if (зум != было) {
            revalidate();
            repaint();
            if (приЗуме != null) {
                приЗуме.run();
            }
        }
    }

    /** Кого дёрнуть, когда увеличение сменилось (подпись на кнопке). */
    public void приЗуме(Runnable r) {
        this.приЗуме = r;
    }

    /** Есть ли что показывать: без печатных планшетов лента не нужна вовсе. */
    public boolean естьЧтоПоказать() {
        ReplayRecord.Frame f = session.frame();
        return f != null && f.snapshot != null && seat < f.snapshot.players.size()
            && PrintedBoards.available(f.snapshot.players.get(seat).seat)
            && PrintedBoards.сцепка(f.snapshot.players.get(seat).seat) != null;
    }

    // ==================== размеры ====================

    /** Поля вокруг сцепки, чтобы она не липла к краям ленты. */
    private static int поле() {
        return BoardSheet.px(10);
    }

    /** Раскладка ленты в экранных точках — одна на отрисовку и на размер. */
    private record Раскладка(double k, int ширина, int высота, PrintedBoards.Сцепка с,
                             double войH, double[] хр, int зазор, int сдвиг, int стопкаW,
                             int столбецW) {
    }

    private Раскладка раскладка() {
        ReplayRecord.Frame f = session.frame();
        if (f == null || f.snapshot == null || seat >= f.snapshot.players.size()) {
            return null;
        }
        int место = f.snapshot.players.get(seat).seat;
        var с = PrintedBoards.сцепка(место);
        if (с == null || с.ширина() <= 0) {
            return null;
        }
        double k = зум * BoardSheet.px(БАЗА) / с.ширина();
        double войH = PrintedBoards.высотаВойск(место);
        double доляЗад = BoardSheet.доляРубашки("objectives");
        int видно = BoardSheet.видимыхКарт(
            f.snapshot.players.get(seat).objectiveHand.size());
        int зазор = (int) Math.round(BoardSheet.px(16) * зум);
        int сдвиг = (int) Math.round(BoardSheet.px(6) * зум);
        int стопкаW = доляЗад <= 0 ? 0
            : (int) Math.round(войH * k * доляЗад) + (видно - 1) * сдвиг;
        // СТОЛБЕЦ СПРАВА: отложенный приказ и запас модулей. Карта приказа
        // втрое уже планшета войск — дизайнер просил её «раза в полтора
        // меньше» прежней (11.09.2026), и мерить её надо от планшета, а не
        // точками, иначе при увеличении она снова разъедется.
        int столбецW = (int) Math.round(войH * k * 0.30);
        double[] хр = PrintedBoards.размерХранилища(место);
        int ширина = (int) Math.round(с.ширина() * k)
            + (стопкаW > 0 ? зазор + стопкаW : 0) + зазор + столбецW
            // запас под подпись «ОТЛОЖЕННЫЙ ПРИКАЗ»: она шире самой карты
            + BoardSheet.px(90);
        int высота = (int) Math.round(с.высота() * k);
        if (хр != null) {
            int хрW = (int) Math.round(хр[0] * с.хрМасштаб() * k);
            int хрНиз = (int) Math.round((с.хрY() + хр[1] * с.хрМасштаб()) * k);
            высота = Math.max(высота, хрНиз + BoardSheet.px(10) + BoardSheet.px(14)
                + (int) Math.round(хрW / BoardSheet.КАРТА_ПРИКАЗА));
        }
        return new Раскладка(k, ширина, высота, с, войH, хр, зазор, сдвиг, стопкаW,
            столбецW);
    }

    @Override
    public Dimension getPreferredSize() {
        Раскладка р = раскладка();
        if (р == null) {
            return new Dimension(BoardSheet.px(200), BoardSheet.px(40));
        }
        return new Dimension(р.ширина() + поле() * 2, р.высота() + поле() * 2);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;               // лента и существует ради прокрутки вбок
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return true;                // по высоте лента всегда по ящику
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int dir) {
        return BoardSheet.px(40);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int dir) {
        return visible.width - BoardSheet.px(40);
    }

    // ==================== отрисовка ====================

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        g.setColor(Theme.bg());
        g.fillRect(0, 0, getWidth(), getHeight());

        модули.clear();
        жетоныХранилища.clear();
        карты.clear();
        вставленные.clear();
        стопки.clear();
        подсказки.clear();

        ReplayRecord.Frame f = session.frame();
        Раскладка р = раскладка();
        if (р == null) {
            g.dispose();
            return;
        }
        ReplayRecord.Player p = f.snapshot.players.get(seat);
        var все = BoardSheet.buildingsOf(f, p.seat);
        var план = BoardSheet.складПлан(p, все);
        int x = поле();
        int y = поле();

        PrintedBoards.paintPair(g, x, y, р.k(), р.с(), p, BoardSheet.troopSide(session, p),
            план.ячейки(), план.старт(), BoardSheet.coveredCells(все),
            BoardSheet.вЗапасе(f, p.seat), BoardSheet.запасВойск(session, f, p.seat),
            модули, жетоныХранилища, проКарты(), карты, вставленные);

        int столбецX = x + (int) Math.round(р.с().ширина() * р.k()) + р.зазор();
        int верхПланшета = y + (int) Math.round(р.с().войY() * р.k());
        int высотаПланшета = (int) Math.round(р.войH() * р.k());
        if (р.стопкаW() > 0) {
            стопка(g, "objectives", p.objectiveHand, "задания", столбецX, верхПланшета,
                высотаПланшета, р.сдвиг());
            столбецX += р.стопкаW() + р.зазор();
        }
        // ОТЛОЖЕННОГО ПРИКАЗА ОТДЕЛЬНО ЗДЕСЬ НЕТ: это та самая повёрнутая карта
        // под планшетом хранилища, на которую кладут трофеи. Запаса модулей
        // тоже нет — все жетоны и так видны на самой печати (заказ дизайнера
        // 11.09.2026).
        if (p.superObjective != null) {
            стопка(g, "super_objectives", java.util.List.of(p.superObjective),
                "супер-задание", столбецX, верхПланшета, высотаПланшета, р.сдвиг());
        }
        местоПриказа = null;
        областиПодсказок(р, p, x, y);
        if (р.хр() != null) {
            // КАРТА ПОД ПЛАНШЕТОМ ХРАНИЛИЩА — ТОГО ЖЕ РАЗМЕРА, ЧТО КАРТА
            // ЗАДАНИЯ СПРАВА, только лежащая (заказ дизайнера 11.09.2026). По
            // ширине планшета она была втрое крупнее любой другой карты на
            // столе, хотя карта у них одна и та же.
            int хрX = x + (int) Math.round(р.с().хрX() * р.k());
            int хрW = (int) Math.round(р.хр()[0] * р.с().хрМасштаб() * р.k());
            int хрНиз = y + (int) Math.round(
                (р.с().хрY() + р.хр()[1] * р.с().хрМасштаб()) * р.k());
            int вW = высотаПланшета;
            int вH = (int) Math.round(высотаПланшета / BoardSheet.КАРТА_ПРИКАЗА);
            // ПО ЦЕНТРУ ПЛАНШЕТА ХРАНИЛИЩА, а не по его левому краю: карта уже
            // не во всю его ширину, и прижатая влево она выглядит съехавшей
            // (замечание дизайнера 11.09.2026).
            int вX = хрX + Math.max(0, (хрW - вW) / 2);
            int вY = хрНиз + BoardSheet.px(10);
            BoardSheet.paintDestroyedCard(g, p, вX, вY, вW, вH,
                приказВскрыт ? лицоПриказа(p) : null);
            местоПриказа = new Rectangle(вX, вY + BoardSheet.px(14), вW, вH);
        }
        g.dispose();
    }

    /**
     * СТОПКА ЗАДАНИЙ СПРАВА ОТ ПЛАНШЕТА ВОЙСК, ростом с него.
     *
     * <p>Заказ дизайнера 11.09.2026. Прежде задания лежали мелкой стопочкой в
     * ряду кнопок под планшетом — при том что это единственные карты игрока,
     * которым на планшете места не напечатано. Рядом с планшетом им и место: на
     * столе они лежат ровно там.
     */
    private void стопка(Graphics2D g, String вид, java.util.List<String> карты,
                        String имя, int x, int y, int h, int сдвиг) {
        var рубашка = kelium.report.Textures.card("deck_" + вид, "deck");
        if (рубашка == null || h <= 0) {
            return;
        }
        int w = (int) Math.round(h * рубашка.getWidth() / (double) рубашка.getHeight());
        int count = карты.size();
        int видно = BoardSheet.видимыхКарт(count);
        java.awt.Composite было = g.getComposite();
        if (count == 0) {
            // Заданий нет — на их месте бледный след одной карты: место за
            // столом существует всегда, просто сейчас пусто.
            g.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, 0.22f));
        }
        for (int i = 0; i < видно; i++) {
            g.drawImage(рубашка, x + i * сдвиг, y + i * сдвиг, w, h, null);
        }
        g.setComposite(было);
        if (count > 0) {
            стопки.put(new Rectangle(x, y, w + (видно - 1) * сдвиг,
                h + (видно - 1) * сдвиг), new Стопка(вид, карты, имя));
        }
    }

    /**
     * КРУПНЫЕ ОБЛАСТИ ЛЕНТЫ И ЧТО ОНИ ЗНАЧАТ: сами планшеты и колонки запаса
     * войск над хранилищем. Подсказка должна быть у ВСЕГО, на что ни наведи
     * (заказ дизайнера 11.09.2026), а мелочь — жетоны, кубики, карты — уже
     * отмечена своими рамками и спрашивается раньше.
     */
    private void областиПодсказок(Раскладка р, ReplayRecord.Player p, int x, int y) {
        var с = р.с();
        double k = р.k();
        int войX = x + (int) Math.round(с.войX() * k);
        int войY = y + (int) Math.round(с.войY() * k);
        int войW = (int) Math.round((с.ширина() - с.войX()) * k);
        int войH = (int) Math.round(р.войH() * k);
        подсказки.put(new Rectangle(войX, войY, войW, войH),
            "ПЛАНШЕТ ВОЙСК\nчетыре колонки по родам: что род умеет, чем"
                + " стреляет и что у него можно собрать.\nВнизу три паза:"
                + " карты арсенала и контейнеры.");
        if (р.хр() != null) {
            int хрX = x + (int) Math.round(с.хрX() * k);
            int хрY = y + (int) Math.round(с.хрY() * k);
            int хрW = (int) Math.round(р.хр()[0] * с.хрМасштаб() * k);
            int хрH = (int) Math.round(р.хр()[1] * с.хрМасштаб() * k);
            подсказки.put(new Rectangle(хрX, хрY, хрW, хрH),
                "ПЛАНШЕТ ХРАНИЛИЩА\nячейки под келемий, боеприпасы и трофеи."
                    + "\nЯчейку открывает построенное здание — пока оно в"
                    + " запасе, его жетон лежит поверх и ячейки закрыты.");
            // КОЛОНКИ ЗАПАСА ВОЙСК — над планшетом хранилища, по одной на род.
            int полосаH = (int) Math.round((с.зданияH() - 18) * k);
            String[] роды = {"infantry", "vehicle", "aircraft", "tower"};
            var запас = BoardSheet.запасВойск(session, session.frame(), p.seat);
            int colW = хрW / роды.length;
            for (int i = 0; i < роды.length; i++) {
                int[] c = запас.getOrDefault(роды[i], new int[]{0, 0});
                подсказки.put(new Rectangle(хрX + i * colW, y, colW, полосаH),
                    Names.unit(роды[i]).toUpperCase(java.util.Locale.ROOT)
                        + "\nв запасе " + c[1] + " из " + (c[0] + c[1])
                        + ", на поле " + c[0]
                        + "\nЖетоны берут отсюда: чего нет в запасе, того не"
                        + " собрать.");
            }
        }
    }

    /** Печатное лицо отложенной карты приказа ({@code null} — нечего показывать). */
    private java.awt.image.BufferedImage лицоПриказа(ReplayRecord.Player p) {
        if (p.orderSetAside == null || p.orderSetAside.isBlank()) {
            return null;
        }
        if (p.orderSetAside.startsWith("security")) {
            String c = p.orderColor == null ? "" : p.orderColor;
            String alt = "red".equals(c) ? "scarlet" : ("scarlet".equals(c) ? "red" : "");
            return kelium.report.Textures.orderCard(c.isEmpty() ? null : "security_" + c,
                alt.isEmpty() ? null : "security_" + alt, "security");
        }
        return kelium.report.Textures.orderCard(p.orderSetAside);
    }

    /** Что программа знает о картах в пазах — для {@link PrintedBoards}. */    /** Что программа знает о картах в пазах — для {@link PrintedBoards}. */
    private PrintedBoards.ПроКарты проКарты() {
        return new PrintedBoards.ПроКарты() {
            @Override
            public String имя(String вид, String id) {
                return Names.card(session.record(), id);
            }

            @Override
            public String[] половины(String id) {
                var набор = session.content() == null ? null
                    : session.content().get("arsenal");
                var карта = набор == null ? null : набор.find(id);
                String имя = Names.card(session.record(), id);
                if (карта == null) {
                    return new String[]{имя};
                }
                return new String[]{имя, половина(карта.get("top")),
                    половина(карта.get("bottom"))};
            }

            @Override
            public boolean начальный(String id) {
                var набор = session.content() == null ? null
                    : session.content().get("arsenal");
                var карта = набор == null ? null : набор.find(id);
                return карта != null
                    && "starting".equals(String.valueOf(карта.get("kind")));
            }

            @Override
            public boolean вскрыта(String id) {
                return вскрытые.contains(id);
            }

            @Override
            public java.awt.image.BufferedImage лицо(String вид, String id) {
                if (!"containers".equals(вид)) {
                    return null;        // лиц арсенала художник ещё не рисовал
                }
                // ЛИЦ ЧЕТЫРНАДЦАТЬ, А КАРТ ТРИДЦАТЬ ДВЕ: картинка ищется по
                // БУКВЕ ЛИЦА из набора, а не по коду карты.
                var набор = session.content() == null ? null
                    : session.content().get("containers");
                var карта = набор == null ? null : набор.find(id);
                Object буква = карта == null ? null : карта.get("face");
                return буква == null ? null
                    : kelium.report.Textures.containerCard(String.valueOf(буква));
            }
        };
    }

    /** Печатная строка половины карты: пометка вида и сам текст. */
    private static String половина(Object половина) {
        if (!(половина instanceof Map<?, ?> m)) {
            return null;
        }
        Object текст = m.get("label");
        if (текст == null) {
            return null;
        }
        String строка = String.valueOf(текст);
        String вид = пометка(m.get("kind"));
        // Пометку вида не дублируем: на многих картах она уже стоит в самом
        // тексте («СПЕЦ: …», «ДЕСАНТ: …»).
        return вид == null || строка.toUpperCase(java.util.Locale.ROOT)
            .startsWith(вид) ? строка : вид + ": " + строка;
    }

    /**
     * ПОМЕТКА ВИДА ПОЛОВИНЫ по-русски. В наборе карт она записана латиницей
     * ({@code SPEC}, {@code POST}), а на печати стоит русскими буквами — в
     * текстах карт того же набора так и написано: «СПЕЦ: …».
     */
    private static String пометка(Object вид) {
        if (вид == null) {
            return null;
        }
        String код = String.valueOf(вид).toUpperCase(java.util.Locale.ROOT);
        return switch (код) {
            case "SPEC" -> "СПЕЦ";
            case "POST" -> "ПОСТ";
            default -> код;
        };
    }

    // ==================== мышь ====================

    private static <T> T найти(Map<Rectangle, T> где, java.awt.Point p) {
        for (Map.Entry<Rectangle, T> en : где.entrySet()) {
            if (en.getKey().contains(p)) {
                return en.getValue();
            }
        }
        return null;
    }

    private void щелчок(java.awt.Point точка) {
        Object[] м = найти(модули, точка);
        if (м != null && м[0] == null) {
            return;                     // жетон здания: показывать нечего
        }
        if (м != null) {
            ModuleZoom.show(javax.swing.SwingUtilities.getWindowAncestor(this),
                (ReplayRecord.Module) м[0], (Boolean) м[1], String.valueOf(м[2]));
            return;
        }
        // ЩЕЛЧОК ПО ЗАКРЫТОЙ КАРТЕ — ПЕРЕВОРОТ (заказ дизайнера 11.09.2026).
        // Карта в пазу лежит рубашкой, как на столе; кто разбирает партию,
        // вправе её перевернуть и положить обратно.
        if (местоПриказа != null && местоПриказа.contains(точка)) {
            приказВскрыт = !приказВскрыт;
            repaint();
            return;
        }
        String id = найти(карты, точка);
        if (id != null) {
            if (!вскрытые.remove(id)) {
                вскрытые.add(id);
            }
            repaint();
            return;
        }
        Стопка стопка = найти(стопки, точка);
        if (стопка != null) {
            ReplayRecord rec = session.record();
            CardReader.show(javax.swing.SwingUtilities.getWindowAncestor(this),
                session.content(), стопка.вид(),
                (rec == null ? "" : rec.playerName(seat) + " · ") + стопка.имя(),
                стопка.карты(), кто -> Names.card(rec, кто));
        }
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        Object[] м = найти(модули, e.getPoint());
        if (м != null && м[0] == null) {
            return Ui2.tip(String.valueOf(м[2])
                + "\nжетон лежит на планшете — здание в запасе, его ещё не"
                + " строили или оно вернулось");
        }
        if (м != null) {
            return Ui2.tip(ModuleSlot.describe((ReplayRecord.Module) м[0],
                (Boolean) м[1], String.valueOf(м[2])));
        }
        String жетон = найти(жетоныХранилища, e.getPoint());
        if (жетон != null) {
            return Ui2.tip(ModuleSlot.storageTokenName(жетон));
        }
        String id = найти(карты, e.getPoint());
        if (id != null) {
            return Ui2.tip(Names.card(session.record(), id) + "\n"
                + (вскрытые.contains(id) ? "лежит лицом; щелчок — закрыть обратно"
                    : "лежит рубашкой; щелчок — перевернуть"));
        }
        String вставлена = найти(вставленные, e.getPoint());
        if (вставлена != null) {
            return Ui2.tip(Names.card(session.record(), вставлена)
                + "\nУСТАНОВЛЕННАЯ КАРТА АРСЕНАЛА: работает её нижняя половина."
                + "\nВерхняя (утиль) ушла в паз — её уже не разыграть.");
        }
        Стопка стопка = найти(стопки, e.getPoint());
        if (стопка != null) {
            return Ui2.tip(стопка.имя() + " — щелчок открывает читалку");
        }
        if (местоПриказа != null && местоПриказа.contains(e.getPoint())) {
            return Ui2.tip("ОТЛОЖЕННАЯ КАРТА ПРИКАЗА, на ней лежат трофеи"
                + "\nщелчок — перевернуть её лицом");
        }
        String область = найти(подсказки, e.getPoint());
        return область == null ? null : Ui2.tip(область);
    }
}
