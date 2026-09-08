package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.agents.Bots;
import kelium.agents.PositionValue;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ЩЕДРОСТЬ ЗАДАНИЙ — при какой награде задания начинают ВЫПОЛНЯТЬ, а не жечь.
 *
 * <p>ВОПРОС, НА КОТОРЫЙ ОТВЕЧАЕТ СТЕНД. Карту задания можно либо выполнить (низ:
 * условие на поле, потом награда), либо сжечь (верх: бесплатное действие сразу).
 * Все замеры проекта показывали одно: жгут в разы чаще. Это арифметика, а не
 * тупость ботов — бесплатное действие стоит примерно половину приказа, а награда
 * низа была три-четыре монеты. Стенд поднимает награды множителем
 * ({@code kelium.cards.objectives.Щедрость}) и меряет, где кривая переходит
 * через половину: доля толку = выполнено / (выполнено + сожжено).
 *
 * <p>ЧТО СЧИТАЕТСЯ, кроме доли: выполнено и сожжено за партию на игрока,
 * получено карт (чтобы доля читалась), победные очки и уничтожения — жирные
 * задания не должны заодно похоронить войну, а лишний ресурс в награде именно
 * это и умеет.
 *
 * <p>ОДИН ПРОЦЕСС НА ВСЕ ТОЧКИ, и это требует осторожности: множитель читается
 * при загрузке каталога, поэтому между точками сбрасывается кэш конфигурации,
 * кэш цен наград у ботов и память геномов. Без сброса вторая точка считалась бы
 * по числам первой и замер соврал бы молча.
 *
 * <p>Запуск: {@code kelium.ЩедростьЗаданий [игроков] [партий на точку] [уровень]
 * [множители через запятую]}. Отчёт: {@code reports/balance/щедрость-заданий.md}.
 */
public final class ЩедростьЗаданий {

    private ЩедростьЗаданий() {
    }

    /** Счётчики одной точки (одного множителя). */
    static final class Точка {
        final double множитель;
        int games;
        int objDrawn;
        int objDone;
        int objBurn;
        int objLeft;
        int arsInstall;
        int arsBurn;
        int units;
        int kills;
        int battles;
        int vp;
        int rounds;
        final Map<String, Integer> перВыполнено = new LinkedHashMap<>();

        Точка(double множитель) {
            this.множитель = множитель;
        }

        double на(int v) {
            return games == 0 ? 0 : (double) v / games;
        }

        /** Доля толку: выполнено из всего, с чем что-то сделали. */
        double доля() {
            int acted = objDone + objBurn;
            return acted == 0 ? 0 : 100.0 * objDone / acted;
        }

        /**
         * СКОЛЬКО КАРТ ДОБРАНО событием движка.
         *
         * <p>Это НЕ все полученные карты: событие {@code objective_drawn} движок
         * шлёт только на раздаче и на пополнении руки в Возврат. Карты, пришедшие
         * НАГРАДОЙ (задание платит картой задания, рынок меняет келемий на две
         * карты, контейнер выдаёт карту), события не шлют — поэтому «выполнено из
         * полученных» здесь не считается вовсе: такая доля была бы посчитана не по
         * тому знаменателю и врала бы тем сильнее, чем щедрее награды. Головная
         * мера одна и та же во всех замерах проекта — доля толку.
         */
        double добрано() {
            return на(objDrawn);
        }
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8);
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 12;
        int level = args.length > 2 ? Integer.parseInt(args[2]) : 2;
        String[] scales = (args.length > 3 ? args[3] : "1,1.5,2,2.5,3").split(",");

        List<Точка> точки = new ArrayList<>();
        for (String s : scales) {
            double k = Double.parseDouble(s.trim().replace(',', '.'));
            точки.add(замер(out, players, games, level, k));
        }

