package kelium.engine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.core.TurnJournal;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.dataio.Ctx;
import kelium.rules.Ruleset;

/**
 * Конкретные реализации 8 действий.
 *
 * <p>Политика вертикального среза: экономические действия (добыча, сборка,
 * стройка, смена энергии, маркет, наука) реализованы достаточно, чтобы гонять
 * полную партию; движение работает по смежным гексам, бой ведёт корректный учёт
 * наценок, но разрешение боя — заглушка (см. {@link CombatResolver}). Все числа
 * берутся из ruleset; этот файл — только процедура.
 */
public final class Actions {

    private Actions() {
    }

    /**
     * Синтетический uid источника энергии «карта арсенала» («Полевой генератор»).
     * Отдельный от {@code EnergySwapAction.STORAGE_SOURCE_UID}, иначе снятие
     * кубиков одного источника уносило бы и кубики другого. Виден снаружи, потому
     * что содержание карты в Обновление ({@code GameEngine.payArsenalUpkeep})
     * снимает её кубики при неуплате.
     */
    public static final int ARSENAL_CARD_SOURCE_UID = -3;

    /**
     * Ключи приза шага 1 по порядку прихода на шаг: ячейка 1 · 2 · 3.
     * Ячеек у шага 1 три, поэтому и ключей три — сколько призов реально
     * прописано, решает свод, а не код.
     */
    public static final String[] PRIZE_RANK_KEYS = {"first", "second", "third"};

    /** Какой род войск собирает каждое военное здание. */
    /** Какой род войск производит каждое военное здание. Публично: это правило
     * читают и карты арсенала (найм вне приказа Разработка). */
    public static final Map<BuildingType, UnitType> ASSEMBLY_UNIT = new EnumMap<>(BuildingType.class);


    static {
        ASSEMBLY_UNIT.put(BuildingType.BARRACKS, UnitType.INFANTRY);
        ASSEMBLY_UNIT.put(BuildingType.FACTORY, UnitType.VEHICLE);
        ASSEMBLY_UNIT.put(BuildingType.AIRBASE, UnitType.AIRCRAFT);
        ASSEMBLY_UNIT.put(BuildingType.COMMAND_CENTER, UnitType.TOWER);

    }

    /** Сколько смежных сторон гекса занимает здание (см. {@link Placement}). */
    public static int buildingFootprint(BuildingType btype) {
        return Placement.footprint(btype);
    }

    /** Гексы, где игроку доступна Стройка (см. {@link Placement}). */
    public static List<String> buildableHexes(GameState state, int seat) {
        return Placement.buildableHexes(state, seat);
    }

    /** Следующий свободный номер жетона (см. {@link Placement}). */
    static int nextUid(GameState state) {
        return Placement.nextUid(state);
    }


    /** Наземная нагрузка гекса войсками (см. {@link Placement}). */
    static int[] groundLoad(GameState state, String hexId, int excludeUid) {
        return Placement.groundLoad(state, hexId, excludeUid);
    }

    /**
     * ВЫСЕЛИТЬ войско из здания, которое сносят или переносят: укрытие держится на
     * здании, а не на гексе. Жетон остаётся там, где стоял, и становится обычной
     * целью — место на гексе как раз освободилось вместе со зданием.
     */
    static void evictFromBuilding(PlayerState player, BuildingToken b) {
        for (UnitToken u : player.units) {
            if (u.inside() && u.insideBuildingUid == b.uid) {
                u.insideBuildingUid = null;
            }
        }
    }

    /**
     * Вернуть СВОЁ здание {@code b} в резерв (hexId=null) — БЕЗ выбора игрока
     * при закрытии ячеек склада (см. {@link #returnOwnBuildingToReserve(GameState,
     * PlayerState, BuildingToken, boolean)}). Оставлен для мест, где возврат не
     * является собственным действием игрока в его ход.
     */
    public static void returnOwnBuildingToReserve(GameState state, PlayerState player,
                                                   BuildingToken b) {
        returnOwnBuildingToReserve(state, player, b, false);
    }

    /**
     * Вернуть СВОЁ здание {@code b} в резерв (hexId=null): войско внутри теряет
     * укрытие, стороны гекса освобождаются, энергия корректно снимается (чужие
     * кубики возвращаются их источникам, симметрично уничтожению в бою), урон
     * сбрасывается. В отличие от уничтожения в бою — здание НЕ уходит в трофеи
     * противника, оно остаётся жетоном владельца. Публичный шов нужен карте
     * арсенала «Аварийные щиты» (принудительный возврат раненых зданий в
     * Возврат) — та же операция, что и снос своего здания Стройкой.
     *
     * @param ownTurnChoice возврат случился ВНУТРИ хода {@code player}, его
     *     собственным действием (снос Стройкой, оплата карты) — тогда при
     *     закрытии ячеек склада игрок сам выбирает, что сгорит (см.
     *     {@link Storage#evictOnBuildingReturn}). {@code false} — возврат
     *     случился не в его ход (Возврат конца раунда, чужое действие), выбора
     *     нет, горит фиксированным порядком.
     */
    public static void returnOwnBuildingToReserve(GameState state, PlayerState player,
                                                   BuildingToken b, boolean ownTurnChoice) {
        String hex = b.hexId;
        for (BuildingToken c : player.buildingsOnField()) {
            if (c.uid != b.uid) {
                c.stripEnergyOf(b.uid);
            }
        }
        for (Map.Entry<Integer, Integer> e : b.energyBySource.entrySet()) {
            for (BuildingToken src : player.buildingsOnField()) {
                if (src.uid == e.getKey() && src.uid != b.uid) {
                    src.energyIdle += e.getValue();
                    break;
                }
            }
        }
        b.energyBySource.clear();
        b.energyPlaced = 0;
        b.energyIdle = 0;
        evictFromBuilding(player, b);
        if (hex != null) {
            state.field.get(hex).freeSidesByToken(b.uid);
        }
        b.hexId = null;
        b.resetDamage();
        // Правило 4 (уточнение 2026-08-15): здание, вернувшись в резерв, ОБЯЗАНО
        // лечь на планшет хранилища на своё место — это ЗАКРЫВАЕТ ранее открытые
        // ячейки склада (добытчик/энергостанция), и любые кубики, набранные,
        // пока здание было на поле или в трофеях у другого игрока, сгорают без
        // права игрока их переставить. Не влияет на прочие типы зданий.
        if (b.type == BuildingType.MINER || b.type == BuildingType.POWER_PLANT) {
            Storage.evictOnBuildingReturn(state, player, ownTurnChoice);
        }
    }

    private static TurnJournal journal(GameState s) {
        return s.journal;
    }

    /** Доступно ли здание для действия (см. {@link Power}). */
    static boolean effectivelyPowered(GameState state, PlayerState player, BuildingToken b,
                                      Agent agent) {
        return Power.usableForAction(state, player, b, agent, null);
    }

    /** То же с накопителем телеметрии (см. {@link Power}). */
    static boolean effectivelyPowered(GameState state, PlayerState player, BuildingToken b,
                                      Agent agent, int[] paid) {
        return Power.usableForAction(state, player, b, agent, paid);
    }



    // ======================================================================
    //  Реестр действий по имени.
    // ======================================================================
    /** Фабрика действия по имени: создаёт экземпляр, привязанный к состоянию. */
    public static Action create(String name, GameState state) {
        return switch (name) {
            case "assembly" -> new AssemblyAction(state);
            case "mining" -> new MiningAction(state);
            case "build" -> new BuildAction(state);
            case "energy_swap" -> new EnergySwapAction(state);
            case "movement" -> new MovementAction(state);
            case "combat" -> new CombatAction(state);
            case "market" -> new MarketAction(state);
            case "science" -> new ScienceAction(state);
            default -> throw new IllegalArgumentException("неизвестное действие: " + name);
        };
    }

    /** Имена всех 8 действий (порядок как в Python ACTION_CLASSES). */
    public static final List<String> ALL_NAMES = List.of(
        "assembly", "mining", "build", "energy_swap",
        "movement", "combat", "market", "science");


    // ======================================================================
    //  DEVELOPMENT: assembly, mining
    // ======================================================================

    /**
     * Жила, до которой ДОБЫТЧИК {@code miner} РЕАЛЬНО ДОТЯГИВАЕТСЯ, либо null.
     * Версия {@link MiningAction#adjacentGridWithKelium} без привязки к
     * экземпляру действия — нужна карте арсенала «Келемиевый бак» (СПЕЦ-добыча
     * одним зданием без розыгрыша полного действия Добыча).
     */
    public static String minerAdjacentGridWithKelium(GameState state, BuildingToken miner) {
        Hex self = state.field.get(miner.hexId);
        if (self == null) {
            return null;
        }
        if (self.spawnTile != null && self.spawnTile.kelium > 0) {
            return miner.hexId;
        }
        for (int side = 0; side < 6; side++) {
            if (self.sideOwner[side] == null || self.sideOwner[side] != miner.uid) {
                continue;
            }
            String nbId = self.neighborBySide[side];
            if (nbId == null) {
                continue;
            }
            Hex nb = state.field.get(nbId);
            if (nb != null && nb.spawnTile != null && nb.spawnTile.kelium > 0) {
                return nbId;
            }
        }
        return null;
    }

    /**
     * Извлечь келемий ОДНИМ добытчиком {@code b} из жилы {@code grid} (та же
     * логика, что и внутри полного действия Добыча: выработка, переворот тайла,
     * трофейные очки, снятие жетона на обороте). Контейнерная ветка сюда не
     * входит — карта, которая этим пользуется («Келемиевый бак»), берёт только
     * келемий. Возвращает добытое количество.
     */
    public static int mineFromMiner(GameState state, PlayerState player, BuildingToken b,
                                    String grid) {
        int bonusK = player.techSteps.getOrDefault("middle", 0) >= 3 ? 1 : 0;
        int yieldK = state.tokenStats.minerYield(b.level) + bonusK;
        return mineFlatFromTile(state, player, grid, yieldK);
    }

    /**
     * Добыть ФИКСИРОВАННОЕ число келемия с тайла зарождения на гексе
     * {@code grid} — та же выработка/переворот тайла, что и у добытчика
     * ({@link #mineFromMiner}), но без здания и без формулы уровня. Публичный
     * шов нужен супер-технике sa2 «Раздор» (СПЕЦ: 1 келемий с примыкающего
     * тайла, «добытчик для этого не нужен»).
     */
    public static int mineFlatFromTile(GameState state, PlayerState player, String grid,
                                       int amount) {
        Ruleset rs = Ctx.rules(state);
        Hex gh = state.field.get(grid);
        kelium.core.SpawnTile tile = gh.spawnTile;
        int want = Math.min(amount, tile.kelium);
        int added = Storage.addKeliumCapped(state, player, want);
        tile.kelium -= added;
        if (added > 0 && tile.kelium <= 0) {
            int bonus = Passives.extractionFlipBonusTrophy(state, player.seat);
            int trophy;
            if (!tile.flipped) {
                tile.flip();
                trophy = rs.getInt(tile.isStart
                    ? "economy.spawn_face_trophy_small"
                    : "economy.spawn_face_trophy_big") + bonus;
                if (tile.isStart) {
                    player.flippedStartTiles += 1;
                } else {
                    player.flippedNormalTiles += 1;
                }
            } else {
                trophy = rs.getInt(tile.isStart
                    ? "economy.spawn_back_trophy_small"
                    : "economy.spawn_back_trophy_big") + bonus;
                player.claimedSpawnTiles += 1;
                if (tile.isStart) {
                    player.claimedStartTiles += 1;
                } else {
                    player.claimedNormalTiles += 1;
                }
                if (!tile.popStack()) {
                    gh.spawnTile = null;
                }
                if (!tile.isStart) {
                    journal(state).of(player.seat).spawnTileClaimedNonStart = true;
                }
            }
            Storage.addDebrisCapped(state, player, trophy);
            journal(state).of(player.seat).tookLastKeliumFromGrid = true;
            if (!tile.isStart) {
                journal(state).of(player.seat).lastKeliumNonStart = true;
            }
        }
        return added;
    }

    /** Действие Добыча (приказ Разработка): извлечение келемия/контейнеров. */
    static final class MiningAction extends Action {
        MiningAction(GameState state) {
            super(state);
        }

        @Override public String name() { return "mining"; }
        @Override public Order order() { return Order.DEVELOPMENT; }
        @Override public boolean implemented() { return true; }

        @Override
        @SuppressWarnings("unchecked")
        public ActionResult perform(PlayerState player, TurnContext ctx, Agent agent) {
            GameState s = state;
            int gainedK = 0;
            int gainedC = 0;
            int[] paid = {0, 0};
            boolean both = Passives.minerTakesKeliumAndContainer(s, player.seat);
            for (BuildingToken b : player.buildingsOnField()) {
                if (b.type != BuildingType.MINER) {
                    continue;
                }
                // Разработка: незапитанный добытчик можно включить монетами
                // на ЭТО действие (см. effectivelyPowered).
                //
                // НАДБАВКА УТИЛЯ «ещё одним добытчиком» (21.08.2026): столько
                // добытчиков работают в эту Добычу БЕЗ энергии. Все запитанные
                // работают и так — «ещё один» может значить только этот, иначе
                // прибавка была бы пустой.
                if (!effectivelyPowered(s, player, b, agent, paid)) {
                    if (ctx.freeMinerMoves > 0) {
                        ctx.freeMinerMoves--;
                    } else {
                        continue;
                    }
                }
                String grid = adjacentGridWithKelium(b);
                // G1 (свод §2: «Пропустить здание можно») — добытчик можно
                // пропустить; выбор по каждому добытчику отдельно.
                boolean takeContainerOnly;
                List<Choice> opts = new ArrayList<>();
                if (grid != null) {
                    opts.add(new Choice("mine", "kelium", "extract kelium"));
                }
                // ВЕТКА КОНТЕЙНЕРА (правило дизайнера 12.08.2026): добытчик
                // берёт контейнер, только если тот НАРИСОВАН И ОТКРЫТ на его
                // гексе либо на примыкающем — так же, как он примыкает к тайлу
                // зарождения. «Взять из запаса просто так» больше нельзя.
                String contHex = PrintedContainers.miningBranchOn(s)
                    ? PrintedContainers.minableContainerHex(s, b) : null;
                if (contHex != null) {
                    opts.add(new Choice("mine", "container", "take container @" + contHex));
                }
                if (opts.isEmpty()) {
                    continue;   // ни келемия рядом, ни открытого контейнера
                }
                opts.add(new Choice("pass", null, "пропустить добытчик"));
                Choice pick = agent.choose(s, opts, Map.of("kind", "mine"));
                if (pick.payload() == null) {
                    continue;   // добытчик пропущен
                }
                takeContainerOnly = "container".equals(pick.payload());
                if (!takeContainerOnly) {
                    gainedK += mineFromMiner(s, player, b, grid);
                    if (both) {
                        gainedC += Storage.addContainersCapped(s, player, 1,
                            "Добыча: выработал тайл");
                    }
                } else {
                    // Добытчик забирает ОТКРЫТЫЙ ПЕЧАТНЫЙ контейнер со своего гекса
                    // или с примыкающего (правило 12.08.2026, ветка выбрана выше —
                    // contHex). Ячейку надо ОТМЕТИТЬ собранной: замер 13.08.2026
                    // показал, что Добыча даёт ПОЛОВИНУ всех контейнеров в игре
                    // (39 за партию из 83), потому что выдавала карту в обход
                    // печатных ячеек — та же ячейка кормила добытчика каждый раунд.
                    PrintedContainers.markMined(s, contHex);
                    // РАЗМЕТКА ВЫБОРА (вопрос дизайнера 13.08.2026): контейнер и келемий —
                    // это ЛИБО-ЛИБО. Надо знать, берут ли контейнер ВМЕСТО келемия,
                    // или просто рядом нет живой грядки и выбора не было.
                    gainedC += Storage.addContainersCapped(s, player, 1,
                        grid != null ? "Добыча: контейнер ВМЕСТО келемия"
                                     : "Добыча: контейнер (келемия рядом не было)");
                    journal(s).of(player.seat).minerTookContainer = true;
                    if (b.level != null) {
                        journal(s).of(player.seat).minerContainerLevels.add(b.level);
                    }
                }
            }
            ctx.actionsPlayed.add(name());
            Map<String, Object> tel = new HashMap<>();
            tel.put("kelium", gainedK);
            tel.put("power_coins", paid[0]);
            tel.put("power_offers", paid[1]);
            tel.put("containers", gainedC);
            return ActionResult.ok("mined " + gainedK + " kelium, " + gainedC + " containers", tel);
        }

        /**
         * Жила, до которой ДОБЫТЧИК РЕАЛЬНО ДОТЯГИВАЕТСЯ, либо null.
         *
         * <p>Примыкание считается по СТЕНКЕ САМОГО ДОБЫТЧИКА: он занимает одну
         * ячейку гекса, и тайл зарождения должен лежать за ИМЕННО ЭТОЙ стенкой.
         * Раньше здесь перебирались все шесть соседей гекса — добытчик копал
         * жилу, к которой стоит спиной (баг, найден дизайнером 12.08.2026).
         *
         * <p>B1: перевёрнутый тайл тоже добываем — на обороте лежит backKelium,
         * вторая выработка снимает жетон-тайл с гекса (стопка ×2 и т. д.).
         */
        private String adjacentGridWithKelium(BuildingToken miner) {
            return minerAdjacentGridWithKelium(state, miner);
        }

    }

