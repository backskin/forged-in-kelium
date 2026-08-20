package kelium;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.gui.GameRecorder;
import kelium.gui.replay2.FullLog;
import kelium.report.ReplayRecord;

/**
 * ПОЛНЫЙ ЛОГ ПАРТИИ — проверка того, ради чего он и заведён.
 *
 * <p>Дизайнер присылает лог и говорит «баг на шаге 833». Значит лог обязан
 * (1) содержать ВСЁ, чем партия задана — иначе её не повторить, и (2) нумеровать
 * шаги теми же номерами, что видны в пульте — иначе «шаг 833» ни на что не
 * указывает. Тест проверяет ровно это.
 */
class FullLogTest {

    private static ReplayRecord game() {
        return GameRecorder.play(GameConfig.DEFAULT_RULESET, 4, 97241,
            List.of("strat:hawk", "strat:dove", "explorer", "chaos"), null);
    }

    @Test
    void логНесётВсеНастройкиПартии() {
        ReplayRecord rec = game();
        String text = FullLog.build(rec);

        // Всё, без чего партию не воспроизвести.
        assertTrue(text.contains(GameConfig.DEFAULT_RULESET), "нет версии свода");
        assertTrue(text.contains("97241"), "нет сида");
        assertTrue(text.contains("игроков: 4"), "нет числа игроков");
        assertTrue(text.contains("ДОПОЛНЕНИЯ"), "нет раздела дополнений");
        assertTrue(text.contains("МЕСТА"), "нет состава мест");
        assertTrue(text.contains("strat:hawk"), "нет идентификаторов ботов");

        // СОСТОЯНИЕ ДОПОЛНЕНИЙ ДОЛЖНО БЫТЬ ЗАПИСАНО В САМУ ПАРТИЮ, а не собрано
        // из текущих настроек при выгрузке: настройки к моменту разбора могли уже
        // поменяться, и лог соврал бы о том, чем партия была сыграна.
        assertTrue(!rec.expansions.isEmpty(),
            "состояние дополнений не попало в запись партии");
        assertTrue(!text.contains("не записаны"),
            "лог считает дополнения незаписанными, хотя партия только что сыграна");
    }

    @Test
    void номераШаговСовпадаютСПультом() {
        ReplayRecord rec = game();
        String text = FullLog.build(rec);
        assertTrue(rec.frames.size() > 50, "партия вышла подозрительно короткой");

        // Пульт показывает «шаг N/всего», где N — индекс кадра. В логе строка
        // должна начинаться тем же номером.
        int probe = Math.min(833, rec.frames.size() - 1);
        boolean found = false;
        for (String line : text.split("\n")) {
            if (line.startsWith(String.format("%6d", probe) + "  ")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "в логе нет строки шага " + probe);
    }

    @Test
    void логПишетсяВФайл() throws Exception {
        ReplayRecord rec = game();
        Path out = Path.of("target", "full-log", FullLog.defaultName(rec));
        FullLog.save(rec, out);
        assertTrue(Files.exists(out), "файл лога не создан");
        assertTrue(Files.size(out) > 2000, "лог подозрительно мал: " + Files.size(out));
    }
}
