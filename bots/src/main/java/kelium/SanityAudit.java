package kelium;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.LayoutLibrary;

/**
 * SanityAudit — «а НЕ ТУПЯТ ли боты»: перечень бессмысленных решений.
 *
 * <p>Заказ дизайнера 12.08.2026: «чтобы не было тупорылых строек и тупорылых
 * наймов», «чтобы энергия использовалась целенаправленно». Проверяем каждую
 * такую претензию отдельным числом, чтобы её можно было измерить до и после
 * обучения, а не обсуждать на глаз:
 *
 * <ul>
 *   <li><b>мёртвая стройка</b> — здание, которому нужна энергия, к концу партии
 *       так и не запитано (картон, который ничего не делал);</li>
 *   <li><b>простой энергии</b> — свободные кубики на источниках при том, что
 *       у самого игрока есть незапитанные потребители;</li>
 *   <li><b>мёртвый наём</b> — войско, которое за партию ни разу не сдвинулось и
 *       ни разу не ударило (вышки считаем отдельно: они неподвижны по правилам);</li>
 *   <li><b>добытчик не у жилы</b> — построен, но копать ему нечего;</li>
 *   <li><b>пустое действие</b> — розыгрыш действия, который ничего не изменил;</li>
 *   <li><b>задания</b> — сколько выполнено против того, сколько было на руках.</li>
 * </ul>
 *
 * <p>Запуск: {@code java -cp ... kelium.SanityAudit [players] [games]}
 */
public final class SanityAudit {

    private SanityAudit() {
    }

    private static final class Tally {
        int games;
        int players;
        int buildingsNeedEnergy;
        int buildingsUnpowered;
        int idleEnergyWhileHungry;
        int unitsOnField;
        int unitsNeverActed;
        int towers;
        int miners;
        int minersLiveVein;
        int minersWrongWall;
        int minersNoTile;
        int minersSpentVein;
        int actions;
        int actionsIdle;
        int objectivesDone;
        int objectiveCardsSeen;
        int kills;
        double vp;
        double vpRivals;
        final Map<String, Integer> idleByAction = new LinkedHashMap<>();
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        // третий аргумент — ПАПКА С ГЕНОМАМИ: так можно замерить «до обучения»
        // из архива и «после» из рабочей памяти одним и тем же кодом
        if (args.length > 2) {
            kelium.dataio.Locations.setBotMemory(java.nio.file.Path.of(args[2]));
            kelium.agents.Bots.forgetCache();
            System.out.println("геномы беру из " + args[2]);
        }

        Tally t = new Tally();
        t.players = players;
        for (int g = 0; g < games; g++) {
            long seed = 7000 + g;
            GameConfig cfg = LayoutLibrary.configFor(players, seed);
            GameState s = Setup.buildGame(cfg);
            // ВАЖНО: выработанный тайл СНИМАЮТ с поля целиком, поэтому к концу
            // партии по состоянию поля не отличить «построен мимо жил» от
            // «выработал свою жилу до конца». Запоминаем гексы с тайлами ДО игры.
            java.util.Set<String> tileHexesAtStart = new java.util.HashSet<>();
            for (Hex h : s.field.hexes.values()) {
                if (h.spawnTile != null) {
                    tileHexesAtStart.add(h.id);
                }
            }
            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < players; seat++) {
                String c = Bots.CHARACTERS.get((seat + g) % Bots.CHARACTERS.size());
                agents.add(Bots.create(c, seat, new Random(seed * 131 + seat + 1), players));
            }
            // КОГО ХОТЬ РАЗ ДВИНУЛИ. Движок не шлёт событие на каждый шаг юнита,
            // поэтому подслушиваем сам ВЫБОР бота: решение «двинуть жетон uid»
            // видно в опции kind=move. Это ровно та претензия дизайнера —
            // «нанял и оставил стоять».
            java.util.Set<Integer> acted = new java.util.HashSet<>();
            for (int i = 0; i < agents.size(); i++) {
                agents.set(i, new MoveWatcher(agents.get(i), acted));
            }
            GameEngine.playGame(s, agents, ev -> {
                String type = String.valueOf(ev.get("type"));
                if ("action".equals(type) && Boolean.TRUE.equals(ev.get("ok"))) {
                    t.actions++;
                    if (didNothing(ev)) {
                        t.actionsIdle++;
                        t.idleByAction.merge(String.valueOf(ev.get("action")), 1, Integer::sum);
                    }
                } else if ("combat_hit".equals(type)) {
                    t.kills += Boolean.TRUE.equals(ev.get("destroyed")) ? 1 : 0;
                } else if ("objective".equals(type)) {
                    t.objectivesDone++;
                }
            });
            t.games++;

