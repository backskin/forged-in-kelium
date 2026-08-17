package kelium.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Состояние игрока — всё, чем владеет одно «место» (seat) в течение партии.
 *
 * <p>Незыблемое ядро: описывает состав имущества игрока. Стартовые значения
 * берутся из правил/расстановки, а не зашиты здесь. Сюда входят ресурсы, жетоны
 * на поле и в резерве, трофейное пространство, руки карт (задания, приказы,
 * арсенал), модули, прогресс по науке, контейнеры и жетоны разрушения ЦУ.
 */
public final class PlayerState {

    public final int seat;
    public final PlayerBoard board;
    public Resources resources;
    public String startHex = "";              // id стартового гекса игрока (ЦУ)

    // Жетоны на поле или в резерве (резерв = hexId == null).
    public final List<UnitToken> units = new ArrayList<>();
    public final List<BuildingToken> buildings = new ArrayList<>();

    // Трофейное пространство: вражеские жетоны, уничтоженные этим игроком В ЭТОМ
    // раунде, перевёрнутые на трофейную сторону. Каждый несёт trophyValue очков.
    // Тратятся на треки через Науку по полной ценности; несданный жетон в
    // Возврат даёт ровно 1 обломок (resources.debris, флат, не по ценности) и
    // возвращается владельцу (уничтожение временно). Правило 2026-08-15.
    public final List<Token> trophySpace = new ArrayList<>();

    /**
     * ТРОФЕИ, ЗАДЕРЖАННЫЕ НА КАРТАХ АРСЕНАЛА («Трофейный склад», b13).
     *
     * <p>На карте нарисована ячейка под ОДИН трофейный жетон. В этап Возврата
     * жетон кладётся сюда вместо возврата владельцу и лежит здесь раунд; если
     * ячейка уже занята, лежавший жетон уходит владельцу, освобождая место.
     *
     * <p>Смысл эффекта — не в очках, а в отказе: пока чужой жетон лежит здесь,
     * владелец не может выставить его заново, потому что личный запас у каждого
     * ровно по четыре жетона рода.
     */
    public final List<Token> trophyHeldOnCards = new ArrayList<>();

    // Руки/установленные карты (id из контента).
    public List<String> orderHand = new ArrayList<>();
    public final List<String> orderPlayed = new ArrayList<>();   // вскрыто в этом раунде
    public String orderSetAside = null;                          // слепо сброшенная карта
    public String orderColor = null;                             // цвет колоды приказов игрока
    public final List<String> objectiveHand = new ArrayList<>();
    public final List<String> arsenalHand = new ArrayList<>();       // рубашкой вверх
    public final List<String> arsenalInstalled = new ArrayList<>();  // до 3 слотов
    /**
     * РЕСУРС, ЛЕЖАЩИЙ НА УСТАНОВЛЕННОЙ КАРТЕ АРСЕНАЛА: id карты → сколько
     * келемия на ней сейчас. Ключ по id карты корректен, потому что правило
     * модуля запрещает держать открытыми две ОДИНАКОВЫЕ технологии, — значит
     * у игрока не может быть двух установленных карт с одним id.
     *
     * <p>Нужно картам, которые копят ресурс на себе («Резервный штаб»: перенеси
     * келемий на карту, потом сожги, чтобы обойти блокировку приказа). Сама
     * способность в реестре — одна на процесс, поэтому хранить это состояние в
     * ней нельзя: оно принадлежит игроку, а не способности.
     */
    public final Map<String, Integer> arsenalCardKelium = new HashMap<>();
    public String superObjective = null;
    /**
     * ПРЕДЛОЖЕНИЕ супер заданий: карты, разданные игроку на выбор (правило 2.0,
     * 12.08.2026 — раздаём две, игрок оставляет одну). Пока выбор не сделан,
     * {@link #superObjective} равен null.
     */
    public final List<String> superObjectiveOffer = new ArrayList<>();
    /**
     * ПРЕДЛОЖЕНИЕ НАЧАЛЬНЫХ ЗАДАНИЙ (режим «начальные задания», 12.08.2026):
     * работает так же, как супер задания — игрок берёт две карты, одну
     * оставляет, вторую сбрасывает. Выбранная уходит в {@link #objectiveHand}.
     */
    public final List<String> startObjectiveOffer = new ArrayList<>();
    public int superObjectiveProgress = 0;
    public boolean superObjectiveComplete = false;

