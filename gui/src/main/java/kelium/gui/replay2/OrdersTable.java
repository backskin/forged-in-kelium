package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;

import kelium.report.ReplayRecord;

/**
 * OrdersTable — РАЗЫГРАННЫЕ ПРИКАЗЫ ИГРОКА КАК ТАБЛИЦА.
 *
 * <p>В версии 1.0 четыре карты накладывались друг на друга со сдвигом 62 % и
 * прозрачностью 0,62, применённой ко ВСЕЙ карте вместе с белой подложкой. Итог:
 * текст первой карты читался сквозь вторую («вскрыта · ждёт своего хо|рынок»), шапки
 * обрезались посередине слова, свежая карта лежала ПОД старыми, а справа при этом
 * оставалось пустое место. Наложение — приём для веера в руке; здесь это просто
 * список того, что игрок разыграл, и его надо показывать таблицей.
 *
 * <p>Слоты идут по кругам слева направо, без наложения. Не хватает ширины — карты
 * сжимаются до КОРЕШКОВ с полной карточкой в подсказке, а не обрезаются.
 */
public final class OrdersTable extends JComponent {

    private static final long serialVersionUID = 1L;

    private static final int CARD_W = 132;
    private static final int CARD_H = 184;
    private static final int SPINE_W = 34;
    private static final int GAP = 8;

    private final Session session;
    private int seat;
    private final List<ReplayRecord.OrderPlay> plays = new ArrayList<>();
    private final List<List<String>> used = new ArrayList<>();
    private final List<Boolean> ready = new ArrayList<>();
    private final Map<Integer, Rectangle> bounds = new LinkedHashMap<>();

    public OrdersTable(Session session, int seat) {
        this.session = session;
        this.seat = seat;
        setOpaque(true);
        setFont(Theme.body());
        ToolTipManager.sharedInstance().registerComponent(this);
        session.whenFrameChanged(s -> refresh());
        session.whenRecordChanged(s -> refresh());
    }

