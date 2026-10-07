package kelium.report;

import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ГДЕ СТАВИТЬ ЖЕТОН ТЕХНИКИ В ГЕКСЕ (дизайнер 07.10.2026, после многих попыток
 * подобрать число «на глаз»).
 *
 * <p>Правило: жетон вместе со своей ТОЛЩИНОЙ (протянутым торцом) стоит на РАВНОМ
 * отступе от двух границ — внутренней, где начинается серая рамка неба в
 * середине гекса, и внешней, края гекса. Не впритык к краю, не у центра.
 *
 * <p>Считается по настоящей непрозрачности картинки: берутся точки силуэта и те
 * же точки, сдвинутые на вектор торца, и бисекцией по радиусу посадки ищется
 * такой, при котором ближайшая точка одинаково далека от рамки неба и от
 * ближайшей стороны гекса. Одно и то же для игры, разбора партии и книги —
 * отличается только вектор торца.
 */
final class ПосадкаТехники {
    /** Радиус внешнего угла рамки неба в долях радиуса гекса (замер по field/hex.png: 150 из 515 px). */
    static final double РАМКА_НЕБА = 0.291;

    private record Ключ(BufferedImage tex, long ширина, long угол, long поворот, long размер,
                        long ex, long ey) { }

    private static final ConcurrentHashMap<Ключ, Double> КЭШ = new ConcurrentHashMap<>();

    private ПосадкаТехники() { }

    /**
     * @param w   ширина жетона на поле
     * @param face направление посадки, градусы (общий угол двух сторон)
     * @param rot поворот картинки жетона, градусы
     * @param size радиус гекса
     * @param ex,ey вектор торца: куда протянута толщина
     * @return расстояние от центра гекса до центра картинки жетона
     */
    static double радиус(BufferedImage tex, double w, double face, double rot, double size,
                         double ex, double ey) {
        Ключ k = new Ключ(tex, Math.round(w * 100), Math.round(face * 100), Math.round(rot * 100),
            Math.round(size * 100), Math.round(ex * 100), Math.round(ey * 100));
        return КЭШ.computeIfAbsent(k, x -> решить(tex, w, face, rot, size, ex, ey));
    }

    private static double решить(BufferedImage tex, double w, double face, double rot, double size,
                                 double ex, double ey) {
        int шаг = Math.max(2, Math.min(tex.getWidth(), tex.getHeight()) / 120);
        double s = w / tex.getWidth();
        double ca = Math.cos(Math.toRadians(rot)), sa = Math.sin(Math.toRadians(rot));
        int n = 0;
        double[] px = new double[(tex.getWidth() / шаг + 1) * (tex.getHeight() / шаг + 1) * 2];
        double[] py = new double[px.length];
        for (int y = 0; y < tex.getHeight(); y += шаг) {
            for (int x = 0; x < tex.getWidth(); x += шаг) {
                if ((tex.getRGB(x, y) >>> 24) < 128) {
                    continue;
                }
                double dx = (x - tex.getWidth() / 2.0) * s;
                double dy = (y - tex.getHeight() / 2.0) * s;
                double ux = dx * ca - dy * sa;
                double uy = dx * sa + dy * ca;
                px[n] = ux; py[n++] = uy;
                px[n] = ux + ex; py[n++] = uy + ey;
            }
        }
        double ap = FieldGeometry.apothem(size);
        double aIn = FieldGeometry.apothem(size * РАМКА_НЕБА);
        double fx = Math.cos(Math.toRadians(face)), fy = Math.sin(Math.toRadians(face));
        double[] nx = new double[6], ny = new double[6];
        for (int i = 0; i < 6; i++) {
            nx[i] = Math.cos(Math.toRadians(FieldGeometry.edgeAngle(i)));
            ny[i] = Math.sin(Math.toRadians(FieldGeometry.edgeAngle(i)));
        }
        double lo = size * 0.1, hi = size * 1.0;
        for (int it = 0; it < 40; it++) {
            double r = (lo + hi) / 2;
            double cx = fx * r, cy = fy * r;
            double внешний = 1e18, внутренний = 1e18;
            for (int i = 0; i < n; i++) {
                double x = cx + px[i], y = cy + py[i];
                double maxIn = -1e18;
                for (int e = 0; e < 6; e++) {
                    double d = x * nx[e] + y * ny[e];
                    внешний = Math.min(внешний, ap - d);
                    maxIn = Math.max(maxIn, d - aIn);
                }
                внутренний = Math.min(внутренний, maxIn);
            }
            // дальше от центра: до края меньше, до рамки неба больше
            if (внешний > внутренний) {
                lo = r;
            } else {
                hi = r;
            }
        }
        return (lo + hi) / 2;
    }
}
