package kelium.cards.objectives;

import java.util.Locale;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.cards.CardContext;

/**
 * ГРУППА А — РАЗРАБОТКА: конвейер и рудник (o01–o10).
 *
 * <p>Написано заново по требованиям карт, без старого реестра предикатов. Каждая
 * карта — класс: сама читает состояние партии и журнал хода, сама говорит, чего
 * не хватает до выполнения, и сама считает долю пройденного пути.
 *
 * <p>Порядок в файле — как в каталоге дизайнера, чтобы карты можно было сверять
 * с бумагой подряд, не прыгая по коду.
 */
public final class GroupDevelopment {

    private GroupDevelopment() {
    }

    /**
     * Сколько войск произведено за ход БЕЗ УЧЁТА ВЫШЕК.
     *
     * <p>Вышку даёт ЦУ и она стоит ноль, поэтому задания на производство её
     * исключают — иначе их можно было бы закрывать бесплатно, ничего не делая.
     */
    private static int unitsExTower(CardContext ctx) {
        // Журнал берём напрямую: помощник живёт во внешнем классе, а ход() —
        // защищённый метод самой карты.
        var j = ctx.state().journal.of(ctx.seat());
        return Math.max(0, j.unitsProduced - j.producedByType.getOrDefault("tower", 0));
    }

    // ==================================================================
    //  o01 «Полный залп» — снарядить войска боеприпасами на сборке
    // ==================================================================

    /**
     * Снарядите минимум N войск боеприпасами вместо орудий прямо на сборочной
     * линии. Усиленно — то же самое на заводе или авиабазе.
     *
     * <p>Требование СОБЫТИЙНОЕ: считается, что игрок выбрал в Сборке боеприпас, а
     * не войско, и сколько раз за этот ход.
     */
    public static final class FullSalvo extends Objective {

        public FullSalvo() {
            super("o01");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).ammoProduced >= порог("count", 2);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            // Усиление — те же снаряжения, но сделанные на заводе или авиабазе.
            return ход(ctx).assemblyAmmoBuildingTypes.contains("factory")
                || ход(ctx).assemblyAmmoBuildingTypes.contains("airbase");
        }

