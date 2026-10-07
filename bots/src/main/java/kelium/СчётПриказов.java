package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * СЧЁТ ПРИКАЗОВ — как на деле играются карты приказов нынешнего свода (факты для
 * разговора с дизайнером 07.10.2026; правил не меняет).
 *
 * <p>По партиям ботов: какую карту вскрывают в каком круге и на каком месте
 * в круге; как часто совпадение и открытый низ; какие действия (развилки
 * и ветки) реально разыгрывают с каждой карты.
 *
 * <p>Запуск: {@code kelium.СчётПриказов [партий] [игроков] [потоков] [боты] [метка]}.
 * Отчёт: {@code reports/боты/приказы-<метка>.md}.
 */
public final class СчётПриказов {

    private СчётПриказов() {
    }

    static final class Счёт {
        int ходов;
        final Map<String, int[]> карта = new TreeMap<>();          // карта → [вскрыта, совпадение, низ открыт]
        final Map<String, int[]> картаКруг = new TreeMap<>();      // карта → вскрытий по кругам 1..4
        final int[][] место = new int[6][3];                       // место в круге → [ходов, совпадений, низ открыт]
        final Map<String, Map<String, Integer>> действия = new TreeMap<>(); // карта → действие:ветка → раз
        final Map<String, Integer> всеДействия = new TreeMap<>();
        final Map<String, Integer> свободные = new TreeMap<>();

        void слить(Счёт о) {
            ходов += о.ходов;
            о.карта.forEach((k, v) -> сложить(карта.computeIfAbsent(k, x -> new int[3]), v));
            о.картаКруг.forEach((k, v) -> сложить(картаКруг.computeIfAbsent(k, x -> new int[4]), v));
            for (int i = 0; i < место.length; i++) {
                сложить(место[i], о.место[i]);
            }
            о.действия.forEach((k, v) -> v.forEach((a, n) ->
                действия.computeIfAbsent(k, x -> new TreeMap<>()).merge(a, n, Integer::sum)));
            о.всеДействия.forEach((k, v) -> всеДействия.merge(k, v, Integer::sum));
            о.свободные.forEach((k, v) -> свободные.merge(k, v, Integer::sum));
        }

        static void сложить(int[] a, int[] b) {
            for (int i = 0; i < a.length; i++) {
                a[i] += b[i];
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int потоков = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        List<String> боты = List.of((args.length > 3 ? args[3]
            : "punisher:3,stalker:3,supplier:3,builder:3").split(","));
        String метка = args.length > 4 ? args[4] : "свод";
        String свод = System.getProperty("kelium.свод", "1.50.0");

        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        List<Future<Счёт>> fs = new ArrayList<>();
        for (int g = 0; g < партий; g++) {
            final int номер = g;
            fs.add(пул.submit(() -> партия(свод, игроков, 9000L + номер, боты, номер)));
        }
        Счёт всего = new Счёт();
        for (Future<Счёт> f : fs) {
            всего.слить(f.get());
        }
        пул.shutdown();

        StringBuilder md = new StringBuilder();
        md.append("# Приказы на деле — ").append(метка).append("\n\n");
        md.append(String.format(Locale.ROOT, "Свод %s, партий %d, игроков %d, боты %s. Ходов: %d.%n%n",
            свод, партий, игроков, боты, всего.ходов));

        md.append("## Карты: как часто, совпадение, открытый низ\n\n");
        md.append("| карта | вскрыта | доля | совпадение | низ открыт | круг 1 | круг 2 | круг 3 | круг 4 |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        всего.карта.forEach((k, v) -> {
            int[] кр = всего.картаКруг.getOrDefault(k, new int[4]);
            md.append(String.format(Locale.ROOT, "| %s | %d | %.1f%% | %.1f%% | %.1f%% | %d | %d | %d | %d |%n",
                k, v[0], 100.0 * v[0] / Math.max(1, всего.ходов), 100.0 * v[1] / Math.max(1, v[0]),
                100.0 * v[2] / Math.max(1, v[0]), кр[0], кр[1], кр[2], кр[3]));
        });

        md.append("\n## Место в круге\n\n| место | ходов | совпадение | низ открыт |\n|---|---:|---:|---:|\n");
        for (int i = 0; i < игроков; i++) {
            int[] v = всего.место[i];
            md.append(String.format(Locale.ROOT, "| %d | %d | %.1f%% | %.1f%% |%n",
                i + 1, v[0], 100.0 * v[1] / Math.max(1, v[0]), 100.0 * v[2] / Math.max(1, v[0])));
        }

        md.append("\n## Действия с карт приказов (развилка:ветка), всего\n\n| действие | раз | на ход |\n|---|---:|---:|\n");
        всего.всеДействия.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> md.append(String.format(Locale.ROOT, "| %s | %d | %.2f |%n",
                e.getKey(), e.getValue(), (double) e.getValue() / Math.max(1, всего.ходов))));

