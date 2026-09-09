package kelium;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ЧТО ДАЁТ КАЖДАЯ СТАРТОВАЯ КАРТА АРСЕНАЛА.
 *
 * <p>Вопрос дизайнера 09.09.2026: «пересмотри эффекты на начальном арсенале, а
 * то вот +1 энергия это имба, а другие наоборот хрень». Пока это разговор о
 * впечатлении, спорить не о чем: восемь карт раздаются по одной на игрока, и
 * увидеть перекос можно только по партиям.
 *
 * <p>Что считается. Стартовая карта у места известна с подготовки (её кладёт
 * {@code Setup} взакрытую в ячейку арсенала), поэтому каждую партию можно
 * приписать четырём картам сразу — по карте на место. Дальше на карту
 * записываются: сколько партий с ней сыграно, сколько из них выиграно, средние
 * победные очки её владельца и что владелец с картой СДЕЛАЛ — установил, сжёг
 * на утиль или так и оставил лежать.
 *
 * <p>ЧЕМ ЭТО НЕ ЯВЛЯЕТСЯ. Это не сила эффекта в вакууме: карта достаётся
 * случайно, а места ротируются, поэтому доля побед мешает силу карты с
 * везением на раскладке. Читать надо разницу между картами при одинаковом
 * числе партий, и чем больше партий, тем меньше шума. Пятьдесят партий на
 * четверых дают по 25 партий на карту — этого хватает, чтобы отличить «имбу»
 * от «хрени», но не хватает на тонкие пять процентов.
 *
 * <p>Запуск: {@code kelium.СтартовыйАрсенал [партий] [игроков] [уровень ботов]}
 */
public final class СтартовыйАрсенал {

    private СтартовыйАрсенал() {
    }

    /** Что случилось с картой за партию. */
    private static final class Итог {
        int партий;
        int побед;
        int очковВсего;
        int установил;
        int сжёг;
        int осталосьВРуке;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int уровень = args.length > 2 ? Integer.parseInt(args[2]) : 2;

        Map<String, Итог> по_карте = new LinkedHashMap<>();
        Map<String, String> имена = new LinkedHashMap<>();
        List<String> роли = Bots.ROSTER_4;

        for (int g = 0; g < партий; g++) {
            long seed = 77000L + g;
            GameConfig cfg = LayoutLibrary.configFor(игроков, seed);
            GameState s = Setup.buildGame(cfg);

            // КАРТА ЗАПОМИНАЕТСЯ ДО ПАРТИИ: к концу она может быть установлена,
            // сожжена или удалена из игры, и по концу уже не узнать, что кому
            // досталось.
            List<String> стартовая = new ArrayList<>();
            for (PlayerState p : s.players) {
                стартовая.add(p.arsenalHand.isEmpty() ? null : p.arsenalHand.get(0));
            }

            List<Agent> боты = new ArrayList<>();
            for (int seat = 0; seat < игроков; seat++) {
                боты.add(Bots.create(роли.get(seat % роли.size()), Bots.Level.of(уровень),
                    seat, new Random(seed * 31 + seat), игроков));
            }
            Map<String, Object> итог = new GameEngine(s, боты, ev -> { }).run();
            Object победитель = итог.get("winner");

            for (int seat = 0; seat < игроков; seat++) {
                String cid = стартовая.get(seat);
                if (cid == null) {
                    continue;
                }
                Итог и = по_карте.computeIfAbsent(cid, k -> new Итог());
                и.партий++;
                if (победитель instanceof Number n && n.intValue() == seat) {
                    и.побед++;
                }
                int очки = 0;
                for (int v : Scoring.scorePlayer(s, seat).values()) {
                    очки += v;
                }
                и.очковВсего += очки;
                PlayerState p = s.player(seat);
                if (p.allInstalledArsenal().contains(cid)) {
                    и.установил++;
                } else if (p.arsenalHand.contains(cid)) {
                    и.осталосьВРуке++;
                } else {
                    и.сжёг++;
                }
                имена.computeIfAbsent(cid, k -> имя(cfg, k));
            }
        }

        System.out.println("свод " + GameConfig.DEFAULT_RULESET + ", партий " + партий
            + ", игроков " + игроков + ", боты уровня " + уровень);
        System.out.println();
        System.out.printf("%-4s %-22s %6s %7s %8s %10s %7s %8s%n",
            "код", "карта", "партий", "побед", "доля", "ПО в среднем", "ставил", "сжигал");
        List<String> коды = new ArrayList<>(по_карте.keySet());
        коды.sort((a, b) -> Double.compare(доля(по_карте.get(b)), доля(по_карте.get(a))));
        for (String cid : коды) {
            Итог и = по_карте.get(cid);
            System.out.printf("%-4s %-22s %6d %7d %7.1f%% %10.1f %7d %8d%n",
                cid, имена.getOrDefault(cid, "?"), и.партий, и.побед,
                100.0 * и.побед / Math.max(1, и.партий),
                и.очковВсего / (double) Math.max(1, и.партий),
                и.установил, и.сжёг);
        }
        System.out.println();
        System.out.println("Доля побед у восьми карт при честной раздаче — 1/" + игроков
            + " = " + Math.round(100.0 / игроков) + "%. Отклонение вверх у одной карты и "
            + "вниз у другой при равном числе партий и есть перекос.");
    }

    private static double доля(Итог и) {
        return и.побед / (double) Math.max(1, и.партий);
    }

    /** Печатное имя карты из набора — чтобы в отчёте не было одних кодов. */
    private static String имя(GameConfig cfg, String cid) {
        try {
            Map<String, Object> c = cfg.content.get("arsenal").find(cid);
            Object n = c == null ? null : c.get("name");
            return n == null ? cid : String.valueOf(n);
        } catch (RuntimeException нет) {
            return cid;
        }
    }
}
