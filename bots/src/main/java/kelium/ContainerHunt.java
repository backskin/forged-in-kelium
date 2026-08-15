package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.Storage;

/**
 * ContainerHunt — НАЙТИ ПАРТИЮ, ГДЕ КОНТЕЙНЕРЫ ТЕКУТ СИЛЬНЕЕ ВСЕГО.
 *
 * <p>Просьба дизайнера 13.08.2026: дать поле и сид, чтобы посмотреть в
 * проигрывателе, как боты жадно доят печатные ячейки. Средние по 10 000 партий
 * показывают масштаб, но не показывают ПОВЕДЕНИЕ — а поведение видно только на
 * конкретной партии.
 *
 * <p>Гоняет партии на действующих правилах (печатные контейнеры) и печатает
 * несколько самых «текущих» с их раскладкой и сидом.
 *
 * <p>Запуск: {@code kelium.ContainerHunt [игроков] [сколько партий перебрать]}.
 */
public final class ContainerHunt {

    private ContainerHunt() {
    }

    private record Hit(long seed, String layout, long containers, int rounds) { }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 400;

        List<Hit> hits = new ArrayList<>();
        for (int g = 0; g < games; g++) {
            long seed = 7_300_000L + g;   // те же сиды, что в стенде моделей
            String layout = args.length > 2 ? args[2] : null;
            // Раскладки дизайнера лежат ОТДЕЛЬНЫМИ файлами (scenarios/new): их надо
            // грузить как ФАЙЛ, а не искать среди встроенных вариантов по имени.
            // Иначе Setup не находит раскладку и молча играет на КОЛЬЦЕ-ЗАГЛУШКЕ.
            java.nio.file.Path file = layout == null ? null
                : GameConfig.resolveDataRoot(null).resolve("scenarios").resolve("new")
                    .resolve(layout + ".yaml");
            GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, players,
                seed, null, null, null, null, file);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            List<String> lineup = List.of("hawk", "dove", "balanced", "opportunist");
            int shift = (int) (seed % players);
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get((i + shift) % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            Storage.resetContainerStats();
            Map<String, Object> res = GameEngine.playGame(s, agents, null);
            long total = 0;
            for (long v : Storage.containerStats().values()) {
                total += v;
            }
            hits.add(new Hit(seed, String.valueOf(cfg.scenarioId), total,
                res.get("rounds") instanceof Number r ? r.intValue() : 0));
        }
        hits.sort((a, b) -> Long.compare(b.containers(), a.containers()));

        out.println("САМЫЕ «ТЕКУЩИЕ» ПАРТИИ (печатные контейнеры, " + players
            + " игрока, перебрано " + games + "):");
        for (int i = 0; i < Math.min(8, hits.size()); i++) {
            Hit h = hits.get(i);
            out.printf("  сид %d · поле %s · контейнеров %d · раундов %d%n",
                h.seed(), h.layout(), h.containers(), h.rounds());
        }
        double sum = 0;
        for (Hit h : hits) {
            sum += h.containers();
        }
        out.printf("средняя по перебору: %.1f контейнера за партию%n", sum / hits.size());
    }
}
