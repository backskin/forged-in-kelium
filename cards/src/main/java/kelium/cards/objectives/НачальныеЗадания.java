package kelium.cards.objectives;

import kelium.cards.Награда;
import kelium.core.BuildingToken;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitToken;
import kelium.engine.cards.CardContext;

import static kelium.cards.objectives.Лицо.Вид.НАЧАЛЬНАЯ;
import static kelium.cards.objectives.Лицо.Природа.ПРОИСШЕСТВИЕ;
import static kelium.cards.objectives.Лицо.Природа.СОСТОЯНИЕ;

/**
 * НАЧАЛЬНЫЕ ЗАДАНИЯ — двенадцать карт первого раунда, каждая своим классом.
 *
 * <p>У всех двенадцати одна форма, и она задана дизайнером: усиления нет ни у
 * одной, награда у всех одна и та же — обломок и карта задания, — а одноразовый
 * эффект у шести карт монета и у шести боеприпас. Форма проверяется кодом
 * ({@link Лицо#жалоба()}), поэтому карта, нарушившая её, не доедет до колоды.
 *
 * <p>ПОРОГИ ЗДЕСЬ НЕ СЛУЧАЙНЫЕ. Начальное задание тем и опасно, что закрывается
 * одним действием: у игрока на подготовке уже стоит ЦУ, лежит боеприпас и есть
 * келемий. Поэтому пороги подняты ровно настолько, чтобы карта требовала хотя бы
 * одного собственного шага, а не выполнялась даром с раздачи.
 */
public final class НачальныеЗадания {

    private НачальныеЗадания() {
    }

    /** Общая награда всех начальных заданий: обломок и карта задания. */
    private static Награда наградаНачальной() {
        return Награда.обломки(1).иКартыЗаданий(1);
    }

    /** Общий предок: держит форму начальной карты и её одноразовый эффект. */
    private abstract static class Начальная extends ЗаданиеВКоде {
        private final boolean монетой;

        Начальная(String id, boolean монетой) {
            super(id);
            this.монетой = монетой;
        }

        /** Одноразовый эффект: монета или боеприпас — больше у начальных нет. */
        @Override
        public final boolean burn(CardContext ctx) {
            ctx.gain(монетой ? Resource.COIN : Resource.AMMO, 1);
            return true;
        }

        final String верх() {
            return монетой ? "+1 монета" : "+1 боеприпас";
        }

        /** Собрать лицо карты, подставив общую награду и общий верх. */
        final Лицо лицоНачальной(String имя, Лицо.Природа природа, String условие,
                                String описание) {
            return new Лицо(имя, НАЧАЛЬНАЯ, природа, условие, null,
                наградаНачальной(), Награда.нет(), верх(), описание);
        }
    }

    // ==================================================================

    /**
     * n1 «Подъём» — три своих здания на поле.
     *
     * <p>ПОРОГ ТРИ, А НЕ ДВА, И ЭТО ГЛАВНОЕ В КАРТЕ. ЦУ стоит у игрока с
     * подготовки, поэтому порог «2» закрывался одной стройкой и карта выполнялась
     * практически даром.
     */
    public static final class Подъём extends Начальная {
        public Подъём() {
            super("n1", true);
        }

        @Override
        public Лицо лицо() {
            return лицоНачальной("Подъём", СОСТОЯНИЕ,
                "Имей на поле не меньше трёх своих зданий, считая ЦУ",
                "Имей на поле не меньше трёх своих зданий, считая ЦУ, — награда "
                + "обломок и карта задания. Считается и ЦУ, поэтому от подготовки "
                + "до выполнения ровно две стройки.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ctx.me().buildingsOnField().size() >= 3;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ctx.me().buildingsOnField().size(), 3);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = ctx.me().buildingsOnField().size();
            return есть >= 3 ? "" : "построить ещё " + (3 - есть) + " здания";
        }
    }

    /** n2 «Первый боец» — найм войска любого рода, кроме вышки. */
    public static final class ПервыйБоец extends Начальная {
        public ПервыйБоец() {
            super("n2", false);
        }

