package kelium.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JPanel;

import kelium.report.FieldGeometry;
import kelium.report.ReplayRecord;

/**
 * OrderStrip — «рука на столе»: разыгранные карты приказов одного игрока.
 *
 * <p>Дизайнер просил видеть на экране, что и когда игрок вскрыл: карты
 * появляются по ходу партии, свежая — впереди и в полную силу, прежние
 * отодвигаются в сторону и бледнеют. Карты минималистичные: жирный контур,
 * крупное имя приказа и строчки действий — сыгранные зачёркнуты, доступные
 * читаются. Отдельно помечено то, что рушит планы: <b>совпадение</b> (сверху
 * вместо двух действий одно) и <b>открытая нижняя половина</b>.
 */
public final class OrderStrip extends JPanel {

    private static final Color BG = new Color(0xFBFAF7);
    private static final Color INK = new Color(0x22201B);
    private static final Color MUTED = new Color(0x8A857A);
    private static final Color CUT = new Color(0xB9B3A6);
    private static final Color WARN = new Color(0xC2410C);
    private static final Color OPEN = new Color(0x2E7D32);

    /** Цвета приказов — по смыслу: разработка зелёная, операция красная и т. д. */
    private static final Color DEV = new Color(0x3F9E60);
    private static final Color INF = new Color(0x3B82D0);
    private static final Color OPS = new Color(0xD9534F);
    private static final Color ACQ = new Color(0xB08A2E);
    private static final Color JOKER = new Color(0x7A5AA8);

    // Карты ВЫТЯНУТЫЕ по вертикали, как настоящие: плоские широкие плашки
    // читались плохо и текст в них не влезал (замечание дизайнера 12.08.2026).
    // Это НОМИНАЛЬНЫЙ размер: карта всегда рисуется в этих координатах, а на экран
    // выводится с общим масштабом под высоту полоски (см. paintComponent).
    private static final int CARD_W = 124;
    private static final int CARD_H = 176;
    private static final int GAP = 8;
    /** Ниже этого карту не сжимаем — дальше текст перестаёт читаться. */
    private static final int MIN_CARD_H = 104;

    private final int seat;
    private final List<ReplayRecord.OrderPlay> plays = new ArrayList<>();
    /** Что уже сыграно по каждой карте: индекс карты -> список действий. */
    private final List<List<String>> used = new ArrayList<>();

