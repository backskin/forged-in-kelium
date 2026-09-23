package kelium;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import kelium.agents.сеть.Кодировщик;
import kelium.agents.сеть.Сеть;

/**
 * ОБУЧЕНИЕ СЕТИ ОЦЕНКИ (23.09.2026) на записях самоигры ({@link СамоИгра}).
 *
 * <p>Проверка честная: последние 10% записей (последние партии файла) в
 * обучение не идут. На них ошибка сети сравнивается с ошибкой «всегда говорить
 * среднее»: если сеть не лучше среднего, она ничего не выучила.
 *
 * <p>Запуск: {@code kelium.ОбучениеСети [данные.bin ...] --out сеть.bin [--epochs N]}.
 */
public final class ОбучениеСети {

    private ОбучениеСети() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        List<Path> файлы = new ArrayList<>();
        Path выход = Path.of("data/selfplay/net.bin");
        int эпох = 30;
        for (int i = 0; i < args.length; i++) {
            if ("--out".equals(args[i])) {
                выход = Path.of(args[++i]);
            } else if ("--epochs".equals(args[i])) {
                эпох = Integer.parseInt(args[++i]);
            } else {
                файлы.add(Path.of(args[i]));
            }
        }
        int длина = Кодировщик.длина(4);
        List<float[]> все = new ArrayList<>();
        for (Path ф : файлы) {
            try (DataInputStream in = new DataInputStream(
                    new java.io.BufferedInputStream(Files.newInputStream(ф)))) {
                while (true) {
                    float[] з = new float[длина + 1];
                    for (int k = 0; k < з.length; k++) {
                        з[k] = in.readFloat();
                    }
                    все.add(з);
                }
            } catch (EOFException конец) {
                // файл прочитан
            }
        }
        int отложено = Math.max(1, все.size() / 10);
        List<float[]> учебные = new ArrayList<>(все.subList(0, все.size() - отложено));
        List<float[]> проверка = все.subList(все.size() - отложено, все.size());
        double среднее = 0;
        for (float[] з : учебные) {
            среднее += з[0];
        }
        среднее /= учебные.size();
        double базовая = 0;
        for (float[] з : проверка) {
            базовая += (з[0] - среднее) * (з[0] - среднее);
        }
        базовая /= проверка.size();
        out.printf("записей %d (учебных %d, проверочных %d); ошибка «всегда среднее» %.4f%n",
            все.size(), учебные.size(), проверка.size(), базовая);

        Сеть сеть = new Сеть(длина, 128, 64, 1);
        Random r = new Random(2);
        int пакет = 256;
        double лучшая = Double.MAX_VALUE;
        for (int эп = 1; эп <= эпох; эп++) {
            Collections.shuffle(учебные, r);
            double уч = 0;
            int пакетов = 0;
            for (int i = 0; i + пакет <= учебные.size(); i += пакет) {
                float[][] xs = new float[пакет][];
                float[] ys = new float[пакет];
                for (int k = 0; k < пакет; k++) {
                    float[] з = учебные.get(i + k);
                    ys[k] = з[0];
                    xs[k] = java.util.Arrays.copyOfRange(з, 1, з.length);
                }
                уч += сеть.учить(xs, ys, 1e-3f);
                пакетов++;
            }
            double пр = 0;
            for (float[] з : проверка) {
                float y = сеть.оценить(java.util.Arrays.copyOfRange(з, 1, з.length));
                пр += (y - з[0]) * (y - з[0]);
            }
            пр /= проверка.size();
            String отметка = "";
            if (пр < лучшая) {
                лучшая = пр;
                сеть.сохранить(выход);
                отметка = " ← сохранена";
            }
            out.printf("эпоха %2d: учебная %.4f, проверочная %.4f (лучше среднего на %.0f%%)%s%n",
                эп, уч / Math.max(1, пакетов), пр, 100 * (1 - пр / базовая), отметка);
        }
        out.printf("лучшая проверочная ошибка %.4f против %.4f у «всегда среднее» → %s%n",
            лучшая, базовая, выход);
    }
}
