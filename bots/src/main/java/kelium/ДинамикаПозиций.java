package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ДИНАМИКА ПОЗИЦИЙ — переезжают ли игроки по полю за партию.
 *
 * <p>Вопрос дизайнера 22.09.2026: задумана партия, где игрок начинает в одном
 * месте, а потом передумывает, бросает, перебирается в другое; к концу поле
 * тесное, бои непрерывны, снесённое тут же отстраивается — желательно в другом
 * месте. На деле он видит, что все сходятся в одной зоне и там взаимно
 * уничтожаются. Замер отвечает числами:
 * <ul>
 *   <li>по раундам — здания и войска на поле, заселённость поля, ресурс поля
 *       (келемий, тайлы, нейтралы), снято за раунд;</li>
 *   <li>переезд — сколько гексов застройки игрок за раунд ОСВОИЛ заново и
 *       сколько БРОСИЛ, насколько далеко база ушла от стартового гекса;</li>
 *   <li>за партию — на скольких разных гексах игрок хоть раз стоял зданием,
 *       и насколько бои сосредоточены на немногих гексах.</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.ДинамикаПозиций [партий] [игроков]}.
 */
public final class ДинамикаПозиций {

    private ДинамикаПозиций() {
    }

    private static final class Раунд {
        int партий;
        double зданий;
        double войск;
        double заселено;      // доля гексов поля, где стоит хоть один жетон игрока
        double келемий;
        double тайлов;
        double нейтралов;
        double снято;
        double освоено;       // новых гексов со своим зданием за раунд
        double брошено;       // гексов, где здание было и больше нет
        double отСтарта;      // среднее расстояние своих зданий до стартового гекса
        double дальний;       // самое дальнее своё здание от старта
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        List<String> состав = List.of("warlord", "reaper", "axiom", "balanced");

        final int МАКС = 12;
        Раунд[] по = new Раунд[МАКС + 1];
        for (int i = 0; i <= МАКС; i++) {
            по[i] = new Раунд();
        }
        double разныхГексов = 0;
        double максОдновременно = 0;
        double доляТоп3Боёв = 0;
        int партийСБоями = 0;
        double раундов = 0;
        double гексовПоля = 0;

        for (int g = 0; g < games; g++) {
            long seed = 9_220_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(состав.get((i + g) % состав.size()), i,
                    new Random(seed * 31 + i), players));
            }
            String[] старт = new String[players];
            for (PlayerState p : s.players) {
                for (BuildingToken b : p.buildingsOnField()) {
                    if (b.type == BuildingType.COMMAND_CENTER) {
                        старт[p.seat] = b.hexId;
                    }
                }
            }
            Map<String, Map<String, Integer>> дист = new HashMap<>();
            List<Set<String>> прежние = new ArrayList<>();
            List<Set<String>> всеГексы = new ArrayList<>();
            int[] макс = new int[players];
            for (int i = 0; i < players; i++) {
                прежние.add(new HashSet<>());
                всеГексы.add(new HashSet<>());
            }
            for (PlayerState p : s.players) {
                прежние.get(p.seat).addAll(гексыЗданий(p));
            }
            int[] снятоЗаРаунд = {0};
            Map<String, Integer> боиПоГексам = new HashMap<>();

