package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.gui.GameRecorder;
import kelium.report.ReplayRecord;

/**
 * СКЛАД НИКОГДА НЕ ПЕРЕПОЛНЕН — ни на одном кадре записи.
 *
 * <p>ПОЧЕМУ ЭТО СТОРОЖ, А НЕ КОСМЕТИКА. Кубик на складе занимает ячейку, и
 * ячеек ровно столько, сколько открыто зданиями. Всё, что попадает на склад,
 * обязано проходить через {@link kelium.engine.Storage} — иначе игрок держит
 * больше, чем физически может, и любая экономика, посчитанная по этому
 * состоянию, врёт. Дырой оказывались способности, писавшие ресурс прямо в
 * счётчик игрока, минуя проверку места.
 *
 * <p>ПРОВЕРЯЕТСЯ ПО ЗАПИСИ, А НЕ ПО ЕДИНИЧНОМУ ХОДУ: пути пополнения склада
 * разбросаны по действиям, бою и способностям карт, и поймать их можно только
 * настоящей партией. Кадры записи для этого годятся: в каждом лежит и занятое,
 * и предел — те самые числа, которые видит дизайнер в проигрывателе.
 */
class StorageNeverOverflowsTest {

    @Test
    void складНеБываетЗанятСверхЧислаЯчеек() {
        List<String> bad = new ArrayList<>();
        for (int players = 2; players <= 4; players++) {
            for (int g = 0; g < 3; g++) {
                long seed = 900L + g * 13L + players;
                ReplayRecord rec = GameRecorder.play(GameConfig.DEFAULT_RULESET, players, seed,
                    bots(players), null);
                for (int i = 0; i < rec.frames.size(); i++) {
                    ReplayRecord.Frame f = rec.frames.get(i);
                    if (f.snapshot == null) {
                        continue;
                    }
                    for (ReplayRecord.Player p : f.snapshot.players) {
                        int busy = p.kelium + p.ammo + p.debris;
                        if (busy > p.storeCap) {
                            bad.add("сид " + seed + ", кадр " + i + ", место " + p.seat
                                + ": занято " + busy + " при " + p.storeCap + " ячейках"
                                + " (к" + p.kelium + " б" + p.ammo + " о" + p.debris + ")"
                                + " — " + f.type);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), bad, "склад переполнен: кубиков больше, чем ячеек");
    }

    private static List<String> bots(int players) {
        String[] pool = {"trained:hawk", "trained:dove", "trained:balanced", "search:balanced"};
        List<String> out = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            out.add(pool[i]);
        }
        return out;
    }
}
