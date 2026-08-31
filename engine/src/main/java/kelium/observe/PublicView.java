package kelium.observe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.core.GameState;
import kelium.report.Json;
import kelium.report.ReplayRecord;

/**
 * ПУБЛИЧНЫЙ ВИД СТОЛА ОТ ЛИЦА ОДНОГО МЕСТА — всё, что игрок на этом месте видит,
 * и ничего, чего он видеть не может.
 *
 * <p>ЗАЧЕМ ЭТО ЕСТЬ. Бот до сих пор видел стол как 33 числа ({@code StateFeatures}):
 * «сколько целей в досягаемости», «насколько я впереди», «сколько добытчиков
 * работает». Из 33 чисел ГЕОМЕТРИЮ ВОССТАНОВИТЬ НЕЛЬЗЯ: какой гекс с каким
 * граничит, стоит ли моя техника между его пехотой и моим ЦУ, перекрыта ли линия
 * выстрела стенкой. «Сколько» — это не «где». И нейросеть, которая в проекте уже
 * была, читала РОВНО ТЕ ЖЕ 33 числа: она меняла форму функции, но не то, что
 * видно. Поэтому и не могла играть лучше.
 *
 * <p>Здесь стол отдаётся ЦЕЛИКОМ: каждый гекс с владельцем каждого своего
 * сектора, каждый жетон со своей координатой, прочностью, уроном и энергией,
 * каждый планшет со всеми показателями, треками, модулями и их местами. Не набор
 * счётчиков, а вся расстановка — «где относительно чего что стоит».
 *
 * <p>ЧТО ЗАКРЫТО. Границу проводят правила, а не удобство:
 * <ul>
 *   <li>рука приказов (4 карты), рука заданий, рука арсенала — закрыты;</li>
 *   <li>слепой сброс приказа лежит рубашкой вверх: то, ЧТО отложено, видит
 *       только владелец, а сам факт — все;</li>
 *   <li>карты контейнеров лежат закрытыми: публично только их ЧИСЛО.</li>
 * </ul>
 * Открыто всё остальное, и это тоже решение правил, а не упущение: установленные
 * карты арсенала лежат на столе открыто («если у чьего-то ЦУ пять здоровья, рядом
 * лежит карта, которая это объясняет»), супер-задание открыто с подготовки, три
 * карты супер-арсенала на вершинах треков — единственная витрина в игре.
 *
 * <p>ЧИСЛА ЗАКРЫТОГО — ТОЖЕ ОТКРЫТАЯ ИНФОРМАЦИЯ. Сколько карт у соседа в руке,
 * видно за столом всем. Поэтому у каждого места счётчики заполнены ВСЕГДА, а
 * списки — только у своего.
 *
 * <p>СОСЕДИ ИДУТ В ПОРЯДКЕ ХОДА ОТ МЕНЯ, а не по номеру места: «следующий за
 * мной», «через одного». Иначе тот, кто это читает, начнёт учить номера мест
 * вместо расстановки — а место в игре ничего не значит, значение имеет только
 * очерёдность.
 *
 * <p>ОСНОВА — {@link ReplayRecord#snapshotOf}, тот же снимок, которым рисует
 * проигрыватель. Второй копии сборки состояния быть не должно: она разойдётся с
 * первой, и разойдётся молча. Отличие ровно одно — снимок проигрывателя это
 * взгляд ВСЕВИДЯЩЕГО зрителя (он несёт настоящие карты всех рук), а этот вид —
 * взгляд ИГРОКА.
 */
public final class PublicView {

    /** Чьими глазами смотрим. */
    public int seat;
    /** Сколько игроков за столом. */
    public int players;

    public int round;
    public int circle;
    /** Место с жетоном первого игрока. */
    public int firstPlayer;
    /** Чей сейчас ход ({@code null} — общая фаза раунда). */
    public Integer active;

