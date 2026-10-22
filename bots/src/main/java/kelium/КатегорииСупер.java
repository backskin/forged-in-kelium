package kelium;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.Ctx;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.СуперЗадания;

/**
 * СКОЛЬКО ОЧКОВ НА ДЕЛЕ ДАЁТ КАЖДАЯ КАТЕГОРИЯ И КАЖДАЯ КАРТА СУПЕР-ЗАДАНИЯ.
 *
 * <p>ЗАЧЕМ. Карта супер-задания — это разделы с категориями, и игрок получает
 * очки за все. Значит карты равноценны ровно настолько, насколько равноценны
 * категории. На глаз этого не видно: «за каждые 3 здания» и «за каждое войско у
 * чужого здания» выглядят одинаково скромно, а дают разное в разы.
 *
 * <p>ЧТО СЧИТАЕТСЯ. Категория и карта считаются для КАЖДОГО игрока в конце
 * партии — независимо от того, пришла ему такая карта или нет. Нас интересует
 * не то, сколько набрал счастливчик, а то, сколько НАБРАЛ БЫ любой: карта
 * раздаётся вслепую на подготовке, и игрок под неё не готовился. Поэтому цифры
 * — нижняя оценка: игрок, который знает свою карту, наберёт больше.
 *
 * <p>ЧТО В ОТЧЁТЕ. По категориям — единицы счёта (до умножения на звёзды):
 * среднее, медиана, доля нулей, максимум. По картам действующего набора — очки
 * лёгкого раздела («по ★»), трудного («по ★★») и всей карты, доля партий, где
 * раздел дал хоть что-то. Доля нулей важнее среднего: раздел, который у
 * половины стола даёт ноль, — это половина карты, выброшенная при раздаче.
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
        Map<String, List<int[]>> карты = new LinkedHashMap<>();
        List<Integer> итоги = new ArrayList<>();
        List<Integer> своиСупер = new ArrayList<>();
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
            List<String> колода = Ctx.cards(s, "super_objectives").ids();
            for (int seat = 0; seat < игроков; seat++) {
                var p = s.player(seat);
                for (String к : СуперЗадания.КАТЕГОРИИ) {
                    замеры.get(к).add(СуперЗадания.очкиКатегории(s, p, к));
                }
                for (String id : колода) {
                    List<String> кат = СуперЗадания.категорииКарты(s, id);
                    List<Integer> веса = СуперЗадания.весаКарты(s, id);
                    int[] r = new int[кат.size()];
                    for (int k = 0; k < кат.size(); k++) {
                        r[k] = веса.get(k) * СуперЗадания.очкиКатегории(s, p, кат.get(k));
                    }
                    карты.computeIfAbsent(id, x -> new ArrayList<>()).add(r);
                }
                итоги.add(Scoring.scorePlayer(s, seat).getOrDefault("total", 0));
                своиСупер.add(СуперЗадания.vp(s, seat));
            }
        }

        System.out.printf("ПАРТИЙ %d, игроков %d, характер %s, средняя длина %.1f раунда%n",
            партий, игроков, характер, раундов / (double) партий);
        System.out.printf("итог игрока: среднее %.1f ПО; из них со своих супер-заданий %.1f%n%n",
            среднее(итоги), среднее(своиСупер));
        System.out.printf("%-24s %7s %7s %7s %7s%n",
            "категория (единиц)", "сред", "медиана", "нулей%", "макс");
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
            System.out.printf("%-24s %7.2f %7d %6.0f%% %7d%n", к, среднее(v),
                s2.get(s2.size() / 2), 100.0 * нулей / v.size(), макс);
        }

        System.out.printf("%n%-8s %9s %8s %9s %8s %8s %6s%n", "карта",
            "★ сред", "★ >0%", "★★ сред", "★★ >0%", "карта", "макс");
        for (var e : карты.entrySet()) {
            List<int[]> v = e.getValue();
            int разделов = v.isEmpty() ? 0 : v.get(0).length;
            double[] сум = new double[разделов];
            int[] непусто = new int[разделов];
            double всего = 0;
            int макс = 0;
            for (int[] r : v) {
                int t = 0;
                for (int k = 0; k < разделов; k++) {
                    сум[k] += r[k];
                    if (r[k] > 0) {
                        непусто[k]++;
                    }
                    t += r[k];
                }
                всего += t;
                макс = Math.max(макс, t);
            }
            double n = v.size();
            System.out.printf("%-8s %9.2f %7.0f%% %9.2f %7.0f%% %8.2f %6d%n", e.getKey(),
                разделов > 0 ? сум[0] / n : 0, разделов > 0 ? 100 * непусто[0] / n : 0,
                разделов > 1 ? сум[1] / n : 0, разделов > 1 ? 100 * непусто[1] / n : 0,
                всего / n, макс);
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
