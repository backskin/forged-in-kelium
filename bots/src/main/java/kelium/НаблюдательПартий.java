package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Bots;
import kelium.agents.Genome;
import kelium.agents.Относительно;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * НАБЛЮДАТЕЛЬ ПАРТИЙ — ведут ли себя боты как люди за столом (заказ дизайнера
 * 07.10.2026).
 *
 * <p>Что хочет видеть дизайнер: большие планы, которые ведут к победе; просчёт
 * соперников — противодействие, помехи развитию, открытая агрессия против
 * важнейших объектов; и главное — СТИХИЙНАЯ КОАЛИЦИЯ: боты, не сговариваясь,
 * давят того, кто оторвался вперёд по ВИДИМОМУ развитию (карты в руке не видны).
 *
 * <p>Прибор смотрит сверху, боты — только своё. Видимая сила игрока считается
 * так же, как её видят боты ({@link Относительно#сила}: очки, открытые угрозы,
 * рука только числом карт). Лидер — сильнейший по этой мерке в миг события.
 *
 * <p>МЕРИЛА:
 * <ul>
 *   <li><b>коалиция</b> — доля ударов НЕ-лидеров по видимому лидеру; при
 *       случайном выборе цели она равна 1/(соперников);</li>
 *   <li><b>ключевые объекты</b> — удары по ЦУ, сносы ЦУ, военные победы,
 *       уничтоженные здания по видам;</li>
 *   <li><b>сдерживание</b> — выигрывает ли лидер середины партии (после 4-го
 *       раунда) и как меняется его отрыв к концу;</li>
 *   <li><b>задания</b> — выполнено против сожжённого (ориентир 50%);</li>
 *   <li><b>хроника</b> — первые партии словами: кто что делал по раундам, кого
 *       бил, чего хотел (намерение бота).</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.НаблюдательПартий [партий] [игроков] [потоков] [боты через запятую]
 * [хроник] [метка]}. Отчёт: {@code reports/боты/наблюдатель-<метка>.md}.
 */
public final class НаблюдательПартий {

    private НаблюдательПартий() {
    }

    /** Счётчики одной партии (складываются по всем). */
    static final class Счёт {
        int партий;
        long ударов;
        long ударовНеЛидеров;
        long ударовНеЛидеровПоЛидеру;
        long возможныхЦелей;          // для ожидаемой доли при случайном выборе
        long ударовПоЦу;
        long сносовЦу;
        long военныхПобед;
        Map<String, Long> снесено = new TreeMap<>();
        int лидерСерединыПобедил;
        int лидерСерединыЕсть;
        double отрывСередины;
        double отрывКонца;
        long заданийВып;
        long заданийСож;
        Map<String, Long> победыПоХарактеру = new TreeMap<>();
        Map<String, Long> побеждаетКак = new TreeMap<>();
        /** Путь в конце партии → [игроков, побед]. */
        Map<String, long[]> пути = new TreeMap<>();
        long сменПути;
        long игроковСПланом;
        /** С планом против без плана: [мест, побед, очков] по группам. */
        Map<String, double[]> группы = new TreeMap<>();
        long раундов;
        StringBuilder хроника = new StringBuilder();

