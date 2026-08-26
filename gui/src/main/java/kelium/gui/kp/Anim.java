package kelium.gui.kp;

import java.util.function.DoubleConsumer;

import javax.swing.Timer;

/**
 * Маленький аниматор «Командного пункта» — по правилам скилла интерфейса:
 * такт 16 мс, панели 120–180 мс, ПРЕРВАННОЕ движение продолжается с текущего
 * нарисованного места (новый {@link #play} на том же держателе перехватывает
 * прогресс, а не прыгает к началу).
 */
public final class Anim {

    private Timer timer;
    private double value;

    /** Текущее значение 0..1 (что реально нарисовано). */
    public double value() {
        return value;
    }

    /**
     * Плавно провести {@code value} к {@code target} за {@code ms} от ТЕКУЩЕГО
     * значения. {@code onTick} зовётся на каждом такте (обычно repaint),
     * {@code onDone} — в конце (можно null).
     */
    public void play(double target, int ms, DoubleConsumer onTick, Runnable onDone) {
        if (timer != null) {
            timer.stop();
        }
        double from = value;
        double span = target - from;
        if (Math.abs(span) < 0.001) {
            value = target;
            onTick.accept(value);
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        long start = System.nanoTime();
        timer = new Timer(16, e -> {
            double t = Math.min(1.0, (System.nanoTime() - start) / 1_000_000.0 / ms);
            // плавный въезд-выезд без библиотек: кубическая S-кривая
            double eased = t * t * (3 - 2 * t);
            value = from + span * eased;
            onTick.accept(value);
            if (t >= 1.0) {
                ((Timer) e.getSource()).stop();
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
        timer.start();
    }

    /** Мгновенно поставить значение (для стартовых состояний). */
    public void snap(double v) {
        if (timer != null) {
            timer.stop();
        }
        value = v;
    }
}
