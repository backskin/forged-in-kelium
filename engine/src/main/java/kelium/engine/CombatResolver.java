package kelium.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.Target;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.rules.Ruleset;
import kelium.core.TurnJournal;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.dataio.Ctx;

/**
 * Разрешение боя — по документу «Бой — полные правила». Порт из
 * forge/engine/combat.py.
 *
 * <p>Процедура из 6 шагов: выбрать свой гекс с юнитом -> выбрать один смежный
 * гекс-цель -> выбрать атакующие юниты -> разрешать атаки по одной (платить
 * боеприпасы, наносить урон, сразу проверять уничтожение) -> ответный бой.
 *
 * <p>Правило закрытого гекса: пока на цели стоит вражеское/нейтральное здание,
 * наземные юниты бьют там только по зданиям/вышкам; авиация игнорирует
 * закрытость. Ответный бой: любой игрок, чьи жетоны повреждены, получает один
 * бесплатный Бой только по атакующему; ответки на ответку нет; наценки нет.
 * Модуль управляется агентом через объекты Choice.
 */
public final class CombatResolver {

    private final GameState state;
    private final Consumer<Map<String, Object>> emit;
    private final Ruleset rs;
    private List<Agent> agents;

    public CombatResolver(GameState state, Consumer<Map<String, Object>> emit) {
        this.state = state;
        this.emit = emit;
        this.rs = Ctx.rules(state);
    }

    /** Привязать агентов по местам (для ответных ударов) и вернуть self. */
    public CombatResolver bindAgents(List<Agent> agents) {
        this.agents = agents;
        return this;
    }

    private TurnJournal journal() {
        return state.journal;
    }

    private void emit(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        emit.accept(m);
    }

    /** Категория цели — свойство самого жетона ({@link Token#category()}). */
    static Target targetCategory(Token token) {
        return token.category();
    }

    private static String victimLabel(Token t) {
        if (t instanceof BuildingToken b) {
            String lvl = b.level != null ? "L" + b.level : "";
            return b.type.code + lvl;
        }
        return ((UnitToken) t).type.code;
    }

    // -- вспомогательные ------------------------------------------------------
    /**
     * Есть ли в гексе {@code source} хоть один живой жетон игрока, который
     * ДОСТАЁТ до соседнего гекса {@code target}: авиация достаёт всегда,
     * наземные — только если сторона не закрыта чужим или нейтральным зданием.
     */
    private boolean anyCanShootAcross(int seat, String source, String target) {
        for (UnitToken u : unitsOf(seat, source)) {
            if (Passability.canShootAcross(state, u, target)) {
                return true;
            }
        }
        return false;
    }

    private List<UnitToken> unitsOf(int seat, String hexId) {
        List<UnitToken> out = new ArrayList<>();
        for (UnitToken u : state.player(seat).units) {
            if (hexId.equals(u.hexId) && u.alive()) {
                out.add(u);
            }
        }
        return out;
    }

