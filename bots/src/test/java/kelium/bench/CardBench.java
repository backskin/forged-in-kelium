package kelium.bench;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * СТЕНД ДЛЯ ПРОВЕРКИ ОДНОЙ КАРТЫ В НАСТОЯЩЕЙ ПАРТИИ.
 *
 * <p>ЗАЧЕМ ОН НУЖЕН. Карты проверялись «по данным»: есть ли запись в каталоге,
 * знает ли движок такой эффект. Этого мало, и цена ошибки уже известна: три карты
 * заданий лежали в колоде, ссылались на предикат, которого в движке нет, и не
 * могли быть выполнены НИКОГДА. Ни один прежний тест этого не видел, потому что ни
 * один не пытался эту карту РАЗЫГРАТЬ.
 *
 * <p>Здесь всё наоборот: поднимается настоящая партия с настоящими правилами,
 * карта кладётся игроку в руку, игрок принуждается её разыграть, и проверяется,
 * что именно произошло на столе. Плюс — и это половина смысла — проверяется, что
 * карта НЕ предлагается там, где сработать не может.
 *
 * <p>Как пользоваться:
 * <pre>
 * var b = CardBench.партия(4)
 *     .арсеналВРуку(0, "b04")
 *     .монеты(0, 5)
 *     .здание(0, BuildingType.BARRACKS, 1)
 *     .играть(0, спец -> спец.установить("b04"));
 * b.проверитьЧтоУстановлена(0, "b04");
 * </pre>
 *
 * <p>ВСЕ ПРЕДЛОЖЕННЫЕ ВАРИАНТЫ ЗАПОМИНАЮТСЯ. Без этого нельзя проверить
 * отрицательный случай: «на пустом складе карта не должна предлагаться» проверяется
 * только тем, что в списке вариантов её не было.
 */
public final class CardBench {

    private final GameConfig cfg;
    private final GameState s;
    private GameEngine движок;

    /** Что было предложено на каждой точке решения: вид → метки вариантов. */
    private final Map<String, List<String>> предложено = new LinkedHashMap<>();
    /** Все вида решений в порядке появления — для разбора неудачных тестов. */
    private final List<String> точки = new ArrayList<>();
    /** События движка за прогон. */
    private final List<Map<String, Object>> события = new ArrayList<>();

    private CardBench(GameConfig cfg, GameState s) {
        this.cfg = cfg;
        this.s = s;
    }

    // ==================================================================
    //  ПОДГОТОВКА СТОЛА
    // ==================================================================

    /** Настоящая партия на {@code players} игроков, поле собрано правилами. */
    public static CardBench партия(int players) {
        return партия(players, 424242L);
    }