    public void setSeat(int seat) {
        this.seat = seat;
        refresh();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(CARD_W * 2 + GAP * 3), Theme.px(CARD_H + 12));
    }

    /** Пересобрать состояние карт под текущий кадр. */
    private void refresh() {
        plays.clear();
        used.clear();
        ready.clear();
        ReplayRecord rec = session.record();
        if (rec == null || rec.frames.isEmpty()) {
            repaint();
            return;
        }
        int idx = session.cursor();
        int round = session.frame().round;
        for (ReplayRecord.OrderPlay op : rec.orderPlays) {
            if (op.seat == seat && op.round == round && op.revealFrame <= idx) {
                plays.add(op);
            }
        }
        // ЧЕТЫРЕ МЕСТА ВСЕГДА. Раньше карты появлялись по одной, и вся полоса
        // ездила туда-сюда каждый круг (замечание дизайнера 13.08.2026). Теперь
        // места стоят на месте с начала раунда, а карта в них просто меняет
        // состояние: «ещё не вскрыта» → «ждёт своего хода» → сыграна.
        roundOver = roundFinished(rec, round, idx);
        // по кругам слева направо: свежая справа, как её и кладут на стол
        plays.sort((a, b) -> Integer.compare(a.circle, b.circle));
        for (ReplayRecord.OrderPlay op : plays) {
            used.add(playedSince(rec, op, idx));
            ready.add(op.turnFrame >= 0 && idx >= op.turnFrame);
        }
        repaint();
    }

    /** Сколько кругов в раунде — столько мест под карты приказов. */
    private static final int CIRCLES = 4;
    /** Раунд уже закончился к текущему кадру? Тогда невскрытых карт не будет. */
    private boolean roundOver;

    /** Раунд закончен, если дальше по записи (до курсора) начался следующий. */
    private static boolean roundFinished(ReplayRecord rec, int round, int upTo) {
        for (int i = Math.min(upTo, rec.frames.size() - 1); i >= 0; i--) {
            if (rec.frames.get(i).round > round) {
                return true;
            }
        }
        return upTo >= rec.frames.size() - 1
            && rec.frames.get(rec.frames.size() - 1).round == round
            && "game_end".equals(rec.frames.get(rec.frames.size() - 1).type);
    }

    /** Что игрок сыграл по этой карте к текущему кадру. */
    private List<String> playedSince(ReplayRecord rec, ReplayRecord.OrderPlay op, int upTo) {
        List<String> out = new ArrayList<>();
        if (op.turnFrame < 0) {
            return out;
        }
        for (int i = op.turnFrame + 1; i <= upTo && i < rec.frames.size(); i++) {
            ReplayRecord.Frame f = rec.frames.get(i);
            if ("turn_orders".equals(f.type)) {
                break;
            }
            if ("action".equals(f.type) && f.seat != null && f.seat == seat) {
                String a = actionOf(f.log);
                if (a != null && !out.contains(a)) {
                    out.add(a);
                }
            }
        }
        return out;
    }

    private static String actionOf(String log) {
        if (log == null) {
            return null;
        }
        String s = log.toLowerCase(Locale.ROOT);
        for (String code : new String[]{"assembly", "mining", "build", "energy_swap",
                                        "movement", "combat", "market", "science"}) {
            if (s.contains(Names.action(code))) {
                return code;
            }
        }
        return null;
    }

    // ==================== отрисовка ====================
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.panel());
        g.fillRect(0, 0, getWidth(), getHeight());
        bounds.clear();

        int n = CIRCLES;
        int avail = getWidth() - GAP;
        int cardW = Theme.px(CARD_W);
        int cardH = Math.min(Theme.px(CARD_H), getHeight() - Theme.px(8));
        boolean spines = n * (cardW + GAP) > avail;
        // Корешки: все карты видны как узкие полоски, последняя — целиком
        int spineW = Theme.px(SPINE_W);
        int y = Math.max(Theme.px(2), (getHeight() - cardH) / 2);
        int x = GAP;
        for (int i = 0; i < n; i++) {
            boolean last = i == n - 1;
            int w = spines && !last ? spineW : cardW;
            Rectangle r = new Rectangle(x, y, w, cardH);
            bounds.put(i, r);
            if (i < plays.size()) {
                drawCard(g, plays.get(i), used.get(i), ready.get(i), r, last,
                    spines && !last);
            } else {
                drawEmptySlot(g, i + 1, r);
            }
            x += w + GAP;
        }
        g.dispose();
    }

    /**
     * ПУСТОЕ МЕСТО ПОД КАРТУ круга: карта этого круга ещё не вскрыта — или раунд
     * кончился раньше, и её так и не сыграли. Место стоит всегда, чтобы полоса не
     * прыгала при каждом круге.
     */
    private void drawEmptySlot(Graphics2D g, int circle, Rectangle r) {
        RoundRectangle2D card = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height,
            Theme.R_OVERLAY * 2, Theme.R_OVERLAY * 2);
        g.setColor(Theme.alpha(Theme.tile(), 0.5));
        g.fill(card);
        g.setColor(Theme.alpha(Theme.border(), 0.9));
        g.setStroke(new BasicStroke(Theme.pxf(1.2), BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND, 0, new float[]{Theme.px(4), Theme.px(4)}, 0));
        g.draw(card);
        g.setFont(Theme.font(11, Font.PLAIN));
        g.setColor(Theme.ink3());
        g.drawString(roundOver ? "не сыграна" : "ещё не вскрыта",
            r.x + Theme.px(8), r.y + Theme.px(22));
        g.setFont(Theme.font(9, Font.PLAIN));
        g.drawString("круг " + circle, r.x + Theme.px(8), r.y + r.height - Theme.px(8));
    }

    private void drawCard(Graphics2D g, ReplayRecord.OrderPlay op, List<String> done,
                          boolean isReady, Rectangle r, boolean fresh, boolean spine) {
        Color accent = orderColour(op.top);
        RoundRectangle2D card = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height,
            Theme.R_OVERLAY * 2, Theme.R_OVERLAY * 2);
        // ПОДЛОЖКА ВСЕГДА ПЛОТНАЯ: сквозь неё ничего не просвечивает
        g.setColor(Theme.isDark() ? Theme.tile() : Color.WHITE);
        g.fill(card);
        g.setColor(accent);
        if (isReady) {
            g.setStroke(new BasicStroke(fresh ? Theme.pxf(2.4) : Theme.pxf(1.4)));
        } else {
            g.setStroke(new BasicStroke(Theme.pxf(1.6), BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND, 0, new float[]{Theme.px(4), Theme.px(3)}, 0));
        }
        g.draw(card);
        // Содержимое обрезается картой — ни одна строка не вылезет на соседнюю
        java.awt.Shape clip = g.getClip();
        g.clip(card);

        int headH = Theme.px(20);
        g.setColor(accent);
        g.fill(new RoundRectangle2D.Double(r.x + 1, r.y + 1, r.width - 2, headH,
            Theme.R_OVERLAY, Theme.R_OVERLAY));
        g.setColor(Color.WHITE);
        // НАЗВАНИЕ ПРИКАЗА — плакатным шрифтом: это имя карты, а не подпись
        // интерфейса (решение дизайнера 13.08.2026).
        g.setFont(Theme.display(10));
        String name = Names.order(op.top);
        if (spine) {
            // на корешке — только первая буква и круг
            g.drawString(name.substring(0, 1), r.x + Theme.px(6), r.y + Theme.px(14));
            g.setColor(Theme.ink3());
            g.setFont(Theme.font(9, Font.PLAIN));
            g.drawString("к" + op.circle, r.x + Theme.px(4), r.y + r.height - Theme.px(5));
            g.setClip(clip);
            return;
        }
        g.drawString(clip(g, name, r.width - Theme.px(10)), r.x + Theme.px(6),
            r.y + Theme.px(14));

        int ty = r.y + headH + Theme.px(14);
        g.setFont(Theme.font(11, Font.BOLD));
        for (String a : op.topActions) {
            boolean played = done.contains(a);
            drawAction(g, Names.action(a), r.x + Theme.px(8), ty, played, Theme.ink(), isReady);
            ty += Theme.px(14);
        }
        g.setFont(Theme.font(10, Font.PLAIN));
        if (!isReady) {
            g.setColor(Theme.ink3());
            g.drawString("ждёт своего хода", r.x + Theme.px(8), ty);
            ty += Theme.px(13);
        } else if (op.coincided || op.topAllowed < op.topActions.size()) {
            g.setColor(Theme.bad());
            g.drawString("совпадение: одно из двух", r.x + Theme.px(8), ty);
            ty += Theme.px(13);
        }
        if (isReady && op.bottom != null && !op.bottomActions.isEmpty()) {
            g.setColor(op.bottomOpen ? Theme.good() : Theme.ink3());
            g.drawString((op.bottomOpen ? "↓ " : "× ")
                + clip(g, Names.order(op.bottom).toLowerCase(Locale.ROOT),
                       r.width - Theme.px(24)), r.x + Theme.px(8), ty);
            ty += Theme.px(13);
            if (op.bottomOpen) {
                g.setFont(Theme.font(11, Font.BOLD));
                for (String a : op.bottomActions) {
                    drawAction(g, Names.action(a), r.x + Theme.px(14), ty,
                        done.contains(a), Theme.good(), true);
                    ty += Theme.px(13);
                }
            }
        }
        g.setFont(Theme.font(9, Font.PLAIN));
        g.setColor(Theme.ink3());
        if (op.maneuver) {
            g.drawString("манёвр", r.x + Theme.px(8), r.y + r.height - Theme.px(6));
        }
        String circle = "круг " + op.circle;
        g.drawString(circle, r.x + r.width - Theme.px(6)
            - g.getFontMetrics().stringWidth(circle), r.y + r.height - Theme.px(6));
        g.setClip(clip);
    }

    /** Строка действия: сыгранное зачёркнуто и бледное, доступное — в полную силу. */
    private void drawAction(Graphics2D g, String text, int x, int y, boolean played,
                            Color live, boolean isReady) {
        g.setColor(played ? Theme.ink3() : (isReady ? live : Theme.ink2()));
        g.drawString(text, x, y);
        if (played) {
            int w = g.getFontMetrics().stringWidth(text);
            g.setStroke(new BasicStroke(1f));
            int my = y - g.getFontMetrics().getAscent() / 2 + 1;
            g.drawLine(x, my, x + w, my);
        }
    }

    private static Color orderColour(String code) {
        return switch (code) {
            case "development" -> new Color(0x3F9E60);
            case "infrastructure" -> new Color(0x3B82D0);
            case "operation" -> new Color(0xD9534F);
            case "acquisitions" -> new Color(0xB08A2E);
            default -> new Color(0x7A5AA8);
        };
    }

    private static String clip(Graphics2D g, String s, int width) {
        if (g.getFontMetrics().stringWidth(s) <= width) {
            return s;
        }
        String t = s;
        while (t.length() > 2 && g.getFontMetrics().stringWidth(t + "…") > width) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        for (Map.Entry<Integer, Rectangle> en : bounds.entrySet()) {
            if (!en.getValue().contains(e.getPoint())) {
                continue;
            }
            ReplayRecord.OrderPlay op = plays.get(en.getKey());
            List<String> done = used.get(en.getKey());
            StringBuilder sb = new StringBuilder();
            sb.append("Круг ").append(op.circle).append(" · ").append(Names.order(op.top));
            sb.append("\nКарта: «").append(Names.card(session.record(), op.card)).append('»');
            sb.append("\nСверху: ");
            List<String> tops = new ArrayList<>();
            for (String a : op.topActions) {
                tops.add(Names.action(a) + (done.contains(a) ? " — сыграно" : ""));
            }
            sb.append(String.join(", ", tops));
            if (op.coincided) {
                sb.append("\nСработало СОВПАДЕНИЕ: вместо двух действий сверху доступно "
                    + "только одно.");
            }
            if (op.bottom != null) {
                sb.append("\nСнизу: ").append(Names.order(op.bottom).toLowerCase(Locale.ROOT))
                  .append(op.bottomOpen ? " — половина ОТКРЫТА" : " — закрыта");
            }
            if (op.maneuver) {
                sb.append("\nБыл манёвр.");
            }
            return Ui2.tip(sb.toString());
        }
        return Ui2.tip("Приказы этого раунда: что вскрыто и что по ним сыграно.");
    }
}