        void добавить(Счёт о) {
            партий += о.партий;
            ударов += о.ударов;
            ударовНеЛидеров += о.ударовНеЛидеров;
            ударовНеЛидеровПоЛидеру += о.ударовНеЛидеровПоЛидеру;
            возможныхЦелей += о.возможныхЦелей;
            ударовПоЦу += о.ударовПоЦу;
            сносовЦу += о.сносовЦу;
            военныхПобед += о.военныхПобед;
            о.снесено.forEach((k, v) -> снесено.merge(k, v, Long::sum));
            лидерСерединыПобедил += о.лидерСерединыПобедил;
            лидерСерединыЕсть += о.лидерСерединыЕсть;
            отрывСередины += о.отрывСередины;
            отрывКонца += о.отрывКонца;
            заданийВып += о.заданийВып;
            заданийСож += о.заданийСож;
            о.победыПоХарактеру.forEach((k, v) -> победыПоХарактеру.merge(k, v, Long::sum));
            о.побеждаетКак.forEach((k, v) -> побеждаетКак.merge(k, v, Long::sum));
            о.пути.forEach((k, v) -> пути.merge(k, v, (a, b2) -> new long[] {a[0] + b2[0], a[1] + b2[1]}));
            сменПути += о.сменПути;
            игроковСПланом += о.игроковСПланом;
            о.группы.forEach((k, v) -> группы.merge(k, v,
                (a, b2) -> new double[] {a[0] + b2[0], a[1] + b2[1], a[2] + b2[2]}));
            раундов += о.раундов;
            хроника.append(о.хроника);
        }
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int потоков = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        List<String> боты = List.of((args.length > 3 ? args[3]
            : "punisher:3,stalker:3,supplier:3,builder:3").split(","));
        int хроник = args.length > 4 ? Integer.parseInt(args[4]) : 2;
        String метка = args.length > 5 ? args[5] : "последний";
        String свод = System.getProperty("kelium.ruleset", "1.50.0");

        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        List<Future<Счёт>> fs = new ArrayList<>();
        for (int g = 0; g < партий; g++) {
            final int номер = g;
            fs.add(пул.submit(() -> партия(свод, игроков, 70_100L + номер, боты, номер, номер < хроник)));
        }
        Счёт всего = new Счёт();
        long t0 = System.nanoTime();
        for (int i = 0; i < fs.size(); i++) {
            try {
                всего.добавить(fs.get(i).get());
            } catch (Exception e) {
                System.out.println("партия " + i + " сорвалась: " + e);
            }
            if ((i + 1) % 10 == 0) {
                System.out.printf(Locale.ROOT, "  %d/%d, %.0f с%n", i + 1, fs.size(), (System.nanoTime() - t0) / 1e9);
            }
        }
        пул.shutdown();
        String md = отчёт(всего, игроков, боты, свод, метка);
        Path out = Path.of("reports", "боты", "наблюдатель-" + метка + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md, StandardCharsets.UTF_8);
        System.out.println(md.substring(0, Math.min(md.length(), 4000)));
        System.out.println("записано: " + out.toAbsolutePath());
    }

    /** Видимая сила каждого игрока — как её видят боты. */
    static double[] силы(GameState s, Genome нейтр) {
        double[] out = new double[s.numPlayers()];
        for (int i = 0; i < out.length; i++) {
            out[i] = Относительно.сила(s, i, нейтр);
        }
        return out;
    }

    static int лидер(double[] с) {
        int best = 0;
        for (int i = 1; i < с.length; i++) {
            if (с[i] > с[best]) {
                best = i;
            }
        }
        return best;
    }

