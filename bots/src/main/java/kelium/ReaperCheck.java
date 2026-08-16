package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ЗАМЕР ЖАТВЫ — сколько чужих жетонов линия сносит за партию.
 *
 * <p>Заказ дизайнера 2026-08-15: нужны боты, уничтожающие 10–15 жетонов за партию.
 * Этот стенд отвечает, сколько сносят сейчас, и заодно показывает цену: сколько
 * теряет сам, сколько шагов науки берёт, сколько усиленных заданий выполняет и не
 * разваливается ли при этом по очкам.
 *
 * <p>Места ротируются: место у стола само по себе даёт перевес, и без ротации
 * замер мерил бы место, а не характер.
 *
 * <p>Запуск: {@code kelium.ReaperCheck [партий] [игроков] [линия,линия,…]}.
 */
public final class ReaperCheck {

    private ReaperCheck() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        List<String> lineup = args.length > 2 ? Arrays.asList(args[2].split(","))
            : List.of("reaper", "warlord", "axiom", "balanced");

        int n = lineup.size();
        double[] kills = new double[n];
        double[] losses = new double[n];
        double[] hits = new double[n];
        double[] tech = new double[n];
        double[] enhanced = new double[n];
        double[] vp = new double[n];
        int[] wins = new int[n];
        double[] tableKills = {0};
        double[] rounds = {0};

        for (int g = 0; g < games; g++) {
            long seed = 5_300_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<String> seats = new ArrayList<>();
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                String ch = lineup.get((i + g) % n);
                seats.add(ch);
                agents.add(Bots.create(ch, i, new Random(seed * 31 + i), players));
            }
            int[] k = new int[players];
            int[] l = new int[players];
            int[] h = new int[players];
            int[] e = new int[players];
            Map<String, Object> res = GameEngine.playGame(s, agents, ev -> {
                String t = String.valueOf(ev.get("type"));
                if ("combat_hit".equals(t)) {
                    if (ev.get("seat") instanceof Number a && a.intValue() < players
                            && a.intValue() >= 0) {
                        h[a.intValue()]++;
                        if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                            k[a.intValue()]++;
                            tableKills[0]++;
                        }
                    }
                    // Свои потери приходят чужими событиями: место в combat_hit —
                    // это атакующий, жертва указана отдельно.
                    if (Boolean.TRUE.equals(ev.get("destroyed"))
                            && ev.get("victim_owner") instanceof Number v
                            && v.intValue() >= 0 && v.intValue() < players) {
                        l[v.intValue()]++;
                    }
                } else if ("objective".equals(t) && Boolean.TRUE.equals(ev.get("enhanced"))
                        && ev.get("seat") instanceof Number o && o.intValue() < players
                        && o.intValue() >= 0) {
                    e[o.intValue()]++;
                }
            });
            rounds[0] += res.get("rounds") instanceof Number r ? r.intValue() : 0;
            int winner = res.get("winner") instanceof Number w ? w.intValue() : -1;
            for (int i = 0; i < players; i++) {
                int idx = lineup.indexOf(seats.get(i));
                kills[idx] += k[i];
                losses[idx] += l[i];
                hits[idx] += h[i];
                enhanced[idx] += e[i];
                int steps = 0;
                for (String track : s.tech.tracks) {
                    steps += s.player(i).techSteps.getOrDefault(track, 0);
                }
                tech[idx] += steps;
                vp[idx] += Scoring.scorePlayer(s, i).getOrDefault("total", 0);
                if (winner == i) {
                    wins[idx]++;
                }
            }
        }

        // Партий на линию: при ротации мест каждая линия садится за стол
        // players/n раз за партию.
        double per = games * (players / (double) n);
        out.println("партий " + games + ", игроков " + players + ", боты: "
            + Bots.describe());
        out.printf(Locale.ROOT, "раундов за партию %.1f · УНИЧТОЖЕНО ЗА ПАРТИЮ ВСЕМИ "
            + "ВМЕСТЕ %.2f%n%n", rounds[0] / games, tableKills[0] / games);
        out.printf("%-10s %9s %8s %8s %7s %9s %7s %6s%n", "линия", "снёс", "потерял",
            "попал", "наука", "усил.зад.", "ПО", "побед");
        for (int i = 0; i < n; i++) {
            out.printf(Locale.ROOT, "%-10s %9.2f %8.2f %8.2f %7.2f %9.2f %7.1f %5d%%%n",
                lineup.get(i), kills[i] / per, losses[i] / per, hits[i] / per,
                tech[i] / per, enhanced[i] / per, vp[i] / per,
                (int) Math.round(100 * wins[i] / per));
        }
    }
}
