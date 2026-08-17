package kelium.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.Ctx;

/**
 * СУПЕРОРУЖИЕ — вторая половина супер задания (решение дизайнера 17.08.2026).
 *
 * <p>ЧТО ОТМЕНЕНО. Прежняя рубашка требовала выложить на поле рисунок из
 * конкретных объектов и собрать три символа под планшетом войск. Рисунок убран
 * целиком: восемь разных фигур движок сводил к одному требованию «поставь
 * перечисленное связкой», а тексты карт обещали линии, кольца и треугольники —
 * за столом это спор о правилах, а не механика.
 *
 * <p>ЧТО ВМЕСТО НЕГО. Отсчёт запуска, как в стратегиях про супероружие:
 *
 * <ol>
 *   <li><b>ВСКРЫТИЕ</b> ({@link #canReveal}/{@link #reveal}) — одно
 *       СПЕЦ-действие, и на карту сразу ложится ВСЁ, что требуют её четыре
 *       ячейки. Частями вносить нельзя. Плюс условие: на одной из ОТКРЫТЫХ карт
 *       арсенала игрока должен быть символ, названный на карте. За вскрытие
 *       игрок получает победные очки и жетон супероружия в запас.</li>
 *   <li><b>ЗАПУСК</b> ({@link #canLaunch}/{@link #launch}) — СПЕЦ-действие,
 *       снимающее содержимое ОДНОЙ ячейки. Условий три: жетон супероружия стоит
 *       на поле, стоит НЕ на гексе того здания, где его наняли, и в этом круге
 *       игрок ещё не снимал. Четыре ячейки — минимум четыре круга.</li>
 *   <li><b>ПОБЕДА</b> — снял с последней занятой ячейки. Ход снятия свой, значит
 *       и победа наступает в свой ход: соперники видят счётчик и считают срок
 *       заранее.</li>
 * </ol>
 *
 * <p>ЖЕТОН МОЖНО СНЕСТИ. Уничтоженное супероружие возвращается на свою карту
 * ({@link #onWeaponDestroyed}), и счётчик встаёт: снимать ячейки нечем, пока
 * игрок не наймёт жетон заново.
 *
 * <p>ЖЕТОН НЕ АТАКУЕТ — см. {@link #isWeapon}. Он ходит как войско своего рода и
 * подчиняется всем эффектам этого рода, но таблица атак планшета ему недоступна:
 * ни в Бою, ни в ответном бою, ни эффектом карты.
 */
public final class SuperWeapon {

    private SuperWeapon() {
    }