    /** Действие Сборка (приказ Разработка): производство юнитов/боеприпасов. */
    static final class AssemblyAction extends Action {
        AssemblyAction(GameState state) {
            super(state);
        }

        @Override public String name() { return "assembly"; }
        @Override public Order order() { return Order.DEVELOPMENT; }
        @Override public boolean implemented() { return true; }

        @Override
        public ActionResult perform(PlayerState player, TurnContext ctx, Agent agent) {
            int ammoMade = 0;
            int unitsMade = 0;
            // Сколько войск КАЖДОГО рода сделано этим действием — уходит в
            // телеметрию: по ней мерится, какие рода игрок вообще производит.
            Map<String, Integer> madeByType = new HashMap<>();
            int[] paid = {0, 0};
            // ПРЕДЕЛ С КАРТЫ: бесплатная Сборка бывает «не более чем N зданиями».
            int buildingLimit = ctx.objectLimit(name());
            int buildingsUsed = 0;
            for (BuildingToken b : player.buildingsOnField()) {
                if (buildingsUsed >= buildingLimit) {
                    break;
                }
                if (!ASSEMBLY_UNIT.containsKey(b.type)) {
                    continue;
                }
                // МОБИЛИЗАЦИЯ (карта рынка «Военный подряд»): в эту Сборку
                // энергия не нужна вообще, все здания считаются запитанными.
                if (!ctx.allPowered && !effectivelyPowered(state, player, b, agent, paid)) {
                    continue;
                }
                UnitType unitType = ASSEMBLY_UNIT.get(b.type);
                // «Нет места → вариант недоступен» (свод §2.1): авиация — своб.
                // воздушная ячейка, наземка — свободная наземная ячейка гекса.
                // K7: здание можно ПРОПУСТИТЬ («Пропустить здание можно»).
                boolean roomForUnit;
                if (unitType == UnitType.TOWER) {
                    // вышке доступна вся зона стройки, не только гекс ЦУ
                    roomForUnit = false;
                    for (String hid : buildableHexes(state, player.seat)) {
                        if (hasRoomForUnit(player, hid, UnitType.TOWER)) {
                            roomForUnit = true;
                            break;
                        }
                    }
                } else {
                    roomForUnit = hasRoomForUnit(player, b.hexId, unitType);
                }
                // ТОЧКИ ПРАВИЛ: сколько выходит за одну Сборку. Спрашиваются ДО
                // выбора и для ОБОИХ выходов — иначе точка «войска» молчала бы в
                // партиях, где боты делали только боеприпасы, и способность,
                // повешенная на неё, оказалась бы мёртвой незаметно. База —
                // печатное число плюс синий модуль.
                int unitsOut = kelium.engine.ability.RuleQuery
                    .of(state, player.seat, kelium.engine.ability.Hook.ASSEMBLY_UNITS_OUT)
                    .about(b).base(Modules.assemblyOutput(player, b.type, "unit")).ask();
                int ammoOut = kelium.engine.ability.RuleQuery
                    .of(state, player.seat, kelium.engine.ability.Hook.ASSEMBLY_AMMO_OUT)
                    .about(b).base(Modules.assemblyOutput(player, b.type, "ammo")).ask();
                List<Choice> opts = new ArrayList<>();
                if (roomForUnit) {
                    opts.add(new Choice("assemble", Map.of("kind", "unit", "building", b.uid),
                        b.type.code + "->" + unitType.code));
                }
                opts.add(new Choice("assemble", Map.of("kind", "ammo", "building", b.uid),
                    b.type.code + "->ammo"));
                // «И НАНИМАЕТ, И ГОТОВИТ» (утиль «Двойная смена», 21.08.2026):
                // столько зданий за эту Сборку выдают ОБА выхода сразу, а не один
                // из двух. Отдельный вариант выбора, а не молчаливая прибавка:
                // выбор остаётся за игроком, и в журнале видно, что он взял.
                boolean dualLeft = ctx.assemblyDualOutput > 0;
                if (dualLeft && roomForUnit) {
                    opts.add(new Choice("assemble",
                        Map.of("kind", "both", "building", b.uid),
                        b.type.code + "->" + unitType.code + " И ammo"));
                }
                opts.add(new Choice("pass", null, "skip " + b.type.code));
                Choice pick = agent.choose(state, opts,
                    Map.of("kind", "assemble", "building_type", b.type.code));
                if (pick.payload() == null) {
                    continue;   // здание пропущено
                }
                buildingsUsed++;   // здание сделало выбор — оно израсходовано
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) pick.payload();
                // Выход зависит от ВЫБОРА (синий модуль задаёт свои числа
                // для боеприпасов и войск по отдельности).
                int nOut = "ammo".equals(payload.get("kind")) ? ammoOut : unitsOut;
                TurnJournal.TurnFacts jf = journal(state).of(player.seat);
                // ОБА ВЫХОДА СРАЗУ: боеприпас выдаётся здесь же, а войско —
                // обычной ветвью ниже (её код длинный, и дублировать его значило
                // бы развести две правды о найме).
                if ("both".equals(payload.get("kind"))) {
                    ctx.assemblyDualOutput--;
                    jf.assemblyAmmoBuildingTypes.add(b.type.code);
                    ammoMade += Storage.addAmmoCapped(state, player, ammoOut);
                    payload = new java.util.HashMap<>(payload);
                    payload.put("kind", "unit");
                    nOut = unitsOut;
                }
                if ("ammo".equals(payload.get("kind"))) {
                    jf.assemblyAmmoBuildingTypes.add(b.type.code);
                    ammoMade += Storage.addAmmoCapped(state, player, nOut);
                } else {
                    jf.assemblyChoseUnits += 1;
                    for (int i = 0; i < nOut; i++) {
                        // B3: сперва ПЕРЕИСПОЛЬЗУЕМ жетон этого типа из резерва
                        // (вернувшийся после гибели) — производственная мощность
                        // не теряется навсегда. Супер-войско выходит первым.
                        UnitToken u = null;
                        for (UnitToken r : player.units) {
                            if (r.hexId == null && r.type == unitType
                                    && (u == null || (r.superUnit && !u.superUnit))) {
                                u = r;
                            }
                        }
                        // ЛИЧНЫЙ ЗАПАС ПО РОДУ: у каждого игрока ровно 4 жетона
                        // каждого рода, и это не меняется за партию. Раньше здесь
                        // стоял только ОБЩИЙ предел (16 жетонов на цвет), поэтому
                        // игрок мог выставить девять вышек — замер на 60 партиях
                        // показал до 9 вышек, 7 пехоты, 5 техники (поймано
                        // дизайнером в проигрывателе 13.08.2026).
                        if (u == null
                                && player.unitsOfKind(unitType)
                                    < state.tokenStats.unitStock(unitType)) {
                            // Номер жетона в запасе рода задаёт его ТРОФЕЙНЫЙ
                            // оборот: у пехоты четвёртый жетон стоит 2 ТО, у техники
                            // и авиации — два из четырёх.
                            u = state.tokenStats.makeUnit(unitType, player.seat,
                                nextUid(state), player.unitsOfKind(unitType));
                            player.units.add(u);
                        }
                        if (u != null) {
                            if (!place(player, u, b, agent, jf)) {
                                // МЕСТА НЕТ СОВСЕМ: жетон не нанимается и остаётся
                                // в запасе — на поле он не появляется и ничем не
                                // компенсируется (уточнение дизайнера 12.08.2026).
                                continue;
                            }
                            unitsMade++;
                            // РАЗБИВКА ПО РОДАМ В ТЕЛЕМЕТРИИ: без неё нельзя
                            // измерить, КАКИЕ войска игрок производит, а вопрос
                            // «почему на поле нет техники и авиации» без этого
                            // числа решается только домыслами. Журнал знает то же
                            // (producedByType), но журнал живёт один ход и в
                            // события не попадает.
                            madeByType.merge(unitType.code, 1, Integer::sum);
                            // журнал: набор уникальных зданий, произведших юнитов
                            // (задание «Конвейер» усиление «3 разных здания»).
                            jf.unitsProducedBuildings.add(b.uid);
                            jf.producedByType.merge(unitType.code, 1, Integer::sum);
                            if (unitType != UnitType.TOWER) {
                                jf.producedUnitBuildingTypes.add(b.type.code);
                            }
                        } else {
                            ammoMade += Storage.addAmmoCapped(state, player, 1);
                        }
                    }
                }
            }
            ctx.actionsPlayed.add(name());
            Map<String, Object> tel = new HashMap<>();
            tel.put("units", unitsMade);
            tel.put("units_by_type", madeByType);
            tel.put("power_coins", paid[0]);
            tel.put("power_offers", paid[1]);
            tel.put("ammo", ammoMade);
            return ActionResult.ok("assembled " + unitsMade + " units, " + ammoMade + " ammo", tel);
        }

        /**
         * ПОСТАВИТЬ НАНЯТОЕ ВОЙСКО. Порядок ровно такой, как назвал дизайнер:
         * <ol>
         *   <li>по умолчанию — на ГЕКС СО ЗДАНИЕМ, которое его произвело;</li>
         *   <li>вышка — иначе: на любой гекс, где стоит ЛЮБОЕ здание игрока
         *       (выбирает игрок), и внутрь здания она не вставляется никогда;</li>
         *   <li>места на гексе нет — войско можно вставить ПРЯМО В ЗДАНИЕ, но не
         *       больше одного войска в здание и не больше одного такого здания у
         *       игрока;</li>
         *   <li>места нет совсем — жетон НЕ нанимается (остаётся в запасе).</li>
         * </ol>
         *
         * @return true, если жетон встал на поле (или внутрь здания)
         */
        private boolean place(PlayerState player, UnitToken u, BuildingToken from,
                              Agent agent, TurnJournal.TurnFacts jf) {
            u.resetDamage();
            u.insideBuildingUid = null;
            if (u.type == UnitType.TOWER) {
                // ВЫШКА: любой гекс, где есть любое своё здание и есть место.
                List<String> spots = new ArrayList<>();
                for (BuildingToken own : player.buildingsOnField()) {
                    if (!spots.contains(own.hexId)
                            && hasRoomForUnit(player, own.hexId, UnitType.TOWER)) {
                        spots.add(own.hexId);
                    }
                }
                if (spots.isEmpty()) {
                    return false;   // вышке некуда встать, а внутрь ей нельзя
                }
                String placeHex = spots.get(0);
                if (spots.size() > 1) {
                    List<Choice> topts = new ArrayList<>();
                    for (String hid : spots) {
                        topts.add(new Choice("tower_hex", hid, "tower @" + hid));
                    }
                    placeHex = (String) agent.choose(state, topts,
                        Map.of("kind", "tower_hex")).payload();
                }
                u.hexId = placeHex;
                boolean cuHere = false;
                for (BuildingToken own : player.buildingsOnField()) {
                    if (own.type == BuildingType.COMMAND_CENTER
                            && placeHex.equals(own.hexId)) {
                        cuHere = true;
                        break;
                    }
                }
                if (!cuHere) {
                    jf.towerPlacedHexes.add(placeHex);
                }
                // ПЕЧАТНЫЙ КОНТЕЙНЕР: жетон НАКРЫЛ ячейку — карта положена.
                // Правило говорит про ЛЮБОЙ жетон и любое накрытие, а не только
                // про вошедшее войско.
                PrintedContainers.onUnitPlaced(state, player, placeHex, u.type);
                return true;
            }
            // НАЙМ ИДЁТ НА ГЕКС СО ЗДАНИЕМ, А НЕ ВНУТРЬ ЗДАНИЯ (правило дизайнера
            // 17.08.2026). Прежде при нехватке места на гексе войско сажалось
            // ГАРНИЗОНОМ внутрь здания прямо на найме — из-за этого укрытие
            // получалось само собой, без единого решения игрока. Гарнизон
            // остаётся, но входят в здание ТОЛЬКО Движением (§5.3): вход внутрь —
            // это перемещение, и оно стоит хода.
            //
            // Единственное исключение из «на гекс своего здания» — вышка: она
            // встаёт на гекс с ЛЮБЫМ своим зданием (см. ветку выше), потому что
            // её производит ЦУ, а стоять она должна там, где нужна.
            if (hasRoomForUnit(player, from.hexId, u.type)) {
                u.hexId = from.hexId;
                // СУПЕРОРУЖИЕ ПОМНИТ СВОЙ СТАПЕЛЬ: с гекса найма счётчик запуска
                // не снимается, оружие обязано выехать (супер задания 3.0).
                if (SuperWeapon.isWeapon(state, u)) {
                    SuperWeapon.onWeaponHired(player, from.hexId);
                }
                PrintedContainers.onUnitPlaced(state, player, from.hexId, u.type);
                return true;
            }
            // Места на гексе нет — жетон не нанимается и остаётся в запасе.
            return false;
        }