    /** Идентификатор активной карты рынка ({@code null} — ещё не открыта). */
    public String market;
    /** Ячейки предложений: [сторона][ячейка] = место игрока или −1. */
    public int[][] marketCells = {{-1, -1}, {-1, -1}};
    /** Витрина треков: трек → карта супер-арсенала на его вершине. */
    public final Map<String, String> superArsenalOffer = new LinkedHashMap<>();
    /** Кто стоит на треках науки: трек → по шагам список мест в порядке прихода. */
    public final Map<String, List<List<Integer>>> techOccupancy = new LinkedHashMap<>();

    /** ПОЛЕ ЦЕЛИКОМ: гекс за гексом, с владельцем каждого сектора. */
    public final List<ReplayRecord.HexState> hexes = new ArrayList<>();
    /** ВСЕ ЖЕТОНЫ НА ПОЛЕ: координата, тип, уровень, урон, прочность, энергия. */
    public final List<ReplayRecord.Tok> tokens = new ArrayList<>();

    /** Мой планшет — целиком, вместе с моими руками карт. */
    public Seat me;
    /** Соседи В ПОРЯДКЕ ХОДА ОТ МЕНЯ; их руки — только числами. */
    public final List<Seat> others = new ArrayList<>();

    /**
     * ОДНО МЕСТО ЗА СТОЛОМ.
     *
     * <p>Списки карт ({@link #orderHand}, {@link #objectiveHand},
     * {@link #arsenalHand}, {@link #setAsideOrder}) заполнены ТОЛЬКО у своего
     * места. У соседей они {@code null}, а рядом стоят их числа — те публичны.
     */
    public static final class Seat {
        /** Номер места за столом (абсолютный). */
        public int seat;
        /** На каком месте по очереди ОТ МЕНЯ: 0 — я, 1 — следующий, и так далее. */
        public int order;

        // --- планшеты ---
        public String troopSide = "";
        public String storageSide = "";
        public String orderColor = "";

        // --- запасы и их потолки (кубики лежат на столе, всё открыто) ---
        public int coin;
        public int kelium;
        public int ammo;
        public int trophy;
        public int keliumCap;
        public int ammoCap;
        public int trophyCap;
        public int storeCap;
        /** Занятость ячеек склада: ячейка → что в ней лежит. */
        public final Map<String, String> storageCells = new LinkedHashMap<>();
        /** Жетоны модуля хранилища (какой стороной положены). */
        public final List<String> storageTokens = new ArrayList<>();

        // --- трофеи ---
        public int trophyTokens;
        public int trophyPoints;
        /** Трофейная карта: какие именно чужие жетоны на ней лежат. */
        public final List<Map<String, Object>> trophyCard = new ArrayList<>();

        // --- наука и модули ---
        /** Трек → достигнутый шаг. */
        public final Map<String, Integer> tech = new LinkedHashMap<>();
        public int redModules;
        public int blueModules;
        public int goldModules;
        /** Куда именно положены красные модули: род войск → жетон. */
        public final Map<String, Object> redPlaced = new LinkedHashMap<>();
        /** Куда именно положены синие модули: здание → жетон. */
        public final Map<String, Object> bluePlaced = new LinkedHashMap<>();

        // --- ЦУ и место старта ---
        public int cuTokens;
        public boolean ownCuToken = true;
        /** Гекс, с которого игрок начал партию — видно всем с подготовки. */
        public String startHex = "";

        /**
         * РАЗБИВКА ПОБЕДНЫХ ОЧКОВ, ключ {@code total} — итог.
         *
         * <p>Публично: очки в этой игре напечатаны на компонентах, лежащих на
         * столе, и любой игрок может их пересчитать. Для обучения это ещё и
         * готовый сигнал: разница {@code total} между началом и концом хода —
         * это и есть «стало ли лучше».
         */
        public final Map<String, Integer> vp = new LinkedHashMap<>();

        // --- супер-задание (открыто с подготовки) ---
        public String superObjective;
        public int superProgress;
        public boolean superComplete;
        /** Ячеек счётчика запуска ещё не погашено (−1 — вторая часть не начата). */
        public int superCells = -1;
        /** Символы на ячейках счётчика. */
        public final List<String> superCellSymbols = new ArrayList<>();
        /** Части первой половины: часть → сколько положено. */
        public final Map<String, Integer> superParts = new LinkedHashMap<>();
        /** Карты супер-арсенала, забранные с вершин треков. */
        public final List<String> superArsenal = new ArrayList<>();

