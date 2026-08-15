package kelium.agents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import kelium.core.GameState;

/**
 * ValueNet — НЕЙРОСЕТЬ ОЦЕНКИ ПОЗИЦИИ: «насколько хороша эта расстановка».
 *
 * <p>Почему именно оценка позиции, а не выбор хода. Прошлая попытка учила сеть
 * ВЫБИРАТЬ ходы методом REINFORCE, и она проиграла обычному эвристическому боту:
 * на всю партию приходится одна награда, решений — сотня, и понять, какое из них
 * было хорошим, почти невозможно. Здесь задача другая и куда более честная:
 * сеть учится ПРЕДСКАЗЫВАТЬ ИСХОД по расстановке. Это обычная регрессия с
 * учителем — устойчивая, быстро сходящаяся и, главное, ПРОВЕРЯЕМАЯ: ошибку
 * предсказания можно замерить на партиях, которых сеть не видела.
 *
 * <p>Дальше обученная сеть встаёт внутрь просчёта вперёд ({@link Lookahead}):
 * бот делает ход на копии состояния и спрашивает у сети, стало ли лучше. Поиск
 * плюс выученная оценка — та же схема, что у сильных игровых движков, только в
 * миниатюре и целиком на Java.
 *
 * <p>Вход — те же признаки, что у линейной оценки ({@link StateFeatures}), в
 * нормированном виде. Значит линейную и нейронную оценку можно честно сравнить:
 * они видят РОВНО ОДНО И ТО ЖЕ, разница только в форме функции.
 */
public final class ValueNet {

    /**
     * Действующая сеть на процесс. {@code null} — оценка позиции считается
     * линейно по геному (базовый вариант). Переключатель нарочно один и явный:
     * так любой замер можно повторить с сетью и без неё, не меняя код ботов.
     */
    private static volatile ValueNet active = null;

    /** Действующая сеть оценки (или null — считаем линейно по геному). */
    public static ValueNet active() {
        return active;
    }

    /** Включить сеть оценки для всего процесса ({@code null} — выключить). */
    public static void use(ValueNet net) {
        active = net;
    }

    private final int in;
    private final int hidden;
    private final double[][] w1;   // [hidden][in]
    private final double[] b1;     // [hidden]
    private final double[] w2;     // [hidden]
    private double b2;

    // Накопленные градиенты пачки (обучение идёт пачками — так устойчивее).
    private final double[][] g1;
    private final double[] gb1;
    private final double[] g2;
    private double gb2;
    private int batchCount = 0;

    public ValueNet(int in, int hidden, Random rng) {
        this.in = in;
        this.hidden = hidden;
        this.w1 = new double[hidden][in];
        this.b1 = new double[hidden];
        this.w2 = new double[hidden];
        this.g1 = new double[hidden][in];
        this.gb1 = new double[hidden];
        this.g2 = new double[hidden];
        // Инициализация масштаба 1/sqrt(in): без неё tanh сразу упирается в ±1 и
        // градиенты пропадают.
        double scale = 1.0 / Math.sqrt(Math.max(1, in));
        for (int h = 0; h < hidden; h++) {
            for (int i = 0; i < in; i++) {
                w1[h][i] = rng.nextGaussian() * scale;
            }
            w2[h] = rng.nextGaussian() * scale;
        }
    }

    /** Предсказание по готовому вектору признаков (нормированному). */
    public double forward(double[] x) {
        double out = b2;
        for (int h = 0; h < hidden; h++) {
            double z = b1[h];
            for (int i = 0; i < in && i < x.length; i++) {
                z += w1[h][i] * x[i];
            }
            out += w2[h] * Math.tanh(z);
        }
        return out;
    }

    /** Оценка позиции {@code seat} — то, чем сеть заменяет линейную сумму. */
    public double value(GameState s, int seat) {
        return forward(StateFeatures.normalized(s, seat));
    }

