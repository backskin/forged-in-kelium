package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import kelium.report.ReplayRecord;

/**
 * РЕШЕНИЕ НА ШАГЕ — карточкой вопроса в углу поля, как в окне партии («Ваш
 * ход: выберите действие»), только уже решённое: чей вопрос, сам вопрос,
 * что выбрано и из чего выбирали.
 *
 * <p>Карточка ловит мышь только над собой — поле под ней тащится и
 * листается как обычно.
 */
public final class DecisionCard extends JComponent {

    private static final long serialVersionUID = 1L;

    private final Session session;
    private Rectangle card = new Rectangle();
    private boolean folded;

    public DecisionCard(Session session) {
        this.session = session;
        setOpaque(false);
        session.whenFrameChanged(s -> {
            folded = false;
            repaint();
        });
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                folded = !folded;
                repaint();
            }
        });
        setToolTipText("Решение, принятое перед этим шагом. Щелчок — свернуть или развернуть");
    }

    @Override
    public boolean contains(int x, int y) {
        return card.contains(x, y);
    }

    /** Решения текущего шага. */
    private List<ReplayRecord.Decision> decisions() {
        ReplayRecord.Frame f = session.frame();
        return f == null ? List.of() : f.decisions;
    }

    /** Убрать из слов служебные коды в скобках — «(blue_explore)», «(o05)». */
    static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s*\\([a-z0-9_:>.\\-]+\\)", "").replaceAll("\\s{2,}", " ").trim();
    }

    static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        List<ReplayRecord.Decision> ds = decisions();
        ReplayRecord rec = session.record();
        if (ds.isEmpty() || rec == null) {
            card = new Rectangle();
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        int pad = Theme.px(14);
        int w = Math.min(Theme.px(380), getWidth() - Theme.px(24));
        Font head = Theme.font(15, Font.BOLD);
        Font who = Theme.font(12, Font.BOLD);
        Font body = Theme.font(13, Font.PLAIN);
        Font small = Theme.font(11, Font.PLAIN);

        // что рисуем: последнее решение — целиком, прежние — строкой
        List<Object[]> lines = new ArrayList<>();       // {текст, шрифт, цвет, отступ, метка}
        int from = Math.max(0, ds.size() - (folded ? 1 : 4));
        for (int k = from; k < ds.size(); k++) {
            ReplayRecord.Decision d = ds.get(k);
            boolean last = k == ds.size() - 1;
            Color seatInk = Theme.seatInk(d.seat);
            // имя места — только когда решает другой игрок, чем строкой выше
            if (k == from || ds.get(k - 1).seat != d.seat) {
                lines.add(new Object[]{ReplayTable.seatName(rec, d.seat), who, seatInk, 0, null});
            }
            lines.add(new Object[]{cap(clean(d.title)), last ? head : body, Theme.ink(), 0, null});
            ReplayRecord.DecisionOption chosen = d.picked >= 0 && d.picked < d.options.size()
                ? d.options.get(d.picked) : null;
            if (chosen != null) {
                lines.add(new Object[]{"Выбрано: " + optionWords(chosen), body, Theme.accent(),
                    Theme.px(4), "pick"});
            }
            if (last && !folded) {
                int shown = 0;
                for (int i = 0; i < d.options.size() && shown < 6; i++) {
                    if (i == d.picked) {
                        continue;
                    }
                    lines.add(new Object[]{optionWords(d.options.get(i)), small, Theme.ink3(),
                        Theme.px(12), "opt"});
                    shown++;
                }
                int rest = d.total - 1 - shown;
                if (rest > 0) {
                    lines.add(new Object[]{"и ещё вариантов: " + rest, small, Theme.ink3(),
                        Theme.px(12), null});
                }
                if (d.total <= 1) {
                    lines.add(new Object[]{"другого варианта не было", small, Theme.ink3(),
                        Theme.px(12), null});
                }
            }
        }

        // перенос строк по ширине карточки
        List<Object[]> wrapped = new ArrayList<>();
        for (Object[] ln : lines) {
            g.setFont((Font) ln[1]);
            FontMetrics fm = g.getFontMetrics();
            int indent = (Integer) ln[3];
            for (String part : wrap((String) ln[0], fm, w - 2 * pad - indent - Theme.px(10), 3)) {
                wrapped.add(new Object[]{part, ln[1], ln[2], ln[3], ln[4]});
                ln[4] = null;           // метка — только у первой строки
            }
        }
        int h = pad;
        for (Object[] ln : wrapped) {
            g.setFont((Font) ln[1]);
            h += g.getFontMetrics().getHeight();
        }
        h += pad;
        int x = Theme.px(12);
        int y = Theme.px(12);
        card = new Rectangle(x, y, w, h);
        Color seat = Theme.seat(ds.get(ds.size() - 1).seat);
        g.setColor(Theme.alpha(Theme.panel(), 0.94));
        g.fillRoundRect(x, y, w, h, Theme.px(10), Theme.px(10));
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(Theme.pxf(1)));
        g.drawRoundRect(x, y, w, h, Theme.px(10), Theme.px(10));
        g.setColor(seat);
        g.fillRoundRect(x, y, Theme.px(5), h, Theme.px(5), Theme.px(5));
        int ty = y + pad;
        for (Object[] ln : wrapped) {
            g.setFont((Font) ln[1]);
            FontMetrics fm = g.getFontMetrics();
            int tx = x + pad + (Integer) ln[3];
            if ("pick".equals(ln[4])) {
                g.setColor(Theme.accent());
                g.fillOval(tx - Theme.px(2), ty + fm.getAscent() / 2 - Theme.px(2),
                    Theme.px(7), Theme.px(7));
            } else if ("opt".equals(ln[4])) {
                g.setColor(Theme.ink3());
                g.drawOval(tx - Theme.px(2), ty + fm.getAscent() / 2 - Theme.px(1),
                    Theme.px(5), Theme.px(5));
            }
            g.setColor((Color) ln[2]);
            g.drawString((String) ln[0], tx + Theme.px(10), ty + fm.getAscent());
            ty += fm.getHeight();
        }
        g.dispose();
    }

    private String optionWords(ReplayRecord.DecisionOption o) {
        String t = clean(o.text);
        if (o.card != null && (t.isEmpty() || t.contains(o.card))) {
            t = "«" + Names.card(session.record(), o.card) + "»";
        }
        if (o.sub != null && !o.sub.isBlank()) {
            t += " — " + clean(o.sub);
        }
        return t;
    }

    static List<String> wrap(String text, FontMetrics fm, int width, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(probe) > width && line.length() > 0 && out.size() < maxLines - 1) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0) {
            String s = line.toString();
            if (out.size() >= maxLines - 1 && fm.stringWidth(s) > width) {
                while (s.length() > 1 && fm.stringWidth(s + "…") > width) {
                    s = s.substring(0, s.length() - 1);
                }
                s = s + "…";
            }
            out.add(s);
        }
        return out;
    }
}
