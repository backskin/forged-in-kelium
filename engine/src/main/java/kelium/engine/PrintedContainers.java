package kelium.engine;

import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitType;

/**
 * PrintedContainers — выдача карт контейнеров за печатные ячейки поля.
 *
 * <p>Правило (дизайнер, 12.08.2026, уточнено): карту контейнера получает тот, кто
 * НАКРЫЛ печатную ячейку ЛЮБЫМ СВОИМ ЖЕТОНОМ — войском или зданием, без разницы.
 * Срабатывает каждый раз, когда на ячейку встаёт жетон: ячейка не выгорает.
 *
 * <p>ДВА ИСКЛЮЧЕНИЯ (тоже правило дизайнера). Контейнер НЕ выдаётся, если
 * накрывшее войско:
 * <ol>
 *   <li>вышло ИЗ ЗДАНИЯ (стояло гарнизоном внутри своего здания);</li>
 *   <li>стояло НА ДРУГОМ КОНТЕЙНЕРЕ.</li>
 * </ol>
 * Оба исключения бьют по одному и тому же — по «доению» ячеек одним и тем же
 * жетоном без реального продвижения по полю.
 *
 * <p><b>Как это моделируется.</b> Движок не приколачивает войска к конкретным
 * ячейкам — они занимают место и свободно переупаковываются внутри гекса
 * (см. {@code Hex.fitsWithRepack}). Поэтому для войск действует очевидное
 * допущение: живой игрок ВСЕГДА поставит жетон на контейнерную ячейку, если
 * она свободна. Значит наземное войско, вошедшее в гекс, берёт контейнер, если
 * контейнер этого гекса наземный и не накрыт зданием; авиация — если контейнер
 * стоит в воздушной ячейке. Для зданий допущений нет: у них ячейки жёсткие,
 * и контейнер выдаётся ровно тогда, когда след здания реально накрыл ячейку.
 */
public final class PrintedContainers {

    private PrintedContainers() {
    }

    /**
     * ТЕЛЕМЕТРИЯ ИСТОЧНИКОВ (только для балансовых пробников): откуда пришли
     * контейнеры — из движения, из появления жетона, из стройки. Замер показал,
     * что печатные ячейки дают ~17 контейнеров на игрока за партию при 18 ячейках
     * на поле, и без разбивки по источникам невозможно понять, откуда поток.
     */
    private static final Map<String, java.util.concurrent.atomic.LongAdder> STATS =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Обнулить счётчики источников (перед серией партий). */
    public static void resetStats() {
        STATS.clear();
    }

    /** Сколько контейнеров выдано по каждому источнику с последнего обнуления. */
    public static Map<String, Long> stats() {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        STATS.forEach((k, v) -> out.put(k, v.sum()));
        return out;
    }

    /**
     * Войско ПЕРЕМЕСТИЛОСЬ из одного гекса в другой.
     *
     * @param wasInsideBuilding стояло ли войско ГАРНИЗОНОМ внутри своего здания
     *     ДО хода. Признак передаётся снаружи, потому что к моменту вызова жетон
     *     уже вышел из здания (смена гекса выводит из него автоматически).
     */
    public static int onUnitMoved(GameState s, PlayerState p, String fromHexId,
                                  String toHexId, UnitType type,
                                  boolean wasInsideBuilding) {
        // ИСКЛЮЧЕНИЕ 1: вышел из здания — контейнер не берёт.
        if (wasInsideBuilding) {
            return 0;
        }
        // ИСКЛЮЧЕНИЕ 2: стоял на другом контейнере — тоже не берёт.
        if (stoodOnContainer(s, fromHexId, type)) {
            return 0;
        }
        return grantForUnit(s, p, toHexId, type, "движение");
    }

    /**
     * Жетон ПОЯВИЛСЯ на поле из запаса (найм в Сборке, разворот с карты). Это
     * тоже НАКРЫТИЕ ячейки, значит контейнер положен: правило говорит про любой
     * жетон и не делает исключения для новичков. Исключения 1 и 2 тут не могут
     * сработать в принципе — жетон пришёл из запаса.
     */
    public static int onUnitPlaced(GameState s, PlayerState p, String hexId, UnitType type) {
        return grantForUnit(s, p, hexId, type, "найм/разворот");
    }