    /**
     * Один пример обучения: подтолкнуть предсказание к {@code target}. Градиент
     * копится в пачку; шаг применяет {@link #applyBatch}.
     *
     * @return квадрат ошибки на этом примере (для отчёта о сходимости)
     */
    public double accumulate(double[] x, double target) {
        double[] z = new double[hidden];
        double[] a = new double[hidden];
        double out = b2;
        for (int h = 0; h < hidden; h++) {
            double zz = b1[h];
            for (int i = 0; i < in && i < x.length; i++) {
                zz += w1[h][i] * x[i];
            }
            z[h] = zz;
            a[h] = Math.tanh(zz);
            out += w2[h] * a[h];
        }
        double err = out - target;              // d(MSE/2)/d(out)
        gb2 += err;
        for (int h = 0; h < hidden; h++) {
            g2[h] += err * a[h];
            double d = err * w2[h] * (1.0 - a[h] * a[h]);   // через tanh'
            gb1[h] += d;
            for (int i = 0; i < in && i < x.length; i++) {
                g1[h][i] += d * x[i];
            }
        }
        batchCount++;
        return err * err;
    }

    /** Применить накопленный градиент пачки с шагом {@code lr} и обнулить его. */
    public void applyBatch(double lr) {
        if (batchCount == 0) {
            return;
        }
        double k = lr / batchCount;
        for (int h = 0; h < hidden; h++) {
            for (int i = 0; i < in; i++) {
                w1[h][i] -= k * g1[h][i];
                g1[h][i] = 0.0;
            }
            b1[h] -= k * gb1[h];
            gb1[h] = 0.0;
            w2[h] -= k * g2[h];
            g2[h] = 0.0;
        }
        b2 -= k * gb2;
        gb2 = 0.0;
        batchCount = 0;
    }

    // ================= сохранение/чтение (простой текст) =================

    /** Сохранить сеть в текстовый файл (читается человеком и глазами). */
    public void save(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ValueNet: оценка позиции по признакам StateFeatures\n");
        sb.append("in ").append(in).append(' ').append("hidden ").append(hidden).append('\n');
        for (int h = 0; h < hidden; h++) {
            sb.append("w1");
            for (int i = 0; i < in; i++) {
                sb.append(' ').append(fmt(w1[h][i]));
            }
            sb.append('\n');
        }
        sb.append("b1");
        for (int h = 0; h < hidden; h++) {
            sb.append(' ').append(fmt(b1[h]));
        }
        sb.append('\n').append("w2");
        for (int h = 0; h < hidden; h++) {
            sb.append(' ').append(fmt(w2[h]));
        }
        sb.append('\n').append("b2 ").append(fmt(b2)).append('\n');
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Записываем веса с ДЕСЯТЬЮ знаками, а не с шестью. Шести не хватало: у сети
     * почти тысяча весов, ошибки округления складываются, и прочитанная сеть
     * отвечала не то же, что сохранённая. Для файла, который читают и глазами, и
     * машиной, лишние четыре знака — ничтожная плата за точное совпадение.
     */
    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.10f", v);
    }

    /** Прочитать сеть из файла, записанного {@link #save}. */
    public static ValueNet load(Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String l : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String t = l.trim();
            if (!t.isEmpty() && !t.startsWith("#")) {
                lines.add(t);
            }
        }
        if (lines.isEmpty()) {
            throw new IOException("пустой файл сети: " + path);
        }
        String[] head = lines.get(0).split("\\s+");
        int in = Integer.parseInt(head[1]);
        int hidden = Integer.parseInt(head[3]);
        ValueNet net = new ValueNet(in, hidden, new Random(1));
        int row = 0;
        for (int li = 1; li < lines.size(); li++) {
            String[] p = lines.get(li).split("\\s+");
            switch (p[0]) {
                case "w1" -> {
                    for (int i = 0; i < in && i + 1 < p.length; i++) {
                        net.w1[row][i] = Double.parseDouble(p[i + 1]);
                    }
                    row++;
                }
                case "b1" -> {
                    for (int h = 0; h < hidden && h + 1 < p.length; h++) {
                        net.b1[h] = Double.parseDouble(p[h + 1]);
                    }
                }
                case "w2" -> {
                    for (int h = 0; h < hidden && h + 1 < p.length; h++) {
                        net.w2[h] = Double.parseDouble(p[h + 1]);
                    }
                }
                case "b2" -> net.b2 = Double.parseDouble(p[1]);
                default -> { }
            }
        }
        return net;
    }
}
