package kelium.engine.ability;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitToken;
import kelium.engine.Actions;
import kelium.engine.Modules;
import kelium.engine.Storage;
import kelium.engine.ability.Hint.Bottleneck;
import kelium.engine.ability.Hint.Horizon;

/**
 * АРСЕНАЛ 2.0.0 — надиктованная дизайнером колода (12–13.08.2026), вторая
 * партия способностей. {@code AuditNewestCards} нашёл 14.08.2026, что 21 карта
 * из 24 изымается из колоды на подготовке ({@code Setup.cullUnimplemented}) —
 * их пассивки не реализованы. Здесь реализована бОльшая часть: где мехами уже
 * повторяет существующую способность (другое название той же карты), она
 * АЛИАСИТСЯ на существующий класс, а не переписывается заново.
 *
 * <p>Не реализованы намеренно (требуют состояния НА КОНКРЕТНОЙ карте — ability
 * в реестре ОДНА на процесс, не на копию карты у игрока — или глубокого
 * вмешательства в конвейер стройки/энергии): {@code kelium_ignores_block},
 * {@code storage_holds_trophy_cubes}, {@code card_is_energy_source_upkeep},
 * {@code build_on_adjacent_without_wall}. Карты с ними остаются отсеянными.
 * {@code storage_holds_trophy_cubes} (id в данных сознательно НЕ переименован —
 * карта отсеяна, лишний риск сломать алиасинг ни к чему) держал «трофейный
 * кубик», а после переименования ресурса (2026-08-15, «трофейное очко» →
 * «обломок») это тот же кубик, что теперь И ТАК ограничен складом наравне с
 * келемием/боеприпасом (см. {@code Storage.debrisMax}) — [КОНФЛИКТ], из-за
 * которого карта была отложена, формально снят; осталось решить МЕХАНИКУ карты
 * (гарантированный доп. пул на 3 обломка сверх общего бюджета, или что-то ещё)
 * — не додумывать её самостоятельно, дождаться дизайнера.
 */
public final class Arsenal2Abilities {

    private Arsenal2Abilities() {
    }

    public static void install() {
        // ==== АЛИАСЫ: та же механика, что уже реализована, другое имя карты ====
        alias("air_gives_range_two", "attack_range2_with_aircraft");
        alias("air_shields_own_hex", "aircraft_protects_hex");
        alias("airbase_needs_one_less_energy", "airbase_energy_minus1");
        alias("vehicle_fast_fragile", "vehicle_speed_plus1_hp_minus1");
        alias("coin_per_energy_cube_on_refresh", "plants_pay_coins");
        alias("kelium_instead_of_trophy", "science_pay_with_kelium");
        aliasOption("spec_heal_one_damage", "heal_one_for_ammo");

        Abilities.register(new AmmoCellAndSpecAssembly());
        Abilities.register(new KeliumCellAndSpecMining());
        Abilities.register(new LootEnemyBuildingHex());
        Abilities.register(new DrawTwoObjectivesKeepOne());
        Abilities.register(new BottomOrderTwoActions());
        Abilities.register(new BuildingsPlus1HpUntilRoundEnd());
        Abilities.register(new AmmoOnBeingRetaliated());
        Abilities.register(new CoinOnKill());
        Abilities.register(new ExtraTrophyOnSpawnFlip());
        Abilities.register(new BuildOnAdjacentWithoutWall());
        Abilities.register(new CardIsEnergySourceUpkeep());
        Abilities.register(new KeliumIgnoresBlock());
    }

    /** Зарегистрировать {@code newId} как точную копию уже работающей способности. */
    private static void alias(String newId, String baseId) {
        Ability base = Abilities.byId(baseId);
        if (base == null) {
            throw new IllegalStateException("alias на незарегистрированную способность: " + baseId);
        }
        Abilities.register(new AliasAbility(newId, base));
    }

    /** То же, но базовая способность ещё и {@link OptionSource} (даёт СПЕЦ-вариант). */
    private static void aliasOption(String newId, String baseId) {
        Ability base = Abilities.byId(baseId);
        if (!(base instanceof OptionSource src)) {
            throw new IllegalStateException("aliasOption: " + baseId + " не OptionSource");
        }
        Abilities.register(new AliasOptionAbility(newId, base, src));
    }