        md.append("\n## Действия по картам\n\n");
        всего.действия.forEach((k, m) -> {
            int n = всего.карта.getOrDefault(k, new int[3])[0];
            md.append("**").append(k).append("** (").append(n).append(" ходов): ");
            List<String> части = new ArrayList<>();
            m.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> части.add(String.format(Locale.ROOT, "%s %.2f", e.getKey(),
                    (double) e.getValue() / Math.max(1, n))));
            md.append(String.join(", ", части)).append("\n\n");
        });

        md.append("## Ветки, полученные с карт (награды, арсенал), всего\n\n| действие | раз |\n|---|---:|\n");
        всего.свободные.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> md.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n"));

        Path out = Path.of("reports", "боты", "приказы-" + метка + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md, StandardCharsets.UTF_8);
        System.out.println(md);
        System.out.println("записано: " + out.toAbsolutePath());
    }

    static Счёт партия(String свод, int игроков, long сид, List<String> боты, int номер) {
        GameConfig cfg = GameConfig.buildCached(свод, игроков, сид, null, null);
        GameState s = Setup.buildGame(cfg);
        List<Agent> ags = new ArrayList<>();
        for (int i = 0; i < игроков; i++) {
            String id = боты.get((i + номер) % боты.size());
            ags.add(kelium.agents.BotCatalog.create(id, i, new Random(сид * 31 + i), игроков));
        }
        Счёт с = new Счёт();
        int[] раунд = {-1};
        int[] вРаунде = {0};
        String[] текущая = {null};
        GameEngine.playGame(s, ags, ev -> {
            String тип = String.valueOf(ev.get("type"));
            if ("turn_orders".equals(тип)) {
                if (s.round != раунд[0]) {
                    раунд[0] = s.round;
                    вРаунде[0] = 0;
                }
                int k = вРаунде[0]++;
                int место = k % игроков;
                int круг = Math.min(3, k / игроков);
                String карта = String.valueOf(ev.get("top"));
                текущая[0] = карта;
                boolean совп = Boolean.TRUE.equals(ev.get("coincided"));
                boolean низ = Boolean.TRUE.equals(ev.get("bottom_open"));
                с.ходов++;
                int[] v = с.карта.computeIfAbsent(карта, x -> new int[3]);
                v[0]++;
                v[1] += совп ? 1 : 0;
                v[2] += низ ? 1 : 0;
                с.картаКруг.computeIfAbsent(карта, x -> new int[4])[круг]++;
                с.место[место][0]++;
                с.место[место][1] += совп ? 1 : 0;
                с.место[место][2] += низ ? 1 : 0;
            } else if ("action".equals(тип) && Boolean.TRUE.equals(ev.get("ok"))) {
                Object f = ev.get("fork");
                String имя = (f == null ? "" : f + ":") + ev.get("action");
                if (Boolean.TRUE.equals(ev.get("free"))) {
                    с.свободные.merge(имя, 1, Integer::sum);
                } else if (текущая[0] != null) {
                    с.всеДействия.merge(имя, 1, Integer::sum);
                    с.действия.computeIfAbsent(текущая[0], x -> new TreeMap<>()).merge(имя, 1, Integer::sum);
                }
            }
        });
        return с;
    }
}
