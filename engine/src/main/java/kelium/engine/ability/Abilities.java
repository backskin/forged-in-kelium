package kelium.engine.ability;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.GameState;
import kelium.core.PlayerState;

/**
 * РЕЕСТР СПОСОБНОСТЕЙ — единственное место, где движок узнаёт о картах.
 *
 * <p>Способность регистрируется по своему {@code id}, совпадающему с полем
 * {@code passive} в данных карты. Активными для игрока считаются способности его
 * УСТАНОВЛЕННЫХ карт арсенала и карт супер-арсенала.
 *
 * <p><b>Зачем реестр знает, какие точки спрашивались.</b> Ревизия 13.08.2026:
 * шесть пассивок из 29 «существовали» и не работали — движок просто не спрашивал
 * их в нужном месте, и поймать это было нечем. Теперь каждый
 * {@link RuleQuery#of} отмечает точку как спрошенную, а {@link #unaskedHooks()}
 * возвращает точки, которые объявлены живыми способностями, но не спрошены ни
 * разу. Тест на этом падает — значит мёртвая пассивка невозможна по построению.
 */
public final class Abilities {

    private Abilities() {
    }

    private static final Map<String, Ability> REGISTRY = new LinkedHashMap<>();
    private static final Set<Hook> ASKED = EnumSet.noneOf(Hook.class);

    // НАБОР СПОСОБНОСТЕЙ ПОДКЛЮЧАЕТСЯ ЗДЕСЬ. Иначе реестр в настоящей игре пуст:
    // класс набора не загружается сам по себе, и все переведённые пассивки
    // считаются нереализованными (карты молча изымаются из колоды). Поймано
    // прогоном настоящей партии 13.08.2026.
    static {
        CoreAbilities.install();
        // Новый арсенал (список дизайнера 12–13.08.2026): способности, вводящие
        // новые ходы, а не прибавку к числу.
        ArsenalAbilities.install();
        // Арсенал 2.0.0 (14.08.2026): 17 из 21 карты, изъятых из колоды на
        // подготовке (Setup.cullUnimplemented) — либо алиасы уже работающих
        // способностей, либо новая механика.
        Arsenal2Abilities.install();
    }

    /** Зарегистрировать способность (обычно из статического блока набора). */
    public static void register(Ability ability) {
        REGISTRY.put(ability.id(), ability);
    }

    /** Способность по идентификатору пассивки, либо null — такой ещё нет. */
    public static Ability byId(String id) {
        return id == null ? null : REGISTRY.get(id);
    }

    /**
     * ПОДКЛЮЧЁННЫЕ ТОЧКИ ПРАВИЛ — точки, которые движок действительно спрашивает.
     *
     * <p>Список объявляется здесь руками и НЕ МОЖЕТ ВРАТЬ: тест
     * {@code AbilityFrameworkTest.declaredWiredHooksAreReallyAsked} играет полную
     * партию и требует, чтобы каждая объявленная точка была спрошена хотя бы раз.
     * Приписал точку, не подключив, — тест падает.
     *
     * <p>Почему не «спрошена ли фактически»: подготовка партии идёт ДО первого
     * вопроса, и на ней ещё ничего не спрошено — тогда изымались бы все карты
     * подряд, а порядок партий в одном запуске влиял бы на состав колоды.
     */
    private static final Set<Hook> WIRED = EnumSet.of(
        Hook.UNIT_SPEED,        // kelium.engine.Speed — движение, манёвр, эффекты карт
        Hook.BUILD_PRICE,       // Actions: стройка и перенос здания
        Hook.STORAGE_CELLS,     // Storage.abilityCells — предел склада
        // Подключено 13.08.2026 вместе с новым арсеналом:
        Hook.ENERGY_SWAP_COST,          // Actions: наценка за гекс в Смене энергии
        Hook.REFRESH_INCOME,            // GameEngine.refresh: доход в Обновление
        Hook.ASSEMBLY_ENERGY_NEEDED,    // Power.usableForAction: сколько энергии нужно
        Hook.ORDER_SPEC_COUNT,          // GameEngine: сколько СПЕЦ за ход
        // Подключено 14.08.2026 вместе с арсеналом 2.0.0:
        Hook.ORDER_BOTTOM_ACTIONS,      // GameEngine.resolveTurn: действий у нижнего приказа
        Hook.BUILD_ZONE,                // Placement.buildableHexes: где можно строить
        Hook.ENERGY_SOURCES,            // Actions.EnergySwapAction: источники кубиков
        Hook.ASSEMBLY_UNITS_OUT,        // Actions.AssemblyAction: войск за Сборку
        Hook.ASSEMBLY_AMMO_OUT,         // Actions.AssemblyAction: боеприпасов за Сборку
        Hook.ATTACK_AMMO_COST,          // CombatResolver.effCost: цена атаки
        Hook.COMBAT_SECOND_BATTLE_SURCHARGE, // Actions.CombatAction: надбавка за бой
        Hook.SCORING_VP_SOURCE,         // Scoring.scorePlayer: новый источник очков
        Hook.SCORING_VP_MODIFIER,       // Scoring.scorePlayer: правка итога
        Hook.SCIENCE_PAY_WITH,          // Actions.payTrophy: чем платить за науку
        Hook.ATTACK_PROTECT_HEX,        // CombatResolver: защита жетонов на гексе
        Hook.ATTACK_RANGE,              // CombatResolver: дальность выбора цели
        Hook.TOKEN_HP,                  // Passives.effectiveHp: прочность жетона
        Hook.MOVEMENT_JUMP_OVER);       // Actions: прыжок пехоты через тайл зарождения

