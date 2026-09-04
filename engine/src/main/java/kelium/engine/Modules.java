package kelium.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Target;
import kelium.core.UnitType;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.dataio.Ctx;

/**
 * Система модулей — модули сборки (синие) и атаки (красные). У игрока комплект
 * из 8 УНИКАЛЬНЫХ жетонов (4+4); выдаются треками/наградами, игрок сам выбирает,
 * какие из своих задействовать.
 *
 * <p>КРАСНЫЕ (атака) M1–M4: накладываются на строку ВТР рода войск, заменяя цель
 * ВЫБОРОМ из двух (каждая из 4 целей встречается ровно дважды). Урон 1, цена
 * печатная (2 БП). ЗОЛОТО: стреляют ОБЕ цели (каждая оплачивается отдельно, бьют
 * по разным жетонам), модуль даёт +1 ПО.
 *
 * <p>СИНИЕ (сборка) C1–C4 (спека дизайнера 2026-08-10, картинки): жетон накрывает
 * ВСЮ зону сборки здания и задаёт СВОИ выходы «X боеприпасов ИЛИ Y войск»
 * (выбор по-прежнему ровно один за Сборку). Стрелка на лице помечает параметр,
 * который на ЗОЛОТОЙ стороне растёт на +1:
 * <pre>
 *   C1: 2 БП / 1 войско (↑войска)  -> золото 2/2
 *   C2: 2 БП (↑БП) / 1 войско      -> золото 3/1
 *   C3: 1 БП / 2 войска (↑войска)  -> золото 1/3
 *   C4: 1 БП (↑БП) / 2 войска      -> золото 2/2
 * </pre>
 */
public final class Modules {

    private Modules() {
    }

    /** Пары целей M1–M4 (каждая из 4 целей встречается ровно дважды). */
    public static final Map<String, Target[]> RED_MODULES = new HashMap<>();

    /** Синие модули C1–C4: {ammo, units, gild} (gild — какой параметр растёт золотом). */
    public static final Map<String, Map<String, Object>> BLUE_MODULES = new HashMap<>();

    /** Типы военных зданий, которые могут нести синий модуль сборки. */
    public static final BuildingType[] MIL_BUILDINGS = {
        BuildingType.BARRACKS, BuildingType.FACTORY,
        BuildingType.AIRBASE, BuildingType.COMMAND_CENTER
    };

    static {
        RED_MODULES.put("M1", new Target[]{Target.INFANTRY, Target.VEHICLE});
        RED_MODULES.put("M2", new Target[]{Target.VEHICLE, Target.AIRCRAFT});
        RED_MODULES.put("M3", new Target[]{Target.AIRCRAFT, Target.BUILDINGS_TOWERS});
        RED_MODULES.put("M4", new Target[]{Target.BUILDINGS_TOWERS, Target.INFANTRY});

        BLUE_MODULES.put("C1", Map.of("ammo", 2, "units", 1, "gild", "units"));
        BLUE_MODULES.put("C2", Map.of("ammo", 2, "units", 1, "gild", "ammo"));
        BLUE_MODULES.put("C3", Map.of("ammo", 1, "units", 2, "gild", "units"));
        BLUE_MODULES.put("C4", Map.of("ammo", 1, "units", 2, "gild", "ammo"));
    }

    private static final String[] RED_NAMES = {"M1", "M2", "M3", "M4"};
    private static final String[] BLUE_NAMES = {"C1", "C2", "C3", "C4"};

    /**
     * Сколько производит здание за одну Сборку для данного выхода
     * ({@code kind} = "unit" | "ammo"). Без модуля — печатная 1. С синим модулем —
     * числа жетона; золото добавляет +1 к помеченному стрелкой параметру.
     */
    public static int assemblyOutput(PlayerState player, BuildingType btype, String kind) {
        Map<String, Object> place = player.bluePlacements.get(btype);
        if (place == null) {
            return 1;
        }
        boolean gold = Boolean.TRUE.equals(place.get("gold"));
        String gild = String.valueOf(place.get("gild"));
        if ("ammo".equals(kind)) {
            int v = place.get("ammo") instanceof Number n ? n.intValue() : 1;
            return v + (gold && "ammo".equals(gild) ? 1 : 0);
        }
        int v = place.get("units") instanceof Number n ? n.intValue() : 1;
        return v + (gold && "units".equals(gild) ? 1 : 0);
    }