    // ======================================================================
    //  СУПЕРОРУЖИЕ (супер задания 3.0, решение дизайнера 17.08.2026)
    // ======================================================================
    /**
     * СКОЛЬКО ЯЧЕЕК КАРТЫ СУПЕР ЗАДАНИЯ ЕЩЁ ЗАНЯТО. −1 — карта не вскрыта.
     *
     * <p>Карта вскрывается одним СПЕЦ-действием, и на неё сразу ложится всё
     * требуемое: четыре ячейки, четыре взноса. Дальше игрок снимает содержимое
     * по одной ячейке за СПЕЦ, не чаще раза за круг, — это и есть счётчик
     * запуска. Снял последнюю — выиграл партию.
     */
    public int superCells = -1;
    /** Жетон супероружия: uid, пока он существует (в запасе или на поле). */
    public Integer superWeaponUid = null;
    /**
     * ГЕКС ЗДАНИЯ, ГДЕ ЖЕТОН СУПЕРОРУЖИЯ БЫЛ НАНЯТ. Снимать ячейки можно, только
     * пока жетон стоит НЕ на нём: оружие должно выехать со стапеля, а не
     * запускаться прямо из цеха.
     */
    public String superWeaponHiredHex = null;
    /**
     * Круг, в котором игрок последний раз снимал ячейку. Снятие разрешено не
     * чаще раза за круг, поэтому четыре ячейки — это минимум четыре круга.
     */
    public int superLastLaunchCircle = -1;
    /**
     * ГЕКСЫ, С КОТОРЫХ УЖЕ ЗАПУСКАЛИ. Каждое снятие ячейки требует НОВОГО гекса:
     * оружие обязано переезжать, а не стоять четыре круга на одном месте.
     */
    public final java.util.Set<String> superLaunchedFrom = new java.util.LinkedHashSet<>();
    /**
     * ПОБЕДНЫЕ ОЧКИ ЗА ПЕРВУЮ ЧАСТЬ супер задания (решение дизайнера 13.08.2026):
     * 2–5 очков по стоимости того, что сдано в лицо карты. Начисляются один раз, в
     * момент, когда лицо собрано целиком, и остаются у игрока даже если рубашка
     * так и не сложится, — иначе вложенное в первую часть пропадает зря.
     */
    public int superFirstPartVp = 0;

    // Модули в резерве (ещё не размещены). red/blue; gold = позолочённые.
    public int redModules = 0;
    public int blueModules = 0;
    /**
     * КОНКРЕТНЫЕ ЖЕТОНЫ, вытянутые из мешка («Модули 2.0», 12.08.2026): id из
     * наборов {@code data/modules/…}. Пока мешки выключены версией правил, списки
     * пусты и работают прежние счётчики — игрок выбирает любой жетон комплекта.
     * С мешками выбор сужается до того, что реально вытянуто.
     */
    public final List<String> redTokens = new ArrayList<>();
    public final List<String> blueTokens = new ArrayList<>();
    public int goldModules = 0;

    // Размещения модулей на доске (выбираются в фазе смены модулей):
    // redPlacements[UnitType] = модуль поверх вторичного ряда атаки этого юнита.
    // bluePlacements[BuildingType] = модуль поверх сборочной «1» этого здания.
    public final Map<UnitType, Map<String, Object>> redPlacements = new HashMap<>();
    public final Map<BuildingType, Map<String, Object>> bluePlacements = new HashMap<>();

    // Прогресс по науке: {trackId: достигнутый шаг}.
    public final Map<String, Integer> techSteps = new HashMap<>();

    // Жетоны модуля хранилища (треки 2.0): у игрока 2 личных двусторонних
    // жетона; при установке выбирается сторона НАВСЕГДА. Элементы списка:
    // "+1_universal_cell" (ячейка под келемий ИЛИ боеприпас) или "+1_energy"
    // (вечный универсальный кубик энергии). Источник — только зелёный трек.
    public final List<String> storageTokens = new ArrayList<>();

