package kelium.cards.objectives;

import java.util.HashSet;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.cards.CardContext;

/**
 * ГРУППА В — ОПЕРАЦИЯ: бой и манёвр (o21–o31).
 *
 * <p>Самая важная группа каталога, и по замерам самая непонятая ботом. Именно
 * здесь живут задания, которые ПЛАТЯТ ЗА ВОЙНУ, — то есть единственный способ
 * сделать войну выгодной, не меняя правил боя. Замер 15.08.2026 показал, почему
 * они не работали: бот видел награду, но не видел, что до неё один удар.
 *
 * <p>Поэтому в этой группе прогресс важнее всего, и почти каждая карта его
 * считает: сколько уже повреждено, сколько войск уже стоит рядом с врагом,
 * сколько гексов уже занято.
 */
public final class GroupOperation {

    private GroupOperation() {
    }

    /** Все чужие живые войска и здания на поле. */
    private static java.util.List<kelium.core.Token> враги(CardContext ctx) {
        java.util.List<kelium.core.Token> out = new java.util.ArrayList<>();
        for (PlayerState other : ctx.state().players) {
            if (other.seat == ctx.seat()) {
                continue;
            }
            out.addAll(other.unitsOnField());
            out.addAll(other.buildingsOnField());
        }
        return out;
    }

    // ==================================================================
    //  o21 «Первая кровь» — уничтожить чужой жетон
    // ==================================================================

    /** Уничтожить N чужих жетонов за ход. Усиленно — двоих в одном бою. */
    public static final class FirstBlood extends Objective {

