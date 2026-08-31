package kelium.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TurnJournal — структурированная память хода для заданий-инцидентов (Х).
 *
 * <p>Порт из forge/engine/journal.py. Многие карты заданий — инциденты,
 * проверяемые по тому, что игрок СДЕЛАЛ за текущий ход (произвёл N юнитов,
 * уничтожил жетон и не потерял своих и т.п.). Журнал наполняет движок:
 * {@link #startTurn(int)} в начале хода и обновление полей по мере событий.
 * Один экземпляр на партию хранит факты ТЕКУЩЕГО хода по каждому месту.
 *
 * <p>Ответный бой происходит внутри хода другого игрока, поэтому убийства в
 * ответке приписываются отвечающему месту в отдельное поле.
 */
public final class TurnJournal {

    /** Факты одного хода одного места (обнуляются каждый ход). */
    public static final class TurnFacts {
        /**
         * Приказ этого хода ЗАБЛОКИРОВАН совпадением верха с чужим (действий 1
         * вместо 2). Нужно способностям карт, которые умеют обходить блокировку:
         * {@link kelium.engine.ability.OptionSource} видит только состояние
         * партии, а не ход, поэтому признак живёт здесь.
         */
        public boolean orderBlocked = false;
        /**
         * Сколько раз за этот ход блокировку УЖЕ обошли картой — каждый обход
         * возвращает одно потерянное действие. Счётчик забирается методом
         * {@link #takeBlockBypassGrants()}, чтобы одно и то же разрешение не
         * сработало дважды.
         */
        public int blockBypassGrants = 0;

        /** Забрать накопленные разрешения на лишнее действие, обнулив счётчик. */
        public int takeBlockBypassGrants() {
            int n = blockBypassGrants;
            blockBypassGrants = 0;
            return n;
        }

        public int unitsProduced = 0;
        public final Set<Integer> unitsProducedBuildings = new HashSet<>();
        public final Set<String> assemblyOutputsUsed = new HashSet<>();  // {"unit","ammo"}
        public int ammoProduced = 0;
        public int keliumMined = 0;
        public int buildOps = 0;
        public final Set<Integer> energySwapSources = new HashSet<>();
        public boolean movedBuilding = false;
        public final Set<Integer> movedBuildingUids = new HashSet<>();
        public boolean razedOwnBuilding = false;
        public final Set<String> razedOwnHexes = new HashSet<>();
        public final Set<String> builtOnHexes = new HashSet<>();
        public boolean tookLastKeliumFromGrid = false;
        public int containersOpened = 0;
        public int unitsMoved = 0;
        public final Set<Integer> movedUids = new HashSet<>();
        public final Set<String> movedFromHexes = new HashSet<>();
        public boolean usedMarket = false;
        public boolean usedMarketPrintedRate = false;

        // боевые факты (как атакующий в этот ход)
        public final Set<Integer> enemyTokensDamaged = new HashSet<>();
        public int enemyTokensDestroyed = 0;
        public int lostOwnThisTurn = 0;
        public int battlesOpened = 0;
        public int destroyedInRetaliation = 0;

        /**
         * СКОЛЬКО ЧУЖИХ ЖЕТОНОВ ТЫ СБИЛ ОТВЕТНЫМ БОЕМ ПОСЛЕ НАЧАЛА СВОЕГО
         * ПРОШЛОГО ХОДА — то есть пока ходили другие.
         *
         * <p>Ответный бой по устройству случается не в твой ход, поэтому
         * {@link #destroyedInRetaliation} к началу твоего хода уже обнулён, и
         * условие карты «Ответный удар» без этого переноса не могло стать истинным
         * никогда. Значение проставляется при начале твоего хода из того, что
         * накопилось за чужие ходы, и живёт ровно один твой ход.
         */
        public int retaliationSincePrevTurn = 0;

        /** Своих жетонов потеряно за те же чужие ходы — для усиления «без потерь». */
        public int lostSincePrevTurn = 0;
        public int maxKillsOneBattle = 0;
        public final Set<String> destroyedTypes = new HashSet<>();
        /**
         * ЧЬИ ЖЕТОНЫ УНИЧТОЖЕНЫ за этот ход — места владельцев.
         *
         * <p>Нужно карте «Охота на лидера» (o43): она платит за удар именно по
         * тому, кто ведёт по очкам, а кто ведёт — известно только на момент
         * проверки, не на момент удара. Поэтому храним владельцев, а сравнение
         * с лидером делает сама карта.
         */
        public final Set<Integer> destroyedOwners = new HashSet<>();
        /** Снесено ли ЗАПИТАННОЕ здание экономики (добытчик/энергостанция) — o42. */
        public boolean destroyedPoweredEconomy = false;
        /** Снесено ли ЗДАНИЕ игрока, ведущего по очкам — усиление o43. */
        public boolean destroyedLeaderBuilding = false;
        /** Повреждён ли (не обязательно добит) жетон ведущего — прогресс o43. */
        public boolean damagedLeader = false;

        // === факты каталога 8.0 (objectives 1.5.0) ===
        // Сборка: producedByType[код рода] = сколько; типы зданий, давших
        // НЕ-вышку; сколько зданий сделали выбор и что выбрали (o01/o02/o09).
        public final Map<String, Integer> producedByType = new HashMap<>();
        public final Set<String> producedUnitBuildingTypes = new HashSet<>();
        public int assemblyChoseUnits = 0;
        public final Set<String> assemblyAmmoBuildingTypes = new HashSet<>();
        // Вышка, поставленная в этот ход в гекс БЕЗ своего ЦУ (o03).
        public final Set<String> towerPlacedHexes = new HashSet<>();
        // Добыча: ветка «контейнер» (o08); выработка тайлов (o05).
        public boolean minerTookContainer = false;
        public final Set<Integer> minerContainerLevels = new HashSet<>();
        public boolean lastKeliumNonStart = false;
        public boolean spawnTileClaimedNonStart = false;
        // Стройка: снос (o13); перенос не-ЦУ (o16); ЦУ на «чистый» гекс (o17).
        public boolean demolishedNonCu = false;
        public boolean movedNonCuBuilding = false;
        public final Set<Integer> movedNonCuUids = new HashSet<>();
        public boolean movedCuToVirginHex = false;
        // Смена энергии: гексы-исходы и «каждый кубик сменил гекс» (o20).
        public final Set<String> energySwapSourceHexes = new HashSet<>();
        public boolean energySwapSameHexCube = false;
        // Движение/маркет: подбор контейнера войском (o30), предложение карты (o33).
        public int containersPickedByUnit = 0;
        public boolean usedMarketCardOffer = false;
        // Бой: жирные жертвы (o22-прочность больше не нужна — Зачистка), нейтралы
        // (o22 «Зачистка»), экономный килл (o24), блицкриг (o26), урон зданиям (o25).
        public int neutralsRazed = 0;
        public boolean razedNeutralAndHitEnemySameBattle = false;
        public int minKillAmmoCost = Integer.MAX_VALUE;
        public boolean movedAndKilledSameUnit = false;
        public final Map<Integer, Integer> killsByMovedUnit = new HashMap<>();
        public int enemyBuildingHits = 0;

        // === факты каталога 10.0 (objectives 1.8.0, ревью дизайнера 17.08.2026) ===
        /**
         * НАИБОЛЬШАЯ ПРОЧНОСТЬ среди уничтоженных за ход чужих жетонов — o21
         * «Первая кровь» платит за то, что один из двоих был толстым.
         */
        public int maxDestroyedHp = 0;
        /** РАЗНЫЕ чужие ЗДАНИЯ, получившие урон за ход — o45 «Пристрелка». */
        public final Set<Integer> enemyBuildingsDamaged = new HashSet<>();
        /**
         * Убийства ПО КАЖДОМУ своему жетону войска: uid убийцы -> сколько снял
         * за этот ход. o26 «Блицкриг» требует двоих ОДНИМ жетоном.
         */
        public final Map<Integer, Integer> killsByUnit = new HashMap<>();
        /** Род войск каждого убийцы (uid -> код рода) — усиление o26. */
        public final Map<Integer, String> killerUnitTypes = new HashMap<>();
        /** Гексы, куда за этот ход поставлено или перенесено СВОЁ ЦУ — o17. */
        public final Set<String> cuPlacedHexes = new HashSet<>();
        /**
         * Гексы всех строительных операций этого хода, ПО ПОРЯДКУ и с повторами:
         * o15 «Стройбум» требует операций на попарно несоседних гексах, поэтому
         * важны сами гексы, а не их число.
         */
        public final List<String> buildOpHexes = new ArrayList<>();
        /** Сколько СВОИХ зданий перенесено за ход и было ли среди них ЦУ — o16. */
        public final Set<Integer> movedAnyBuildingUids = new HashSet<>();
        public boolean movedCuThisTurn = false;
        /**
         * РАЗНЫЕ ОПЛАЧЕННЫЕ ПРЕДЛОЖЕНИЯ планшета маркета за ход — o33 «Биржа».
         * Ключ описывает предложение: {@code printed:<курс>} или {@code card:<id>}.
         */
        public final Set<String> marketOffersUsed = new HashSet<>();
        /**
         * РАЗНЫЕ ПРЕДЛОЖЕНИЯ планшета технологий за ход — o34 «Научный отдел».
         * Ключ: {@code track:<id>} для шага по треку, {@code rate:<id>} для
         * вечного курса.
         */
        public final Set<String> scienceOffersUsed = new HashSet<>();
        /**
         * ЕДИНИЦ ОПЛАТЫ, ВНЕСЁННЫХ В НАУКУ за ход — o39 «Сдача».
         *
         * <p>БЫЛО СЛОМАНО с 21.08.2026 и починено 25.08.2026. Поле называлось
         * scienceTrophiesSpent и считало, насколько уменьшилось ТРОФЕЙНОЕ МЕСТО.
         * Пока наука брала целые жетоны, это работало. Потом правило поменялось
         * (tech.pay_with_debris_only: наука платится трофеями), трофейное место
         * перестало уменьшаться вовсе — и счётчик застыл на нуле. Условие o39
         * стало невыполнимым: карту раздали 89 раз за 200 партий и не выполнили
         * НИ РАЗУ.
         *
         * <p>Теперь считается то, чем платят: сколько единиц оплаты реально
         * ушло в науку (трофеи по действующему своду, жетоны по старому). Так
         * счётчик не зависит от того, какое правило оплаты включено.
         */
        public int sciencePaidUnits = 0;
        /** На какие треки ушла оплата — один трек за действие по своду 1.20.0. */
        public final Set<String> scienceTracksUsed = new HashSet<>();
        /** Нижний приказ карты приказа открылся и дал действие — n11. */
        public boolean lowerOrderOpen = false;
        /**
         * РОД ВОЙСК, ПОЛУЧИВШИЙ +1 К СКОРОСТИ ДО КОНЦА ХОДА (эффект speed_boost).
         * «До конца хода» — ровно срок жизни журнала, поэтому и живёт здесь, а не
         * новым состоянием объекта: плодить состояния ради одного эффекта правила
         * запрещают (СВОД §9.1).
         */
        public String speedBoostKind = null;
        /** Снят лимит СПЕЦ-действий до конца хода (эффект unlimited_spec). */
        public boolean unlimitedSpec = false;

        /**
         * Скопировать в себя все факты из {@code o} — нужно копии состояния для
         * просчёта вперёд: без журнала просчёт «забывает», что игрок уже успел
         * сделать в этот ход, и условия заданий врут.
         */
        void copyFrom(TurnFacts o) {
            unitsProduced = o.unitsProduced;
            unitsProducedBuildings.clear();
            unitsProducedBuildings.addAll(o.unitsProducedBuildings);
            assemblyOutputsUsed.clear();
            assemblyOutputsUsed.addAll(o.assemblyOutputsUsed);
            ammoProduced = o.ammoProduced;
            keliumMined = o.keliumMined;
            buildOps = o.buildOps;
            energySwapSources.clear();
            energySwapSources.addAll(o.energySwapSources);
            movedBuilding = o.movedBuilding;
            movedBuildingUids.clear();
            movedBuildingUids.addAll(o.movedBuildingUids);
            razedOwnBuilding = o.razedOwnBuilding;
            razedOwnHexes.clear();
            razedOwnHexes.addAll(o.razedOwnHexes);
            builtOnHexes.clear();
            builtOnHexes.addAll(o.builtOnHexes);
            tookLastKeliumFromGrid = o.tookLastKeliumFromGrid;
            containersOpened = o.containersOpened;
            unitsMoved = o.unitsMoved;
            movedUids.clear();
            movedUids.addAll(o.movedUids);
            movedFromHexes.clear();
            movedFromHexes.addAll(o.movedFromHexes);
            usedMarket = o.usedMarket;
            usedMarketPrintedRate = o.usedMarketPrintedRate;
            enemyTokensDamaged.clear();
            enemyTokensDamaged.addAll(o.enemyTokensDamaged);
            enemyTokensDestroyed = o.enemyTokensDestroyed;
            lostOwnThisTurn = o.lostOwnThisTurn;
            battlesOpened = o.battlesOpened;
            destroyedInRetaliation = o.destroyedInRetaliation;
            maxKillsOneBattle = o.maxKillsOneBattle;
            destroyedTypes.clear();
            destroyedTypes.addAll(o.destroyedTypes);
            producedByType.clear();
            producedByType.putAll(o.producedByType);
            producedUnitBuildingTypes.clear();
            producedUnitBuildingTypes.addAll(o.producedUnitBuildingTypes);
            assemblyChoseUnits = o.assemblyChoseUnits;
            assemblyAmmoBuildingTypes.clear();
            assemblyAmmoBuildingTypes.addAll(o.assemblyAmmoBuildingTypes);
            towerPlacedHexes.clear();
            towerPlacedHexes.addAll(o.towerPlacedHexes);
            minerTookContainer = o.minerTookContainer;
            minerContainerLevels.clear();
            minerContainerLevels.addAll(o.minerContainerLevels);
            lastKeliumNonStart = o.lastKeliumNonStart;
            spawnTileClaimedNonStart = o.spawnTileClaimedNonStart;
            demolishedNonCu = o.demolishedNonCu;
            movedNonCuBuilding = o.movedNonCuBuilding;
            movedNonCuUids.clear();
            movedNonCuUids.addAll(o.movedNonCuUids);
            movedCuToVirginHex = o.movedCuToVirginHex;
            energySwapSourceHexes.clear();
            energySwapSourceHexes.addAll(o.energySwapSourceHexes);
            energySwapSameHexCube = o.energySwapSameHexCube;
            containersPickedByUnit = o.containersPickedByUnit;
            usedMarketCardOffer = o.usedMarketCardOffer;
            neutralsRazed = o.neutralsRazed;
            razedNeutralAndHitEnemySameBattle = o.razedNeutralAndHitEnemySameBattle;
            minKillAmmoCost = o.minKillAmmoCost;
            movedAndKilledSameUnit = o.movedAndKilledSameUnit;
            killsByMovedUnit.clear();
            destroyedOwners.clear();
            destroyedPoweredEconomy = false;
            destroyedLeaderBuilding = false;
            damagedLeader = false;
            killsByMovedUnit.putAll(o.killsByMovedUnit);
            enemyBuildingHits = o.enemyBuildingHits;
            maxDestroyedHp = o.maxDestroyedHp;
            enemyBuildingsDamaged.clear();
            enemyBuildingsDamaged.addAll(o.enemyBuildingsDamaged);
            killsByUnit.clear();
            killsByUnit.putAll(o.killsByUnit);
            killerUnitTypes.clear();
            killerUnitTypes.putAll(o.killerUnitTypes);
            cuPlacedHexes.clear();
            cuPlacedHexes.addAll(o.cuPlacedHexes);
            buildOpHexes.clear();
            buildOpHexes.addAll(o.buildOpHexes);
            movedAnyBuildingUids.clear();
            movedAnyBuildingUids.addAll(o.movedAnyBuildingUids);
            movedCuThisTurn = o.movedCuThisTurn;
            marketOffersUsed.clear();
            marketOffersUsed.addAll(o.marketOffersUsed);
            scienceOffersUsed.clear();
            scienceOffersUsed.addAll(o.scienceOffersUsed);
            sciencePaidUnits = o.sciencePaidUnits;
            scienceTracksUsed.clear();
            scienceTracksUsed.addAll(o.scienceTracksUsed);
            lowerOrderOpen = o.lowerOrderOpen;
            speedBoostKind = o.speedBoostKind;
            unlimitedSpec = o.unlimitedSpec;
        }

        void reset() {
            orderBlocked = false;
            blockBypassGrants = 0;
            unitsProduced = 0;
            unitsProducedBuildings.clear();
            assemblyOutputsUsed.clear();
            ammoProduced = 0;
            keliumMined = 0;
            buildOps = 0;
            energySwapSources.clear();
            movedBuilding = false;
            movedBuildingUids.clear();
            razedOwnBuilding = false;
            razedOwnHexes.clear();
            builtOnHexes.clear();
            tookLastKeliumFromGrid = false;
            containersOpened = 0;
            unitsMoved = 0;
            movedUids.clear();
            movedFromHexes.clear();
            usedMarket = false;
            usedMarketPrintedRate = false;
            enemyTokensDamaged.clear();
            enemyTokensDestroyed = 0;
            // ОТВЕТНЫЙ БОЙ СЛУЧАЕТСЯ В ЧУЖОЙ ХОД — и это ломало карту o41
            // «Ответный удар» наглухо. Когда по тебе бьют, а падает нападавший,
            // запись ложится в ТВОЙ журнал, но идёт это в ход другого игрока. К
            // началу твоего собственного хода запись стиралась здесь, поэтому
            // условие «в этот ход уничтожь жетон в ответном бою» не могло стать
            // истинным НИКОГДА: в свой ход тебя не атакуют, ты атакуешь сам.
            //
            // РЕШЕНИЕ (17.08.2026): факт ПЕРЕНОСИТСЯ на один ход. На карте это
            // одна строка — «годится ответный бой, случившийся после начала твоего
            // прошлого хода», — и за столом это проверяемо: удар только что был,
            // его видели все, а сбитый жетон лежит в твоём трофейном месте.
            // Задание по-прежнему не заглядывает в прошлое дальше одного хода, то
            // есть правило «задание проверяет только этот ход» не нарушено: речь
            // о событиях, случившихся ПОСЛЕ твоего прошлого хода.
            retaliationSincePrevTurn = destroyedInRetaliation;
            lostSincePrevTurn = lostOwnThisTurn;
            lostOwnThisTurn = 0;
            battlesOpened = 0;
            destroyedInRetaliation = 0;
            maxKillsOneBattle = 0;
            destroyedTypes.clear();
            producedByType.clear();
            producedUnitBuildingTypes.clear();
            assemblyChoseUnits = 0;
            assemblyAmmoBuildingTypes.clear();
            towerPlacedHexes.clear();
            minerTookContainer = false;
            minerContainerLevels.clear();
            lastKeliumNonStart = false;
            spawnTileClaimedNonStart = false;
            demolishedNonCu = false;
            movedNonCuBuilding = false;
            movedNonCuUids.clear();
            movedCuToVirginHex = false;
            energySwapSourceHexes.clear();
            energySwapSameHexCube = false;
            containersPickedByUnit = 0;
            usedMarketCardOffer = false;
            neutralsRazed = 0;
            razedNeutralAndHitEnemySameBattle = false;
            minKillAmmoCost = Integer.MAX_VALUE;
            movedAndKilledSameUnit = false;
            killsByMovedUnit.clear();
            destroyedOwners.clear();
            destroyedPoweredEconomy = false;
            destroyedLeaderBuilding = false;
            damagedLeader = false;
            enemyBuildingHits = 0;
            maxDestroyedHp = 0;
            enemyBuildingsDamaged.clear();
            killsByUnit.clear();
            killerUnitTypes.clear();
            cuPlacedHexes.clear();
            buildOpHexes.clear();
            movedAnyBuildingUids.clear();
            movedCuThisTurn = false;
            marketOffersUsed.clear();
            scienceOffersUsed.clear();
            sciencePaidUnits = 0;
            scienceTracksUsed.clear();
            lowerOrderOpen = false;
            speedBoostKind = null;
            unlimitedSpec = false;
        }
    }

    private final TurnFacts[] perSeat;

    public TurnJournal(int numPlayers) {
        perSeat = new TurnFacts[numPlayers];
        for (int i = 0; i < numPlayers; i++) {
            perSeat[i] = new TurnFacts();
        }
    }

    /** Точная копия журнала (для копии состояния при просчёте вперёд). */
    public TurnJournal copy() {
        TurnJournal j = new TurnJournal(perSeat.length);
        for (int i = 0; i < perSeat.length; i++) {
            j.perSeat[i].copyFrom(perSeat[i]);
        }
        return j;
    }

    /** Начать ход места: обнулить его запись. */
    public void startTurn(int seat) {
        perSeat[seat].reset();
    }

    /** Факты текущего хода указанного места. */
    public TurnFacts of(int seat) {
        return perSeat[seat];
    }

    /** Обновить факты по результату действия (телеметрия). */
    public void onAction(int seat, String action, Map<String, Object> telemetry) {
        if (telemetry == null) {
            return;
        }
        TurnFacts f = perSeat[seat];
        switch (action) {
            case "assembly" -> {
                if (telemetry.get("units") instanceof Number n) {
                    f.unitsProduced += n.intValue();
                    if (n.intValue() > 0) {
                        f.assemblyOutputsUsed.add("unit");
                    }
                }
                if (telemetry.get("ammo") instanceof Number n && n.intValue() > 0) {
                    f.ammoProduced += n.intValue();
                    f.assemblyOutputsUsed.add("ammo");
                }
            }
            case "mining" -> {
                if (telemetry.get("kelium") instanceof Number n) {
                    f.keliumMined += n.intValue();
                }
            }
            // Стройка СЮДА НЕ ПОПАДАЕТ намеренно: операции считает сам
            // BuildAction (по одной на каждую построенную/перенесённую вещь).
            // Раньше здесь добавлялась ещё одна — и задания вида «сделай N
            // операций стройки» выполнялись на операцию раньше срока.
            default -> {
                // остальные факты подаются напрямую действиями/боем
            }
        }
    }

    /**
     * Отметить попадание в бою: повреждение/уничтожение цели, тип цели, флаг
     * ответки; потеря приписывается владельцу уничтоженной цели.
     */
    public void noteCombatHit(int attackerSeat, int victimOwner, int victimUid,
                              String victimType, boolean destroyed, boolean isRetaliation) {
        TurnFacts f = perSeat[attackerSeat];
        f.enemyTokensDamaged.add(victimUid);
        if (destroyed) {
            f.enemyTokensDestroyed += 1;
            f.destroyedTypes.add(victimType);
            if (isRetaliation) {
                f.destroyedInRetaliation += 1;
            }
            if (victimOwner >= 0 && victimOwner < perSeat.length) {
                perSeat[victimOwner].lostOwnThisTurn += 1;
            }
        }
    }
}