    // Карты супер-арсенала, взятые с вершин треков (открытые, до конца партии).
    public final List<String> superArsenalCards = new ArrayList<>();

    // B5: прогресс сборки супер-задания ПО ЧАСТЯМ (kind -> внесено), чтобы
    // нельзя было закрыть карту пятикратной сдачей одной дешёвой части.
    public final Map<String, Integer> superPartProgress = new HashMap<>();

    // Контейнеры на руках (не вскрытые). Вскрытие — бесплатное действие.
    public int containers = 0;

    /**
     * ЖЕТОНЫ ЩИТА, ЛЕЖАЩИЕ НА СТРОКАХ ПЛАНШЕТА ВОЙСК (эффект «щит», 17.08.2026).
     *
     * <p>Щит — физический жетон, он кладётся на строку РОДА войск и снимает
     * ПЕРВОЕ попадание по любому жетону этого рода, после чего уходит. Правило
     * «эффект действует, пока на столе лежит объект, который о нём сообщает»
     * (СВОД §9.1) соблюдено: объект — сам жетон щита.
     *
     * <p>Заведено потому, что прежняя редакция утиля предлагала «снять 1 урон с
     * пехоты», а у пехоты прочность 1: снимать там нечего, жетон уже уничтожен.
     * Защита должна работать ДО попадания, а не после.
     */
    public final java.util.Set<UnitType> shieldedKinds = java.util.EnumSet.noneOf(UnitType.class);

    /**
     * «МАНДАТ СОВЕТА» (супер-арсенал sa8): поверх карты есть место для ОДНОЙ
     * карты арсенала либо до ДВУХ контейнеров. Она лежит здесь ТАК ЖЕ, как в
     * обычной ячейке планшета, и работает по своим правилам — но за отдельный
     * слот, вытесняя туда лишнюю карту не из обычных трёх.
     */
    public String mandateArsenalCard = null;
    public int mandateContainers = 0;

    /**
     * ВСЕ УСТАНОВЛЕННЫЕ КАРТЫ АРСЕНАЛА: обычные три слота плюс карта под
     * «Мандатом совета», если она там лежит. Карта под мандатом «работает по
     * своим правилам» (текст sa8) — то есть ровно как обычная установленная
     * карта, — поэтому всякая точка движка, что спрашивает «какие пассивки/
     * символы/бонусы сейчас действуют у игрока», обязана видеть и её. Слотовое
     * управление (лимит трёх, вытеснение) по-прежнему смотрит на
     * {@link #arsenalInstalled} НАПРЯМУЮ — мандат отдельный слот, не четвёртый
     * такой же.
     */
    public List<String> allInstalledArsenal() {
        if (mandateArsenalCard == null) {
            return arsenalInstalled;
        }
        List<String> out = new ArrayList<>(arsenalInstalled);
        out.add(mandateArsenalCard);
        return out;
    }

    /**
     * КАРТА, ПОДЛОЖЕННАЯ ПОД ПЛАНШЕТ ВОЙСК ради СИМВОЛА супер задания
     * (правило «Супер задания 2.0», 12.08.2026).
     *
     * <p>Подложить карту — свободно, она уходит под планшет ЗАКРЫТОЙ. Вскрыть —
     * только СПЕЦ-действием. Контейнер под планшетом своего бонуса НЕ даёт:
     * либо бонус (тогда карта сгорает), либо символ.
     */
    public static final class TuckedCard {
        /** {@code container} или {@code arsenal}. */
        public final String kind;
        public final String cardId;
        public boolean revealed;

        public TuckedCard(String kind, String cardId) {
            this.kind = kind;
            this.cardId = cardId;
        }
    }

    /** Карты под планшетом войск (символы супер задания). */
    public final List<TuckedCard> tucked = new ArrayList<>();

    // Жетоны разрушения ЦУ, которые ЭТОТ игрок держит (взяты у жертв). Каждый = 2 ПО;
    // держание 2-го запускает мгновенную военную победу.
    public int cuDestructionTokens = 0;
    // СОБСТВЕННЫЙ жетон разрушения игрока — стартует у него, отдаётся тому, кто
    // первым разрушит его ЦУ. После отдачи повторное разрушение не даёт ничего.
    public boolean ownCuTokenAvailable = true;

