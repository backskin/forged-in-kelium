package kelium.gui.replay2;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import javax.swing.JLayeredPane;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import kelium.gui.CardArt;
import kelium.gui.kp.BoardZoom;
import kelium.gui.kp.CardSpread;
import kelium.gui.kp.OrderCardFace;
import kelium.gui.kp.PlayerTable;
import kelium.gui.kp.ZoomCard;
import kelium.report.ReplayRecord;

/**
 * СТОЛ ИГРОКА В РАЗБОРЕ ПАРТИИ — тот же стол, что в окне партии, только
 * смотреть (заказ дизайнера 26.09.2026: «подтяни графику реплея до цифровой
 * версии»).
 *
 * <p>Рисует его не своя копия, а сами детали окна партии: {@link PlayerTable}
 * (печатные планшеты одной мерой печати, карты, свалка, приказ, арсенал,
 * ресурсы иконками), увеличение карты по наведению ({@link ZoomCard}),
 * раскрытие группы карт ({@link CardSpread}) и планшет крупно
 * ({@link BoardZoom}). Кормится стол снимком выбранного шага записи; выбирать
 * на нём нечего — вариантов решения у разбора нет.
 *
 * <p>Разбор знает всё: руки всех мест открыты, закрытых карт соперника нет.
 */
public final class ReplayTable {

    private final Session session;
    private final PlayerTable table = new PlayerTable();
    private final JScrollPane scroll;
    private final BoardSheet sheet;
    private final ZoomCard zoom = new ZoomCard();
    private final CardSpread spread = new CardSpread();
    private final BoardZoom boardZoom = new BoardZoom();
    private JLayeredPane host;
    private int seat;
    private IntConsumer onSeat = s -> { };

    public ReplayTable(Session session) {
        this.session = session;
        this.sheet = new BoardSheet(session, 0);
        table.setMinimumSize(new Dimension(Theme.px(400), Theme.px(180)));
        table.setViewOnly(true);
        table.setBoards(new PlayerTable.BoardsArt() {
            @Override
            public double aspect() {
                return sheet.tableAspect();
            }

            @Override
            public double[] storageBox() {
                return sheet.tableStorageBox();
            }

            @Override
            public double cardWidth() {
                return sheet.tableCardWidth();
            }

            @Override
            public double hang() {
                return sheet.tableHang();
            }

            @Override
            public double printWidth() {
                return sheet.tablePrintWidth();
            }

            @Override
            public int paint(java.awt.Graphics2D g, int x, int y, int width,
                             Map<String, Rectangle> hits, Map<String, java.awt.Shape> outlines) {
                return sheet.paintTableBoards(g, x, y, width, hits, outlines);
            }
        });
        table.setCards(this::anyFace, CardArt::back, this::cardName, id -> null);
        table.onCardHover(this::showZoom, () -> zoom.setVisible(false));
        table.onOpen(this::openSpread);
        scroll = new JScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.getHorizontalScrollBar().setUnitIncrement(Theme.px(40));
        scroll.getViewport().setBackground(Theme.bg());
        zoom.setVisible(false);
        session.whenFrameChanged(s -> refresh());
        session.whenRecordChanged(s -> {
            if (seat >= Math.max(1, s.record() == null ? 1 : s.record().players)) {
                seat = 0;
            }
            refresh();
        });
    }

    /** Зона стола (с прокруткой вбок) — её кладут в окно. */
    public JScrollPane component() {
        return scroll;
    }

    public PlayerTable table() {
        return table;
    }

    /** Слой окна, поверх которого всплывают увеличение, раскрытые карты и планшет. */
    public void install(JLayeredPane layers) {
        this.host = layers;
        layers.add(zoom, JLayeredPane.POPUP_LAYER);
        layers.add(spread, JLayeredPane.MODAL_LAYER);
        layers.add(boardZoom, Integer.valueOf(JLayeredPane.MODAL_LAYER + 10));
    }

    public int seat() {
        return seat;
    }

