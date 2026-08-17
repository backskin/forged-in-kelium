package kelium.gui.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.report.ReplayRecord;

/**
 * СЦЕНА — РУЧНОЕ СОСТОЯНИЕ ИГРЫ ДЛЯ ПРОВЕРКИ ОТОБРАЖЕНИЯ.
 *
 * <p>ЗАЧЕМ. Проигрыватель до сих пор умел показывать только СЫГРАННУЮ партию. Чтобы
 * посмотреть, как рисуется, скажем, гекс с четырьмя разными жетонами, подбитая
 * авиабаза на жёлтом секторе и счётчик супероружия на трёх ячейках, надо было
 * играть партии, пока такое не сложится само. Обычно не складывалось: половина
 * состояний в случайной партии не встречается за сотню прогонов, а нужное
 * сочетание — почти никогда. Проверить отрисовку было нечем.
 *
 * <p>Сцена — это состояние, собранное РУКАМИ и показанное сразу. Не сыгранная
 * партия, а один кадр: поставил что хотел, где хотел, и смотришь.
 *
 * <p>ЧТО СЦЕНА НЕ ДЕЛАЕТ. Она не проверяет правила. Можно поставить технику на
 * гекс без двух свободных секторов и авиацию в занятое небо — и это НАРОЧНО:
 * отрисовка обязана выдерживать и то, чего в партии не бывает, а если рисовальщик
 * ломается только на невозможном состоянии, знать об этом всё равно надо.
 *
 * <p>Как пользоваться:
 * <pre>
 * Scene.of(4)
 *     .building(0, "h0_0", BuildingType.MINER, 3).damage(1)
 *     .unit(1, "h0_0", UnitType.VEHICLE)
 *     .res(0, Resource.COIN, 9)
 *     .market("corps_hq").marketCell(0, 0, 1)
 *     .superCells(0, 3)
 *     .show();
 * </pre>
 * Каждый вызов возвращает саму сцену, а {@code damage} и подобные уточнения
 * относятся к ПОСЛЕДНЕМУ поставленному жетону.
 */
public final class Scene {

    private final GameState state;
    private final GameConfig cfg;
    private final int players;
    private String title = "сцена";
    /** Последний поставленный жетон — к нему относятся уточнения вида damage(). */
    private Object last;
    private int nextUid = 9000;

    private Scene(GameState state, GameConfig cfg, int players) {
        this.state = state;
        this.cfg = cfg;
        this.players = players;
    }

    /**
     * ПУСТОЙ СТОЛ на {@code players} игроков: поле, блоки, планшеты и колоды
     * настоящие, но НИ ОДНОГО жетона на поле нет.
     *
     * <p>Почему начинаем с настоящей подготовки, а не с нуля: поле, печатные
     * контейнеры, жёлтые секторы и стороны планшетов — это данные, и подделывать
     * их вручную значит проверять отрисовку не той игры.
     */
    public static Scene of(int players) {
        return of(players, 20250817L);
    }