            GameEngine.playGame(s, agents, ev -> {
                String type = String.valueOf(ev.get("type"));
                if ("combat_hit".equals(type)) {
                    Object t = ev.get("source");
                    if (t != null) {
                        боиПоГексам.merge(String.valueOf(t), 1, Integer::sum);
                    }
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        снятоЗаРаунд[0]++;
                    }
                    return;
                }
                if (!"return".equals(type)) {
                    return;
                }
                int r = ev.get("round") instanceof Number n ? n.intValue() : 0;
                if (r < 1 || r > МАКС) {
                    return;
                }
                Раунд a = по[r];
                a.партий++;
                a.снято += снятоЗаРаунд[0];
                снятоЗаРаунд[0] = 0;
                Set<String> занято = new HashSet<>();
                for (PlayerState p : s.players) {
                    Set<String> теперь = гексыЗданий(p);
                    Set<String> было = прежние.get(p.seat);
                    int нов = 0;
                    int брош = 0;
                    for (String h : теперь) {
                        if (!было.contains(h)) {
                            нов++;
                        }
                    }
                    for (String h : было) {
                        if (!теперь.contains(h)) {
                            брош++;
                        }
                    }
                    a.освоено += нов;
                    a.брошено += брош;
                    прежние.set(p.seat, теперь);
                    всеГексы.get(p.seat).addAll(теперь);
                    макс[p.seat] = Math.max(макс[p.seat], теперь.size());
                    a.зданий += p.buildingsOnField().size();
                    a.войск += p.unitsOnField().size();
                    занято.addAll(теперь);
                    for (UnitToken u : p.unitsOnField()) {
                        занято.add(u.hexId);
                    }
                    Map<String, Integer> d = дист.computeIfAbsent(старт[p.seat],
                        h -> расстояния(s, h));
                    double сумма = 0;
                    int дальн = 0;
                    for (BuildingToken b : p.buildingsOnField()) {
                        int x = d.getOrDefault(b.hexId, 0);
                        сумма += x;
                        дальн = Math.max(дальн, x);
                    }
                    int n = p.buildingsOnField().size();
                    a.отСтарта += n == 0 ? 0 : сумма / n;
                    a.дальний += дальн;
                }
                a.заселено += (double) занято.size() / s.field.size();
                int кел = 0;
                int тайл = 0;
                int нейт = 0;
                for (Hex h : s.field.hexes.values()) {
                    if (h.spawnTile != null) {
                        тайл++;
                        кел += h.spawnTile.kelium;
                    }
                    нейт += h.neutrals.size();
                }
                a.келемий += кел;
                a.тайлов += тайл;
                a.нейтралов += нейт;
            });

            раундов += s.round;
            гексовПоля += s.field.size();
            for (int i = 0; i < players; i++) {
                разныхГексов += всеГексы.get(i).size();
                максОдновременно += макс[i];
            }
            int всего = боиПоГексам.values().stream().mapToInt(Integer::intValue).sum();
            if (всего > 0) {
                List<Integer> v = new ArrayList<>(боиПоГексам.values());
                v.sort((x, y) -> y - x);
                int топ = 0;
                for (int i = 0; i < Math.min(3, v.size()); i++) {
                    топ += v.get(i);
                }
                доляТоп3Боёв += (double) топ / всего;
                партийСБоями++;
            }
        }

        out.printf("СВОД %s · игроков %d · партий %d · раундов %.1f · гексов поля %.0f%n%n",
            GameConfig.DEFAULT_RULESET, players, games, раундов / games, гексовПоля / games);
        out.println("раунд | зданий | войск | заселено | келемий | тайлов | нейтр | снято "
            + "| освоено | брошено | от старта | дальнее");
        for (int r = 1; r <= МАКС; r++) {
            Раунд a = по[r];
            if (a.партий == 0) {
                continue;
            }
            double м = a.партий * players;
            out.printf("  %2d  | %5.2f  | %5.2f |  %4.0f%%   | %6.1f  | %5.1f  | %4.1f | %5.2f "
                    + "|  %5.2f  |  %5.2f  |   %4.2f    |  %4.2f   (%d партий)%n",
                r, a.зданий / м, a.войск / м, 100 * a.заселено / a.партий,
                a.келемий / a.партий, a.тайлов / a.партий, a.нейтралов / a.партий,
                a.снято / a.партий, a.освоено / м, a.брошено / м, a.отСтарта / м,
                a.дальний / м, a.партий);
        }
        int мест = games * players;
        out.printf("%nРАЗНЫХ ГЕКСОВ со своим зданием за партию: %.2f на игрока "
                + "(одновременно максимум %.2f).%n",
            разныхГексов / мест, максОдновременно / мест);
        out.printf("БОИ: на 3 самых горячих гекса приходится %.0f%% всех ударов партии.%n",
            партийСБоями == 0 ? 0 : 100 * доляТоп3Боёв / партийСБоями);
    }

    private static Set<String> гексыЗданий(PlayerState p) {
        Set<String> out = new HashSet<>();
        for (BuildingToken b : p.buildingsOnField()) {
            out.add(b.hexId);
        }
        return out;
    }

    private static Map<String, Integer> расстояния(GameState s, String от) {
        Map<String, Integer> d = new HashMap<>();
        if (от == null) {
            return d;
        }
        ArrayDeque<String> q = new ArrayDeque<>();
        d.put(от, 0);
        q.add(от);
        while (!q.isEmpty()) {
            String h = q.poll();
            for (String n : s.field.neighbors(h)) {
                if (!d.containsKey(n)) {
                    d.put(n, d.get(h) + 1);
                    q.add(n);
                }
            }
        }
        return d;
    }
}