    /** Сколько ячеек на карте (и, значит, сколько СПЕЦ-действий до победы). */
    public static int cellCount(GameState s, String cardId) {
        Map<String, Object> card = Ctx.cards(s, "super_objectives").find(cardId);
        return card != null && card.get("cells") instanceof List<?> l ? l.size() : 0;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cells(GameState s, String cardId) {
        Map<String, Object> card = Ctx.cards(s, "super_objectives").find(cardId);
        if (card == null || !(card.get("cells") instanceof List<?> l)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : l) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    // ======================================================================
    //  ВСКРЫТИЕ
    // ======================================================================

    /**
     * МОЖНО ЛИ ВСКРЫТЬ КАРТУ ПРЯМО СЕЙЧАС: карта есть, ещё не вскрыта, оплатимы
     * ВСЕ четыре ячейки разом и есть нужный символ на открытом арсенале.
     */
    public static boolean canReveal(GameState s, PlayerState p) {
        if (p.superObjective == null || p.superCells >= 0) {
            return false;
        }
        if (!hasRequiredSymbol(s, p)) {
            return false;
        }
        return canPayAll(s, p, cells(s, p.superObjective));
    }

    /**
     * ЕСТЬ ЛИ У ИГРОКА НУЖНЫЙ СИМВОЛ на ОТКРЫТОЙ карте арсенала.
     *
     * <p>Именно на открытой: закрытая карта на руке символа не показывает, а
     * смысл требования в том, чтобы игрок занял слот планшета под карту с нужной
     * формой — то есть заплатил за вскрытие ещё и местом.
     */
    public static boolean hasRequiredSymbol(GameState s, PlayerState p) {
        Map<String, Object> card = Ctx.cards(s, "super_objectives").find(p.superObjective);
        if (card == null || card.get("requires_symbol") == null) {
            return true;   // карта символа не требует
        }
        String need = String.valueOf(card.get("requires_symbol"));
        Symbols.Marking m = Symbols.of(s);
        for (String cid : p.allInstalledArsenal()) {
            if (need.equals(m.ofArsenal(cid))) {
                return true;
            }
        }
        return false;
    }

    /** Хватает ли на ВСЕ ячейки разом (частями вносить нельзя). */
    private static boolean canPayAll(GameState s, PlayerState p,
                                     List<Map<String, Object>> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        // Считаем ПОТРЕБНОСТЬ суммарно: две ячейки по келемию — это два келемия,
        // и проверять их поодиночке нельзя, иначе карта «вскроется» с одним.
        Map<String, Integer> need = new java.util.HashMap<>();
        for (Map<String, Object> cell : cells) {
            String kind = String.valueOf(cell.get("kind"));
            int amount = cell.get("amount") instanceof Number n ? n.intValue() : 1;
            need.merge(kind, amount, Integer::sum);
        }
        for (var e : need.entrySet()) {
            if (supply(s, p, e.getKey()) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Сколько единиц такого вида у игрока сейчас есть. */
    private static int supply(GameState s, PlayerState p, String kind) {
        switch (kind) {
            case "coin", "ammo", "kelium", "debris" -> {
                try {
                    return p.resources.get(Resource.fromCode(kind));
                } catch (RuntimeException e) {
                    return 0;
                }
            }
            case "enemy_unit_token" -> {
                return countTrophies(p, true, false);
            }
            case "enemy_building_token" -> {
                return countTrophies(p, false, true);
            }
            case "enemy_token" -> {
                return countTrophies(p, true, true);
            }
            case "own_building" -> {
                return ownBuildings(p, null).size();
            }
            case "own_miner" -> {
                return ownBuildings(p, BuildingType.MINER).size();
            }
            case "own_power_plant" -> {
                return ownBuildings(p, BuildingType.POWER_PLANT).size();
            }
            case "own_unit" -> {
                return ownSacrificeableUnits(p).size();
            }
            default -> {
                return 0;
            }
        }
    }

    private static int countTrophies(PlayerState p, boolean units, boolean buildings) {
        int n = 0;
        for (Token t : p.trophySpace) {
            if (t instanceof UnitToken ? units : buildings) {
                n++;
            }
        }
        return n;
    }

    /** Свои здания на поле, кроме ЦУ (ЦУ в жертву не идёт: это выбывание). */
    private static List<BuildingToken> ownBuildings(PlayerState p, BuildingType type) {
        List<BuildingToken> out = new ArrayList<>();
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                continue;
            }
            if (type == null || b.type == type) {
                out.add(b);
            }
        }
        return out;
    }

    /** Свои войска на поле, кроме самого супероружия. */
    private static List<UnitToken> ownSacrificeableUnits(PlayerState p) {
        List<UnitToken> out = new ArrayList<>();
        for (UnitToken u : p.unitsOnField()) {
            if (p.superWeaponUid == null || u.uid != p.superWeaponUid) {
                out.add(u);
            }
        }
        return out;
    }

    /**
     * ВСКРЫТЬ КАРТУ: списать всё, что требуют ячейки, выдать победные очки и
     * положить жетон супероружия в запас игрока.
     *
     * @return сколько победных очков начислено (0 — вскрыть не удалось)
     */
    public static int reveal(GameState s, PlayerState p) {
        if (!canReveal(s, p)) {
            return 0;
        }
        // ПОРЯДОК ОПЛАТЫ ЗНАЧИМ, и это не косметика. Возврат добытчика или
        // энергостанции ЗАКРЫВАЕТ их ячейки склада, а вместе с ними срезается и
        // всё, что в этих ячейках лежало. Заплати сперва зданием — и обещанных
        // шести боеприпасов на карте уже не окажется, хотя минуту назад они были.
        // Поэтому: сперва ресурсы из хранилища, потом трофеи, и только затем
        // жетоны с поля.
        List<Map<String, Object>> ordered = new ArrayList<>(cells(s, p.superObjective));
        ordered.sort(java.util.Comparator.comparingInt(
            c -> payOrder(String.valueOf(c.get("kind")))));
        for (Map<String, Object> cell : ordered) {
            pay(s, p, String.valueOf(cell.get("kind")),
                cell.get("amount") instanceof Number n ? n.intValue() : 1);
        }
        p.superCells = cellCount(s, p.superObjective);
        p.superObjectiveComplete = true;

        Map<String, Object> card = Ctx.cards(s, "super_objectives").find(p.superObjective);
        int vp = card != null && card.get("vp_on_reveal") instanceof Number n ? n.intValue() : 0;
        p.superFirstPartVp = vp;

        // ЖЕТОН СУПЕРОРУЖИЯ — ПЯТЫЙ ЖЕТОН СВОЕГО РОДА сверх личного запаса.
        // Помечен superUnit: движок уже умеет выпускать такие жетоны Сборкой
        // первыми и не считать их в запас рода.
        UnitType type = weaponUnit(s, p.superObjective);
        if (type != null) {
            UnitToken w = s.tokenStats.makeUnit(type, p.seat, Placement.nextUid(s));
            w.superUnit = true;
            w.hexId = null;              // лежит на карте, ждёт найма
            p.units.add(w);
            p.superWeaponUid = w.uid;
        }
        return vp;
    }

    /** Род войск, к которому принадлежит жетон супероружия этой карты. */
    public static UnitType weaponUnit(GameState s, String cardId) {
        Map<String, Object> card = Ctx.cards(s, "super_objectives").find(cardId);
        if (card == null || card.get("weapon_unit") == null) {
            return null;
        }
        try {
            return UnitType.fromCode(String.valueOf(card.get("weapon_unit")));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Очередь оплаты: 0 — ресурсы, 1 — трофеи, 2 — жетоны с поля. */
    private static int payOrder(String kind) {
        return switch (kind) {
            case "coin", "ammo", "kelium", "debris" -> 0;
            case "enemy_unit_token", "enemy_building_token", "enemy_token" -> 1;
            default -> 2;
        };
    }

    /** Списать одну ячейку. Жетоны уходят ИЗ ИГРЫ — ни владельцу, ни в запас. */
    private static void pay(GameState s, PlayerState p, String kind, int amount) {
        switch (kind) {
            case "coin", "ammo", "kelium", "debris" -> p.resources.pay(Resource.fromCode(kind), amount);
            case "enemy_unit_token" -> takeTrophies(p, amount, true, false);
            case "enemy_building_token" -> takeTrophies(p, amount, false, true);
            case "enemy_token" -> takeTrophies(p, amount, true, true);
            case "own_building" -> takeBuildings(s, p, amount, null);
            case "own_miner" -> takeBuildings(s, p, amount, BuildingType.MINER);
            case "own_power_plant" -> takeBuildings(s, p, amount, BuildingType.POWER_PLANT);
            case "own_unit" -> {
                int left = amount;
                for (UnitToken u : ownSacrificeableUnits(p)) {
                    if (left == 0) {
                        break;
                    }
                    u.setHexId(null);
                    p.units.remove(u);   // из игры насовсем
                    left--;
                }
            }
            default -> { }
        }
    }

    private static void takeTrophies(PlayerState p, int amount, boolean units, boolean buildings) {
        int left = amount;
        for (Token t : new ArrayList<>(p.trophySpace)) {
            if (left == 0) {
                break;
            }
            if (t instanceof UnitToken ? units : buildings) {
                p.trophySpace.remove(t);   // из игры насовсем, владельцу не вернётся
                left--;
            }
        }
    }

    private static void takeBuildings(GameState s, PlayerState p, int amount, BuildingType type) {
        int left = amount;
        for (BuildingToken b : ownBuildings(p, type)) {
            if (left == 0) {
                break;
            }
            Actions.returnOwnBuildingToReserve(s, p, b, true);
            p.buildings.remove(b);   // из игры насовсем, а не в запас
            left--;
        }
    }

    // ======================================================================
    //  ЗАПУСК
    // ======================================================================

    /**
     * МОЖНО ЛИ СНЯТЬ ЯЧЕЙКУ. Условий пять, и все они про одно: оружие должно
     * ЕЗДИТЬ, а не стоять (уточнение дизайнера 17.08.2026 — «нанял и стой себе
     * четыре круга» слишком легко).
     *
     * <ol>
     *   <li>карта вскрыта и занятые ячейки ещё остались;</li>
     *   <li>ЖЕТОН СУПЕРОРУЖИЯ СТОИТ НА ПОЛЕ — в запасе или снесённый он ничего
     *       не снимает, и счётчик встаёт;</li>
     *   <li>жетон стоит НЕ на гексе того здания, где его наняли;</li>
     *   <li><b>жетон стоит НЕ на гексе со своим зданием вообще</b> — оружие
     *       выехало из базы и стоит открыто, где его можно снести;</li>
     *   <li><b>с ЭТОГО гекса ещё не запускали</b> — каждое снятие требует нового
     *       места, значит между кругами жетон обязан переехать;</li>
     *   <li>в этом круге игрок ещё не снимал.</li>
     * </ol>
     *
     * <p>Вместе это значит: четыре снятия — четыре РАЗНЫХ гекса вне своей базы, и
     * между ними надо тратить Движение. Оружие всю дорогу стоит под ударом, и у
     * соперников есть и время, и повод его снести.
     */
    public static boolean canLaunch(GameState s, PlayerState p) {
        if (p.superCells <= 0 || p.superWeaponUid == null) {
            return false;
        }
        if (p.superLastLaunchCircle == s.circle) {
            return false;   // не чаще раза за круг
        }
        UnitToken w = weaponToken(p);
        if (w == null || w.hexId == null) {
            return false;   // жетон в запасе или снесён
        }
        if (p.superWeaponHiredHex != null && p.superWeaponHiredHex.equals(w.hexId)) {
            return false;   // не выехал со стапеля
        }
        if (p.superLaunchedFrom.contains(w.hexId)) {
            return false;   // с этого гекса уже запускали — надо переехать
        }
        for (BuildingToken b : p.buildingsOnField()) {
            if (w.hexId.equals(b.hexId)) {
                return false;   // прячется в своей базе
            }
        }
        return true;
    }

    /** Жетон супероружия игрока, если он существует. */
    public static UnitToken weaponToken(PlayerState p) {
        if (p.superWeaponUid == null) {
            return null;
        }
        for (UnitToken u : p.units) {
            if (u.uid == p.superWeaponUid) {
                return u;
            }
        }
        return null;
    }

    /**
     * СНЯТЬ ОДНУ ЯЧЕЙКУ. Возвращает true, если это была ПОСЛЕДНЯЯ занятая — то
     * есть игрок выиграл партию.
     */
    public static boolean launch(GameState s, PlayerState p) {
        if (!canLaunch(s, p)) {
            return false;
        }
        UnitToken w = weaponToken(p);
        p.superCells -= 1;
        p.superLastLaunchCircle = s.circle;
        if (w != null && w.hexId != null) {
            p.superLaunchedFrom.add(w.hexId);
        }
        return p.superCells == 0;
    }

    // ======================================================================
    //  ЖЕТОН
    // ======================================================================

    /** Этот ли жетон — супероружие своего владельца. */
    public static boolean isWeapon(GameState s, UnitToken u) {
        if (u == null || u.owner() < 0 || u.owner() >= s.numPlayers()) {
            return false;
        }
        Integer uid = s.player(u.owner()).superWeaponUid;
        return uid != null && uid == u.uid;
    }

    /**
     * ЖЕТОН СУПЕРОРУЖИЯ УНИЧТОЖЕН — он возвращается на свою карту, и счётчик
     * встаёт: снимать ячейки нечем, пока игрок не наймёт его заново.
     *
     * <p>Сам жетон при этом НЕ исчезает из списка: он ложится в запас владельца,
     * как любое уничтоженное войско, и его можно нанять снова.
     */
    public static void onWeaponDestroyed(GameState s, PlayerState owner) {
        owner.superWeaponHiredHex = null;
    }

    /** Отметить гекс найма: с него запускать нельзя, оружие должно выехать. */
    public static void onWeaponHired(PlayerState owner, String hexId) {
        owner.superWeaponHiredHex = hexId;
    }
}
