package kelium.cards.objectives;

import java.util.HashSet;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.engine.cards.CardContext;

/**
 * ГРУППА Б — ИНФРАСТРУКТУРА: стройка и энергия (o11–o20).
 *
 * <p>Написано заново по требованиям карт. Общее у группы: почти все условия
 * говорят о ЗДАНИЯХ — где построено, запитано ли, сдвинуто ли, снесено ли. Это
 * самая «мирная» группа каталога, и именно ей проще всего дать прогресс:
 * здания считаются, а не случаются.
 */
public final class GroupInfrastructure {

    private GroupInfrastructure() {
    }

    // ==================================================================
    //  o11 «Передовой узел» — построить впритык к врагу
    // ==================================================================

    /**
     * Построить здание на гексе, граничащем с чужими ВОЙСКАМИ. Усиленно —
     * граничащем с чужим ЗДАНИЕМ (то есть влезть прямо в чужую базу).
     *
     * <p>Условие СОБЫТИЙНОЕ: важно, что построено в этот ход, а не что стоит.
     * Иначе карта закрывалась бы зданием, построенным пять раундов назад, когда
     * врага рядом ещё не было.
     */
    public static final class ForwardNode extends Objective {

        public ForwardNode() {
            super("o11");
        }

        /** Проверить построенные за ход гексы на соседство нужного вида. */
        private boolean builtNextTo(CardContext ctx, boolean buildings) {
            for (String hex : ход(ctx).builtOnHexes) {
                for (String nb : ctx.state().field.neighbors(hex)) {
                    for (PlayerState other : ctx.state().players) {
                        if (other.seat == ctx.seat()) {
                            continue;
                        }
                        if (buildings) {
                            for (BuildingToken b : other.buildingsOnField()) {
                                if (nb.equals(b.hexId)) {
                                    return true;
                                }
                            }
                        } else {
                            for (UnitToken u : other.unitsOnField()) {
                                if (nb.equals(u.hexId)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return builtNextTo(ctx, false);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return builtNextTo(ctx, true);
        }

        @Override public double progress(CardContext ctx) {
            // Построил в этот ход, но не у врага — половина: место не то, но
            // действие уже потрачено правильно.
            if (satisfied(ctx)) {
                return 1.0;
            }
            return ход(ctx).builtOnHexes.isEmpty() ? 0.0 : 0.5;
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово"
                : "построить здание вплотную к чужим войскам";
        }
    }

    // ==================================================================
    //  o12 «Стройка в поле» — здание на гексе с чужими войсками
    // ==================================================================

    /** Держать своё здание на гексе, где стоят N чужих войск. */
    public static final class FieldConstruction extends Objective {

        public FieldConstruction() {
            super("o12");
        }

        /** Максимум чужих войск на гексе, где стоит МОЁ здание. */
        private int worstHex(CardContext ctx) {
            int worst = 0;
            for (BuildingToken b : моиЗдания(ctx)) {
                if (b.hexId == null) {
                    continue;
                }
                int enemies = 0;
                for (PlayerState other : ctx.state().players) {
                    if (other.seat == ctx.seat()) {
                        continue;
                    }
                    for (UnitToken u : other.unitsOnField()) {
                        if (b.hexId.equals(u.hexId)) {
                            enemies++;
                        }
                    }
                }
                worst = Math.max(worst, enemies);
            }
            return worst;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return worstHex(ctx) >= порог("units", 1);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return worstHex(ctx) >= порогУсил("units", 2);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(worstHex(ctx), порог("units", 1));
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово"
                : "чужие войска должны встать на гекс с моим зданием";
        }
    }

    // ==================================================================
    //  o13 «Расчистка» — снести своё здание
    // ==================================================================

    /** Снести собственное здание. Усиленно — снести И построить в тот же ход. */
    public static final class Clearance extends Objective {

        public Clearance() {
            super("o13");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).razedOwnBuilding;
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).razedOwnBuilding && !ход(ctx).builtOnHexes.isEmpty();
        }

        @Override public double progress(CardContext ctx) {
            return satisfied(ctx) ? 1.0 : 0.0;
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово" : "снести собственное здание в этот ход";
        }
    }

    // ==================================================================
    //  o14 «Круговая порука» — окружить чужое здание своими
    // ==================================================================

    /**
     * Поставить N своих зданий вокруг гекса, в центре которого чужое здание.
     *
     * <p>Самое медленное условие каталога: здания не двигаются, значит окружение
     * надо строить заранее и рядом с чужой базой. Прогресс здесь особенно нужен —
     * бот должен видеть, что до кольца остался один дом.
     */
    public static final class Encirclement extends Objective {

        public Encirclement() {
            super("o14");
        }

        /** Наибольшее число моих зданий вокруг одного чужого. */
        private int bestRing(CardContext ctx) {
            int best = 0;
            for (PlayerState other : ctx.state().players) {
                if (other.seat == ctx.seat()) {
                    continue;
                }
                for (BuildingToken center : other.buildingsOnField()) {
                    if (center.hexId == null) {
                        continue;
                    }
                    Set<String> ring = new HashSet<>(
                        ctx.state().field.neighbors(center.hexId));
                    int mine = 0;
                    for (BuildingToken b : моиЗдания(ctx)) {
                        if (b.hexId != null && ring.contains(b.hexId)) {
                            mine++;
                        }
                    }
                    best = Math.max(best, mine);
                }
            }
            return best;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return bestRing(ctx) >= порог("count", 2);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return bestRing(ctx) >= порогУсил("count", 3);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(bestRing(ctx), порог("count", 2));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 2) - bestRing(ctx);
            return need <= 0 ? "готово"
                : "поставить ещё " + need + " своих зданий вокруг чужого";
        }
    }

    // ==================================================================
    //  o15 «Подрядчик» — несколько построек за ход
    // ==================================================================

    /** Сделать N операций стройки за один ход. */
    public static final class Contractor extends Objective {

        public Contractor() {
            super("o15");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).buildOps >= порог("count", 2);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).buildOps >= порогУсил("count", 3);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(ход(ctx).buildOps, порог("count", 2));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 2) - ход(ctx).buildOps;
            return need <= 0 ? "готово"
                : "построить ещё " + need + " раз в этот ход (доплата за каждую)";
        }
    }

