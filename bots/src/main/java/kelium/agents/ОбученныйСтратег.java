package kelium.agents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import kelium.agents.сеть.КодировщикСтратега;
import kelium.agents.сеть.Сеть;
import kelium.core.Agent;
import kelium.dataio.Locations;

/**
 * СТРАТЕГ ЗА СТОЛОМ (30.09.2026) — гроссмейстер-планировщик с оценкой позиции
 * сетью, выученной самоигрой ({@code kelium.ЦиклСтратега}).
 *
 * <p>Сеть лежит в памяти ботов: {@code strateg_4p.bin} и доля сети в оценке —
 * {@code strateg_4p.txt}. Их кладёт туда обучение каждый раз, когда принимает
 * новое поколение, поэтому игра всегда берёт лучшее принятое. Сеть учена на
 * столе вчетвером; при другом числе игроков, как и без файла, стратег играет
 * прежним гроссмейстером.
 */
public final class ОбученныйСтратег {

    private ОбученныйСтратег() {
    }

    private static Сеть сеть;
    private static double доля;
    private static long прочитано = -1;

    /** Файл сети в памяти ботов. */
    public static Path файл() {
        return Locations.botMemoryFile("strateg_4p.bin");
    }

    /** Файл доли сети в памяти ботов. */
    public static Path файлДоли() {
        return Locations.botMemoryFile("strateg_4p.txt");
    }

    /** Прочитать сеть, если файл новее прочитанного. */
    private static synchronized Сеть сеть() {
        Path ф = файл();
        try {
            if (!Files.exists(ф)) {
                return null;
            }
            long изменён = Files.getLastModifiedTime(ф).toMillis();
            if (изменён != прочитано) {
                Сеть с = Сеть.загрузить(ф);
                if (с.вход() != КодировщикСтратега.длина(4)) {
                    return null;          // сеть под другой стол — не наша
                }
                сеть = с;
                доля = Files.exists(файлДоли())
                    ? Double.parseDouble(Files.readString(файлДоли()).trim()) : 1.0;
                прочитано = изменён;
            }
            return сеть;
        } catch (Exception e) {
            return null;
        }
    }

    /** Есть ли обученная сеть для стола на {@code игроков}. */
    public static boolean есть(int игроков) {
        return игроков == 4 && сеть() != null;
    }

    /** Стратег характера {@code характер}; без сети — прежний гроссмейстер. */
    public static Agent создать(String характер, int seat, Random rng, int игроков) {
        PlannerAgent бот = (PlannerAgent) Bots.create(характер, Bots.Level.ГРОССМЕЙСТЕР, seat,
            rng, игроков);
        Сеть с = игроков == 4 ? сеть() : null;
        if (с != null && доля > 0) {
            бот.обученная = (s, место) -> 10.0 * с.оценить(КодировщикСтратега.закодировать(s, место));
            бот.доляСети = доля;
        }
        return бот;
    }
}
