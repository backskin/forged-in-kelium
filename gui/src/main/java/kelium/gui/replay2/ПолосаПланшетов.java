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

    /** Стопка карт: что это, что открывать читалкой и как назвать. */
    private record Стопка(String вид, java.util.List<String> карты, String имя) {
    }

    /** Стопки карт на ленте: рамка → стопка. */
    private final Map<Rectangle, Стопка> стопки = new LinkedHashMap<>();

    /** Какие карты игрок перевернул лицом — живёт, пока открыт планшет. */
    private final Set<String> вскрытые = new LinkedHashSet<>();

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
                щелчок(e.getPoint());
            }
        });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                boolean рука = найти(карты, e.getPoint()) != null
                    || найти(стопки, e.getPoint()) != null
                    || найти(модули, e.getPoint()) != null;
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
        стопки.clear();

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
            модули, жетоныХранилища, проКарты(), карты);

        int столбецX = x + (int) Math.round(р.с().ширина() * р.k()) + р.зазор();
        int верхПланшета = y + (int) Math.round(р.с().войY() * р.k());
        int высотаПланшета = (int) Math.round(р.войH() * р.k());
        if (р.стопкаW() > 0) {
            стопка(g, "objectives", p.objectiveHand, "задания", столбецX, верхПланшета,
                высотаПланшета, р.сдвиг());
            столбецX += р.стопкаW() + р.зазор();
        }
        // ОТЛОЖЕННЫЙ ПРИКАЗ И ЗАПАС МОДУЛЕЙ — тут же, рядом с планшетами: своего
        // листа под ними больше нет (заказ дизайнера 11.09.2026).
        int столбецY = верхПланшета;
        if (p.superObjective != null) {
            int вh = (int) Math.round(высотаПланшета * 0.42);
            стопка(g, "super_objectives", java.util.List.of(p.superObjective),
                "супер-задание", столбецX, столбецY, вh, р.сдвиг());
            столбецY += вh + BoardSheet.px(16);
        }
        лист.отложенныйПриказ(g, p, столбецX, столбецY, р.столбецW(),
            (int) Math.round(р.столбецW() * BoardSheet.КАРТА_ПРИКАЗА));
        лист.запасМодулей(g, p, столбецX, столбецY
            + (int) Math.round(р.столбецW() * BoardSheet.КАРТА_ПРИКАЗА) + BoardSheet.px(26));
        if (р.хр() != null) {
            // КАРТА ТРОФЕЕВ — СТРОГО ПОД ПЛАНШЕТОМ ХРАНИЛИЩА, в его ширину и в
            // своей пропорции (заказ дизайнера 11.09.2026).
            int хрX = x + (int) Math.round(р.с().хрX() * р.k());
            int хрW = (int) Math.round(р.хр()[0] * р.с().хрМасштаб() * р.k());
            int хрНиз = y + (int) Math.round(
                (р.с().хрY() + р.хр()[1] * р.с().хрМасштаб()) * р.k());
            BoardSheet.paintDestroyedCard(g, p, хрX, хрНиз + BoardSheet.px(10), хрW,
                (int) Math.round(хрW / BoardSheet.КАРТА_ПРИКАЗА));
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

    /** Что программа знает о картах в пазах — для {@link PrintedBoards}. */
    private PrintedBoards.ПроКарты проКарты() {
        return new PrintedBoards.ПроКарты() {
            @Override
            public String имя(String вид, String id) {
                return Names.card(session.record(), id);
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
        if (м != null) {
            ModuleZoom.show(javax.swing.SwingUtilities.getWindowAncestor(this),
                (ReplayRecord.Module) м[0], (Boolean) м[1], String.valueOf(м[2]));
            return;
        }
        // ЩЕЛЧОК ПО ЗАКРЫТОЙ КАРТЕ — ПЕРЕВОРОТ (заказ дизайнера 11.09.2026).
        // Карта в пазу лежит рубашкой, как на столе; кто разбирает партию,
        // вправе её перевернуть и положить обратно.
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
            return Ui2.tip(Names.card(session.record(), id)
                + "\nщелчок — перевернуть карту");
        }
        Стопка стопка = найти(стопки, e.getPoint());
        if (стопка != null) {
            return Ui2.tip(стопка.имя() + " — щелчок открывает читалку");
        }
        return null;
    }
}