    // ==================================================================
    //  o16 «Передел» — двигать здания
    // ==================================================================

    /** Передвинуть N своих зданий (кроме ЦУ) за ход. */
    public static final class Redivision extends Objective {

        public Redivision() {
            super("o16");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).movedBuildingUids.size() >= порог("count", 1);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).movedBuildingUids.size() >= порогУсил("count", 2);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(ход(ctx).movedBuildingUids.size(), порог("count", 1));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 1) - ход(ctx).movedBuildingUids.size();
            return need <= 0 ? "готово" : "передвинуть ещё " + need + " своих зданий";
        }
    }

    // ==================================================================
    //  o17 «Ход на новостройку» — перенести ЦУ
    // ==================================================================

    /**
     * Перенести ЦУ на гекс, где ещё никто не стоял. Усиленно — на гекс,
     * граничащий с врагом.
     *
     * <p>Самое рискованное задание группы: ЦУ — это половина энергии игрока и его
     * поражение, и двигать его к врагу значит подставлять партию.
     */
    public static final class NewGround extends Objective {

        public NewGround() {
            super("o17");
        }

        private BuildingToken cu(CardContext ctx) {
            for (BuildingToken b : моиЗдания(ctx)) {
                if (b.type == BuildingType.COMMAND_CENTER) {
                    return b;
                }
            }
            return null;
        }

        @Override public boolean satisfied(CardContext ctx) {
            BuildingToken cu = cu(ctx);
            return cu != null && ход(ctx).movedBuildingUids.contains(cu.uid());
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            BuildingToken cu = cu(ctx);
            return satisfied(ctx) && cu.hexId != null && граничитСВрагом(ctx, cu.hexId);
        }

        @Override public double progress(CardContext ctx) {
            return satisfied(ctx) ? 1.0 : 0.0;
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово" : "перенести ЦУ на нетронутый гекс";
        }
    }

    // ==================================================================
    //  o18 «Полное питание» — ни одного незапитанного здания
    // ==================================================================

    /**
     * Ни одно моё здание не стоит без энергии. Усиленно — то же самое при
     * четырёх и более зданиях.
     *
     * <p>Условие обратное обычному: чем больше построил, тем ТРУДНЕЕ выполнить.
     * Дефицит энергии в игре — три кубика на десять потребителей, поэтому
     * «запитано всё» естественно только у маленькой базы.
     */
    public static final class FullPower extends Objective {

        public FullPower() {
            super("o18");
        }

        private int unpowered(CardContext ctx) {
            int n = 0;
            for (BuildingToken b : моиЗдания(ctx)) {
                if (b.energySlots > 0 && !b.powered()) {
                    n++;
                }
            }
            return n;
        }

        private boolean ok(CardContext ctx, int minBuildings) {
            return моиЗдания(ctx).size() >= minBuildings && unpowered(ctx) == 0;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ok(ctx, порог("min_buildings", 1));
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ok(ctx, порогУсил("min_buildings", 4));
        }

        @Override public double progress(CardContext ctx) {
            // ДВА УСЛОВИЯ, А НЕ ОДНО: мало запитать всё — надо ещё, чтобы зданий
            // было не меньше порога. Считая только долю запитанных, карта на
            // старте показывала ЕДИНИЦУ (одно ЦУ, оно запитано) при невыполненном
            // условии — бот считал бы задание готовым и ждал награды напрасно.
            int all = моиЗдания(ctx).size();
            if (all == 0) {
                return 0.0;
            }
            double byPower = ratio(all - unpowered(ctx), all);
            double byCount = ratio(all, порог("min_buildings", 1));
            return Math.min(byPower, byCount);
        }

        @Override public String needed(CardContext ctx) {
            if (satisfied(ctx)) {
                return "готово";
            }
            // Условия два, и подсказка обязана называть то, которого не хватает.
            // Иначе карта говорила «готово» при одном запитанном ЦУ — то есть
            // обещала боту награду, которой не будет.
            int n = unpowered(ctx);
            if (n > 0) {
                return "запитать ещё " + n + " своих зданий";
            }
            int need = порог("min_buildings", 1) - моиЗдания(ctx).size();
            return "построить ещё " + need + " зданий и держать их все запитанными";
        }
    }

    // ==================================================================
    //  o19 «Гарнизон» — разные военные здания, все запитаны
    // ==================================================================

    /** N военных зданий РАЗНЫХ типов, и все запитаны. */
    public static final class Garrison extends Objective {

        public Garrison() {
            super("o19");
        }

        private int poweredMilitaryKinds(CardContext ctx) {
            Set<BuildingType> kinds = new HashSet<>();
            for (BuildingToken b : моиЗдания(ctx)) {
                boolean military = b.type == BuildingType.BARRACKS
                    || b.type == BuildingType.FACTORY
                    || b.type == BuildingType.AIRBASE;
                if (military && b.powered()) {
                    kinds.add(b.type);
                }
            }
            return kinds.size();
        }

        @Override public boolean satisfied(CardContext ctx) {
            return poweredMilitaryKinds(ctx) >= порог("count", 2);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return poweredMilitaryKinds(ctx) >= порогУсил("count", 3);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(poweredMilitaryKinds(ctx), порог("count", 2));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 2) - poweredMilitaryKinds(ctx);
            return need <= 0 ? "готово"
                : "построить и запитать ещё " + need + " военных зданий другого типа";
        }
    }

    // ==================================================================
    //  o20 «Перекоммутация» — переносить энергию между гексами
    // ==================================================================

    /** Перенести энергию с N разных источников за один ход. */
    public static final class Rewiring extends Objective {

        public Rewiring() {
            super("o20");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).energySwapSources.size() >= порог("sources", 2);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return satisfied(ctx) && !ход(ctx).builtOnHexes.isEmpty();
        }

        @Override public double progress(CardContext ctx) {
            return ratio(ход(ctx).energySwapSources.size(), порог("sources", 2));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("sources", 2) - ход(ctx).energySwapSources.size();
            return need <= 0 ? "готово"
                : "перенести энергию ещё с " + need + " источника";
        }
    }
}
