package kelium;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * SiegeProbe — БЫВАЕТ ЛИ В ИГРЕ МНОГОРАУНДОВАЯ ОСАДА?
 *
 * <p>Повод. Балансовый стенд 14.08.2026 показал невозможное: вариант «урон не
 * лечится в Обновление» ({@code combat_model.heal_per_refresh: 0}) дал ПОБИТОВО
 * те же числа, что и правила без правки — 11.0 боёв, 4.84 уничтожения, 1.5%
 * военных побед на 200 партий. Совпадение до третьего знака на такой выборке
 * невозможно, если ключ хоть на что-то влияет.
 *
 * <p>Гипотеза, которую проверяет пробник: снимать в Обновление НЕЧЕГО, потому что
 * раненых жетонов к этому моменту не остаётся. Урон копится только внутри одного
 * хода: жетон либо добивают сразу, либо он не получает урона вовсе. Тогда правило
 * «урон копится по раундам, штурм ЦУ можно вести несколько раундов» существует
 * только в тексте СВОДа, а в игре его нет — и это объясняет, почему военная
 * победа не случается: её нельзя подготовить заранее, только успеть за один ход.
 *
 * <p>Считается в момент НАЧАЛА Обновления, ДО лечения (движок испускает событие
 * {@code refresh} перед тем, как снимать кубики).
 *
 * <p>Запуск: {@code kelium.SiegeProbe [игроков] [партий]}.
 */
public final class SiegeProbe {

    private SiegeProbe() {
    }

    /** Накопитель по одной партии — чтобы не заводить полей уровня класса. */
    private static final class Tally {
        long refreshes;
        long refreshesWithWound;
        long woundedUnits;
        long woundedBuildings;
        long woundedCu;
        long maxCuDamage;

        void add(Tally o) {
            refreshes += o.refreshes;
            refreshesWithWound += o.refreshesWithWound;
            woundedUnits += o.woundedUnits;
            woundedBuildings += o.woundedBuildings;
            woundedCu += o.woundedCu;
            maxCuDamage = Math.max(maxCuDamage, o.maxCuDamage);
        }
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        // Третий аргумент — сколько урона снимать в Обновление (по умолчанию как в
        // правилах, 1). Это КОНТРОЛЬНАЯ ПРОВЕРКА самого стенда: при 0 раненых
        // обязано стать заметно больше. Если числа не сдвинулись — правка правил
        // не доезжает до движка, и все замеры с этим ключом ничего не значат.
        Integer heal = args.length > 2 ? Integer.parseInt(args[2]) : null;
        List<String> lineup = List.of("hawk", "dove", "balanced", "opportunist");

        Tally total = new Tally();
        int gamesWithWound = 0;

        for (int g = 0; g < games; g++) {
            long seed = 1000L + g;
            GameConfig base = LayoutLibrary.configFor(players, seed);
            GameConfig cfg = base;
            if (heal != null) {
                kelium.rules.Ruleset rules = base.ruleset.copy();
                rules.override("combat_model.heal_per_refresh", heal);
                cfg = new GameConfig(rules, base.content, players, seed, base.dataRoot,
                    base.boardSides, base.scenarioId, base.cuFacing, base.scenarioFile);
            }
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get(i % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }

            Tally one = new Tally();
            new GameEngine(s, agents, ev -> {
                if (!"refresh".equals(ev.get("type"))) {
                    return;
                }
                one.refreshes++;
                int wu = 0;
                int wb = 0;
                int wcu = 0;
                for (PlayerState p : s.players) {
                    for (UnitToken t : p.units) {
                        if (t.damage > 0) {
                            wu++;
                        }
                    }
                    for (BuildingToken t : p.buildings) {
                        if (t.damage > 0) {
                            wb++;
                            if (t.type == BuildingType.COMMAND_CENTER) {
                                wcu++;
                                one.maxCuDamage = Math.max(one.maxCuDamage, t.damage);
                            }
                        }
                    }
                }
                one.woundedUnits += wu;
                one.woundedBuildings += wb;
                one.woundedCu += wcu;
                if (wu + wb > 0) {
                    one.refreshesWithWound++;
                }
            }).run();

            total.add(one);
            if (one.woundedUnits + one.woundedBuildings > 0) {
                gamesWithWound++;
            }
        }

        double r = Math.max(1, total.refreshes);
        System.out.println();
        System.out.println("# Бывает ли многораундовая осада");
        System.out.printf("партий %d, игроков %d, Обновлений всего %d%n",
            games, players, total.refreshes);
        System.out.println();
        System.out.printf("Обновлений, где на поле был ХОТЬ ОДИН раненый: %d (%.1f%%)%n",
            total.refreshesWithWound, 100.0 * total.refreshesWithWound / r);
        System.out.printf("раненых войск за Обновление:  %.3f%n", total.woundedUnits / r);
        System.out.printf("раненых зданий за Обновление: %.3f%n", total.woundedBuildings / r);
        System.out.printf("раненых ЦУ за Обновление:     %.3f%n", total.woundedCu / r);
        System.out.printf("наибольший урон на ЦУ, доживший до Обновления: %d (прочность 3)%n",
            total.maxCuDamage);
        System.out.printf("партий, где хоть раз кто-то дожил до Обновления раненым: %d (%.1f%%)%n",
            gamesWithWound, 100.0 * gamesWithWound / Math.max(1, games));
        System.out.println();
        System.out.println("Все числа около нуля = правило «урон копится по раундам» в игре");
        System.out.println("не работает: жетон либо добивают в тот же ход, либо он не получает");
        System.out.println("урона вовсе. Тогда heal_per_refresh мёртв не из-за ошибки стенда,");
        System.out.println("а потому что лечить нечего — и осады как механики в игре нет.");
    }
}
