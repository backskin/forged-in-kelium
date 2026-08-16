package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.agents.PassiveAgent;
import kelium.agents.SearchAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.TokenStats;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.core.PlayerBoard;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * СОЛО-УЧЕНИЯ: один настоящий бот один на один с полем, набитым чужими
 * жетонами, — второе место за столом занимает {@link PassiveAgent} и хода
 * фактически не делает (см. javadoc там же).
 *
 * <p>Проверяет дословный вопрос дизайнера 16.08.2026: если у бота уже есть
 * ударный кулак и боеприпас (экономику не гоняем заново — это отдельный
 * вопрос), хватит ли ДВУХ раундов, чтобы снести 7-8 расставленных вражеских
 * жетонов? Раньше у меня был другой довод (потолок «один бой в приказ в
 * раунд») — designer поймал в нём фактическую ошибку (Бой на приказе
 * Операция ЕЩЁ и на джокере «Безопасность», плюс арсенал даёт бесплатные
 * повторные Бои, плюс один розыгрыш Боя уже и так бьёт, пока хватает
 * боеприпаса и есть цель — см. {@code CombatResolver.runBattle}). Этот
 * скрипт — прямой измерительный ответ вместо новой прикидки на бумаге.
 *
 * <p>Жетоны раскиданы по НЕСКОЛЬКИМ разным гексам (кольца 1 и 2 от старта
 * атакующего), а не в одну кучу — один розыгрыш Боя бьёт только ПО ОДНОМУ
 * гексу-цели, значит снести жетоны на разных гексах можно только несколькими
 * розыгрышами Боя за раунд (Операция + джокер + арсенал), и это тоже видно
 * в логе.
 *
 * <p>Замечание дизайнера (16.08.2026): цена в 2 БП за удар — не свойство рода
 * войск, а печатная цена БЕЗ КРАСНОГО МОДУЛЯ АТАКИ. Модули M1-M4 (набор R1,
 * {@code data/modules/modules.2.0.0.yaml}) кладутся на слот рода войск и режут
 * цену атаки до 1 БП. Даём атакующему {@code redModulesGiven} готовых модулей
 * — движок сам спросит агента, куда их положить, на бесплатном этапе
 * «Обновление» перед первым раундом ({@code Modules.moduleSwap}), и видно,
 * возьмёт ли бот их вообще и на какой род войск поставит.
 *
 * <p>Четвёртый аргумент — id набора красных модулей (bag_R0/bag_R1/bag_R2),
 * если нужно сравнить наборы между собой: пересобирает {@code s.redBag} тем же
 * загрузчиком, что и {@link Setup}, а не своей копией.
 *
 * <p>Запуск: {@code kelium.SoloWarDrill [сид] [число вражеских жетонов]
 * [красных модулей у атакующего] [id набора красных модулей]}.
 */
public final class SoloWarDrill {

    private SoloWarDrill() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 777001L;
        int enemyCount = args.length > 1 ? Integer.parseInt(args[1]) : 8;
        int redModulesGiven = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        String redBagOverride = args.length > 3 ? args[3] : null;

        GameConfig base = GameConfig.build(GameConfig.DEFAULT_RULESET, 2, seed, null, null);
        GameConfig config = LayoutLibrary.configFor(base, 2, seed);
        GameState s = Setup.buildGame(config);

        if (redBagOverride != null) {
            Object mv = config.ruleset.get("content_versions.modules", null);
            kelium.engine.ModuleSets.Library lib = kelium.engine.ModuleSets.load(
                config.dataRoot, mv == null ? null : mv.toString());
            s.redBag.clear();
            s.redBag.addAll(kelium.engine.ModuleSets.buildBag(
                lib, lib.redSets(), redBagOverride, 2, new Random(seed)));
            out.println("набор красных модулей переопределён на " + redBagOverride);
        }

        TokenStats stats = TokenStats.fromContent(
            PlayerBoard.tokensEntry(config.content.get("boards").entries),
            config.ruleset.tokenHpBonusAll());

        PlayerState attacker = s.player(0);
        PlayerState dummy = s.player(1);
        int uid = 500_000;

        // УДАРНЫЙ КУЛАК УЖЕ СОБРАН И БОЕПРИПАС УЖЕ ЕСТЬ — экономику с нуля тут
        // не гоняем, это отдельный вопрос (сколько раундов уходит на завод и
        // сборку — уже измерено и задокументировано отдельно). Тут проверяем
        // ИМЕННО пропускную способность Боя при готовом войске.
        attacker.resources.add(Resource.AMMO, 20);
        UnitType[] strikeForce = {UnitType.INFANTRY, UnitType.INFANTRY,
            UnitType.VEHICLE, UnitType.VEHICLE, UnitType.AIRCRAFT};
        for (UnitType t : strikeForce) {
            UnitToken u = stats.makeUnit(t, 0, uid++);
            u.hexId = attacker.startHex;
            attacker.units.add(u);
        }
        // БЕСПЛАТНАЯ НАГРАДА МОДУЛЯМИ — через настоящий Modules.awardModule,
        // не через голый счётчик: партия играет на мешке bag_R1 (все атаки за
        // 1 БП, см. modules.2.0.0.yaml), и только через настоящий розыгрыш
        // жетон получает верную цену — счётчик без токена откатывается на
        // старый хардкод M1-M4 без цены вообще. Движок сам предложит их
        // разложить на бесплатном «Обновлении» перед первым раундом.
        List<String> drawnRed = new ArrayList<>();
        for (int i = 0; i < redModulesGiven; i++) {
            String id = kelium.engine.Modules.awardModule(s, attacker, "red");
            if (id != null) {
                drawnRed.add(id);
            }
        }
        out.println("выдано атакующему красных модулей атаки: " + drawnRed);