    /** То же с выбранным зерном: раскладка поля зависит от него. */
    public static Scene of(int players, long seed) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        Scene sc = new Scene(s, cfg, players);
        sc.clearField();
        return sc;
    }

    /**
     * НАСТОЯЩАЯ ПАРТИЯ, доигранная до раунда {@code round}, — и дальше правь
     * руками.
     *
     * <p>Нужно, когда сцену проще получить из живой игры, чем выставить: «поле
     * середины партии, но у второго игрока отнять всю энергию».
     */
    public static Scene played(int players, long seed, int round, List<String> characters) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        List<kelium.core.Agent> agents = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            String c = characters == null || characters.isEmpty()
                ? "balanced" : characters.get(i % characters.size());
            agents.add(kelium.agents.Bots.create(c, i, new Random(seed * 31 + i), players));
        }
        new kelium.engine.GameEngine(s, agents, ev -> { }).runToRound(round);
        return new Scene(s, cfg, players);
    }

    /** Убрать с поля все жетоны всех игроков (нейтралы и тайлы остаются). */
    public Scene clearField() {
        for (PlayerState p : state.players) {
            for (BuildingToken b : new ArrayList<>(p.buildingsOnField())) {
                state.field.get(b.hexId).freeSidesByToken(b.uid);
                b.hexId = null;
            }
            for (UnitToken u : new ArrayList<>(p.unitsOnField())) {
                u.setHexId(null);
            }
        }
        return this;
    }

    /** Подпись сцены — попадёт в заголовок окна и в имя файла снимка. */
    public Scene title(String t) {
        this.title = t == null || t.isBlank() ? "сцена" : t;
        return this;
    }

    // ================== ПОЛЕ ==================

    /**
     * Поставить ЗДАНИЕ игрока {@code seat} на гекс.
     *
     * @param level уровень для добытчика и энергостанции (1..4), иначе {@code null}
     */
    public Scene building(int seat, String hexId, BuildingType type, Integer level) {
        return building(seat, hexId, type, level, null);
    }

    /**
     * То же, но с выбранным ПОВОРОТОМ: {@code facing} — номер первого занятого
     * сектора (0..5). {@code null} — первый подходящий.
     */
    public Scene building(int seat, String hexId, BuildingType type, Integer level,
                          Integer facing) {
        PlayerState p = state.player(seat);
        BuildingToken b = state.tokenStats.makeBuilding(type, seat, nextUid++, level);
        p.buildings.add(b);
        b.hexId = hexId;
        Hex h = state.field.get(hexId);
        int fp = footprint(type);
        List<Integer> sides = new ArrayList<>();
        if (facing != null) {
            for (int i = 0; i < fp; i++) {
                sides.add((facing + i) % 6);
            }
        } else {
            // Первые СМЕЖНЫЕ свободные секторы; не нашлось — ставим всё равно, на
            // секторы с нуля. Сцена правила не проверяет НАРОЧНО: перекрытие
            // жетонов — само по себе то, на что надо посмотреть.
            List<Integer> free = h.freeSideIndices();
            for (int i = 0; i < 6 && sides.isEmpty(); i++) {
                boolean fits = true;
                for (int k = 0; k < fp; k++) {
                    fits &= free.contains((i + k) % 6);
                }
                if (fits) {
                    for (int k = 0; k < fp; k++) {
                        sides.add((i + k) % 6);
                    }
                }
            }
            for (int i = 0; sides.isEmpty() && i < fp; i++) {
                sides.add(i);
            }
        }
        for (Integer i : sides) {
            h.sideOwner[i] = b.uid;
        }
        last = b;
        return this;
    }

    /** Поставить ВОЙСКО игрока {@code seat} на гекс. */
    public Scene unit(int seat, String hexId, UnitType type) {
        PlayerState p = state.player(seat);
        UnitToken u = state.tokenStats.makeUnit(type, seat, nextUid++, p.unitsOfKind(type));
        p.units.add(u);
        u.setHexId(hexId);
        last = u;
        return this;
    }

    /** Войско ВНУТРИ здания (гарнизон) — на том же гексе, что и здание. */
    public Scene garrison(int seat, String hexId, UnitType type) {
        unit(seat, hexId, type);
        if (last instanceof UnitToken u) {
            for (BuildingToken b : state.player(seat).buildingsOnField()) {
                if (hexId.equals(b.hexId)) {
                    u.insideBuildingUid = b.uid;
                    break;
                }
            }
        }
        return this;
    }

    /** Урон на ПОСЛЕДНЕМ поставленном жетоне. */
    public Scene damage(int n) {
        if (last instanceof BuildingToken b) {
            b.damage = n;
        } else if (last instanceof UnitToken u) {
            u.damage = n;
        }
        return this;
    }

    /** Кубики энергии на ПОСЛЕДНЕМ поставленном здании (источник — «ниоткуда»). */
    public Scene energy(int n) {
        if (last instanceof BuildingToken b) {
            b.addEnergyFrom(-1, n);
        }
        return this;
    }

    /** Простаивающие кубики НА источнике (последнем поставленном здании). */
    public Scene idleEnergy(int n) {
        if (last instanceof BuildingToken b) {
            b.energyIdle = n;
        }
        return this;
    }

    /** Нейтральное здание на указанные секторы гекса. */
    public Scene neutral(String hexId, boolean big, Integer... sectors) {
        Hex h = state.field.get(hexId);
        int uid = -1000 - h.neutrals.size();
        h.neutrals.add(new Hex.NeutralBuilding(uid, big, List.of(sectors)));
        for (Integer i : sectors) {
            h.sideOwner[i] = uid;
        }
        return this;
    }

    // ================== ПЛАНШЕТЫ И ЗАПАСЫ ==================

    /** Ресурс в хранилище игрока (ставится НАПРЯМУЮ, потолок не проверяется). */
    public Scene res(int seat, Resource r, int n) {
        PlayerState p = state.player(seat);
        int now = p.resources.get(r);
        p.resources.add(r, n - now);
        return this;
    }

    /** Карты заданий на руке игрока (идентификаторы из каталога). */
    public Scene objectiveHand(int seat, String... ids) {
        PlayerState p = state.player(seat);
        p.objectiveHand.clear();
        p.objectiveHand.addAll(List.of(ids));
        return this;
    }

    /** Карты арсенала на руке игрока. */
    public Scene arsenalHand(int seat, String... ids) {
        PlayerState p = state.player(seat);
        p.arsenalHand.clear();
        p.arsenalHand.addAll(List.of(ids));
        return this;
    }

    /** Установленные карты арсенала игрока. */
    public Scene arsenalInstalled(int seat, String... ids) {
        PlayerState p = state.player(seat);
        p.arsenalInstalled.clear();
        p.arsenalInstalled.addAll(List.of(ids));
        return this;
    }

    /**
     * Достигнутый шаг на треке науки. {@code trackId} — идентификатор трека из
     * данных («red», «green», «blue»): прогресс хранится картой, а не тремя
     * числами, потому что треков в правилах может стать больше.
     */
    public Scene techStep(int seat, String trackId, int step) {
        state.player(seat).techSteps.put(trackId, step);
        return this;
    }

    /** Жетоны модулей игрока: красные, синие, позолоченные. */
    public Scene modules(int seat, int red, int blue, int gold) {
        PlayerState p = state.player(seat);
        p.redModules = red;
        p.blueModules = blue;
        p.goldModules = gold;
        return this;
    }

    // ================== КОЛОДЫ И СБРОСЫ ==================

    /**
     * ЗАДАТЬ КОЛОДУ НАБОРА — карты в порядке СВЕРХУ ВНИЗ, как их видит человек.
     *
     * <p>Разворот в порядок движка делается здесь: {@code Deck.draw} снимает карту
     * с КОНЦА списка, и если бы разворота не было, сцена показывала бы колоду вверх
     * ногами относительно того, что вытянется следующим.
     */
    public Scene deck(String набор, String... сверхуВниз) {
        kelium.core.Deck d = state.decks.get(набор);
        if (d == null) {
            return this;
        }
        d.drawPile.clear();
        for (int i = сверхуВниз.length - 1; i >= 0; i--) {
            d.drawPile.add(сверхуВниз[i]);
        }
        return this;
    }

    /** Задать СБРОС набора: карты сверху вниз, нулевая сброшена последней. */
    public Scene discard(String набор, String... сверхуВниз) {
        kelium.core.Deck d = state.decks.get(набор);
        if (d == null) {
            return this;
        }
        d.discardPile.clear();
        for (int i = сверхуВниз.length - 1; i >= 0; i--) {
            d.discardPile.add(сверхуВниз[i]);
        }
        return this;
    }

    /** Оставить в колоде только первые {@code сколько} карт — «колода на исходе». */
    public Scene deckSize(String набор, int сколько) {
        kelium.core.Deck d = state.decks.get(набор);
        if (d == null) {
            return this;
        }
        while (d.drawPile.size() > Math.max(0, сколько)) {
            d.drawPile.remove(0);
        }
        return this;
    }

    /** Две открытые карты витрины арсенала (первая лежит слева). */
    public Scene arsenalDisplay(String... ids) {
        state.arsenalDisplay.clear();
        state.arsenalDisplay.addAll(List.of(ids));
        return this;
    }

    // ================== ПРИКАЗЫ, ТРОФЕИ, ЖЕТОНЫ ЦУ ==================

    /** Рука приказов игрока (закрыта для соседей, но сцена ставит её как есть). */
    public Scene orderHand(int seat, String... ids) {
        PlayerState p = state.player(seat);
        p.orderHand = new ArrayList<>(List.of(ids));
        return this;
    }

    /** Разыгранные в этом раунде приказы — они лежат открыто. */
    public Scene orderPlayed(int seat, String... ids) {
        PlayerState p = state.player(seat);
        p.orderPlayed.clear();
        p.orderPlayed.addAll(List.of(ids));
        return this;
    }

    /** Отложенный слепым сбросом приказ (рубашкой вверх) и цвет колоды. */
    public Scene orders(int seat, String отложен, String цвет) {
        PlayerState p = state.player(seat);
        p.orderSetAside = отложен;
        if (цвет != null) {
            p.orderColor = цвет;
        }
        return this;
    }

    /**
     * ТРОФЕИ НА ТРОФЕЙНОЙ КАРТЕ: чужие жетоны, перевёрнутые на трофейную сторону.
     *
     * <p>Берутся у названного соседа с поля, поэтому на карте оказываются жетоны
     * его цвета — как за столом.
     */
    public Scene trophies(int seat, int уКого, int сколько) {
        PlayerState мой = state.player(seat);
        PlayerState чужой = state.player(уКого);
        int взято = 0;
        for (kelium.core.UnitToken u : new ArrayList<>(чужой.unitsOnField())) {
            if (взято >= сколько) {
                break;
            }
            u.setHexId(null);
            мой.trophySpace.add(u);
            взято++;
        }
        return this;
    }

    /** Карты контейнеров на руке — лежат закрытыми, публично только число. */
    public Scene containers(int seat, int сколько) {
        state.player(seat).containers = сколько;
        return this;
    }

    /** Жетоны уничтожения ЦУ: сколько чужих собрано и на месте ли свой. */
    public Scene cuTokens(int seat, int чужих, boolean свойНаМесте) {
        PlayerState p = state.player(seat);
        p.cuDestructionTokens = чужих;
        p.ownCuTokenAvailable = свойНаМесте;
        return this;
    }

    /**
     * ПАРТИЯ ОКОНЧЕНА — нужно экрану итогов: без этого его нечем посмотреть.
     *
     * @param winner   место победителя ({@code null} — победителя нет)
     * @param условие  чем закончилась: «vp», «cu», «super» и прочее из движка
     */
    public Scene finished(Integer winner, String условие) {
        state.finished = true;
        state.winner = winner;
        state.winCondition = условие;
        return this;
    }

    // ================== РЫНОК И СУПЕР-ЗАДАНИЕ ==================

    /** Активная карта рынка (идентификатор из каталога). */
    public Scene market(String cardId) {
        state.marketActive = cardId;
        return this;
    }

    /**
     * Занять ячейку предложения: {@code side} — 0 левое, 1 правое;
     * {@code cell} — 0 или 1; {@code seat} — кто занял.
     */
    public Scene marketCell(int side, int cell, int seat) {
        if (side >= 0 && side < state.marketCells.length
                && cell >= 0 && cell < state.marketCells[side].length) {
            state.marketCells[side][cell] = seat;
        }
        return this;
    }

    /**
     * ВЫДАННАЯ КАРТА СУПЕР-ЗАДАНИЯ и её первая часть.
     *
     * <p>Без этого планшет пишет «супер-задание: не выдано», даже когда счётчик
     * второй части уже выставлен: он смотрит на КАРТУ, а не на ячейки. Наступил
     * на это сразу же, снимая сцену со счётчиком.
     *
     * @param cardId   идентификатор карты из каталога супер-заданий
     * @param progress сколько ресурсов/жетонов уже положено в первой части
     */
    public Scene superObjective(int seat, String cardId, int progress) {
        PlayerState p = state.player(seat);
        p.superObjective = cardId;
        p.superObjectiveProgress = progress;
        return this;
    }

    /**
     * СУПЕРОРУЖИЕ НА ПОЛЕ: жетон-войско, помеченный как супероружие, и гекс
     * завода, который его собрал (по правилам оружие обязано отъехать от завода).
     */
    public Scene superWeapon(int seat, String hexId, UnitType type, String hiredHex) {
        unit(seat, hexId, type);
        PlayerState p = state.player(seat);
        if (last instanceof UnitToken u) {
            u.superUnit = true;
            p.superWeaponUid = u.uid;
        }
        p.superWeaponHiredHex = hiredHex;
        return this;
    }

    /**
     * ВТОРАЯ ЧАСТЬ СУПЕР-ЗАДАНИЯ: сколько ячеек счётчика ещё не погашено и какие
     * символы на них стоят. Символы берутся из разметки, если не заданы.
     */
    public Scene superCells(int seat, int cells, String... symbols) {
        PlayerState p = state.player(seat);
        p.superCells = cells;
        p.superCellSymbols.clear();
        if (symbols.length > 0) {
            p.superCellSymbols.addAll(List.of(symbols));
        } else {
            List<String> forms = kelium.engine.Symbols.of(state).allForms();
            for (int i = 0; i < cells; i++) {
                p.superCellSymbols.add(forms.isEmpty() ? "" : forms.get(i % forms.size()));
            }
        }
        return this;
    }

    /** Раунд и круг — они видны в строке контекста проигрывателя. */
    public Scene round(int round, int circle) {
        state.round = round;
        state.circle = circle;
        return this;
    }

    /** Жетон первого игрока. */
    public Scene firstPlayer(int seat) {
        state.firstPlayer = seat;
        return this;
    }

    // ================== ВЫВОД ==================

    /** Настройка партии сцены — нужна, чтобы дотянуться до каталогов карт. */
    public GameConfig cfg() {
        return cfg;
    }

    /** Само состояние — если сцене нужно что-то, чего в этом наборе нет. */
    public GameState state() {
        return state;
    }

    /** Подпись сцены. */
    public String titleText() {
        return title;
    }

    /**
     * Собрать ЗАПИСЬ ИЗ ОДНОГО КАДРА — её и читает проигрыватель.
     *
     * <p>Одного кадра достаточно: проигрыватель показывает кадр, а лента времени
     * просто окажется длиной в одно деление.
     */
    public ReplayRecord record() {
        ReplayRecord rec = new ReplayRecord();
        rec.players = players;
        rec.seed = 0;
        rec.ruleset = kelium.dataio.GameConfig.DEFAULT_RULESET;
        for (int i = 0; i < players; i++) {
            rec.seatLabels.add("сцена");
            rec.sides.add(state.player(i).board.troop.side);
        }
        for (kelium.core.UnitType t : kelium.core.UnitType.values()) {
            rec.unitStock.put(t.code, state.tokenStats.unitStock(t));
        }
        // НАЗВАНИЯ КАРТ И ГЕОМЕТРИЯ ПОЛЯ — тем же швом, что у настоящей партии.
        // Без геометрии проигрыватель рисует пустое поле: список гексов он берёт
        // из шапки записи, а не из снимка.
        kelium.gui.GameRecorder.fillTableAndField(rec, cfg, state);
        ReplayRecord.Frame f = new ReplayRecord.Frame();
        f.type = "scene";
        f.round = state.round;
        f.circle = state.circle;
        f.log = "СЦЕНА РАЗРАБОТЧИКА: " + title;
        f.snapshot = ReplayRecord.snapshotOf(state, 0);
        rec.frames.add(f);
        return rec;
    }

    /** Показать сцену в окне проигрывателя. */
    public void show() {
        DevMode.show(this);
    }

    private static int footprint(BuildingType t) {
        return kelium.engine.Actions.buildingFootprint(t);
    }
}