    /** Сколько тайлов зарождения выработано ДО КОНЦА (оборот) — всего. */
    public int claimedSpawnTiles = 0;
    /**
     * Выработанные ДО КОНЦА тайлы по видам. Победное очко даёт только БОЛЬШОЕ
     * зарождение (обычный тайл): игрок сохраняет его в запасе как очко. Малое
     * (стартовое) даёт только трофейные очки — правило дизайнера 12.08.2026.
     */
    public int claimedStartTiles = 0;
    public int claimedNormalTiles = 0;
    /**
     * Сколько тайлов исчерпано ПО ЛИЦУ (первый переворот). Победных очков за это
     * НЕ даётся — только трофейные. Счётчики нужны отчётам и признакам позиции.
     */
    public int flippedStartTiles = 0;
    public int flippedNormalTiles = 0;
    // «Военный трек» (эксперимент): ПО за несданные трофеи в конце раунда.
    public int warTrackVp = 0;
    /**
     * Очки, начисленные КАРТАМИ-ОБЪЕКТАМИ напрямую ({@link
     * kelium.engine.cards.CardContext#grantVp}) — например наградой задания за
     * прямой пункт {@code vp} в данных. Отдельное поле по тому же образцу, что
     * {@link #warTrackVp} и {@link #superFirstPartVp}: копится по ходу партии,
     * суммируется в {@code Scoring}.
     */
    public int objectiveCardVp = 0;
    // Сколько ЦУ уничтожил этот игрок (ЛЮБЫХ: второй снос = военная победа).
    public int cuKills = 0;
    /**
     * СКОЛЬКО ЧУЖИХ ЖЕТОНОВ УНИЧТОЖИЛ за партию (нарастающий счётчик, не
     * обнуляется в Возврат — в отличие от трофейного пространства).
     *
     * <p>Нужен балансовому стенду ({@code kelium.RuleExperiment}): по правилам
     * уничтожение НЕ даёт победных очков напрямую, и проверить «а если давать»
     * без такого счётчика нельзя. Соответствующий ключ правил
     * {@code economy.vp_per_kill} по умолчанию 0 — то есть поведение игры не
     * меняется, пока его явно не включат в опыте.
     */
    public int killsTotal = 0;

    public PlayerState(int seat, PlayerBoard board, Resources resources, String startHex) {
        this.seat = seat;
        this.board = board;
        this.resources = resources;
        this.startHex = startHex;
    }