    /** Совместимость: выход по войскам (старые вызовы). */
    public static int assemblyOutput(PlayerState player, BuildingType btype) {
        return assemblyOutput(player, btype, "unit");
    }

    /** Вернуть словарь красного модуля, размещённого на этом роде войск, или null. */
    public static Map<String, Object> redModuleOn(PlayerState player, UnitType unitType) {
        return player.redPlacements.get(unitType);
    }

    /**
     * ВЫДАТЬ ИГРОКУ МОДУЛЬ («Модули 2.0», 12.08.2026).
     *
     * <p>С включёнными мешками ({@code modules.from_bag}) жетон ТЯНЕТСЯ СЛУЧАЙНО
     * из мешка своего цвета и оттуда извлекается: игрок не выбирает, какой жетон
     * взять, — он выбирает только, куда его вставить. Мешок опустел — награда не
     * выдаётся (и это нормально: поздние награды-модули ценнее ранних).
     *
     * <p>Мешки выключены — работает прежний счётчик, и игрок раскладывает любые
     * жетоны своего комплекта.
     *
     * @param colour {@code red} или {@code blue}
     * @return id вытянутого жетона, либо null (мешок пуст или мешки выключены)
     */
    public static String awardModule(GameState s, PlayerState p, String colour) {
        boolean red = "red".equals(colour);
        if (!ModuleSets.bagsEnabled(s)) {
            if (red) {
                p.redModules += 1;
            } else {
                p.blueModules += 1;
            }
            return null;
        }
        String id = ModuleSets.draw(red ? s.redBag : s.blueBag, s.rng);
        if (id == null) {
            return null;                  // мешок пуст — модулей больше нет
        }
        if (red) {
            p.redTokens.add(id);
            p.redModules += 1;
        } else {
            p.blueTokens.add(id);
            p.blueModules += 1;
        }
        return id;
    }

    /**
     * Прибавка к характеристике рода войск от ХАРАКТЕРИСТИЧЕСКОГО красного жетона
     * (набор R2: «+1 здоровье», «+1 скорость»; золотая сторона даёт оба).
     *
     * @param stat {@code hp} или {@code speed}
     */
    public static int statBonus(PlayerState p, UnitType unitType, String stat) {
        Map<String, Object> place = p.redPlacements.get(unitType);
        if (place == null) {
            return 0;
        }
        boolean gold = Boolean.TRUE.equals(place.get("gold"));
        String own = place.get("stat") == null ? null : String.valueOf(place.get("stat"));
        if (own == null) {
            return 0;
        }
        int plus = place.get("plus") instanceof Number n ? n.intValue() : 1;
        if (own.equals(stat)) {
            return plus;
        }
        // золотая сторона характеристического жетона даёт И здоровье, И скорость
        return gold && ("hp".equals(stat) || "speed".equals(stat)) ? 1 : 0;
    }