        StringBuilder md = new StringBuilder();
        md.append("# Щедрость заданий — где их начинают выполнять\n\n")
            .append("Стенд `kelium.ЩедростьЗаданий`. ").append(players)
            .append(" игроков, ").append(games).append(" партий на точку, боты уровня ")
            .append(level).append(", свод ").append(GameConfig.DEFAULT_RULESET)
            .append(".\n\nМножитель поднимает СЧЁТНОЕ добро в наградах заданий "
                + "(монеты, боеприпасы, трофеи, келемий, карты заданий); жетоны модулей "
                + "и карты арсенала не умножаются, начальные задания не трогаются вовсе.\n\n")
            .append("Доля толку = выполнено / (выполнено + сожжено). Всё остальное — "
                + "на игрока за партию.\n\n")
            .append("| множитель | доля толку | выполнено | сожжено | осталось | добрано | ПО | убито | боёв | арсенал уст. | войск | раундов |\n")
            .append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Точка т : точки) {
            md.append(String.format(Locale.ROOT,
                "| ×%.2f | **%.0f%%** | %.2f | %.2f | %.2f | %.2f | %.1f | %.2f | %.2f | %.2f | %.2f | %.1f |\n",
                т.множитель, т.доля(), т.на(т.objDone), т.на(т.objBurn),
                т.на(т.objLeft), т.добрано(), т.на(т.vp), т.на(т.kills),
                т.на(т.battles), т.на(т.arsInstall), т.на(т.units),
                (double) т.rounds / Math.max(1, т.games / players)));
        }
        md.append("\n## Чем выполняют\n\n");
        for (Точка т : точки) {
            md.append("- ×").append(String.format(Locale.ROOT, "%.2f", т.множитель))
                .append(": ").append(т.перВыполнено).append("\n");
        }
        Path dir = Path.of("reports", "balance");
        Files.createDirectories(dir);
        Path file = dir.resolve("щедрость-заданий.md");
        Files.writeString(file, md, StandardCharsets.UTF_8);
        out.println();
        out.print(md);
        out.println("отчёт: " + file);
    }

    static Точка замер(PrintStream out, int players, int games, int level, double k) {
        // МНОЖИТЕЛЬ ЧИТАЕТСЯ ПРИ ЗАГРУЗКЕ КАТАЛОГА — значит сперва настройка,
        // потом сброс всего, что успело посчитаться по прежним числам.
        System.setProperty("kelium.objectives.scale", String.valueOf(k));
        GameConfig.clearCache();
        PositionValue.forgetRewards();
        Bots.forgetCache();

        Точка т = new Точка(k);
        List<String> roster = Bots.ROSTER_4;
        for (int g = 0; g < games; g++) {
            long seed = 9000L + g;
            GameConfig cfg = LayoutLibrary.configFor(players, seed);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            int shift = (int) (seed % players);
            for (int i = 0; i < players; i++) {
                String ch = roster.get((i + shift) % roster.size());
                agents.add(Bots.create(ch, Bots.Level.of(level), i,
                    new Random(seed * 31 + i), players));
            }
            new GameEngine(s, agents, ev -> {
                switch (String.valueOf(ev.get("type"))) {
                    case "objective_drawn" -> т.objDrawn++;
                    case "objective" -> {
                        т.objDone++;
                        т.перВыполнено.merge(String.valueOf(ev.get("card")), 1, Integer::sum);
                    }
                    case "objective_burn" -> т.objBurn++;
                    case "arsenal" -> {
                        if ("install".equals(ev.get("mode"))) {
                            т.arsInstall++;
                        } else if ("burn".equals(ev.get("mode"))) {
                            т.arsBurn++;
                        }
                    }
                    case "combat_hit" -> {
                        if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                            т.kills++;
                        }
                    }
                    case "action" -> {
                        if (!(ev.get("telemetry") instanceof Map<?, ?> m)) {
                            return;
                        }
                        if ("assembly".equals(ev.get("action")) && m.get("units") instanceof Number n) {
                            т.units += n.intValue();
                        }
                        if ("combat".equals(ev.get("action")) && m.get("battle") instanceof Number b) {
                            т.battles += b.intValue();
                        }
                    }
                    default -> { }
                }
            }).run();
            т.rounds += s.round;
            for (int i = 0; i < players; i++) {
                т.games++;
                т.vp += Scoring.scorePlayer(s, i).getOrDefault("total", 0);
                т.objLeft += s.player(i).objectiveHand.size();
            }
        }
        out.printf(Locale.ROOT,
            "×%.2f: доля толку %.0f%% (выполнено %.2f, сожжено %.2f), ПО %.1f, убито %.2f%n",
            k, т.доля(), т.на(т.objDone), т.на(т.objBurn), т.на(т.vp), т.на(т.kills));
        return т;
    }
}