    /** Чей стол смотреть. */
    public void setSeat(int s) {
        if (s == seat) {
            return;
        }
        seat = s;
        if (spread.isOpen()) {
            spread.close();
        }
        if (boardZoom.isOpen()) {
            boardZoom.close();
        }
        zoom.setVisible(false);
        refresh();
        onSeat.accept(s);
    }

    public void onSeat(IntConsumer c) {
        onSeat = c == null ? s -> { } : c;
    }

    /** Закрыть всё всплывшее (Esc). */
    public boolean closePopups() {
        boolean any = spread.isOpen() || boardZoom.isOpen() || zoom.isVisible();
        spread.close();
        boardZoom.close();
        zoom.setVisible(false);
        return any;
    }

    /**
     * Наибольшая высота зоны, при которой стол влезает в ширину без ползунка
     * (как в окне партии: 40 % высоты, от 230 до 340 точек).
     */
    public int fitHeight(int windowHeight, int width) {
        int zone = Math.max(Theme.px(230), Math.min(Theme.px(340), (int) (windowHeight * 0.40)));
        if (width > 0) {
            while (zone > Theme.px(200) && table.contentWidth(zone) > width) {
                zone -= Theme.px(6);
            }
        }
        return zone;
    }

    // ==================== что лежит на столе ====================

    private ReplayRecord.Player player() {
        ReplayRecord.Frame f = session.frame();
        if (f == null || f.snapshot == null || seat >= f.snapshot.players.size()) {
            return null;
        }
        return f.snapshot.players.get(seat);
    }

    /** Имя места для игрока: «Игрок 1», «Зодчий · новичок». */
    public static String seatName(ReplayRecord rec, int s) {
        if (rec == null) {
            return "Игрок " + (s + 1);
        }
        String id = s < rec.seatIds.size() ? rec.seatIds.get(s) : "";
        String label = s < rec.seatLabels.size() ? rec.seatLabels.get(s) : "";
        if ("human".equals(id) || label.isBlank() || label.startsWith("Игрок ")) {
            return "Игрок " + (s + 1);
        }
        return label;
    }

    /** Короткое имя для вкладки: «Игрок 2», «Зодчий». */
    public static String shortName(ReplayRecord rec, int s) {
        String n = seatName(rec, s);
        int dot = n.indexOf(" · ");
        return dot > 0 ? n.substring(0, dot) : n;
    }

    public static int vpTotal(ReplayRecord.Player p) {
        return p.vp.getOrDefault("total",
            p.vp.values().stream().mapToInt(Integer::intValue).sum());
    }

    /** Приказ, вскрытый местом в текущем круге к этому шагу, либо null. */
    private String revealedOrder(int s) {
        ReplayRecord rec = session.record();
        ReplayRecord.Frame f = session.frame();
        if (rec == null || f == null || f.circle <= 0) {
            return null;
        }
        int cursor = session.cursor();
        String out = null;
        for (ReplayRecord.OrderPlay op : rec.orderPlays) {
            if (op.seat == s && op.round == f.round && op.circle == f.circle
                    && op.revealFrame <= cursor) {
                out = op.card;
            }
        }
        return out;
    }

