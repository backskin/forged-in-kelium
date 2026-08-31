package kelium.gui.kp;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import kelium.gui.replay2.MarkIcons;
import kelium.gui.replay2.Theme;

/**
 * ПОСТОЯННАЯ ПОЛОСА ВСЕХ МЕСТ (блокер приёмки агентом-игроком, 24.08.2026):
 * «я принимаю решение о бое, не видя счёта соперника — дисквалификация».
 * Для каждого места: плашка цветом места с именем, затем ПО, монеты, келемий,
 * БПР и число карт в руках (приказы/задания/арсенал — за столом эти числа
 * видны всем). Своё место подсвечено. Ничего закрытого здесь нет.
 */
public final class OpponentStrip extends JComponent {

    public record Row(int seat, String name, boolean me, int vp, int coin,
                       int kelium, int ammo, int orderCards, int objectiveCards,
                       int arsenalCards, int trophyPoints) {
    }

    private final List<Row> rows = new ArrayList<>();

    public OpponentStrip() {
        setOpaque(false);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
    }

    public void update(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        repaint();
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        return "Открытый счёт всех мест: очки · монеты · келемий · БПР · "
            + "карты в руках (приказы/задания/арсенал) · трофейные очки. "
            + "Полный планшет — в ящике «Планшет»";
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(300), Theme.px(34));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int x = Theme.px(10);
        int h = getHeight();
        for (Row r : rows) {
            int cardW = cardWidth(g, r);
            int top = Theme.px(3);
            int ch = h - Theme.px(6);
            g.setColor(r.me() ? Theme.seatWash(r.seat(), 0.18) : Theme.tile());
            g.fillRoundRect(x, top, cardW, ch, Theme.px(9), Theme.px(9));
            g.setColor(r.me() ? Theme.seat(r.seat()) : Theme.border());
            g.drawRoundRect(x, top, cardW, ch, Theme.px(9), Theme.px(9));

            int cx = x + Theme.px(8);
            int cy = h / 2;
            // плашка места
            g.setColor(Theme.seat(r.seat()));
            g.fillRoundRect(cx, cy - Theme.px(7), Theme.px(4), Theme.px(14), 2, 2);
            cx += Theme.px(9);
            g.setFont(Theme.font(11.5, Font.BOLD));
            g.setColor(Theme.seatInk(r.seat()));
            var fm = g.getFontMetrics();
            String nm = r.name() + (r.me() ? " (вы)" : "");
            g.drawString(nm, cx, cy + (fm.getAscent() - fm.getDescent()) / 2);
            cx += fm.stringWidth(nm) + Theme.px(10);

            cx = stat(g, cx, cy, "SUPER", Theme.points(), String.valueOf(r.vp()));
            cx = stat(g, cx, cy, "COIN", Theme.points(), String.valueOf(r.coin()));
            cx = stat(g, cx, cy, "KELIUM", Theme.kelium(), String.valueOf(r.kelium()));
            cx = stat(g, cx, cy, "AMMO", Theme.energy(), String.valueOf(r.ammo()));
            cx = stat(g, cx, cy, "CARD", Theme.neutral(),
                r.orderCards() + "·" + r.objectiveCards() + "·" + r.arsenalCards());
            cx = stat(g, cx, cy, "TROPHY", Theme.trophy(), String.valueOf(r.trophyPoints()));

            x += cardW + Theme.px(8);
        }
        g.dispose();
    }

    private int cardWidth(Graphics2D g, Row r) {
        g.setFont(Theme.font(11.5, Font.BOLD));
        int w = Theme.px(22) + g.getFontMetrics()
            .stringWidth(r.name() + (r.me() ? " (вы)" : ""));
        g.setFont(Theme.mono(11, Font.BOLD));
        var fm = g.getFontMetrics();
        String cards = r.orderCards() + "·" + r.objectiveCards() + "·" + r.arsenalCards();
        for (String v : List.of(String.valueOf(r.vp()), String.valueOf(r.coin()),
                String.valueOf(r.kelium()), String.valueOf(r.ammo()), cards,
                String.valueOf(r.trophyPoints()))) {
            w += Theme.px(15) + fm.stringWidth(v) + Theme.px(7);
        }
        return w + Theme.px(6);
    }

    private int stat(Graphics2D g, int x, int cy, String icon, Color color, String value) {
        double s = Theme.px(11);
        MarkIcons.paint(g, icon, x + s / 2, cy, s, color);
        g.setFont(Theme.mono(11, Font.BOLD));
        g.setColor(Theme.ink());
        var fm = g.getFontMetrics();
        g.drawString(value, (int) (x + s + Theme.px(3)),
            cy + (fm.getAscent() - fm.getDescent()) / 2);
        return (int) (x + s + Theme.px(3)) + fm.stringWidth(value) + Theme.px(7);
    }
}