        @Override
        public Лицо лицо() {
            return лицоНачальной("Первый боец", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД найми войско любого рода, кроме вышки",
                "В ЭТОТ ХОД найми войско любого рода, кроме вышки, — награда "
                + "обломок и карта задания. Вышка не считается: её ЦУ собирает "
                + "даром, и наймом это назвать нельзя.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            for (var e : ход(ctx).producedByType.entrySet()) {
                if (e.getValue() > 0 && !"tower".equals(e.getKey())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "нанять войско, кроме вышки";
        }
    }

    /** n3 «Патроны» — не меньше трёх боеприпасов. */
    public static final class Патроны extends Начальная {
        public Патроны() {
            super("n3", true);
        }

        @Override
        public Лицо лицо() {
            return лицоНачальной("Патроны", СОСТОЯНИЕ,
                "Имей не меньше трёх боеприпасов",
                "Имей не меньше трёх боеприпасов — награда обломок и карта "
                + "задания. Порог три, а не два: один боеприпас у тебя уже есть с "
                + "подготовки.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ctx.have(Resource.AMMO) >= 3;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ctx.have(Resource.AMMO), 3);
        }

        @Override
        public String needed(CardContext ctx) {
            int надо = 3 - ctx.have(Resource.AMMO);
            return надо <= 0 ? "" : "накопить ещё " + надо + " боеприпаса";
        }
    }

    /** n4 «Первая сделка» — предложение карты сделок на рынке. */
    public static final class ПерваяСделка extends Начальная {
        public ПерваяСделка() {
            super("n4", false);
        }

        @Override
        public Лицо лицо() {
            return лицоНачальной("Первая сделка", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД воспользуйся предложением карты сделок на рынке",
                "В ЭТОТ ХОД воспользуйся предложением карты сделок на рынке — "
                + "награда обломок и карта задания. Напечатанный курс не подходит: "
                + "карта требует именно предложения карты сделок.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).usedMarketCardOffer;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "оплатить предложение карты сделок на рынке";
        }
    }

    /** n5 «Коммутация» — запитанное здание вне гекса своего ЦУ. */
    public static final class Коммутация extends Начальная {
        public Коммутация() {
            super("n5", true);
        }

        @Override
        public Лицо лицо() {
            return лицоНачальной("Коммутация", СОСТОЯНИЕ,
                "Имей на поле запитанное здание, стоящее не на гексе с твоим ЦУ",
                "Имей на поле запитанное здание, стоящее не на гексе с твоим ЦУ, — "
                + "награда обломок и карта задания. Энергия до соседнего гекса сама "
                + "не дотянется: её надо туда перекинуть Сменой энергии.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            PlayerState p = ctx.me();
            var цу = гексыЦУ(p);
            for (BuildingToken b : p.buildingsOnField()) {
                if (b.powered() && !цу.contains(b.hexId)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "запитать здание вне гекса своего ЦУ";
        }
    }

    /** n6 «Выход» — войско на расстоянии двух и больше гексов от своего ЦУ. */
    public static final class Выход extends Начальная {
        public Выход() {
            super("n6", false);
        }

        @Override
        public Лицо лицо() {
            return лицоНачальной("Выход", СОСТОЯНИЕ,
                "Имей на поле своё войско на расстоянии двух и больше гексов "
                + "от гекса с твоим ЦУ",
                "Имей на поле своё войско на расстоянии двух и больше гексов от "
                + "гекса с твоим ЦУ — награда обломок и карта задания. Вышка "
                + "неподвижна, так что выйти придётся кем-то живым.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            var цу = гексыЦУ(ctx.me());
            if (цу.isEmpty()) {
                return false;
            }
            for (UnitToken u : ctx.me().unitsOnField()) {
                Integer d = kelium.engine.Movement.distance(ctx.state(), u.hexId, цу);
                if (d != null && d >= 2) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "отвести войско на два гекса от своего ЦУ";
        }
    }

    /** n7 «Жила» — не меньше трёх келемия. */
    public static final class Жила extends Начальная {
        public Жила() {
            super("n7", true);
        }

        @Override
        public Лицо лицо() {
            return лицоНачальной("Жила", СОСТОЯНИЕ,
                "Имей не меньше трёх келемия",
                "Имей не меньше трёх келемия — награда обломок и карта задания. "
                + "Келемий равен победному очку, поэтому держать его дорого, а "
                + "тратить жалко.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ctx.have(Resource.KELIUM) >= 3;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ctx.have(Resource.KELIUM), 3);
        }

        @Override
        public String needed(CardContext ctx) {
            int надо = 3 - ctx.have(Resource.KELIUM);
            return надо <= 0 ? "" : "добыть ещё " + надо + " келемия";
        }
    }

    /** n8 «Находка» — два неоткрытых контейнера на руках. */
    public static final class Находка extends Начальная {
        public Находка() {
            super("n8", false);
        }

        @Override
        public Лицо лицо() {
            return лицоНачальной("Находка", СОСТОЯНИЕ,
                "Имей у себя не меньше двух неоткрытых контейнеров",
                "Имей у себя не меньше двух неоткрытых контейнеров — награда "
                + "обломок и карта задания. Вскрытый контейнер сразу уходит, так "
                + "что придётся терпеть и не открывать.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ctx.me().containers >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            return доля(ctx.me().containers, 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int надо = 2 - ctx.me().containers;
            return надо <= 0 ? "" : "взять ещё " + надо + " контейнер";
        }
    }

    /** n9 «Первый шаг» — шаг на любом треке технологий. */
    public static final class ПервыйШаг extends Начальная {
        public ПервыйШаг() {
            super("n9", true);
        }

        @Override
        public Лицо лицо() {
            return лицоНачальной("Первый шаг", СОСТОЯНИЕ,
                "Стой хотя бы на первом шаге любого трека технологий",
                "Стой хотя бы на первом шаге любого трека технологий — награда "
                + "обломок и карта задания. За действие Наука больше одного шага "
                + "на трек не сделать, поэтому начинают все с одного и того же.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            for (int шаг : ctx.me().techSteps.values()) {
                if (шаг >= 1) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "шагнуть на любом треке технологий";
        }
    }

    /** n10 «Первый трофей» — хотя бы один жетон в трофеях. */
    public static final class ПервыйТрофей extends Начальная {
        public ПервыйТрофей() {
            super("n10", false);
        }

        @Override
        public Лицо лицо() {
            return лицоНачальной("Первый трофей", СОСТОЯНИЕ,
                "Имей в трофеях хотя бы один жетон",
                "Имей в трофеях хотя бы один жетон — награда обломок и карта "
                + "задания. Трофей берут только с боя, поэтому карта платит за "
                + "первый настоящий выстрел.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return !ctx.me().trophySpace.isEmpty();
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "уничтожить чужой жетон и взять его в трофеи";
        }
    }

    /** n11 «Второй заход» — действие с нижнего приказа своей карты приказа. */
    public static final class ВторойЗаход extends Начальная {
        public ВторойЗаход() {
            super("n11", true);
        }

        @Override
        public Лицо лицо() {
            return лицоНачальной("Второй заход", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД получи действие с нижнего приказа своей карты приказа",
                "В ЭТОТ ХОД получи действие с нижнего приказа своей карты приказа "
                + "— награда обломок и карта задания. Нижний приказ открывается не "
                + "всегда, так что карту держат до подходящего раунда.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).lowerOrderOpen;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "открыть нижний приказ своей карты приказа";
        }
    }

    /**
     * n12 «В унисон» — тот же приказ, что у другого игрока.
     *
     * <p>СОВПАДЕНИЕ ПРИКАЗА — ЭТО БЛОКИРОВКА, и карта платит именно за неё: по
     * правилам совпавший приказ не срабатывает. Поэтому карта — утешение за
     * потерянное действие, а не награда за угадывание.
     */
    public static final class ВУнисон extends Начальная {
        public ВУнисон() {
            super("n12", false);
        }

        @Override
        public Лицо лицо() {
            return лицоНачальной("В унисон", ПРОИСШЕСТВИЕ,
                "В ЭТОТ ХОД сыграй верхом тот же приказ, что и другой игрок",
                "В ЭТОТ ХОД сыграй верхом тот же приказ, что и другой игрок, — "
                + "награда обломок и карта задания. Совпавший приказ не "
                + "срабатывает, так что эта карта — плата за потерянное действие.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return ход(ctx).orderBlocked;
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "совпасть приказом с другим игроком";
        }
    }
}
