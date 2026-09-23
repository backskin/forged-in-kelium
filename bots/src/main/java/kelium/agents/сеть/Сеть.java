package kelium.agents.сеть;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * СЕТЬ ОЦЕНКИ ПОЗИЦИИ (23.09.2026): многослойный перцептрон на чистой Java.
 * Вход — {@link Кодировщик}, выход — ожидаемый итог партии для игрока в
 * пределах [−1, 1] (отрыв в очках от лучшего соперника, делённый на 10).
 *
 * <p>Два скрытых слоя с ReLU, выход через tanh. Обучение — средний квадрат
 * ошибки, Adam, мини-пакеты. Ничего не знает об игре: всё, что она умеет, —
 * из партий самоигры.
 */
public final class Сеть {

    private final int[] слои;
    private final float[][][] w;   // [слой][выход][вход]
    private final float[][] b;     // [слой][выход]
    // Adam
    private final float[][][] mw, vw;
    private final float[][] mb, vb;
    private int шагов;

    public Сеть(int вход, int скрытый1, int скрытый2, long seed) {
        this(new int[]{вход, скрытый1, скрытый2, 1}, seed);
    }

    private Сеть(int[] слои, long seed) {
        this.слои = слои;
        int n = слои.length - 1;
        w = new float[n][][];
        b = new float[n][];
        mw = new float[n][][];
        vw = new float[n][][];
        mb = new float[n][];
        vb = new float[n][];
        Random r = new Random(seed);
        for (int l = 0; l < n; l++) {
            int in = слои[l];
            int out = слои[l + 1];
            w[l] = new float[out][in];
            mw[l] = new float[out][in];
            vw[l] = new float[out][in];
            b[l] = new float[out];
            mb[l] = new float[out];
            vb[l] = new float[out];
            float масштаб = (float) Math.sqrt(2.0 / in);
            for (int o = 0; o < out; o++) {
                for (int k = 0; k < in; k++) {
                    w[l][o][k] = (float) (r.nextGaussian() * масштаб);
                }
            }
        }
    }

    public int вход() {
        return слои[0];
    }

    /** Оценка позиции. */
    public float оценить(float[] x) {
        float[] a = x;
        int n = слои.length - 1;
        for (int l = 0; l < n; l++) {
            float[] z = new float[слои[l + 1]];
            for (int o = 0; o < z.length; o++) {
                float s = b[l][o];
                float[] wo = w[l][o];
                for (int k = 0; k < a.length; k++) {
                    s += wo[k] * a[k];
                }
                z[o] = l < n - 1 ? Math.max(0, s) : (float) Math.tanh(s);
            }
            a = z;
        }
        return a[0];
    }

    /**
     * Один проход обучения по пакету: вход {@code xs}, цель {@code ys}.
     *
     * @return средний квадрат ошибки на пакете до шага
     */
    public float учить(float[][] xs, float[] ys, float скорость) {
        int n = слои.length - 1;
        float[][][] gw = new float[n][][];
        float[][] gb = new float[n][];
        for (int l = 0; l < n; l++) {
            gw[l] = new float[слои[l + 1]][слои[l]];
            gb[l] = new float[слои[l + 1]];
        }
        float ошибка = 0;
        for (int p = 0; p < xs.length; p++) {
            // прямой проход с запоминанием
            float[][] acts = new float[n + 1][];
            acts[0] = xs[p];
            for (int l = 0; l < n; l++) {
                float[] z = new float[слои[l + 1]];
                for (int o = 0; o < z.length; o++) {
                    float s = b[l][o];
                    float[] wo = w[l][o];
                    float[] a = acts[l];
                    for (int k = 0; k < a.length; k++) {
                        s += wo[k] * a[k];
                    }
                    z[o] = l < n - 1 ? Math.max(0, s) : (float) Math.tanh(s);
                }
                acts[l + 1] = z;
            }
            float y = acts[n][0];
            float d = y - ys[p];
            ошибка += d * d;
            // обратный проход
            float[] delta = {2 * d * (1 - y * y) / xs.length};
            for (int l = n - 1; l >= 0; l--) {
                float[] a = acts[l];
                float[] пред = new float[слои[l]];
                for (int o = 0; o < delta.length; o++) {
                    gb[l][o] += delta[o];
                    float[] wo = w[l][o];
                    float[] go = gw[l][o];
                    for (int k = 0; k < a.length; k++) {
                        go[k] += delta[o] * a[k];
                        пред[k] += delta[o] * wo[k];
                    }
                }
                if (l > 0) {
                    for (int k = 0; k < пред.length; k++) {
                        if (acts[l][k] <= 0) {
                            пред[k] = 0;
                        }
                    }
                }
                delta = пред;
            }
        }
        // Adam
        шагов++;
        float b1 = 0.9f, b2 = 0.999f, eps = 1e-8f;
        float c1 = (float) (1 - Math.pow(b1, шагов));
        float c2 = (float) (1 - Math.pow(b2, шагов));
        for (int l = 0; l < n; l++) {
            for (int o = 0; o < w[l].length; o++) {
                for (int k = 0; k < w[l][o].length; k++) {
                    float g = gw[l][o][k];
                    mw[l][o][k] = b1 * mw[l][o][k] + (1 - b1) * g;
                    vw[l][o][k] = b2 * vw[l][o][k] + (1 - b2) * g * g;
                    w[l][o][k] -= скорость * (mw[l][o][k] / c1)
                        / ((float) Math.sqrt(vw[l][o][k] / c2) + eps);
                }
                float g = gb[l][o];
                mb[l][o] = b1 * mb[l][o] + (1 - b1) * g;
                vb[l][o] = b2 * vb[l][o] + (1 - b2) * g * g;
                b[l][o] -= скорость * (mb[l][o] / c1) / ((float) Math.sqrt(vb[l][o] / c2) + eps);
            }
        }
        return ошибка / xs.length;
    }

    public void сохранить(Path файл) throws IOException {
        Files.createDirectories(файл.toAbsolutePath().getParent());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(файл))) {
            out.writeInt(слои.length);
            for (int x : слои) {
                out.writeInt(x);
            }
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
            Сеть с = new Сеть(слои, 0);
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
