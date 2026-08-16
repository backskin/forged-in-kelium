package kelium.cards.objectives;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Resource;
import kelium.engine.cards.CardContext;

/**
 * ГРУППА Г — ПРИОБРЕТЕНИЯ: рынок, наука, запасы (o33, o36–o38, o40)
 * плюс ВОСЕМЬ НАЧАЛЬНЫХ заданий (n1–n8).
 *
 * <p>Из группы вырезаны все шесть карт-жертв — они были торговым автоматом
 * «заплати и получи», а не заданием: в партии от них ничего не двигалось.
 *
 * <p>Начальные задания живут здесь же, потому что устроены так же просто:
 * это первые шаги игрока, и у них по построению нет усиленной ветки. В каталоге
 * 1.7.0 им поднята базовая награда — раньше они платили одну монету, то есть
 * меньше одного предложения рынка, за требование, которое всё-таки надо
 * выполнить.
 */
public final class GroupAcquisitions {

    private GroupAcquisitions() {
    }

    // ==================================================================
    //  o33 «Сделка» — воспользоваться рынком
    // ==================================================================

    /** Взять уникальное предложение карты рынка. Усиленно — ещё и печатную сделку. */
    public static final class Deal extends Objective {

        public Deal() {
            super("o33");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).usedMarketCardOffer;
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).usedMarketCardOffer && ход(ctx).usedMarketPrintedRate;
        }

        @Override public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Пошёл на рынок, но взял только печатную сделку — половина.
            return ход(ctx).usedMarket ? 0.5 : 0.0;
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово"
                : "взять уникальное предложение с активной карты рынка";
        }
    }

    // ==================================================================
    //  o36 «Дальний рубеж» — глубина по одному треку
    // ==================================================================

    /** Дойти до N-го шага хотя бы на одном треке науки. */
    public static final class FarFrontier extends Objective {

        public FarFrontier() {
            super("o36");
        }

        private int deepest(CardContext ctx) {
            int best = 0;
            for (String track : ctx.state().tech.tracks) {
                best = Math.max(best, я(ctx).techSteps.getOrDefault(track, 0));
            }
            return best;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return deepest(ctx) >= порог("step", 3);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return deepest(ctx) >= порогУсил("step", 4);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(deepest(ctx), порог("step", 3));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("step", 3) - deepest(ctx);
            return need <= 0 ? "готово"
                : "пройти ещё " + need + " шагов по одному треку науки";
        }
    }

    // ==================================================================
    //  o37 «Все треки» — ширина, а не глубина
    // ==================================================================

    /**
     * Занять все три трека науки минимум на N-м шаге.
     *
     * <p>Противоположность «Дальнему рубежу»: там глубина, здесь ширина. Прогресс
     * считается по САМОМУ ОТСТАЮЩЕМУ треку — именно он и решает.
     */
    public static final class AllTracks extends Objective {

        public AllTracks() {
            super("o37");
        }

        private int weakest(CardContext ctx) {
            int worst = Integer.MAX_VALUE;
            for (String track : ctx.state().tech.tracks) {
                worst = Math.min(worst, я(ctx).techSteps.getOrDefault(track, 0));
            }
            return worst == Integer.MAX_VALUE ? 0 : worst;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return weakest(ctx) >= порог("min_step", 1);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return weakest(ctx) >= порогУсил("min_step", 2);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(weakest(ctx), порог("min_step", 1));
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово"
                : "подтянуть отстающий трек науки до шага " + порог("min_step", 1);
        }
    }

    // ==================================================================
    //  o38 «Энерговооружённость» — запитанные добытчики
    // ==================================================================

    /** Держать N запитанных добытчиков. */
    public static final class PowerRatio extends Objective {

        public PowerRatio() {
            super("o38");
        }

        private int poweredMiners(CardContext ctx) {
            int n = 0;
            for (BuildingToken b : моиЗдания(ctx)) {
                if (b.type == BuildingType.MINER && b.powered()) {
                    n++;
                }
            }
            return n;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return poweredMiners(ctx) >= порог("count", 3);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return poweredMiners(ctx) >= порогУсил("count", 4);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(poweredMiners(ctx), порог("count", 3));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 3) - poweredMiners(ctx);
            return need <= 0 ? "готово"
                : "построить и запитать ещё " + need + " добытчиков";
        }
    }

    // ==================================================================
    //  o40 «По-нулям» — пустой склад
    // ==================================================================

    /**
     * Остаться без монет и боеприпасов. Усиленно — ещё и без келемия.
     *
     * <p>Единственное задание каталога, которое требует ПОТРАТИТЬ всё. Оно
     * работает как клапан против накопительства — того самого, за которое мы
     * ругаем ботов: 17 боеприпасов, лежащих мёртвым грузом до конца партии.
     */
    public static final class ZeroBalance extends Objective {

        public ZeroBalance() {
            super("o40");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ctx.have(Resource.COIN) <= порог("coin", 0)
                && ctx.have(Resource.AMMO) <= порог("ammo", 0);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return satisfied(ctx) && ctx.have(Resource.KELIUM) <= порогУсил("kelium", 0);
        }

        @Override public double progress(CardContext ctx) {
            // Чем меньше осталось, тем ближе. Считаем от «пять единиц всего» —
            // дальше этого прогресс всё равно неинформативен.
            int left = ctx.have(Resource.COIN) + ctx.have(Resource.AMMO);
            return ratio(Math.max(0, 5 - left), 5);
        }

        @Override public String needed(CardContext ctx) {
            int left = ctx.have(Resource.COIN) + ctx.have(Resource.AMMO);
            return left == 0 ? "готово"
                : "потратить ещё " + left + " монет и боеприпасов до нуля";
        }
    }

    // ==================================================================
    //  НАЧАЛЬНЫЕ ЗАДАНИЯ (n1–n8)
    // ==================================================================

    /** n1 «Основа» — построить N зданий. */
    public static final class FirstBuildings extends Objective {

        public FirstBuildings() {
            super("n1");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return моиЗдания(ctx).size() >= порог("count", 2);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(моиЗдания(ctx).size(), порог("count", 2));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 2) - моиЗдания(ctx).size();
            return need <= 0 ? "готово" : "построить ещё " + need + " зданий";
        }
    }

    /** n2 «Первый набор» — произвести войско за ход. */
    public static final class FirstUnits extends Objective {

        public FirstUnits() {
            super("n2");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).unitsProduced >= порог("count", 1);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(ход(ctx).unitsProduced, порог("count", 1));
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово" : "произвести войско в этот ход";
        }
    }

    /** n3 «Запасы» и n7 «Жила» — накопить ресурс. */
    public static final class Stock extends Objective {

        private final Resource resource;

        public Stock(String id, Resource resource) {
            super(id);
            this.resource = resource;
        }

        private int need(CardContext ctx) {
            return param("amount", 2);
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ctx.have(resource) >= need(ctx);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(ctx.have(resource), need(ctx));
        }

        @Override public String needed(CardContext ctx) {
            int left = need(ctx) - ctx.have(resource);
            return left <= 0 ? "готово" : "накопить ещё " + left + " " + resource;
        }
    }

    /** n4 «Первый рынок» — воспользоваться печатной сделкой. */
    public static final class FirstMarket extends Objective {

        public FirstMarket() {
            super("n4");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).usedMarketPrintedRate;
        }

        @Override public double progress(CardContext ctx) {
            return satisfied(ctx) ? 1.0 : (ход(ctx).usedMarket ? 0.5 : 0.0);
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово" : "совершить печатную сделку на рынке";
        }
    }

    /** n5 «Подключение» — запитать здание, кроме ЦУ. */
    public static final class FirstPower extends Objective {

        public FirstPower() {
            super("n5");
        }

        @Override public boolean satisfied(CardContext ctx) {
            for (BuildingToken b : моиЗдания(ctx)) {
                if (b.type != BuildingType.COMMAND_CENTER && b.energySlots > 0
                        && b.powered()) {
                    return true;
                }
            }
            return false;
        }

        @Override public double progress(CardContext ctx) {
            return satisfied(ctx) ? 1.0 : 0.0;
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово" : "запитать любое здание кроме ЦУ";
        }
    }

    /** n6 «Выход» — вывести войско с гекса ЦУ. */
    public static final class FirstStep extends Objective {

        public FirstStep() {
            super("n6");
        }

        private String cuHex(CardContext ctx) {
            for (BuildingToken b : моиЗдания(ctx)) {
                if (b.type == BuildingType.COMMAND_CENTER) {
                    return b.hexId;
                }
            }
            return null;
        }

        @Override public boolean satisfied(CardContext ctx) {
            String cu = cuHex(ctx);
            for (var u : моиВойска(ctx)) {
                if (u.hexId != null && !u.hexId.equals(cu)) {
                    return true;
                }
            }
            return false;
        }

        @Override public double progress(CardContext ctx) {
            return satisfied(ctx) ? 1.0 : (моиВойска(ctx).isEmpty() ? 0.0 : 0.5);
        }

        @Override public String needed(CardContext ctx) {
            if (satisfied(ctx)) {
                return "готово";
            }
            return моиВойска(ctx).isEmpty() ? "собрать войско и вывести его с гекса ЦУ"
                : "вывести войско с гекса ЦУ";
        }
    }

    /** n8 «Находка» — держать неоткрытый контейнер. */
    public static final class FirstFind extends Objective {

        public FirstFind() {
            super("n8");
        }

        @Override public boolean satisfied(CardContext ctx) {
            // ПОРОГ ИЗ ДАННЫХ, а не «хотя бы один». Игрок стартует С ОДНИМ
            // контейнером на руках, поэтому прежний порог делал карту выполненной
            // с первой секунды партии — даром (поймано договорным тестом).
            return я(ctx).containers >= param("count", 2);
        }

        @Override public double progress(CardContext ctx) {
            return ratio(я(ctx).containers, param("count", 2));
        }

        @Override public String needed(CardContext ctx) {
            int need = param("count", 2) - я(ctx).containers;
            return need <= 0 ? "готово"
                : "накопить ещё " + need + " неоткрытых контейнеров";
        }
    }
}