        // Кольцо 1 и кольцо 2 вокруг стартового гекса атакующего — цели на
        // РАЗНЫХ гексах, не в одну кучу.
        List<String> ring1 = new ArrayList<>(s.field.neighbors(attacker.startHex));
        List<String> ring2 = new ArrayList<>();
        for (String h : ring1) {
            for (String nb : s.field.neighbors(h)) {
                if (!nb.equals(attacker.startHex) && !ring1.contains(nb) && !ring2.contains(nb)) {
                    ring2.add(nb);
                }
            }
        }
        List<String> targets = new ArrayList<>();
        targets.addAll(ring1);
        targets.addAll(ring2);
        if (targets.isEmpty()) {
            out.println("на этом поле у стартового гекса нет соседей — раскладка не годится для учений");
            return;
        }

        UnitType[] enemyMix = {UnitType.INFANTRY, UnitType.INFANTRY, UnitType.INFANTRY,
            UnitType.VEHICLE, UnitType.VEHICLE, UnitType.VEHICLE,
            UnitType.AIRCRAFT, UnitType.AIRCRAFT};
        List<String> placedOn = new ArrayList<>();
        for (int i = 0; i < enemyCount; i++) {
            String hex = targets.get(i % targets.size());
            UnitToken u = stats.makeUnit(enemyMix[i % enemyMix.length], 1, uid++);
            u.hexId = hex;
            dummy.units.add(u);
            placedOn.add(u.type.code + "@" + hex);
        }
        out.println("=== СОЛО-УЧЕНИЯ, сид " + seed + " ===");
        out.println("вражеские жетоны (" + enemyCount + "): " + String.join(", ", placedOn));

        Agent attackerAgent = SearchAgent.deep(0, new Random(seed), Bots.genome("reaper", 2), "reaper");
        Agent dummyAgent = new PassiveAgent(1);

        TreeMap<Integer, Integer> killsByRound = new TreeMap<>();
        // combat_hit САМ ПО СЕБЕ не несёт номер раунда (CombatResolver.emit не
        // проставляет его, в отличие от круговых событий движка) — следим за
        // раундом по любому событию, которое его несёт, и вешаем бой на
        // последний известный раунд.
        int[] currentRound = {1};
        int[] ammoSpent = {0};
        var res = GameEngine.playGame(s, List.of(attackerAgent, dummyAgent), ev -> {
            String type = String.valueOf(ev.get("type"));
            if (ev.get("round") instanceof Number r) {
                currentRound[0] = r.intValue();
            }
            boolean mine = ev.get("seat") instanceof Number seatN && seatN.intValue() == 0;
            if ("module_swap".equals(type) && mine) {
                out.println("  раунд " + currentRound[0] + ": разложил модули — красные "
                    + ev.get("placed_red") + ", синие " + ev.get("placed_blue"));
            } else if ("combat_hit".equals(type) && mine) {
                int ammo = ev.get("ammo") instanceof Number a ? a.intValue() : 0;
                ammoSpent[0] += ammo;
                out.printf(Locale.ROOT, "  раунд %2d: попадание по %s, боеприпас %d, уничтожен: %s%n",
                    currentRound[0], ev.get("victim"), ammo, ev.get("destroyed"));
                if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                    killsByRound.merge(currentRound[0], 1, Integer::sum);
                }
            } else if (type.matches("build.*|assemble.*|mine.*|sell.*|market.*|hire.*|move.*")
                    && mine) {
                out.printf(Locale.ROOT, "  раунд %2d: %s%n", currentRound[0], type);
            }
        });

        int total = killsByRound.values().stream().mapToInt(Integer::intValue).sum();
        int throughRound2 = killsByRound.entrySet().stream()
            .filter(e -> e.getKey() <= 2)
            .mapToInt(java.util.Map.Entry::getValue).sum();
        out.println();
        out.println("уничтожено по раундам: " + killsByRound);
        out.printf(Locale.ROOT, "уничтожено за первые 2 раунда партии: %d из %d%n",
            throughRound2, enemyCount);
        out.printf(Locale.ROOT, "уничтожено всего за партию: %d из %d%n", total, enemyCount);
        out.printf(Locale.ROOT, "боеприпаса потрачено всего: %d (%.2f на уничтожение)%n",
            ammoSpent[0], total == 0 ? 0.0 : (double) ammoSpent[0] / total);
        out.println("итог партии: " + res);
    }
}