    private static final class AliasAbility implements Ability {
        private final String id;
        private final Ability base;

        AliasAbility(String id, Ability base) {
            this.id = id;
            this.base = base;
        }

        @Override public String id() { return id; }
        @Override public Trigger trigger() { return base.trigger(); }
        @Override public Set<Hook> hooks() { return base.hooks(); }
        @Override public void modify(RuleQuery q) { base.modify(q); }
        @Override public boolean apply(GameState s, int seat, Agent a) {
            return base.apply(s, seat, a);
        }
        @Override public Hint hint() { return base.hint(); }
    }

    private static final class AliasOptionAbility implements Ability, OptionSource {
        private final String id;
        private final Ability base;
        private final OptionSource baseOptions;

        AliasOptionAbility(String id, Ability base, OptionSource baseOptions) {
            this.id = id;
            this.base = base;
            this.baseOptions = baseOptions;
        }

        @Override public String id() { return id; }
        @Override public Trigger trigger() { return base.trigger(); }
        @Override public Set<Hook> hooks() { return base.hooks(); }
        @Override public void modify(RuleQuery q) { base.modify(q); }
        @Override public Hint hint() { return base.hint(); }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            return baseOptions.options(state, seat, slot);
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            return baseOptions.perform(state, seat, chosen, agent);
        }
    }

    // ==================================================================
    //  СОСТАВНЫЕ: доп. ячейка склада (PASSIVE) + СПЕЦ-действие
    // ==================================================================

    /**
     * «Патронный ящик»: +1 ячейка боеприпаса, СПЕЦ за 1 монету — сборка
     * боеприпасов с любого своего запитанного военного здания (не открывая
     * действие Сборка). Военные здания — те, что печатают ammo в assembly.
     */
    private static final class AmmoCellAndSpecAssembly implements Ability, OptionSource {

        @Override public String id() {
            return "ammo_cell_and_spec_assembly";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.STORAGE_CELLS);
        }

        @Override public void modify(RuleQuery q) {
            if (q.subject() == Resource.AMMO || q.subject() == null) {
                q.add(1);
            }
        }

        private static BuildingToken poweredMilitary(GameState state, int seat) {
            for (BuildingToken b : state.player(seat).buildingsOnField()) {
                if ((b.type == BuildingType.BARRACKS || b.type == BuildingType.FACTORY
                        || b.type == BuildingType.AIRBASE
                        || b.type == BuildingType.COMMAND_CENTER)
                        && b.powered()) {
                    return b;
                }
            }
            return null;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC || poweredMilitary(state, seat) == null
                    || !state.player(seat).resources.canPay(Resource.COIN, 1)) {
                return List.of();
            }
            return List.of(new Choice("ability:" + id(), id(),
                "СПЕЦ: 1 монета — сборка боеприпасов с запитанного здания"));
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            return apply(state, seat, agent);
        }

        @Override public boolean apply(GameState state, int seat, Agent agent) {
            PlayerState p = state.player(seat);
            BuildingToken b = poweredMilitary(state, seat);
            if (b == null || !p.resources.canPay(Resource.COIN, 1)) {
                return false;
            }
            p.resources.pay(Resource.COIN, 1);
            Storage.addAmmoCapped(state, p, Modules.assemblyOutput(p, b.type, "ammo"));
            return true;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.AMMO, 2.5, Horizon.NOW, null,
                "лишняя ячейка боеприпаса + внеочередная сборка за монету", false);
        }
    }

    /**
     * «Келемиевый бак»: +1 ячейка келемия, СПЕЦ за 1 монету — добыча одним своим
     * запитанным добытчиком (та же выработка/переворот тайла, что и в обычной
     * Добыче — {@link Actions#mineFromMiner}).
     */
    private static final class KeliumCellAndSpecMining implements Ability, OptionSource {

        @Override public String id() {
            return "kelium_cell_and_spec_mining";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.STORAGE_CELLS);
        }

        @Override public void modify(RuleQuery q) {
            if (q.subject() == Resource.KELIUM || q.subject() == null) {
                q.add(1);
            }
        }

        /** Запитанный добытчик с жилой в досягаемости, либо null. */
        private static Object[] poweredMinerWithGrid(GameState state, int seat) {
            for (BuildingToken b : state.player(seat).buildingsOnField()) {
                if (b.type != BuildingType.MINER || !b.powered()) {
                    continue;
                }
                String grid = Actions.minerAdjacentGridWithKelium(state, b);
                if (grid != null) {
                    return new Object[]{b, grid};
                }
            }
            return null;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC || poweredMinerWithGrid(state, seat) == null
                    || !state.player(seat).resources.canPay(Resource.COIN, 1)) {
                return List.of();
            }
            return List.of(new Choice("ability:" + id(), id(),
                "СПЕЦ: 1 монета — добыча запитанным добытчиком"));
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            return apply(state, seat, agent);
        }

        @Override public boolean apply(GameState state, int seat, Agent agent) {
            PlayerState p = state.player(seat);
            Object[] found = poweredMinerWithGrid(state, seat);
            if (found == null || !p.resources.canPay(Resource.COIN, 1)) {
                return false;
            }
            p.resources.pay(Resource.COIN, 1);
            Actions.mineFromMiner(state, p, (BuildingToken) found[0], (String) found[1]);
            return true;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.KELIUM, 3.0, Horizon.NOW, null,
                "лишняя ячейка келемия + внеочередная добыча за монету", false);
        }
    }

    // ==================================================================
    //  ЧИСТЫЕ СПЕЦ-ДЕЙСТВИЯ
    // ==================================================================

    /**
     * «Мародёрка»: если твои войска стоят на гексе со зданием врага — забери у
     * него 1 боеприпас или 1 келемий.
     */
    private static final class LootEnemyBuildingHex implements Ability, OptionSource {

        @Override public String id() {
            return "spec_loot_enemy_building_hex";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        /** Первый вражеский владелец здания на гексе, где стоит наше войско. */
        private static Integer targetOwner(GameState state, int seat) {
            PlayerState me = state.player(seat);
            for (UnitToken u : me.units) {
                if (u.hexId == null || !u.alive()) {
                    continue;
                }
                for (int other = 0; other < state.numPlayers(); other++) {
                    if (other == seat) {
                        continue;
                    }
                    for (BuildingToken b : state.player(other).buildingsOnField()) {
                        if (u.hexId.equals(b.hexId)) {
                            return other;
                        }
                    }
                }
            }
            return null;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            Integer owner = slot == Slot.SPEC ? targetOwner(state, seat) : null;
            if (owner == null) {
                return List.of();
            }
            var enemy = state.player(owner).resources;
            if (!enemy.canPay(Resource.AMMO, 1) && !enemy.canPay(Resource.KELIUM, 1)) {
                return List.of();
            }
            return List.of(new Choice("ability:" + id(), owner,
                "СПЕЦ: забрать боеприпас или келемий у соседа по гексу"));
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen.payload() instanceof Integer owner)) {
                return apply(state, seat, agent);
            }
            return loot(state, seat, owner, agent);
        }

        @Override public boolean apply(GameState state, int seat, Agent agent) {
            Integer owner = targetOwner(state, seat);
            return owner != null && loot(state, seat, owner, agent);
        }

        private static boolean loot(GameState state, int seat, int owner, Agent agent) {
            PlayerState me = state.player(seat);
            var enemy = state.player(owner).resources;
            List<Choice> opts = new ArrayList<>();
            if (enemy.canPay(Resource.AMMO, 1)) {
                opts.add(new Choice("loot", Resource.AMMO, "боеприпас"));
            }
            if (enemy.canPay(Resource.KELIUM, 1)) {
                opts.add(new Choice("loot", Resource.KELIUM, "келемий"));
            }
            if (opts.isEmpty()) {
                return false;
            }
            Choice pick = agent == null ? opts.get(0)
                : agent.choose(state, opts, java.util.Map.of("kind", "loot"));
            Resource r = (Resource) pick.payload();
            enemy.pay(r, 1);
            me.resources.add(r, 1);
            return true;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.AMMO, 2.0, Horizon.NOW,
                (s, seat) -> targetOwner(s, seat) != null,
                "войска стоят на гексе с чужим зданием", false);
        }
    }

    /** «Штабная почта»: 1 монета — возьми две карты задания, оставь одну. */
    private static final class DrawTwoObjectivesKeepOne implements Ability, OptionSource {

        @Override public String id() {
            return "spec_draw_two_objectives_keep_one";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC || !state.player(seat).resources.canPay(Resource.COIN, 1)
                    || state.decks.get("objectives").drawPile.isEmpty()) {
                return List.of();
            }
            return List.of(new Choice("ability:" + id(), id(),
                "СПЕЦ: 1 монета — тяни 2 карты задания, оставь 1"));
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            return apply(state, seat, agent);
        }

        @Override public boolean apply(GameState state, int seat, Agent agent) {
            PlayerState p = state.player(seat);
            if (!p.resources.canPay(Resource.COIN, 1)) {
                return false;
            }
            String c1 = state.decks.get("objectives").draw(state.rng);
            if (c1 == null) {
                return false;
            }
            p.resources.pay(Resource.COIN, 1);
            String c2 = state.decks.get("objectives").draw(state.rng);
            if (c2 == null) {
                p.objectiveHand.add(c1);
                return true;
            }
            List<Choice> opts = List.of(
                new Choice("keep", c1, c1), new Choice("keep", c2, c2));
            Choice pick = agent == null ? opts.get(0)
                : agent.choose(state, opts, java.util.Map.of("kind", "keep_objective"));
            String kept = (String) pick.payload();
            String discarded = kept.equals(c1) ? c2 : c1;
            p.objectiveHand.add(kept);
            state.decks.get("objectives").discard(discarded);
            return true;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ACTIONS, 2.0, Horizon.NOW, null,
                "выбор задания за монету, минуя обычную раздачу", false);
        }
    }

    // ==================================================================
    //  ПРАВКА ПРИКАЗА
    // ==================================================================

    /**
     * «Двойной протокол» / «Параллельный контур»: если твой нижний приказ
     * совпал с чужим верхним — играешь ДВА разных его действия вместо одного.
     * Копии одной технологии — держать открытой можно только одну.
     */
    private static final class BottomOrderTwoActions implements Ability {

        @Override public String id() {
            return "bottom_order_two_actions";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.ORDER_BOTTOM_ACTIONS);
        }

        @Override public void modify(RuleQuery q) {
            q.atLeast(2);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ACTIONS, 3.0, Horizon.THIS_ROUND, null,
                "нижний приказ при совпадении даёт два действия вместо одного", false);
        }
    }

    // ==================================================================
    //  ЗДАНИЯ И БОЙ (реакции на события)
    // ==================================================================

    /**
     * «Аварийные щиты»: твои здания +1 здоровья до конца раунда; в Возврат
     * здания, набравшие урон не меньше ИСХОДНОЙ прочности, уходят в резерв.
     */
    private static final class BuildingsPlus1HpUntilRoundEnd implements Ability {

        @Override public String id() {
            return "buildings_plus1_hp_until_round_end";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.TOKEN_HP);
        }

        @Override public void modify(RuleQuery q) {
            if (q.subject() instanceof BuildingToken b && b.owner() == q.seat()) {
                q.add(1);
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 2.0, Horizon.THIS_ROUND, null,
                "здания на раунд крепче, но раненые не переживут Возврат", false);
        }
    }

    /** «Ответный залп»: контратаковали после твоего Боя — получи 1 боеприпас. */
    private static final class AmmoOnBeingRetaliated implements Ability {

        @Override public String id() {
            return "ammo_on_being_retaliated";
        }

        @Override public Trigger trigger() {
            return Trigger.ON_EVENT;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.AMMO, 1.5, Horizon.NOW, null,
                "контратака по мне возвращает боеприпас", false);
        }
    }

    /** «Премия за голову»: конец Боя, уничтожил хоть один жетон — 1 монета. */
    private static final class CoinOnKill implements Ability {

        @Override public String id() {
            return "coin_on_kill";
        }

        @Override public Trigger trigger() {
            return Trigger.ON_EVENT;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.COINS, 1.5, Horizon.NOW, null,
                "успешный бой приносит монету", false);
        }
    }

    /**
     * «Геологи»: алиас легаси-пассивки {@code extraction_flip_bonus_trophy} —
     * оба id проверяются {@link kelium.engine.Passives#extractionFlipBonusTrophy}
     * напрямую (эта пассивка живёт вне реестра способностей, портирована из
     * forge/engine/passives.py вместе с остальными легаси-пассивками).
     */
    /**
     * «Вольная застройка»: строить можно на гексах, просто СОСЕДСТВУЮЩИХ с
     * твоими зданиями, без примыкания своей стенкой. Чужая или нейтральная
     * стенка с той стороны по-прежнему держит проход — карта снимает требование
     * к СВОЕЙ форме, а не физическую преграду.
     */
    private static final class BuildOnAdjacentWithoutWall implements Ability {

        @Override public String id() {
            return "build_on_adjacent_without_wall";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.BUILD_ZONE);
        }

        @Override public void modify(RuleQuery q) {
            q.atLeast(1.0);   // 1 = «соседства достаточно, стенка не нужна»
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 3.5, Horizon.REST_OF_GAME, null,
                "строю на любом соседнем гексе, не разворачивая здание", false);
        }
    }

    /**
     * «Полевой генератор»: карта сама — источник 1 кубика энергии в Смене
     * энергии. Содержание — 1 монета в Обновление, иначе карта удаляется из игры
     * (см. {@code GameEngine.payArsenalUpkeep}).
     */
    private static final class CardIsEnergySourceUpkeep implements Ability {

        @Override public String id() {
            return "card_is_energy_source_upkeep";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.ENERGY_SOURCES);
        }

        @Override public void modify(RuleQuery q) {
            q.add(1);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ENERGY, 1.0, Horizon.REST_OF_GAME, null,
                "карта даёт кубик энергии, но требует монету каждый раунд", false);
        }
    }

    /**
     * «Резервный штаб»: СПЕЦ — перенеси на карту 1 келемий из запаса ЛИБО сожги
     * лежащий на ней келемий, чтобы проигнорировать блокировку одного действия
     * своего приказа (совпадение верха с чужим режет ход до одного действия).
     *
     * <p>Келемий лежит НА КАРТЕ, а не в запасе игрока, поэтому состояние живёт в
     * {@link PlayerState#arsenalCardKelium}: способность в реестре одна на
     * процесс и своего состояния иметь не может.
     */
    private static final class KeliumIgnoresBlock implements Ability, OptionSource {

        private static final String CARD = "b08";

        @Override public String id() {
            return "kelium_ignores_block";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        private static int onCard(GameState state, int seat) {
            return state.player(seat).arsenalCardKelium.getOrDefault(CARD, 0);
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            PlayerState p = state.player(seat);
            if (onCard(state, seat) == 0 && p.resources.canPay(Resource.KELIUM, 1)) {
                out.add(new Choice("ability:" + id() + ":put", id(),
                    "СПЕЦ: положить келемий на «Резервный штаб»"));
            }
            // Жечь есть смысл только когда блокировка ДЕЙСТВИТЕЛЬНО случилась в
            // этот ход: иначе карта сгорит впустую.
            if (onCard(state, seat) > 0 && state.journal != null
                    && state.journal.of(seat).orderBlocked) {
                out.add(new Choice("ability:" + id() + ":burn", id(),
                    "СПЕЦ: сжечь келемий — обойти блокировку приказа"));
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            PlayerState p = state.player(seat);
            String kind = chosen == null || chosen.kind() == null ? "" : chosen.kind();
            if (kind.endsWith(":put")) {
                if (!p.resources.canPay(Resource.KELIUM, 1) || onCard(state, seat) > 0) {
                    return false;
                }
                p.resources.pay(Resource.KELIUM, 1);
                p.arsenalCardKelium.merge(CARD, 1, Integer::sum);
                return true;
            }
            if (kind.endsWith(":burn")) {
                if (onCard(state, seat) <= 0 || state.journal == null
                        || !state.journal.of(seat).orderBlocked) {
                    return false;
                }
                p.arsenalCardKelium.merge(CARD, -1, Integer::sum);
                state.journal.of(seat).blockBypassGrants += 1;
                return true;
            }
            return false;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ACTIONS, 3.0, Horizon.THIS_ROUND, null,
                "келемий на карте выкупает действие, отнятое блокировкой", false);
        }
    }

    private static final class ExtraTrophyOnSpawnFlip implements Ability {

        @Override public String id() {
            return "extra_trophy_on_spawn_flip";
        }

        @Override public Trigger trigger() {
            return Trigger.ON_EVENT;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.TROPHY, 2.0, Horizon.REST_OF_GAME, null,
                "переворот тайла зарождения приносит трофейное очко сверху", false);
        }
    }
}
