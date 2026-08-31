package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * СКОЛЬКО ЖЕТОНОВ МОДУЛЕЙ ВЫТЯГИВАЮТ ЗА ПАРТИЮ — красных и синих.
 *
 * <p>ВОПРОС ДИЗАЙНЕРА 25.08.2026: сколько копий каждого вида класть в синий
 * мешок — по четыре, по три или по два. Сама вероятность вытянуть НУЖНЫЙ вид от
 * этого не зависит вовсе: при равном числе копий она всегда 1/4, сколько бы
 * жетонов ни лежало. Значит выбор решает другое — ХВАТАЕТ ЛИ МЕШКА НА ПАРТИЮ.
 * Мешок, который пустеет к середине, превращает награду «жетон модуля» в пустую
 * строку на карте; мешок, который никогда не кончается, лишает поздние тяги
 * всякой интриги.
 *
 * <p>Поэтому считается ровно одно: сколько тяг случается за партию — всего и у
 * самого удачливого игрока, — и как часто мешок пустеет.
 *
 * <p>Запуск: {@code kelium.МешкиМодулей [партий] [игроков] [свод]}
 */
public final class МешкиМодулей {

    private МешкиМодулей() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        List<Integer> красныхЗаПартию = new ArrayList<>();
        List<Integer> синихЗаПартию = new ArrayList<>();
        List<Integer> красныхМаксИгрок = new ArrayList<>();
        List<Integer> синихМаксИгрок = new ArrayList<>();
        int пустелКрасный = 0;
        int пустелСиний = 0;

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 99000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 271L + g), players));
            }
            GameEngine.playGame(s, ags, ev -> { });

            int красных = 0;
            int синих = 0;
            int максК = 0;
            int максС = 0;
            for (PlayerState p : s.players) {
                красных += p.redModules;
                синих += p.blueModules;
                максК = Math.max(максК, p.redModules);
                максС = Math.max(максС, p.blueModules);
            }
            красныхЗаПартию.add(красных);
            синихЗаПартию.add(синих);
            красныхМаксИгрок.add(максК);
            синихМаксИгрок.add(максС);
            if (s.redBag != null && s.redBag.isEmpty()) {
                пустелКрасный++;
            }
            if (s.blueBag != null && s.blueBag.isEmpty()) {
                пустелСиний++;
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("# Сколько жетонов модулей вытягивают за партию\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players).append(". Места ротируются.\n\n");
        b.append("Вероятность вытянуть НУЖНЫЙ вид от числа копий не зависит: при ")
            .append("равных копиях она всегда одна и та же. Число копий решает ")
            .append("другое — хватает ли мешка на партию.\n\n");
        b.append("| показатель | красные | синие |\n|---|---:|---:|\n");
        b.append("| тяг за партию, среднее | ").append(окр(среднее(красныхЗаПартию)))
            .append(" | ").append(окр(среднее(синихЗаПартию))).append(" |\n");
        b.append("| тяг за партию, максимум | ").append(макс(красныхЗаПартию))
            .append(" | ").append(макс(синихЗаПартию)).append(" |\n");
        b.append("| у самого удачливого игрока, среднее | ")
            .append(окр(среднее(красныхМаксИгрок))).append(" | ")
            .append(окр(среднее(синихМаксИгрок))).append(" |\n");
        b.append("| у самого удачливого игрока, максимум | ").append(макс(красныхМаксИгрок))
            .append(" | ").append(макс(синихМаксИгрок)).append(" |\n");
        b.append("| партий, где мешок опустел | ").append(пустелКрасный)
            .append(" из ").append(games).append(" | ").append(пустелСиний)
            .append(" из ").append(games).append(" |\n");

        Path out = Path.of("reports", "balance", "мешки-модулей-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("красных за партию: " + окр(среднее(красныхЗаПартию))
            + ", синих: " + окр(среднее(синихЗаПартию)));
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static double среднее(List<Integer> v) {
        return v.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private static int макс(List<Integer> v) {
        return v.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static String окр(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