    /** Точки правил, объявленные подключёнными к движку. */
    public static Set<Hook> wiredHooks() {
        return EnumSet.copyOf(WIRED);
    }

    /**
     * Реализована ли способность с таким идентификатором.
     *
     * <p>Требует, чтобы точки правил способности были ПОДКЛЮЧЕНЫ к движку (см.
     * {@link #WIRED}). Иначе карта реализована только на словах: реестр её знает, а
     * в игре она молчит — ровно та беда, из-за которой шесть пассивок оказались
     * мёртвыми. Такая карта честно изымается из колоды, пока точку не подключат.
     */
    public static boolean implemented(String id) {
        Ability a = byId(id);
        if (a == null) {
            return false;
        }
        if (a.trigger() != Ability.Trigger.PASSIVE) {
            return true;             // СПЕЦ и реакции проверяются своим стендом
        }
        for (Hook h : a.hooks()) {
            if (!WIRED.contains(h)) {
                return false;        // точка ещё не подключена — карта молчит
            }
        }
        return true;
    }

    /** Все зарегистрированные способности. */
    public static List<Ability> all() {
        return List.copyOf(REGISTRY.values());
    }

    /**
     * Способности, действующие у игрока СЕЙЧАС: от установленных карт арсенала и
     * от карт супер-арсенала. Карта в руке ничего не даёт — её надо установить.
     */
    public static List<Ability> activeFor(GameState state, int seat) {
        List<Ability> out = new ArrayList<>();
        PlayerState p = state.player(seat);
        for (String cid : p.arsenalInstalled) {
            addFromCard(state, "arsenal", cid, out);
        }
        for (String cid : p.superArsenalCards) {
            addFromCard(state, "super_arsenal", cid, out);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void addFromCard(GameState state, String set, String cid,
                                    List<Ability> out) {
        Map<String, Object> card;
        try {
            card = kelium.dataio.Ctx.cards(state, set).find(cid);
        } catch (RuntimeException e) {
            return;
        }
        if (card == null) {
            return;
        }
        // у обычных карт пассивка лежит в bottom.passive, у супер-арсенала — в passive
        Object id = card.get("passive");
        if (id == null && card.get("bottom") instanceof Map<?, ?> bm) {
            id = ((Map<String, Object>) bm).get("passive");
        }
        Ability a = byId(id == null ? null : id.toString());
        if (a != null) {
            out.add(a);
        }
    }

    // ==================== новые варианты в меню решений ====================

    /**
     * Варианты, которые способности игрока добавляют в данное решение. Движок
     * спрашивает это в каждой точке выбора — так карта даёт НОВОЕ действие, а не
     * движок «знает про карту».
     */
    public static List<kelium.core.Choice> options(GameState state, int seat,
                                                   OptionSource.Slot slot) {
        List<kelium.core.Choice> out = new ArrayList<>();
        for (Ability a : activeFor(state, seat)) {
            if (a instanceof OptionSource src) {
                out.addAll(src.options(state, seat, slot));
            }
        }
        return out;
    }

    /**
     * Исполнить вариант, пришедший от способности. Опознаётся по {@code kind} вида
     * {@code ability:<id>} — по нему движок находит хозяина и отдаёт исполнение
     * ему, не зная о карте ничего.
     */
    public static boolean perform(GameState state, int seat, kelium.core.Choice chosen,
                                  kelium.core.Agent agent) {
        if (chosen == null || chosen.kind() == null || !chosen.kind().startsWith("ability:")) {
            return false;
        }
        String id = chosen.kind().substring("ability:".length());
        int cut = id.indexOf(':');
        if (cut >= 0) {
            id = id.substring(0, cut);
        }
        Ability a = byId(id);
        if (a instanceof OptionSource src) {
            return src.perform(state, seat, chosen, agent);
        }
        return false;
    }

    /** Вариант пришёл от способности (движку не надо знать, от какой именно). */
    public static boolean isAbilityChoice(kelium.core.Choice ch) {
        return ch != null && ch.kind() != null && ch.kind().startsWith("ability:");
    }

    // ==================== покрытие точек (страховка от мёртвых карт) ====================

    /** Отметить, что движок спросил эту точку. Вызывается из {@link RuleQuery}. */
    static void markAsked(Hook hook) {
        ASKED.add(hook);
    }

    /** Забыть историю опросов (перед прогоном в тесте). */
    public static void resetAsked() {
        ASKED.clear();
    }

    /** Точки, спрошенные движком хотя бы раз с последнего сброса. */
    public static Set<Hook> askedHooks() {
        return EnumSet.copyOf(ASKED.isEmpty() ? EnumSet.noneOf(Hook.class) : ASKED);
    }

    /**
     * ТОЧКИ-СИРОТЫ: объявлены зарегистрированными способностями, но движок их не
     * спрашивал. Каждая такая точка — способность, которая ничего не делает.
     */
    public static Map<Hook, List<String>> unaskedHooks() {
        Map<Hook, List<String>> out = new LinkedHashMap<>();
        for (Ability a : REGISTRY.values()) {
            for (Hook h : a.hooks()) {
                if (a.trigger() == Ability.Trigger.PASSIVE && !ASKED.contains(h)) {
                    out.computeIfAbsent(h, k -> new ArrayList<>()).add(a.id());
                }
            }
        }
        return out;
    }
}
