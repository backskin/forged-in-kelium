package kelium;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ОТКУДА У ИГРОКА БЕРЁТСЯ ГОРА МОНЕТ.
 *
 * <p>Повод: в замере категорий супер-заданий 15.09.2026 у одного игрока в
 * финале оказалось 64 монеты при медиане по столу 5. Категория «1 ПО за
 * каждые 4 монеты» превращала это в 16 победных очков — больше, чем весь
 * остальной счёт партии. Прежде чем назначать категории пороги, надо понять,
 * это разовый выброс или в игре есть насос.
 *
 * <p>Стенд ищет самую денежную партию из прогона, переигрывает её с журналом и
 * складывает все приходы монет по источникам.
 *
 * <p>Запуск: {@code kelium.ОткудаМонеты [партий] [игроков] [характер]}
 */
public final class ОткудаМонеты {

    private ОткудаМонеты() {
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String характер = args.length > 2 ? args[2] : "balanced";

        long лучшийSeed = -1;
        int лучшееМесто = -1;
        int больше = -1;
        for (int i = 0; i < партий; i++) {
            long seed = 91000L + i;
            GameState s = сыграть(seed, игроков, характер, ev -> { });
            for (int seat = 0; seat < игроков; seat++) {
                int монет = s.player(seat).resources.coin();
                if (монет > больше) {
                    больше = монет;
                    лучшийSeed = seed;
                    лучшееМесто = seat;
                }
            }
        }
        System.out.printf("САМАЯ ДЕНЕЖНАЯ ПАРТИЯ: seed=%d, место %d, монет в финале %d%n%n",
            лучшийSeed, лучшееМесто, больше);

        final int место = лучшееМесто;
        Map<String, Long> поТипам = new TreeMap<>();
        Map<String, Long> доход = new TreeMap<>();
        List<Map<String, Object>> события = new ArrayList<>();
        GameState s = сыграть(лучшийSeed, игроков, характер, ev -> {
            Object seat = ev.get("seat");
            if (seat instanceof Number n && n.intValue() == место) {
                события.add(new LinkedHashMap<>(ev));
            }
        });
        for (Map<String, Object> ev : события) {
            String тип = String.valueOf(ev.get("type"));
            поТипам.merge(тип, 1L, Long::sum);
            if ("refresh_income".equals(тип) && ev.get("coin") instanceof Number c) {
                доход.merge("доход в Обновлении (карты арсенала)", c.longValue(), Long::sum);
            }
        }
        System.out.println("СОБЫТИЯ ЭТОГО ИГРОКА (тип: сколько раз)");
        for (var e : поТипам.entrySet()) {
            if (e.getValue() >= 2) {
                System.out.printf("  %-34s %d%n", e.getKey(), e.getValue());
            }
        }
        System.out.println();
        for (var e : доход.entrySet()) {
            System.out.printf("  %s: %d монет%n", e.getKey(), e.getValue());
        }
        System.out.println();
        System.out.println("ЧТО У НЕГО УСТАНОВЛЕНО:");
        System.out.println("  арсенал: " + s.player(место).allInstalledArsenal());
        System.out.println("  супер-арсенал: " + s.player(место).superArsenalCards);
        System.out.println("  зданий на поле: " + s.player(место).buildingsOnField().size());
        System.out.println("  монет в финале: " + s.player(место).resources.coin());
    }

    private static GameState сыграть(long seed, int игроков, String характер,
                                     java.util.function.Consumer<Map<String, Object>> слушатель) {
        GameState s = Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, игроков, seed, null, null));
        List<Agent> боты = new ArrayList<>();
        for (int seat = 0; seat < игроков; seat++) {
            боты.add(kelium.agents.Bots.create(характер, seat,
                new Random(seed * 31 + seat), игроков));
        }
        new GameEngine(s, боты, слушатель::accept).run();
        return s;
    }
}
