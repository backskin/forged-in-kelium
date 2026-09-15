package kelium;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.СуперЗадания;

/**
 * СКОЛЬКО ОЧКОВ НА ДЕЛЕ ДАЁТ КАЖДАЯ КАТЕГОРИЯ СУПЕР-ЗАДАНИЯ.
 *
 * <p>ЗАЧЕМ. Карта супер-задания — это ПАРА категорий, и игрок получает очки за
 * обе. Значит карты равноценны ровно настолько, насколько равноценны категории.
 * На глаз этого не видно: «1 ПО за каждые 3 здания» и «1 ПО за каждое войско у
 * чужого здания» выглядят одинаково скромно, а дают разное в разы.
 *
 * <p>ЧТО СЧИТАЕТСЯ. Категория считается для КАЖДОГО игрока в конце партии —
 * независимо от того, пришла ему такая карта или нет. Нас интересует не то,
 * сколько набрал счастливчик, а то, сколько НАБРАЛ БЫ любой: карта раздаётся
 * вслепую на подготовке, и игрок под неё не готовился.
 *
 * <p>ЧТО В ОТЧЁТЕ. Среднее, медиана, доля нулей и максимум. Доля нулей важнее
 * среднего: категория, которая у половины стола даёт ноль, — это половина
 * карты, выброшенная в мусор при раздаче.
 *
 * <p>Запуск: {@code kelium.КатегорииСупер [партий] [игроков] [характер]}
 */
public final class КатегорииСупер {

    private КатегорииСупер() {
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String характер = args.length > 2 ? args[2] : "balanced";

        Map<String, List<Integer>> замеры = new LinkedHashMap<>();
        for (String к : СуперЗадания.КАТЕГОРИИ) {
            замеры.put(к, new ArrayList<>());
        }
        long раундов = 0;
        for (int i = 0; i < партий; i++) {
            long seed = 91000L + i;
            GameState s = Setup.buildGame(
                GameConfig.buildCached(GameConfig.DEFAULT_RULESET, игроков, seed, null, null));
            List<Agent> боты = new ArrayList<>();
            for (int seat = 0; seat < игроков; seat++) {
                боты.add(kelium.agents.Bots.create(характер, seat,
                    new Random(seed * 31 + seat), игроков));
            }
            new GameEngine(s, боты, ev -> { }).run();
            раундов += s.round;
            for (int seat = 0; seat < игроков; seat++) {
                for (String к : СуперЗадания.КАТЕГОРИИ) {
                    замеры.get(к).add(СуперЗадания.очкиКатегории(s, s.player(seat), к));
                }
            }
        }

        System.out.printf("ПАРТИЙ %d, игроков %d, характер %s, средняя длина %.1f раунда%n%n",
            партий, игроков, характер, раундов / (double) партий);
        System.out.printf("%-22s %7s %7s %7s %7s%n",
            "категория", "сред", "медиана", "нулей%", "макс");
        List<String> порядок = new ArrayList<>(замеры.keySet());
        порядок.sort((a, b) -> Double.compare(среднее(замеры.get(b)), среднее(замеры.get(a))));
        for (String к : порядок) {
            List<Integer> v = замеры.get(к);
            List<Integer> s2 = new ArrayList<>(v);
            java.util.Collections.sort(s2);
            int нулей = 0;
            int макс = 0;
            for (int x : v) {
                if (x == 0) {
                    нулей++;
                }
                макс = Math.max(макс, x);
            }
            System.out.printf("%-22s %7.2f %7d %6.0f%% %7d%n", к, среднее(v),
                s2.get(s2.size() / 2), 100.0 * нулей / v.size(), макс);
        }
    }

    private static double среднее(List<Integer> v) {
        long сум = 0;
        for (int x : v) {
            сум += x;
        }
        return v.isEmpty() ? 0 : сум / (double) v.size();
    }
}
