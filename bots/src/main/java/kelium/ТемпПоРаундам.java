package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ТЕМП ПАРТИИ ПО РАУНДАМ — три числа, которых не хватило после онлайн-теста
 * 18.09.2026.
 *
 * <p>Дизайнер сказал по итогам партии: модули приходят только к концу, деньги
 * копятся и девать их некуда, войск производится мало и сносить нечего. Все три
 * утверждения — про ТЕМП, то есть про то, что происходит К КАКОМУ РАУНДУ,
 * а не в сумме за партию. Прежние пробы ({@link CombatFunnel}, {@code BalanceProbe})
 * считают итог и на такой вопрос не отвечают — отсюда отдельный замер.
 *
 * <p>На конец каждого раунда, средним по игрокам и партиям:
 * <ul>
 *   <li>сколько жетонов модулей лежит на планшете — красных и синих порознь,
 *       и в каком раунде приходит ПЕРВЫЙ;</li>
 *   <li>сколько монет лежит неистраченными;</li>
 *   <li>сколько войск стоит на поле и сколько жетонов уничтожено за раунд.</li>
 * </ul>
 *
 * <p>Состояние читается прямо из {@link GameState} в обработчике событий: на
 * событии «return» раунд уже закрыт, и стол находится ровно в том виде, в каком
 * его видят игроки перед новым раундом.
 *
 * <p>Запуск: {@code kelium.ТемпПоРаундам [партий] [игроков]}.
 */
public final class ТемпПоРаундам {

    private ТемпПоРаундам() {
    }

    /** Накопитель по одному раунду: суммы по всем партиям и игрокам. */
    private static final class Раунд {
        int партий;           // сколько партий дожило до этого раунда
        double модулиКрасные;
        double модулиСиние;
        double монеты;
        double трофеи;
        double шагиВсего;      // кубиков на всех треках
        double шагиСиний;      // кубиков на правом треке — только он даёт синий модуль
        double войскаНаПоле;
        double уничтожено;    // жетонов снято ЗА этот раунд, на партию
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        List<String> состав = List.of("warlord", "reaper", "axiom", "balanced");

        final int МАКС = 14;
        Раунд[] по = new Раунд[МАКС + 1];
        for (int i = 0; i <= МАКС; i++) {
            по[i] = new Раунд();
        }
        // В каком раунде место получило первый модуль каждого цвета; 0 — не получило.
        double суммаПервыхКрасных = 0;
        double суммаПервыхСиних = 0;
        int былКрасный = 0;
        int былСиний = 0;
        double раундовВсего = 0;

        for (int g = 0; g < games; g++) {
            long seed = 7_400_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(состав.get((i + g) % состав.size()), i,
                    new Random(seed * 31 + i), players));
            }
            int[] сноcыЗаРаунд = {0};
            int[] первыйКрасный = new int[players];
            int[] первыйСиний = new int[players];

            GameEngine.playGame(s, agents, ev -> {
                String type = String.valueOf(ev.get("type"));
                if ("combat_hit".equals(type) && Boolean.TRUE.equals(ev.get("destroyed"))) {
                    сноcыЗаРаунд[0]++;
                } else if ("return".equals(type)) {
                    int r = ev.get("round") instanceof Number n ? n.intValue() : 0;
                    if (r < 1 || r > МАКС) {
                        return;
                    }
                    Раунд acc = по[r];
                    acc.партий++;
                    acc.уничтожено += сноcыЗаРаунд[0];
                    сноcыЗаРаунд[0] = 0;
                    for (PlayerState p : s.players) {
                        acc.модулиКрасные += p.redPlacements.size();
                        acc.модулиСиние += p.bluePlacements.size();
                        acc.монеты += p.resources.coin();
                        acc.трофеи += p.resources.trophy();
                        for (String трек : s.tech.tracks) {
                            for (int шаг = 0; шаг < s.tech.steps; шаг++) {
                                if (s.tech.occupancy.get(трек).get(шаг).contains(p.seat)) {
                                    acc.шагиВсего++;
                                    if ("right".equals(трек)) {
                                        acc.шагиСиний++;
                                    }
                                }
                            }
                        }
                        acc.войскаНаПоле += p.unitsOnField().size();
                        if (первыйКрасный[p.seat] == 0 && !p.redPlacements.isEmpty()) {
                            первыйКрасный[p.seat] = r;
                        }
                        if (первыйСиний[p.seat] == 0 && !p.bluePlacements.isEmpty()) {
                            первыйСиний[p.seat] = r;
                        }
                    }
                }
            });
            раундовВсего += s.round;
            for (int seat = 0; seat < players; seat++) {
                if (первыйКрасный[seat] > 0) {
                    суммаПервыхКрасных += первыйКрасный[seat];
                    былКрасный++;
                }
                if (первыйСиний[seat] > 0) {
                    суммаПервыхСиних += первыйСиний[seat];
                    былСиний++;
                }
            }
        }

        int мест = players * games;
        out.printf("СВОД %s · игроков %d · партий %d · раундов в среднем %.1f%n%n",
            GameConfig.DEFAULT_RULESET, players, games, раундовВсего / games);

        out.println("раунд | мод.бой | мод.сборка | монет | трофеев | шагов | из них "
            + "синий трек | войск | снято");
        out.println("------+---------+------------+-------+---------+-------+-----"
            + "-------------+-------+------");
        for (int r = 1; r <= МАКС; r++) {
            Раунд a = по[r];
            if (a.партий == 0) {
                continue;
            }
            int местВРаунде = a.партий * players;
            out.printf("  %2d  |  %5.2f  |   %5.2f    | %5.2f |  %5.2f  | %5.2f |    "
                    + "  %5.2f      | %5.2f | %5.2f%n",
                r, a.модулиКрасные / местВРаунде, a.модулиСиние / местВРаунде,
                a.монеты / местВРаунде, a.трофеи / местВРаунде,
                a.шагиВсего / местВРаунде, a.шагиСиний / местВРаунде,
                a.войскаНаПоле / местВРаунде, a.уничтожено / a.партий);
        }

        out.printf("%nПЕРВЫЙ МОДУЛЬ БОЯ: в среднем раунд %.2f, дошли %d мест из %d "
                + "(%.0f%% так и остались без него).%n",
            былКрасный == 0 ? 0 : суммаПервыхКрасных / былКрасный, былКрасный, мест,
            100.0 * (мест - былКрасный) / мест);
        out.printf("ПЕРВЫЙ МОДУЛЬ СБОРКИ: в среднем раунд %.2f, дошли %d мест из %d "
                + "(%.0f%% без него).%n",
            былСиний == 0 ? 0 : суммаПервыхСиних / былСиний, былСиний, мест,
            100.0 * (мест - былСиний) / мест);
    }
}
