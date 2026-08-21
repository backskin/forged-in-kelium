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
            return new Лицо("Отгрузка", ОБЫЧНАЯ, ЖЕРТВА,
                "Сдай два своих неоткрытых контейнера",
                "Сдай третий",
                Награда.монеты(4), Награда.обломки(2).иКартыЗаданий(1),
                "СВОБОДНЫЙ МАРКЕТ",
                "Сдай два своих неоткрытых контейнера — награда 4 монеты. Сдай "
                + "третий, и условие усилено: 2 обломка и карта задания. Контейнер "
                + "стоит ровно столько, сколько в нём лежит, а лежит в нём неизвестно "
                + "что.");
        }

        @Override
        protected Map<String, Object> жертваВЗаписи() {
            return Map.of("resource", "container", "amount", 2);
        }

        @Override
        protected Map<String, Object> усиленнаяЖертваВЗаписи() {
            return Map.of("predicate", "sacrifice_enhanced",
                "params", Map.of("resource", "container", "amount", 3));
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return true;
        }

        /** Запрос без побочного эффекта: хватит ли контейнеров на усиленную сдачу. */
        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ctx.me().containers >= 3;
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
        public boolean burn(CardContext ctx) {
            return свободныйМаркет(ctx);
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
                "Верни в свой запас два своих войска с разных гексов, не считая "
                + "вышек и не считая гексов со своими зданиями",
                "Верни третье с третьего гекса",
                Награда.монеты(3).иБоеприпасы(2), Награда.картыЗаданий(1),
                "СВОБОДНОЕ ДВИЖЕНИЕ",
                "Верни в свой запас два своих войска с разных гексов, не считая "
                + "вышек и не считая гексов со своими зданиями, — награда 3 монеты и "
                + "2 боеприпаса. Верни третье с третьего гекса, и условие усилено: "
                + "карта задания. Сданные войска не считаются уничтоженными и "
                + "трофеями противнику не идут.");
        }

        @Override
        protected Map<String, Object> жертваВЗаписи() {
            return Map.of("resource", "units_off_base", "amount", 2);
        }

        @Override
        protected Map<String, Object> усиленнаяЖертваВЗаписи() {
            return Map.of("predicate", "sacrifice_enhanced",
                "params", Map.of("resource", "units_off_base", "amount", 3));
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return true;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return гексыВне(ctx).size() >= 3;
        }

        @Override
        public double progress(CardContext ctx) {
            // Та же поправка, что у «Отгрузки»: карта-жертва без ресурса на сдачу
            // неиграбельна, и близость должна считаться по собранной жертве.
            // Считаются ГЕКСЫ, а не войска: требование просит с РАЗНЫХ гексов.
            return доля(гексыВне(ctx).size(), 2);
        }

        @Override
        public String needed(CardContext ctx) {
            int есть = гексыВне(ctx).size();
            return есть >= 2 ? ""
                : "вывести войско ещё на " + (2 - есть) + " гекс вне своей базы";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободноеДвижение(ctx);
        }

        @Override
        protected String действие() {
            return "movement";
        }
    }

    /**
     * o22 «Зачистка» — вернуть чужое здание из трофеев владельцу.
     *
     * <p>ПЕРЕПИСАНА. Прежний класс {@code GroupOperation.Mopping} читал
     * совершенно другой факт ({@code neutralsRazed} — снос нейтрального здания),
     * хотя каталог давно описывает трофейную жертву: разошлись код и данные,
     * пока карта считалась готовой. Теперь источник один.
     */
    public static final class Зачистка extends ЗаданиеВКоде {
        public Зачистка() {
            super("o22");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Зачистка", ОБЫЧНАЯ, ЖЕРТВА,
                "Имей в трофеях здание противника и верни его владельцу",
                "Верни владельцу ещё один трофейный жетон",
                Награда.монеты(3).иБоеприпасы(2), Награда.картаАрсенала(),
                "СВОБОДНАЯ НАУКА",
                "Имей в трофеях здание противника и верни его владельцу — награда 3 "
                + "монеты и 2 боеприпаса. Верни владельцу ещё один трофейный жетон, "
                + "и условие усилено: карта арсенала. Отданный трофей очков не "
                + "приносит: за это и платят.");
        }

        @Override
        protected Map<String, Object> жертваВЗаписи() {
            return Map.of("resource", "trophies", "amount", 1);
        }

        @Override
        protected Map<String, Object> усиленнаяЖертваВЗаписи() {
            return Map.of("predicate", "sacrifice_enhanced",
                "params", Map.of("resource", "trophies", "amount", 2));
        }

        /** Требуется хотя бы одно чужое ЗДАНИЕ в трофеях, а не любой жетон. */
        @Override
        public boolean satisfied(CardContext ctx) {
            for (var t : ctx.me().trophySpace) {
                if (t instanceof kelium.core.BuildingToken) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return ctx.me().trophySpace.size() >= 2;
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Нужно именно чужое ЗДАНИЕ в трофеях. Трофейные ВОЙСКА к цели не
            // приближают — сдать их эта карта не позволяет, — поэтому близость
            // мерим по самому побитому чужому зданию в пределах выстрела, ровно
            // как «Осада»: путь к цели у карт один и тот же.
            if (ctx.have(kelium.core.Resource.AMMO) < 1) {
                return 0.0;
            }
            double лучшая = 0.0;
            for (var u : ctx.me().unitsOnField()) {
                for (String гекс : ctx.attackReach(u)) {
                    for (var t : ctx.enemyBuildingsOn(гекс)) {
                        if (t instanceof kelium.core.BuildingToken b && b.hp > 0) {
                            лучшая = Math.max(лучшая, 1.0 / b.hp);
                        }
                    }
                }
            }
            return готовность(лучшая);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "взять здание противника в трофеи";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободнаяНаука(ctx);
        }

        @Override
        protected String действие() {
            return "combat";
        }
    }

    /** o50 «Линия фронта» — войска связывают два гекса со своими добытчиками. */
    public static final class ЛинияФронта extends ЗаданиеВКоде {
        public ЛинияФронта() {
            super("o50");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Линия фронта", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Твои войска образуют непрерывное соседство, связывающее два "
                + "разных гекса, где стоят твои добытчики",
                "В этом соседстве не участвует ни одна вышка",
                Награда.боеприпасы(3), Награда.обломки(2).иКартуСВитрины(),
                "СВОБОДНОЕ ДВИЖЕНИЕ",
                "Твои войска образуют непрерывное соседство, связывающее два "
                + "разных гекса, где стоят твои добытчики, — награда 3 боеприпаса. "
                + "Если в этом соседстве не участвует ни одна вышка, условие "
                + "усилено: 2 обломка и карта арсенала на выбор из открытых. Вышка стоит даром и не "
                + "двигается — линия без вышек означает, что ты держишь её живыми "
                + "войсками.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return Shapes.chainConnects(ctx.state(), ctx.seat(), Map.of(
                "what", "unit:any", "anchors", "own_miner_hexes", "anchor_count", 2));
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return Shapes.chainConnects(ctx.state(), ctx.seat(), Map.of(
                "what", "unit:any", "anchors", "own_miner_hexes", "anchor_count", 2,
                "forbid_kinds", List.of("tower")));
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // ТРЕБОВАНИЕ-РИСУНОК СОБИРАЕТСЯ В ДВА ЭТАПА, и они очень разной цены.
            // Сперва нужны ДВЕ ОПОРЫ — гексы со своими добытчиками; пока их нет,
            // карта ждёт Стройки, и это далеко. Когда опоры есть, остаётся
            // перекрыть разрыв войсками, и вот это уже близко.
            java.util.Set<String> опоры = new java.util.LinkedHashSet<>();
            for (var b : ctx.me().buildingsOnField()) {
                if (b.type == kelium.core.BuildingType.MINER) {
                    опоры.add(b.hexId);
                }
            }
            if (опоры.size() < 2) {
                // Половина шкалы готовности отдана этапу опор: один добытчик —
                // это заметно, но до рисунка ещё стройка.
                return 0.5 * готовность(доля(опоры.size(), 2));
            }
            // Чтобы связать два гекса на расстоянии d, между ними нужно d-1
            // жетонов. Считаем самую дешёвую пару опор и сколько подвижных войск
            // на неё уже есть — это и есть остаток работы Движением.
            int нужно = Integer.MAX_VALUE;
            java.util.List<String> список = java.util.List.copyOf(опоры);
            for (int i = 0; i < список.size(); i++) {
                for (int j = i + 1; j < список.size(); j++) {
                    Integer d = ctx.distance(список.get(i),
                        java.util.List.of(список.get(j)));
                    if (d != null) {
                        нужно = Math.min(нужно, Math.max(0, d - 1));
                    }
                }
            }
            if (нужно == Integer.MAX_VALUE) {
                return 0.5 * готовность(1.0);
            }
            if (нужно == 0) {
                return готовность(1.0);   // опоры уже рядом — рисунок в одном шаге
            }
            int подвижных = 0;
            for (var u : ctx.me().unitsOnField()) {
                if (u.type != UnitType.TOWER) {
                    подвижных++;
                }
            }
            return готовность(доля(подвижных, нужно));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "связать войсками два гекса, где стоят твои добытчики";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободноеДвижение(ctx);
        }

        @Override
        protected String действие() {
            return "movement";
        }
    }

    /** o53 «Промышленный узел» — здания связывают три гекса по прямой. */
    public static final class ПромышленныйУзел extends ЗаданиеВКоде {
        public ПромышленныйУзел() {
            super("o53");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Промышленный узел", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Твои здания образуют непрерывное соседство, связывающее три "
                + "гекса, лежащих по прямой",
                "В этом соседстве участвуют две твои авиабазы",
                Награда.монеты(3), Награда.обломки(2).иКартыЗаданий(1),
                "ОДНА СТРОИТЕЛЬНАЯ ОПЕРАЦИЯ",
                "Твои здания образуют непрерывное соседство, связывающее три гекса, "
                + "лежащих по прямой, — награда 3 монеты. Если в этом соседстве "
                + "участвуют две твои авиабазы, условие усилено: 2 обломка и карта "
                + "задания. Авиабаза просит три кубика энергии, и две подряд — это "
                + "уже вся сеть.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return Shapes.chainConnects(ctx.state(), ctx.seat(), Map.of(
                "what", "building:any", "anchors", "straight_line", "anchor_count", 3));
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return Shapes.chainConnects(ctx.state(), ctx.seat(), Map.of(
                "what", "building:any", "anchors", "straight_line", "anchor_count", 3,
                "require_types", List.of("airbase"), "require_count", 2));
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // РИСУНОК ИЗ ЗДАНИЙ. Прямую из трёх гексов держат минимум три
            // здания, поэтому близость мерим по их числу: с двумя зданиями до
            // узла одна стройка, с одним — две. Само расположение здесь не
            // считаем: проверять прямизну по всем тройкам гексов дороже, чем
            // стоит подсказка, а число зданий — верхняя граница возможного и
            // никогда не обещает больше, чем есть.
            int зданий = ctx.me().buildingsOnField().size();
            return готовность(доля(зданий, 3));
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? "" : "связать зданиями три гекса, лежащих по прямой";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return свободнаяСтройка(ctx, 1);
        }

        @Override
        protected String действие() {
            return "build";
        }
    }

    /** o54 «Заслон» — жетоны связывают два противоположных гекса вокруг тайла зарождения. */
    public static final class Заслон extends ЗаданиеВКоде {
        public Заслон() {
            super("o54");
        }

        @Override
        public Лицо лицо() {
            return new Лицо("Заслон", ОБЫЧНАЯ, СОСТОЯНИЕ,
                "Твои жетоны образуют непрерывное соседство, связывающее два "
                + "противоположных гекса вокруг одного тайла зарождения",
                "В этом соседстве есть войска всех четырёх родов",
                Награда.монеты(2).иБоеприпасы(2), Награда.картаСВитрины(),
                "РЕМОНТ ГЕКСА",
                "Твои жетоны образуют непрерывное соседство, связывающее два "
                + "противоположных гекса вокруг одного тайла зарождения, — награда 2 "
                + "монеты и 2 боеприпаса. Если в этом соседстве есть войска всех "
                + "четырёх родов, условие усилено: карта арсенала на выбор из открытых. Заслон вокруг "
                + "зарождения отрезает соседа от келемия, не тратя ни одного "
                + "боеприпаса.");
        }

        @Override
        public boolean satisfied(CardContext ctx) {
            return Shapes.chainConnects(ctx.state(), ctx.seat(), Map.of(
                "what", "any", "anchors", "opposite_around_spawn", "anchor_count", 2));
        }

        @Override
        public boolean satisfiedEnhanced(CardContext ctx) {
            return Shapes.chainConnects(ctx.state(), ctx.seat(), Map.of(
                "what", "any", "anchors", "opposite_around_spawn", "anchor_count", 2,
                "require_unit_kinds", List.of("infantry", "vehicle", "aircraft", "tower")));
        }

        @Override
        public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // ЗАСЛОН СЧИТАЕТСЯ ОТ ТАЙЛА ЗАРОЖДЕНИЯ, а не от жетонов вообще:
            // требование говорит про два противоположных гекса ВОКРУГ одного
            // тайла, и без тайла на поле карта невыполнима в принципе. Поэтому
            // ищем лучший тайл — тот, вокруг которого у меня уже больше всего
            // своих жетонов, — и мерим близость по его окружению. Годится любой
            // жетон, а не только войско: карта так и написана («твои жетоны»).
            java.util.Set<String> мои = new java.util.LinkedHashSet<>();
            for (var t : ctx.myTokensOnField()) {
                мои.add(t.hexId());
            }
            if (мои.isEmpty()) {
                return 0.0;
            }
            double лучшая = 0.0;
            for (var hex : ctx.state().field.hexes.values()) {
                if (hex.spawnTile == null) {
                    continue;
                }
                int своих = 0;
                for (String рядом : ctx.neighbors(hex.id)) {
                    if (мои.contains(рядом)) {
                        своих++;
                    }
                }
                // Замкнуть противоположные гексы вокруг тайла — это цепочка из
                // трёх соседних клеток по одной из дуг; по ней и считаем.
                лучшая = Math.max(лучшая, доля(своих, 3));
            }
            return готовность(лучшая);
        }

        @Override
        public String needed(CardContext ctx) {
            return satisfied(ctx) ? ""
                : "связать жетонами два противоположных гекса вокруг тайла зарождения";
        }

        @Override
        public boolean burn(CardContext ctx) {
            return ctx.healHex();
        }

        @Override
        protected String действие() {
            return "movement";
        }
    }
}