    static Счёт партия(String свод, int игроков, long сид, List<String> боты, int номер, boolean хроника) {
        GameConfig cfg = GameConfig.buildCached(свод, игроков, сид, null, null);
        GameState s = Setup.buildGame(cfg);
        List<Agent> ags = new ArrayList<>();
        String[] кто = new String[игроков];
        for (int i = 0; i < игроков; i++) {
            String id = боты.get((i + номер) % боты.size());
            кто[i] = id;
            Agent a = kelium.agents.BotCatalog.create(id, i, new Random(сид * 31 + i), игроков);
            // ОПЫТ «С ПЛАНОМ ПРОТИВ БЕЗ»: -Dkelium.наблюдатель.безПлана=чёт — без
            // плана играют чётные места в чётных партиях и нечётные в нечётных,
            // так каждое место и характер побывают в обеих группах.
            if ("чёт".equals(System.getProperty("kelium.наблюдатель.безПлана"))
                    && a instanceof kelium.agents.PlannerAgent pa && (i + номер) % 2 == 0) {
                pa.безПлана();
            }
            ags.add(a);
        }
        Genome нейтр = Bots.genome("balanced", игроков);
        Счёт с = new Счёт();
        с.партий = 1;
        int[] лидерСередины = {-1};
        double[] отрывСередины = {0};
        int[] раунд = {-1};
        StringBuilder х = new StringBuilder();
        Map<Integer, List<String>> ходы = new HashMap<>();
        if (хроника) {
            х.append("\n### Партия ").append(номер + 1).append(" (сид ").append(сид).append(")\n\n");
            for (int i = 0; i < игроков; i++) {
                х.append("- Игрок ").append(i + 1).append(": ").append(кто[i]).append('\n');
            }
        }
        GameEngine.playGame(s, ags, ev -> {
            String тип = String.valueOf(ev.get("type"));
            if (s.round != раунд[0]) {
                // НОВЫЙ РАУНД: подвести прошлый в хронике, на конце 4-го — лидер середины
                if (хроника && раунд[0] > 0) {
                    х.append("\n**Раунд ").append(раунд[0]).append("**\n\n");
                    double[] сил = силы(s, нейтр);
                    for (int i = 0; i < игроков; i++) {
                        String план = ags.get(i) instanceof kelium.agents.PlannerAgent pa && pa.стратегия() != null
                            ? " [" + pa.стратегия().словами() + "]" : "";
                        х.append(String.format(Locale.ROOT, "- И%d%s (сила %.1f, очки %d): %s%n", i + 1, план, сил[i],
                            Scoring.scorePlayer(s, i).getOrDefault("total", 0),
                            String.join("; ", ходы.getOrDefault(i, List.of()))));
                    }
                    ходы.clear();
                }
                if (раунд[0] == 4) {
                    double[] сил = силы(s, нейтр);
                    int л = лидер(сил);
                    лидерСередины[0] = л;
                    double второй = Double.NEGATIVE_INFINITY;
                    for (int i = 0; i < игроков; i++) {
                        if (i != л) {
                            второй = Math.max(второй, сил[i]);
                        }
                    }
                    отрывСередины[0] = сил[л] - второй;
                }
                раунд[0] = s.round;
            }
            Object seatO = ev.get("seat");
            int seat = seatO instanceof Integer i ? i : -1;
            switch (тип) {
                case "combat_hit" -> {
                    Object жо = ev.get("victim_owner");
                    if (!(жо instanceof Integer жертва) || жертва < 0 || жертва == seat) {
                        break;
                    }
                    с.ударов++;
                    double[] сил = силы(s, нейтр);
                    int л = лидер(сил);
                    if (seat != л) {
                        с.ударовНеЛидеров++;
                        с.возможныхЦелей += игроков - 1;
                        if (жертва == л) {
                            с.ударовНеЛидеровПоЛидеру++;
                        }
                    }
                    String жертваЧто = String.valueOf(ev.get("victim"));
                    if (жертваЧто.contains("command_center") || жертваЧто.contains("ЦУ")) {
                        с.ударовПоЦу++;
                    }
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        String вид = жертваЧто.replaceAll("L\\d+$", "");
                        с.снесено.merge(вид, 1L, Long::sum);
                    }
                    if (хроника) {
                        ходы.computeIfAbsent(seat, k -> new ArrayList<>()).add("бьёт И" + (жертва + 1)
                            + (жертва == л ? "(лидера)" : "") + " " + жертваЧто
                            + (Boolean.TRUE.equals(ev.get("destroyed")) ? " — уничтожен" : ""));
                    }
                }
                case "cu_destroyed" -> {
                    с.сносовЦу++;
                    if (хроника) {
                        Object by = ev.get("by");
                        ходы.computeIfAbsent(by instanceof Integer b ? b : -1, k -> new ArrayList<>())
                            .add("СНОСИТ ЦУ И" + (seat + 1));
                    }
                }
                case "objective" -> {
                    с.заданийВып++;
                    if (хроника) {
                        ходы.computeIfAbsent(seat, k -> new ArrayList<>()).add("выполнил задание " + ev.get("card"));
                    }
                }
                case "objective_burn" -> с.заданийСож++;
                case "action" -> {
                    if (хроника && Boolean.TRUE.equals(ev.get("ok")) && seat >= 0) {
                        String намерение = ags.get(seat).intent();
                        ходы.computeIfAbsent(seat, k -> new ArrayList<>()).add(ev.get("action")
                            + (намерение == null || намерение.isBlank() ? "" : " («" + намерение + "»)"));
                    }
                }
                default -> {
                }
            }
        });
        с.раундов = s.round;
        List<Integer> победители = s.winners.isEmpty() && s.winner != null ? List.of(s.winner) : s.winners;
        if ("military".equals(s.winCondition)) {
            с.военныхПобед++;
        }
        с.побеждаетКак.merge(String.valueOf(s.winCondition), 1L, Long::sum);
        for (int i = 0; i < игроков; i++) {
            boolean план = ags.get(i) instanceof kelium.agents.PlannerAgent pa && pa.стратегия() != null
                && pa.стратегия().путь != null;
            String группа = план ? "с планом" : "без плана";
            double[] гр = с.группы.computeIfAbsent(группа, k -> new double[3]);
            гр[0] += 1;
            гр[1] += победители.contains(i) ? 1.0 / победители.size() : 0;
            гр[2] += Scoring.scorePlayer(s, i).getOrDefault("total", 0);
            if (план) {
                kelium.agents.Стратегия ст = ((kelium.agents.PlannerAgent) ags.get(i)).стратегия();
                с.игроковСПланом++;
                с.сменПути += ст.смен;
                long[] п = с.пути.computeIfAbsent(ст.путь.name(), k -> new long[2]);
                п[0]++;
                if (победители.contains(i)) {
                    п[1]++;
                }
            }
        }
        for (int w : победители) {
            с.победыПоХарактеру.merge(кто[w].replaceAll(":.*", ""), 1L, Long::sum);
        }
        if (лидерСередины[0] >= 0) {
            с.лидерСерединыЕсть++;
            if (победители.contains(лидерСередины[0])) {
                с.лидерСерединыПобедил++;
            }
            с.отрывСередины = отрывСередины[0];
            double[] сил = силы(s, нейтр);
            double второй = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < игроков; i++) {
                if (i != лидерСередины[0]) {
                    второй = Math.max(второй, сил[i]);
                }
            }
            с.отрывКонца = сил[лидерСередины[0]] - второй;
        }
        if (хроника) {
            х.append("\n**Итог:** победил ").append(победители.stream().map(w -> "И" + (w + 1) + " (" + кто[w] + ")")
                .reduce((a, b) -> a + ", " + b).orElse("—")).append(", как: ").append(s.winCondition)
                .append(", раундов ").append(s.round).append('\n');
            с.хроника.append(х);
        }
        return с;
    }

    static String отчёт(Счёт в, int игроков, List<String> боты, String свод, String метка) {
        StringBuilder b = new StringBuilder();
        b.append("# Наблюдатель партий — ").append(метка).append("\n\n");
        b.append("Свод ").append(свод).append(", игроков ").append(игроков).append(", партий ").append(в.партий)
            .append(", боты: ").append(String.join(", ", боты)).append(" (места по кругу).\n\n");
        double ожид = в.ударовНеЛидеров == 0 ? 0 : (double) в.ударовНеЛидеров / в.возможныхЦелей;
        b.append("| мерило | значение |\n|---|---:|\n");
        b.append(String.format(Locale.ROOT, "| ударов по соперникам | %d |%n", в.ударов));
        b.append(String.format(Locale.ROOT, "| **коалиция:** удары не-лидеров по видимому лидеру | %.1f%% (случайно было бы %.1f%%) |%n",
            100.0 * в.ударовНеЛидеровПоЛидеру / Math.max(1, в.ударовНеЛидеров), 100 * ожид));
        b.append(String.format(Locale.ROOT, "| удары по ЦУ | %d (%.2f на партию) |%n", в.ударовПоЦу, (double) в.ударовПоЦу / в.партий));
        b.append(String.format(Locale.ROOT, "| сносов ЦУ | %d (%.2f на партию) |%n", в.сносовЦу, (double) в.сносовЦу / в.партий));
        b.append(String.format(Locale.ROOT, "| **сдерживание:** лидер после 4-го раунда победил | %d из %d (%.0f%%) |%n",
            в.лидерСерединыПобедил, в.лидерСерединыЕсть, 100.0 * в.лидерСерединыПобедил / Math.max(1, в.лидерСерединыЕсть)));
        b.append(String.format(Locale.ROOT, "| отрыв лидера середины: после 4-го раунда → в конце | %.1f → %.1f |%n",
            в.отрывСередины / Math.max(1, в.лидерСерединыЕсть), в.отрывКонца / Math.max(1, в.лидерСерединыЕсть)));
        b.append(String.format(Locale.ROOT, "| задания: выполнено / сожжено | %d / %d (%.0f%% выполнения) |%n",
            в.заданийВып, в.заданийСож, 100.0 * в.заданийВып / Math.max(1, в.заданийВып + в.заданийСож)));
        b.append(String.format(Locale.ROOT, "| раундов в среднем | %.1f |%n", (double) в.раундов / в.партий));
        b.append("\n## Уничтожено по видам\n\n");
        в.снесено.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue()))
            .forEach(e -> b.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n'));
        b.append("\n## Чем кончаются партии\n\n");
        в.побеждаетКак.forEach((k, v) -> b.append("- ").append(k).append(": ").append(v).append('\n'));
        b.append("\n## Пути (план бота к концу партии)\n\n| путь | игроков | побед | доля побед |\n|---|---:|---:|---:|\n");
        в.пути.forEach((k, v) -> b.append(String.format(Locale.ROOT, "| %s | %d | %d | %.0f%%%n", k, v[0], v[1],
            100.0 * v[1] / Math.max(1, v[0])).replace("%\n", "% |\n")));
        b.append(String.format(Locale.ROOT, "%nСмен пути за партию в среднем: %.2f%n",
            (double) в.сменПути / Math.max(1, в.игроковСПланом)));
        if (в.группы.size() > 1) {
            b.append("\n## С планом против без плана (одни раздачи)\n\n| группа | мест | доля побед | очки в среднем |\n|---|---:|---:|---:|\n");
            в.группы.forEach((k, v) -> b.append(String.format(Locale.ROOT, "| %s | %.0f | %.1f%% | %.1f |%n",
                k, v[0], 100 * v[1] / Math.max(1, v[0]), v[2] / Math.max(1, v[0]))));
        }
        b.append("\n## Победы по характерам\n\n");
        в.победыПоХарактеру.forEach((k, v) -> b.append("- ").append(k).append(": ").append(v).append('\n'));
        if (в.хроника.length() > 0) {
            b.append("\n## Хроники\n").append(в.хроника);
        }
        return b.toString();
    }
}