    private List<Token> allTokensOn(String hexId) {
        List<Token> out = new ArrayList<>();
        for (PlayerState p : state.players) {
            for (UnitToken u : p.units) {
                if (hexId.equals(u.hexId) && u.alive()) {
                    out.add(u);
                }
            }
            for (BuildingToken b : p.buildings) {
                if (hexId.equals(b.hexId) && b.alive()) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    private boolean hexClosedAgainst(String hexId, int attackerSeat) {
        Hex h = state.field.get(hexId);
        if (h.hasNeutral()) {
            return true;
        }
        for (PlayerState p : state.players) {
            if (p.seat == attackerSeat) {
                continue;
            }
            for (BuildingToken b : p.buildings) {
                if (hexId.equals(b.hexId) && b.alive()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Одна строка атаки: ключ строки, стоимость боеприпасов, категория цели. */
    private record AttackRow(String row, int ammoCost, Target target) {
    }

    /**
     * Строки (row, стоимость, цель) для юнита из таблицы его планшета. Красный
     * модуль заменяет цель ВТР-строки выбором из двух; позолота — обе цели.
     */
    private List<AttackRow> attackRows(int seat, UnitToken unit) {
        // ЖЕТОН СУПЕРОРУЖИЯ НЕ АТАКУЕТ (супер задания 3.0). АТАКА — это когда
        // игрок выбирает войско на гексе, смотрит таблицу атак своего планшета и
        // тратит боеприпасы по её строкам. Супероружию эта таблица недоступна:
        // ни в Бою, ни в ответном бою, ни эффектом карты. Пустой список строк —
        // единственное честное место для этого правила: всё, что бьёт, спрашивает
        // строки здесь.
        if (SuperWeapon.isWeapon(state, unit)) {
            return List.of();
        }
        // СУПЕР-ВОЙСКО (карта супер-арсенала): ДВЕ УНИВЕРСАЛЬНЫЕ АТАКИ, каждая
        // за 1 боеприпас (редакция 17.08.2026 — атаки за 2 боеприпаса отменены
        // везде). Универсальная значит «по любой цели»: все четыре категории, из
        // них выбирается одна. Ключ строки общий внутри атаки, поэтому каждая
        // атака срабатывает не больше раза за бой.
        //
        // ИСКЛЮЧЕНИЕ — ВЫШКА: у неё одна универсальная атака, зато её способность
        // бьёт веером по нескольким соседним гексам сразу.
        if (unit.superUnit) {
            List<AttackRow> sup = new ArrayList<>();
            int slots = unit.type == UnitType.TOWER ? 1 : 2;
            for (int slot = 1; slot <= slots; slot++) {
                String key = "super" + slot;
                sup.add(new AttackRow(key, 1, Target.INFANTRY));
                sup.add(new AttackRow(key, 1, Target.VEHICLE));
                sup.add(new AttackRow(key, 1, Target.AIRCRAFT));
                sup.add(new AttackRow(key, 1, Target.BUILDINGS_TOWERS));
            }
            return sup;
        }
        var side = state.player(seat).board.troop;
        if (side.dualCell()) {
            return dualCellAttackRows(seat, side, unit);
        }
        Target[] pair = side.attacks(unit.type);
        if (pair == null) {
            return List.of();
        }
        int primCost = rs.getInt("actions.combat.primary_row_ammo_cost");
        int secCost = rs.getInt("actions.combat.secondary_row_ammo_cost");
        List<AttackRow> rows = new ArrayList<>();
        rows.add(new AttackRow("primary", primCost, pair[0]));

        Map<String, Object> mod = Modules.redModuleOn(state.player(seat), unit.type);
        // ЦЕЛИ МОДУЛЯ — не всегда пара. Набор R2 (characteristics, «Модули
        // 2.0») кладёт сюда ЛИБО чисто характеристический жетон (R2-1/R2-2:
        // ключа "targets" нет вовсе — меняет HP/скорость, не цель боя), ЛИБО
        // жетон с ОДНОЙ печатной целью на обычной стороне (R2-3/R2-4: "any_unit"
        // тоже пока не заведён в {@link Target} — designer ещё не решил, как
        // это разыгрывать: свободный выбор рода войск при стрельбе — отдельная
        // точка решения, а не готовая пара). Раньше код слепо читал tcodes[0]/
        // tcodes[1], что падало с NPE/IndexOutOfBounds, стоило игроку положить
        // такой жетон, — а падать посреди партии из-за недоделанного набора
        // модулей нельзя: молча откатываемся на печатную вторичную строку.
        Object rawTargets = mod == null ? null : mod.get("targets");
        String[] tcodes = rawTargets instanceof String[] arr ? arr : null;
        Target t0 = null;
        Target t1 = null;
        if (tcodes != null && tcodes.length >= 1) {
            try {
                t0 = Target.fromCode(tcodes[0]);
                t1 = tcodes.length >= 2 ? Target.fromCode(tcodes[1]) : null;
            } catch (IllegalArgumentException notYetSupported) {
                t0 = null;   // код цели существует в данных, но Target его не знает (R3/"any_unit")
            }
        }
        if (mod == null || t0 == null) {
            rows.add(new AttackRow("secondary", secCost, pair[1]));
        } else {
            // ЦЕНА ЖЕТОНА МОДУЛЯ, А НЕ ПЕЧАТНАЯ ВТОРИЧНАЯ. Раньше здесь стоял
            // тот же secCost, что и без модуля — жетон менял ТОЛЬКО цели
            // второй строки, а цену игнорировал целиком, хотя каждый набор
            // модулей (data/modules/modules.2.0.0.yaml) несёт СВОЮ цену (у R1
            // — 1 БП на весь набор, у R0/легаси-хардкода M1-M4 цены в данных
            // нет вовсе — тогда честно остаёмся на печатной secCost).
            // Замер designer'а 16.08.2026: боевой стенд с розданными R1
            // модулями не показал НИКАКОГО падения цены удара именно из-за
            // этой строки.
            int modCost = mod.get("ammo") instanceof Number n ? n.intValue() : secCost;
            if (t1 == null) {
                rows.add(new AttackRow("secondary", modCost, t0));
            } else if (Boolean.TRUE.equals(mod.get("gold"))) {
                rows.add(new AttackRow("secondary_a", modCost, t0));
                rows.add(new AttackRow("secondary_b", modCost, t1));
            } else {
                rows.add(new AttackRow("secondary", modCost, t0));
                rows.add(new AttackRow("secondary", modCost, t1));
            }
        }
        return rows;
    }

    /**
     * БОЙ 2.0 (заказ дизайнера 18.08.2026, {@code boards.2.0.0.yaml}): вместо
     * печатной пары целей — универсальная ячейка (любая из четырёх целей, не
     * прокачивается) плюс специализированная (одна печатная цель, апгрейд
     * красным модулем).
     *
     * <p>УНИВЕРСАЛЬНАЯ строка сделана ровно тем же приёмом, что и у
     * супер-войска чуть выше ({@code sup1}/{@code sup2}): один общий ключ
     * строки на все четыре цели — строка используется не больше раза за бой,
     * а конкретная цель выбирается уже при розыгрыше.
     *
     * <p>СПЕЦИАЛИЗИРОВАННАЯ строка у ВЫШКИ бесплатна (0 боеприпасов) — так
     * решил дизайнер именно для этой ячейки в этой версии правил; на
     * универсальную ячейку это не распространяется, вышка платит за неё как
     * все.
     */
    private List<AttackRow> dualCellAttackRows(int seat, kelium.core.TroopSide side,
                                                UnitToken unit) {
        List<AttackRow> rows = new ArrayList<>();
        int universalCost = rs.getInt("actions.combat.universal_ammo_cost");

        // КУДА КЛАДЁТСЯ ЖЕТОН МОДУЛЯ — ТОЧКА ПРАВИЛ (предложение дизайнера
        // 25.08.2026, ключ actions.combat.module_on_universal).
        //
        // ПО УМОЛЧАНИЮ модуль накрывает СПЕЦИАЛЬНУЮ ячейку: печатная цель
        // меняется на две, цена та же. Это чистая прибавка — отказываться не от
        // чего, и решения в прокачке нет.
        //
        // ВАРИАНТ: модуль накрывает УНИВЕРСАЛЬНУЮ. Тогда прокачка — размен:
        // получаешь ещё одну дешёвую цель (из двух на жетоне), но теряешь
        // «достану любого за 2 боеприпаса». Появляется слепое пятно — род, до
        // которого этот жетон не дотянется вовсе, — и вместе с ним контр-игра.
        boolean модульНаУниверсальной =
            rs.getBool("actions.combat.module_on_universal", false);
        Map<String, Object> накладка =
            Modules.redModuleOn(state.player(seat), unit.type);
        boolean глухой = накладка != null
            && Boolean.TRUE.equals(накладка.get("blocks"));
        boolean универсальнаяЗакрыта = модульНаУниверсальной && накладка != null;

        if (!универсальнаяЗакрыта) {
            for (Target t : Target.values()) {
                rows.add(new AttackRow("universal", universalCost, t));
            }
        }

        Target base = side.specializedTarget(unit.type);
        if (base == null) {
            return rows;
        }
        // ГЛУХОЙ ЖЕТОН ЗАКРЫВАЕТ ЯЧЕЙКУ (решение дизайнера 25.08.2026).
        // Собственный жетон уничтожения ЦУ — такой же жетон модуля атаки, только
        // он ничего не открывает: род с ним бьёт лишь универсальной за 2
        // боеприпаса. Лежит он в общей раскладке модулей, поэтому и двигается
        // как все — обменом на планшете науки или утилем карты; игрок сам решает,
        // каким родом сейчас не воевать.
        // ЦЕНА СПЕЦИАЛЬНОЙ АТАКИ — ОДНА ДЛЯ ВСЕХ РОДОВ (диктовка дизайнера
        // 24.08.2026): 1 боеприпас, и у вышки тоже. Прежний черновик «Бой 2.0»
        // (18.08.2026) давал вышке спец-атаку бесплатно; ключ оставлен, чтобы
        // тот замер воспроизводился, но по умолчанию поблажки нет.
        int specCost = rs.getBool("actions.combat.tower_specialized_free", false)
            && unit.type == UnitType.TOWER
            ? 0
            : rs.getInt("actions.combat.specialized_ammo_cost",
                rs.getInt("actions.combat.secondary_row_ammo_cost"));

        if (глухой) {
            // Глухой жетон закрывает ТУ ячейку, на которой лежит. Если модули
            // кладутся на универсальную, то род теряет именно её — а печатная
            // дешёвая атака остаётся. Без этой ветки род с глухим жетоном
            // оставался бы вообще без единой атаки.
            if (модульНаУниверсальной) {
                rows.add(new AttackRow("specialized", specCost, base));
            }
            return rows;
        }

        Map<String, Object> mod = накладка;
        if (модульНаУниверсальной) {
            // Печатная специальная цель НЕ перекрывается: жетон лёг на
            // универсальную ячейку. Значит она остаётся у рода навсегда, и
            // распределение целей по сторонам планшета работает всю партию.
            rows.add(new AttackRow("specialized", specCost, base));
        }
        Object rawTargets = mod == null ? null : mod.get("targets");
        String[] tcodes = rawTargets instanceof String[] arr ? arr : null;
        Target t0 = null;
        Target t1 = null;
        if (tcodes != null && tcodes.length >= 1) {
            try {
                t0 = Target.fromCode(tcodes[0]);
                t1 = tcodes.length >= 2 ? Target.fromCode(tcodes[1]) : null;
            } catch (IllegalArgumentException notYetSupported) {
                t0 = null;   // код цели существует в данных, но Target его не знает
            }
        }
        if (mod == null || t0 == null) {
            if (!модульНаУниверсальной) {
                rows.add(new AttackRow("specialized", specCost, base));
            }
        } else {
            int modCost = mod.get("ammo") instanceof Number n ? n.intValue() : specCost;
            if (t1 == null) {
                rows.add(new AttackRow("specialized", modCost, t0));
            } else if (Boolean.TRUE.equals(mod.get("gold"))) {
                // ЗОЛОТО В БОЮ 2.0: ОДНА цена на ОБЕ цели суммарно (заказ
                // дизайнера) — первая стоит modCost, вторая идёт бесплатно.
                // В старой системе (не dual_cell) золото оплачивало каждую
                // цель отдельно — здесь это НАМЕРЕННО дешевле.
                rows.add(new AttackRow("specialized_gold_a", modCost, t0));
                rows.add(new AttackRow("specialized_gold_b", 0, t1));
            } else {
                rows.add(new AttackRow("specialized", modCost, t0));
                rows.add(new AttackRow("specialized", modCost, t1));
            }
        }
        return rows;
    }

    /**
     * КАКИЕ КАТЕГОРИИ ЦЕЛЕЙ этот жетон вообще способен поражать прямо сейчас —
     * с учётом красного модуля и супер-войска, а не только печатной пары на
     * планшете.
     *
     * <p>Публичный вход для ботов: раньше они смотрели одну лишь печатную
     * таблицу и потому «не видели», что модуль дал юниту право бить, скажем,
     * ЦУ, — планировали не тот ход.
     */
    public java.util.Set<Target> reachableTargets(int seat, UnitToken unit) {
        java.util.Set<Target> out = new java.util.LinkedHashSet<>();
        for (AttackRow r : attackRows(seat, unit)) {
            out.add(r.target());
        }
        return out;
    }

    /** Способен ли жетон {@code unit} игрока {@code seat} поразить этот жетон. */
    public boolean canHit(int seat, UnitToken unit, Token enemy) {
        return reachableTargets(seat, unit).contains(enemy.category());
    }

    /**
     * То же, но по состоянию партии: боты держат {@link GameState}, а не
     * резолвер. Если бой ещё не привязан (сцена собрана вручную), честно
     * откатываемся на печатную таблицу планшета.
     */
    public static boolean canHit(GameState state, int seat, UnitToken unit, Token enemy) {
        if (state.combat instanceof CombatResolver cr) {
            return cr.canHit(seat, unit, enemy);
        }
        kelium.core.TroopSide side = state.player(seat).board.troop;
        if (side.dualCell()) {
            // БОЙ 2.0: универсальная ячейка достаёт любую категорию всегда —
            // этой печатной таблицы для dual_cell сторон вообще не хватает,
            // чтобы честно ответить «нет».
            return true;
        }
        Target[] pair = side.attacks(unit.type);
        if (pair == null) {
            return false;
        }
        Target cat = enemy.category();
        for (Target t : pair) {
            if (t == cat) {
                return true;
            }
        }
        return false;
    }

    /** Провести один бой для места {@code seat} (обычный вход из действия Бой). */
    /**
     * МОГ ЛИ вообще состояться бой у игрока прямо сейчас.
     *
     * <p>Замечание дизайнера (12.08.2026): «холостым» бой считать честно —
     * только когда бой был ВОЗМОЖЕН, но не состоялся. Приказ Операция сплошь и
     * рядом вскрывают ради Движения, чтобы подвигаться или выждать момент, и
     * бить при этом попросту некого — такой розыгрыш холостым не является.
     *
     * <p>Проверяем ту же цепочку, что и настоящий бой: у игрока есть живой
     * юнит; на СОСЕДНЕМ гексе есть допустимая цель; по таблице атак этого
     * юнита есть строка, которая до цели дотягивается; и хватает боеприпасов
     * на её стоимость.
     */
    public boolean anyAttackPossible(int attackerSeat) {
        GameState s = state;
        PlayerState p = s.player(attackerSeat);
        for (UnitToken u : p.units) {
            if (u.hexId == null || !u.alive()) {
                continue;
            }
            for (String target : s.field.neighbors(u.hexId)) {
                if (!validTarget(target, attackerSeat, null)
                        || !Passability.canShootAcross(s, u, target)) {
                    continue;   // между гексами стена — этот жетон не достаёт
                }
                boolean closed = hexClosedAgainst(target, attackerSeat);
                for (AttackRow ar : attackRows(attackerSeat, u)) {
                    boolean[] firstAttackUsed = {false};
                    int cost = effCost(ar.ammoCost(), ar.target(), attackerSeat, target,
                        firstAttackUsed);
                    if (!p.resources.canPay(Resource.AMMO, cost)) {
                        continue;
                    }
                    if (pickVictimCategory(target, attackerSeat, ar.target(), null, closed, u)
                            != null) {
                        return true;
                    }
                    if (ar.target() == Target.BUILDINGS_TOWERS
                            && s.field.get(target).hasNeutral()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean runBattle(int seat, Agent agent) {
        return runBattle(seat, agent, false, null);
    }

    /**
     * Разрешить один бой. Возвращает true, если состоялась хоть одна атака.
     * {@code isRetaliation} — ответка (без повторной ответки и наценки);
     * {@code restrictTargetOwner} ограничивает цели одним владельцем.
     */
    public boolean runBattle(int attackerSeat, Agent agent, boolean isRetaliation,
                             Integer restrictTargetOwner) {
        GameState s = state;
        PlayerState p = s.player(attackerSeat);

        // Шаг 1: выбрать свой гекс, где есть хотя бы один живой юнит.
        Set<String> srcSet = new java.util.TreeSet<>();
        for (UnitToken u : p.units) {
            if (u.hexId != null && u.alive()) {
                srcSet.add(u.hexId);
            }
        }
        if (srcSet.isEmpty()) {
            dry(attackerSeat, null, null, "нет своих войск на поле");
            return false;
        }
        List<Choice> srcOpts = new ArrayList<>();
        for (String h : srcSet) {
            srcOpts.add(new Choice("combat_source", h, h));
        }
        srcOpts.add(new Choice("pass", null, "не бить"));
        Choice src = agent.choose(s, srcOpts,
            Map.of("kind", "combat_source", "retaliation", isRetaliation));
        if (src.payload() == null) {
            // Игрок сам отказался бить. Но отказ отказу рознь, и различие тут
            // принципиальное: если бить было НЕЧЕМ, отказ правильный, а действие
            // испортила прежняя решимость взять Бой при пустом поле. Если же
            // выстрел был — это ошибка бота, и чинится она в оценке, а не в
            // правилах. Проверяем честно: есть ли хоть один свой гекс, с которого
            // достаём хоть одну допустимую цель.
            // Проверять НАДО ТЕМ ЖЕ мерилом, каким пользуется игрок, иначе замер
            // соврёт: «цель рядом есть» — ещё не «я могу по ней попасть». Род
            // войск бьёт только две категории из четырёх, и рядом может стоять
            // ровно то, чего он не пробивает. Поэтому canAttack, а не соседство.
            boolean hadShot = false;
            for (String h : srcSet) {
                for (String n : s.field.neighbors(h)) {
                    if (validTarget(n, attackerSeat, restrictTargetOwner)
                            && canAttack(attackerSeat, h, n)) {
                        hadShot = true;
                        break;
                    }
                }
                if (hadShot) {
                    break;
                }
            }
            dry(attackerSeat, null, null, hadShot
                ? "сам отказался бить, ХОТЯ МОГ"
                : "сам отказался бить (бить было нечем)");
            return false;
        }
        String source = (String) src.payload();

        // Шаг 2: выбрать один смежный гекс-цель с допустимой целью.
        //
        // ДОСТАЁТ ЛИ ВООБЩЕ. Соседство по полю — ещё не значит, что до гекса можно
        // дотянуться: сторону могло закрыть чужое или нейтральное здание, и тогда
        // между гексами стена. Раньше Бой брал просто всех соседей и стрелял
        // сквозь неё — дизайнер поймал случай, где техника выстрелила через
        // нейтральную постройку, стоявшую в её же гексе (сид 770698, «сценарий 4
        // игрока 1»). Правило проходимости одно и то же для Движения и для Боя,
        // живёт в Passability. Авиация стенок не замечает — она бьёт сверху.
        // ТОЧКА ПРАВИЛ: дальность выбора цели. По умолчанию только соседний гекс;
        // карта арсенала «Целеуказание» поднимает до двух, если на гексе-источнике
        // есть своя авиация.
        int range = (int) Math.round(kelium.engine.ability.RuleQuery
            .of(s, attackerSeat, kelium.engine.ability.Hook.ATTACK_RANGE)
            .about(source).base(1).ask());
        List<String> targets = new ArrayList<>();
        for (String h : s.field.neighbors(source)) {
            if (validTarget(h, attackerSeat, restrictTargetOwner)
                    && anyCanShootAcross(attackerSeat, source, h)) {
                targets.add(h);
            }
        }
        if (range >= 2) {
            // Второй пояс: соседи соседей. Стенки на пути не проверяем — цель
            // указывает авиация сверху, а бьют по указанному гексу.
            for (String near : s.field.neighbors(source)) {
                for (String far : s.field.neighbors(near)) {
                    if (!far.equals(source) && !targets.contains(far)
                            && validTarget(far, attackerSeat, restrictTargetOwner)) {
                        targets.add(far);
                    }
                }
            }
        }
        if (targets.isEmpty()) {
            dry(attackerSeat, source, null, "рядом нет цели, до которой достаём");
            return false;
        }
        List<Choice> tgtOpts = new ArrayList<>();
        for (String h : targets) {
            tgtOpts.add(new Choice("combat_target", h, h));
        }
        Choice tgt = agent.choose(s, tgtOpts,
            Map.of("kind", "combat_target", "source", source));
        String target = (String) tgt.payload();

        // Шаги 3-5: разрешать атаки по одной.
        boolean didDamage = false;
        Set<Integer> damagedOwners = new HashSet<>();
        List<UnitToken> attackers = unitsOf(attackerSeat, source);
        Set<String> usedRows = new HashSet<>();   // "uid:row"
        boolean[] firstAttackUsed = {false};
        int killsThisBattle = 0;
        boolean neutralRazedThisBattle = false;
        boolean enemyDamagedThisBattle = false;

        while (true) {
            boolean closed = hexClosedAgainst(target, attackerSeat);
            List<Choice> options = new ArrayList<>();
            for (UnitToken u : attackers) {
                if (!Passability.canShootAcross(s, u, target)) {
                    continue;   // этому жетону цель закрыта стеной, а другому — нет
                }
                for (AttackRow ar : attackRows(attackerSeat, u)) {
                    String key = u.uid + ":" + ar.row();
                    if (usedRows.contains(key)) {
                        continue;
                    }
                    int cost = effCost(ar.ammoCost(), ar.target(), attackerSeat, target, firstAttackUsed);
                    if (!p.resources.canPay(Resource.AMMO, cost)) {
                        continue;
                    }
                    Token victim = pickVictimCategory(target, attackerSeat, ar.target(),
                        restrictTargetOwner, closed, u);
                    if (victim != null) {
                        Map<String, Object> pl = new HashMap<>();
                        pl.put("uid", u.uid);
                        pl.put("row", ar.row());
                        pl.put("ammo", cost);
                        pl.put("base_ammo", ar.ammoCost());
                        pl.put("tcat", ar.target().code);
                        options.add(new Choice("attack", pl,
                            u.type.code + "." + ar.row() + "->" + ar.target().code));
                    } else if (ar.target() == Target.BUILDINGS_TOWERS
                            && s.field.get(target).hasNeutral()
                            && restrictTargetOwner == null) {
                        Map<String, Object> pl = new HashMap<>();
                        pl.put("uid", u.uid);
                        pl.put("row", ar.row());
                        pl.put("ammo", cost);
                        pl.put("tcat", ar.target().code);
                        pl.put("neutral", Boolean.TRUE);
                        options.add(new Choice("attack", pl,
                            u.type.code + "." + ar.row() + "->raze neutral"));
                    }
                }
            }
            if (options.isEmpty()) {
                // ПОЧЕМУ ЗАЛП НЕ СОСТОЯЛСЯ. Замер 15.08.2026: 69% действий Бой не
                // дают НИ ОДНОГО попадания, и по событиям было не понять почему —
                // движок просто молча выходил. Причин ровно три, и лечатся они
                // по-разному, поэтому их надо различать: стенка (геометрия),
                // нехватка боеприпаса (экономика) и НЕСОВПАДЕНИЕ ТАБЛИЦЫ АТАК
                // (мой род войск не бьёт того, кто стоит в цели).
                if (killsThisBattle == 0 && !enemyDamagedThisBattle
                        && !neutralRazedThisBattle) {
                    emit("type", "combat_dry", "seat", attackerSeat, "source", source,
                        "target", target, "reason", dryReason(attackerSeat, attackers,
                            source, target, restrictTargetOwner, firstAttackUsed),
                        "round", s.round);
                }
                break;
            }
            options.add(new Choice("pass", null, "stop attacking"));
            Choice pick = agent.choose(s, options, Map.of("kind", "attack", "target", target));
            if (pick.payload() == null) {
                break;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> pl = (Map<String, Object>) pick.payload();
            int uid = ((Number) pl.get("uid")).intValue();
            String row = (String) pl.get("row");
            int ammo = ((Number) pl.get("ammo")).intValue();
            Target tcat = Target.fromCode((String) pl.get("tcat"));
            String key = uid + ":" + row;
            closed = hexClosedAgainst(target, attackerSeat);
            UnitToken unit = null;
            for (UnitToken u : attackers) {
                if (u.uid == uid) {
                    unit = u;
                    break;
                }
            }

            // Снос нейтральной постройки: на гексе их может быть несколько —
            // бьётся ПЕРВАЯ живая; при обнулении HP — трофеи + контейнеры
            // (награда зависит от размера), без ответки.
            if (Boolean.TRUE.equals(pl.get("neutral"))) {
                Hex nh = s.field.get(target);
                if (!nh.hasNeutral()) {
                    usedRows.add(key);
                    continue;
                }
                p.resources.pay(Resource.AMMO, ammo);
                usedRows.add(key);
                // K4: если нейтралов на гексе несколько — какой сносить, выбирает игрок
                Hex.NeutralBuilding nb;
                if (nh.neutrals.size() > 1) {
                    List<Choice> nopts = new ArrayList<>();
                    for (Hex.NeutralBuilding cand : nh.neutrals) {
                        nopts.add(new Choice("neutral_victim", cand,
                            (cand.big ? "двойное" : "одинарное") + " HP " + cand.hp));
                    }
                    Choice npick = agent.choose(s, nopts,
                        Map.of("kind", "neutral_victim", "target", target));
                    nb = (Hex.NeutralBuilding) npick.payload();
                } else {
                    nb = nh.neutrals.get(0);
                }
                nb.hp -= 1;
                if (nb.hp <= 0) {
                    nh.neutrals.remove(nb);
                    nh.freeSidesByToken(nb.uid);   // §12.3: стенки нейтрала освобождаются
                    int tro = nb.trophyReward();
                    int con = nb.containerReward();
                    Storage.addDebrisCapped(s, p, tro);
                    Storage.addContainersCapped(s, p, con, "снос нейтрала");
                    emit("type", "raze_neutral", "seat", attackerSeat, "target", target,
                        "debris", tro, "containers", con,
                        "big", Boolean.valueOf(nb.big),
                        "left", nh.neutrals.size());
                    journal().of(attackerSeat).neutralsRazed += 1;   // o22 «Зачистка»
                    neutralRazedThisBattle = true;
                } else {
                    emit("type", "damage_neutral", "seat", attackerSeat, "target", target,
                        "hpLeft", nb.hp);
                }
                continue;
            }

            // K4: жертву внутри категории выбирает ИГРОК (поимённо): важно для
            // добивания раненых и выбора, ЧЕЙ жетон бить (кто получит ответку).
            List<Token> victims = victimCandidates(target, attackerSeat, tcat,
                restrictTargetOwner, closed, unit);
            Token victim;
            if (victims.isEmpty()) {
                victim = null;
            } else if (victims.size() == 1) {
                victim = victims.get(0);
            } else {
                List<Choice> vopts = new ArrayList<>();
                for (Token t : victims) {
                    vopts.add(new Choice("combat_victim", t,
                        victimLabel(t) + " игрока " + t.owner()
                        + " (урон " + damageOf(t) + "/" + Passives.effectiveHp(s, t) + ")"));
                }
                Choice vpick = agent.choose(s, vopts,
                    Map.of("kind", "combat_victim", "target", target));
                victim = (Token) vpick.payload();
            }
            if (victim == null) {
                usedRows.add(key);
                continue;
            }
            p.resources.pay(Resource.AMMO, ammo);
            firstAttackUsed[0] = true;
            usedRows.add(key);
            // ЖЕТОН ЩИТА (эффект «щит», 17.08.2026) снимает ПЕРВОЕ попадание по
            // жетону защищённого рода и уходит. Проверяется ДО начисления урона:
            // у пехоты прочность 1, и «снять урон потом» её уже не спасает.
            if (victim instanceof UnitToken shielded) {
                PlayerState owner0 = s.player(shielded.owner());
                if (owner0.shieldedKinds.remove(shielded.type)) {
                    usedRows.add(key);
                    emit("type", "shield_absorbed", "seat", shielded.owner(),
                        "kind", shielded.type.code, "attacker", attackerSeat);
                    continue;
                }
            }
            int dmg = rs.getInt("combat_model.all_attacks_damage");
            if (victim instanceof UnitToken vt) {
                vt.damage += dmg;
            } else {
                ((BuildingToken) victim).damage += dmg;
            }
            didDamage = true;
            boolean destroyed = damageOf(victim) >= Passives.effectiveHp(s, victim);
            int owner = victim.owner();
            damagedOwners.add(owner);
            String vtype = victim instanceof UnitToken vt2 ? vt2.type.code : "building";
            journal().noteCombatHit(attackerSeat, owner, uidOf(victim), vtype, destroyed, isRetaliation);
            TurnJournal.TurnFacts af = journal().of(attackerSeat);
            enemyDamagedThisBattle = true;
            // ЧУЖИЕ ЖЕТОНЫ ПОД УРОНОМ И ДОБИТЫЕ — общий счёт хода.
            //
            // НАЙДЕНО 28.08.2026 ЗАМЕРОМ БЛИЗОСТИ. Оба поля журнала читались
            // картами и предикатами, но не заполнялись НИКЕМ: бой писал только
            // здания (enemyBuildingsDamaged) и убийства по жетонам-убийцам.
            // Из-за этого «Подранки» (двое раненых, никого добитого) держали
            // близость РОВНО НОЛЬ во всех 58 попаданиях карты в руку за 60
            // партий — условие было невыполнимо в принципе, а выглядело как
            // тупость ботов. Той же дырой болели предикаты
            // damaged_distinct_no_kills, damaged_distinct и destroyed_count.
            af.enemyTokensDamaged.add(uidOf(victim));
            // o43 «Охота на сильного» считает прогресс по РАНЕНОМУ лидеру, а не
            // только по добитому — поэтому признак ставится здесь, на уроне.
            if (owner == leadingRivalOf(attackerSeat)) {
                af.damagedLeader = true;
            }
            if (victim instanceof BuildingToken) {
                af.enemyBuildingHits += 1;   // o25 в прежней редакции
                af.enemyBuildingsDamaged.add(uidOf(victim));   // o45 «Пристрелка»
            }
            if (destroyed) {
                af.enemyTokensDestroyed += 1;
                af.minKillAmmoCost = Math.min(af.minKillAmmoCost, ammo);
                if (af.movedUids.contains(unit.uid)) {
                    af.movedAndKilledSameUnit = true;
                    af.killsByMovedUnit.merge(unit.uid, 1, Integer::sum);
                }
                // o26 «Блицкриг» 10.0: двое ОДНИМ жетоном войска — без оговорки
                // про перемещение, поэтому счёт ведётся по каждому убийце.
                af.killsByUnit.merge(unit.uid, 1, Integer::sum);
                af.killerUnitTypes.put(unit.uid, unit.type.code);
                // o21 «Первая кровь» 10.0: усиление платит за толстую цель.
                af.maxDestroyedHp = Math.max(af.maxDestroyedHp, Passives.effectiveHp(s, victim));
            }
            // ТРОФЕЙНЫЕ ОЧКИ убитого — в событие: без этого поля трофейную
            // экономику нечем мерить, а она половина смысла боя. Ценность
            // напечатана на КОНКРЕТНОМ жетоне (у пехоты четвёртый жетон стоит 2 ТО,
            // у техники и авиации — два жетона из четырёх).
            emit("type", "combat_hit", "seat", attackerSeat, "source", source, "target", target,
                "attacker", unit.type.code + "." + row, "victim_owner", owner,
                "victim", victimLabel(victim), "destroyed", destroyed, "ammo", ammo,
                "trophy", destroyed ? victim.trophyValue() : 0,
                "base_ammo", pl.getOrDefault("base_ammo", ammo));
            if (destroyed) {
                killsThisBattle++;
                destroy(victim, attackerSeat);
            }
        }
        if (killsThisBattle > journal().of(attackerSeat).maxKillsOneBattle) {
            journal().of(attackerSeat).maxKillsOneBattle = killsThisBattle;
        }
        if (neutralRazedThisBattle && enemyDamagedThisBattle) {
            // o22-усил: в ОДНОМ бою снёс нейтрала и достал жетон противника
            // (цель боя — один гекс, так что «на том же гексе» выполняется).
            journal().of(attackerSeat).razedNeutralAndHitEnemySameBattle = true;
        }
        evacuateShieldedEconomy(damagedOwners);

        // Шаг 6 / §4: ответный бой (один раз, не для самой ответки).
        if (didDamage && !isRetaliation && rs.getBool("actions.combat.retaliation_enabled", true)) {
            boolean gotRetaliated = false;
            for (int owner : clockwise(attackerSeat, damagedOwners)) {
                if (owner == attackerSeat) {
                    continue;
                }
                gotRetaliated |= runBattle(owner, agentFor(owner), true, attackerSeat);
                if (s.finished) {
                    break;
                }
            }
            // «Ответный залп» (арсенал 2.0.0): контратаковали в ответ на твой
            // Бой — 1 боеприпас. Реакция вне реестра способностей (ON_EVENT там
            // не диспетчеризуется никем) — прямая проверка, как у легаси-пассивок.
            if (gotRetaliated && Passives.hasPassive(s, attackerSeat, "ammo_on_being_retaliated")) {
                // ЧЕРЕЗ СКЛАД, А НЕ МИМО НЕГО: боеприпас — кубик в ячейке, и если
                // ячейки кончились, он не помещается. Прямое пополнение счётчика
                // переполняло склад (в проигрывателе — «занято 13 из 11»).
                int got = Storage.addAmmoCapped(s, p, 1);
                emit("type", "ability_reaction", "seat", attackerSeat,
                    "ability", "ammo_on_being_retaliated", "got_ammo", got);
            }
            // «Ответный залп» 2.3 (редакция 17.08.2026): платит не за сам факт
            // контратаки, а за ПОНЕСЁННУЮ ПОТЕРЮ — контратака должна была снять
            // твой жетон. Прежняя редакция срабатывала и тогда, когда противник
            // впустую расстрелял боеприпасы.
            if (gotRetaliated && journal().of(attackerSeat).lostOwnThisTurn > 0
                    && Passives.hasPassive(s, attackerSeat, "ammo_on_retaliation_kill")) {
                int got = Storage.addAmmoCapped(s, p, 1);
                emit("type", "ability_reaction", "seat", attackerSeat,
                    "ability", "ammo_on_retaliation_kill", "got_ammo", got);
            }
        }
        return didDamage;
    }

    /** Записать в журнал партии, почему бой ничего не дал. */
    private void dry(int seat, String source, String target, String reason) {
        emit("type", "combat_dry", "seat", seat, "source", source, "target", target,
            "reason", reason, "round", state.round);
    }

    /**
     * ПОЧЕМУ ЗАЛП НЕ СОСТОЯЛСЯ — одна из трёх причин, в порядке «что чинить».
     *
     * <p>Различать их обязательно: стенка — вопрос геометрии поля, боеприпас —
     * вопрос экономики, а несовпадение таблицы атак — вопрос самих правил боя
     * (род войск бьёт только две категории из четырёх, и в цели может не
     * оказаться ни одной из них).
     */
    private String dryReason(int attackerSeat, List<UnitToken> attackers, String source,
                             String target, Integer restrictTargetOwner,
                             boolean[] firstAttackUsed) {
        GameState s = state;
        PlayerState p = s.player(attackerSeat);
        boolean closed = hexClosedAgainst(target, attackerSeat);
        boolean anyReaches = false;
        boolean anyAffordable = false;
        for (UnitToken u : attackers) {
            if (!Passability.canShootAcross(s, u, target)) {
                continue;
            }
            anyReaches = true;
            for (AttackRow ar : attackRows(attackerSeat, u)) {
                int cost = effCost(ar.ammoCost(), ar.target(), attackerSeat, target,
                    firstAttackUsed);
                if (p.resources.canPay(Resource.AMMO, cost)) {
                    anyAffordable = true;
                }
            }
        }
        if (!anyReaches) {
            return "стенка";
        }
        if (!anyAffordable) {
            return "нет боеприпасов";
        }
        if (closed) {
            return "гекс закрыт";
        }
        return "таблица атак не бьёт эту цель";
    }

    private int effCost(int baseAmmo, Target tcat, int attackerSeat, String target,
                        boolean[] firstAttackUsed) {
        int cost = baseAmmo;
        if (!firstAttackUsed[0] && Passives.firstAttackDiscountActive(state, attackerSeat)) {
            cost -= 1;
        }
        if (Passives.antiArmorDiscountActive(state, attackerSeat)
                && (tcat == Target.VEHICLE || tcat == Target.BUILDINGS_TOWERS)) {
            cost -= 1;
        }
        cost += Passives.defenderAmmoSurcharge(state, defenderAt(target, attackerSeat), target);
        // Супер-арсенал sa7 «Военная машина» (редакция 17.08.2026): было
        // «все атаки −1 БП» (imba, безусловная скидка на весь бой), дизайнер
        // попросил заменить на «+1 боеприпас, годный ТОЛЬКО внутри действия
        // Движения и ТОЛЬКО внутри действия Боя, не копится, не складывается
        // на склад». Половина «Бой» — та же точка, что и у первой бесплатной
        // атаки (firstAttackUsed): −1 БП ровно на ОДНУ, первую атаку боя, не
        // суммируясь с собой между боями за ход. Половина «Движение» пока
        // НЕИСПОЛЬЗУЕМА этим движком: у Движения сейчас нет ни одной операции,
        // берущей плату боеприпасами (см. MOVEMENT_JUMP_OVER — бесплатный
        // прыжок через тайл зарождения, без цены) — если такая цена появится,
        // сюда нужно добавить симметричную скидку.
        if (!firstAttackUsed[0]
                && Passives.superArsenalPassive(state, attackerSeat, "free_ammo_for_move_and_combat")) {
            cost = Math.max(1, cost - 1);
        }
        // ТОЧКА ПРАВИЛ: цена атаки в боеприпасах. База — печатная строка со всеми
        // легаси-скидками и наценкой защитника; карта арсенала правит поверх.
        cost = kelium.engine.ability.RuleQuery
            .of(state, attackerSeat, kelium.engine.ability.Hook.ATTACK_AMMO_COST)
            .about(tcat).base(cost).ask();
        // C6: единый пол — платная строка не может стать бесплатной, сколько бы
        // скидок ни сложилось (наценка защитника уже учтена выше).
        return baseAmmo > 0 ? Math.max(1, cost) : Math.max(0, cost);
    }

    /**
     * Есть ли с гекса source хоть ОДНА реально оплачиваемая атака по гексу
     * target — ровно та же логика, что и в бою (строки, стоимость, категория
     * жертвы, закрытый гекс, скрытые юниты, нейтралы). Для ботов: гарантия,
     * что выбранный бой не будет пустым.
     */
    public boolean canAttack(int attackerSeat, String source, String target) {
        PlayerState p = state.player(attackerSeat);
        boolean closed = hexClosedAgainst(target, attackerSeat);
        for (UnitToken u : unitsOf(attackerSeat, source)) {
            for (AttackRow ar : attackRows(attackerSeat, u)) {
                int cost = effCost(ar.ammoCost(), ar.target(), attackerSeat, target,
                    new boolean[]{true});
                if (!p.resources.canPay(Resource.AMMO, cost)) {
                    continue;
                }
                if (pickVictimCategory(target, attackerSeat, ar.target(), null, closed, u) != null) {
                    return true;
                }
                if (ar.target() == Target.BUILDINGS_TOWERS
                        && state.field.get(target).hasNeutral()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Ценность реально атакуемых целей вокруг источника: сумма по соседям, где
     * {@link #canAttack} истинен (ЦУ ценнее). 0 = бой отсюда будет пустым.
     */
    public double attackableValue(int attackerSeat, String source) {
        double v = 0;
        for (String nb : state.field.neighbors(source)) {
            if (!validTarget(nb, attackerSeat, null) || !canAttack(attackerSeat, source, nb)) {
                continue;
            }
            v += 1.0;
            for (PlayerState pl : state.players) {
                if (pl.seat == attackerSeat) {
                    continue;
                }
                for (BuildingToken b : pl.buildingsOnField()) {
                    if (nb.equals(b.hexId) && b.type == kelium.core.BuildingType.COMMAND_CENTER) {
                        v += 2.0;
                    }
                }
            }
        }
        return v;
    }

    private boolean validTarget(String hx, int attackerSeat, Integer restrictOwner) {
        // Гекс с нейтральной постройкой — легальная цель (снос по HP);
        // при ответном бое (restrictOwner) нейтралы не цель.
        if (restrictOwner == null && state.field.get(hx).hasNeutral()) {
            return true;
        }
        for (Token t : allTokensOn(hx)) {
            if (restrictOwner != null && t.owner() != restrictOwner) {
                continue;
            }
            if (t.owner() != attackerSeat) {
                return true;
            }
        }
        return false;
    }

    private int defenderAt(String hexId, int attackerSeat) {
        for (PlayerState pl : state.players) {
            if (pl.seat == attackerSeat) {
                continue;
            }
            for (UnitToken t : pl.unitsOnField()) {
                if (hexId.equals(t.hexId)) {
                    return pl.seat;
                }
            }
            for (BuildingToken t : pl.buildingsOnField()) {
                if (hexId.equals(t.hexId)) {
                    return pl.seat;
                }
            }
        }
        return attackerSeat;
    }

    /**
     * K3: энергия уничтоженного ИСТОЧНИКА — его кубики (по принадлежности)
     * снимаются со всех потребителей владельца и исчезают вместе с источником.
     */
    private void removeSourceEnergy(int ownerSeat, int sourceUid) {
        for (BuildingToken b : state.player(ownerSeat).buildingsOnField()) {
            b.stripEnergyOf(sourceUid);
        }
    }

    /**
     * K3 (§3.2): кубики уничтоженного ПОТРЕБИТЕЛЯ возвращаются каждый на СВОЙ
     * источник (простаивать); источника нет на поле — кубик исчезает.
     */
    private void returnConsumerEnergy(BuildingToken victim) {
        PlayerState owner = state.player(victim.owner);
        for (Map.Entry<Integer, Integer> e : victim.energyBySource.entrySet()) {
            for (BuildingToken src : owner.buildingsOnField()) {
                if (src.uid == e.getKey()) {
                    src.energyIdle += e.getValue();
                    break;
                }
            }
        }
        victim.energyBySource.clear();
        victim.energyPlaced = 0;
        victim.energyIdle = 0;
    }

    /**
     * ВОЙСКО ВНУТРИ ЗДАНИЯ НЕ АТАКУЕМО, пока здание живо: сперва надо снести
     * здание. Состояние ЯВНОЕ — {@link UnitToken#insideBuildingUid}.
     *
     * <p>Раньше укрытие ВЫЧИСЛЯЛОСЬ по совпадению «войско стоит на гексе своего
     * здания подходящего рода», и из-за этого неуязвимыми становились ВСЕ войска у
     * своих зданий (казарма превращалась в крепость), а также вышка на гексе ЦУ —
     * при прямом запрете правила «вышки спрятаться не могут нигде». По уточнению
     * дизайнера укрытие — тактический приём: внутри здания стоит РОВНО ОДНО войско,
     * и такое здание у игрока только одно.
     */
    private boolean unitHidden(UnitToken unit) {
        if (!unit.inside()) {
            return false;
        }
        for (BuildingToken b : state.player(unit.owner).buildings) {
            if (b.uid == unit.insideBuildingUid) {
                // Здание снесено — укрытия больше нет (жетон уже выселен, но
                // проверка остаётся на случай рассинхронизации).
                return b.alive() && b.hexId != null;
            }
        }
        return false;
    }

    /**
     * ВЫСЕЛИТЬ войско из снесённого здания: укрытие держалось на здании, и с его
     * падением жетон становится обычной целью на своём гексе.
     */
    /**
     * «АВАРИЙНЫЕ ЩИТЫ» (арсенал 2.3): добытчик или энергостанция под щитом
     * пережила попадание — и СРАЗУ ПОСЛЕ БОЯ уходит владельцу в запас.
     *
     * <p>Карта не спасает здание, а меняет ФОРМУ его потери. Без щита такое
     * здание сносится с одного удара, идёт атакующему в трофеи и приносит ему
     * очки; со щитом атакующий тратит боеприпас и не получает ничего, а
     * владелец теряет постройку и место на поле. Это и есть та формулировка,
     * которую просил дизайнер вместо прежнего безусловного «+1 всем зданиям».
     *
     * <p>Проверяется СРАЗУ ПОСЛЕ БОЯ, а не в Возврат: иначе раненое здание
     * доживало бы до конца раунда и успевало отработать, то есть щит был бы
     * чистой прибавкой.
     */
    private void evacuateShieldedEconomy(java.util.Set<Integer> owners) {
        for (int owner : owners) {
            if (owner < 0 || owner >= state.numPlayers()) {
                continue;
            }
            // РАНЕНАЯ ПЕХОТА УХОДИТ В ЗАПАС (арсенал 5.0): карта даёт пехоте
            // пережить удар, но не остаться на месте. Проверяется здесь же, где
            // и щит экономики: событие одно - конец боя.
            if (Passives.hasPassive(state, owner, "infantry_hp2_returns_on_damage")) {
                PlayerState ip = state.player(owner);
                for (UnitToken u : new java.util.ArrayList<>(ip.unitsOnField())) {
                    if (u.type != UnitType.INFANTRY || u.damage <= 0) {
                        continue;
                    }
                    u.hexId = null;
                    u.resetDamage();
                    emit("type", "ability_reaction", "seat", owner,
                        "ability", "infantry_hp2_returns_on_damage",
                        "returned_unit", u.type.code);
                }
            }
            if (!Passives.hasPassive(state, owner, "economy_plus1_hp_returns_on_damage")) {
                continue;
            }
            PlayerState pl = state.player(owner);
            for (BuildingToken b : new java.util.ArrayList<>(pl.buildingsOnField())) {
                boolean economy = b.type == BuildingType.MINER
                    || b.type == BuildingType.POWER_PLANT;
                // ПЕЧАТНАЯ прочность 1 — щит покрывает только их; здание с
                // прочностью 2 и выше держит удар само и никуда не уходит.
                if (!economy || b.damage <= 0 || state.tokenStats.buildingHp(b.type, b.level) > 1) {
                    continue;
                }
                Actions.returnOwnBuildingToReserve(state, pl, b);
                emit("type", "ability_reaction", "seat", owner,
                    "ability", "economy_plus1_hp_returns_on_damage",
                    "returned_building", b.type.code);
            }
        }
    }

    private void evictFromBuilding(BuildingToken destroyed) {
        for (UnitToken u : state.player(destroyed.owner).units) {
            if (u.inside() && u.insideBuildingUid == destroyed.uid) {
                u.insideBuildingUid = null;
            }
        }
    }

    /**
     * Вернуть допустимую жертву категории tcat на гексе, или null. Соблюдает
     * правило закрытого гекса (наземка при закрытости бьёт только здания/вышки;
     * авиация игнорирует) и правило спрятанного юнита.
     */
    private Token pickVictimCategory(String hexId, int attackerSeat, Target tcat,
                                     Integer restrictOwner, boolean closed, UnitToken unit) {
        List<Token> candidates =
            victimCandidates(hexId, attackerSeat, tcat, restrictOwner, closed, unit);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * K4: все допустимые жертвы категории tcat на гексе (закрытый гекс,
     * спрятанные юниты и владелец-ограничение учтены). Детерминированный
     * порядок: здания вперёд, затем по uid — но КОГО бить, выбирает игрок
     * (см. вызов с kind = "combat_victim").
     */
    private List<Token> victimCandidates(String hexId, int attackerSeat, Target tcat,
                                         Integer restrictOwner, boolean closed, UnitToken unit) {
        boolean isAircraft = unit.type == UnitType.AIRCRAFT;
        if (closed && !isAircraft && tcat != Target.BUILDINGS_TOWERS) {
            return List.of();
        }
        List<Token> candidates = new ArrayList<>();
        for (Token t : allTokensOn(hexId)) {
            if (t.owner() == attackerSeat) {
                continue;
            }
            // ТОЧКА ПРАВИЛ: жетоны владельца могут быть ЗАЩИЩЕНЫ на этом гексе
            // (карта арсенала «Крыло прикрытия»: пока жива своя авиация, её
            // соседей по гексу атаковать нельзя). Здания под защиту не попадают:
            // авиация прикрывает войска, а не бетон.
            if (!(t instanceof kelium.core.BuildingToken)
                    && kelium.engine.ability.RuleQuery
                        .of(state, t.owner(), kelium.engine.ability.Hook.ATTACK_PROTECT_HEX)
                        .about(hexId).base(0).ask() >= 1.0) {
                continue;
            }
            if (restrictOwner != null && t.owner() != restrictOwner) {
                continue;
            }
            if (targetCategory(t) != tcat) {
                continue;
            }
            if (t instanceof UnitToken ut && unitHidden(ut)) {
                continue;
            }
            candidates.add(t);
        }
        candidates.sort((a, b) -> {
            int ra = a instanceof BuildingToken ? 0 : 1;
            int rb = b instanceof BuildingToken ? 0 : 1;
            if (ra != rb) {
                return Integer.compare(ra, rb);
            }
            return Integer.compare(uidOf(a), uidOf(b));
        });
        return candidates;
    }

    private static int uidOf(Token t) {
        return t instanceof UnitToken u ? u.uid : ((BuildingToken) t).uid;
    }

    private static int damageOf(Token t) {
        return t instanceof UnitToken u ? u.damage : ((BuildingToken) t).damage;
    }

    /**
     * ОДИН УДАР по конкретной жертве, вне очереди обычного боя — публичный шов
     * для карт, бьющих не по правилам «Боя» (супер-вышка sa4 «Цитадель»:
     * залп по X разным соседним гексам за один СПЕЦ). Та же механика, что и
     * внутри обычной атаки: жетон щита снимает попадание и уходит, иначе
     * начисляется {@code combat_model.all_attacks_damage} урона, и жертва
     * уничтожается (см. {@link #destroy}), если урон дошёл до эффективной
     * прочности. Не списывает боеприпас и не проверяет дальность/строку атаки —
     * это забота вызывающей способности.
     *
     * @return true, если жертва этим ударом уничтожена
     */
    public boolean hit(Token victim, int attackerSeat) {
        GameState s = state;
        if (victim instanceof UnitToken shielded) {
            PlayerState owner0 = s.player(shielded.owner());
            if (owner0.shieldedKinds.remove(shielded.type)) {
                emit("type", "shield_absorbed", "seat", shielded.owner(),
                    "kind", shielded.type.code, "attacker", attackerSeat);
                return false;
            }
        }
        int dmg = rs.getInt("combat_model.all_attacks_damage");
        if (victim instanceof UnitToken vt) {
            vt.damage += dmg;
        } else {
            ((BuildingToken) victim).damage += dmg;
        }
        boolean destroyed = damageOf(victim) >= Passives.effectiveHp(s, victim);
        if (destroyed) {
            destroy(victim, attackerSeat);
        }
        return destroyed;
    }

    /**
     * Уничтожить жертву: ЦУ обрабатывается отдельно, прочие переходят на
     * трофейное поле убийцы; владельцу зданий выдаётся компенсация контейнерами,
     * уничтожение энергостанции снимает выданную ею энергию; применяются пассивы
     * арсенала (доп. боеприпас/ТО за убийство).
     */
    /**
     * Кто ведёт по очкам среди СОПЕРНИКОВ этого места.
     *
     * <p>Нужно карте «Охота на лидера»: усиление платит за снос ЗДАНИЯ ведущего,
     * и определять ведущего надо в момент удара, а не потом — к концу хода счёт
     * уже другой.
     */
    private int leadingRivalOf(int seat) {
        int best = -1;
        int bestVp = Integer.MIN_VALUE;
        for (PlayerState p : state.players) {
            if (p.seat == seat) {
                continue;
            }
            int vp = Scoring.scorePlayer(state, p.seat).getOrDefault("total", 0);
            if (vp > bestVp) {
                bestVp = vp;
                best = p.seat;
            }
        }
        return best;
    }

    public void destroy(Token victim, int attackerSeat) {
        GameState s = state;
        PlayerState attacker = s.player(attackerSeat);

        if (victim instanceof BuildingToken bt && bt.type == BuildingType.COMMAND_CENTER) {
            destroyCu(bt, attackerSeat);
            return;
        }

        // КОНТЕЙНЕР ЗА ПОТЕРЮ ЗДАНИЯ (арсенал 5.0, «если твоё здание уничтожили
        // — получи 1 контейнер из запаса»). Утешение владельцу, а не награда
        // убийце: карта смотрит на потерю, а не на виновника, поэтому считается
        // любое уничтожение своего здания — в том числе своим же ядерным
        // ударом. ЦУ сюда не попадает: у него своя развязка (destroyCu).
        if (victim instanceof BuildingToken потеря
                && Passives.hasPassive(s, потеря.owner, "container_on_own_building_lost")) {
            PlayerState хозяин = s.player(потеря.owner);
            int взято = Storage.addContainersCapped(s, хозяин, 1);
            if (взято > 0) {
                emit("type", "ability_reaction", "seat", потеря.owner,
                    "ability", "container_on_own_building_lost",
                    "building", потеря.type.code, "containers", взято);
            }
        }

        // Нарастающий счётчик уничтожений (в отличие от трофеев, он не сбрасывается
        // в Возврат). Сам по себе очков не даёт — только если это включено в опыте
        // ключом economy.vp_per_kill.
        attacker.killsTotal++;
        // СУПЕРОРУЖИЕ СНЕСЛИ: жетон возвращается на свою карту, счётчик запуска
        // встаёт, пока владелец не наймёт его заново.
        if (victim instanceof UnitToken vu && SuperWeapon.isWeapon(s, vu)) {
            SuperWeapon.onWeaponDestroyed(s, s.player(vu.owner()));
        }

        // ЖУРНАЛ ХОДА: что именно снесено. Раньше здесь заполнялся только счётчик
        // уничтожений, а поле destroyedTypes было ОБЪЯВЛЕНО И НИКОГДА НЕ
        // ЗАПОЛНЯЛОСЬ — то есть любое задание, опирающееся на «что снесли», не
        // могло выполниться в принципе. Ровно та беда, из-за которой каталог
        // выглядел рабочим и не работал.
        {
            TurnJournal.TurnFacts j = s.journal.of(attackerSeat);
            j.destroyedOwners.add(victim.owner());
            // ПОТЕРИ ЖЕРТВЫ — в её собственный журнал. Поле читалось (условие
            // «без потерь» и ответный бой), но не заполнялось ничем: та же дыра,
            // что была у destroyedTypes.
            s.journal.of(victim.owner()).lostOwnThisTurn += 1;
            if (victim instanceof UnitToken u) {
                j.destroyedTypes.add(u.type.code);
            } else if (victim instanceof BuildingToken b) {
                j.destroyedTypes.add(b.type.name().toLowerCase(java.util.Locale.ROOT));
                // ТОЛЬКО ДОБЫТЧИК (правка 17.08.2026). Энергостанция энергию
                // производит, а не потребляет: «запитанная энергостанция» — не
                // состояние игры, и усиление o42 на ней срабатывало бы неверно.
                if (b.type == BuildingType.MINER && b.energyPlaced > 0) {
                    j.destroyedPoweredEconomy = true;
                }
                if (victim.owner() == leadingRivalOf(attackerSeat)) {
                    j.destroyedLeaderBuilding = true;
                }
            }
        }

        if (victim instanceof UnitToken ut) {
            ut.trophyValue = scaledUnitTrophy(attackerSeat, ut.type);
            ut.setHexId(null);   // уходит в трофеи — и из здания, если был внутри
            ut.damage = 0;
            ut.capturedBy = attackerSeat;
            attacker.trophySpace.add(ut);
        } else {
            BuildingToken bt = (BuildingToken) victim;
            // Войско, стоявшее ВНУТРИ этого здания, теряет укрытие и остаётся на
            // гексе обычной целью: место освободилось вместе со зданием.
            evictFromBuilding(bt);
            if (s.field.hexes.containsKey(bt.hexId)) {
                s.field.get(bt.hexId).freeSidesByToken(bt.uid);
            }
            bt.hexId = null;
            bt.damage = 0;
            bt.capturedBy = attackerSeat;
            // K3: сначала вернуть кубики жетона на их источники (в трофеи
            // жетон уезжает БЕЗ энергии), затем — если это источник — снять
            // его кубики со всех потребителей.
            returnConsumerEnergy(bt);
            if (bt.type == BuildingType.POWER_PLANT) {
                removeSourceEnergy(bt.owner, bt.uid);
            }
            attacker.trophySpace.add(bt);
            int comp = buildingCompensation(bt);
            if (comp > 0) {
                Storage.addContainersCapped(s, s.player(bt.owner), comp,
                    "компенсация за снесённое здание");
            }
        }

        int bonusAmmo = Passives.ammoOnKill(s, attackerSeat);
        if (bonusAmmo > 0) {
            // Тоже через склад: у боеприпаса есть предел, и награда за снос его
            // не отменяет — лишний кубик просто некуда положить.
            Storage.addAmmoCapped(s, attacker, bonusAmmo);
        }
        int bonusTrophy = Passives.bonusTrophyOnKill(s, attackerSeat);
        if (bonusTrophy > 0) {
            Storage.addDebrisCapped(s, attacker, bonusTrophy);
        }
    }

    /**
     * Уничтожение ЦУ — каноническое правило (решение дизайнера 2026-08-11,
     * ревизия §12.1): ЦУ не даёт ТО и не идёт в трофейное пространство. Награда —
     * жетон уничтожения ЦУ ВЛАДЕЛЬЦА (перманентные 3 ПО), если он ещё у него.
     * ПРОВЕРКА ПОБЕДЫ в момент любого сноса ЦУ: если у атакующего УЖЕ есть
     * чей-либо чужой жетон — мгновенная военная победа (не счётчик убийств!).
     * Жертва немедленно забирает ЦУ себе В ЗАПАС (на поле — обычной Стройкой)
     * и получает 2 контейнера (напечатаны на обороте жетона).
     */
    private void destroyCu(BuildingToken cu, int attackerSeat) {
        GameState s = state;
        PlayerState attacker = s.player(attackerSeat);
        PlayerState owner = s.player(cu.owner);

        attacker.cuKills += 1;   // телеметрия, к победе отношения не имеет
        // СКОЛЬКО ЖЕТОНОВ РАЗРУШЕНИЯ НУЖНО ДЛЯ ВОЕННОЙ ПОБЕДЫ. Правило — ДВА
        // (СВОД), это и значение по умолчанию: при 2 условие ниже совпадает с
        // прежним «у меня уже был чужой жетон» во ВСЕХ случаях, включая тот, где
        // за снос жетон не достался (владелец отдал его раньше другому).
        //
        // Ключ вынесен ради замера, а не ради изменения правила. Замер 14.08.2026:
        // военная победа случается в 0.8% партий, ПЕРВЫЙ снос ЦУ приходится на
        // раунд 6.0 при длине партии 6.0 — второму сносу физически негде
        // поместиться. Отсюда вопрос, на который нельзя ответить рассуждением:
        // сколько даст порог в один жетон. Цель дизайнера — 20%.
        int need = ((Number) rs.get("command_center.cu_tokens_for_military_win", 2))
            .intValue();
        boolean heldForeignToken = attacker.cuDestructionTokens >= need - 1;
        // Супер-задания 5.0: «Трофейный обоз» и «Тень штаба» смотрят, разрушалось
        // ли ЦУ игрока ХОТЬ РАЗ за партию.
        owner.super5CuEverLost = true;
        // «Тень штаба»: глухой жетон изъят из игры навсегда — за снос этого ЦУ
        // захватчик не получает НИЧЕГО: ни оборота, ни шага к военной победе.
        if (owner.super5SealRemoved) {
            owner.ownCuTokenAvailable = false;
        }
        if (owner.ownCuTokenAvailable) {
            owner.ownCuTokenAvailable = false;
            attacker.cuDestructionTokens += 1;
            // ЖЕТОН УЕХАЛ — ЯЧЕЙКА ОТКРЫЛАСЬ. Он лежал на планшете жертвы глухим
            // модулем; теперь он у захватчика перевёрнутым, и жертва снова бьёт
            // этим родом дёшево. Так снос ЦУ перестаёт быть чистой потерей: чем
            // сильнее тебя бьют, тем свободнее твой планшет.
            owner.redPlacements.entrySet().removeIf(e ->
                Boolean.TRUE.equals(e.getValue().get("blocks")));
        }

        // ЦУ — в запас владельца: с поля долой, урон снят, энергия обнулена
        evictFromBuilding(cu);   // войско внутри ЦУ теряет укрытие
        if (cu.hexId != null && s.field.hexes.containsKey(cu.hexId)) {
            s.field.get(cu.hexId).freeSidesByToken(cu.uid);
        }
        cu.hexId = null;
        cu.damage = 0;
        // ЦУ — источник И потребитель: его кубики исчезают с ним (K3)
        removeSourceEnergy(cu.owner, cu.uid);
        cu.energyBySource.clear();
        cu.energyPlaced = 0;
        cu.energyIdle = 0;

        Storage.addContainersCapped(s, owner,
            rs.getInt("command_center.owner_compensation_containers"),
            "компенсация за снесённое ЦУ");

        // СНОС ЦУ — СОБЫТИЕ. До 24.08.2026 его не было вовсе: самое громкое
        // событие партии нигде не отмечалось, и вопрос «как быстро владелец
        // ставит ЦУ обратно» нельзя было даже измерить. Жетоны разрушения тут же,
        // потому что военная победа считается ими, а не числом сносов.
        emit("type", "cu_destroyed", "seat", cu.owner, "by", attackerSeat,
            "round", s.round, "tokens_of_attacker", attacker.cuDestructionTokens,
            "need", need);

        if (heldForeignToken
                && rs.getBool("command_center.military_win_on_second_cu_kill", true)) {
            s.finished = true;
            s.winner = attackerSeat;
            s.winCondition = "military";
        }
    }

    private int scaledUnitTrophy(int attackerSeat, UnitType unitType) {
        List<Integer> vals = state.tokenStats.unitTrophyList(unitType);
        int already = 0;
        for (Token t : state.player(attackerSeat).trophySpace) {
            if (t instanceof UnitToken u && u.type == unitType) {
                already++;
            }
        }
        return vals.get(Math.min(already, vals.size() - 1));
    }

    /**
     * Компенсация контейнерами владельцу уничтоженного здания — из ruleset
     * `building_compensation_containers` (решение дизайнера §12.2): казарма/
     * завод/авиабаза = 1, добытчики и ЭС №1/№3 = 1, №2/№4 = 0, ЦУ = 2 (отдельно).
     */
    private int buildingCompensation(BuildingToken b) {
        String key = switch (b.type) {
            case BARRACKS -> "barracks";
            case FACTORY -> "factory";
            case AIRBASE -> "airbase";
            case MINER -> "miner_by_level";
            case POWER_PLANT -> "power_station_by_level";
            default -> null;
        };
        if (key == null) {
            return 0;
        }
        Object v = rs.get("building_compensation_containers." + key, 1);
        if (v instanceof List<?> lst) {
            int lv = b.level != null ? b.level : 1;
            int idx = Math.max(0, Math.min(lst.size() - 1, lv - 1));
            return ((Number) lst.get(idx)).intValue();
        }
        return ((Number) v).intValue();
    }

    private List<Integer> clockwise(int startSeat, Set<Integer> owners) {
        int n = state.numPlayers();
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int o = (startSeat + i) % n;
            if (owners.contains(o)) {
                out.add(o);
            }
        }
        return out;
    }

    private Agent agentFor(int seat) {
        return agents.get(seat);
    }
}