    /**
     * Копия состояния игрока БЕЗ трофейного пространства: жетоны копируются и
     * регистрируются в {@code registry} по своему uid. Трофейное пространство
     * доwiring-ается вторым проходом в {@link GameState#deepCopy(long)}, потому
     * что там лежат ЧУЖИЕ жетоны — их копии создаёт другой игрок, и подменять их
     * новыми объектами нельзя (движок возвращает владельцу тот же жетон).
     *
     * <p>Доска ({@link PlayerBoard}) и характеристики жетонов не копируются
     * намеренно: они неизменяемы за партию и общие для всех копий.
     */
    public PlayerState copyWithoutTrophies(Map<Integer, Token> registry) {
        PlayerState p = new PlayerState(seat, board, resources.copy(), startHex);
        for (UnitToken u : units) {
            UnitToken c = u.copy();
            p.units.add(c);
            registry.put(c.uid, c);
        }
        for (BuildingToken b : buildings) {
            BuildingToken c = b.copy();
            p.buildings.add(c);
            registry.put(c.uid, c);
        }
        p.orderHand = new ArrayList<>(orderHand);
        p.orderPlayed.addAll(orderPlayed);
        p.orderSetAside = orderSetAside;
        p.orderColor = orderColor;
        p.objectiveHand.addAll(objectiveHand);
        p.arsenalHand.addAll(arsenalHand);
        p.arsenalInstalled.addAll(arsenalInstalled);
        p.arsenalCardKelium.putAll(arsenalCardKelium);
        p.superObjective = superObjective;
        p.superObjectiveOffer.addAll(superObjectiveOffer);
        p.startObjectiveOffer.addAll(startObjectiveOffer);
        p.superObjectiveProgress = superObjectiveProgress;
        p.superObjectiveComplete = superObjectiveComplete;
        p.superCells = superCells;
        p.superWeaponUid = superWeaponUid;
        p.superWeaponHiredHex = superWeaponHiredHex;
        p.superLastLaunchCircle = superLastLaunchCircle;
        p.superLaunchedFrom.addAll(superLaunchedFrom);
        p.superFirstPartVp = superFirstPartVp;
        p.redModules = redModules;
        p.blueModules = blueModules;
        p.redTokens.addAll(redTokens);
        p.blueTokens.addAll(blueTokens);
        p.goldModules = goldModules;
        for (Map.Entry<UnitType, Map<String, Object>> e : redPlacements.entrySet()) {
            p.redPlacements.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        for (Map.Entry<BuildingType, Map<String, Object>> e : bluePlacements.entrySet()) {
            p.bluePlacements.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        p.techSteps.putAll(techSteps);
        // Карты под планшетом — изменяемое состояние (их вскрывают СПЕЦ-действием),
        // поэтому копируются САМИ КАРТЫ, а не ссылки на них.
        for (TuckedCard t : tucked) {
            TuckedCard c = new TuckedCard(t.kind, t.cardId);
            c.revealed = t.revealed;
            p.tucked.add(c);
        }
        p.storageTokens.addAll(storageTokens);
        p.superArsenalCards.addAll(superArsenalCards);
        p.superPartProgress.putAll(superPartProgress);
        p.containers = containers;
        p.shieldedKinds.addAll(shieldedKinds);
        p.mandateArsenalCard = mandateArsenalCard;
        p.mandateContainers = mandateContainers;
        p.cuDestructionTokens = cuDestructionTokens;
        p.ownCuTokenAvailable = ownCuTokenAvailable;
        p.claimedSpawnTiles = claimedSpawnTiles;
        p.claimedStartTiles = claimedStartTiles;
        p.claimedNormalTiles = claimedNormalTiles;
        p.flippedStartTiles = flippedStartTiles;
        p.flippedNormalTiles = flippedNormalTiles;
        p.warTrackVp = warTrackVp;
        p.cuKills = cuKills;
        p.killsTotal = killsTotal;
        return p;
    }

    /** Сумма трофейных очков жетонов, лежащих в трофейном пространстве. */
    /**
     * Сколько жетонов этого рода у игрока УЖЕ ЗАВЕДЕНО (не считая супер-войск с
     * карт: те печатаются отдельно и в личный запас рода не входят).
     *
     * <p>Нужно, чтобы соблюдать личный запас «по 4 жетона каждого рода». Считаем
     * все свои жетоны рода, где бы они ни лежали — на поле, в запасе или сейчас на
     * чужой карте трофеев: на этапе Возврата они вернутся, значит из запаса они не
     * исчезли.
     */
    public int unitsOfKind(kelium.core.UnitType type) {
        int n = 0;
        for (UnitToken u : units) {
            if (u.type == type && !u.superUnit) {
                n++;
            }
        }
        return n;
    }

    public int trophySpacePoints() {
        int sum = 0;
        for (Token t : trophySpace) {
            sum += t.trophyValue();
        }
        return sum;
    }

    /** Живые юниты игрока, стоящие на поле (не в резерве). */
    public List<UnitToken> unitsOnField() {
        List<UnitToken> out = new ArrayList<>();
        for (UnitToken u : units) {
            if (u.hexId != null && u.alive()) {
                out.add(u);
            }
        }
        return out;
    }

    /** Живые здания игрока, стоящие на поле (не в резерве). */
    public List<BuildingToken> buildingsOnField() {
        List<BuildingToken> out = new ArrayList<>();
        for (BuildingToken b : buildings) {
            if (b.hexId != null && b.alive()) {
                out.add(b);
            }
        }
        return out;
    }

    /** Есть ли у игрока живой командный центр (ЦУ) на поле. */
    public boolean hasCommandCenter() {
        for (BuildingToken b : buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                return true;
            }
        }
        return false;
    }
}