            for (PlayerState p : s.players) {
                t.vp += Scoring.scorePlayer(s, p.seat).getOrDefault("total", 0);
                t.objectiveCardsSeen += p.objectiveHand.size();
                int hungry = 0;
                int idle = 0;
                for (BuildingToken b : p.buildingsOnField()) {
                    if (b.energySlots > 0) {
                        t.buildingsNeedEnergy++;
                        if (b.energyPlaced < b.energySlots) {
                            t.buildingsUnpowered++;
                            hungry++;
                        }
                    }
                    idle += b.energyIdle;
                    if (b.type == BuildingType.MINER) {
                        t.miners++;
                        if (minerTouchesTile(s, b, true)) {
                            t.minersLiveVein++;        // копает живую жилу — как надо
                        } else if (wallReaches(s, b, tileHexesAtStart)) {
                            t.minersSpentVein++;       // жилу выработал: тайла уже нет
                        } else if (hexNearTile(s, b.hexId, tileHexesAtStart)) {
                            t.minersWrongWall++;       // гекс верный, СТЕНКА не туда
                        } else {
                            t.minersNoTile++;          // построен вообще мимо жил
                        }
                    }
                }
                if (hungry > 0) {
                    t.idleEnergyWhileHungry += Math.min(idle, hungry);
                }
                for (UnitToken u : p.unitsOnField()) {
                    t.unitsOnField++;
                    if (u.type == UnitType.TOWER) {
                        t.towers++;
                    } else if (!acted.contains(u.uid)) {
                        t.unitsNeverActed++;
                    }
                }
            }
        }

