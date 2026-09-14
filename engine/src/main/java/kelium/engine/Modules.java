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
 * ВЫБОРОМ из двух. Урон 1, цена жетона (1 БП). ЗОЛОТО (по печати жетона,
 * 14.09.2026): ОДНА атака за 1 боеприпас наносит по 1 урону одному жетону КАЖДОГО
 * из двух типов на выбранном гексе; модуль даёт +1 ПО.
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

    /**
     * ЧИСЛА СИНЕГО ЖЕТОНА ПО ЕГО ИМЕНИ — одно место на движок и ботов.
     *
     * <p>Комплект C1-C4 вшит в код, а жетоны из мешка («Модули 2.0») описаны
     * данными, и у них другие имена. Пока лазили прямо в {@link #BLUE_MODULES},
     * бот на вытянутом C30-1 получал null и ронял партию: жетон был, чисел у
     * него не было. Спрашивать надо ЗДЕСЬ — сперва комплект, потом набор.
     *
     * @return {@code null}, если такого жетона нет нигде
     */
    public static Map<String, Object> blueSpec(GameState s, String id) {
        Map<String, Object> spec = BLUE_MODULES.get(id);
        if (spec != null) {
            return spec;
        }
        var tok = ModuleSets.token(ModuleSets.of(s), id);
        if (tok == null || !tok.blue()) {
            return null;
        }
        return Map.of("ammo", tok.ammo(), "units", tok.units(),
            "gild", tok.gild() == null ? "units" : tok.gild());
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
        // ПОЛУЧЕННЫЙ ЖЕТОН СРАЗУ ЛОЖИТСЯ НА СВОБОДНУЮ ЯЧЕЙКУ (решение дизайнера
        // 14.09.2026). Лежащие жетоны при этом НЕ трогаются: смены модулей в
        // раунде нет, а перекладка — платный обмен планшета науки. Прежде
        // движок при каждом получении раскладывал все модули заново — так
        // нельзя. Свободной ячейки нет — жетон остаётся в запасе игрока и
        // ставится позже обменом «перемещение модуля» (пока дизайнер не решил
        // иначе).
        if (s.agents != null && p.seat < s.agents.size() && s.agents.get(p.seat) != null) {
            placeNewToken(s, p, s.agents.get(p.seat), id, red);
        }
        return id;
    }

    /**
     * ПОЛОЖИТЬ ТОЛЬКО ЧТО ВЫТЯНУТЫЙ ЖЕТОН на одну из свободных ячеек его цвета.
     * Куда — выбирает игрок. Свободных ячеек нет — жетон лежит в запасе
     * ({@link PlayerState#redTokens}/{@link PlayerState#blueTokens} без записи в
     * раскладке).
     *
     * @return ячейка (род войск или здание), куда лёг жетон, либо null
     */
    public static Object placeNewToken(GameState s, PlayerState p, Agent agent,
                                       String id, boolean red) {
        List<Choice> opts = new ArrayList<>();
        if (red) {
            Map<String, Object> placement = redPlacementFor(s, id);
            if (placement == null) {
                return null;
            }
            for (UnitType t : UnitType.values()) {
                if (redSlotsFor(p, t) > 0 && !p.redPlacements.containsKey(t)) {
                    opts.add(new Choice("red_slot", Map.of("module", id, "unit", t),
                        id + "->" + t.code));
                }
            }
            if (opts.isEmpty()) {
                // СВОБОДНОЙ ЯЧЕЙКИ НЕТ (решение дизайнера 14.09.2026): можно
                // заменить новым жетоном любой свой лежащий (кроме глухого),
                // строго на его место; снят золотой — новый кладётся золотым.
                for (Map.Entry<UnitType, Map<String, Object>> e : p.redPlacements.entrySet()) {
                    if (!Boolean.TRUE.equals(e.getValue().get("blocks"))) {
                        opts.add(new Choice("red_replace", Map.of("module", id, "unit", e.getKey()),
                            id + " вместо " + e.getValue().get("id") + " на " + e.getKey().code));
                    }
                }
                if (opts.isEmpty()) {
                    return null;
                }
                opts.add(new Choice("pass", null, "оставить в запасе"));
                Choice ch = agent.choose(s, opts, Map.of("kind", "module_replace_red"));
                if (!(ch.payload() instanceof Map<?, ?> pick)) {
                    return null;
                }
                UnitType slot = (UnitType) pick.get("unit");
                Map<String, Object> снятый = p.redPlacements.get(slot);
                placement.put("gold", Boolean.TRUE.equals(снятый.get("gold")));
                p.redTokens.remove(String.valueOf(снятый.get("id")));   // снятый уходит из игры
                p.redPlacements.put(slot, placement);
                p.goldModules = countGold(p);
                return slot;
            }
            Choice ch = agent.choose(s, opts, Map.of("kind", "module_place_red"));
            if (!(ch.payload() instanceof Map<?, ?> pick)) {
                return null;
            }
            UnitType slot = (UnitType) pick.get("unit");
            p.redPlacements.put(slot, placement);
            return slot;
        }
        Map<String, Object> placement = bluePlacementFor(s, id);
        if (placement == null) {
            return null;
        }
        for (BuildingType b : MIL_BUILDINGS) {
            if (!p.bluePlacements.containsKey(b)) {
                opts.add(new Choice("blue_slot", Map.of("module", id, "building", b),
                    id + "->" + b.code));
            }
        }
        if (opts.isEmpty()) {
            for (Map.Entry<BuildingType, Map<String, Object>> e : p.bluePlacements.entrySet()) {
                opts.add(new Choice("blue_replace", Map.of("module", id, "building", e.getKey()),
                    id + " вместо " + e.getValue().get("id") + " на " + e.getKey().code));
            }
            if (opts.isEmpty()) {
                return null;
            }
            opts.add(new Choice("pass", null, "оставить в запасе"));
            Choice ch = agent.choose(s, opts, Map.of("kind", "module_replace_blue"));
            if (!(ch.payload() instanceof Map<?, ?> pick)) {
                return null;
            }
            BuildingType slot = (BuildingType) pick.get("building");
            Map<String, Object> снятый = p.bluePlacements.get(slot);
            placement.put("gold", Boolean.TRUE.equals(снятый.get("gold")));
            p.blueTokens.remove(String.valueOf(снятый.get("id")));
            p.bluePlacements.put(slot, placement);
            p.goldModules = countGold(p);
            return slot;
        }
        Choice ch = agent.choose(s, opts, Map.of("kind", "module_place_blue"));
        if (!(ch.payload() instanceof Map<?, ?> pick)) {
            return null;
        }
        BuildingType slot = (BuildingType) pick.get("building");
        p.bluePlacements.put(slot, placement);
        return slot;
    }

    /** Запись раскладки красного жетона по его id, обычной стороной; null — жетон неизвестен. */
    public static Map<String, Object> redPlacementFor(GameState s, String id) {
        Map<String, Object> placement = new HashMap<>();
        placement.put("id", id);
        placement.put("gold", false);
        Target[] pair = RED_MODULES.get(id);
        if (pair != null) {
            placement.put("targets", new String[]{pair[0].code, pair[1].code});
            return placement;
        }
        var tok = ModuleSets.token(ModuleSets.of(s), id);
        if (tok != null && !tok.targets().isEmpty()) {
            placement.put("targets", tok.targets().toArray(new String[0]));
            placement.put("ammo", tok.ammo());
            return placement;
        }
        if (tok != null && tok.stat() != null) {
            placement.put("stat", tok.stat());
            placement.put("plus", tok.plus());
            return placement;
        }
        return null;
    }

    /** Запись раскладки синего жетона по его id, обычной стороной; null — жетон неизвестен. */
    public static Map<String, Object> bluePlacementFor(GameState s, String id) {
        Map<String, Object> spec = blueSpec(s, id);
        if (spec == null) {
            return null;
        }
        Map<String, Object> placement = new HashMap<>();
        placement.put("id", id);
        placement.put("ammo", spec.get("ammo"));
        placement.put("units", spec.get("units"));
        placement.put("gild", spec.get("gild"));
        placement.put("gold", false);
        return placement;
    }

    /** Жетоны игрока этого цвета, которые вытянуты, но не лежат на планшете. */
    public static List<String> unplacedTokens(PlayerState p, boolean red) {
        List<String> out = new ArrayList<>(red ? p.redTokens : p.blueTokens);
        var placed = red ? p.redPlacements.values() : p.bluePlacements.values();
        for (Map<String, Object> pl : placed) {
            out.remove(String.valueOf(pl.get("id")));
        }
        return out;
    }

    /** Сколько золотых модулей лежит на планшете игрока (глухой жетон не в счёт). */
    public static int countGold(PlayerState p) {
        int n = 0;
        for (Map<String, Object> pl : p.redPlacements.values()) {
            if (Boolean.TRUE.equals(pl.get("gold")) && !Boolean.TRUE.equals(pl.get("blocks"))) {
                n++;
            }
        }
        for (Map<String, Object> pl : p.bluePlacements.values()) {
            if (Boolean.TRUE.equals(pl.get("gold"))) {
                n++;
            }
        }
        return n;
    }

    /** Есть ли у игрока лежащий модуль, который ещё можно позолотить. */
    public static boolean canGild(PlayerState p) {
        for (Map<String, Object> pl : p.redPlacements.values()) {
            if (!Boolean.TRUE.equals(pl.get("gold")) && !Boolean.TRUE.equals(pl.get("blocks"))) {
                return true;
            }
        }
        for (Map<String, Object> pl : p.bluePlacements.values()) {
            if (!Boolean.TRUE.equals(pl.get("gold"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * ПОЗОЛОТА: перевернуть ОДИН лежащий модуль золотой стороной. Какой —
     * выбирает игрок. Золото — свойство конкретного жетона: оно лежит в его
     * записи раскладки и переезжает вместе с ним. Прежний общий счётчик
     * {@link PlayerState#goldModules} держится равным числу золотых записей,
     * потому что его читают подсчёт очков, отчёты и боты.
     *
     * @return true, если позолотили
     */
    public static boolean gildOne(GameState s, PlayerState p, Agent agent) {
        List<Choice> opts = new ArrayList<>();
        for (Map.Entry<UnitType, Map<String, Object>> e : p.redPlacements.entrySet()) {
            Map<String, Object> pl = e.getValue();
            if (!Boolean.TRUE.equals(pl.get("gold")) && !Boolean.TRUE.equals(pl.get("blocks"))) {
                opts.add(new Choice("gild_red", e.getKey(), pl.get("id") + " на " + e.getKey().code));
            }
        }
        for (Map.Entry<BuildingType, Map<String, Object>> e : p.bluePlacements.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue().get("gold"))) {
                opts.add(new Choice("gild_blue", e.getKey(),
                    e.getValue().get("id") + " на " + e.getKey().code));
            }
        }
        if (opts.isEmpty()) {
            return false;
        }
        Choice ch = opts.size() == 1 || agent == null ? opts.get(0)
            : agent.choose(s, opts, Map.of("kind", "module_gild_pick"));
        if (ch == null || ch.payload() == null) {
            ch = opts.get(0);
        }
        if ("gild_red".equals(ch.kind())) {
            p.redPlacements.get((UnitType) ch.payload()).put("gold", true);
        } else {
            p.bluePlacements.get((BuildingType) ch.payload()).put("gold", true);
        }
        p.goldModules = countGold(p);
        return true;
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
     * Смена модулей: переложить свои модули по слотам типов (эффектом карты
     * или при получении нового жетона). Каждая смена перекладывает всё заново. По одному модулю на
     * слот. Управляется агентом через Choices.
     */
    public static void moduleSwap(GameState s, int seat, Agent agent,
                                  Consumer<Map<String, Object>> emit) {
        PlayerState p = s.player(seat);
        // ГЛУХОЙ ЖЕТОН УНИЧТОЖЕНИЯ ЦУ ПЕРЕЖИВАЕТ ПЕРЕКЛАДКУ.
        //
        // НАЙДЕНО ЗАМЕРОМ 25.08.2026: у 470 игроков из 600 жетона к концу партии
        // не было вовсе, хотя ЦУ снесли только в 0.27 партии. Причина здесь:
        // смена стирает раскладку целиком и собирает её заново из ВЫТЯНУТЫХ
        // жетонов, а глухой в мешке не лежит и в руке не числится — значит он
        // молча пропадал.
        //
        // Он остаётся НА ТОМ ЖЕ РОДЕ: бесплатная перекладка была бы щедрее
        // правила. По правилу его двигают за плату — обменом на
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
        // ЗОЛОТО ПРИНАДЛЕЖИТ ЖЕТОНУ, а не месту и не порядковому номеру:
        // запоминаем, какие именно жетоны лежали золотой стороной, и кладём их
        // золотыми же. Прежде золото «прилипало» к первым разложенным и при
        // перекладке перетекало с красного на синий.
        java.util.Set<String> золотые = new java.util.HashSet<>();
        for (Map<String, Object> pl : p.redPlacements.values()) {
            if (Boolean.TRUE.equals(pl.get("gold")) && !Boolean.TRUE.equals(pl.get("blocks"))) {
                золотые.add(String.valueOf(pl.get("id")));
            }
        }
        for (Map<String, Object> pl : p.bluePlacements.values()) {
            if (Boolean.TRUE.equals(pl.get("gold"))) {
                золотые.add(String.valueOf(pl.get("id")));
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
            placement.put("gold", золотые.contains(mod));
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
        // СИНИЕ РАСКЛАДЫВАЮТСЯ ТАК ЖЕ, КАК КРАСНЫЕ: с мешками игрок ставит ТЕ
        // жетоны, что вытянул. Прежде здесь всегда перебирались C1-C4 из кода,
        // а вытянутое (p.blueTokens) не смотрели вовсе: вытянув три одинаковых
        // жетона, игрок всё равно ставил четыре разных. У красных это было
        // сделано сразу, у синих — забыто.
        List<String> blueNames = p.blueTokens.isEmpty()
            ? java.util.Arrays.asList(BLUE_NAMES) : new ArrayList<>(p.blueTokens);
        int blueAvail = p.blueTokens.isEmpty()
            ? Math.min(p.blueModules, BLUE_NAMES.length) : p.blueTokens.size();
        java.util.Set<String> usedBlue = new java.util.HashSet<>();
        for (int i = 0; i < blueAvail; i++) {
            List<Choice> bopts = new ArrayList<>();
            for (String mod : blueNames) {
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
            Map<String, Object> spec = blueSpec(s, mod);
            if (spec == null) {
                continue;                 // неизвестный жетон — не раскладываем
            }
            Map<String, Object> placement = new HashMap<>();
            placement.put("id", mod);
            placement.put("ammo", spec.get("ammo"));
            placement.put("units", spec.get("units"));
            placement.put("gild", spec.get("gild"));
            placement.put("gold", золотые.contains(mod));
            usedBlue.add(mod);
            p.bluePlacements.put(slot, placement);
        }
        p.goldModules = countGold(p);

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
        // ЖЕТОН ИЗ ЗАПАСА: вытянут, когда свободной ячейки не было, — за тот же
        // трофей его можно положить на освободившуюся ячейку.
        for (String id : unplacedTokens(p, true)) {
            picks.add(new Choice("place_red", id, id + " из запаса"));
        }
        for (String id : unplacedTokens(p, false)) {
            picks.add(new Choice("place_blue", id, id + " из запаса"));
        }
        if (picks.isEmpty()) {
            return;
        }
        picks.add(new Choice("pass", null, "cancel"));
        Choice pick = agent.choose(s, picks, Map.of("kind", "module_move_pick"));
        if (pick.payload() == null) {
            return;
        }
        if ("place_red".equals(pick.kind())) {
            placeNewToken(s, p, agent, (String) pick.payload(), true);
            return;
        }
        if ("place_blue".equals(pick.kind())) {
            placeNewToken(s, p, agent, (String) pick.payload(), false);
            return;
        }
        if ("move_red".equals(pick.kind())) {
            UnitType from = (UnitType) pick.payload();
            Map<String, Object> placement = p.redPlacements.remove(from);
            List<Choice> slots = new ArrayList<>();
            // ЗАНЯТАЯ ЯЧЕЙКА ТОЖЕ ГОДИТСЯ: два своих жетона меняются местами, и
            // это одна смена модуля (решение дизайнера 14.09.2026).
            for (UnitType t : UnitType.values()) {
                if (t != from && redSlotsFor(p, t) > 0) {
                    Map<String, Object> там = p.redPlacements.get(t);
                    slots.add(new Choice("red_slot", Map.of("module",
                        placement.get("id"), "unit", t), placement.get("id") + "->" + t.code
                        + (там == null ? "" : " (обмен с " + там.get("id") + ")")));
                }
            }
            if (slots.isEmpty()) {
                p.redPlacements.put(from, placement);   // переложить некуда — остаётся
                return;
            }
            Choice slot = agent.choose(s, slots, Map.of("kind", "module_place_red"));
            Map<String, Object> sp = (Map<String, Object>) slot.payload();
            UnitType to = (UnitType) sp.get("unit");
            Map<String, Object> другой = p.redPlacements.remove(to);
            if (другой != null) {
                p.redPlacements.put(from, другой);
            }
            p.redPlacements.put(to, placement);
        } else {
            BuildingType from = (BuildingType) pick.payload();
            Map<String, Object> placement = p.bluePlacements.remove(from);
            List<Choice> slots = new ArrayList<>();
            for (BuildingType b : MIL_BUILDINGS) {
                if (b != from) {
                    Map<String, Object> там = p.bluePlacements.get(b);
                    slots.add(new Choice("blue_slot", Map.of("module",
                        placement.get("id"), "building", b), placement.get("id") + "->" + b.code
                        + (там == null ? "" : " (обмен с " + там.get("id") + ")")));
                }
            }
            if (slots.isEmpty()) {
                p.bluePlacements.put(from, placement);
                return;
            }
            Choice slot = agent.choose(s, slots, Map.of("kind", "module_place_blue"));
            Map<String, Object> sp = (Map<String, Object>) slot.payload();
            BuildingType to = (BuildingType) sp.get("building");
            Map<String, Object> другой = p.bluePlacements.remove(to);
            if (другой != null) {
                p.bluePlacements.put(from, другой);
            }
            p.bluePlacements.put(to, placement);
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