        // --- ОТКРЫТЫЕ карты ---
        /** Установленные карты арсенала — лежат на столе открыто. */
        public final List<String> arsenalInstalled = new ArrayList<>();
        /** Разыгранные в этом раунде карты приказов — открыты. */
        public final List<String> orderPlayed = new ArrayList<>();

        // --- ЧИСЛА закрытого: публичны у ВСЕХ ---
        public int orderHandCount;
        public int objectiveHandCount;
        public int arsenalHandCount;
        /** Карт контейнеров на руке (лежат закрытыми, число публично). */
        public int containers;
        public int containerCap;
        /** Отложен ли слепым сбросом приказ (что именно — видит только владелец). */
        public boolean hasSetAsideOrder;

        // --- ЗАКРЫТОЕ: заполнено только у своего места, иначе null ---
        public List<String> orderHand;
        public List<String> objectiveHand;
        public List<String> arsenalHand;
        public String setAsideOrder;
    }

    /**
     * Снять вид от лица места {@code seat}.
     *
     * <p>Порядок соседей — по кругу от меня, поэтому {@code others.get(0)} это
     * всегда «следующий за мной по очереди».
     */
    public static PublicView of(GameState s, int seat) {
        return from(ReplayRecord.snapshotOf(s, null), seat, s.numPlayers());
    }

    /**
     * То же из готового снимка — например из записи партии.
     *
     * <p>Отдельный вход нужен, чтобы обучать и на записанных партиях, не заводя
     * движок: снимок из записи и снимок с живого состояния — одно и то же.
     */
    public static PublicView from(ReplayRecord.Snapshot snap, int seat, int players) {
        PublicView v = new PublicView();
        v.seat = seat;
        v.players = players;
        v.round = snap.round;
        v.circle = snap.circle;
        v.firstPlayer = snap.firstPlayer;
        v.active = snap.active;
        v.market = snap.market;
        for (int i = 0; i < snap.marketCells.length && i < v.marketCells.length; i++) {
            v.marketCells[i] = snap.marketCells[i].clone();
        }
        v.superArsenalOffer.putAll(snap.superArsenalOffer);
        v.techOccupancy.putAll(snap.techOccupancy);
        v.hexes.addAll(snap.hexes);
        v.tokens.addAll(snap.tokens);

        // ПОРЯДОК ХОДА ОТ МЕНЯ, а не по номеру места.
        for (int step = 0; step < players; step++) {
            int other = (seat + step) % players;
            ReplayRecord.Player src = playerOf(snap, other);
            if (src == null) {
                continue;
            }
            Seat st = seatOf(src, step, other == seat);
            if (step == 0) {
                v.me = st;
            } else {
                v.others.add(st);
            }
        }
        return v;
    }

    private static ReplayRecord.Player playerOf(ReplayRecord.Snapshot snap, int seat) {
        for (ReplayRecord.Player p : snap.players) {
            if (p.seat == seat) {
                return p;
            }
        }
        return null;
    }

    private static Seat seatOf(ReplayRecord.Player p, int order, boolean mine) {
        Seat st = new Seat();
        st.seat = p.seat;
        st.order = order;
        st.troopSide = p.side;
        st.storageSide = p.storageSide;
        st.orderColor = p.orderColor;

        st.coin = p.coin;
        st.kelium = p.kelium;
        st.ammo = p.ammo;
        st.trophy = p.trophy;
        st.keliumCap = p.keliumCap;
        st.ammoCap = p.ammoCap;
        st.trophyCap = p.trophyCap;
        st.storeCap = p.storeCap;
        st.storageCells.putAll(p.storageCells);
        st.storageTokens.addAll(p.storageTokens);

        st.trophyTokens = p.trophyTokens;
        st.trophyPoints = p.trophyPoints;
        for (ReplayRecord.TrophyToken t : p.trophyCard) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", t.uid);
            m.put("owner", t.owner);
            m.put("type", t.type);
            m.put("building", t.building);
            m.put("value", t.value);
            st.trophyCard.add(m);
        }