    public void refresh() {
        ReplayRecord rec = session.record();
        ReplayRecord.Player p = player();
        if (rec == null || p == null) {
            table.setState(null);
            return;
        }
        table.setResources(List.of(
            new PlayerTable.Res("SUPER", Theme.points(), String.valueOf(vpTotal(p)), null, "очков"),
            new PlayerTable.Res("COIN", Theme.points(), String.valueOf(p.coin), null, "монет"),
            new PlayerTable.Res("KELIUM", Theme.kelium(), String.valueOf(p.kelium),
                String.valueOf(p.keliumCap), "келемий"),
            new PlayerTable.Res("AMMO", Theme.energy(), String.valueOf(p.ammo),
                String.valueOf(p.ammoCap), "боеприпасы"),
            new PlayerTable.Res("TROPHY", Theme.neutral(), String.valueOf(p.trophy),
                String.valueOf(p.trophyCap), "трофеи")));
        List<PlayerTable.SeatTab> tabs = new ArrayList<>();
        for (int s = 0; s < rec.players; s++) {
            tabs.add(new PlayerTable.SeatTab(s, shortName(rec, s), false));
        }
        table.setSeatTabs(tabs, this::setSeat);
        sheet.setSeat(seat);
        String order = revealedOrder(seat);
        BufferedImage dumpBack = null;
        if (p.orderSetAside != null) {
            dumpBack = CardArt.orderBack(p.orderColor);
            if (dumpBack == null) {
                dumpBack = new BufferedImage(10, 16, BufferedImage.TYPE_INT_RGB);
            }
        }
        List<PlayerTable.Trophy> dump = new ArrayList<>();
        int dumpValue = 0;
        for (ReplayRecord.DestroyedToken t : p.destroyedCard) {
            dump.add(new PlayerTable.Trophy(CardArt.trophy(t.type, t.level, t.value), t.value));
            dumpValue += t.value;
        }
        table.setState(new PlayerTable.State(seat, seatName(rec, seat),
            order, order == null ? null : orderFace(order),
            order == null ? null : CardArt.order(order, p.orderColor),
            List.copyOf(p.objectiveHand), List.copyOf(p.superObjectives),
            List.copyOf(p.arsenalHand), List.copyOf(p.arsenalInstalled),
            List.copyOf(p.orderHand), List.copyOf(p.orderPlayed),
            dumpBack, dump, dumpValue, status(), false, CardArt.orderBack(p.orderColor)));
        if (boardZoom.isOpen()) {
            boardZoom.refresh();
        }
    }

    /** Строка под кнопкой хода окна партии — здесь: чей ход на этом шаге. */
    private String status() {
        ReplayRecord.Frame f = session.frame();
        ReplayRecord rec = session.record();
        if (f == null || f.snapshot == null || rec == null) {
            return null;
        }
        if (rec.winner != null && session.cursor() >= session.frameCount() - 1) {
            return "партия окончена";
        }
        Integer active = f.snapshot.active;
        return active == null ? "общая фаза" : "ходит " + shortName(rec, active);
    }

    // ==================== карты ====================