    /**
     * Бесплатный этап смены модулей (Обновление): переложить свои модули по
     * слотам типов. Каждая смена перекладывает всё заново. По одному модулю на
     * слот. Управляется агентом через Choices.
     */
    public static void moduleSwap(GameState s, int seat, Agent agent,
                                  Consumer<Map<String, Object>> emit) {
        PlayerState p = s.player(seat);
        // ГЛУХОЙ ЖЕТОН УНИЧТОЖЕНИЯ ЦУ ПЕРЕЖИВАЕТ ПЕРЕКЛАДКУ.
        //
        // НАЙДЕНО ЗАМЕРОМ 25.08.2026: у 470 игроков из 600 жетона к концу партии
        // не было вовсе, хотя ЦУ снесли только в 0.27 партии. Причина здесь:
        // Обновление стирает раскладку целиком и собирает её заново из
        // ВЫТЯНУТЫХ жетонов, а глухой в мешке не лежит и в руке не числится —
        // значит каждый раунд он молча пропадал.
        //
        // Он остаётся НА ТОМ ЖЕ РОДЕ: бесплатная перекладка каждое Обновление
        // была бы щедрее правила. По правилу его двигают за плату — обменом на
        // планшете науки за трофей или утилем карты задания (moveOneModule).
        // Заодно занятый им род не предлагается под другие модули: он и так
        // занят, как любая занятая ячейка.
        Map.Entry<UnitType, Map<String, Object>> глухой = null;
        for (Map.Entry<UnitType, Map<String, Object>> e : p.redPlacements.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue().get("blocks"))) {
                глухой = e;
                break;
            }
        }
        p.redPlacements.clear();
        p.bluePlacements.clear();
        if (глухой != null) {
            p.redPlacements.put(глухой.getKey(), глухой.getValue());
        }

        // ЖЕТОН-ЗАГЛУШКА КЛАДЁТСЯ ПЕРВЫМ (правило дизайнера 27.08.2026): он
        // такой же красный жетон, как остальные, и ячейку занимает
        // ПО-НАСТОЯЩЕМУ — значит место под ним должно быть занято ДО того, как
        // игрок разложит рабочие модули, иначе они успеют занять его сами.
        moveSealToken(s, p, agent, emit);

        // Красные: у игрока комплект из 4 УНИКАЛЬНЫХ жетонов (М1-М4); выдано
        // (доступно) redModules штук — игрок сам выбирает, КАКИЕ из четырёх
        // задействовать и на какие рода войск положить.
        // МОДУЛИ 2.0 (12.08.2026): с мешками игрок раскладывает ТЕ жетоны, что
        // вытянул, а не любые из комплекта. Мешки выключены — прежнее поведение.
        List<String> redNames = p.redTokens.isEmpty()
            ? java.util.Arrays.asList(RED_NAMES) : new ArrayList<>(p.redTokens);
        int redAvail = p.redTokens.isEmpty()
            ? Math.min(p.redModules, RED_NAMES.length) : p.redTokens.size();
        int goldRed = Math.min(p.goldModules, Math.max(p.redModules, redAvail));
        java.util.Set<String> usedModules = new java.util.HashSet<>();
        for (int i = 0; i < redAvail; i++) {
            List<Choice> topts = new ArrayList<>();
            for (String mod : redNames) {
                if (usedModules.contains(mod)) {
                    continue;
                }
                for (UnitType t : UnitType.values()) {
                    // МЕСТА ПОД КРАСНЫЕ МОДУЛИ НАПЕЧАТАНЫ НА ПЛАНШЕТЕ (наборы «В»
                    // и «Г», 15.08.2026). До этого места были у всех родов
                    // поровну и молча: планшет не мог сказать «у авиации мест
                    // нет», и асимметрия по модулям была невозможна.
                    if (redSlotsFor(p, t) <= 0) {
                        continue;
                    }
                    if (sealSits(s, p, t)) {
                        continue;   // место занято жетоном-заглушкой
                    }
                    if (!p.redPlacements.containsKey(t)) {
                        Map<String, Object> pl = new HashMap<>();
                        pl.put("module", mod);
                        pl.put("unit", t);
                        topts.add(new Choice("red_slot", pl, mod + "->" + t.code));
                    }
                }
            }
            if (topts.isEmpty()) {
                break;
            }
            topts.add(new Choice("pass", null, "leave in reserve"));
            Choice ch = agent.choose(s, topts, Map.of("kind", "module_place_red"));
            if (ch.payload() == null) {
                continue;
            }
            Map<String, Object> pick = (Map<String, Object>) ch.payload();
            String mod = (String) pick.get("module");
            UnitType slot = (UnitType) pick.get("unit");
            Map<String, Object> placement = new HashMap<>();
            placement.put("id", mod);
            placement.put("gold", i < goldRed);
            Target[] pair = RED_MODULES.get(mod);
            if (pair != null) {
                placement.put("targets", new String[]{pair[0].code, pair[1].code});
            } else {
                // жетон из НАБОРА ДАННЫХ («Модули 2.0»): цели и цена берутся из
                // файла наборов, а характеристические жетоны (R2-1/R2-2) целей
                // не задают вовсе — они меняют HP/скорость рода войск.
                var tok = ModuleSets.token(ModuleSets.of(s), mod);
                if (tok != null && !tok.targets().isEmpty()) {
                    placement.put("targets", tok.targets().toArray(new String[0]));
                    placement.put("ammo", tok.ammo());
                } else if (tok != null && tok.stat() != null) {
                    placement.put("stat", tok.stat());
                    placement.put("plus", tok.plus());
                } else {
                    continue;             // неизвестный жетон — не раскладываем
                }
            }
            usedModules.add(mod);
            p.redPlacements.put(slot, placement);
        }

        // Синие: комплект из 4 УНИКАЛЬНЫХ жетонов C1-C4; выдано blueModules штук —
        // игрок выбирает, КАКИЕ задействовать и на какие здания положить.
        int blueAvail = Math.min(p.blueModules, BLUE_NAMES.length);
        int goldBlue = Math.max(0, p.goldModules - goldRed);
        java.util.Set<String> usedBlue = new java.util.HashSet<>();
        for (int i = 0; i < blueAvail; i++) {
            List<Choice> bopts = new ArrayList<>();
            for (String mod : BLUE_NAMES) {
                if (usedBlue.contains(mod)) {
                    continue;
                }
                for (BuildingType b : MIL_BUILDINGS) {
                    if (!p.bluePlacements.containsKey(b)) {
                        Map<String, Object> pl = new HashMap<>();
                        pl.put("module", mod);
                        pl.put("building", b);
                        bopts.add(new Choice("blue_slot", pl, mod + "->" + b.code));
                    }
                }
            }
            if (bopts.isEmpty()) {
                break;
            }
            bopts.add(new Choice("pass", null, "leave in reserve"));
            Choice ch = agent.choose(s, bopts, Map.of("kind", "module_place_blue"));
            if (ch.payload() == null) {
                continue;
            }
            Map<String, Object> pick = (Map<String, Object>) ch.payload();
            String mod = (String) pick.get("module");
            BuildingType slot = (BuildingType) pick.get("building");
            Map<String, Object> spec = BLUE_MODULES.get(mod);
            Map<String, Object> placement = new HashMap<>();
            placement.put("id", mod);
            placement.put("ammo", spec.get("ammo"));
            placement.put("units", spec.get("units"));
            placement.put("gild", spec.get("gild"));
            placement.put("gold", i < goldBlue);
            usedBlue.add(mod);
            p.bluePlacements.put(slot, placement);
        }

        // ЧТО ИМЕННО ПОСТАВЛЕНО — в событие. Без этих полей отчёт по модулям
        // читает пустоту и показывает «ни один модуль не поставлен», хотя жетоны
        // тянутся и раскладываются (обжёгся на этом 13.08.2026).
        Map<String, Object> ev = new HashMap<>();
        ev.put("type", "module_swap");
        ev.put("seat", seat);
        Map<String, Object> red = new java.util.LinkedHashMap<>();
        p.redPlacements.forEach((unit, spec) ->
            red.put(unit.code, spec == null ? "?" : String.valueOf(spec.get("id"))));
        Map<String, Object> blue = new java.util.LinkedHashMap<>();
        p.bluePlacements.forEach((building, spec) ->
            blue.put(building.code, spec == null ? "?" : String.valueOf(spec.get("id"))));
        ev.put("placed_red", red);
        ev.put("placed_blue", blue);
        ev.put("placed", red.size() + blue.size());
        emit.accept(ev);
    }

    /**
     * Вечный курс науки «1 трофей -> 1 перемещение модуля»: снять ОДИН уже
     * размещённый модуль (красный или синий) и поставить его на другой слот
     * своего типа. Управляется агентом.
     */
    @SuppressWarnings("unchecked")
    public static void moveOneModule(GameState s, int seat, Agent agent) {
        PlayerState p = s.player(seat);
        List<Choice> picks = new ArrayList<>();
        for (Map.Entry<UnitType, Map<String, Object>> e : p.redPlacements.entrySet()) {
            picks.add(new Choice("move_red", e.getKey(),
                e.getValue().get("id") + " с " + e.getKey().code));
        }
        for (Map.Entry<BuildingType, Map<String, Object>> e : p.bluePlacements.entrySet()) {
            picks.add(new Choice("move_blue", e.getKey(),
                e.getValue().get("id") + " с " + e.getKey().code));
        }
        if (picks.isEmpty()) {
            return;
        }
        picks.add(new Choice("pass", null, "cancel"));
        Choice pick = agent.choose(s, picks, Map.of("kind", "module_move_pick"));
        if (pick.payload() == null) {
            return;
        }
        if ("move_red".equals(pick.kind())) {
            UnitType from = (UnitType) pick.payload();
            Map<String, Object> placement = p.redPlacements.remove(from);
            List<Choice> slots = new ArrayList<>();
            for (UnitType t : UnitType.values()) {
                if (!p.redPlacements.containsKey(t)) {
                    slots.add(new Choice("red_slot", Map.of("module",
                        placement.get("id"), "unit", t), placement.get("id") + "->" + t.code));
                }
            }
            Choice slot = agent.choose(s, slots, Map.of("kind", "module_place_red"));
            Map<String, Object> sp = (Map<String, Object>) slot.payload();
            p.redPlacements.put((UnitType) sp.get("unit"), placement);
        } else {
            BuildingType from = (BuildingType) pick.payload();
            Map<String, Object> placement = p.bluePlacements.remove(from);
            List<Choice> slots = new ArrayList<>();
            for (BuildingType b : MIL_BUILDINGS) {
                if (!p.bluePlacements.containsKey(b)) {
                    slots.add(new Choice("blue_slot", Map.of("module",
                        placement.get("id"), "building", b), placement.get("id") + "->" + b.code));
                }
            }
            Choice slot = agent.choose(s, slots, Map.of("kind", "module_place_blue"));
            Map<String, Object> sp = (Map<String, Object>) slot.payload();
            p.bluePlacements.put((BuildingType) sp.get("building"), placement);
        }
    }

    /**
     * СКОЛЬКО МЕСТ ПОД КРАСНЫЙ МОДУЛЬ у этого рода на планшете игрока.
     *
     * <p>Читается из стороны планшета войск ({@code red_slots}). Если сторона
     * ничего не говорит — считаем по одному месту на род, как было до наборов
     * «В» и «Г»: старые планшеты не должны менять поведение.
     */
    /**
     * ГЛУХОЙ ЖЕТОН НА ЭТОЙ ЯЧЕЙКЕ? Пока он у игрока, ячейка занята, и рабочий
     * красный модуль туда не положить. Уехал к захватчику за снесённое ЦУ —
     * место освободилось.
     *
     * <p>Жетон живёт в {@link PlayerState#redPlacements} рядом с обычными
     * модулями и помечен флагом {@code blocks}: он и есть такой же красный
     * жетон, только ничего не открывает.
     */
    public static boolean sealSits(GameState s, PlayerState p, UnitType type) {
        Map<String, Object> м = p.redPlacements.get(type);
        return sealActive(s, p) && м != null && Boolean.TRUE.equals(м.get("blocks"));
    }

    /** Лежит ли у игрока глухой жетон (и включено ли правило вообще). */
    public static boolean sealActive(GameState s, PlayerState p) {
        if (!p.ownCuTokenAvailable
                || !Ctx.rules(s).getBool("command_center.destruction_token_seals_cell", false)) {
            return false;
        }
        return sealUnit(p) != null;
    }

    /** Род войск, чью ячейку сейчас закрывает глухой жетон, или null. */
    private static UnitType sealUnit(PlayerState p) {
        for (Map.Entry<UnitType, Map<String, Object>> e : p.redPlacements.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue().get("blocks"))) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * ПЕРЕЛОЖИТЬ ГЛУХОЙ ЖЕТОН (правило дизайнера 27.08.2026).
     *
     * <p>Он — обычный красный жетон и переносится по тем же правилам: в
     * Обновление игрок раскладывает красные заново, и его тоже. Род войск,
     * нарисованный на лице, значил ТОЛЬКО стартовое положение при подготовке —
     * после того как жетон лёг, картинка не значит ничего, и держать его на
     * своём роде игрок не обязан.
     */
    private static void moveSealToken(GameState s, PlayerState p, Agent agent,
                                      Consumer<Map<String, Object>> emit) {
        if (!sealActive(s, p)) {
            return;
        }
        UnitType был = sealUnit(p);
        List<Choice> opts = new ArrayList<>();
        for (UnitType t : UnitType.values()) {
            if (redSlotsFor(p, t) > 0) {
                opts.add(new Choice("seal_slot", t, "глухой жетон -> " + t.code));
            }
        }
        if (opts.size() <= 1) {
            return;             // класть некуда, кроме как обратно
        }
        Choice ch = agent.choose(s, opts, Map.of("kind", "seal_move"));
        if (!(ch.payload() instanceof UnitType picked) || picked == был) {
            return;
        }
        Map<String, Object> жетон = p.redPlacements.remove(был);
        p.redPlacements.put(picked, жетон);
        if (emit != null) {
            Map<String, Object> ev = new HashMap<>();
            ev.put("type", "seal_move");
            ev.put("seat", p.seat);
            ev.put("from", был.code);
            ev.put("unit", picked.code);
            emit.accept(ev);
        }
    }

    public static int redSlotsFor(PlayerState p, UnitType type) {
        Object raw = p.board == null || p.board.troop == null ? null
            : p.board.troop.raw.get("red_slots");
        if (!(raw instanceof Map<?, ?> slots)) {
            return 1;
        }
        Object v = slots.get(type.name().toLowerCase(java.util.Locale.ROOT));
        return v instanceof Number n ? n.intValue() : 0;
    }
}
