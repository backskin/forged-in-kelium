package kelium.report;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Zones — РАЗМЕТКА ЗОН НА ТЕКСТУРЕ ЖЕТОНА.
 *
 * <p>Художник рисует картинку жетона, а рядом кладёт вторую — маску, где плоскими
 * цветами помечено, куда приложение ставит свои значки:
 *
 * <ul>
 *   <li><b>красный</b> {@code #FF0000} — ЯЧЕЙКИ ПОД ЭНЕРГИЮ. Каждый квадрат рисуется
 *       отдельным пятном; угол поворота считается сам, поэтому квадраты можно
 *       ставить как угодно;</li>
 *   <li><b>синий</b> {@code #0080FF} — ПЛОЩАДЬ ПОЯВЛЕНИЯ энергии (ЦУ и
 *       энергостанция): любая форма, кубики лягут внутри;</li>
 *   <li><b>зелёный</b> {@code #00C000} — МЕСТО ПОД ПОДПИСЬ: код здания и уровень.</li>
 * </ul>
 *
 * <p>Почему отдельным файлом, а не цветом в самой картинке: иначе художник обязан
 * держать точные значения цвета, не может сглаживать края и не может нарисовать на
 * этих пикселях ничего своего.
 *
 * <p>Разметка живёт В КООРДИНАТАХ ЖЕТОНА, поэтому при повороте жетона поворачивается
 * вместе с ним — и текстура, и ячейки, и кубики в них.
 */
public final class Zones {

    /** Ячейка под кубик: центр, размеры и угол — в пикселях маски. */
    public record Slot(double cx, double cy, double w, double h, double angleDeg) {
    }

    /** Пятно свободной формы: центр, главная ось и размеры вдоль неё. */
    public record Area(double cx, double cy, double w, double h, double angleDeg) {
    }

    private static final Map<String, Zones> CACHE = new HashMap<>();
    private static final Zones EMPTY = new Zones(0, 0, List.of(), null, null);

    private final int maskW;
    private final int maskH;
    private final List<Slot> slots;
    private final Area energy;
    private final double[] label;

    private Zones(int maskW, int maskH, List<Slot> slots, Area energy, double[] label) {
        this.maskW = maskW;
        this.maskH = maskH;
        this.slots = slots;
        this.energy = energy;
        this.label = label;
    }

    public boolean isEmpty() {
        return slots.isEmpty() && energy == null && label == null;
    }

    public List<Slot> slots() {
        return slots;
    }

    public Area energyArea() {
        return energy;
    }

    /** Точка под подпись (в пикселях маски) или null. */
    public double[] labelSpot() {
        return label == null ? null : label.clone();
    }

    public int maskWidth() {
        return maskW;
    }

    public int maskHeight() {
        return maskH;
    }

    /**
     * Разметка для ключа текстуры (без расширения). Ищется файл
     * {@code <ключ>.zones.png} рядом с самой текстурой; нет файла — пустая разметка.
     */
    public static synchronized Zones of(String key, Path root) {
        Zones got = CACHE.get(key);
        if (got != null) {
            return got;
        }
        Zones z = EMPTY;
        if (root != null) {
            Path file = key.contains("/")
                ? root.resolve(key.replace('/', java.io.File.separatorChar) + ".zones.png")
                : root.resolve("token").resolve(key + ".zones.png");
            if (Files.isReadable(file)) {
                try {
                    BufferedImage img = ImageIO.read(file.toFile());
                    // НЕТРОНУТАЯ ЗАГОТОВКА МАСКИ — это ещё не разметка: пока
                    // художник её не правил, энергия и подпись считаются по-старому.
                    if (img != null
                            && !Textures.isUntouchedStub(key + ".zones.png", img)) {
                        z = parse(img);
                    }
                } catch (IOException e) {
                    z = EMPTY;         // битая маска не должна ломать показ
                }
            }
        }
        CACHE.put(key, z);
        return z;
    }

    /** Забыть разобранное — нужно при смене папки текстур. */
    public static synchronized void forget() {
        CACHE.clear();
    }

    // ==================== разбор маски ====================
    private static final int RED = 0;
    private static final int BLUE = 1;
    private static final int GREEN = 2;

    /** Разобрать готовую картинку маски — этим же пользуются тесты. */
    public static Zones parse(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] kind = new int[w * h];
        java.util.Arrays.fill(kind, -1);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                kind[y * w + x] = classify(img.getRGB(x, y));
            }
        }
        List<Slot> slots = new ArrayList<>();
        Area energy = null;
        double[] label = null;

        boolean[] seen = new boolean[w * h];
        List<double[]> bluePixels = new ArrayList<>();
        List<double[]> greenPixels = new ArrayList<>();
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] == BLUE) {
                bluePixels.add(new double[]{i % w, i / w});
            } else if (kind[i] == GREEN) {
                greenPixels.add(new double[]{i % w, i / w});
            }
        }
        // КРАСНЫЕ ПЯТНА — по одному на ячейку, каждое считается отдельно: у них
        // свои размеры и свой угол.
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] != RED || seen[i]) {
                continue;
            }
            List<double[]> blob = flood(kind, seen, w, h, i, RED);
            if (blob.size() < 9) {
                continue;              // случайные точки-одиночки пропускаем
            }
            Area a = principal(blob);
            slots.add(new Slot(a.cx(), a.cy(), a.w(), a.h(), a.angleDeg()));
        }
        // Ячейки идут в порядке слева направо и сверху вниз — так их нумерация
        // совпадает с тем, как их видит человек.
        slots.sort((p, q) -> {
            int byY = Double.compare(p.cy(), q.cy());
            return Math.abs(p.cy() - q.cy()) > Math.max(p.h(), q.h()) * 0.6
                ? byY : Double.compare(p.cx(), q.cx());
        });
        if (!bluePixels.isEmpty()) {
            energy = principal(bluePixels);
        }
        if (!greenPixels.isEmpty()) {
            Area g = principal(greenPixels);
            label = new double[]{g.cx(), g.cy()};
        }
        return new Zones(w, h, List.copyOf(slots), energy, label);
    }

    /** К какому смыслу отнести цвет пикселя; −1 — ни к какому. */
    private static int classify(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a < 128) {
            return -1;
        }
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        // Не требуем точного попадания: художник может слегка промахнуться пипеткой
        if (r > 140 && g < 110 && b < 110) {
            return RED;
        }
        if (b > 140 && r < 110) {
            return g > 140 ? BLUE : BLUE;
        }
        if (g > 120 && r < 130 && b < 130) {
            return GREEN;
        }
        return -1;
    }

    /** Связное пятно одного смысла (обход в ширину по четырём соседям). */
    private static List<double[]> flood(int[] kind, boolean[] seen, int w, int h,
                                        int start, int want) {
        List<double[]> out = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(start);
        seen[start] = true;
        while (!stack.isEmpty()) {
            int i = stack.pop();
            int x = i % w;
            int y = i / w;
            out.add(new double[]{x, y});
            int[] next = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1,
                y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
            for (int n : next) {
                if (n >= 0 && !seen[n] && kind[n] == want) {
                    seen[n] = true;
                    stack.push(n);
                }
            }
        }
        return out;
    }

    /**
     * МИНИМАЛЬНЫЙ ОХВАТЫВАЮЩИЙ ПРЯМОУГОЛЬНИК пятна: его наклон и есть тот угол, под
     * которым художник нарисовал квадрат.
     *
     * <p>Сперва тут считалась главная ось по разбросу точек — и на квадрате она
     * вырождается: разброс одинаков во все стороны, и угол получался случайным
     * (тест поймал 75° вместо 30°). Поэтому честный перебор: поворачиваем на пол
     * градуса в пределах 90° и берём поворот с наименьшей площадью коробки. Для
     * пятна в тысячу точек это доли миллисекунды, зато результат точный и
     * одинаковый от запуска к запуску.
     */
    private static Area principal(List<double[]> pts) {
        double bestArea = Double.MAX_VALUE;
        double bestAngle = 0;
        double bestW = 0;
        double bestH = 0;
        double bestCx = 0;
        double bestCy = 0;
        for (double deg = 0; deg < 90; deg += 0.5) {
            double a = Math.toRadians(deg);
            double ux = Math.cos(a);
            double uy = Math.sin(a);
            double minU = Double.MAX_VALUE;
            double maxU = -Double.MAX_VALUE;
            double minV = Double.MAX_VALUE;
            double maxV = -Double.MAX_VALUE;
            for (double[] p : pts) {
                double u = p[0] * ux + p[1] * uy;
                double v = -p[0] * uy + p[1] * ux;
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
            double w = maxU - minU + 1;
            double h = maxV - minV + 1;
            double area = w * h;
            if (area < bestArea - 1e-9) {
                bestArea = area;
                bestAngle = deg;
                bestW = w;
                bestH = h;
                double mu = (minU + maxU) / 2;
                double mv = (minV + maxV) / 2;
                bestCx = mu * ux - mv * uy;
                bestCy = mu * uy + mv * ux;
            }
        }
        // Длинная сторона считается «вдоль»: у вытянутого пятна ось должна идти по
        // длине, иначе кубики запаса лягут поперёк площадки.
        if (bestH > bestW) {
            double t = bestW;
            bestW = bestH;
            bestH = t;
            bestAngle += 90;
        }
        return new Area(bestCx, bestCy, bestW, bestH, bestAngle);
    }
}