    public OrderStrip(int seat) {
        this.seat = seat;
        setBackground(BG);
        setOpaque(true);
        setPreferredSize(new Dimension(CARD_W * 2 + GAP * 3, CARD_H + 14));
        setMinimumSize(new Dimension(CARD_W + GAP * 2, 60));
        setToolTipText("<html><div style='width:300px'><b>Разыгранные приказы</b><br>"
            + "Свежая карта — первая. Зачёркнутое действие уже сыграно, "
            + "яркое — ещё доступно.<br>«×1» — сработало совпадение: сверху "
            + "вместо двух действий одно. «↓» — открылась нижняя половина.</div></html>");
        // Полоска низкая — карты рисуются мельче; полоску растянули — крупнее.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                fitCards();
                repaint();
            }
        });
    }

    /** Высота и ширина карты на этом размере полоски (номинал ужимается вместе). */
    private int cardH = CARD_H;
    private int cardW = CARD_W;

    /**
     * ПОДОГНАТЬ КАРТЫ ПОД ПОЛОСКУ.
     *
     * <p>Раньше карта всегда была 124×176, а полоске доставалось ~130 пикселей
     * высоты: низ карты («круг N», «манёвр») просто срезался. Хуже того, карты
     * сдвигались друг на друга на 62 % ширины, и шапки читались как
     * «ПРИОБРЕТЕНИ», а текст нижней карты просвечивал сквозь верхнюю.
     *
     * <p>Теперь карты идут ПОДРЯД, без наложения, и целиком влезают в полоску:
     * рисунок один и тот же, меняется только общий масштаб.
     */
    private void fitCards() {
        int h = getHeight();
        cardH = h <= 0 ? CARD_H : Math.max(MIN_CARD_H, Math.min(CARD_H, h - 10));
        cardW = Math.max(1, Math.round(cardH * (CARD_W / (float) CARD_H)));
        int need = GAP + Math.max(1, plays.size()) * (cardW + GAP);
        Dimension want = new Dimension(need, cardH + 10);
        if (!want.equals(getPreferredSize())) {
            setPreferredSize(want);
            revalidate();
        }
    }

    /**
     * Показать состояние на данном кадре: карты ТЕКУЩЕГО раунда этого игрока и
     * то, что он успел по ним сыграть к этому моменту.
     */
    public void update(ReplayRecord rec, int frameIdx) {
        plays.clear();
        used.clear();
        resolved.clear();
        if (rec == null || rec.frames.isEmpty()) {
            repaint();
            return;
        }
        int idx = Math.max(0, Math.min(frameIdx, rec.frames.size() - 1));
        int round = rec.frames.get(idx).round;
        for (ReplayRecord.OrderPlay op : rec.orderPlays) {
            // Карта появляется на столе в момент ВСКРЫТИЯ — у всех игроков
            // одновременно, ещё до чьих-либо ходов.
            if (op.seat != seat || op.round != round || op.revealFrame > idx) {
                continue;
            }
            plays.add(op);
        }
        // свежая карта — первой
        plays.sort((a, b) -> Integer.compare(b.revealFrame, a.revealFrame));
        for (ReplayRecord.OrderPlay op : plays) {
            used.add(playedSince(rec, op, idx));
            resolved.add(op.turnFrame >= 0 && idx >= op.turnFrame);
        }
        fitCards();
        repaint();
    }

    /**
     * Дошла ли до этой карты очередь К ЭТОМУ КАДРУ. Считается сравнением с
     * кадром хода, а не флагом в записи: при перемотке назад карта должна снова
     * становиться «вскрыта, ждёт хода».
     */
    private final List<Boolean> resolved = new ArrayList<>();

    /**
     * Какие действия игрок сыграл по этой карте: пробегаем кадры от начала его
     * хода до текущего, пока не начался чей-то следующий ход.
     */
    private List<String> playedSince(ReplayRecord rec, ReplayRecord.OrderPlay op, int upTo) {
        List<String> out = new ArrayList<>();
        if (op.turnFrame < 0) {
            return out;      // ход по этой карте ещё не начинался
        }
        for (int i = op.turnFrame + 1; i <= upTo && i < rec.frames.size(); i++) {
            ReplayRecord.Frame f = rec.frames.get(i);
            if ("turn_orders".equals(f.type)) {
                break;          // начался следующий ход — карта закрыта
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

    /** Достать код действия из строки лога вида «   ▪ СНАРЯЖЕНИЕ: …». */
    private static String actionOf(String log) {
        if (log == null) {
            return null;
        }
        String s = log.toLowerCase(Locale.ROOT);
        for (var e : RU.entrySet()) {
            if (s.contains(e.getValue())) {
                return e.getKey();
            }
        }
        return null;
    }

    private static final java.util.Map<String, String> RU = java.util.Map.ofEntries(
        java.util.Map.entry("assembly", "снаряжение"),
        java.util.Map.entry("mining", "добыча"),
        java.util.Map.entry("build", "стройка"),
        java.util.Map.entry("energy_swap", "энергия"),
        java.util.Map.entry("movement", "движение"),
        java.util.Map.entry("combat", "бой"),
        java.util.Map.entry("market", "рынок"),
        java.util.Map.entry("science", "наука"));

    private static final java.util.Map<String, String> ORDER_RU = java.util.Map.of(
        "development", "РАЗРАБОТКА",
        "infrastructure", "ИНФРАСТРУКТУРА",
        "operation", "ОПЕРАЦИЯ",
        "acquisitions", "ПРИОБРЕТЕНИЯ");

    private static Color orderColor(String code) {
        return switch (code) {
            case "development" -> DEV;
            case "infrastructure" -> INF;
            case "operation" -> OPS;
            case "acquisitions" -> ACQ;
            default -> JOKER;
        };
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (plays.isEmpty()) {
            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g.drawString("приказы этого раунда ещё не вскрыты", 8, getHeight() / 2);
            return;
        }
        double k = cardH / (double) CARD_H;
        int x = GAP;
        int y = Math.max(2, (getHeight() - cardH) / 2);
        for (int i = 0; i < plays.size(); i++) {
            if (x + cardW > getWidth() && i > 0) {
                // Место кончилось: честно говорим, сколько карт не показано. Полоска
                // лежит в прокрутке — остальные видны сдвигом вправо.
                g.setColor(MUTED);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                g.drawString("+" + (plays.size() - i), x + 2, y + cardH / 2);
                break;
            }
            // Карта рисуется в НОМИНАЛЬНЫХ координатах 124×176, а на экран попадает
            // с общим масштабом: одна геометрия на любую высоту полоски.
            Graphics2D gc = (Graphics2D) g.create();
            gc.translate(x, y);
            gc.scale(k, k);
            drawCard(gc, plays.get(i), used.get(i), resolved.get(i), 0, 0, i == 0);
            gc.dispose();
            x += cardW + GAP;
        }
    }

    private void drawCard(Graphics2D g, ReplayRecord.OrderPlay op, List<String> done,
                          boolean ready, int x, int y, boolean fresh) {
        Color accent = orderColor(op.top);

        // ПОДЛОЖКА — ВСЕГДА ПЛОТНАЯ. Раньше прозрачность 0,62 накладывалась на всю
        // карту вместе с белым фоном, и текст соседней карты просвечивал наружу:
        // «вскрыта · ждёт своего хо|рынок». Бледнеет только СОДЕРЖИМОЕ прежних карт.
        RoundRectangle2D card = new RoundRectangle2D.Double(x, y, CARD_W, CARD_H, 12, 12);
        g.setColor(Color.WHITE);
        g.fill(card);
        if (!fresh) {
            g.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, 0.72f));
        }
        // ЖИРНЫЙ контур цветом приказа — карта должна читаться с одного взгляда.
        // Пока ход игрока не наступил, карта уже лежит на столе, но её раскладка
        // неизвестна: рисуем контур ПУНКТИРОМ.
        if (ready) {
            g.setStroke(new BasicStroke(fresh ? 3.4f : 2.2f));
        } else {
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND, 0, new float[]{5, 4}, 0));
        }
        g.setColor(accent);
        g.draw(card);
        // ВСЁ СОДЕРЖИМОЕ ОБРЕЗАЕТСЯ КАРТОЙ: длинная строка больше не вылезает за
        // её край и не лезет на соседнюю.
        g.clip(card);

        // шапка с именем приказа
        g.fill(new RoundRectangle2D.Double(x + 1, y + 1, CARD_W - 2, 22, 11, 11));
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        String name = ORDER_RU.getOrDefault(op.top, "БЕЗОПАСНОСТЬ");
        g.drawString(fit(g, name, CARD_W - 34), x + 7, y + 16);

        // метки: совпадение и открытая нижняя половина
        int badgeX = x + CARD_W - 8;
        if (op.coincided && ready) {
            badgeX = badge(g, "×1", badgeX, y + 16, WARN);
        }
        if (op.bottomOpen && ready) {
            badgeX = badge(g, "↓", badgeX, y + 16, OPEN);
        }

        int ty = y + 40;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        for (String a : op.topActions) {
            boolean played = done.contains(a);
            drawAction(g, RU.getOrDefault(a, a), x + 9, ty, played, INK);
            ty += 15;
        }
        if (!ready) {
            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
            g.drawString(fit(g, "ждёт своего хода", CARD_W - 18), x + 9, ty);
            ty += 13;
        } else if (op.topAllowed < op.topActions.size()) {
            g.setColor(WARN);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.drawString("только одно из двух", x + 9, ty);
            ty += 13;
        }
        if (ready && op.bottom != null && !op.bottomActions.isEmpty()) {
            g.setColor(op.bottomOpen ? OPEN : CUT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            String low = ORDER_RU.getOrDefault(op.bottom, op.bottom).toLowerCase(Locale.ROOT);
            g.drawString((op.bottomOpen ? "↓ " : "× ") + fit(g, low, CARD_W - 24), x + 9, ty);
            ty += 13;
            if (op.bottomOpen) {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                for (String a : op.bottomActions) {
                    drawAction(g, RU.getOrDefault(a, a), x + 15, ty, done.contains(a), OPEN);
                    ty += 14;
                }
            }
        }
        if (op.maneuver) {
            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.drawString("⇢ манёвр", x + 9, y + CARD_H - 6);
        }
        g.setColor(MUTED);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        g.drawString("круг " + op.circle, x + CARD_W - 44, y + CARD_H - 6);

        g.setComposite(java.awt.AlphaComposite.SrcOver);
    }

    /** Строка действия: сыгранное зачёркнуто и бледное, доступное — жирное. */
    private void drawAction(Graphics2D g, String text, int x, int y,
                            boolean played, Color live) {
        g.setColor(played ? CUT : live);
        g.drawString(text, x, y);
        if (played) {
            int w = g.getFontMetrics().stringWidth(text);
            g.setStroke(new BasicStroke(1.4f));
            g.drawLine(x, y - 4, x + w, y - 4);
        }
    }

    /** Кружок-метка справа в шапке; возвращает новый левый край. */
    private int badge(Graphics2D g, String text, int right, int baseline, Color color) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        int w = g.getFontMetrics().stringWidth(text) + 8;
        g.setColor(Color.WHITE);
        g.fillRoundRect(right - w, baseline - 12, w, 15, 6, 6);
        g.setColor(color);
        g.drawRoundRect(right - w, baseline - 12, w, 15, 6, 6);
        g.drawString(text, right - w + 4, baseline - 1);
        return right - w - 4;
    }

    private static String fit(Graphics2D g, String text, int maxW) {
        if (g.getFontMetrics().stringWidth(text) <= maxW) {
            return text;
        }
        String s = text;
        while (s.length() > 2 && g.getFontMetrics().stringWidth(s + "…") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    /** Сколько карт сейчас показано (для тестов). */
    int cardsShown() {
        return plays.size();
    }

    /** Что уже сыграно по карте с этим номером в полоске (для тестов). */
    List<String> usedOn(int index) {
        return index < used.size() ? used.get(index) : List.of();
    }

    /** Цвет места — чтобы полоска приказов совпадала с зоной игрока. */
    public Color seatColor() {
        return Color.decode(FieldGeometry.SEAT_STROKE[seat % 4]);
    }
}