        public FirstBlood() {
            super("o21");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).enemyTokensDestroyed >= порог("count", 1);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).maxKillsOneBattle >= 2;
        }

        @Override public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Повреждённый, но не добитый враг — это половина дела, и бот должен
            // это видеть: иначе он не поймёт, что до награды остался один удар.
            return ход(ctx).enemyTokensDamaged.isEmpty() ? 0.0 : 0.5;
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово" : "уничтожить чужой жетон в этот ход";
        }
    }

    // ==================================================================
    //  o22 «Зачистка» — снести нейтральное здание
    // ==================================================================

    /** Снести нейтральное здание. Усиленно — ещё и повредить чужой жетон. */
    public static final class Mopping extends Objective {

        public Mopping() {
            super("o22");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).neutralsRazed > 0;
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).razedNeutralAndHitEnemySameBattle;
        }

        @Override public double progress(CardContext ctx) {
            return satisfied(ctx) ? 1.0 : 0.0;
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово" : "снести нейтральное здание";
        }
    }

    // ==================================================================
    //  o23 «Растяжка» — ранить многих, не добивая
    // ==================================================================

    /**
     * Повредить N РАЗНЫХ чужих жетонов и никого при этом не уничтожить.
     *
     * <p>Единственное задание каталога, которое НАКАЗЫВАЕТ за добивание: убил —
     * условие сорвано. Тонкая карта, и бот раньше не мог её сыграть в принципе,
     * потому что не различал «повредил» и «уничтожил».
     */
    public static final class Spread extends Objective {

        public Spread() {
            super("o23");
        }

        private boolean noKills(CardContext ctx) {
            return ход(ctx).enemyTokensDestroyed == 0;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return noKills(ctx)
                && ход(ctx).enemyTokensDamaged.size() >= порог("count", 2);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return noKills(ctx)
                && ход(ctx).enemyTokensDamaged.size() >= порогУсил("count", 3);
        }

        @Override public double progress(CardContext ctx) {
            if (!noKills(ctx)) {
                return 0.0;         // добил — условие сорвано на весь ход
            }
            return ratio(ход(ctx).enemyTokensDamaged.size(), порог("count", 2));
        }

        @Override public String needed(CardContext ctx) {
            if (!noKills(ctx)) {
                return "в этот ход уже не выйдет: кто-то добит";
            }
            int need = порог("count", 2) - ход(ctx).enemyTokensDamaged.size();
            return need <= 0 ? "готово"
                : "ранить ещё " + need + " разных жетонов, никого не добивая";
        }
    }

    // ==================================================================
    //  o24 «Точный выстрел» — уничтожить дёшево
    // ==================================================================

    /** Уничтожить жетон, потратив не больше N боеприпасов на удар. */
    public static final class PreciseShot extends Objective {

        public PreciseShot() {
            super("o24");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).minKillAmmoCost <= порог("ammo", 2)
                && ход(ctx).enemyTokensDestroyed > 0;
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).minKillAmmoCost <= порогУсил("ammo", 1)
                && ход(ctx).enemyTokensDestroyed > 0;
        }

        @Override public double progress(CardContext ctx) {
            return satisfied(ctx) ? 1.0 : 0.0;
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово"
                : "уничтожить жетон ударом не дороже " + порог("ammo", 2)
                    + " боеприпасов";
        }
    }

    // ==================================================================
    //  o25 «Осада» — бить чужие здания
    // ==================================================================

    /** Попасть по N чужим ЗДАНИЯМ за ход. */
    public static final class Siege extends Objective {

        public Siege() {
            super("o25");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).enemyBuildingHits >= порог("count", 1);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).enemyBuildingHits >= порогУсил("count", 2);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(ход(ctx).enemyBuildingHits, порог("count", 1));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 1) - ход(ctx).enemyBuildingHits;
            return need <= 0 ? "готово"
                : "ударить ещё по " + need + " чужому зданию (бьют техника и авиация)";
        }
    }

    // ==================================================================
    //  o26 «Наскок» — сходить и убить тем же жетоном
    // ==================================================================

    /** Одним и тем же войском сходить и уничтожить N жетонов в этот ход. */
    public static final class Raid extends Objective {

        public Raid() {
            super("o26");
        }

        private int bestMovedKiller(CardContext ctx) {
            int best = 0;
            for (var e : ход(ctx).killsByMovedUnit.entrySet()) {
                best = Math.max(best, e.getValue());
            }
            return best;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return bestMovedKiller(ctx) >= порог("kills", 1);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return bestMovedKiller(ctx) >= порогУсил("kills", 2);
        }

        @Override public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Сходил, но пока не убил — половина: жетон уже на позиции.
            return ход(ctx).movedUids.isEmpty() ? 0.0 : 0.5;
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово"
                : "сходить войском и им же уничтожить жетон в тот же ход";
        }
    }

    // ==================================================================
    //  o27 «На чужой земле», o31 «Воздушное превосходство»
    // ==================================================================

    /** Держать N своих войск на гексах, где стоят чужие войска. */
    public static final class OnEnemyGround extends Objective {

        public OnEnemyGround() {
            super("o27");
        }

        private int intruders(CardContext ctx) {
            int n = 0;
            for (UnitToken u : моиВойска(ctx)) {
                if (u.hexId != null && тамВраг(ctx, u.hexId)) {
                    n++;
                }
            }
            return n;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return intruders(ctx) >= порог("count", 1);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return intruders(ctx) >= порогУсил("count", 2);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(intruders(ctx), порог("count", 1));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 1) - intruders(ctx);
            return need <= 0 ? "готово"
                : "завести ещё " + need + " войск на гекс с чужими войсками";
        }
    }

    /** Авиация стоит на чужом гексе. Усиленно — на гексе с чужим ЦУ. */
    public static final class AirSupremacy extends Objective {

        public AirSupremacy() {
            super("o31");
        }

        private UnitToken airOnEnemy(CardContext ctx, boolean onCu) {
            for (UnitToken u : моиВойска(ctx)) {
                if (u.type != UnitType.AIRCRAFT || u.hexId == null) {
                    continue;
                }
                if (!onCu && тамВраг(ctx, u.hexId)) {
                    return u;
                }
                if (onCu) {
                    for (PlayerState other : ctx.state().players) {
                        if (other.seat == ctx.seat()) {
                            continue;
                        }
                        for (BuildingToken b : other.buildingsOnField()) {
                            if (b.type == BuildingType.COMMAND_CENTER
                                    && u.hexId.equals(b.hexId)) {
                                return u;
                            }
                        }
                    }
                }
            }
            return null;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return airOnEnemy(ctx, false) != null;
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return airOnEnemy(ctx, true) != null;
        }

        @Override public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Авиация есть — полдела: она ходит на два и игнорирует стенки.
            return войскРода(ctx, UnitType.AIRCRAFT) > 0 ? 0.5 : 0.0;
        }

        @Override public String needed(CardContext ctx) {
            if (satisfied(ctx)) {
                return "готово";
            }
            return войскРода(ctx, UnitType.AIRCRAFT) > 0
                ? "завести авиацию на чужой гекс"
                : "построить авиабазу и собрать авиацию";
        }
    }

    // ==================================================================
    //  o28 «Клещи» — окружить чужой гекс
    // ==================================================================

    /** Мои войска стоят на N разных гексах вокруг ОДНОГО чужого гекса. */
    public static final class Pincers extends Objective {

        public Pincers() {
            super("o28");
        }

        private int bestPincer(CardContext ctx) {
            int best = 0;
            for (kelium.core.Token enemy : враги(ctx)) {
                if (enemy.hexId() == null) {
                    continue;
                }
                Set<String> ring = new HashSet<>(
                    ctx.state().field.neighbors(enemy.hexId()));
                Set<String> mine = new HashSet<>();
                for (UnitToken u : моиВойска(ctx)) {
                    if (u.hexId != null && ring.contains(u.hexId)) {
                        mine.add(u.hexId);
                    }
                }
                best = Math.max(best, mine.size());
            }
            return best;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return bestPincer(ctx) >= порог("hexes", 2);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return bestPincer(ctx) >= порогУсил("hexes", 3);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(bestPincer(ctx), порог("hexes", 2));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("hexes", 2) - bestPincer(ctx);
            return need <= 0 ? "готово"
                : "подвести войска ещё с " + need + " стороны к одному чужому гексу";
        }
    }

    // ==================================================================
    //  o29 «Дальний рейд» — войска вне своих гексов
    // ==================================================================

    /**
     * Держать N войск на гексах, где нет ни одного моего здания.
     *
     * <p>Задание против «сидения дома»: войско на своей базе не считается. Прямо
     * толкает выводить армию в поле — то самое, чего боты не делали.
     */
    public static final class DeepRaid extends Objective {

        public DeepRaid() {
            super("o29");
        }

        private int away(CardContext ctx) {
            Set<String> home = new HashSet<>();
            for (BuildingToken b : моиЗдания(ctx)) {
                if (b.hexId != null) {
                    home.add(b.hexId);
                }
            }
            int n = 0;
            for (UnitToken u : моиВойска(ctx)) {
                if (u.hexId != null && !home.contains(u.hexId)) {
                    n++;
                }
            }
            return n;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return away(ctx) >= порог("count", 2);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return away(ctx) >= порогУсил("count", 3);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(away(ctx), порог("count", 2));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 2) - away(ctx);
            return need <= 0 ? "готово"
                : "вывести ещё " + need + " войск с гексов своих зданий";
        }
    }

    // ==================================================================
    //  o30 «Трофей» — войско открыло контейнер
    // ==================================================================

    /** Открыть N печатных контейнеров ВОЙСКОМ (не зданием) за ход. */
    public static final class Loot extends Objective {

        public Loot() {
            super("o30");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).containersPickedByUnit >= порог("count", 1);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).containersPickedByUnit >= порогУсил("count", 2);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(ход(ctx).containersPickedByUnit, порог("count", 1));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 1) - ход(ctx).containersPickedByUnit;
            return need <= 0 ? "готово"
                : "накрыть войском ещё " + need + " печатный контейнер";
        }
    }
}
