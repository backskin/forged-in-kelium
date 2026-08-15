package kelium.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * GameState — вся партия в конкретный момент времени.
 *
 * <p>Агрегат незыблемого ядра: игроки, поле, колоды, доска науки, счётчики
 * раунда и круга, жетон первого игрока, генератор случайностей. Движок изменяет
 * это состояние; агенты (ИИ) его наблюдают.
 *
 * <p>{@code config} хранится как Object, чтобы избежать цикла зависимостей с
 * пакетом dataio (как в Python-версии); движок приводит его к нужному типу.
 * {@code journal}/{@code combat} — служебные поля движка, привязываемые в run().
 */
public final class GameState {

    public final Object config;               // GameConfig (без прямой зависимости)
    public final List<PlayerState> players;
    public final Field field;
    public final TokenStats tokenStats;
    public final TechBoard tech;
    public final Map<String, Deck> decks;
    public final Random rng;

    public int round = 0;                     // с 1 после старта
    public int circle = 0;                    // 1..circlesPerRound
    public int firstPlayer = 0;               // место с жетоном первого игрока
    public String marketActive = null;        // id активной карты маркета
    // Карты супер-арсенала, выложенные В ОТКРЫТУЮ на вершинах треков при
    // подготовке: {trackId -> cardId}. Забираются на шаге 4 (по одной).
    public final Map<String, String> superArsenalOffer = new java.util.HashMap<>();
    /**
     * МЕШКИ ЖЕТОНОВ МОДУЛЕЙ («Модули 2.0», 12.08.2026): награда «модуль» = тянуть
     * случайный жетон из мешка своего цвета, и жетон из мешка ИЗВЛЕКАЕТСЯ. В
     * мешок при подготовке кладутся полные наборы по числу игроков. Пустые списки
     * = мешки выключены версией правил, модули выдаются по-старому счётчиком.
     */
    public final List<String> redBag = new ArrayList<>();
    public final List<String> blueBag = new ArrayList<>();

    /**
     * ЯЧЕЙКИ ПРЕДЛОЖЕНИЙ АКТИВНОЙ КАРТЫ РЫНКА: {@code marketCells[сторона][ячейка]}
     * = место игрока, чей кубик келемия там стоит, либо −1 (ячейка свободна).
     * Сторона 0 — левое предложение, 1 — правое; ячеек по две, ВТОРАЯ открыта
     * только при 3–4 игроках (на карте помечена «3+»).
     *
     * <p>Ячейки печатные и расходуются за раунд: занял — предложение для
     * остальных сузилось. Сбрасываются на Обновлении вместе со сменой карты.
     */
    public final int[][] marketCells = {{-1, -1}, {-1, -1}};
    public boolean finished = false;
    public Integer winner = null;
    public String winCondition = null;

    // Служебные объекты движка (привязываются в GameEngine.run()).
    /**
     * Факты ТЕКУЩЕГО хода: что игрок успел сделать (сколько построил, добыл,
     * кого убил). На них смотрят условия заданий. Привязывается движком.
     */
    public TurnJournal journal = null;
    /**
     * Разрешитель боя текущей партии. Тип нарочно {@code Object}: бой живёт в
     * пакете движка, а ядро о нём знать не должно. Привязывается движком.
     */
    public Object combat = null;
    // Агенты по местам (нужны эффектам/бою для inline-выборов и ответок).
    /** Агенты по местам — их спрашивают эффекты и бой. Привязывается движком. */
    public List<Agent> agents = null;

    public GameState(Object config, List<PlayerState> players, Field field,
                     TokenStats tokenStats, TechBoard tech,
                     Map<String, Deck> decks, Random rng, int firstPlayer) {
        this.config = config;
        this.players = players;
        this.field = field;
        this.tokenStats = tokenStats;
        this.tech = tech;
        this.decks = decks != null ? decks : new HashMap<>();
        this.rng = rng != null ? rng : new Random();
        this.firstPlayer = firstPlayer;
    }

    /**
     * ГЛУБОКАЯ КОПИЯ ПАРТИИ — то, без чего бот не может «подумать вперёд».
     *
     * <p>Копируется вся ИЗМЕНЯЕМАЯ обстановка: игроки с жетонами, поле, колоды,
     * доска науки, счётчики раунда/круга, журнал текущего хода. По ссылке
     * остаётся всё НЕИЗМЕНЯЕМОЕ за партию: конфигурация, правила, контент карт,
     * доски игроков и характеристики жетонов — копировать их бессмысленно и
     * дорого (копия делается десятки раз за ход).
     *
     * <p>ГСЧ копии — НОВЫЙ, с переданным зерном: так просчёт не сдвигает поток
     * случайностей настоящей партии (иначе одна и та же партия шла бы по-разному
     * в зависимости от того, сколько бот думал), а разные просчёты честно дают
     * разные раздачи — в этом и смысл усреднения по нескольким прогонам.
     *
     * <p>Служебные поля {@code combat}/{@code agents} НЕ переносятся: они держат
     * ссылки на прежнее состояние и агентов. Копию обязан привязать вызывающий
     * через {@code GameEngine.bind(copy, agents)}.
     */
    public GameState deepCopy(long rngSeed) {
        Map<Integer, Token> registry = new HashMap<>();
        List<PlayerState> ps = new ArrayList<>();
        for (PlayerState p : players) {
            ps.add(p.copyWithoutTrophies(registry));
        }
        // Второй проход: трофейное пространство держит ЧУЖИЕ жетоны — берём их
        // копии из общего реестра по uid, чтобы объект был ровно один.
        for (int i = 0; i < players.size(); i++) {
            for (Token t : players.get(i).trophySpace) {
                Token c = registry.get(t.uid());
                ps.get(i).trophySpace.add(c != null ? c : t);
            }
        }
        Map<String, Deck> dk = new HashMap<>();
        for (Map.Entry<String, Deck> e : decks.entrySet()) {
            dk.put(e.getKey(), e.getValue().copy());
        }
        GameState s = new GameState(config, ps, field.copy(), tokenStats, tech.copy(),
            dk, new Random(rngSeed), firstPlayer);
        s.round = round;
        s.circle = circle;
        s.marketActive = marketActive;
        s.superArsenalOffer.putAll(superArsenalOffer);
        s.redBag.addAll(redBag);
        s.blueBag.addAll(blueBag);
        for (int side = 0; side < marketCells.length; side++) {
            System.arraycopy(marketCells[side], 0, s.marketCells[side], 0,
                marketCells[side].length);
        }
        s.finished = finished;
        s.winner = winner;
        s.winCondition = winCondition;
        s.journal = journal == null ? null : journal.copy();
        return s;
    }

    /** Число игроков в партии. */
    public int numPlayers() {
        return players.size();
    }

    /** Места игроков по часовой стрелке, начиная с первого игрока. */
    public List<Integer> seatsInOrder() {
        int n = numPlayers();
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add((firstPlayer + i) % n);
        }
        return out;
    }

    /** Состояние игрока по номеру его места (seat). */
    public PlayerState player(int seat) {
        return players.get(seat);
    }
}