        st.tech.putAll(p.tech);
        st.redModules = p.redModules;
        st.blueModules = p.blueModules;
        st.goldModules = p.goldModules;
        for (var e : p.redPlaced.entrySet()) {
            st.redPlaced.put(e.getKey(), moduleMap(e.getValue()));
        }
        for (var e : p.bluePlaced.entrySet()) {
            st.bluePlaced.put(e.getKey(), moduleMap(e.getValue()));
        }

        st.cuTokens = p.cuTokens;
        st.ownCuToken = p.ownCuToken;
        st.startHex = p.startHex;
        st.vp.putAll(p.vp);

        st.superObjective = p.superObjective;
        st.superProgress = p.superProgress;
        st.superComplete = p.superComplete;
        st.superCells = p.superCells;
        st.superCellSymbols.addAll(p.superCellSymbols);
        st.superParts.putAll(p.superParts);
        st.superArsenal.addAll(p.superArsenal);

        st.arsenalInstalled.addAll(p.arsenalInstalled);
        st.orderPlayed.addAll(p.orderPlayed);

        // ЧИСЛА — всегда, СПИСКИ — только своему.
        st.orderHandCount = p.orderHand.size();
        st.objectiveHandCount = p.objectiveHand.size();
        st.arsenalHandCount = p.arsenalHand.size();
        st.containers = p.containers;
        st.containerCap = p.containerCap;
        st.hasSetAsideOrder = p.orderSetAside != null;
        if (mine) {
            st.orderHand = List.copyOf(p.orderHand);
            st.objectiveHand = List.copyOf(p.objectiveHand);
            st.arsenalHand = List.copyOf(p.arsenalHand);
            st.setAsideOrder = p.orderSetAside;
        }
        return st;
    }

    private static Map<String, Object> moduleMap(ReplayRecord.Module m) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (m == null) {
            return out;
        }
        out.put("id", m.id);
        out.put("gold", m.gold);
        return out;
    }

    // ==================================================================
    //  ВЫГРУЗКА
    // ==================================================================

    /** Вид как дерево карт и списков — годится для JSON и для питона. */
    public Map<String, Object> toMap() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("seat", seat);
        o.put("players", players);
        o.put("round", round);
        o.put("circle", circle);
        o.put("firstPlayer", firstPlayer);
        o.put("active", active);
        o.put("market", market);
        List<Object> mc = new ArrayList<>();
        for (int[] side : marketCells) {
            List<Object> row = new ArrayList<>();
            for (int c : side) {
                row.add(c);
            }
            mc.add(row);
        }
        o.put("marketCells", mc);
        o.put("superArsenalOffer", superArsenalOffer);
        o.put("techOccupancy", techOccupancy);
        List<Object> hx = new ArrayList<>();
        for (ReplayRecord.HexState h : hexes) {
            hx.add(hexMap(h));
        }
        o.put("hexes", hx);
        List<Object> tk = new ArrayList<>();
        for (ReplayRecord.Tok t : tokens) {
            tk.add(tokMap(t));
        }
        o.put("tokens", tk);
        o.put("me", seatMap(me));
        List<Object> os = new ArrayList<>();
        for (Seat st : others) {
            os.add(seatMap(st));
        }
        o.put("others", os);
        return o;
    }

    /** Записать вид в файл JSON. */
    public void save(Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, Json.write(toMap()), StandardCharsets.UTF_8);
    }

    /** Вид одной строкой JSON — для потока обучающих примеров. */
    public String toJson() {
        return Json.write(toMap());
    }

    private static Map<String, Object> hexMap(ReplayRecord.HexState h) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", h.id);
        o.put("containerCell", h.containerCell);
        o.put("energyCell", h.energyCell);
        o.put("ownerTint", h.ownerTint);
        o.put("ownerBuilt", h.ownerBuilt);
        List<Object> so = new ArrayList<>();
        for (int s : h.sideOwner) {
            so.add(s);
        }
        o.put("sideOwner", so);
        if (h.spawn != null) {
            Map<String, Object> sp = new LinkedHashMap<>();
            sp.put("start", h.spawn.start);
            sp.put("kelium", h.spawn.kelium);
            sp.put("stack", h.spawn.stack);
            sp.put("flipped", h.spawn.flipped);
            o.put("spawn", sp);
        }
        List<Object> ns = new ArrayList<>();
        for (ReplayRecord.Neutral n : h.neutrals) {
            Map<String, Object> nm = new LinkedHashMap<>();
            nm.put("big", n.big);
            nm.put("hp", n.hp);
            nm.put("hpMax", n.hpMax);
            nm.put("corners", n.corners);
            ns.add(nm);
        }
        o.put("neutrals", ns);
        return o;
    }

    private static Map<String, Object> tokMap(ReplayRecord.Tok t) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("uid", t.uid);
        o.put("owner", t.owner);
        o.put("building", t.building);
        o.put("type", t.type);
        o.put("hexId", t.hexId);
        o.put("damage", t.damage);
        o.put("hp", t.hp);
        o.put("energySlots", t.energySlots);
        o.put("energyPlaced", t.energyPlaced);
        o.put("energyIdle", t.energyIdle);
        o.put("level", t.level);
        o.put("insideBuildingUid", t.insideBuildingUid);
        o.put("capturedBy", t.capturedBy);
        return o;
    }

    private static Map<String, Object> seatMap(Seat st) {
        Map<String, Object> o = new LinkedHashMap<>();
        if (st == null) {
            return o;
        }
        o.put("seat", st.seat);
        o.put("order", st.order);
        o.put("troopSide", st.troopSide);
        o.put("storageSide", st.storageSide);
        o.put("orderColor", st.orderColor);
        o.put("coin", st.coin);
        o.put("kelium", st.kelium);
        o.put("ammo", st.ammo);
        o.put("debris", st.trophy);
        o.put("keliumCap", st.keliumCap);
        o.put("ammoCap", st.ammoCap);
        o.put("debrisCap", st.trophyCap);
        o.put("storeCap", st.storeCap);
        o.put("storageCells", st.storageCells);
        o.put("storageTokens", st.storageTokens);
        o.put("trophyTokens", st.trophyTokens);
        o.put("trophyPoints", st.trophyPoints);
        o.put("trophyCard", st.trophyCard);
        o.put("tech", st.tech);
        o.put("redModules", st.redModules);
        o.put("blueModules", st.blueModules);
        o.put("goldModules", st.goldModules);
        o.put("redPlaced", st.redPlaced);
        o.put("bluePlaced", st.bluePlaced);
        o.put("cuTokens", st.cuTokens);
        o.put("ownCuToken", st.ownCuToken);
        o.put("startHex", st.startHex);
        o.put("vp", st.vp);
        o.put("superObjective", st.superObjective);
        o.put("superProgress", st.superProgress);
        o.put("superComplete", st.superComplete);
        o.put("superCells", st.superCells);
        o.put("superCellSymbols", st.superCellSymbols);
        o.put("superParts", st.superParts);
        o.put("superArsenal", st.superArsenal);
        o.put("arsenalInstalled", st.arsenalInstalled);
        o.put("orderPlayed", st.orderPlayed);
        o.put("orderHandCount", st.orderHandCount);
        o.put("objectiveHandCount", st.objectiveHandCount);
        o.put("arsenalHandCount", st.arsenalHandCount);
        o.put("containers", st.containers);
        o.put("containerCap", st.containerCap);
        o.put("hasSetAsideOrder", st.hasSetAsideOrder);
        o.put("orderHand", st.orderHand);
        o.put("objectiveHand", st.objectiveHand);
        o.put("arsenalHand", st.arsenalHand);
        o.put("setAsideOrder", st.setAsideOrder);
        return o;
    }
}
