package kelium.gui.replay2;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * КРУЖОК ИЗ КРУЖОЧКОВ — иконка занятости для долгих действий (просьба
 * дизайнера 17.08.2026: на кнопке «Сыграть и показать» вместо статичного
 * текста «играю партию…» должна крутиться живая анимация, а не просто
 * смениться подпись).
 *
 * <p>Восемь точек по кругу с затухающим следом — классический «iOS-спиннер».
 * Крутится сама: {@link #start()} заводит таймер, который двигает фазу и зовёт
 * {@code repaint()} у хозяина-компонента (иконка сама себя не перерисует —
 * Swing рисует {@link Icon} только когда просят родителя).
 */
public final class SpinnerIcon implements Icon {

    private final JComponent owner;
    private final int size;
    private final Color color;
    private double angleDeg = 0;
    private Timer timer;

    public SpinnerIcon(JComponent owner, int size, Color color) {
        this.owner = owner;
        this.size = size;
        this.color = color;
    }

    /** Завести анимацию. Повторный вызов, пока крутится, ничего не делает. */
    public void start() {
        if (timer != null) {
            return;
        }
        timer = new Timer(70, e -> {
            angleDeg = (angleDeg + 28) % 360;
            owner.repaint();
        });
        timer.start();
    }

    /** Остановить анимацию — иконка застывает на текущей фазе. */
    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int dots = 8;
        int r = size / 2;
        int dotR = Math.max(2, size / 9);
        int cx = x + r;
        int cy = y + r;
        for (int i = 0; i < dots; i++) {
            double a = Math.toRadians(angleDeg + i * 360.0 / dots);
            int dx = (int) Math.round(cx + (r - dotR) * Math.cos(a));
            int dy = (int) Math.round(cy + (r - dotR) * Math.sin(a));
            float alpha = 0.15f + 0.85f * i / (dots - 1);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.round(alpha * 255)));
            g.fillOval(dx - dotR, dy - dotR, dotR * 2, dotR * 2);
        }
        g.dispose();
    }
}