        int seats = t.games * t.players;
        System.out.println("=== Аудит осмысленности: " + games + " партий, "
            + players + " игроков (все шесть характеров по кругу) ===");
        System.out.println();
        System.out.printf(Locale.ROOT, "Мёртвая стройка: %d из %d зданий с ячейками энергии "
            + "остались НЕзапитанными (%.1f%%)%n",
            t.buildingsUnpowered, t.buildingsNeedEnergy,
            pct(t.buildingsUnpowered, t.buildingsNeedEnergy));
        System.out.printf(Locale.ROOT, "Простой энергии: %.2f свободных кубиков на игрока "
            + "при живых голодных потребителях%n", t.idleEnergyWhileHungry / (double) seats);
        System.out.printf(Locale.ROOT, "Мёртвый наём: %d из %d подвижных войск НИ РАЗУ не были "
            + "двинуты (%.1f%%); вышек (неподвижны по правилам) %.2f на игрока%n",
            t.unitsNeverActed, t.unitsOnField - t.towers,
            pct(t.unitsNeverActed, t.unitsOnField - t.towers), t.towers / (double) seats);
        System.out.printf(Locale.ROOT, "Добытчики: %.2f на игрока. Копают живую жилу %.1f%% · "
            + "жила выработана %.1f%% · гекс верный, но СТЕНКА не туда %.1f%% · "
            + "построены вообще мимо жил %.1f%%%n",
            t.miners / (double) seats, pct(t.minersLiveVein, t.miners),
            pct(t.minersSpentVein, t.miners), pct(t.minersWrongWall, t.miners),
            pct(t.minersNoTile, t.miners));
        System.out.printf(Locale.ROOT, "Решения про добытчик, когда выбор БЫЛ: гекс у живой жилы "
            + "взят %d из %d (%.1f%%); стенка к живой жиле взята %d из %d (%.1f%%)%n",
            minerHexTaken, minerHexChances, pct(minerHexTaken, minerHexChances),
            minerFacingTaken, minerFacingChances, pct(minerFacingTaken, minerFacingChances));
        System.out.printf(Locale.ROOT, "Пустые действия: %d из %d (%.1f%%)%n",
            t.actionsIdle, t.actions, pct(t.actionsIdle, t.actions));
        System.out.println("  чаще всего пустыми оказываются: " + top(t.idleByAction, 5));
        System.out.printf(Locale.ROOT, "Задания: выполнено %.2f за партию на стол; "
            + "карт осталось на руках к концу %.2f на игрока%n",
            t.objectivesDone / (double) t.games, t.objectiveCardsSeen / (double) seats);
        System.out.printf(Locale.ROOT, "Убито жетонов: %.2f за партию на стол%n",
            t.kills / (double) t.games);
        System.out.printf(Locale.ROOT, "Победные очки: %.2f в среднем на игрока%n",
            t.vp / seats);
    }

    // Умел ли бот воспользоваться возможностью, когда она БЫЛА: считаем отдельно
    // «возможность была» и «бот её взял». Иначе не отличить тупость бота от
    // отсутствия выбора (например, нужная стенка занята чужим жетоном).
    private static int minerHexChances;
    private static int minerHexTaken;
    private static int minerFacingChances;
    private static int minerFacingTaken;

    /**
     * Обёртка над ботом: подслушивает его РЕШЕНИЯ. Запоминает, какие жетоны он
     * двинул, и проверяет качество двух решений про добытчика — выбор гекса и
     * выбор стенки, — но только когда выбор реально был.
     */
    private static final class MoveWatcher extends Agent {
        private final Agent inner;
        private final java.util.Set<Integer> moved;

        MoveWatcher(Agent inner, java.util.Set<Integer> moved) {
            super(inner.seat, inner.name);
            this.inner = inner;
            this.moved = moved;
        }

        @Override
        @SuppressWarnings("unchecked")
        public kelium.core.Choice choose(GameState state, List<kelium.core.Choice> options,
                                         Map<String, Object> context) {
            kelium.core.Choice pick = inner.choose(state, options, context);
            if (pick == null) {
                return pick;
            }
            if ("move".equals(pick.kind()) && pick.payload() instanceof Map<?, ?> pl
                    && pl.get("uid") instanceof Number n) {
                moved.add(n.intValue());
            }
            String kind = context == null ? "" : String.valueOf(context.get("kind"));
            boolean miner = context != null && "miner".equals(context.get("btype"));
            if (miner && "build_hex".equals(kind)) {
                boolean any = false;
                for (kelium.core.Choice o : options) {
                    if (o.payload() instanceof String hid && hexHasLiveVeinNear(state, hid)) {
                        any = true;
                        break;
                    }
                }
                if (any) {
                    minerHexChances++;
                    if (pick.payload() instanceof String hid && hexHasLiveVeinNear(state, hid)) {
                        minerHexTaken++;
                    }
                }
            } else if (miner && "build_facing".equals(kind)) {
                String hex = String.valueOf(context.get("hex"));
                boolean any = false;
                for (kelium.core.Choice o : options) {
                    if (facesLiveVein(state, hex, (List<Integer>) o.payload())) {
                        any = true;
                        break;
                    }
                }
                if (any) {
                    minerFacingChances++;
                    if (facesLiveVein(state, hex, (List<Integer>) pick.payload())) {
                        minerFacingTaken++;
                    }
                }
            }
            return pick;
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            inner.observeEvent(event);
        }
    }

    /** Есть ли живая жила на гексе или на соседнем (до выбора стенки). */
    private static boolean hexHasLiveVeinNear(GameState s, String hexId) {
        Hex h = s.field.get(hexId);
        if (h == null) {
            return false;
        }
        if (tileHere(h, true)) {
            return true;
        }
        for (String nb : s.field.neighbors(hexId)) {
            Hex n = s.field.get(nb);
            if (n != null && tileHere(n, true)) {
                return true;
            }
        }
        return false;
    }

    /** Смотрит ли хоть одна из выбранных сторон на живую жилу. */
    private static boolean facesLiveVein(GameState s, String hexId, List<Integer> sides) {
        Hex h = s.field.get(hexId);
        if (h == null || sides == null) {
            return false;
        }
        if (tileHere(h, true)) {
            return true;                 // сам стоит на жиле — стенка не нужна
        }
        for (int side : sides) {
            String nb = h.neighborBySide[side];
            Hex n = nb == null ? null : s.field.get(nb);
            if (n != null && tileHere(n, true)) {
                return true;
            }
        }
        return false;
    }

    /** Действие сыграно, но ничего не произошло (телеметрия пустая по сути). */
    private static boolean didNothing(Map<String, Object> ev) {
        if (!(ev.get("telemetry") instanceof Map<?, ?> tel)) {
            return false;
        }
        int sum = 0;
        for (Object v : tel.values()) {
            if (v instanceof Number n) {
                sum += Math.abs(n.intValue());
            } else if (Boolean.TRUE.equals(v)) {
                sum += 1;
            }
        }
        return sum == 0;
    }

    /**
     * Достаёт ли добытчик до тайла зарождения: своим гексом или СВОЕЙ СТЕНКОЙ.
     *
     * @param needKelium true — тайл должен быть ещё не выработан (живая жила);
     *                   false — достаточно того, что тайл вообще там лежит
     */
    private static boolean minerTouchesTile(GameState s, BuildingToken miner, boolean needKelium) {
        Hex self = s.field.get(miner.hexId);
        if (self == null) {
            return false;
        }
        if (tileHere(self, needKelium)) {
            return true;
        }
        for (int side = 0; side < 6; side++) {
            if (self.sideOwner[side] == null || self.sideOwner[side] != miner.uid) {
                continue;
            }
            String nb = self.neighborBySide[side];
            Hex n = nb == null ? null : s.field.get(nb);
            if (n != null && tileHere(n, needKelium)) {
                return true;
            }
        }
        return false;
    }

    /** Достаёт ли СТЕНКА добытчика до гекса из набора (или он сам на нём стоит). */
    private static boolean wallReaches(GameState s, BuildingToken miner,
                                       java.util.Set<String> hexes) {
        if (hexes.contains(miner.hexId)) {
            return true;
        }
        Hex self = s.field.get(miner.hexId);
        if (self == null) {
            return false;
        }
        for (int side = 0; side < 6; side++) {
            if (self.sideOwner[side] != null && self.sideOwner[side] == miner.uid
                    && hexes.contains(self.neighborBySide[side])) {
                return true;
            }
        }
        return false;
    }

    /** Был ли тайл на самом гексе или на любом его соседе (на начало партии). */
    private static boolean hexNearTile(GameState s, String hexId, java.util.Set<String> hexes) {
        if (hexes.contains(hexId)) {
            return true;
        }
        for (String nb : s.field.neighbors(hexId)) {
            if (hexes.contains(nb)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tileHere(Hex h, boolean needKelium) {
        return h.spawnTile != null && (!needKelium || h.spawnTile.kelium > 0);
    }

    private static double pct(int part, int total) {
        return total == 0 ? 0.0 : 100.0 * part / total;
    }

    private static String top(Map<String, Integer> m, int n) {
        return m.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(n)
            .map(e -> e.getKey() + " ×" + e.getValue())
            .reduce((a, b) -> a + ", " + b)
            .orElse("нет");
    }
}