    /**
     * Считаем ли, что войско стояло на ячейке с контейнером в этом гексе.
     * Опирается на то же допущение, что и выдача: игрок ставит жетон на
     * контейнерную ячейку, когда она свободна.
     */
    private static boolean stoodOnContainer(GameState s, String hexId, UnitType type) {
        Hex h = hexId == null ? null : s.field.get(hexId);
        if (h == null || h.containerCell < 0) {
            return false;
        }
        if (type == UnitType.AIRCRAFT) {
            return h.containerCell == AIR_CELL;
        }
        return groundContainerFree(s, h);
    }

    private static final int AIR_CELL = BlockStamp.AIR;

    /**
     * ДОРАБОТКА ПРАВИЛ ПОЛУЧЕНИЯ (диктовка дизайнера 13.08.2026): контейнер
     * достаётся только с гекса, на котором НЕТ ВООБЩЕ НИКАКИХ жетонов игроков.
     *
     * <p>Зачем: замер 13.08.2026 показал, что печатные ячейки сыплются почти без
     * конца (79.6 контейнера за партию против 16.4 у прежних жетонов, +2.7 ПО на
     * игрока). Требование пустого гекса лишает смысла доение одного гекса скоплением
     * жетонов: контейнер получает только тот, кто пришёл на ЧИСТЫЙ гекс первым.
     */
    private static boolean requiresEmptyHex(GameState s) {
        return Boolean.TRUE.equals(kelium.dataio.Ctx.rules(s)
            .get("containers.printed_requires_empty_hex", Boolean.FALSE));
    }

    /**
     * ЯЧЕЙКА ВЫГОРАЕТ: отдав контейнер один раз, печатная ячейка больше не отдаёт
     * его никому до конца партии (правило-вариант
     * {@code containers.printed_cell_burns_out}).
     *
     * <p>Зачем такой вариант вообще нужен. Движок НЕ моделирует, какую ячейку
     * занимает войско: {@code sideOwner} заполняют только здания и стенки нейтралов.
     * Поэтому «ячейка закрыта жетоном, пока он там стоит» в замерах не работает —
     * ячейка всегда выглядит свободной для следующего пришедшего, и та же ячейка
     * отдаёт карту каждый раунд. Выгорание — то же ограничение, но без модели
     * занятости ячеек: за столом помечается одним маркером.
     */
    private static boolean burnsOut(GameState s) {
        return Boolean.TRUE.equals(kelium.dataio.Ctx.rules(s)
            .get("containers.printed_cell_burns_out", Boolean.FALSE));
    }

    /**
     * Контейнер выдаётся ТОЛЬКО за жетон, который остаётся на ячейке (здание,
     * вышка). Ограничитель без единого нового компонента: здание само себе маркер.
     */
    private static boolean onlyLastingTokens(GameState s) {
        return Boolean.TRUE.equals(kelium.dataio.Ctx.rules(s)
            .get("containers.printed_only_lasting_tokens", Boolean.FALSE));
    }

    /** Берёт ли добытчик печатный контейнер вообще (ветку можно выключить). */
    public static boolean miningBranchOn(GameState s) {
        return !Boolean.FALSE.equals(kelium.dataio.Ctx.rules(s)
            .get("containers.printed_mining_branch", Boolean.TRUE));
    }

    /** Отметить ячейку выгоревшей, если правило включено. */
    private static void markTaken(GameState s, Hex h) {
        if (h != null && burnsOut(s)) {
            h.containerTaken = true;
        }
    }

    /**
     * ДОБЫЧА забрала печатный контейнер с этого гекса — отметить ячейку собранной
     * (при включённом выгорании). Точка нужна потому, что ветка контейнера в Добыче
     * выдаёт карту сама, минуя выдачу за накрытие ячейки.
     */
    public static void markMined(GameState s, String hexId) {
        markTaken(s, hexId == null ? null : s.field.get(hexId));
    }

    /** Уже собран ли контейнер с этой ячейки (при включённом выгорании). */
    private static boolean alreadyTaken(GameState s, Hex h) {
        return h != null && h.containerTaken && burnsOut(s);
    }

