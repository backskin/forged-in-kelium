package kelium.agents.сеть;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * МНОГОСЛОЙНЫЙ ПЕРЦЕПТРОН на чистой Java (23.09.2026) — основа обеих сетей
 * AlphaZero-бота:
 * <ul>
 *   <li><b>сеть оценки</b> — вход {@link Кодировщик#закодировать}, выход через
 *       tanh: ожидаемый итог партии для игрока в [−1, 1];</li>
 *   <li><b>сеть ходов</b> — вход «стол + вариант» ({@link Кодировщик#вариант}),
 *       выход линейный: сырая оценка варианта; оценки вариантов одного решения
 *       сравниваются между собой через softmax.</li>
 * </ul>
 * Два скрытых слоя с ReLU, Adam. Ничего не знает об игре: всё, что она умеет, —
 * из партий самоигры.
 */
public final class Сеть {

    private final int[] слои;
    private final boolean выходTanh;
    private final float[][][] w;   // [слой][выход][вход]
    private final float[][] b;     // [слой][выход]
    // накопленные градиенты текущего пакета
    private final float[][][] gw;
    private final float[][] gb;
    // Adam
    private final float[][][] mw, vw;
    private final float[][] mb, vb;
    private int шагов;

    /** Сеть оценки (выход tanh). */
    public Сеть(int вход, int скрытый1, int скрытый2, long seed) {
        this(new int[]{вход, скрытый1, скрытый2, 1}, true, seed);
    }

    /** Сеть с выбором выхода: tanh (оценка) или линейный (ходы). */
    public Сеть(int вход, int скрытый1, int скрытый2, boolean выходTanh, long seed) {
        this(new int[]{вход, скрытый1, скрытый2, 1}, выходTanh, seed);
    }

    private Сеть(int[] слои, boolean выходTanh, long seed) {
        this.слои = слои;
        this.выходTanh = выходTanh;
        int n = слои.length - 1;
        w = new float[n][][];
        b = new float[n][];
        gw = new float[n][][];
        gb = new float[n][];
        mw = new float[n][][];
        vw = new float[n][][];
        mb = new float[n][];
        vb = new float[n][];
        Random r = new Random(seed);
        for (int l = 0; l < n; l++) {
            int in = слои[l];
            int out = слои[l + 1];
            w[l] = new float[out][in];
            gw[l] = new float[out][in];
            mw[l] = new float[out][in];
            vw[l] = new float[out][in];
            b[l] = new float[out];
            gb[l] = new float[out];
            mb[l] = new float[out];
            vb[l] = new float[out];
            float масштаб = (float) Math.sqrt(2.0 / in);
            for (int o = 0; o < out; o++) {
                for (int k = 0; k < in; k++) {
                    w[l][o][k] = (float) (r.nextGaussian() * масштаб);
                }
            }
        }
        // последний слой — мелкими числами: необученная сеть говорит «около нуля»
        for (float[] row : w[n - 1]) {
            for (int k = 0; k < row.length; k++) {
                row[k] *= 0.1f;
            }
        }
    }

    public int вход() {
        return слои[0];
    }

    /** Прямой проход: все слои (для обучения). acts[0] — вход, acts[n] — выход. */
    private float[][] проход(float[] x) {
        int n = слои.length - 1;
        float[][] acts = new float[n + 1][];
        acts[0] = x;
        for (int l = 0; l < n; l++) {
            float[] z = new float[слои[l + 1]];
            float[] a = acts[l];
            for (int o = 0; o < z.length; o++) {
                float s = b[l][o];
                float[] wo = w[l][o];
                for (int k = 0; k < a.length; k++) {
                    s += wo[k] * a[k];
                }
                if (l < n - 1) {
                    z[o] = Math.max(0, s);
                } else {
                    z[o] = выходTanh ? (float) Math.tanh(s) : s;
                }
            }
            acts[l + 1] = z;
        }
        return acts;
    }

    /** Выход сети на входе x. */
    public float оценить(float[] x) {
        float[][] acts = проход(x);
        return acts[acts.length - 1][0];
    }

    /** Обратный проход: накопить градиенты при dL/d(выход) = {@code dy}. */
    private void назад(float[][] acts, float dy) {
        int n = слои.length - 1;
        float y = acts[n][0];
        float[] delta = {выходTanh ? dy * (1 - y * y) : dy};
        for (int l = n - 1; l >= 0; l--) {
            float[] a = acts[l];
            float[] пред = l > 0 ? new float[слои[l]] : null;
            for (int o = 0; o < delta.length; o++) {
                float d = delta[o];
                if (d == 0) {
                    continue;
                }
                gb[l][o] += d;
                float[] wo = w[l][o];
                float[] go = gw[l][o];
                for (int k = 0; k < a.length; k++) {
                    go[k] += d * a[k];
                    if (пред != null) {
                        пред[k] += d * wo[k];
                    }
                }
            }
            if (пред != null) {
                for (int k = 0; k < пред.length; k++) {
                    if (acts[l][k] <= 0) {
                        пред[k] = 0;
                    }
                }
            }
            delta = пред;
        }
    }

    /** Шаг Adam по накопленным градиентам и их обнуление. */
    private void шаг(float скорость) {
        шагов++;
        float b1 = 0.9f, b2 = 0.999f, eps = 1e-8f;
        float c1 = (float) (1 - Math.pow(b1, шагов));
        float c2 = (float) (1 - Math.pow(b2, шагов));
        int n = слои.length - 1;
        for (int l = 0; l < n; l++) {
            for (int o = 0; o < w[l].length; o++) {
                for (int k = 0; k < w[l][o].length; k++) {
                    float g = gw[l][o][k];
                    gw[l][o][k] = 0;
                    mw[l][o][k] = b1 * mw[l][o][k] + (1 - b1) * g;
                    vw[l][o][k] = b2 * vw[l][o][k] + (1 - b2) * g * g;
                    w[l][o][k] -= скорость * (mw[l][o][k] / c1)
                        / ((float) Math.sqrt(vw[l][o][k] / c2) + eps);
                }
                float g = gb[l][o];
                gb[l][o] = 0;
                mb[l][o] = b1 * mb[l][o] + (1 - b1) * g;
                vb[l][o] = b2 * vb[l][o] + (1 - b2) * g * g;
                b[l][o] -= скорость * (mb[l][o] / c1) / ((float) Math.sqrt(vb[l][o] / c2) + eps);
            }
        }
    }

    /**
     * Пакет обучения СЕТИ ОЦЕНКИ: средний квадрат ошибки.
     *
     * @return средний квадрат ошибки на пакете до шага
     */
    public float учить(float[][] xs, float[] ys, float скорость) {
        float ошибка = 0;
        for (int p = 0; p < xs.length; p++) {
            float[][] acts = проход(xs[p]);
            float d = acts[acts.length - 1][0] - ys[p];
            ошибка += d * d;
            назад(acts, 2 * d / xs.length);
        }
        шаг(скорость);
        return ошибка / xs.length;
    }

    /**
     * Пакет обучения СЕТИ ХОДОВ: перекрёстная энтропия между softmax оценок
     * вариантов и распределением посещений поиска.
     *
     * @param решения  по решению — входы вариантов {@code [вариант][вход]}
     * @param цели     по решению — доли посещений вариантов (в сумме 1)
     * @return средняя перекрёстная энтропия до шага
     */
    public float учитьХоды(float[][][] решения, float[][] цели, float скорость) {
        float потеря = 0;
        for (int p = 0; p < решения.length; p++) {
            float[][] входы = решения[p];
            float[][][] acts = new float[входы.length][][];
            float[] logits = new float[входы.length];
            float max = Float.NEGATIVE_INFINITY;
            for (int k = 0; k < входы.length; k++) {
                acts[k] = проход(входы[k]);
                logits[k] = acts[k][acts[k].length - 1][0];
                max = Math.max(max, logits[k]);
            }
            float сумма = 0;
            float[] pr = new float[входы.length];
            for (int k = 0; k < входы.length; k++) {
                pr[k] = (float) Math.exp(logits[k] - max);
                сумма += pr[k];
            }
            for (int k = 0; k < входы.length; k++) {
                pr[k] /= сумма;
                if (цели[p][k] > 0) {
                    потеря -= цели[p][k] * Math.log(Math.max(1e-9, pr[k]));
                }
                назад(acts[k], (pr[k] - цели[p][k]) / решения.length);
            }
        }
        шаг(скорость);
        return потеря / решения.length;
    }

    /** Softmax-вероятности вариантов по их входам (для поиска). */
    public float[] вероятности(float[][] входы, float температура) {
        float[] logits = new float[входы.length];
        float max = Float.NEGATIVE_INFINITY;
        for (int k = 0; k < входы.length; k++) {
            logits[k] = оценить(входы[k]) / температура;
            max = Math.max(max, logits[k]);
        }
        float сумма = 0;
        for (int k = 0; k < logits.length; k++) {
            logits[k] = (float) Math.exp(logits[k] - max);
            сумма += logits[k];
        }
        for (int k = 0; k < logits.length; k++) {
            logits[k] /= сумма;
        }
        return logits;
    }

    public void сохранить(Path файл) throws IOException {
        if (файл.toAbsolutePath().getParent() != null) {
            Files.createDirectories(файл.toAbsolutePath().getParent());
        }
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(файл))) {
            out.writeInt(слои.length);
            for (int x : слои) {
                out.writeInt(x);
            }
            out.writeBoolean(выходTanh);
            for (int l = 0; l < слои.length - 1; l++) {
                for (float[] row : w[l]) {
                    for (float x : row) {
                        out.writeFloat(x);
                    }
                }
                for (float x : b[l]) {
                    out.writeFloat(x);
                }
            }
        }
    }

    public static Сеть загрузить(Path файл) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(файл))) {
            int n = in.readInt();
            int[] слои = new int[n];
            for (int i = 0; i < n; i++) {
                слои[i] = in.readInt();
            }
            boolean tanh = in.readBoolean();
            Сеть с = new Сеть(слои, tanh, 0);
            for (int l = 0; l < n - 1; l++) {
                for (float[] row : с.w[l]) {
                    for (int k = 0; k < row.length; k++) {
                        row[k] = in.readFloat();
                    }
                }
                for (int k = 0; k < с.b[l].length; k++) {
                    с.b[l][k] = in.readFloat();
                }
            }
            return с;
        }
    }
}