    /** Печатное имя карты: у дублей печати имя в каталоге служебное. */
    public String cardName(String id) {
        var content = session.content();
        if (content != null && id != null) {
            try {
                Map<String, Object> card = content.get("arsenal").find(id);
                if (card != null && card.get("печатное_имя") != null) {
                    return String.valueOf(card.get("печатное_имя"));
                }
            } catch (RuntimeException ignored) {
                // не арсенал
            }
        }
        return Names.card(session.record(), id);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> orderData(String id) {
        var content = session.content();
        if (content == null || id == null) {
            return null;
        }
        try {
            return (Map<String, Object>) content.get("orders").byId(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public OrderCardFace.Info orderFace(String id) {
        return OrderCardFace.Info.of(id, orderData(id));
    }

    /** Лицо любой карты стола: задания, арсенал, приказы (приказ — в цвете колоды места). */
    public BufferedImage anyFace(String id) {
        ReplayRecord.Player p = player();
        return CardArt.any(id, p == null ? null : p.orderColor);
    }

    /** Лицо карты любого места (для выбора в решении). */
    public BufferedImage faceFor(int s, String id) {
        ReplayRecord.Frame f = session.frame();
        String colour = f == null || f.snapshot == null || s >= f.snapshot.players.size()
            ? null : f.snapshot.players.get(s).orderColor;
        return CardArt.any(id, colour);
    }

    private void showZoom(String id, Rectangle inTable) {
        if (inTable == null || host == null) {
            return;
        }
        BufferedImage img = anyFace(id);
        if (img == null) {
            zoom.show(cardName(id), "Карта", Theme.border(), "", -1);
            zoom.setSize(Theme.px(300), Theme.px(420));
        } else {
            zoom.showFace(img, null);
            zoom.setSize(zoom.faceSize(Theme.px(440)));
        }
        Point p = SwingUtilities.convertPoint(table, inTable.x, inTable.y, host);
        int x = Math.max(Theme.px(6), Math.min(p.x + inTable.width / 2 - zoom.getWidth() / 2,
            host.getWidth() - zoom.getWidth() - Theme.px(6)));
        int y = Math.max(Theme.px(6), p.y - zoom.getHeight() - Theme.px(6));
        zoom.setLocation(x, y);
    }

    /** Раскрыть группу карт стола (как в окне партии, только без вариантов). */
    public void openSpread(String group) {
        if (host == null) {
            return;
        }
        if (group.startsWith("board:")) {
            zoom.setVisible(false);
            sheet.setSeat(seat);
            boardZoom.setBounds(0, 0, host.getWidth(), host.getHeight());
            boardZoom.open(sheet, seat, group.substring("board:".length()),
                seatName(session.record(), seat));
            return;
        }
        ReplayRecord.Player p = player();
        if (p == null) {
            return;
        }
        List<CardSpread.Card> cards = new ArrayList<>();
        String title;
        String subtitle = "Щёлкните мимо карт, чтобы сложить их";
        List<String> ids;
        switch (group) {
            case "objectives" -> {
                ids = p.objectiveHand;
                title = "Задания на руке";
            }
            case "super" -> {
                ids = p.superObjectives;
                title = "Супер-задания";
            }
            case "arsenal" -> {
                ids = p.arsenalHand;
                title = "Арсенал: закрытые карты";
            }
            case "installed" -> {
                ids = p.arsenalInstalled;
                title = "Арсенал: установленные карты";
            }
            case "orders" -> {
                ids = p.orderHand;
                title = "Приказы в руке";
            }
            case "played" -> {
                ids = p.orderPlayed;
                title = "Сыграно в этом раунде";
            }
            case "dump" -> {
                ids = List.of();
                title = "Свалка";
                subtitle = "Уничтоженные жетоны врагов на отложенном приказе";
                if (p.orderSetAside != null) {
                    cards.add(new CardSpread.Card(p.orderSetAside,
                        CardArt.order(p.orderSetAside, p.orderColor), cardName(p.orderSetAside),
                        "отложенный приказ — свалка", List.of()));
                }
                for (ReplayRecord.DestroyedToken t : p.destroyedCard) {
                    String nm = t.building ? kelium.report.Labels.buildingName(t.type, t.level)
                        : kelium.report.Labels.unitName(t.type);
                    cards.add(new CardSpread.Card("t" + t.uid,
                        CardArt.trophy(t.type, t.level, t.value), nm,
                        nm + " · трофеев " + t.value, List.of()));
                }
            }
            case "containers" -> {
                ids = List.of();
                title = "Контейнеры под планшетом: " + p.containers;
                subtitle = "Лежат рубашкой вверх — что внутри, узнают при вскрытии спец-действием";
                BufferedImage back = CardArt.back("containers");
                for (int i = 0; i < p.containers; i++) {
                    cards.add(new CardSpread.Card("container" + i, back, "Контейнер",
                        "рубашкой вверх", List.of()));
                }
            }
            default -> {
                return;
            }
        }
        for (String id : ids) {
            cards.add(new CardSpread.Card(id, anyFace(id), cardName(id),
                "installed".equals(group) ? "установлена" : null, List.of()));
        }
        if (cards.isEmpty()) {
            return;
        }
        zoom.setVisible(false);
        spread.setBounds(0, 0, host.getWidth(), host.getHeight());
        spread.open(title, subtitle, cards, Theme.seat(seat), null);
    }

    /** Для прогонщиков: раскрыто ли что-нибудь поверх окна. */
    public boolean popupOpen() {
        return spread.isOpen() || boardZoom.isOpen();
    }
}
