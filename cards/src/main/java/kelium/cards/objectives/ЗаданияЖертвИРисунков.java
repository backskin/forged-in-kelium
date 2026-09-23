package kelium.cards.objectives;

import java.util.List;
import java.util.Map;

import kelium.cards.Награда;
import kelium.core.UnitType;
import kelium.engine.Shapes;
import kelium.engine.cards.CardContext;

import static kelium.cards.objectives.Лицо.Вид.ОБЫЧНАЯ;
import static kelium.cards.objectives.Лицо.Природа.СОСТОЯНИЕ;
import static kelium.cards.objectives.Лицо.Природа.ЖЕРТВА;

/**
 * ЖЕРТВЫ И РИСУНКИ.
 *
 * <p>ЖЕРТВЫ. Плата вносится в момент розыгрыша карты, действий не требует —
 * дизайнер отдельно отметил, что таких карт в прежнем каталоге не было ни одной.
 * У карты-жертвы {@link #satisfied} тривиально истинно: настоящее условие — это
 * возможность заплатить, а её проверяет и списывает движок сам по записи
 * {@code sacrifice} ({@link ЗаданиеВКоде#жертваВЗаписи}).
 *
 * <p>РИСУНКИ. Дизайнер: считать надо не жетоны, а СВЯЗЬ. Фигура задаётся тем, ЧТО
 * она соединяет, — тогда её нельзя закрыть кучей жетонов на одном гексе, и
 * требование остаётся честным на любом поле. Геометрия живёт в {@link Shapes};
 * карта лишь называет параметры фигуры, что уже не предикат в YAML, а прямой
 * вызов движковой геометрии из кода карты.
 */
public final class ЗаданияЖертвИРисунков {

    private ЗаданияЖертвИРисунков() {
    }

    /** o06 «Отгрузка» — сдать два неоткрытых контейнера. */
    public static final class Отгрузка extends ЗаданиеВКоде {
        public Отгрузка() {
            super("o06");
        }

        @Override
        public Лицо лицо() {
            // Усиления нет, награда одна и выдаётся целиком (решение дизайнера
            // 09.09.2026: у восьми карт из сорока в колонке усиления тире, а
            // обе колонки награды — одна и та же награда).
            return new Лицо("Отгрузка", ОБЫЧНАЯ, ЖЕРТВА,
                "Сдай два своих неоткрытых контейнера",
                null,
                Награда.спецДействиями(3), Награда.нет(),
                Утиль.ЭНЕРГИЯ_ИЛИ_МОДУЛИ,
                "Сдай два своих неоткрытых контейнера — награда: три спец-действия в "
                + "этот ход. Усиления у карты нет.");
        }

        @Override
        protected Map<String, Object> жертваВЗаписи() {
            return Map.of("resource", "container", "amount", 2);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return true;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return false;              // усиления у карты нет
        }

        @Override
        public double progress(CardContext ctx) {
            // У КАРТЫ-ЖЕРТВЫ satisfied() ВСЕГДА ВЕРНО: условия на поле у неё нет,
            // платится она сдачей ресурса. Поэтому близость по умолчанию давала
            // 1.0 даже при нуле контейнеров — то есть врала: сдать нечем, и карта
            // неиграбельна. Настоящая близость здесь — насколько собрана жертва.
            return доля(ctx.me().containers, 2);
        }

        @Override
        public String needed(CardContext ctx) {
            return ctx.me().containers >= 2 ? ""
                : "накопить ещё " + (2 - ctx.me().containers) + " контейнер для сдачи";
        }


        @Override
        protected String действие() {
            return "market";
        }
    }

    /**
     * o10 «Разоружение» — вернуть в запас два своих войска с разных гексов вне
     * гексов своих зданий, не считая вышек.
     */
    public static final class Разоружение extends ЗаданиеВКоде {
        public Разоружение() {
            super("o10");
        }

        /** Гексы вне своей базы, где стоят мои войска (не вышки) — по одному на гекс. */
        private java.util.Set<String> гексыВне(CardContext ctx) {
            var свои = ctx.myBuildingHexes();
            java.util.Set<String> out = new java.util.LinkedHashSet<>();
            for (var u : ctx.me().unitsOnField()) {
                if (u.type != UnitType.TOWER && !свои.contains(u.hexId)) {
                    out.add(u.hexId);
                }
            }
            return out;
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Разоружение", ОБЫЧНАЯ, ЖЕРТВА,
                "Верни в запас 2 своих войска с поля",
                "Технику и/или авиацию - и с одного гекса",
                Награда.выбор("assembly", "market"),
                Награда.модуль("assembly").иСпец(1),
                Утиль.БОЙ,
                "Верни в запас 2 своих войска с поля — награда на выбор: Снаряжение или "
                + "Рынок. Усиление — Технику и/или авиацию - и с одного гекса: жетон "
                + "модуля сборки и спец-действие.");
        }

        @Override
        protected Map<String, Object> жертваВЗаписи() {
            return Map.of("resource", "units_on_field", "amount", 2);
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return true;
        }

        /**
         * УСИЛЕНИЕ ЧИТАЕТСЯ ИЗ ЖУРНАЛА, А НЕ СО СТОЛА. Плата снимается раньше
         * проверки, и сданных жетонов на поле уже нет: смотреть надо на то, ЧТО
         * было сдано ({@code sacrificedStrikeGroup}), а движок при оплате
         * нарочно снимает первой пару техники или авиации с одного гекса.
         */
        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).sacrificedStrikeGroup || ударнаяПараЕсть(ctx);
        }

        /** Стоят ли на одном гексе два жетона техники и/или авиации. */
        private boolean ударнаяПараЕсть(CardContext ctx) {
            java.util.Map<String, Integer> поГексам = new java.util.LinkedHashMap<>();
            for (var u : ctx.me().unitsOnField()) {
                if (u.type == UnitType.VEHICLE || u.type == UnitType.AIRCRAFT) {
                    поГексам.merge(u.hexId, 1, Integer::sum);
                }
            }
            for (int n : поГексам.values()) {
                if (n >= 2) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double progress(CardContext ctx) {
            // Та же поправка, что у «Отгрузки»: карта-жертва без ресурса на сдачу
            // неиграбельна, и близость должна считаться по собранной жертве.
            return доля(ctx.me().unitsOnField().size(), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = ctx.me().unitsOnField().size();
            return есть >= 2 ? ""
                : "вывести на поле ещё " + (2 - есть) + " войско, чтобы было что сдать";
        }


        @Override
        protected String действие() {
            return "movement";
        }
    }


}