        @Override public double progress(CardContext ctx) {
            return ratio(ход(ctx).ammoProduced, порог("count", 2));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 2) - ход(ctx).ammoProduced;
            return need <= 0 ? "готово"
                : "снарядить боеприпасом ещё " + need + " войск в этот ход";
        }
    }

    // ==================================================================
    //  o02 «Конвейер», o09 «Ударное соединение» — произвести войска
    // ==================================================================

    /**
     * Произвести N войск (вышки не в счёт) в разных зданиях.
     *
     * <p>Одна карта на два номера: o02 требует два войска в ДВУХ РАЗНЫХ зданиях,
     * o09 — просто войска, но без снаряжения боеприпасом. Разница — в параметрах
     * из данных, поэтому код один, а поведение задают числа.
     */
    public static final class Conveyor extends Objective {

        private final boolean distinctBuildings;

        public Conveyor(String id, boolean distinctBuildings) {
            super(id);
            this.distinctBuildings = distinctBuildings;
        }

        private int made(CardContext ctx) {
            return unitsExTower(ctx);
        }

        @Override public boolean satisfied(CardContext ctx) {
            if (made(ctx) < порог("count", 2)) {
                return false;
            }
            if (distinctBuildings) {
                return ход(ctx).unitsProducedBuildings.size()
                    >= порог("distinct_buildings", 2);
            }
            return true;
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            if (!satisfied(ctx)) {
                return false;
            }
            // Усиление o02: одно из зданий — авиабаза. Усиление o09: больше войск.
            if (distinctBuildings) {
                return ход(ctx).producedUnitBuildingTypes.contains("airbase");
            }
            return made(ctx) >= порогУсил("count", 3);
        }

        @Override public double progress(CardContext ctx) {
            double byCount = ratio(made(ctx), порог("count", 2));
            if (!distinctBuildings) {
                return byCount;
            }
            double byPlaces = ratio(ход(ctx).unitsProducedBuildings.size(),
                порог("distinct_buildings", 2));
            return Math.min(byCount, byPlaces);
        }

        @Override public String needed(CardContext ctx) {
            int needUnits = порог("count", 2) - made(ctx);
            if (needUnits > 0) {
                return "произвести ещё " + needUnits + " войск в этот ход";
            }
            if (distinctBuildings) {
                int needPlaces = порог("distinct_buildings", 2)
                    - ход(ctx).unitsProducedBuildings.size();
                if (needPlaces > 0) {
                    return "произвести ещё в " + needPlaces + " другом здании";
                }
            }
            return "готово";
        }
    }

    // ==================================================================
    //  o03 «Опорный пункт» — вышка вне гекса ЦУ
    // ==================================================================

    /**
     * Поставьте вышку отдельно от центра управления — снять её будет уже нельзя.
     * Усиленно — если вышка при этом граничит с врагом.
     *
     * <p>Требование по СОСТОЯНИЮ: вышка либо стоит вне ЦУ, либо нет; событие
     * постановки для этого не нужно, а состояние честнее — карту можно выполнить
     * и вышкой, поставленной раньше.
     */
    public static final class Strongpoint extends Objective {

        public Strongpoint() {
            super("o03");
        }

        private UnitToken towerOffCu(CardContext ctx) {
            String cuHex = cuHex(ctx);
            for (UnitToken u : моиВойска(ctx)) {
                if (u.type == UnitType.TOWER && u.hexId != null
                        && !u.hexId.equals(cuHex)) {
                    return u;
                }
            }
            return null;
        }

        private static String cuHex(CardContext ctx) {
            for (BuildingToken b : моиЗдания(ctx)) {
                if (b.type == BuildingType.COMMAND_CENTER) {
                    return b.hexId;
                }
            }
            return null;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return towerOffCu(ctx) != null;
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            for (UnitToken u : моиВойска(ctx)) {
                if (u.type == UnitType.TOWER && u.hexId != null
                        && !u.hexId.equals(cuHex(ctx))
                        && граничитСВрагом(ctx, u.hexId)) {
                    return true;
                }
            }
            return false;
        }

        @Override public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Вышка есть, но стоит на гексе ЦУ — полдела: осталось вынести.
            return войскРода(ctx, UnitType.TOWER) > 0 ? 0.5 : 0.0;
        }

        @Override public String needed(CardContext ctx) {
            if (satisfied(ctx)) {
                return "готово";
            }
            return войскРода(ctx, UnitType.TOWER) > 0
                ? "вынести вышку с гекса ЦУ"
                : "собрать вышку и поставить её вне гекса ЦУ";
        }
    }

    // ==================================================================
    //  o04 «Жила» — запитанные добытчики на разных тайлах зарождения
    // ==================================================================

    /** Держите N запитанных добытчиков у РАЗНЫХ тайлов зарождения. */
    public static final class Vein extends Objective {

        public Vein() {
            super("o04");
        }

        /** Тайлы зарождения, к которым примыкает мой запитанный добытчик. */
        private java.util.Set<String> spawnsCovered(CardContext ctx, boolean nonStartOnly) {
            java.util.Set<String> spawns = new java.util.HashSet<>();
            for (BuildingToken b : моиЗдания(ctx)) {
                if (b.type != BuildingType.MINER || b.hexId == null || !b.powered()) {
                    continue;
                }
                for (String nb : ctx.state().field.neighbors(b.hexId)) {
                    Hex h = ctx.state().field.get(nb);
                    if (h == null || h.spawnTile == null || h.spawnTile.kelium <= 0) {
                        continue;
                    }
                    if (nonStartOnly && h.spawnTile.isStart) {
                        continue;
                    }
                    spawns.add(nb);
                }
            }
            return spawns;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return spawnsCovered(ctx, false).size() >= порог("count", 2);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            // Усиление: хотя бы один тайл — НЕ стартовый.
            return satisfied(ctx) && !spawnsCovered(ctx, true).isEmpty();
        }

        @Override public double progress(CardContext ctx) {
            return ratio(spawnsCovered(ctx, false).size(), порог("count", 2));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 2) - spawnsCovered(ctx, false).size();
            return need <= 0 ? "готово"
                : "запитать добытчик ещё у " + need + " тайла зарождения";
        }
    }

    // ==================================================================
    //  o06, o10 — ЖЕРТВА
    // ==================================================================

    /**
     * ЗАДАНИЕ-ЖЕРТВА: платишь — выполнено.
     *
     * <p>Такие карты выполняются всегда, когда есть чем платить: условие — это
     * сама плата. Усиление — заплатить больше или другим, более дорогим ресурсом.
     *
     * <p>Прогресс тут особенно важен: он показывает, сколько ресурса накоплено к
     * цене, — раньше бот не мог понять, что до выполнения не хватает одной
     * монеты.
     */
    public static final class Sacrifice extends Objective {

        private final Resource resource;
        private final int amount;
        private final Resource enhancedResource;
        private final int enhancedAmount;

        public Sacrifice(String id, Resource resource, int amount,
                         Resource enhancedResource, int enhancedAmount) {
            super(id);
            this.resource = resource;
            this.amount = amount;
            this.enhancedResource = enhancedResource;
            this.enhancedAmount = enhancedAmount;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ctx.have(resource) >= amount;
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return enhancedResource != null && ctx.have(enhancedResource) >= enhancedAmount;
        }

        @Override public void reward(CardContext ctx, boolean enhanced) {
            // ПЛАТА СНАЧАЛА. Это и есть условие карты: не заплатив, выполнить её
            // нельзя, а награда выдаётся уже за уплаченное.
            if (enhanced && enhancedResource != null) {
                ctx.pay(enhancedResource, enhancedAmount);
            } else {
                ctx.pay(resource, amount);
            }
            super.reward(ctx, enhanced);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(ctx.have(resource), amount);
        }

        @Override public String needed(CardContext ctx) {
            int need = amount - ctx.have(resource);
            return need <= 0 ? "готово (можно платить)"
                : String.format(Locale.ROOT, "накопить ещё %d %s", need, resource);
        }
    }

    // ==================================================================
    //  o07 «Засада» — свои войска рядом с врагом
    // ==================================================================

    /** Держите N своих войск на гексах, граничащих с чужими жетонами. */
    public static final class Ambush extends Objective {

        public Ambush() {
            super("o07");
        }

        private int lurkers(CardContext ctx) {
            int n = 0;
            for (UnitToken u : моиВойска(ctx)) {
                if (u.hexId != null && граничитСВрагом(ctx, u.hexId)) {
                    n++;
                }
            }
            return n;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return lurkers(ctx) >= порог("count", 1);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return lurkers(ctx) >= порогУсил("count", 2);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(lurkers(ctx), порог("count", 1));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 1) - lurkers(ctx);
            return need <= 0 ? "готово"
                : "подвести ещё " + need + " войск вплотную к врагу";
        }
    }

    // ==================================================================
    //  o05 «Последыш», o08 «Счастливый рейс»
    // ==================================================================

    /** Заберите ПОСЛЕДНИЙ келемий с нестартового тайла зарождения. */
    public static final class LastDrop extends Objective {

        public LastDrop() {
            super("o05");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).lastKeliumNonStart;
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).spawnTileClaimedNonStart;
        }

        @Override public double progress(CardContext ctx) {
            // Прогресса тут нет: либо взял последний кубик, либо нет. Врать боту
            // промежуточной долей нельзя — он начнёт планировать несуществующее.
            return satisfied(ctx) ? 1.0 : 0.0;
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово"
                : "выработать нестартовый тайл зарождения до конца";
        }
    }

    /** Добытчик открыл контейнер. Усиленно — добытчик 3-го или 4-го уровня. */
    public static final class LuckyRun extends Objective {

        public LuckyRun() {
            super("o08");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).minerTookContainer;
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).minerContainerLevels.contains(3)
                || ход(ctx).minerContainerLevels.contains(4);
        }

        @Override public double progress(CardContext ctx) {
            return satisfied(ctx) ? 1.0 : 0.0;
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово"
                : "накрыть добытчиком печатный контейнер";
        }
    }
}