    public static CardBench партия(int players, long seed) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        CardBench b = new CardBench(cfg, s);
        b.очиститьПоле();
        b.очиститьРуки();
        return b;
    }

    /**
     * Убрать с поля ВСЕ жетоны, кроме ЦУ.
     *
     * <p>Стол должен быть предсказуемым: карта проверяется на той обстановке,
     * которую поставил тест, а не на случайной раскладке подготовки. ЦУ остаётся —
     * без него у игрока нет зоны стройки и половина карт бессмысленна.
     */
    public CardBench очиститьПоле() {
        for (PlayerState p : s.players) {
            for (BuildingToken b : new ArrayList<>(p.buildingsOnField())) {
                if (b.type == BuildingType.COMMAND_CENTER) {
                    continue;
                }
                s.field.get(b.hexId).freeSidesByToken(b.uid);
                b.hexId = null;
            }
            for (UnitToken u : new ArrayList<>(p.unitsOnField())) {
                u.setHexId(null);
            }
        }
        return this;
    }

    /** Опустошить руки всех игроков: тест сам кладёт то, что проверяет. */
    public CardBench очиститьРуки() {
        for (PlayerState p : s.players) {
            p.arsenalHand.clear();
            p.arsenalInstalled.clear();
            p.objectiveHand.clear();
            p.containers = 0;
        }
        return this;
    }

    /** Положить карту арсенала в руку игрока. */
    public CardBench арсеналВРуку(int seat, String id) {
        s.player(seat).arsenalHand.add(id);
        return this;
    }

    /** Считать карту арсенала уже установленной (для проверки её низа). */
    public CardBench арсеналУстановлен(int seat, String id) {
        s.player(seat).arsenalInstalled.add(id);
        return this;
    }

    /** Положить карту задания в руку игрока. */
    public CardBench заданиеВРуку(int seat, String id) {
        s.player(seat).objectiveHand.add(id);
        return this;
    }

    public CardBench монеты(int seat, int n) {
        return ресурс(seat, Resource.COIN, n);
    }

    public CardBench боеприпасы(int seat, int n) {
        return ресурс(seat, Resource.AMMO, n);
    }

    public CardBench келемий(int seat, int n) {
        return ресурс(seat, Resource.KELIUM, n);
    }

    public CardBench обломки(int seat, int n) {
        return ресурс(seat, Resource.DEBRIS, n);
    }

    /** Ровно {@code n} этого ресурса в хранилище, не «плюс n». */
    public CardBench ресурс(int seat, Resource r, int n) {
        PlayerState p = s.player(seat);
        p.resources.add(r, n - p.resources.get(r));
        return this;
    }

    /**
     * СНЯТЬ ВСЮ ЭНЕРГИЮ у игрока — со зданий и с источников.
     *
     * <p>Нужно почти каждому отрицательному случаю. ЦУ на подготовке запитано, а
     * для многих карт ЦУ — такое же военное здание, как казарма: без этой ручки
     * проверка «карта не работает без запитанного военного здания» падает на
     * исправной карте. Я на это наступил на первой же карте.
     */
    public CardBench безЭнергии(int seat) {
        PlayerState p = s.player(seat);
        for (BuildingToken b : p.buildings) {
            for (int src : new ArrayList<>(b.energyBySource.keySet())) {
                b.stripEnergyOf(src);
            }
            b.energyIdle = 0;
        }
        return this;
    }

    /** Гекс ЦУ игрока — от него удобно отсчитывать обстановку. */
    public String гексЦУ(int seat) {
        for (BuildingToken b : s.player(seat).buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                return b.hexId;
            }
        }
        return s.field.hexes.keySet().iterator().next();
    }

    /** Соседний гекс к названному (первый по порядку поля). */
    public String сосед(String hexId) {
        return s.field.neighbors(hexId).iterator().next();
    }

    /**
     * Поставить здание игрока на гекс. {@code null} для hexId — на гекс своего ЦУ.
     *
     * @param energy сколько кубиков энергии положить (запитанность)
     */
    public CardBench здание(int seat, String hexId, BuildingType type, Integer level,
                           int energy) {
        PlayerState p = s.player(seat);
        String где = hexId == null ? гексЦУ(seat) : hexId;
        BuildingToken b = s.tokenStats.makeBuilding(type, seat, ++uid, level);
        p.buildings.add(b);
        b.hexId = где;
        var h = s.field.get(где);
        int fp = kelium.engine.Actions.buildingFootprint(type);
        List<Integer> свободные = h.freeSideIndices();
        List<Integer> занять = new ArrayList<>();
        for (int i = 0; i < 6 && занять.isEmpty(); i++) {
            boolean влезает = true;
            for (int k = 0; k < fp; k++) {
                влезает &= свободные.contains((i + k) % 6);
            }
            if (влезает) {
                for (int k = 0; k < fp; k++) {
                    занять.add((i + k) % 6);
                }
            }
        }
        for (Integer i : занять) {
            h.sideOwner[i] = b.uid;
        }
        if (energy > 0) {
            b.addEnergyFrom(-1, energy);
        }
        последнееЗдание = b;
        return this;
    }

    /** То же на гекс своего ЦУ. */
    public CardBench здание(int seat, BuildingType type, int energy) {
        return здание(seat, null, type, type == BuildingType.MINER
            || type == BuildingType.POWER_PLANT ? 1 : null, energy);
    }

    /** Поставить войско игрока на гекс. */
    public CardBench войско(int seat, String hexId, UnitType type) {
        PlayerState p = s.player(seat);
        UnitToken u = s.tokenStats.makeUnit(type, seat, ++uid, p.unitsOfKind(type));
        p.units.add(u);
        u.setHexId(hexId == null ? гексЦУ(seat) : hexId);
        последнееВойско = u;
        return this;
    }

    /** Урон на последнем поставленном жетоне. */
    public CardBench урон(int n) {
        if (последнееВойско != null) {
            последнееВойско.damage = n;
        }
        if (последнееЗдание != null) {
            последнееЗдание.damage = n;
        }
        return this;
    }

    private int uid = 50_000;
    private BuildingToken последнееЗдание;
    private UnitToken последнееВойско;

    /** Положить в трофейное место игрока жетон войска противника. */
    public CardBench трофейВойско(int seat, int чей, UnitType type) {
        PlayerState жертва = s.player(чей);
        UnitToken u = s.tokenStats.makeUnit(type, чей, ++uid, жертва.unitsOfKind(type));
        s.player(seat).trophySpace.add(u);
        return this;
    }

    /** Положить в трофейное место игрока здание противника. */
    public CardBench трофейЗдание(int seat, int чей, BuildingType type) {
        BuildingToken b = s.tokenStats.makeBuilding(type, чей, ++uid,
            type == BuildingType.MINER || type == BuildingType.POWER_PLANT ? 1 : null);
        s.player(seat).trophySpace.add(b);
        return this;
    }

    /** Контейнеры на руках у игрока — ровно {@code n}. */
    public CardBench контейнеры(int seat, int n) {
        s.player(seat).containers = n;
        return this;
    }

    /** Поставить игрока на шаг {@code step} названного трека технологий. */
    public CardBench техШаг(int seat, String track, int step) {
        s.player(seat).techSteps.put(track, step);
        return this;
    }


    // ==================================================================
    //  ЗАДАНИЯ: СПРОС УСЛОВИЯ НА ЗАМОРОЖЕННОМ СОСТОЯНИИ
    // ==================================================================

    /** Правки журнала, которые надо внести уже ВНУТРИ хода игрока. */
    private final List<java.util.function.Consumer<kelium.core.TurnJournal.TurnFacts>> правкиХода
        = new ArrayList<>();

    /**
     * ЗАПИСАТЬ ФАКТ ХОДА УЖЕ ВНУТРИ ХОДА, а не до партии.
     *
     * <p>ЗАЧЕМ ЭТО ОТДЕЛЬНАЯ РУЧКА. Движок при запуске ЗАВОДИТ НОВЫЙ ЖУРНАЛ, и
     * всё, что тест положил в журнал заранее, пропадает. Я на это наступил на
     * карте «Ответный удар»: факт ставился до партии, движок его стирал, задание
     * оказывалось невыполнимым, бот его жёг — и тест падал на исправной карте,
     * причём выглядело это как ошибка карты.
     *
     * <p>Правка вносится на первой точке решения игрока, то есть уже после начала
     * его хода, — и поэтому доживает до розыгрыша задания.
     */
    public CardBench фактХода(
            java.util.function.Consumer<kelium.core.TurnJournal.TurnFacts> правка) {
        правкиХода.add(правка);
        return this;
    }

    /**
     * Журнал хода. Нужен спросам условия: требования-происшествия читают его, и
     * без журнала спрос упал бы на пустой ссылке.
     */
    public kelium.core.TurnJournal журнал() {
        if (s.journal == null) {
            s.journal = new kelium.core.TurnJournal(s.numPlayers());
        }
        return s.journal;
    }

    /**
     * ВЫПОЛНЕНО ЛИ БАЗОВОЕ ТРЕБОВАНИЕ ЗАДАНИЯ ПРЯМО СЕЙЧАС.
     *
     * <p>Главный спрос для карт заданий, и по той же причине, по которой у
     * арсенала им стал {@code доступноПрименение}: состояние заморожено. «За
     * партию задание разу не выполнилось» ничего не доказывает — бот мог просто
     * не дойти до нужной расстановки. Здесь тест ставит расстановку сам и
     * спрашивает движок, видит ли он условие выполненным.
     *
     * <p>Спрос идёт через {@link kelium.engine.Objectives#playableObjectives},
     * то есть тем же путём, которым задание играет живой игрок: сюда входит и
     * проверка оплаты жертвы, и карты, чьё условие живёт в коде
     * ({@code checked_by: card}).
     */
    public boolean готово(int seat, String id) {
        return kelium.engine.Objectives.playableObjectives(s, seat, журнал()).contains(id);
    }

    /**
     * Выполнено ли ещё и УСИЛЕННОЕ требование — на том же замороженном столе.
     *
     * <p>ОТДЕЛЬНО ПРОВЕРЯЕТСЯ, ЧТО УСИЛЕНИЕ У КАРТЫ ВООБЩЕ ЕСТЬ. Индикатор движка
     * намеренно отвечает «усиление достигнуто» карте без усиления: боту он этим
     * говорит «ждать нечего, потолок уже взят», и для решения бота это верно.
     * Но для проверки карты такой ответ означал бы, что у начального задания есть
     * усиление, — а его нет ни у одного. Различие держится здесь, чтобы не
     * ломать смысл индикатора.
     */
    public boolean усилено(int seat, String id) {
        var набор = cfg.content.get("objectives");
        if (набор != null) {
            try {
                if (набор.byId(id).get("enhanced") == null) {
                    return false;
                }
            } catch (RuntimeException e) {
                return false;
            }
        }
        var h = подсказка(seat, id);
        return h != null && h.enhancedReady();
    }

    /** Индикаторы карты: ГОТОВО, УСИЛЕНО, ДОСТИЖИМО и планы. */
    public kelium.engine.ObjectiveHints.Hint подсказка(int seat, String id) {
        return kelium.engine.ObjectiveHints.forCard(s, seat, журнал(), id,
            List.of("build", "assembly", "combat", "movement", "mining", "science", "market"),
            2);
    }

    /** Инструкции, которыми движок предлагает закрыть карту в этот ход. */
    public List<String> планы(int seat, String id) {
        var h = подсказка(seat, id);
        List<String> out = new ArrayList<>();
        if (h != null) {
            for (var pl : h.plans()) {
                out.add(pl.summary());
            }
        }
        return out;
    }

    /** Само состояние — когда стенду не хватает готовой ручки. */
    public GameState состояние() {
        return s;
    }

    public GameConfig настройка() {
        return cfg;
    }

    // ==================================================================
    //  СЦЕНАРИЙ ХОДА
    // ==================================================================

    /** Что тест хочет навязать на точке решения. */
    public static final class Шаг {
        final String вид;
        final Predicate<Choice> подходит;
        final String описание;

        Шаг(String вид, Predicate<Choice> подходит, String описание) {
            this.вид = вид;
            this.подходит = подходит;
            this.описание = описание;
        }
    }

    /** Установить карту арсенала СПЕЦ-действием. */
    public static Шаг установить(String id) {
        return new Шаг("spec", c -> "spec_arsenal_install".equals(c.kind())
            && id.equals(c.payload()), "установить арсенал " + id);
    }

    /** Сжечь карту арсенала ради верха. */
    public static Шаг сжечьАрсенал(String id) {
        return new Шаг("spec", c -> "spec_arsenal_burn".equals(c.kind())
            && id.equals(c.payload()), "сжечь арсенал " + id);
    }

    /** Выполнить задание СПЕЦ-действием. */
    public static Шаг выполнитьЗадание(String id) {
        return new Шаг("spec", c -> "spec_objective".equals(c.kind())
            && id.equals(c.payload()), "выполнить задание " + id);
    }

    /** Сжечь задание ради верха. */
    public static Шаг сжечьЗадание(String id) {
        return new Шаг("spec", c -> "spec_objective_burn".equals(c.kind())
            && id.equals(c.payload()), "сжечь задание " + id);
    }

    /**
     * Применить СПЕЦ установленной карты арсенала.
     *
     * <p>ВНИМАНИЕ НА КЛЮЧ. Движок предлагает такой СПЕЦ ДВУМЯ разными способами:
     * часть карт идёт как {@code spec_arsenal_use|<id карты>}, а те, чей низ
     * реализован способностью из реестра, — как {@code ability:<id пассивки>}.
     * Первая версия теста искала только первый вид, не находила его никогда и
     * поэтому ОТРИЦАТЕЛЬНЫЕ проверки проходили сами собой, ничего не проверяя.
     * Ложно-зелёный тест хуже отсутствующего.
     *
     * <p>Поэтому шаг ищет оба вида, а id пассивки берётся из каталога — см.
     * {@link #ключПрименения}.
     */
    public Шаг применить(String id) {
        String пассивка = ключПрименения(id);
        return new Шаг("spec", c -> ("spec_arsenal_use".equals(c.kind())
                && id.equals(c.payload()))
                || (пассивка != null && String.valueOf(c.payload()).equals(пассивка)),
            "применить арсенал " + id);
    }

    /**
     * ID пассивки низа карты арсенала — им движок называет вариант в реестре
     * способностей; {@code null}, если у карты нет низа или он не SPEC.
     */
    @SuppressWarnings("unchecked")
    public String ключПрименения(String id) {
        var набор = cfg.content.get("arsenal");
        if (набор == null) {
            return null;
        }
        Map<String, Object> c;
        try {
            c = набор.byId(id);
        } catch (RuntimeException e) {
            return null;
        }
        if (c == null || !(c.get("bottom") instanceof Map<?, ?> bm)) {
            return null;
        }
        Map<String, Object> низ = (Map<String, Object>) bm;
        Object п = низ.get("passive");
        return п == null ? null : String.valueOf(п);
    }

    /** Предлагался ли СПЕЦ этой карты за прогон — оба вида ключа. */
    public boolean предлагалосьПрименение(String id) {
        String пассивка = ключПрименения(id);
        return предлагалось("spec", "spec_arsenal_use|" + id)
            || (пассивка != null && предлагалось("spec", пассивка));
    }

    /**
     * ДОСТУПЕН ЛИ СПЕЦ КАРТЫ ПРЯМО СЕЙЧАС, на этом состоянии, БЕЗ игры.
     *
     * <p>ЭТИМ И ТОЛЬКО ЭТИМ проверяются отрицательные случаи. Проверка «за партию
     * вариант ни разу не предлагался» негодна: за два раунда бот сам построит
     * казарму и заработает монету, условие станет верным по-настоящему, и тест
     * упадёт на исправной карте. Я на это уже наступил — b04 «не работает без
     * запитанного военного здания» падал именно так, хотя карта была права.
     *
     * <p>Здесь состояние заморожено: расставили обстановку — спросили доступность.
     */
    public boolean доступноПрименение(int seat, String id) {
        String пассивка = ключПрименения(id);
        for (Choice c : kelium.engine.ability.Abilities.options(s, seat,
                kelium.engine.ability.OptionSource.Slot.SPEC)) {
            if (пассивка != null && пассивка.equals(String.valueOf(c.payload()))) {
                return true;
            }
            if (id.equals(String.valueOf(c.payload()))) {
                return true;
            }
        }
        return false;
    }

    /** Все СПЕЦ-варианты от способностей на текущем состоянии — для разбора. */
    public List<String> доступныеСпец(int seat) {
        List<String> out = new ArrayList<>();
        for (Choice c : kelium.engine.ability.Abilities.options(s, seat,
                kelium.engine.ability.OptionSource.Slot.SPEC)) {
            out.add(c.kind() + "|" + c.payload() + "|" + c.label());
        }
        return out;
    }

    /** Любой вариант, чья метка содержит подстроку — на любой точке решения. */
    public static Шаг вариант(String вид, String подстрокаМетки) {
        return new Шаг(вид, c -> c.label() != null && c.label().contains(подстрокаМетки),
            вид + " ~ " + подстрокаМетки);
    }

    /**
     * ПРОИГРАТЬ ПАРТИЮ до конца, навязывая шаги игроку {@code seat}.
     *
     * <p>Партия играется целиком: карта должна работать в живой игре, а не в
     * вырезанном куске. Остальные игроки играют обычными ботами, сам игрок —
     * обычным ботом, которому навязаны только названные шаги.
     */
    public CardBench играть(int seat, Шаг... шаги) {
        List<Agent> агенты = new ArrayList<>();
        for (int i = 0; i < s.numPlayers(); i++) {
            Agent обычный = kelium.agents.Bots.create("balanced", i, new Random(1000L + i),
                s.numPlayers());
            агенты.add(i == seat ? new Сценарист(i, обычный, шаги) : обычный);
        }
        движок = new GameEngine(s, агенты, события::add);
        движок.run();
        return this;
    }

    /** Проиграть только до конца раунда {@code round}. */
    public CardBench игратьДо(int round, int seat, Шаг... шаги) {
        List<Agent> агенты = new ArrayList<>();
        for (int i = 0; i < s.numPlayers(); i++) {
            Agent обычный = kelium.agents.Bots.create("balanced", i, new Random(1000L + i),
                s.numPlayers());
            агенты.add(i == seat ? new Сценарист(i, обычный, шаги) : обычный);
        }
        движок = new GameEngine(s, агенты, события::add);
        движок.runToRound(round);
        return this;
    }

    /**
     * Агент, навязывающий заданные шаги и запоминающий ВСЕ предложенные варианты.
     *
     * <p>Шаг срабатывает один раз и в любом порядке: тест говорит «надо установить
     * b04», а на какой точно точке решения это станет возможно, знает движок.
     */
    private final class Сценарист extends Agent {
        private final Agent обычный;
        private final List<Шаг> осталось;

        Сценарист(int seat, Agent обычный, Шаг[] шаги) {
            super(seat, "сценарист");
            this.обычный = обычный;
            this.осталось = new ArrayList<>(List.of(шаги));
        }

        @Override
        public Choice choose(GameState state, List<Choice> варианты,
                             Map<String, Object> контекст) {
            String вид = контекст == null ? ""
                : String.valueOf(контекст.getOrDefault("kind", ""));
            // ПРАВКИ ЖУРНАЛА ВНОСЯТСЯ НА ВЫБОРЕ ДЕЙСТВИЯ, а не на первой точке
            // вообще. Раздача приказов и слепой сброс идут ДО начала хода, а
            // начало хода обнуляет запись места — правка, внесённая раньше,
            // стирается. Выбор действия — первая точка уже внутри хода.
            if ("action".equals(вид) && !правкиХода.isEmpty() && state.journal != null) {
                for (var правка : правкиХода) {
                    правка.accept(state.journal.of(seat));
                }
                правкиХода.clear();
            }
            точки.add(вид);
            List<String> метки = предложено.computeIfAbsent(вид, k -> new ArrayList<>());
            for (Choice c : варианты) {
                метки.add(c.kind() + "|" + c.payload() + "|" + c.label());
            }
            for (int i = 0; i < осталось.size(); i++) {
                Шаг ш = осталось.get(i);
                if (!ш.вид.equals(вид)) {
                    continue;
                }
                for (Choice c : варианты) {
                    if (ш.подходит.test(c)) {
                        осталось.remove(i);
                        обычный.choose(state, List.of(c), контекст);
                        return c;
                    }
                }
            }
            return обычный.choose(state, варианты, контекст);
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            обычный.observeEvent(event);
        }
    }

    // ==================================================================
    //  ЧТО ПОЛУЧИЛОСЬ
    // ==================================================================

    /** Предлагался ли когда-нибудь вариант такого вида с таким признаком. */
    public boolean предлагалось(String вид, String признак) {
        List<String> метки = предложено.get(вид);
        if (метки == null) {
            return false;
        }
        for (String m : метки) {
            if (m.contains(признак)) {
                return true;
            }
        }
        return false;
    }

    /** Все предложенные варианты этого вида — для разбора упавшего теста. */
    public List<String> вариантыВида(String вид) {
        return предложено.getOrDefault(вид, List.of());
    }

    /** Виды точек решения в порядке появления. */
    public List<String> точкиРешения() {
        return List.copyOf(new LinkedHashSet<>(точки));
    }

    /** Сколько событий такого типа случилось за партию. */
    public int событий(String тип) {
        int n = 0;
        for (Map<String, Object> e : события) {
            if (тип.equals(String.valueOf(e.get("type")))) {
                n++;
            }
        }
        return n;
    }

    /** Случилось ли событие такого типа с такими полями. */
    public boolean было(String тип, Object... полеЗначение) {
        for (Map<String, Object> e : события) {
            if (!тип.equals(String.valueOf(e.get("type")))) {
                continue;
            }
            boolean всё = true;
            for (int i = 0; i + 1 < полеЗначение.length; i += 2) {
                Object было = e.get(String.valueOf(полеЗначение[i]));
                всё &= String.valueOf(было).equals(String.valueOf(полеЗначение[i + 1]));
            }
            if (всё) {
                return true;
            }
        }
        return false;
    }

    /**
     * ЧТО ДВИЖОК ВЫДАЛ ЗА ВЫПОЛНЕННОЕ ЗАДАНИЕ — базовая и усиленная часть.
     *
     * <p>ПОЧЕМУ НЕ ПО КОШЕЛЬКУ. Проверять награду по остатку ресурсов нельзя: до
     * конца раунда игрок успевает её потратить, и тест падает на исправной карте.
     * Это та же ловушка, на которой у меня уже упал арсенал, только с другого
     * конца. Награда проверяется по тому, что движок объявил в момент выдачи.
     *
     * @return {@code {base: {...}, special: {...}}} или пустая карта, если задание
     *         за прогон не выполнялось
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> награда(String id) {
        for (Map<String, Object> e : события) {
            if ("objective".equals(String.valueOf(e.get("type")))
                    && id.equals(String.valueOf(e.get("card")))
                    && e.get("granted") instanceof Map<?, ?> g) {
                return (Map<String, Object>) g;
            }
        }
        return Map.of();
    }

    /** Сколько единиц названного добра выдала базовая часть награды. */
    @SuppressWarnings("unchecked")
    public int наградаБазовая(String id, String что) {
        Object base = награда(id).get("base");
        if (base instanceof Map<?, ?> m && ((Map<String, Object>) m).get(что) instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    /** Сколько единиц названного добра выдала усиленная часть награды. */
    @SuppressWarnings("unchecked")
    public int наградаУсиленная(String id, String что) {
        Object sp = награда(id).get("special");
        if (sp instanceof Map<?, ?> m && ((Map<String, Object>) m).get(что) instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    /** Все события — когда нужно посмотреть глазами, что происходило. */
    public List<Map<String, Object>> всеСобытия() {
        return List.copyOf(события);
    }

    // ==================================================================
    //  ВСЕ СПОСОБЫ РАЗЫГРАТЬ КАРТУ
    // ==================================================================

    /**
     * ЧЕМ КАРТА МОЖЕТ БЫТЬ РАЗЫГРАНА — выводится ИЗ ДАННЫХ карты, а не из головы.
     *
     * <p>Заказ дизайнера: каждую карту проверять всеми способами, какими её можно
     * разыграть. Список способов нельзя держать в тесте руками — он разойдётся с
     * каталогом на первой же правке. Поэтому способы считаются по записи карты:
     *
     * <ul>
     *   <li>ЗАДАНИЕ: {@code ВЫПОЛНИТЬ} (есть requirement), {@code УСИЛИТЬ}
     *       (есть enhanced), {@code СЖЕЧЬ} (есть top);</li>
     *   <li>АРСЕНАЛ: {@code СЖЕЧЬ} (есть top), {@code УСТАНОВИТЬ} (есть bottom),
     *       {@code ПРИМЕНИТЬ} (низ помечен SPEC), {@code ОЧКИ} (низ SCORING);</li>
     *   <li>КОНТЕЙНЕР: {@code ВАРИАНТ_А} и {@code ВАРИАНТ_Б} по полям a и b;</li>
     *   <li>КАРТА СДЕЛОК НА РЫНКЕ: {@code ЛЕВОЕ} и {@code ПРАВОЕ}.</li>
     * </ul>
     *
     * <p>Набор тестов на карту обязан покрыть каждый способ из этого списка. Если
     * способ в данных есть, а теста нет — карта проверена не полностью, и это
     * видно проверкой {@link #способыБезПроверки}.
     */
    public enum Способ {
        ВЫПОЛНИТЬ, УСИЛИТЬ, СЖЕЧЬ, УСТАНОВИТЬ, ПРИМЕНИТЬ, ОЧКИ,
        ВАРИАНТ_А, ВАРИАНТ_Б, ЛЕВОЕ, ПРАВОЕ
    }

    /** Способы разыграть эту карту — по её записи в каталоге. */
    @SuppressWarnings("unchecked")
    public static Set<Способ> способы(GameConfig cfg, String набор, String id) {
        Set<Способ> out = new LinkedHashSet<>();
        var set = cfg.content.get(набор);
        if (set == null) {
            return out;
        }
        Map<String, Object> c;
        try {
            c = set.byId(id);
        } catch (RuntimeException e) {
            return out;
        }
        if (c == null) {
            return out;
        }
        if (c.get("requirement") != null || c.get("sacrifice") != null) {
            out.add(Способ.ВЫПОЛНИТЬ);
        }
        if (c.get("enhanced") != null) {
            out.add(Способ.УСИЛИТЬ);
        }
        if (c.get("top") != null) {
            out.add(Способ.СЖЕЧЬ);
        }
        if (c.get("bottom") instanceof Map<?, ?> bm) {
            Map<String, Object> низ = (Map<String, Object>) bm;
            String вид = String.valueOf(низ.get("kind"));
            if ("SPEC".equals(вид)) {
                out.add(Способ.ПРИМЕНИТЬ);
            }
            if ("SCORING".equals(вид)) {
                out.add(Способ.ОЧКИ);
            }
            out.add(Способ.УСТАНОВИТЬ);
        }
        if (c.get("a") != null) {
            out.add(Способ.ВАРИАНТ_А);
        }
        if (c.get("b") != null) {
            out.add(Способ.ВАРИАНТ_Б);
        }
        if (c.get("left") != null) {
            out.add(Способ.ЛЕВОЕ);
        }
        if (c.get("right") != null) {
            out.add(Способ.ПРАВОЕ);
        }
        return out;
    }

    /** Каких способов набор тестов не покрыл. */
    public static Set<Способ> способыБезПроверки(GameConfig cfg, String набор, String id,
                                                 Set<Способ> покрыто) {
        Set<Способ> надо = new LinkedHashSet<>(способы(cfg, набор, id));
        надо.removeAll(покрыто);
        return надо;
    }

    /** Короткая сводка прогона — её печатают в сообщении упавшего теста. */
    public String сводка() {
        Set<String> типы = new LinkedHashSet<>();
        for (Map<String, Object> e : события) {
            типы.add(String.valueOf(e.get("type")));
        }
        return "точки решения: " + точкиРешения() + "; типы событий: " + типы;
    }
}
