package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;
import java.util.function.LongConsumer;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * СИД ПАРТИИ — рисованное поле с числом и кнопкой «бросить».
 *
 * <p>Сид набирается прямо здесь: щелчок по числу открывает набор, цифры идут с
 * клавиатуры, {@code Enter} закрепляет, {@code Esc} отменяет. Стокового поля
 * ввода тут нет намеренно — в игровых зонах их не бывает.
 *
 * <p>Зачем игроку это число: одинаковый сид, та же раскладка и тот же состав
 * соперников дают ровно ту же партию. Так повторяют спорную ситуацию.
 */
public final class SeedBox extends JComponent {

    private final LongConsumer onChange;
    private final Random rng = new Random();
    private long value;
    private boolean editing;
    private String typed = "";
    private boolean hoverDice;

    public SeedBox(long initial, LongConsumer onChange) {
        this.value = initial;
        this.onChange = onChange;
        setOpaque(false);
        setFocusable(true);
        setPreferredSize(new Dimension(Theme.px(150), Theme.px(28)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(28)));
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                boolean d = e.getX() >= getWidth() - diceW();
                if (d != hoverDice) {
                    hoverDice = d;
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverDice = false;
                setCursor(Cursor.getDefaultCursor());
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getX() >= getWidth() - diceW()) {
                    reroll();
                } else {
                    editing = true;
                    typed = "";
                    requestFocusInWindow();
                    repaint();
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!editing) {
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    commit();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    editing = false;
                    repaint();
                } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && !typed.isEmpty()) {
                    typed = typed.substring(0, typed.length() - 1);
                    repaint();
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                if (editing && Character.isDigit(e.getKeyChar()) && typed.length() < 12) {
                    typed += e.getKeyChar();
                    repaint();
                }
            }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (editing) {
                    commit();
                }
            }
        });
    }

    private void commit() {
        if (!typed.isEmpty()) {
            try {
                set(Long.parseLong(typed));
            } catch (NumberFormatException ignore) {
                // набрали что-то невозможно длинное — оставляем прежний сид
            }
        }
        editing = false;
        repaint();
    }

    /** Бросить новый сид. */
    public void reroll() {
        set(100000L + rng.nextInt(900000));
    }

    public long value() {
        return value;
    }

    public void set(long v) {
        if (v == value) {
            return;
        }
        value = v;
        repaint();
        if (onChange != null) {
            onChange.accept(value);
        }
    }

    private int diceW() {
        return Theme.px(30);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g.setColor(Theme.tile());
        g.fillRoundRect(0, 0, w - 1, h - 1, Theme.px(6), Theme.px(6));
        g.setColor(editing ? Theme.accent() : Theme.border());
        g.setStroke(new BasicStroke(editing ? Theme.pxf(1.6) : 1f));
        g.drawRoundRect(0, 0, w - 1, h - 1, Theme.px(6), Theme.px(6));

        g.setFont(Theme.caption());
        g.setColor(Theme.ink3());
        g.drawString("СИД", Theme.px(8), (h + g.getFontMetrics().getAscent()) / 2 - 1);

        g.setFont(Theme.mono(13, Font.BOLD));
        g.setColor(editing ? Theme.accent() : Theme.ink());
        var fm = g.getFontMetrics();
        String text = editing ? (typed.isEmpty() ? "…" : typed) : String.valueOf(value);
        g.drawString(text, w - diceW() - Theme.px(6) - fm.stringWidth(text),
            (h + fm.getAscent() - fm.getDescent()) / 2);

        // кнопка «бросить»: две точки в скруглённом квадрате — жест «перебросить»
        int bx = w - diceW() + Theme.px(3);
        int by = (h - Theme.px(20)) / 2;
        g.setColor(hoverDice ? Theme.hover() : Theme.paper());
        g.fillRoundRect(bx, by, Theme.px(20), Theme.px(20), Theme.px(5), Theme.px(5));
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(bx, by, Theme.px(20), Theme.px(20), Theme.px(5), Theme.px(5));
        g.setColor(Theme.ink2());
        double cx = bx + Theme.px(10);
        double cy = by + Theme.px(10);
        double r = Theme.px(6);
        g.setStroke(new BasicStroke(Theme.pxf(1.5), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 40, 280,
            java.awt.geom.Arc2D.OPEN));
        int ah = Theme.px(3);
        g.drawPolyline(new int[]{(int) (cx + r * 0.55) - ah, (int) (cx + r * 0.9),
            (int) (cx + r * 0.9) + ah},
            new int[]{(int) (cy - r * 0.75), (int) (cy - r * 0.95), (int) (cy - r * 0.35)}, 3);
        g.dispose();
    }
}