    /**
     * Есть ли на гексе жетоны игроков, кроме жетона с указанным uid (null — не
     * исключать никого). Нейтральные постройки и тайлы зарождения не считаются:
     * правило говорит именно про жетоны ИГРОКОВ.
     */
    private static boolean hasPlayerTokens(GameState s, String hexId, Integer exceptUid) {
        for (int seat = 0; seat < s.numPlayers(); seat++) {
            PlayerState ps = s.player(seat);
            for (kelium.core.UnitToken u : ps.unitsOnField()) {
                if (hexId.equals(u.hexId) && (exceptUid == null || u.uid != exceptUid)) {
                    return true;
                }
            }
            for (BuildingToken b : ps.buildingsOnField()) {
                if (hexId.equals(b.hexId) && (exceptUid == null || b.uid != exceptUid)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * То же для ПРИШЕДШЕГО войска: к моменту вызова оно уже стоит на гексе, и его
     * uid здесь неизвестен, поэтому «пусто» означает «ровно один жетон — этот».
     */
    private static boolean hasOtherPlayerTokensThanTheArrival(GameState s, String hexId) {
        int count = 0;
        for (int seat = 0; seat < s.numPlayers(); seat++) {
            PlayerState ps = s.player(seat);
            for (kelium.core.UnitToken u : ps.unitsOnField()) {
                if (hexId.equals(u.hexId)) {
                    count++;
                }
            }
            for (BuildingToken b : ps.buildingsOnField()) {
                if (hexId.equals(b.hexId)) {
                    count++;
                }
            }
            if (count > 1) {
                return true;
            }
        }
        return count > 1;
    }

    /**
     * Войско оказалось в гексе: выдать контейнер, если оно накрывает печатную
     * ячейку. Возвращает, сколько карт получено (0 или 1).
     */
    private static int grantForUnit(GameState s, PlayerState p, String hexId,
                                    UnitType type, String source) {
        Hex h = hexId == null ? null : s.field.get(hexId);
        if (h == null || h.containerCell < 0) {
            return 0;
        }
        if (requiresEmptyHex(s) && hasOtherPlayerTokensThanTheArrival(s, hexId)) {
            return 0;   // доработка 13.08.2026: гекс должен быть пуст от жетонов
        }
        if (alreadyTaken(s, h)) {
            return 0;   // ячейка уже отдала свой контейнер
        }
        // ТОЛЬКО ЖЕТОНОМ, КОТОРЫЙ ОСТАЁТСЯ (правило-вариант
        // containers.printed_only_lasting_tokens). Дизайнер против выгорания
        // ячеек: оно требует маркеров на поле. А здание, накрывшее ячейку,
        // маркирует её САМО СОБОЙ — оно никуда не уходит, и повторно доить ячейку
        // нечем. Войско же приходит и уходит, поэтому при этом правиле движение и
        // найм контейнера не дают.
        if (onlyLastingTokens(s)) {
            return 0;
        }
        boolean air = h.containerCell == BlockStamp.AIR;
        if (type == UnitType.AIRCRAFT) {
            if (!air) {
                return 0;   // авиация садится только в воздушную ячейку
            }
        } else {
            if (air) {
                return 0;   // наземному до воздушной ячейки не дотянуться
            }
            // ячейка занята зданием или стенкой нейтрала — встать нельзя
            if (h.sideOwner[h.containerCell] != null) {
                return 0;
            }
        }
        markTaken(s, h);
        return grant(s, p, source);
    }

    /**
     * Здание построено или перенесено: выдать контейнер, если его след накрыл
     * печатную ячейку.
     */
    public static int onBuildingPlaced(GameState s, PlayerState p, BuildingToken b) {
        Hex h = b.hexId == null ? null : s.field.get(b.hexId);
        if (h == null || h.containerCell < 0 || h.containerCell == BlockStamp.AIR) {
            return 0;
        }
        // Доработка 13.08.2026: гекс должен быть пуст от жетонов игроков. Само
        // ставимое здание не считается — оно и есть тот жетон, который накрывает.
        if (requiresEmptyHex(s) && hasPlayerTokens(s, b.hexId, b.uid)) {
            return 0;
        }
        if (alreadyTaken(s, h)) {
            return 0;
        }
        Integer owner = h.sideOwner[h.containerCell];
        if (owner == null || owner != b.uid) {
            return 0;
        }
        markTaken(s, h);
        return grant(s, p, "стройка");
    }

    /**
     * ВИДЕН ли на гексе печатный контейнер — то есть нарисован и не накрыт
     * жетоном. Нужен Добыче: добытчик берёт контейнер только с ОТКРЫТОЙ ячейки
     * на своём гексе или на примыкающем (правило дизайнера 12.08.2026).
     */
    public static boolean visibleContainer(GameState s, Hex h) {
        if (h == null || h.containerCell < 0 || alreadyTaken(s, h)) {
            return false;
        }
        // ПРАВИЛО ВИДИМОСТИ (уточнение дизайнера 13.08.2026): если контейнер
        // ЧЕМ-ТО НАКРЫТ — хоть жетоном игрока, хоть нейтральным зданием, — для
        // добытчика его как будто и нет. Иначе получался бездонный источник: добытчик, стоящий
        // не у грядки, каждый раунд доил одну и ту же ячейку (замер: 39 контейнеров
        // за партию из 83, и во ВСЕХ случаях выбора «вместо келемия» не было —
        // просто рядом не было живой грядки).
        if (h.hasNeutral() || h.hasSpawnTile()) {
            return false;
        }
        if (h.containerCell == BlockStamp.AIR) {
            // воздушную ячейку зданиями не накрыть, но авиация её занимает
            return h.airToken == null;
        }
        // Войска в модели ячеек не значатся (sideOwner заполняют только здания и
        // стенки нейтралов), поэтому считаем консервативно: есть на гексе наземные
        // жетоны — ячейка может быть под ними, контейнера не видно.
        return groundContainerFree(s, h) && h.groundTokens.isEmpty();
    }

    /**
     * Гекс с ВИДИМЫМ контейнером, до которого добытчик ДОТЯГИВАЕТСЯ: его
     * собственный гекс либо тот, что лежит за ЕГО СТЕНКОЙ — ровно так же, как
     * он примыкает к тайлу зарождения. null, если брать нечего.
     *
     * <p>Считать по всем шести соседям гекса нельзя: добытчик занимает одну
     * ячейку и «смотрит» только в одну сторону (баг найден дизайнером
     * 12.08.2026 на добыче келемия и правится здесь заодно).
     */
    public static String minableContainerHex(GameState s, BuildingToken miner) {
        Hex self = miner.hexId == null ? null : s.field.get(miner.hexId);
        if (self == null) {
            return null;
        }
        if (visibleContainer(s, self)) {
            return miner.hexId;
        }
        for (int side = 0; side < 6; side++) {
            if (self.sideOwner[side] == null || self.sideOwner[side] != miner.uid) {
                continue;
            }
            String nbId = self.neighborBySide[side];
            if (nbId != null && visibleContainer(s, s.field.get(nbId))) {
                return nbId;
            }
        }
        return null;
    }

    /** Есть ли на гексе печатная ячейка, СВОБОДНАЯ под наземный жетон. */
    public static boolean groundContainerFree(GameState s, Hex h) {
        if (h == null || h.containerCell < 0 || h.containerCell == BlockStamp.AIR) {
            return false;
        }
        // sideOwner заполняют только ЖЁСТКИЕ жетоны — здания игроков и стенки
        // нейтралов; войска в нём не значатся. Значит любой владелец = ячейка
        // накрыта, контейнера не видно.
        return h.sideOwner[h.containerCell] == null;
    }

    private static int grant(GameState s, PlayerState p, String source) {
        int got = Storage.addContainersCapped(s, p, 1, "печатная ячейка: " + source);
        if (got > 0) {
            if (s.journal != null) {
                s.journal.of(p.seat).containersPickedByUnit += got;
            }
            STATS.computeIfAbsent(source,
                k -> new java.util.concurrent.atomic.LongAdder()).add(got);
        }
        return got;
    }
}