        /** Есть ли на гексе место под юнит данного типа (ячейка по размеру). */
        private boolean hasRoomForUnit(PlayerState player, String hexId, UnitType t) {
            return Actions.roomForUnit(state, hexId, t);
        }
    }

    /**
     * СКОЛЬКО ЯЧЕЕК ПРЕДЛОЖЕНИЯ ОТКРЫТО на карте рынка при таком числе игроков.
     *
     * <p>Одна — и ТОЛЬКО ПРИ ЧЕТЫРЁХ ИГРОКАХ две (ревью дизайнера 17.08.2026;
     * прежде вторая открывалась с трёх). Предложение — дефицитный ресурс стола:
     * при трёх игроках две ячейки на два предложения означают, что хватает почти
     * всем, а на четверых без второй ячейки последний по кругу до рынка не
     * доходил вообще.
     *
     * <p>ПОЧЕМУ ЭТО ОТДЕЛЬНЫЙ ПУБЛИЧНЫЙ МЕТОД, а не условие внутри действия:
     * то же самое число нужно проигрывателю, который рисует ячейки на планшете
     * рынка. Раньше правило лежало в трёх местах — в движке и в двух подписях
     * интерфейса, — и после первой же правки они разошлись: движок открывал одну
     * ячейку, а планшет рисовал две открытыми.
     */
    public static int marketCellsOpen(int numPlayers) {
        return numPlayers >= 4 ? 2 : 1;
    }

    /**
     * СТАНЦИЯ СЪЕХАЛА — ПЕРЕСЧИТАТЬ ЕЁ ВЫРАБОТКУ.
     *
     * <p>У энергостанции выработка зависит от сектора, на котором она стоит:
     * полный номинал только на ЖЁЛТОМ секторе гекса, на любом другом — 1 кубик.
     * Поэтому любой её переезд меняет число кубиков, и если оно изменилось,
     * станция забирает свои кубики обратно на себя (как при постройке) и
     * раскладывает их заново ближайшей Сменой энергии. Без этого станция,
     * съехавшая с жёлтого сектора, продолжала бы кормить здания номиналом,
     * которого больше не выдаёт.
     *
     * <p>Правило одно на все способы переезда — и на Стройку, и на «Эвакуацию».
     * Второй копии этого расчёта быть не должно: она разойдётся с первой.
     */
    static void resettlePlant(GameState state, PlayerState player, BuildingToken b) {
        if (b.type != BuildingType.POWER_PLANT) {
            return;
        }
        int now = Power.plantOutput(state, b);
        int had = b.energyIdle;
        for (BuildingToken c : player.buildingsOnField()) {
            had += c.energyBySource.getOrDefault(b.uid, 0);
        }
        if (now != had) {
            for (BuildingToken c : player.buildingsOnField()) {
                c.stripEnergyOf(b.uid);
            }
            b.energyIdle = now;
        }
    }

    /**
     * ВЛЕЗЕТ ЛИ ЖЕТОН ВОЙСКА НА ГЕКС ФИЗИЧЕСКИ — только про место, без правил
     * проходимости.
     *
     * <p>Отделено от {@code canEnterHex} намеренно: у гекса шесть секторов земли и
     * один сектор Неба, и это ограничение физическое — его не отменяет ни карта,
     * ни телепорт. А вот запретные гексы, гряды зарождения, чужие здания и
     * требование двух секторов технике — это правила ДВИЖЕНИЯ, и «Эвакуация» их
     * не соблюдает, потому что она не движение.
     */
    static boolean roomForUnit(GameState state, String hexId, UnitType t) {
        Hex h = state.field.get(hexId);
        if (t == UnitType.AIRCRAFT) {
            for (PlayerState pl : state.players) {
                for (UnitToken u : pl.units) {
                    if (u.type == UnitType.AIRCRAFT && hexId.equals(u.hexId)) {
                        return false;   // сектор Неба на гексе занят
                    }
                }
            }
            return true;
        }
        int[] load = groundLoad(state, hexId, -1);
        return h.fitsWithRepack(t == UnitType.VEHICLE ? 2 : 1, load[0], load[1]);
    }

    // ======================================================================
    //  INFRASTRUCTURE: build, energy_swap
    // ======================================================================

    /** Действие Стройка (приказ Инфраструктура): постройка одного здания. */
    static final class BuildAction extends Action {
        BuildAction(GameState state) {
            super(state);
        }

        @Override public String name() { return "build"; }
        @Override public Order order() { return Order.INFRASTRUCTURE; }
        @Override public boolean implemented() { return true; }

        @Override
        @SuppressWarnings("unchecked")
        public ActionResult perform(PlayerState player, TurnContext ctx, Agent agent) {
            // B10: за одно действие Стройка — НЕСКОЛЬКО операций (построить/
            // перенести), 2-я и далее с наценкой из ruleset. Цикл до паса.
            int ops = 0;
            int coinsSpent = 0;
            // ПРЕДЕЛ С КАРТЫ: бесплатная Стройка бывает «одна операция».
            int opLimit = ctx.objectLimit(name());
            // ОДНА ОПЕРАЦИЯ НА ОДНО ЗДАНИЕ ЗА ДЕЙСТВИЕ (заказ дизайнера
            // 25.08.2026, ключ actions.build.one_op_per_building). Иначе то же
            // здание можно поставить и тут же снять, доя монету за снос.
            java.util.Set<Integer> тронутые = new java.util.HashSet<>();
            StringBuilder detail = new StringBuilder();
            while (true) {
                if (ops >= opLimit) {
                    break;
                }
                ActionResult one = performOneOp(player, ctx, agent, тронутые);
                if (one == null) {
                    break;   // пас или ничего доступного
                }
                if (!one.ok()) {
                    if (ops == 0) {
                        ctx.actionsPlayed.add(name());
                        return one;
                    }
                    break;
                }
                ops++;
                if (one.telemetry() != null
                        && one.telemetry().get("coin_spent") instanceof Number cs) {
                    coinsSpent += cs.intValue();
                }
                detail.append(one.detail()).append("; ");
            }
            ctx.actionsPlayed.add(name());
            if (ops == 0) {
                return ActionResult.ok("built nothing");
            }
            Map<String, Object> tel = new HashMap<>();
            tel.put("ops", ops);
            tel.put("coin_spent", coinsSpent);
            return ActionResult.ok(detail.toString().trim(), tel);
        }

        /** Одна операция стройки/переноса; null = пас или нет доступного. */
        @SuppressWarnings("unchecked")
        private ActionResult performOneOp(PlayerState player, TurnContext ctx, Agent agent,
                                          java.util.Set<Integer> тронутые) {
            // УДОРОЖАНИЕ КАСАЕТСЯ ТОЛЬКО РАЗМЕЩЕНИЯ ИЗ ЗАПАСА (правило дизайнера
            // 17.08.2026). У Стройки три операции: разместить здание из запаса на
            // поле, вернуть здание с поля в запас, переместить здание по полю
            // (поворот на том же гексе — тоже перемещение). Дорожает только
            // первая: первое размещение по напечатанной цене, каждое следующее на
            // 1 монету дороже. Возврат и перемещение не дорожают никогда —
            // перемещение стоит фиксированную монету, возврат даёт фиксированную
            // компенсацию. Поэтому счётчик наценки ведётся по ключу build_place, а
            // не по всем операциям подряд, как было раньше.
            List<Integer> schedule = rs.getIntList("actions.build.surcharge_coins");
            // ПОДРЯД НА СТРОЙКУ (карта рынка «Инженерная контора»): второе и
            // третье здание ставятся по печатной цене, без надбавки.
            int surcharge = ctx.noSurcharge.contains("build_place")
                ? 0 : ctx.nextOpSurcharge("build_place", schedule);
            // СКИДКА И БЕСПЛАТНОСТЬ ОТ УТИЛЯ (21.08.2026) идут ЧЕРЕЗ ТУ ЖЕ
            // надбавку, которой считается удорожание: цена постройки в одном
            // месте, и складывать её из двух источников порознь значило бы
            // разойтись с ним рано или поздно. Отрицательная «надбавка» и есть
            // скидка; ниже нуля цена не падает — это проверяет buildable.
            surcharge -= Math.max(0, ctx.buildDiscountCoins);
            List<Map<String, Object>> menu = ctx.buildMovesOnly
                ? new ArrayList<>() : buildable(player, surcharge, ctx.buildFree);
            List<Map<String, Object>> moveMenu = movable(player, ctx);
            // ОДНА ОПЕРАЦИЯ НА ЗДАНИЕ. Уже тронутое этим действием здание из меню
            // уходит: иначе его можно переставлять и сносить по кругу.
            boolean одноНаЗдание = rs.getBool("actions.build.one_op_per_building", false);
            if (одноНаЗдание && !тронутые.isEmpty()) {
                moveMenu.removeIf(m -> m.get("uid") instanceof Number n
                    && тронутые.contains(n.intValue()));
            }
            if (menu.isEmpty() && moveMenu.isEmpty()) {
                return null;
            }
            List<Choice> opts = new ArrayList<>();
            for (Map<String, Object> spec : menu) {
                opts.add(new Choice("build_pick", spec, (String) spec.get("label")));
            }
            for (Map<String, Object> spec : moveMenu) {
                opts.add(new Choice("move_pick", spec, (String) spec.get("label")));
            }
            // B10: снос — любое своё здание на поле убирается в резерв за возврат
            // demolish_refund_coins монет (ЦУ не сносится — только переносится).
            int refund = rs.getInt("actions.build.demolish_refund_coins");
            // СНОС СВОЕГО ЦУ (заказ дизайнера 25.08.2026, ключ
            // actions.build.demolish_cu_allowed). Прежде ЦУ из меню исключалось
            // всегда. Теперь его можно разобрать и получить монету — это выход
            // из тупика, когда строить не на что; расплата в том, что в свой ход
            // игрок ОБЯЗАН вернуть ЦУ на поле спец-действием.
            boolean цуМожноСносить = rs.getBool("actions.build.demolish_cu_allowed", false);
            for (BuildingToken b : player.buildingsOnField()) {
                if (b.type == BuildingType.COMMAND_CENTER && !цуМожноСносить) {
                    continue;
                }
                if (одноНаЗдание && тронутые.contains(b.uid)) {
                    continue;
                }
                opts.add(new Choice("demolish_pick", b.uid,
                    "снести " + b.type.code + "@" + b.hexId + " (+" + refund + " мон)"));
            }
            opts.add(new Choice("pass", null, "stop building"));
            Choice pick = agent.choose(state, opts, Map.of("kind", "build_pick"));
            if (pick.payload() == null) {
                return null;
            }
            if ("move_pick".equals(pick.kind())) {
                Map<String, Object> mv = (Map<String, Object>) pick.payload();
                if (mv.get("uid") instanceof Number n) {
                    тронутые.add(n.intValue());
                }
                return performMove(player, ctx, agent, mv);
            }
            if ("demolish_pick".equals(pick.kind())) {
                тронутые.add(((Number) pick.payload()).intValue());
                return performDemolish(player, ctx, ((Number) pick.payload()).intValue(), refund);
            }
            Map<String, Object> spec = (Map<String, Object>) pick.payload();
            BuildingType btype = (BuildingType) spec.get("btype");
            int fp = buildingFootprint(btype);
            List<String> candidates = new ArrayList<>();
            for (String hid : buildSpots(player, btype)) {
                // здание жёсткое, но войска на гексе НЕЖЁСТКИЕ: строить можно,
                // если ПЕРЕУПАКОВКОЙ войск освобождается след из fp смежных ячеек
                int[] ld = groundLoad(state, hid, -1);
                if (state.field.get(hid).chooseFootprint(fp, ld[0], ld[1]) != null) {
                    candidates.add(hid);
                }
            }
            if (candidates.isEmpty()) {
                return ActionResult.fail("no room to build " + spec.get("label"));
            }
            List<Choice> hopts = new ArrayList<>();
            for (String hid : candidates) {
                hopts.add(new Choice("build_hex", hid, hid));
            }
            Choice hpick = agent.choose(state, hopts, Map.of("kind", "build_hex", "btype", btype.code));
            String targetHex = (String) hpick.payload();
            List<Integer> sides = chooseFacing(player, agent, targetHex, btype, fp);

            // ЦЕНА ПОСТРОЙКИ спрашивается через точку правил: так карты вроде
            // «Промышленник» (стройка дешевле) вмешиваются в одном месте, а не
            // правкой каждого места, где считается цена (13.08.2026).
            int cost = Math.max(0, kelium.engine.ability.RuleQuery
                .of(state, player.seat, kelium.engine.ability.Hook.BUILD_PRICE)
                .about(btype)
                .base(((Number) spec.get("cost")).intValue())
                .ask());
            player.resources.pay(Resource.COIN, cost);
            Integer level = (Integer) spec.get("level");
            // B2/B3: физический жетон здания один — уничтоженный (hexId == null)
            // ПЕРЕИСПОЛЬЗУЕТСЯ при повторной стройке, а не плодится дубликат.
            BuildingToken b = null;
            for (BuildingToken rb : player.buildings) {
                if (rb.hexId == null && rb.type == btype
                        && java.util.Objects.equals(rb.level, level)) {
                    b = rb;
                    break;
                }
            }
            if (b == null) {
                b = state.tokenStats.makeBuilding(btype, player.seat, nextUid(state), level);
                // B7: активные пассивки «+HP» действуют и на новые здания
                if (Passives.hasPassive(state, player.seat, "buildings_plus1_hp")) {
                    b.hp += 1;
                }
                if (btype == BuildingType.COMMAND_CENTER
                        && Passives.hasPassive(state, player.seat, "cu_plus2_hp")) {
                    b.hp += 2;
                }
                player.buildings.add(b);
            }
            b.resetDamage();
            b.hexId = targetHex;
            // B10/§12.1/§3.2: «новый источник приходит со своими кубиками уже
            // на себе». ЭС — кубики простаивают на станции до Смены энергии;
            // ЦУ — сам себе источник и потребитель, запитывается сразу.
            if (btype == BuildingType.COMMAND_CENTER) {
                int gives = state.tokenStats.buildingEnergyGives(BuildingType.COMMAND_CENTER);
                int self = Math.min(gives, b.energySlots);
                b.addEnergyFrom(b.uid, self);
                b.energyIdle = gives - self;
            }
            Hex bh = state.field.get(targetHex);
            bh.occupySides(b.uid, sides);
            if (btype == BuildingType.POWER_PLANT) {
                // ПОСЛЕ occupySides: выработка станции зависит от того, накрыл
                // ли её след ЖЁЛТУЮ ЯЧЕЙКУ гекса (см. Power.plantOutput), а до
                // занятия ячеек этот вопрос ещё не имеет ответа.
                b.energyIdle = Power.plantOutput(state, b);
            }
            // ПЕЧАТНЫЙ КОНТЕЙНЕР: если след здания накрыл ячейку с
            // напечатанным контейнером — владелец берёт карту из запаса.
            PrintedContainers.onBuildingPlaced(state, player, b);
            // СТАРЫЙ РЕЖИМ: стройка на гексе СЖИГАЕТ лежащий там жетон
            // контейнера (ruleset 1.6.0-c1).
            TokenContainers.onBuildingPlaced(state, b);
            TurnJournal.TurnFacts f = journal(state).of(player.seat);
            f.buildOps += 1;
            f.builtOnHexes.add(targetHex);
            f.buildOpHexes.add(targetHex);      // o15 «Стройбум»: гексы операций
            if (btype == BuildingType.COMMAND_CENTER) {
                f.cuPlacedHexes.add(targetHex);  // o17 «Штаб на передовой»
            }
            ctx.recordOp("build");
            ctx.recordOp("build_place");   // дорожает только размещение из запаса
            Map<String, Object> tel = new HashMap<>();
            tel.put("coin_spent", cost);
            tel.put("hex", targetHex);
            return ActionResult.ok("built " + spec.get("label") + " @ " + targetHex + " for " + cost, tel);
        }

        /**
         * ПОВОРОТ ЗДАНИЯ — решение игрока, а не «первое свободное место».
         *
         * <p>С тех пор как зона стройки растёт только через стенки своих зданий,
         * а добытчик копает только через свою стенку, поворот стал полноценным
         * решением: куда повернул — туда и растёшь, ту жилу и копаешь, ту ячейку
         * с печатным контейнером и накрываешь. Раньше след подбирался
         * автоматически первым подходящим, и боты ставили добытчики «спиной» к
         * жилам (баг найден дизайнером 12.08.2026).
         *
         * <p>Перебираем все допустимые следы (дуги из fp смежных свободных
         * ячеек, с учётом переупаковки войск) и спрашиваем игрока. Один вариант
         * — не спрашиваем.
         */
        private List<Integer> chooseFacing(PlayerState player, Agent agent, String hexId,
                                           BuildingType btype, int fp) {
            Hex h = state.field.get(hexId);
            int[] load = groundLoad(state, hexId, -1);
            List<List<Integer>> variants = new ArrayList<>();
            for (int start = 0; start < 6; start++) {
                List<Integer> run = h.footprintAt(start, fp, load[0], load[1]);
                if (run != null) {
                    variants.add(run);
                }
            }
            if (variants.isEmpty()) {
                return h.chooseFootprint(fp, load[0], load[1]);
            }
            if (variants.size() == 1) {
                return variants.get(0);
            }
            List<Choice> opts = new ArrayList<>();
            for (List<Integer> v : variants) {
                StringBuilder label = new StringBuilder("стенками");
                for (int side : v) {
                    String nb = h.neighborBySide[side];
                    label.append(' ').append(side).append(nb == null ? "" : "→" + nb);
                }
                opts.add(new Choice("build_facing", v, label.toString()));
            }
            Choice pick = agent.choose(state, opts, Map.of("kind", "build_facing",
                "btype", btype.code, "hex", hexId));
            @SuppressWarnings("unchecked")
            List<Integer> chosen = (List<Integer>) pick.payload();
            return chosen != null ? chosen : variants.get(0);
        }

        /**
         * B10: снос своего здания. Жетон уходит в резерв (hexId=null), стороны
         * гекса освобождаются, энергия корректно снимается, игрок получает возврат
         * монет. Считается операцией стройки (наценка на следующие операции).
         */
        private ActionResult performDemolish(PlayerState player, TurnContext ctx, int uid, int refund) {
            BuildingToken b = null;
            for (BuildingToken x : player.buildingsOnField()) {
                if (x.uid == uid) {
                    b = x;
                    break;
                }
            }
            if (b == null) {
                return ActionResult.fail("demolish: здание не найдено");
            }
            String hex = b.hexId;
            returnOwnBuildingToReserve(state, player, b, true);
            player.resources.add(Resource.COIN, refund);
            TurnJournal.TurnFacts f = journal(state).of(player.seat);
            f.buildOps += 1;
            f.razedOwnHexes.add(hex);
            f.buildOpHexes.add(hex);        // o15: снос — тоже операция стройки
            if (b.type != BuildingType.COMMAND_CENTER) {
                f.demolishedNonCu = true;
            }
            ctx.recordOp("build");
            Map<String, Object> tel = new HashMap<>();
            tel.put("coin_refund", refund);
            tel.put("hex", hex);
            return ActionResult.ok("demolished " + b.type.code + " @ " + hex + " (+" + refund + ")", tel);
        }

        /**
         * ЦЕНА ПЕРЕНОСА — РОВНО 1 МОНЕТА, какое бы здание ни переносили
         * (правило дизайнера 16.08.2026, {@code actions.build.move_cost_coins}).
         *
         * <p>Раньше перенос стоил полную цену постройки, и передвинуть авиабазу
         * было втрое дороже, чем казарму, — хотя перекладывается один и тот же
         * картонный жетон. ЦУ при этом переносилось бесплатно, то есть было
         * исключением в другую сторону. Теперь исключений нет: любое стоящее на
         * поле здание переезжает за монету, ЦУ в том числе.
         *
         * <p>Бесплатной осталась только ПОСТРОЙКА уничтоженного ЦУ заново —
         * это не перенос, а возвращение в игру (см. {@code buildable}).
         */
        private int moveCost(PlayerState player, BuildingToken b) {
            return Math.max(0, Ctx.rules(state).getInt("actions.build.move_cost_coins", 1));
        }

        /**
         * Перечень зданий игрока, доступных к ПЕРЕНОСУ (Стройка/манёвр): снести и
         * поставить в другом доступном гексе за полную цену; ЦУ — бесплатно, но не
         * более 1 раза за ход. Здание доступно, если оно на поле, игрок может
         * оплатить перенос и есть куда его поставить (кроме текущего гекса).
         */
        /**
         * КУДА МОЖНО ПОСТАВИТЬ СТРОЯЩЕЕСЯ ЗДАНИЕ. Обычно — только в свою зону
         * стройки.
         *
         * <p>ИСКЛЮЧЕНИЕ ДЛЯ ПОТЕРЯННОГО ЦУ (правило дизайнера 16.08.2026):
         * игрок, которому снесли ЦУ, ставит его заново на ЛЮБОЙ гекс поля —
         * даже туда, где строить по обычным правилам не может. Иначе снос ЦУ
         * у того, чья зона стройки держалась на самом ЦУ, означал бы, что
         * поставить его негде вообще: не потеря, а выбывание из игры.
         *
         * <p>Гексы под запретом и под тайлами зарождения исключение не
         * открывает: туда не встаёт вообще ни один жетон.
         */
        private List<String> buildSpots(PlayerState player, BuildingType btype) {
            if (btype != BuildingType.COMMAND_CENTER) {
                return buildableHexes(state, player.seat);
            }
            List<String> all = new ArrayList<>();
            for (Hex h : state.field.hexes.values()) {
                if (h.kind != kelium.core.HexKind.FORBIDDEN && !h.hasSpawnTile()) {
                    all.add(h.id);
                }
            }
            return all;
        }

        private List<Map<String, Object>> movable(PlayerState player, TurnContext ctx) {
            List<Map<String, Object>> out = new ArrayList<>();
            int coin = player.resources.coin();
            // ПЕРЕНОС ЗА СЧЁТ УТИЛЯ («Перестройка», 21.08.2026): столько первых
            // переносов в этом действии не стоят ничего. Даром, а не дешевле:
            // на карте так и написано — «бесплатно».
            boolean freeMove = ctx != null && ctx.freeBuildingMoves > 0;
            boolean cuMovedThisTurn = cuAlreadyMoved(player);
            for (BuildingToken b : player.buildingsOnField()) {
                boolean isCu = b.type == BuildingType.COMMAND_CENTER;
                int cost = freeMove ? 0 : moveCost(player, b);
                if (isCu && cuMovedThisTurn) {
                    continue;   // ЦУ уже переносили в этот ход
                }
                // ЦУ БОЛЬШЕ НЕ ПЕРЕЕЗЖАЕТ ДАРОМ: монету за перенос платят все.
                if (cost > coin) {
                    continue;
                }
                int fp = buildingFootprint(b.type);
                boolean hasSpot = false;
                for (String hid : buildableHexes(state, player.seat)) {
                    if (hid.equals(b.hexId)) {
                        continue;
                    }
                    int[] ld = groundLoad(state, hid, -1);
                    if (state.field.get(hid).chooseFootprint(fp, ld[0], ld[1]) != null) {
                        hasSpot = true;
                        break;
                    }
                }
                if (!hasSpot) {
                    continue;
                }
                Map<String, Object> m = new HashMap<>();
                m.put("uid", b.uid);
                m.put("cost", cost);
                m.put("is_cu", isCu);
                m.put("label", "move " + b.type.code
                    + (b.level != null ? " L" + b.level : "")
                    + " (" + cost + " coin)");
                out.add(m);
            }
            return out;
        }

        // Помечал ли уже журнал перенос ЦУ в этот ход (для лимита 1x/ход).
        private boolean cuAlreadyMoved(PlayerState player) {
            for (int uid : journal(state).of(player.seat).movedBuildingUids) {
                for (BuildingToken b : player.buildings) {
                    if (b.uid == uid && b.type == BuildingType.COMMAND_CENTER) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Выполнить перенос здания: снять с текущего гекса (освободить стороны),
         * оплатить полную цену (ЦУ бесплатно), выбрать новый гекс и занять стороны.
         * Питает журнал {@code movedBuilding}/{@code movedBuildingUids} (задание
         * «Переезд» o16) и {@code razedOwnHexes}/{@code builtOnHexes}.
         */
        @SuppressWarnings("unchecked")
        private ActionResult performMove(PlayerState player, TurnContext ctx, Agent agent,
                                         Map<String, Object> spec) {
            int uid = ((Number) spec.get("uid")).intValue();
            BuildingToken b = null;
            for (BuildingToken bt : player.buildings) {
                if (bt.uid == uid) {
                    b = bt;
                    break;
                }
            }
            if (b == null || b.hexId == null) {
                ctx.actionsPlayed.add(name());
                return ActionResult.fail("move: building gone");
            }
            int fp = buildingFootprint(b.type);
            String fromHex = b.hexId;
            // ЦЕНА ПОСТРОЙКИ спрашивается через точку правил: так карты вроде
            // «Промышленник» (стройка дешевле) вмешиваются в одном месте, а не
            // правкой каждого места, где считается цена (13.08.2026).
            int cost = Math.max(0, kelium.engine.ability.RuleQuery
                .of(state, player.seat, kelium.engine.ability.Hook.BUILD_PRICE)
                .about(b.type)
                .base(((Number) spec.get("cost")).intValue())
                .ask());
            // ПОРЯДОК ПЕРЕНОСА (правило дизайнера 16.08.2026): сначала СНЯТЬ
            // здание, и только потом считать, куда его можно поставить. Снятый
            // жетон больше не держит зону стройки — а раньше места считались,
            // пока он ещё стоял, и здание могло переехать на гекс, который
            // держало собой же.
            //
            // Войско, стоявшее ВНУТРИ здания, с ним не переезжает — остаётся на
            // прежнем гексе и теряет укрытие.
            evictFromBuilding(player, b);
            state.field.get(fromHex).freeSidesByToken(b.uid);
            b.hexId = null;

            List<String> candidates = new ArrayList<>();
            for (String hid : buildableHexes(state, player.seat)) {
                if (hid.equals(fromHex)) {
                    continue;
                }
                int[] ld = groundLoad(state, hid, -1);
                if (state.field.get(hid).chooseFootprint(fp, ld[0], ld[1]) != null) {
                    candidates.add(hid);
                }
            }
            if (candidates.isEmpty()) {
                // Ставить некуда — возвращаем здание туда, где стояло: ход не
                // состоялся, а не «здание пропало».
                b.hexId = fromHex;
                List<Integer> back = state.field.get(fromHex)
                    .chooseFootprint(fp, groundLoad(state, fromHex, -1)[0],
                        groundLoad(state, fromHex, -1)[1]);
                if (back != null) {
                    state.field.get(fromHex).occupySides(b.uid, back);
                }
                ctx.actionsPlayed.add(name());
                return ActionResult.fail("move: no room");
            }
            List<Choice> hopts = new ArrayList<>();
            for (String hid : candidates) {
                hopts.add(new Choice("move_hex", hid, hid));
            }
            Choice hpick = agent.choose(state, hopts, Map.of("kind", "move_hex", "btype", b.type.code));
            String targetHex = (String) hpick.payload();
            // БЕСПЛАТНЫЙ ПЕРЕНОС ТРАТИТСЯ: карта даёт их столько, сколько
            // напечатано, и следующий перенос в этом же действии снова платный.
            if (ctx.freeBuildingMoves > 0) {
                ctx.freeBuildingMoves--;
                cost = 0;
            }
            if (cost > 0) {
                player.resources.pay(Resource.COIN, cost);
            }
            Hex targetH = state.field.get(targetHex);
            // ПОВОРОТ ПРИ ПЕРЕНОСЕ — ТАК ЖЕ, КАК ПРИ СТРОЙКЕ (см. chooseFacing).
            // Раньше здесь стороны подбирались слепо, первым подходящим с
            // начала (chooseFootprint) — и перенесённое здание могло встать
            // спиной к гексу, который иначе вошёл бы в зону стройки: тот же
            // баг, что дизайнер уже ловил на добытчиках 12.08.2026, только
            // для переноса его не исправили заодно (найдено 14.08.2026 на
            // перенесённом ЦУ).
            List<Integer> sides = chooseFacing(player, agent, targetHex, b.type, fp);
            // «чистый» гекс для o17: до переноса на нём не стояло СВОИХ зданий
            boolean virgin = true;
            for (BuildingToken own : player.buildingsOnField()) {
                if (own.uid != b.uid && targetHex.equals(own.hexId)) {
                    virgin = false;
                    break;
                }
            }
            b.hexId = targetHex;
            targetH.occupySides(b.uid, sides);
            // ЖЁЛТАЯ ЯЧЕЙКА И ПЕРЕНОС: у энергостанции выработка зависит от
            // ячейки, на которой она стоит, поэтому переезд её меняет. Если
            // число кубиков стало другим — источник забирает свои кубики
            // обратно на себя (как при постройке) и раскладывает их заново
            // ближайшей Сменой энергии. Без этого станция, съехавшая с жёлтой
            // ячейки, продолжала бы кормить здания номиналом, которого больше
            // не выдаёт.
            resettlePlant(state, player, b);
            // ПЕЧАТНЫЙ КОНТЕЙНЕР: перенос — тоже «здание встало на ячейку».
            PrintedContainers.onBuildingPlaced(state, player, b);
            // СТАРЫЙ РЕЖИМ: стройка на гексе СЖИГАЕТ лежащий там жетон
            // контейнера (ruleset 1.6.0-c1).
            TokenContainers.onBuildingPlaced(state, b);
            TurnJournal.TurnFacts f = journal(state).of(player.seat);
            f.movedBuilding = true;
            f.movedBuildingUids.add(b.uid);
            f.razedOwnBuilding = true;
            f.razedOwnHexes.add(fromHex);
            f.builtOnHexes.add(targetHex);
            // o16 «Переезд» 10.0: считаются ЛЮБЫЕ перенесённые здания, ЦУ — это
            // усиление, а не исключение: перенос стоит одну монету всем.
            f.movedAnyBuildingUids.add(b.uid);
            f.buildOpHexes.add(targetHex);
            f.buildOps += 1;
            if (b.type == BuildingType.COMMAND_CENTER) {
                f.movedCuThisTurn = true;
                f.cuPlacedHexes.add(targetHex);   // o17 «Штаб на передовой»
                if (virgin) {
                    f.movedCuToVirginHex = true;
                }
            } else {
                f.movedNonCuBuilding = true;
                f.movedNonCuUids.add(b.uid);
            }
            ctx.recordOp("build");
            ctx.actionsPlayed.add(name());
            Map<String, Object> tel = new HashMap<>();
            tel.put("coin_spent", cost);
            tel.put("moved_building", b.uid);
            tel.put("from", fromHex);
            tel.put("to", targetHex);
            return ActionResult.ok("moved " + b.type.code + " " + fromHex + " -> " + targetHex, tel);
        }

        /**
         * То же меню стройки, но с послаблениями утиля (21.08.2026).
         *
         * <p>{@code free} — «построй здание бесплатно»: цена не платится вовсе,
         * поэтому и по карману проходит ЛЮБОЕ здание, даже когда монет нет. Это
         * сделано отрицательной надбавкой, а не отдельной веткой: цена здания
         * считается в одном месте, и вторая ветка неизбежно разошлась бы с ним.
         *
         * <p>Отрицательная цена в меню невозможна: скидка опускает её только до
         * нуля — «постройка не может доплачивать».
         */
        private List<Map<String, Object>> buildable(PlayerState player, int surcharge,
                                                     boolean free) {
            List<Map<String, Object>> out = buildable(player, free ? -999 : surcharge);
            for (Map<String, Object> m : out) {
                int c = ((Number) m.get("cost")).intValue();
                int fixed = free ? 0 : Math.max(0, c);
                m.put("cost", fixed);
                m.put("label", m.get("label") + " (" + fixed + " мон)");
            }
            return out;
        }

        private List<Map<String, Object>> buildable(PlayerState player, int surcharge) {
            List<Map<String, Object>> out = new ArrayList<>();
            int coin = player.resources.coin();
            // B2: считаем ТОЛЬКО стоящие на поле здания — уничтоженное (hexId
            // null) вернулось в запас и может быть построено заново.
            Set<Integer> minerLvls = new java.util.HashSet<>();
            Set<Integer> plantLvls = new java.util.HashSet<>();
            for (BuildingToken b : player.buildingsOnField()) {
                if (b.type == BuildingType.MINER) {
                    minerLvls.add(b.level);
                } else if (b.type == BuildingType.POWER_PLANT) {
                    plantLvls.add(b.level);
                }
            }
            for (int lv = 1; lv <= 4; lv++) {
                if (!minerLvls.contains(lv)) {
                    int c = state.tokenStats.minerCost(lv) + surcharge;
                    if (c <= coin) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("btype", BuildingType.MINER);
                        m.put("level", lv);
                        m.put("cost", c);
                        m.put("label", "miner L" + lv);
                        out.add(m);
                    }
                }
                if (!plantLvls.contains(lv)) {
                    int c = state.tokenStats.plantCost(lv) + surcharge;
                    if (c <= coin) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("btype", BuildingType.POWER_PLANT);
                        m.put("level", lv);
                        m.put("cost", c);
                        m.put("label", "plant L" + lv);
                        out.add(m);
                    }
                }
            }
            Set<BuildingType> builtMil = new java.util.HashSet<>();
            for (BuildingToken b : player.buildingsOnField()) {   // B2
                builtMil.add(b.type);
            }
            BuildingType[][] mil = {
                {BuildingType.BARRACKS}, {BuildingType.FACTORY}, {BuildingType.AIRBASE}
            };
            String[] keys = {"barracks", "factory", "airbase"};
            for (int i = 0; i < mil.length; i++) {
                BuildingType bt = mil[i][0];
                if (!builtMil.contains(bt)) {
                    int c = player.board.troop.buildingPrice(keys[i]) + surcharge;
                    if (c <= coin) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("btype", bt);
                        m.put("cost", c);
                        m.put("label", keys[i]);
                        out.add(m);
                    }
                }
            }
            // §12.1: ЦУ из запаса отстраивается ОБЫЧНОЙ Стройкой, как любое
            // военное здание. Цены на планшете нет — временный ключ ruleset
            // (command_center.build_price_coins), вопрос дизайнеру открыт.
            if (!builtMil.contains(BuildingType.COMMAND_CENTER)
                    && hasReserve(player, BuildingType.COMMAND_CENTER)) {
                int c = ((Number) rs.get("command_center.build_price_coins", 2)).intValue()
                    + surcharge;
                if (c <= coin) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("btype", BuildingType.COMMAND_CENTER);
                    m.put("cost", c);
                    m.put("label", "command_center");
                    out.add(m);
                }
            }
            return out;
        }

        private boolean hasReserve(PlayerState player, BuildingType bt) {
            for (BuildingToken b : player.buildings) {
                if (b.type == bt && b.hexId == null) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Действие Смена энергии (приказ Инфраструктура): перераспределение энергии. */
    static final class EnergySwapAction extends Action {
        EnergySwapAction(GameState state) {
            super(state);
        }

        @Override public String name() { return "energy_swap"; }
        @Override public Order order() { return Order.INFRASTRUCTURE; }
        @Override public boolean implemented() { return true; }

        @Override
        public ActionResult perform(PlayerState player, TurnContext ctx, Agent agent) {
            // K3 (§3.2 свода): выбери ОДИН свой гекс и перераспредели энергию,
            // ВЫТЕКАЮЩУЮ ИЗ НЕГО (кубики источников этого гекса — где бы они
            // сейчас ни стояли). Каждый следующий гекс-исход в том же действии
            // оплачивается наценкой (actions.energy_swap.surcharge_coins).
            // «Вечный кубик» жетона хранилища — источник вне поля, доступен в
            // любой Смене энергии без наценки за гекс.
            // ПЕРЕКОММУТАЦИЯ: когда наценки сняты со всех гексов, пошаговый выбор
            // «взял гекс — разложил — взял следующий» становится выбором БЕЗ
            // СИГНАЛА. Обычно шаги ограничивает цена: второй гекс стоит монету, и
            // потому есть смысл решать по одному. Здесь цены нет, гексов бывает
            // пять, кубиков восемь, и число разных последовательностей уходит в
            // тысячи — а результат зависит только от ИТОГОВОЙ раскладки, не от
            // порядка. Поэтому здесь предлагается сразу ГОТОВАЯ РАСКЛАДКА целиком.
            if (ctx.noSurcharge.contains("energy_swap")) {
                ActionResult whole = rewireEverything(player, ctx, agent);
                if (whole != null) {
                    return whole;
                }
            }
            List<Integer> schedule = rs.getIntList("actions.energy_swap.surcharge_coins");
            int placedTotal = 0;
            int hexesDone = 0;
            boolean storageDone = false;
            boolean cardSourceDone = false;
            java.util.Set<String> doneHexes = new java.util.HashSet<>();
            while (true) {
                // ПЕРЕКОММУТАЦИЯ (карта рынка «Инженерная контора»): энергия
                // переставляется с любых своих гексов и полностью бесплатно —
                // надбавки за второй и последующие гексы-исходы нет.
                //
                // ЗАЦИКЛИТЬСЯ ЗДЕСЬ НЕЛЬЗЯ, хотя цена и снята: каждый гекс-исход
                // попадает в doneHexes и из меню уходит, поэтому выбор кончается
                // сам, когда кончились гексы с источниками.
                int surcharge = ctx.noSurcharge.contains("energy_swap")
                    ? 0 : ctx.nextOpSurcharge("energy_swap", schedule);
                // ТОЧКА ПРАВИЛ: карта арсенала может сделать второй гекс бесплатным
                // («Ваше второе перемещение энергии тоже бесплатно»).
                surcharge = (int) Math.round(kelium.engine.ability.RuleQuery
                    .of(state, player.seat, kelium.engine.ability.Hook.ENERGY_SWAP_COST)
                    .about(hexesDone + 1).base(surcharge).ask());
                List<Choice> opts = new ArrayList<>();
                // гексы-исходы: свои гексы, где стоит хоть один источник (ЭС/ЦУ)
                java.util.Set<String> srcHexes = new java.util.LinkedHashSet<>();
                for (BuildingToken b : player.buildingsOnField()) {
                    if (isSource(b) && !doneHexes.contains(b.hexId)) {
                        srcHexes.add(b.hexId);
                    }
                }
                for (String hid : srcHexes) {
                    opts.add(new Choice("energy_hex", hid,
                        "swap @" + hid + (surcharge > 0 ? " (+" + surcharge + " МОН)" : "")));
                }
                if (!storageDone && storageEnergyTokens(player) > 0) {
                    opts.add(new Choice("energy_storage", null, "кубик жетона хранилища"));
                }
                // ТОЧКА ПРАВИЛ: карта арсенала может САМА быть источником энергии
                // («Полевой генератор» даёт 1 кубик). Источник вне поля, наценки
                // за гекс не платит — как жетон хранилища.
                int cardEnergy = kelium.engine.ability.RuleQuery
                    .of(state, player.seat, kelium.engine.ability.Hook.ENERGY_SOURCES)
                    .base(0).ask();
                if (!cardSourceDone && cardEnergy > 0) {
                    opts.add(new Choice("energy_card", null,
                        "кубик карты арсенала (" + cardEnergy + ")"));
                }
                opts.add(new Choice("pass", null, "stop swapping"));
                if (opts.size() == 1) {
                    break;
                }
                Choice pick = agent.choose(state, opts, Map.of("kind", "energy_hex",
                    "surcharge", surcharge));
                if (pick.payload() == null && !"energy_storage".equals(pick.kind())
                        && !"energy_card".equals(pick.kind())) {
                    break;
                }
                if ("energy_card".equals(pick.kind())) {
                    // кубики карты арсенала: снять и разложить заново, без наценки
                    for (BuildingToken c : player.buildingsOnField()) {
                        c.stripEnergyOf(ARSENAL_CARD_SOURCE_UID);
                    }
                    placedTotal += placeCubes(player, agent, ARSENAL_CARD_SOURCE_UID,
                        cardEnergy, null);
                    cardSourceDone = true;
                    continue;
                }
                if ("energy_storage".equals(pick.kind())) {
                    // кубики жетонов хранилища: снять и разложить заново, без наценки
                    int pool = storageEnergyTokens(player);
                    for (BuildingToken c : player.buildingsOnField()) {
                        c.stripEnergyOf(STORAGE_SOURCE_UID);
                    }
                    placedTotal += placeCubes(player, agent, STORAGE_SOURCE_UID, pool, null);
                    storageDone = true;
                    continue;
                }
                String hid = (String) pick.payload();
                if (surcharge > 0) {
                    if (!player.resources.canPay(Resource.COIN, surcharge)) {
                        break;
                    }
                    player.resources.pay(Resource.COIN, surcharge);
                }
                // собрать кубики всех источников выбранного гекса
                for (BuildingToken src : player.buildingsOnField()) {
                    if (!isSource(src) || !hid.equals(src.hexId)) {
                        continue;
                    }
                    src.energyIdle = 0;
                    for (BuildingToken c : player.buildingsOnField()) {
                        c.stripEnergyOf(src.uid);
                    }
                    // Пул = выработка источника (+ пассив «+1 станциям»): модель
                    // самовосстанавливается при появлении/уходе пассивки. Сама
                    // выработка считается в Power.plantOutput — там же живёт
                    // правило жёлтой ячейки.
                    int pool = Power.plantOutput(state, src)
                        + (src.type == BuildingType.POWER_PLANT
                            ? Passives.plantEnergyBonus(state, player.seat) : 0);
                    placedTotal += placeCubes(player, agent, src.uid, pool, src);
                }
                doneHexes.add(hid);
                hexesDone++;
                journal(state).of(player.seat).energySwapSourceHexes.add(hid);
                ctx.recordOp("energy_swap");
            }
            if (hexesDone == 0 && placedTotal == 0) {
                return ActionResult.fail("energy swap: nothing to do");
            }
            ctx.actionsPlayed.add(name());
            Map<String, Object> tel = new HashMap<>();
            tel.put("energy_placed", placedTotal);
            tel.put("hexes", hexesDone);
            return ActionResult.ok("energy swap: " + placedTotal + " cubes over "
                + hexesDone + " hex(es)", tel);
        }

        /**
         * ПЕРЕКОММУТАЦИЯ ЦЕЛИКОМ: снять со стола ВСЮ свою энергию и разложить её
         * заново одним решением.
         *
         * <p>Возвращает {@code null}, если раскладывать нечего — тогда действие
         * идёт обычным пошаговым путём и честно сообщает «nothing to do».
         *
         * <p>ЧТО ТУТ ГЛАВНОЕ. Здание либо запитано целиком, либо не работает
         * вовсе: кубик, положенный в здание, которому нужно два, не даёт ничего.
         * Значит осмысленных раскладок немного — они отличаются тем, КОГО решили
         * не питать. Поэтому игрок выбирает не кубик за кубиком, а ПОРЯДОК
         * ВАЖНОСТИ, и раскладка достраивается по нему жадно: пока пула хватает
         * закрыть здание целиком — закрываем, не хватает — идём к следующему.
         * Для одинаковых кубиков это и есть оптимум по числу работающих зданий,
         * когда порядок «сначала дешёвые».
         *
         * <p>Последний вариант — «разложить самому» — возвращает игрока к
         * пошаговому выбору: живому человеку он нужен, боту нет.
         */
        private ActionResult rewireEverything(PlayerState player, TurnContext ctx, Agent agent) {
            // 1. СКОЛЬКО ВСЕГО ЭНЕРГИИ У ИГРОКА. Пул каждого источника считается
            // через Power.plantOutput — там живёт правило жёлтого сектора.
            java.util.Map<Integer, Integer> pools = new java.util.LinkedHashMap<>();
            for (BuildingToken src : player.buildingsOnField()) {
                if (!isSource(src)) {
                    continue;
                }
                pools.put(src.uid, Power.plantOutput(state, src)
                    + (src.type == BuildingType.POWER_PLANT
                        ? Passives.plantEnergyBonus(state, player.seat) : 0));
            }
            int storage = storageEnergyTokens(player);
            if (storage > 0) {
                pools.put(STORAGE_SOURCE_UID, storage);
            }
            int cardEnergy = kelium.engine.ability.RuleQuery
                .of(state, player.seat, kelium.engine.ability.Hook.ENERGY_SOURCES)
                .base(0).ask();
            if (cardEnergy > 0) {
                pools.put(ARSENAL_CARD_SOURCE_UID, cardEnergy);
            }
            int total = 0;
            for (int n : pools.values()) {
                total += n;
            }
            List<BuildingToken> consumers = new ArrayList<>();
            for (BuildingToken c : player.buildingsOnField()) {
                if (c.energySlots > 0) {
                    consumers.add(c);
                }
            }
            if (total == 0 || consumers.isEmpty()) {
                return null;
            }

            // 2. ЧЕТЫРЕ ПОРЯДКА ВАЖНОСТИ. Это не «варианты алгоритма», а разные
            // ответы на вопрос «что мне сейчас нужнее»: побольше работающих
            // зданий, добыча, войска или самое крупное производство.
            java.util.LinkedHashMap<String, java.util.Comparator<BuildingToken>> plans =
                new java.util.LinkedHashMap<>();
            plans.put("запитать как можно больше зданий",
                java.util.Comparator.comparingInt((BuildingToken c) -> c.energySlots));
            plans.put("сначала добыча",
                java.util.Comparator.comparingInt((BuildingToken c) ->
                    (c.type == BuildingType.MINER ? 0 : 1) * 100 + c.energySlots));
            plans.put("сначала военные здания",
                java.util.Comparator.comparingInt((BuildingToken c) ->
                    (ASSEMBLY_UNIT.containsKey(c.type) ? 0 : 1) * 100 + c.energySlots));
            plans.put("сначала крупные",
                java.util.Comparator.comparingInt((BuildingToken c) -> -c.energySlots));

            List<Choice> opts = new ArrayList<>();
            java.util.Map<String, List<Integer>> layouts = new java.util.LinkedHashMap<>();
            for (var e : plans.entrySet()) {
                List<BuildingToken> order = new ArrayList<>(consumers);
                order.sort(e.getValue());
                List<Integer> uids = new ArrayList<>();
                int left = total;
                for (BuildingToken c : order) {
                    if (c.energySlots <= left) {
                        uids.add(c.uid);
                        left -= c.energySlots;
                    }
                }
                // Одинаковые по результату раскладки в меню не дублируются: две
                // подписи на одно и то же — это выбор, которого нет.
                if (layouts.containsValue(uids)) {
                    continue;
                }
                layouts.put(e.getKey(), uids);
                opts.add(new Choice("energy_layout", e.getKey(),
                    e.getKey() + ": запитано " + uids.size() + " из " + consumers.size()));
            }
            opts.add(new Choice("energy_manual", null, "разложить кубики самому"));
            Choice pick = agent.choose(state, opts,
                Map.of("kind", "energy_layout", "cubes", total));
            if (pick == null || pick.payload() == null) {
                return null;            // «самому» — обычный пошаговый путь
            }
            List<Integer> chosen = layouts.get(String.valueOf(pick.payload()));
            if (chosen == null) {
                return null;
            }

            // 3. ПРИМЕНИТЬ ОДНИМ ДЕЙСТВИЕМ: снять всё и положить по раскладке.
            for (BuildingToken c : player.buildingsOnField()) {
                for (int srcUid : new ArrayList<>(c.energyBySource.keySet())) {
                    c.stripEnergyOf(srcUid);
                }
            }
            for (BuildingToken src : player.buildingsOnField()) {
                if (isSource(src)) {
                    src.energyIdle = 0;
                }
            }
            java.util.Iterator<java.util.Map.Entry<Integer, Integer>> src = pools.entrySet().iterator();
            java.util.Map.Entry<Integer, Integer> cur = src.hasNext() ? src.next() : null;
            int placed = 0;
            for (int uid : chosen) {
                BuildingToken c = null;
                for (BuildingToken t : player.buildingsOnField()) {
                    if (t.uid == uid) {
                        c = t;
                        break;
                    }
                }
                if (c == null) {
                    continue;
                }
                int need = c.energySlots;
                while (need > 0 && cur != null) {
                    if (cur.getValue() <= 0) {
                        cur = src.hasNext() ? src.next() : null;
                        continue;
                    }
                    int take = Math.min(need, cur.getValue());
                    c.addEnergyFrom(cur.getKey(), take);
                    cur.setValue(cur.getValue() - take);
                    need -= take;
                    placed += take;
                }
            }
            // ОСТАТОК ПРОСТАИВАЕТ НА СВОЁМ ИСТОЧНИКЕ — кубик из игры не исчезает.
            while (cur != null) {
                if (cur.getValue() > 0 && cur.getKey() >= 0) {
                    for (BuildingToken s : player.buildingsOnField()) {
                        if (s.uid == cur.getKey()) {
                            s.energyIdle = cur.getValue();
                            break;
                        }
                    }
                }
                cur = src.hasNext() ? src.next() : null;
            }

            // Все свои гексы-исходы пошли в дело — журнал должен видеть каждый:
            // на этом стоят задания про Смену энергии («Перекоммутация» o20).
            int hexes = 0;
            java.util.Set<String> hexIds = new java.util.LinkedHashSet<>();
            for (BuildingToken s : player.buildingsOnField()) {
                if (isSource(s) && s.hexId != null) {
                    hexIds.add(s.hexId);
                }
            }
            for (String hid : hexIds) {
                journal(state).of(player.seat).energySwapSourceHexes.add(hid);
                ctx.recordOp("energy_swap");
                hexes++;
            }
            ctx.actionsPlayed.add(name());
            Map<String, Object> tel = new HashMap<>();
            tel.put("energy_placed", placed);
            tel.put("hexes", hexes);
            tel.put("layout", String.valueOf(pick.payload()));
            tel.put("powered", chosen.size());
            return ActionResult.ok("перекоммутация целиком: " + placed + " кубиков, "
                + chosen.size() + " зданий запитано (" + pick.payload() + ")", tel);
        }

        /**
         * Разложить pool кубиков источника srcUid: по одному, выбором игрока —
         * на любое своё здание со свободной ячейкой или оставить простаивать
         * на источнике (src == null для кубика хранилища — «простаивать» =
         * лежать на жетоне, кубик просто не размещается).
         */
        private int placeCubes(PlayerState player, Agent agent, int srcUid, int pool,
                               BuildingToken src) {
            int placed = 0;
            for (int i = 0; i < pool; i++) {
                List<Choice> opts = new ArrayList<>();
                for (BuildingToken c : player.buildingsOnField()) {
                    if (c.energySlots > c.energyPlaced) {
                        opts.add(new Choice("energy_place", c.uid,
                            c.type.code + (c.level != null ? " L" + c.level : "") + " @" + c.hexId));
                    }
                }
                opts.add(new Choice("pass", null, "оставить простаивать"));
                Choice pick = opts.size() == 1 ? opts.get(0)
                    : agent.choose(state, opts, Map.of("kind", "energy_place",
                        "remaining", pool - i));
                if (pick.payload() == null) {
                    if (src != null) {
                        src.energyIdle += pool - i;
                    }
                    break;
                }
                int cuid = ((Number) pick.payload()).intValue();
                for (BuildingToken c : player.buildingsOnField()) {
                    if (c.uid == cuid) {
                        c.addEnergyFrom(srcUid, 1);
                        journal(state).of(player.seat).energySwapSources.add(c.uid);
                        // o20: «каждый снятый кубик встал в ДРУГОЙ гекс» — кубик,
                        // легший в гекс своего источника, ломает условие.
                        if (src != null && src.hexId != null && src.hexId.equals(c.hexId)) {
                            journal(state).of(player.seat).energySwapSameHexCube = true;
                        }
                        placed++;
                        break;
                    }
                }
            }
            return placed;
        }

        /** Источник энергии: энергостанция или ЦУ. */
        static boolean isSource(BuildingToken b) {
            return b.type == BuildingType.POWER_PLANT || b.type == BuildingType.COMMAND_CENTER;
        }

        /** Сколько «вечных кубиков» дают жетоны хранилища игрока. */
        private static int storageEnergyTokens(PlayerState player) {
            int n = 0;
            for (String tok : player.storageTokens) {
                if ("+1_energy".equals(tok)) {
                    n++;
                }
            }
            return n;
        }

        /** Синтетический uid источника «жетон хранилища». */
        static final int STORAGE_SOURCE_UID = -1;
    }

    // ======================================================================
    //  OPERATION: movement, combat
    // ======================================================================

    /** Действие Движение (приказ Операция): перемещение юнитов по смежным гексам. */
    static final class MovementAction extends Action {
        MovementAction(GameState state) {
            super(state);
        }

        @Override public String name() { return "movement"; }
        @Override public Order order() { return Order.OPERATION; }
        @Override public boolean implemented() { return true; }

        @Override
        @SuppressWarnings("unchecked")
        public ActionResult perform(PlayerState player, TurnContext ctx, Agent agent) {
            GameState s = state;
            var side = player.board.troop;
            Integer aircraftSpeed = Passives.aircraftSpeedOverride(s, player.seat);
            int freeExtra = 0;
            if (Passives.firstTwoMovesFree(s, player.seat)) {
                freeExtra = 2;
            } else if (Passives.firstExtraMoveFree(s, player.seat)) {
                freeExtra = 1;
            }
            int movesDone = 0;
            boolean freeUsed = false;
            Map<Integer, Integer> perUnitSteps = new HashMap<>();
            while (true) {
                List<Choice> opts = new ArrayList<>();
                for (UnitToken u : player.unitsOnField()) {
                    // скорость спрашиваем в одном месте: карты и жетоны модулей
                    // вмешиваются через точку правил UNIT_SPEED (13.08.2026)
                    int speed = Speed.of(s, player.seat, u);
                    if (perUnitSteps.getOrDefault(u.uid, 0) >= speed) {
                        continue;
                    }
                    // ПРЫЖОК ЧЕРЕЗ ТАЙЛ ЗАРОЖДЕНИЯ (точка правил MOVEMENT_JUMP_OVER,
                    // карта «Десантные тропы»): гекс с тайлом нельзя занять, но
                    // пехота может перескочить его на противоположный гекс. Один
                    // прыжок стоит один шаг скорости, как обычное перемещение.
                    boolean canJump = kelium.engine.ability.RuleQuery
                        .of(s, player.seat, kelium.engine.ability.Hook.MOVEMENT_JUMP_OVER)
                        .about(u).base(0).ask() >= 1.0;
                    if (canJump) {
                        for (String over : s.field.neighbors(u.hexId)) {
                            if (!s.field.get(over).hasSpawnTile()) {
                                continue;
                            }
                            for (String behind : s.field.neighbors(over)) {
                                if (!behind.equals(u.hexId) && canEnter(u, behind, player.seat)) {
                                    opts.add(new Choice("move",
                                        Map.of("uid", u.uid, "to", behind),
                                        u.type.code + " ПРЫЖОК через " + over + "->" + behind));
                                }
                            }
                        }
                    }
                    for (String nb : s.field.neighbors(u.hexId)) {
                        if (canEnter(u, nb, player.seat)) {
                            opts.add(new Choice("move", Map.of("uid", u.uid, "to", nb),
                                u.type.code + "->" + nb));
                        }
                    }
                }
                if (opts.isEmpty()) {
                    break;
                }
                opts.add(new Choice("pass", null, "stop moving"));
                Choice pick = agent.choose(s, opts, Map.of("kind", "move"));
                if (pick.payload() == null) {
                    break;
                }
                int cost = 0;
                if (freeUsed) {
                    if (freeExtra > 0) {
                        freeExtra -= 1;
                        cost = 0;
                    } else if ("flat".equals(rs.getStr("actions.movement.cost_model", "flat"))) {
                        cost = rs.getInt("actions.movement.flat_ammo_per_extra_move");
                    } else {
                        List<Integer> sched = rs.getIntList("actions.movement.escalating_surcharge_ammo");
                        cost = sched.get(Math.min(movesDone, sched.size() - 1));
                    }
                }
                if (cost > 0 && !player.resources.canPay(Resource.AMMO, cost)) {
                    break;
                }
                if (cost > 0) {
                    player.resources.pay(Resource.AMMO, cost);
                }
                Map<String, Object> mp = (Map<String, Object>) pick.payload();
                int uid = ((Number) mp.get("uid")).intValue();
                UnitToken unit = null;
                for (UnitToken u : player.units) {
                    if (u.uid == uid) {
                        unit = u;
                        break;
                    }
                }
                String dest = (String) mp.get("to");
                String fromHex = unit.hexId;
                // Признак «был гарнизоном» снимаем ДО хода: смена гекса выводит
                // войско из здания, и после setHexId узнать это уже нельзя.
                boolean wasInside = unit.inside();
                // setHexId, а не присваивание: смена гекса ВЫВОДИТ войско из
                // здания, внутри которого оно стояло.
                unit.setHexId(dest);
                // ПЕЧАТНЫЙ КОНТЕЙНЕР: войско встало на ячейку — берёт карту.
                // o30 «Мародёр» считает такие контейнеры (учёт внутри).
                PrintedContainers.onUnitMoved(s, player, fromHex, dest, unit.type, wasInside);
                // СТАРЫЙ РЕЖИМ: жетон контейнера на гексе подбирает войско
                // (ruleset 1.6.0-c1; в основном режиме вызов ничего не делает).
                TokenContainers.onUnitEntered(s, player, dest);
                perUnitSteps.merge(uid, 1, Integer::sum);
                freeUsed = true;
                movesDone += 1;
                TurnJournal.TurnFacts f = journal(s).of(player.seat);
                f.movedUids.add(uid);
                f.unitsMoved = f.movedUids.size();
                f.movedFromHexes.add(fromHex);
            }
            ctx.recordOp("movement");
            ctx.actionsPlayed.add(name());
            Map<String, Object> tel = new HashMap<>();
            tel.put("moves", movesDone);
            return ActionResult.ok("moved " + movesDone + " steps", tel);
        }

        private boolean canEnter(UnitToken unit, String hexId, int seat) {
            return canEnterHex(state, unit, hexId, seat);
        }

        /**
         * Может ли юнит войти на гекс (правила проходимости движения). Публичный
         * статический — чтобы переиспользовать в плашке манёвра (СПЕЦ-перемещение)
         * без дублирования правил.
         */
        static boolean canEnterHex(GameState state, UnitToken unit, String hexId, int seat) {
            Hex h = state.field.get(hexId);
            if (h.kind == HexKind.FORBIDDEN) {
                return false;
            }
            boolean isAir = unit.type == UnitType.AIRCRAFT;
            if (isAir) {
                // тайл зарождения занимает и воздушную ячейку: пролетать можно,
                // ОСТАНАВЛИВАТЬСЯ нельзя (проверяется только пункт назначения)
                if (h.hasSpawnTile()) {
                    return Passives.aircraftSpeedOverride(state, seat) != null;
                }
                // G3: воздушная ячейка гекса одна — В НЕЙ один жетон авиации
                // (любого игрока). Считаем вживую, поле airToken упразднено.
                for (PlayerState pl : state.players) {
                    for (UnitToken u : pl.units) {
                        if (u.type == UnitType.AIRCRAFT && u.uid != unit.uid
                                && hexId.equals(u.hexId)) {
                            return false;
                        }
                    }
                }
                return true;
            }
            if (h.hasSpawnTile()) {
                // ТОЧКА ПРАВИЛ: карта арсенала «Десантные тропы» разрешает пехоте
                // ПЕРЕПРЫГИВАТЬ гекс с тайлом зарождения. Остановиться на нём
                // по-прежнему нельзя — тайл занимает все наземные ячейки, — но
                // тайл перестаёт быть стеной для маршрута.
                return false;
            }
            // §12.3 (решение дизайнера): блокировка ПО-СТОРОННЯЯ, не по-гексовая.
            // Здание/нейтрал закрывает проход только СВОЕЙ СТЕНКОЙ (занятыми
            // сторонами); гекс с чужим зданием или нейтралом проходим, пока в
            // нём есть свободная наземная ячейка.
            // Само правило живёт в Passability — им же пользуется Бой. Второй
            // копии правила быть не должно: пока Бой считал соседей сам, он стрелял
            // сквозь стенки (пойманo дизайнером в проигрывателе).
            if (!Passability.groundEdgeOpen(state, unit.hexId, hexId, seat)) {
                return false;
            }
            // УМНАЯ проверка стоянки: войска НЕ приколочены к ячейкам — считаем,
            // влезет ли новичок, если стоящие переупакуются (правило дизайнера).
            // Технике нужна пара СМЕЖНЫХ ячеек; жёсткие только здания/нейтралы.
            int[] load = groundLoad(state, hexId, unit.uid);
            return h.fitsWithRepack(unit.type == UnitType.VEHICLE ? 2 : 1,
                load[0], load[1]);
        }

    }

    /** Действие Бой (приказ Операция): проведение одной битвы через CombatResolver. */
    static final class CombatAction extends Action {
        CombatAction(GameState state) {
            super(state);
        }

        @Override public String name() { return "combat"; }
        @Override public Order order() { return Order.OPERATION; }
        @Override public boolean implemented() { return true; }

        @Override
        public ActionResult perform(PlayerState player, TurnContext ctx, Agent agent) {
            // C1: за одно действие Бой можно провести НЕСКОЛЬКО боёв; второй и
            // далее — с наценкой (open_battle_surcharge_ammo), платится ДО боя
            // (C2). Телеметрия battlesOpened — только по состоявшимся (C3).
            List<Integer> schedule = rs.getIntList("actions.combat.open_battle_surcharge_ammo");
            CombatResolver resolver = (CombatResolver) state.combat;
            // Было ли вообще кого бить В МОМЕНТ РОЗЫГРЫША — по этому признаку
            // отчёты отличают «бой не состоялся, потому что некого» от
            // «бой был возможен, но бот его не провёл».
            boolean couldFight = resolver.anyAttackPossible(player.seat);
            int killsBefore = player.killsTotal;
            int battles = 0;
            while (true) {
                int surcharge = ctx.nextOpSurcharge("combat", schedule);
                if (Passives.noSecondBattleSurcharge(state, player.seat)) {
                    surcharge = 0;
                }
                // ТОЧКА ПРАВИЛ: надбавка за второй и следующий бой в ход. База —
                // расписание из правил (уже со скидкой легаси-пассивки), карта
                // арсенала может снять её совсем.
                surcharge = kelium.engine.ability.RuleQuery
                    .of(state, player.seat,
                        kelium.engine.ability.Hook.COMBAT_SECOND_BATTLE_SURCHARGE)
                    .about(battles + 1).base(surcharge).ask();
                if (surcharge > 0 && !player.resources.canPay(Resource.AMMO, surcharge)) {
                    break;
                }
                if (surcharge > 0) {
                    player.resources.pay(Resource.AMMO, surcharge);   // плата за ПРАВО боя
                }
                boolean did = resolver.runBattle(player.seat, agent);
                if (!did) {
                    // бой не состоялся (пас/нет целей): вернуть наценку — право
                    // не было использовано.
                    //
                    // ВОЗВРАТ ИДЁТ ЧЕРЕЗ СКЛАД. Казалось бы, возвращаем своё же и
                    // переполнить не можем — но между платой и возвратом успевает
                    // пройти бой, а в бою сносят здания: закрылись ячейки, и
                    // прежнее количество боеприпасов уже не помещается. Поймано
                    // сторожем StorageNeverOverflowsTest на одной партии из девяти.
                    if (surcharge > 0) {
                        Storage.addAmmoCapped(state, player, surcharge);
                    }
                    break;
                }
                battles++;
                journal(state).of(player.seat).battlesOpened += 1;
                ctx.recordOp("combat");
                if (state.finished) {
                    break;
                }
            }
            // «Премия за голову» (арсенал 2.0.0): конец Боя, есть хоть одно
            // уничтожение за это действие — 1 монета (один раз, не за кадого
            // жетона). killsTotal только растёт, поэтому дельта — честный счёт
            // уничтожений именно в этом действии.
            if (player.killsTotal > killsBefore
                    && Passives.hasPassive(state, player.seat, "coin_on_kill")) {
                player.resources.add(Resource.COIN, 1);
            }
            ctx.actionsPlayed.add(name());
            Map<String, Object> tel = new HashMap<>();
            tel.put("battle", battles);
            tel.put("could_fight", couldFight);
            if (battles == 0) {
                return ActionResult.ok(couldFight
                    ? "combat: не стал бить (цели были)" : "combat: бить некого", tel);
            }
            return ActionResult.ok("combat resolved x" + battles, tel);
        }
    }

    // ======================================================================
    //  ACQUISITIONS: market, science
    // ======================================================================

    /** Действие Маркет (приказ Приобретения): обмен келемия по карте/ставке. */
    /**
     * Действие Маркет (приказ Приобретения).
     *
     * <p><b>Правила (дизайнер, 2026-08-12).</b> На планшете маркета напечатаны
     * ПОСТОЯННЫЕ обмены — каждый отдаёт 1 келемий и даёт:
     * <ul>
     *   <li>3 монеты;</li>
     *   <li>2 боеприпаса;</li>
     *   <li>2 карты задания;</li>
     *   <li>кубик келемия НАВСЕГДА встаёт вместо энергии в ячейку своего здания.</li>
     * </ul>
     * Любым из них можно пользоваться <b>сколько угодно раз за одно действие</b>,
     * пока есть келемий. А вот <b>уникальным предложением с КАРТЫ</b> маркета —
     * только <b>один раз за действие</b>.
     *
     * <p>Раньше здесь была одна-единственная сделка за действие и предлагался
     * ровно один печатный обмен (на монеты) — остальных трёх не существовало.
     */
    static final class MarketAction extends Action {

        /** Источник «кубика с маркета»: он не двигается и в игру не возвращается. */
        static final int MARKET_KELIUM_UID = -2;

        /**
         * Номер СВОБОДНОЙ ячейки предложения карты рынка, либо −1, если свободных
         * нет. Ячеек две; ВТОРАЯ открыта ТОЛЬКО ПРИ ЧЕТЫРЁХ ИГРОКАХ (на карте
         * помечена «4») — именно это и показано в проигрывателе на планшете рынка.
         *
         * <p>Почему не «3+», как раньше: предложение — дефицитный ресурс стола, и
         * при трёх игроках две ячейки на два предложения означают, что хватает
         * почти всем. Один и тот же рынок должен поджимать одинаково при любом
         * числе игроков, а на четверых без второй ячейки последний по кругу не
         * доходил до рынка вообще.
         */
        static int freeMarketCell(GameState s, String side) {
            int[] cells = s.marketCells["right".equals(side) ? 1 : 0];
            int open = Math.min(cells.length, marketCellsOpen(s.numPlayers()));
            for (int i = 0; i < open; i++) {
                if (cells[i] < 0) {
                    return i;
                }
            }
            return -1;
        }

        MarketAction(GameState state) {
            super(state);
        }

        @Override public String name() { return "market"; }
        @Override public Order order() { return Order.ACQUISITIONS; }
        @Override public boolean implemented() { return true; }

        @Override
        @SuppressWarnings("unchecked")
        public ActionResult perform(PlayerState player, TurnContext ctx, Agent agent) {
            GameState s = state;
            GameConfig cfg = Ctx.cfg(s);
            ctx.actionsPlayed.add(name());
            if (player.resources.kelium() < 1) {
                return ActionResult.ok("market: no kelium");
            }
            TurnJournal.TurnFacts f = journal(s).of(player.seat);

            int coinRate = intRate(cfg, "kelium_to_coin", "per_kelium_coin", 3);
            int ammoRate = intRate(cfg, "kelium_to_ammo", "per_kelium_ammo", 2);
            int cardRate = intRate(cfg, "kelium_to_objective", "per_kelium_cards", 2);

            boolean cardOfferUsed = false;
            int deals = 0;
            int keliumSpent = 0;
            int coinGot = 0;
            int ammoGot = 0;
            int cardsGot = 0;
            int energyPlaced = 0;
            // ЧТО ИМЕННО брали на планшете рынка: обмен → сколько раз. Общее
            // «сделок N» не отвечает на вопрос, какими из четырёх печатных
            // обменов боты вообще пользуются, а это балансовый вопрос.
            Map<String, Integer> dealUses = new HashMap<>();
            // Какие стороны карт рынка брали за это действие (для лаборатории карт).
            List<String> offerSides = new ArrayList<>();
            // КАКИЕ УТИЛЬ-ЭФФЕКТЫ СРАБОТАЛИ с карты рынка. Рынок был
            // единственным из четырёх источников разовых эффектов, кто о них
            // молчал, — и метрики утиля из-за этого считались неполными.
            List<String> offerEffects = new ArrayList<>();
            List<Map<String, Object>> offerResults = new ArrayList<>();
            StringBuilder detail = new StringBuilder();

            // СКИДКА ЗА ПАРУ: келемий, сданный сразу парой, идёт по лучшему курсу
            // (правило дизайнера 13.08.2026). Величина — из правил, не из кода.
            int pairBonus = ((Number) rs.get("market.pair_bonus_coin", 0)).intValue();

            while (player.resources.kelium() >= 1) {
                List<Choice> opts = new ArrayList<>();
                // ---- постоянные обмены: доступны СКОЛЬКО УГОДНО раз ----
                opts.add(new Choice("market_rate", rate("coin", coinRate),
                    "1 КЕЛ -> " + coinRate + " МОН"));
                if (pairBonus > 0 && player.resources.kelium() >= 2) {
                    Map<String, Object> pair = rate("coin", 2 * coinRate + pairBonus);
                    pair.put("kelium", 2);
                    opts.add(new Choice("market_rate", pair,
                        "2 КЕЛ разом -> " + (2 * coinRate + pairBonus) + " МОН"));
                }
                if (Storage.ammoMax(state, player) > player.resources.ammo()) {
                    opts.add(new Choice("market_rate", rate("ammo", ammoRate),
                        "1 КЕЛ -> " + ammoRate + " БПР"));
                }
                opts.add(new Choice("market_rate", rate("objective_cards", cardRate),
                    "1 КЕЛ -> " + cardRate + " карты задания"));
                BuildingToken needsEnergy = firstHungryBuilding(player);
                if (needsEnergy != null) {
                    opts.add(new Choice("market_rate", rate("energy", 1),
                        "1 КЕЛ -> кубик НАВСЕГДА в ячейку " + needsEnergy.type.code));
                }
                // ---- предложение КАРТЫ: только один раз за действие ----
                String active = s.marketActive;
                // ОБЕ ПОЛОВИНЫ КАРТЫ РЫНКА (утиль «Двойная сделка», 21.08.2026):
                // обычное правило — не больше ОДНОГО предложения с карты за
                // действие; надбавка утиля снимает именно это ограничение, а
                // ячейки предложения по-прежнему расходуются.
                if ((!cardOfferUsed || ctx.marketBothOffers) && active != null) {
                    Map<String, Object> card = cfg.content.get("market").find(active);
                    if (card != null) {
                        for (String side : new String[]{"left", "right"}) {
                            // ЯЧЕЙКИ ПРЕДЛОЖЕНИЯ РАСХОДУЮТСЯ (правило с карты
                            // рынка, показано в проигрывателе 12.08.2026): у
                            // предложения две ячейки, вторая открыта только при
                            // 3–4 игроках. Все ячейки заняты — предложение
                            // недоступно, как и за столом.
                            if (freeMarketCell(s, side) < 0) {
                                continue;
                            }
                            // КАЖДАЯ ПОЛОВИНА — ПО ОДНОМУ РАЗУ. «Обе половины»
                            // значит левую и правую, а не одну и ту же дважды:
                            // на четверых у предложения две ячейки, и без этой
                            // проверки карта позволяла бы взять одно и то же
                            // предложение два раза.
                            if (offerSides.contains(side)) {
                                continue;
                            }
                            if (card.get(side) instanceof Map<?, ?> off) {
                                Map<String, Object> pl = new HashMap<>();
                                pl.put("card", active);
                                pl.put("side", side);
                                pl.put("offer", off);
                                opts.add(new Choice("market_offer", pl,
                                    card.getOrDefault("name", "") + ": "
                                        + ((Map<String, Object>) off).getOrDefault("label", "")));
                            }
                        }
                    }
                }
                opts.add(new Choice("pass", null, "хватит торговать"));

                Choice pick = agent.choose(s, opts, Map.of("kind", "market",
                    "deals_done", deals));
                if (pick.payload() == null) {
                    break;
                }
                // СКОЛЬКО КЕЛЕМИЯ СТОИТ СДЕЛКА берём из самой сделки: обычная —
                // один кубик, парная — два (см. скидку за пару).
                int keliumCost = 1;
                if (pick.payload() instanceof Map<?, ?> pm
                        && pm.get("kelium") instanceof Number kn) {
                    keliumCost = Math.max(1, kn.intValue());
                }
                player.resources.pay(Resource.KELIUM, keliumCost);
                keliumSpent += keliumCost;
                deals++;
                f.usedMarket = true;

                if ("market_rate".equals(pick.kind())) {
                    f.usedMarketPrintedRate = true;
                    Map<String, Object> pl = (Map<String, Object>) pick.payload();
                    String what = String.valueOf(pl.get("what"));
                    int amount = ((Number) pl.get("amount")).intValue();
                    // СЧЁТЧИК ПО КАЖДОЙ ПЕЧАТНОЙ СДЕЛКЕ ОТДЕЛЬНО: сколько раз
                    // взяли именно этот обмен. Общее «сделок N» не отвечает на
                    // вопрос, какими из четырёх обменов боты вообще пользуются.
                    dealUses.merge(what, 1, Integer::sum);
                    // o33 «Биржа»: РАЗНЫЕ предложения планшета маркета. Каждый
                    // напечатанный курс — своё предложение, повтор одного и того
                    // же курса второй раз не засчитывается.
                    f.marketOffersUsed.add("printed:" + what);
                    switch (what) {
                        case "coin" -> {
                            player.resources.add(Resource.COIN, amount);
                            coinGot += amount;
                            detail.append(amount).append(" МОН; ");
                        }
                        case "ammo" -> {
                            int added = Storage.addAmmoCapped(state, player, amount);
                            ammoGot += added;
                            detail.append(added).append(" БПР; ");
                        }
                        case "objective_cards" -> {
                            int drawn = 0;
                            for (int i = 0; i < amount; i++) {
                                String c = s.decks.get("objectives").draw(s.rng);
                                if (c == null) {
                                    break;
                                }
                                player.objectiveHand.add(c);
                                drawn++;
                            }
                            cardsGot += drawn;
                            detail.append(drawn).append(" карт; ");
                        }
                        default -> {
                            // КЕЛЕМИЙ НАВСЕГДА В ЯЧЕЙКУ ЭНЕРГИИ. Кубик встаёт от
                            // особого «источника», которого нет на поле: Смена
                            // энергии его не снимет, при сносе он не вернётся —
                            // покупка окончательная, ПО за него не начисляются.
                            BuildingToken tgt = firstHungryBuilding(player);
                            if (tgt == null) {
                                player.resources.add(Resource.KELIUM, 1);   // откат
                                keliumSpent--;
                                deals--;
                                continue;
                            }
                            tgt.addEnergyFrom(MARKET_KELIUM_UID, 1);
                            energyPlaced++;
                            detail.append("кубик в ").append(tgt.type.code).append("; ");
                        }
                    }
                    continue;
                }

                // ---- уникальное предложение карты ----
                cardOfferUsed = true;
                f.usedMarketCardOffer = true;   // n4 «Первая сделка»
                Map<String, Object> pl = (Map<String, Object>) pick.payload();
                f.marketOffersUsed.add("card:" + pl.get("card") + ":" + pl.get("side"));
                // КАКУЮ СТОРОНУ карты взяли — левую или правую. Нужно лаборатории
                // карт: без этого нельзя сказать, какая половина карты мёртвая.
                offerSides.add(String.valueOf(pl.get("side")));
                Map<String, Object> offer = (Map<String, Object>) pl.get("offer");
                // КУБИК КЕЛЕМИЯ ЛОЖИТСЯ В ЯЧЕЙКУ предложения: он и есть плата за
                // ячейку (келемий за сделку уже списан выше), и по нему за столом
                // видно, кто предложение занял.
                int cell = freeMarketCell(s, String.valueOf(pl.get("side")));
                if (cell >= 0) {
                    s.marketCells["right".equals(pl.get("side")) ? 1 : 0][cell] = player.seat;
                }
                Map<String, Object> gotOffer;
                try {
                    gotOffer = Effects.apply((String) offer.get("effect"), s, player.seat,
                        (Map<String, Object>) offer.getOrDefault("params", Map.of()));
                } catch (Effects.EffectError e) {
                    // предложение неприменимо — келемий уже уплачен, как за столом
                    gotOffer = Map.of("failed", true);
                }
                // МЕТРИКИ УТИЛЯ: рынок — четвёртый источник разовых эффектов, и он
                // единственный, кто о них молчал. Без этого события «сколько раз
                // сработал такой-то эффект за партию» считается неверно.
                offerEffects.add(String.valueOf(offer.get("effect")));
                // РЕЗУЛЬТАТ эффекта тоже в телеметрию: без него нельзя отличить
                // сработавший эффект от отработавшего вхолостую, а именно это и
                // есть главный вопрос к утилю.
                offerResults.add(gotOffer == null ? Map.of() : gotOffer);
                detail.append(offer.getOrDefault("label", "предложение карты")).append("; ");
            }

            if (deals == 0) {
                return ActionResult.ok("market: no trade");
            }
            Map<String, Object> tel = new HashMap<>();
            tel.put("kelium_spent", keliumSpent);
            tel.put("deals", deals);
            tel.put("coin", coinGot);
            tel.put("ammo", ammoGot);
            tel.put("objective_cards", cardsGot);
            tel.put("energy_bought", energyPlaced);
            // Воспользовался ли игрок УНИКАЛЬНЫМ предложением с карты рынка —
            // отдельным полем: печатные сделки и предложение карты это разные
            // вещи, и мерить их надо порознь.
            tel.put("card_offer", cardOfferUsed);
            if (!offerEffects.isEmpty()) {
                tel.put("offer_effect", offerEffects.get(0));
                tel.put("offer_effects", String.join(",", offerEffects));
                tel.put("offer_got", offerResults.get(0));
            }
            if (!offerSides.isEmpty()) {
                tel.put("offer_side", offerSides.get(0));
                tel.put("offer_sides", String.join(",", offerSides));
            }
            for (Map.Entry<String, Integer> e : dealUses.entrySet()) {
                tel.put("deal_" + e.getKey(), e.getValue());
            }
            return ActionResult.ok("market: " + detail.toString().trim(), tel);
        }

        private static Map<String, Object> rate(String what, int amount) {
            Map<String, Object> m = new HashMap<>();
            m.put("what", what);
            m.put("amount", amount);
            m.put("kelium", 1);
            return m;
        }

        /** Первое своё здание со свободной ячейкой энергии (или null). */
        private BuildingToken firstHungryBuilding(PlayerState player) {
            for (BuildingToken b : player.buildingsOnField()) {
                if (b.energySlots > b.energyPlaced) {
                    return b;
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private static int intRate(GameConfig cfg, String id, String key, int def) {
            Object raw = cfg.ruleset.get("market.base_exchanges", null);
            if (!(raw instanceof List<?> list)) {
                return def;
            }
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && id.equals(m.get("id"))
                        && m.get(key) instanceof Number n) {
                    return n.intValue();
                }
            }
            return def;
        }
    }

    static final class ScienceAction extends Action {
        ScienceAction(GameState state) {
            super(state);
        }

        @Override public String name() { return "science"; }
        @Override public Order order() { return Order.ACQUISITIONS; }
        @Override public boolean implemented() { return true; }

        @Override
        public ActionResult perform(PlayerState player, TurnContext ctx, Agent agent) {
            // Какой обмен взят — в телеметрию: без этого нельзя ответить на вопрос
            // «пользуются ли боты вечными обменами науки», а он балансовый.
            // ОБМЕНОВ ЗА ОДНО ДЕЙСТВИЕ МОЖНО НЕСКОЛЬКО, включая повтор ОДНОГО И
            // ТОГО ЖЕ печатного обмена (уточнение 2026-08-15) — ограничен только
            // пулом трофеев/обломков, не количеством использований.
            List<String> usedExchanges = new ArrayList<>();
            String exchange = null;
            while (true) {
                String got = maybeExchange(player, agent);
                if (got == null) {
                    break;
                }
                usedExchanges.add(got);
                // Вечный курс — такое же ОТДЕЛЬНОЕ предложение планшета, как шаг
                // по треку (o34 «Научный отдел»).
                journal(state).of(player.seat).scienceOffersUsed.add("rate:" + got);
                exchange = String.join("+", usedExchanges);
            }
            List<Integer> costs = rs.getIntList("tech.step_cost_trophy");
            var tech = state.tech;
            List<Integer> caps = rs.stepCapacity(state.numPlayers());
            // ТРЕК ВЫБИРАЕТ ИГРОК, а сколько РАЗНЫХ треков он успеет за одно
            // действие — правило свода. Ключ tech.tracks_per_action: 1 значит
            // «одно действие Науки — один трек» (решение дизайнера 21.08.2026
            // ради баланса: за одно действие нельзя разложить трофеи сразу по
            // всем трекам). Ключа нет — работает как раньше, по одному шагу на
            // каждом треке, поэтому старые своды читаются без правок.
            int tracksAllowed = rs.getInt("tech.tracks_per_action", tech.tracks.size());
            java.util.Set<String> steppedTracks = new java.util.HashSet<>();
            int stepsMade = 0;
            int spentTotal = 0;
            StringBuilder detail = new StringBuilder();
            while (steppedTracks.size() < tracksAllowed) {
                // ЧЕМ ПЛАТИМ, ТЕМ И СЧИТАЕМ КАРМАН (см. payTrophy): при
                // tech.pay_with_debris_only жетоны в оплату не идут, значит и
                // предлагать шаги «по карману из жетонов» нельзя — иначе игрок
                // увидел бы вариант, который не может оплатить.
                int pool = сколькоМожемЗаплатить(player);
                List<Choice> opts = new ArrayList<>();
                for (String track : tech.tracks) {
                    if (steppedTracks.contains(track)) {
                        continue;
                    }
                    int step = player.techSteps.getOrDefault(track, 0);
                    if (step >= tech.steps) {
                        continue;
                    }
                    // ПЕРЕПРЫГИВАНИЕ ЯЧЕЕК (уточнение дизайнера 13.08.2026): игрок
                    // может уйти не на следующую ячейку, а ДАЛЬШЕ, перескочив через
                    // занятые впереди. Бонусы перепрыгнутых ячеек НЕ достаются —
                    // только бонус той, куда встал. Победные очки в конце партии
                    // считаются за ВСЕ пройденные шаги, поэтому прыжок не обкрадывает
                    // игрока по очкам. Смысл: если впереди столпились соперники,
                    // можно накопить трофеи и перегнать их всех разом.
                    int paid = 0;
                    for (int to = step + 1; to <= tech.steps; to++) {
                        paid += sciStepCost(player, costs, to - 1, stepsMade);
                        Integer cap = caps.get(to - 1);
                        boolean full = cap != null
                            && tech.occupancy.get(track).get(to - 1).size() >= cap;
                        if (pool < paid) {
                            break;      // дальше уже не по карману
                        }
                        if (full) {
                            continue;   // ячейка занята — её можно только перепрыгнуть
                        }
                        opts.add(new Choice("sci_track", new Object[]{track, to},
                            track + " -> шаг " + to + " (цена " + paid
                                + (to > step + 1 ? ", прыжок через " + (to - step - 1) + ")" : ")")));
                    }
                    continue;
                }
                if (opts.isEmpty()) {
                    break;
                }
                opts.add(new Choice("pass", null, "stop science"));
                Choice ch = agent.choose(state, opts, Map.of("kind", "sci_track"));
                if (ch.payload() == null) {
                    break;
                }
                Object[] pick = (Object[]) ch.payload();
                String track = (String) pick[0];
                int target = (Integer) pick[1];
                int step = player.techSteps.getOrDefault(track, 0);
                // Платится за КАЖДЫЙ пройденный шаг, включая перепрыгнутые: прыжок
                // не дешевле обычного пути, он лишь позволяет не застревать за
                // спинами соперников.
                int cost = 0;
                for (int to = step + 1; to <= target; to++) {
                    cost += sciStepCost(player, costs, to - 1, stepsMade);
                }
                // o39 «Сдача»: считаем ЕДИНИЦЫ ОПЛАТЫ, ушедшие в науку.
                //
                // ПОЧЕМУ НЕ РАЗМЕР ТРОФЕЙНОГО МЕСТА, как было до 25.08.2026.
                // Тот замер работал, только пока наука брала целые жетоны. С
                // ключом tech.pay_with_debris_only жетоны в оплату не идут,
                // трофейное место не уменьшается — и счётчик застыл на нуле,
                // сделав условие o39 невыполнимым. Платёж знает сам payTrophy,
                // поэтому он и возвращает уплаченное.
                int paid = payTrophy(player, cost, agent);
                TurnJournal.TurnFacts sf = journal(state).of(player.seat);
                sf.sciencePaidUnits += paid;
                sf.scienceTracksUsed.add(track);
                sf.scienceOffersUsed.add("track:" + track);
                player.techSteps.put(track, target);
                // КУБИК ПЕРЕСТАВЛЯЕТСЯ, а не ставится новый: игрок освобождает
                // прежний шаг (уточнение дизайнера 12.08.2026). Раньше он оставался
                // во всех пройденных ячейках, и шаги «забивались» им же одним.
                tech.moveCube(track, player.seat, step, target);
                // Награда — ТОЛЬКО за ячейку, куда встал: бонусы перепрыгнутых
                // ячеек не достаются никому.
                techStepReward(player, track, target, agent);
                steppedTracks.add(track);
                stepsMade++;
                spentTotal += cost;
                detail.append(track).append("->").append(target).append(' ');
            }
            ctx.actionsPlayed.add(name());
            Map<String, Object> tel = new HashMap<>();
            tel.put("trophy_spent", spentTotal);
            tel.put("steps", stepsMade);
            if (exchange != null) {
                tel.put("exchange", exchange);
            }
            // ПО КАКИМ ТРЕКАМ шагнули — отдельно: «шагов N» не говорит, ходят ли
            // боты по всем трём трекам или топчут один.
            for (String track : steppedTracks) {
                tel.put("track_" + track, 1);
            }
            if (stepsMade == 0) {
                // Шага не вышло — но обмен мог состояться, и действие тогда НЕ
                // холостое. Раньше телеметрия здесь терялась целиком.
                return ActionResult.ok(exchange == null
                    ? "science: no affordable step"
                    : "science: exchange " + exchange, tel);
            }
            return ActionResult.ok("science: " + detail.toString().trim(), tel);
        }

        @SuppressWarnings("unchecked")
        private void techStepReward(PlayerState player, String track, int reached, Agent agent) {
            GameConfig cfg = Ctx.cfg(state);
            Map<String, Object> te = null;
            for (Map<String, Object> e : cfg.content.get("boards").entries) {
                if ("tech_board".equals(e.get("kind"))) {
                    te = e;
                    break;
                }
            }
            String kind = null;
            List<Object> rewards = null;
            if (te != null) {
                for (Object tObj : (List<Object>) te.get("tracks")) {
                    Map<String, Object> t = (Map<String, Object>) tObj;
                    if (track.equals(t.get("id"))) {
                        kind = (String) t.get("modules");
                        break;
                    }
                }
                rewards = (List<Object>) te.get("step_rewards");
            }
            // СВОД ПЕРЕБИВАЕТ ДОСКУ. Лестница шагов — настраиваемая величина
            // (цена, очки, ёмкость шагов уже живут в своде), и когда вариант
            // правил меняет ЧИСЛО шагов, список наград обязан меняться вместе с
            // ними. Держать его только на доске значило бы плодить копию всего
            // набора планшетов ради одной строки.
            if (cfg.ruleset.get("tech.step_rewards", null) instanceof List<?> rl
                    && !rl.isEmpty()) {
                rewards = new ArrayList<>(rl);
            }
            // Награда шага берётся из данных step_rewards[reached-1], чтобы правка
            // числа шагов не требовала менять код. Возможные значения:
            // prize_cube / module / permanent_ability / super_arsenal_card.
            String reward = null;
            if (rewards != null && reached - 1 >= 0 && reached - 1 < rewards.size()) {
                reward = String.valueOf(rewards.get(reached - 1));
            }
            if ("prize_cube".equals(reward)) {
                // Приз шага 1 убывает по мере занятия ячеек: чем позже пришёл,
                // тем меньше досталось. Каким по счёту игрок ПРИШЁЛ на шаг 1:
                // по числу стоящих сейчас это не определить — кубик уходит
                // дальше, освобождая ячейку.
                //
                // Ключей ровно столько, сколько ячеек у шага, и они читаются из
                // свода: у трека синих модулей приз есть и на ТРЕТЬЕЙ ячейке
                // (правило дизайнера 16.08.2026), а у красного — только на двух.
                // Раньше здесь стояли зашитые first/second, и третья ячейка не
                // могла отдать приз, сколько бы его ни прописали в своде.
                int rank = state.tech.stepOneRank(track);
                String key = rank >= 1 && rank <= PRIZE_RANK_KEYS.length
                    ? PRIZE_RANK_KEYS[rank - 1] : null;
                if (key != null) {
                    Object prize = cfg.ruleset.get("tech.step1_prize." + track + "." + key, null);
                    if (prize instanceof Map<?, ?> pm) {
                        Map<String, Object> p = (Map<String, Object>) pm;
                        if (p.get("ammo") instanceof Number n) {
                            Storage.addAmmoCapped(state, player, n.intValue());
                        }
                        if (p.get("kelium") instanceof Number n) {
                            Storage.addKeliumCapped(state, player, n.intValue());
                        }
                        if (p.get("coin") instanceof Number n) {
                            player.resources.add(Resource.COIN, n.intValue());
                        }
                    }
                }
            } else if ("module".equals(reward)) {
                if ("red".equals(kind)) {
                    // «Модули 2.0»: награда трека = тянуть жетон из мешка
                    Modules.awardModule(state, player, "red");
                } else if ("blue".equals(kind)) {
                    Modules.awardModule(state, player, "blue");
                } else if ("storage".equals(kind) && player.storageTokens.size() < 2) {
                    // Жетон хранилища: выбор стороны НАВСЕГДА (ячейка ресурса
                    // или вечный кубик энергии).
                    List<Choice> opts = List.of(
                        new Choice("storage_side", "+1_universal_cell",
                            "universal resource cell"),
                        new Choice("storage_side", "+1_energy", "permanent energy cube"));
                    Choice pick = agent.choose(state, opts, Map.of("kind", "storage_side"));
                    player.storageTokens.add(String.valueOf(pick.payload()));
                }
            } else if ("super_arsenal_card".equals(reward)) {
                // ВЕРШИНА ТРЕКА БЕЗ СУПЕР-АРСЕНАЛА (дополнение выключено, решение
                // дизайнера 17.08.2026). Приз обязан остаться: вершина стоит 4
                // трофея и приближает конец партии, пустой она быть не может.
                //
                // Каждый трек даёт ЕЩЁ ОДИН СВОЙ ЖЕТОН — то же, что он выдаёт на
                // шагах 2 и 3, и никакого нового компонента в коробку не нужно:
                //   красный трек  → красный модуль (атака);
                //   синий трек    → синий модуль (сборка);
                //   зелёный трек  → ПОЗОЛОТА одного своего модуля.
                // Зелёный трек своих жетонов не производит (он про хранилище), и
                // позолота — ровно то, что он и продаёт за два обломка вечным
                // курсом. Десять монет здесь были бы вдвое слабее: 10 МОН это
                // 2 ПО, а вершина стоит четырёх трофеев.
                if (!kelium.engine.Setup.expansionOn(rs, "super_arsenal")) {
                    topPrizeWithoutSuperArsenal(player, kind);
                    return;
                }
                // Вершина трека: забрать выложенную В ОТКРЫТУЮ карту супер-арсенала
                // этого трека (одна на трек за партию). Супер-войско сразу в запас.
                String cid = state.superArsenalOffer.remove(track);
                if (cid != null) {
                    player.superArsenalCards.add(cid);
                    Map<String, Object> card = cfg.content.get("super_arsenal").find(cid);
                    if (card != null && "troop".equals(card.get("kind"))) {
                        UnitType ut = UnitType.fromCode(String.valueOf(card.get("unit")));
                        UnitToken su = state.tokenStats.makeUnit(ut, player.seat,
                            nextUid(state));
                        su.hp += card.get("hp_bonus") instanceof Number n ? n.intValue() : 1;
                        su.superUnit = true;
                        su.superCardId = cid;
                        player.units.add(su);   // в резерв; выйдет через Сборку/эффекты
                    }
                }
            }
        }

        /**
         * ПРИЗ ВЕРШИНЫ ТРЕКА, КОГДА СУПЕР-АРСЕНАЛ ВЫКЛЮЧЕН ДОПОЛНЕНИЕМ.
         *
         * <p>Приз обязан остаться: вершина стоит 4 трофея и приближает конец
         * партии — пустой она быть не может. Каждый трек даёт ЕЩЁ ОДИН СВОЙ
         * ЖЕТОН, то есть то же, что выдаёт на шагах 2 и 3, и нового компонента в
         * коробку не нужно:
         *
         * <ul>
         *   <li>красный трек → красный модуль (атака);</li>
         *   <li>синий трек → синий модуль (сборка);</li>
         *   <li>зелёный трек (хранилище) → ПОЗОЛОТА одного своего модуля.</li>
         * </ul>
         *
         * <p>Зелёный трек своих жетонов не печатает, и позолота — ровно тот приз,
         * который он и продаёт вечным курсом за два обломка. Десять монет здесь были
         * бы вдвое слабее: 10 МОН это 2 ПО, а вершина стоит четырёх трофеев.
         *
         * @param kind род модулей трека из данных доски: red | blue | storage
         */
        private void topPrizeWithoutSuperArsenal(PlayerState player, String kind) {
            switch (kind == null ? "storage" : kind) {
                case "red" -> Modules.awardModule(state, player, "red");
                case "blue" -> Modules.awardModule(state, player, "blue");
                default -> {
                    // ПОЗОЛОТА: улучшить один уже выданный жетон. Если золотить
                    // нечего, приз пропадает — как и всякая недоступная награда.
                    if (player.redModules + player.blueModules > player.goldModules) {
                        player.goldModules += 1;
                    }
                }
            }
        }

        /**
         * СКОЛЬКО ИГРОК МОЖЕТ ЗАПЛАТИТЬ ЗА НАУКУ — тем же счётом, каким платит.
         *
         * <p>Иначе меню и оплата расходятся: предложение считалось по жетонам с
         * обломками, а оплата (при новом правиле) берёт только обломки, и игрок
         * видел бы шаги, за которые ему нечем платить.
         */
        private int сколькоМожемЗаплатить(PlayerState player) {
            return rs.getBool("tech.pay_with_debris_only", false)
                ? player.resources.debris()
                : player.trophySpacePoints() + player.resources.debris();
        }

        private void payTrophy(PlayerState player, int cost) {
            payTrophy(player, cost, null);
        }

        /*
         * ВОЗВРАЩАЕТ УПЛАЧЕННОЕ. Ровно этого числа не хватало заданию o39: чем
         * платят за науку, зависит от свода (обломки или жетоны), и мерить это
         * снаружи — через размер трофейного места — можно только для одного из
         * двух правил. Кто платит, тот и знает сумму.
         */

        /**
         * Стоимость шага науки. Пассив science_first_step_discount (стартовая
         * карта «Изыскатели»): ПЕРВЫЙ шаг каждого действия Наука на 1 трофей
         * дешевле (минимум 1).
         */
        private int sciStepCost(PlayerState player, List<Integer> costs, int step, int stepsMade) {
            int cost = costs.get(step);
            if (stepsMade == 0 && cost > 1
                    && Passives.hasPassive(state, player.seat, "science_first_step_discount")) {
                cost = Math.max(1, cost - 1);
            }
            return cost;
        }

        /**
         * Оплата трофеями. K5: КАКОЙ жетон сдать — выбирает ИГРОК (излишек
         * сгорает, поэтому выбор важен); дефолт при отсутствии агента — жадно
         * СНИЗУ (минимизация потерь). B4: сданные жетоны ВОЗВРАЩАЮТСЯ владельцам
         * (правило §5.2), а не уничтожаются.
         */
        private int payTrophy(PlayerState player, int cost, Agent agent) {
            int remaining = cost;
            // ПЛАТЯТ ОБЛОМКАМИ, А НЕ ЦЕЛЫМИ ЖЕТОНАМИ (уточнение дизайнера
            // 21.08.2026, ключ tech.pay_with_debris_only).
            //
            // Правило целиком: снесённый жетон уезжает к тебе на трофейное место,
            // а в Возврат ВСЕ трофеи конвертируются в обломки 1:1 (это уже
            // работает, см. GameEngine.returnStep). Обломок и есть монета науки.
            // Движок же до сих пор позволял сдать в науку сам ЖЕТОН, минуя
            // конвертацию, — то есть тратить трофей в тот же ход, когда он взят.
            // Это меняло темп: война оплачивала науку немедленно, без раунда
            // ожидания, и «трофей» с «обломком» становились одним и тем же.
            //
            // Ключа нет — работает как раньше (жетоны, потом обломки), поэтому
            // старые своды и замеры воспроизводятся без правок.
            if (rs.getBool("tech.pay_with_debris_only", false)) {
                // ТОЧКА ПРАВИЛ СПРАШИВАЕТСЯ ЗАРАНЕЕ И ВСЕГДА — ровно по той же
                // причине, что и в ветке ниже: если спрашивать её только когда
                // обломков не хватило, точка молчит почти всегда, и «способность
                // подключена» становится правдой лишь иногда. Это поймал сторож
                // AbilityFrameworkTest, а не партия.
                boolean keliumOkHere = kelium.engine.ability.RuleQuery
                    .of(state, player.seat, kelium.engine.ability.Hook.SCIENCE_PAY_WITH)
                    .base(0).ask() >= 1.0;
                int pay = Math.min(remaining, player.resources.debris());
                if (pay > 0) {
                    player.resources.pay(Resource.DEBRIS, pay);
                    remaining -= pay;
                }
                if (remaining > 0 && keliumOkHere) {
                    // «Научный подряд» по-прежнему разрешает келемий.
                    int keliumPay = Math.min(remaining, player.resources.kelium());
                    if (keliumPay > 0) {
                        player.resources.pay(Resource.KELIUM, keliumPay);
                        remaining -= keliumPay;
                    }
                }
                return cost - remaining;
            }
            // ТОЧКА ПРАВИЛ спрашивается ЗАРАНЕЕ и всегда: иначе она срабатывала бы
            // только в редкой ветке «трофеев не хватило», и объявление «точка
            // подключена» было бы правдой лишь иногда.
            boolean keliumOk = kelium.engine.ability.RuleQuery
                .of(state, player.seat, kelium.engine.ability.Hook.SCIENCE_PAY_WITH)
                .base(0).ask() >= 1.0;
            while (remaining > 0 && !player.trophySpace.isEmpty()) {
                kelium.core.Token tok;
                if (agent != null && player.trophySpace.size() > 1) {
                    List<Choice> opts = new ArrayList<>();
                    for (kelium.core.Token t : player.trophySpace) {
                        opts.add(new Choice("trophy_pay", t,
                            "token worth " + t.trophyValue()));
                    }
                    Choice pick = agent.choose(state, opts,
                        Map.of("kind", "trophy_pay", "remaining", remaining));
                    tok = (kelium.core.Token) pick.payload();
                } else {
                    // жадно СНИЗУ: наименьшая ценность первой (минимум потерь)
                    tok = player.trophySpace.get(0);
                    for (kelium.core.Token t : player.trophySpace) {
                        if (t.trophyValue() < tok.trophyValue()) {
                            tok = t;
                        }
                    }
                }
                player.trophySpace.remove(tok);
                remaining -= tok.trophyValue();
                // возврат владельцу: жетон снова в его пуле (в запасе)
                tok.setCapturedBy(null);
                tok.resetDamage();
                tok.setHexId(null);
                // Правило 4 (уточнение 2026-08-15): возврат добытчика/энергостанции
                // владельцу ЗАКРЫВАЕТ его ячейки склада — владелец не выбирает,
                // излишек сгорает. См. Storage.forceEvictOnBuildingReturn.
                if (tok instanceof BuildingToken bt
                        && (bt.type == BuildingType.MINER || bt.type == BuildingType.POWER_PLANT)) {
                    Storage.forceEvictOnBuildingReturn(state, state.player(tok.owner()));
                }
            }
            if (remaining > 0) {
                int pay = Math.min(remaining, player.resources.debris());
                player.resources.pay(Resource.DEBRIS, pay);
                remaining -= pay;
            }
            // ТОЧКА ПРАВИЛ: чем ещё можно платить за шаги науки. Карта арсенала
            // «Научный подряд» разрешает келемий — это второй путь на треки, кроме
            // войны. Один келемий закрывает одно трофейное очко.
            if (remaining > 0 && keliumOk) {
                int pay = Math.min(remaining, player.resources.kelium());
                if (pay > 0) {
                    player.resources.pay(Resource.KELIUM, pay);
                    remaining -= pay;
                }
            }
            // Жетон мог стоить больше остатка (трофейное очко неделимо), поэтому
            // остаток бывает отрицательным — переплата в счёт не идёт.
            return cost - Math.max(0, remaining);
        }

        /** Предложить вечные обмены. Вернуть id взятого обмена или null. */
        @SuppressWarnings("unchecked")
        private String maybeExchange(PlayerState player, Agent agent) {
            int pool = сколькоМожемЗаплатить(player);
            List<Choice> opts = new ArrayList<>();
            if (pool >= 1) {
                Map<String, Object> ex = new HashMap<>();
                ex.put("id", "trophy_to_coin");
                ex.put("give", 1);
                ex.put("coin", 1);
                opts.add(new Choice("sci_exchange", ex, "1 trophy -> 1 coin"));
            }
            // СКИДКА ЗА ПАРУ, как на рынке (правило дизайнера 13.08.2026): два
            // трофея, сданные разом, дают на одну монету больше. Величина — из
            // правил, тем же ключом, что и на рынке.
            int pairBonus = ((Number) rs.get("tech.pair_bonus_coin", 0)).intValue();
            if (pairBonus > 0 && pool >= 2) {
                Map<String, Object> ex = new HashMap<>();
                ex.put("id", "trophy_to_coin");
                ex.put("give", 2);
                ex.put("coin", 2 + pairBonus);
                opts.add(new Choice("sci_exchange", ex,
                    "2 trophy разом -> " + (2 + pairBonus) + " coin"));
            }
            // ЗА КАРТУ НЕ ПЛАТЯТ, ЕСЛИ ЕЁ НЕКУДА ПОЛОЖИТЬ: ячейки под планшетом
            // заняты — обмен не предлагается вовсе. Иначе трофеи уходят, а карта
            // не приходит.
            if (pool >= 2 && kelium.engine.Storage.arsenalCellFree(state, player)) {
                Map<String, Object> ex = new HashMap<>();
                ex.put("id", "draw_arsenal");
                ex.put("give", 2);
                opts.add(new Choice("sci_exchange", ex, "2 trophy -> draw 2 arsenal, keep 1"));
            }
            // ЦЕНА ПОЗОЛОТЫ — из свода, а не из кода (решение дизайнера
            // 28.08.2026: два обломка вместо трёх). Старым сводам, где ключа
            // нет, остаётся прежняя тройка — числа сыгранных партий не должны
            // меняться задним числом.
            int gildCost = ((Number) rs.get("tech.gild_trophy_cost", 3)).intValue();
            if (pool >= gildCost
                    && (player.redModules + player.blueModules) > player.goldModules) {
                Map<String, Object> ex = new HashMap<>();
                ex.put("id", "gild");
                ex.put("give", gildCost);
                opts.add(new Choice("sci_exchange", ex,
                    gildCost + " trophy -> gild a module"));
            }
            // Вечный курс: 1 трофей -> 1 перемещение модуля (перестановка
            // посреди раунда, не дожидаясь Смены модулей в Обновление).
            if (pool >= 1
                    && (!player.redPlacements.isEmpty() || !player.bluePlacements.isEmpty())) {
                Map<String, Object> ex = new HashMap<>();
                ex.put("id", "move_module");
                ex.put("give", 1);
                opts.add(new Choice("sci_exchange", ex, "1 trophy -> move a module"));
            }
            // КАЖДЫЙ печатный обмен можно повторять сколько угодно раз за одно
            // действие Науки, пока хватает пула (уточнение дизайнера 2026-08-15,
            // отменяет более раннее «не более раза за действие» от 13.08.2026 —
            // тот заход оказался слишком строгим). Один жетон трофея по-прежнему
            // нельзя раздробить между РАЗНЫМИ обменами — payTrophy тратит его
            // целиком за один вызов, сдача сгорает.
            if (opts.isEmpty()) {
                return null;
            }
            opts.add(new Choice("pass", null, "без обмена"));
            Choice ch = agent.choose(state, opts, Map.of("kind", "sci_exchange"));
            if (ch.payload() == null) {
                return null;
            }
            Map<String, Object> ex = (Map<String, Object>) ch.payload();
            payTrophy(player, ((Number) ex.get("give")).intValue(), agent);
            String id = (String) ex.get("id");
            if ("trophy_to_coin".equals(id)) {
                player.resources.add(Resource.COIN, ((Number) ex.get("coin")).intValue());
            } else if ("draw_arsenal".equals(id)) {
                // ВЫБОР ИЗ ВИТРИНЫ (правило дизайнера 15.08.2026). Раньше игрок
                // тянул две карты вслепую и одну выбрасывал — то есть половина
                // колоды уходила в сброс, ничего не решая, а выбора не было
                // вовсе. Теперь рядом с планшетом науки лежат ДВЕ ОТКРЫТЫЕ
                // карты: игрок берёт одну, место немедленно пополняется с верха
                // колоды.
                // КАКУЮ ИМЕННО КАРТУ ВЗЯЛИ — в имя обмена: иначе на вопрос
                // «каждая ли карта арсенала вообще попадает в игру» ответить
                // нечем, а он балансовый (взятие с витрины было единственным
                // путём получения карты, не оставлявшим следа в событиях).
                String tookArsenal = takeFromArsenalDisplay(state, player, agent);
                if (tookArsenal != null) {
                    return id + ":" + tookArsenal;
                }
            } else if ("gild".equals(id)) {
                player.goldModules += 1;
            } else if ("move_module".equals(id)) {
                Modules.moveOneModule(state, player.seat, agent);
            }
            return id;
        }
    }

    /**
     * ЗАБРАТЬ КАРТУ АРСЕНАЛА С ВИТРИНЫ и тут же пополнить витрину.
     *
     * <p>Витрина — часть стола: карта на ней ФИЗИЧЕСКИ ушла из колоды, и вытянуть
     * её вслепую нельзя, пока она лежит открытой. Освободившееся место
     * пополняется немедленно, с верха колоды.
     *
     * <p>Если витрина пуста (колода и сброс исчерпаны) — игрок не получает
     * ничего. Это законный конец колоды, а не ошибка: карты кончились так же, как
     * кончились бы за столом.
     */
    static String takeFromArsenalDisplay(GameState state, PlayerState player, Agent agent) {
        // ЯЧЕЙКИ ПОД ПЛАНШЕТОМ ЗАНЯТЫ — брать некуда, и витрину трогать незачем.
        if (!kelium.engine.Storage.arsenalCellFree(state, player)) {
            return null;
        }
        if (state.arsenalDisplay.isEmpty()) {
            kelium.engine.Setup.refillArsenalDisplay(state);
        }
        if (state.arsenalDisplay.isEmpty()) {
            return null;
        }
        List<Choice> opts = new ArrayList<>();
        for (String cid : state.arsenalDisplay) {
            Map<String, Object> card = Ctx.cards(state, "arsenal").find(cid);
            String label = card == null ? cid : String.valueOf(card.getOrDefault("name", cid));
            opts.add(new Choice("arsenal_display", cid, label));
        }
        Choice pick = agent == null ? opts.get(0)
            : agent.choose(state, opts, Map.of("kind", "arsenal_display"));
        String taken = pick != null && pick.payload() instanceof String c
            ? c : state.arsenalDisplay.get(0);
        state.arsenalDisplay.remove(taken);
        player.arsenalHand.add(taken);
        kelium.engine.Setup.refillArsenalDisplay(state);
        return taken;
    }
}
