package kelium.agents;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.observe.PublicView;
import kelium.report.Json;

/**
 * ВЫГРУЗКА ОБУЧАЮЩИХ ДАННЫХ ДЛЯ PYTORCH-ПАЙПЛАЙНА (18.08.2026).
 *
 * <p>Играет самим собой ({@link BotCatalog} — тот же состав, что и в
 * {@link Arena}) и на каждом конце хода снимает {@link PublicView} того места,
 * чей ход закончился, — ПОЛНЫЙ вид стола, а не 33 числа {@link StateFeatures}
 * (см. её javadoc: обучение на бедных признаках — ровно причина, почему прошлая
 * нейросеть не смогла играть сильнее эвристики). Каждая строка выгрузки —
 * снимок стола на момент решения плюс исход партии для того же места: это
 * ровно тот сигнал, на котором обучается {@link ValueNet} — «оценка позиции →
 * как всё кончилось», только на богатом входе.
 *
 * <p>Формат — JSONL (одна партия даёт много строк, каждая — отдельный JSON):
 * <pre>
 * {"seat":N, "round":R, "view":{...PublicView.toMap()...},
 *  "outcome":{"vp":V, "win":true|false, "margin":M}}
 * </pre>
 *
 * <p>Разнообразие партий: на каждый стол садятся боты РАЗНЫХ характеров и
 * видов из {@link BotCatalog#ALL} (кроме чистого {@code random} — с ним стол
 * играет бессмысленно, и хорошие/плохие позиции неотличимы). Без разнообразия
 * сеть выучила бы только то, как ходит один характер, а не что вообще значит
 * «хорошая позиция».
 */
public final class TrainingDataExport {

    private TrainingDataExport() {
    }

    public static void main(String[] args) throws IOException {
        int games = 200;
        int players = 4;
        long seed = 1;
        Path out = Path.of("data", "training", "games.jsonl");

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--games" -> games = Integer.parseInt(args[++i]);
                case "--players" -> players = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                default -> { }
            }
        }

        // Весь состав, КРОМЕ чистого random — см. javadoc класса.
        List<String> pool = new ArrayList<>();
        for (BotCatalog.Entry e : BotCatalog.ALL) {
            if (!"random".equals(e.id())) {
                pool.add(e.id());
            }
        }

        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        int written = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (int g = 0; g < games; g++) {
                long gameSeed = seed * 1_000_003L + g;
                Random pickRng = new Random(gameSeed);
                written += playOne(players, gameSeed, pool, pickRng, w);
                if ((g + 1) % 10 == 0) {
                    System.out.println("партий: " + (g + 1) + "/" + games
                        + ", строк: " + written);
                }
            }
        }
        System.out.println("готово: " + games + " партий, " + written
            + " обучающих строк -> " + out.toAbsolutePath());
    }

    private static int playOne(int players, long seed, List<String> pool,
                                Random pickRng, BufferedWriter w) throws IOException {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState state = Setup.buildGame(cfg);

        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            String spec = pool.get(pickRng.nextInt(pool.size()));
            agents.add(BotCatalog.create(spec, seat, new Random(seed * 131 + seat * 17 + 3), players));
        }

        List<Map<String, Object>> snapshots = new ArrayList<>();
        GameEngine.playGame(state, agents, event -> {
            if ("turn_end".equals(event.get("type"))) {
                int seat = (Integer) event.get("seat");
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("seat", seat);
                rec.put("round", state.round);
                rec.put("view", PublicView.of(state, seat).toMap());
                snapshots.add(rec);
            }
        });

        int[] vp = new int[players];
        for (int seat = 0; seat < players; seat++) {
            vp[seat] = Scoring.scorePlayer(state, seat).getOrDefault("total", 0);
        }

        for (Map<String, Object> rec : snapshots) {
            int seat = (Integer) rec.get("seat");
            int rivalMax = Integer.MIN_VALUE;
            for (int other = 0; other < players; other++) {
                if (other != seat) {
                    rivalMax = Math.max(rivalMax, vp[other]);
                }
            }
            int margin = rivalMax == Integer.MIN_VALUE ? 0 : vp[seat] - rivalMax;
            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("vp", vp[seat]);
            outcome.put("win", margin >= 0);
            outcome.put("margin", margin);
            rec.put("outcome", outcome);
            w.write(Json.write(rec));
            w.newLine();
        }
        return snapshots.size();
    }
}
