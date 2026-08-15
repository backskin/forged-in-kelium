package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.HeuristicAgent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.Action;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.Storage;
import kelium.engine.TurnContext;
import kelium.report.BatchResult;
import kelium.report.TelemetryCollector;
import kelium.core.TurnJournal;
import kelium.core.Agent;
import kelium.core.Choice;

/** Тесты закрытых пробелов паритета с Python: телеметрия, склад-кап, перенос здания. */
class ParityGapsTest {

    private GameState build() {
        GameConfig cfg = GameConfig.build(4, 7L);
        GameState s = Setup.buildGame(cfg);
        s.journal = new TurnJournal(s.numPlayers());
        s.agents = new ArrayList<>();
        return s;
    }

    // ---- 1. Телеметрия собирает победителя, счёт и действия из потока событий ----
    @Test
    void telemetryCollectsWinnerAndActionsFromEventStream() {
        GameConfig cfg = GameConfig.build(4, 123L);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            agents.add(new HeuristicAgent(seat, new Random(123L * 1000 + seat), "balanced"));
        }
        TelemetryCollector col = new TelemetryCollector();
        Map<String, Object> result = GameEngine.playGame(s, agents, col::record);
        TelemetryCollector.GameReport rep = col.report();

        assertEquals(4, rep.numPlayers);
        assertEquals(result.get("winner"), rep.winner);
        assertEquals(result.get("condition"), rep.condition);
        assertEquals(4, rep.scores.size(), "счёт по всем 4 местам");
        assertTrue(rep.rounds >= 1, "число раундов посчитано");
        int totalActions = rep.actionCounts.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(totalActions > 0, "какие-то действия сыграны и посчитаны");

        // BatchResult поверх одного отчёта: Markdown должен собираться без ошибок.
        BatchResult br = new BatchResult("t", 4, 1);
        br.margins.add(rep.margin());
        br.seatWins.merge(rep.winner, 1, Integer::sum);
        for (var e : rep.actionCounts.entrySet()) {
            br.actionTotals.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        String md = br.renderMarkdown();
        assertTrue(md.contains("# Batch report"), "Markdown-отчёт отрендерен");
        assertTrue(md.contains("Win rate by seat"), "есть секция долей побед по месту");
    }

    // ---- 2. Награда задания (келемий) не превышает вместимость склада ----
    @Test
    void objectiveKeliumRewardRespectsStorageCap() {
        GameState s = build();
        PlayerState p = s.player(0);
        // Забиваем склад под завязку, затем щедрая награда — сверх капа не влезет.
        Storage.addKeliumCapped(p, 100);
        int capped = p.resources.kelium();
        // Прямая проверка кап-функции: добавление сверх капа ничего не даёт.
        int added = Storage.addKeliumCapped(p, 50);
        assertEquals(0, added, "склад полон — награда келемия не добавляется");
        assertEquals(capped, p.resources.kelium(), "келемий не превышает вместимость склада");
        assertTrue(capped < 18, "18 келемия невозможно (склад ограничен)");
    }

    // ---- 3. Перенос здания питает журнал movedBuilding (задание «Переезд» o16) ----
    @Test
    void moveBuildingFeedsJournal() {
        GameState s = build();
        PlayerState p = s.player(0);

        // СВОД-старт (1.5.0) добытчик больше не кладёт — ставим его руками,
        // тест проверяет ПЕРЕНОС, а не сетап.
        BuildingToken miner = s.tokenStats.makeBuilding(BuildingType.MINER, 0, 9990, 1);
        miner.hexId = p.startHex;
        p.buildings.add(miner);
        // Зона стройки растёт ТОЛЬКО через стенки, которые занимают свои здания
        // (правило уточнено 12.08.2026), поэтому добытчику надо реально занять
        // сторону — иначе переносить его будет некуда.
        kelium.core.Hex startHexObj = s.field.get(p.startHex);
        for (int side = 0; side < 6; side++) {
            if (startHexObj.sideOwner[side] == null
                    && startHexObj.neighborBySide[side] != null) {
                startHexObj.occupySides(miner.uid, java.util.List.of(side));
                break;
            }
        }
        assertNotNull(miner, "у игрока есть добытчик (поставлен тестом)");
        // Дадим достаточно монет на полную цену переноса.
        p.resources.add(kelium.core.Resource.COIN, 20);

        final int minerUid = miner.uid;
        // Агент выбирает опцию переноса именно нашего добытчика, затем любой гекс.
        Agent agent = new Agent(0, "mover") {
            @Override
            @SuppressWarnings("unchecked")
            public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                String kind = String.valueOf(ctx.getOrDefault("kind", ""));
                if ("build_pick".equals(kind)) {
                    for (Choice o : options) {
                        if ("move_pick".equals(o.kind())) {
                            Map<String, Object> pl = (Map<String, Object>) o.payload();
                            if (((Number) pl.get("uid")).intValue() == minerUid) {
                                return o;
                            }
                        }
                    }
                }
                if ("move_hex".equals(kind)) {
                    return options.get(0);   // первый доступный гекс
                }
                // прочие точки решения — первая не-pass опция
                for (Choice o : options) {
                    if (!"pass".equals(o.kind())) {
                        return o;
                    }
                }
                return options.get(options.size() - 1);
            }
        };

        String fromHex = miner.hexId;
        s.journal.startTurn(0);
        TurnContext ctx = new TurnContext(0, 1);
        Action build = Actions.create("build", s);
        var res = build.perform(p, ctx, agent);

        assertTrue(res.ok(), "перенос выполнен: " + res.detail());
        TurnJournal.TurnFacts f = s.journal.of(0);
        assertTrue(f.movedBuilding, "журнал отметил перенос здания");
        assertTrue(f.movedBuildingUids.contains(minerUid), "uid перенесённого здания в журнале");
        assertTrue(f.razedOwnHexes.contains(fromHex), "исходный гекс отмечен как снос");
        // Здание действительно сменило гекс.
        assertTrue(!fromHex.equals(miner.hexId), "добытчик стоит уже на другом гексе");
    }
}
