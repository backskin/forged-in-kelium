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

    /**
     * ВИТРИНА АРСЕНАЛА — две открытые карты рядом с планшетом науки.
     *
     * <p>Правило дизайнера 15.08.2026: карты арсенала больше не берут вслепую.
     * Рядом с планшетом науки лежат ДВЕ открытые карты и колода; покупая арсенал,
     * игрок ВЫБИРАЕТ одну из двух, и освободившееся место немедленно пополняется
     * с верха колоды.
     *
     * <p>Карта на витрине ФИЗИЧЕСКИ ушла из колоды — вытянуть её вслепую нельзя,
     * пока она лежит здесь. Награда «карта арсенала» с задания берётся, наоборот,
     * вслепую с верха колоды, витрины не касаясь.
     */
    public final java.util.List<String> arsenalDisplay = new java.util.ArrayList<>();
    public int circle = 0;                    // 1..circlesPerRound
    public int firstPlayer = 0;               // место с жетоном первого игрока

    /**
     * ЖЕТОН ПЕРВОГО ИГРОКА НЕ ПЕРЕДАЁТСЯ в следующее Обновление.
     *
     * <p>Карта рынка «Штаб корпуса», предложение «Приоритет»: игрок ЗАБИРАЕТ
     * жетон себе сейчас же, и правило передачи по кругу на этот один раз
     * отменяется — иначе жетон уехал бы к соседу, не дав ничего.
     *
     * <p>Флаг одноразовый: Обновление, пропустив передачу, сразу его снимает.
     */
    public boolean firstPlayerHeld = false;
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
    /**
     * СКОЛЬКО ИСТОЧНИКОВ КЕЛЕМИЯ ОСТАЛОСЬ, когда партия кончилась по ним, и при
     * каком пороге свода. Нужны, чтобы подпись конца не врала: при пороге выше
     * единицы келемий на поле ещё ЕСТЬ, а партия уже кончилась (жалоба
     * дизайнера 02.09.2026 — «пишется, что кончился келемий, а на поле два
     * тайла»). −1 означает «партия кончилась не по келемию».
     */
    public int spawnLeftAtEnd = -1;
    public int spawnThreshold = -1;

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
    /**
     * Контекст ТЕКУЩЕГО хода как памятка для отката (см. {@link TurnUndo}).
     * Привязывается движком на время хода; вне хода — null. В {@link #deepCopy}
     * и {@link #restoreFrom} НЕ переносится нарочно: он принадлежит живому
     * стеку движка, а не снимку.
     */
    public TurnUndo turnUndo = null;

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
        s.firstPlayerHeld = firstPlayerHeld;
        s.superArsenalOffer.putAll(superArsenalOffer);
        // Витрина арсенала — часть состояния стола, и копия партии для просчёта
        // обязана её нести: без этого бот, доигрывая копию, «видел» бы пустую
        // витрину и не мог оценить покупку арсенала вовсе.
        s.arsenalDisplay.addAll(arsenalDisplay);
        s.redBag.addAll(redBag);
        s.blueBag.addAll(blueBag);
        for (int side = 0; side < marketCells.length; side++) {
            System.arraycopy(marketCells[side], 0, s.marketCells[side], 0,
                marketCells[side].length);
        }
        s.finished = finished;
        s.winner = winner;
        s.winCondition = winCondition;
        s.spawnLeftAtEnd = spawnLeftAtEnd;
        s.spawnThreshold = spawnThreshold;
        s.journal = journal == null ? null : journal.copy();
        return s;
    }

    /**
     * ОТКАТ НА МЕСТЕ — вернуть ЭТОТ объект (тот же самый, не новый) к
     * состоянию {@code snapshot} (обычно — результат более раннего
     * {@link #deepCopy}). Нужен живому окну (заказ на цифровую версию):
     * пока игрок пробует Стройку/Добычу/Манёвр и передумывает, отменить их
     * можно, ПОКА никто другой ещё не увидел результат (см. {@code
     * UndoableAgent} — там же граница, где это безопасно вызывать).
     *
     * <p>ПОЧЕМУ НА МЕСТЕ, А НЕ ПОДМЕНОЙ ССЫЛКИ: движок ({@code GameEngine})
     * держит {@code GameState} одной и той же ссылкой всю партию, и
     * {@code GameEngine.playActions(PlayerState p, ...)} держит СВОЮ ссылку
     * на {@code PlayerState} параметром на ВЕСЬ ход (не на одно действие) —
     * это ПРОВЕРЕНО тестом ({@code GameStateRestoreFromTest}): первая версия
     * этого метода подменяла игроков в списке новыми объектами, и второй
     * откат за тот же ход возвращал не то состояние, потому что чужой код
     * продолжал молча писать в осиротевший старый объект. Поэтому игроки и
     * гексы восстанавливаются {@link PlayerState#restoreFrom}/{@link
     * Hex#restoreFrom} — НА МЕСТЕ, поле за полем (см. {@link StateRestore}),
     * а не пересозданием списка/карты. Вызывать это можно только МЕЖДУ
     * вызовами {@code choose} (граница «какое действие» — см. {@code
     * UndoableAgent}), никогда пока какой-то код по действию ещё «на стеке».
     *
     * @param pinnedSeed тот же сид, что был передан в {@code deepCopy} при
     *                   снятии {@code snapshot} — ГСЧ живой партии ставится
     *                   на ту же точку, чтобы дальнейшая случайность была
     *                   воспроизводима от исходного сида партии (заказ §3:
     *                   «партию можно детерминированно воспроизвести»), а не
     *                   от посторонней энтропии, которую откат бы иначе внёс.
     */
    public void restoreFrom(GameState snapshot, long pinnedSeed) {
        GameState fresh = snapshot.deepCopy(pinnedSeed);
        for (int i = 0; i < players.size(); i++) {
            players.get(i).restoreFrom(fresh.players.get(i));
        }
        for (Hex h : field.hexes.values()) {
            Hex fh = fresh.field.hexes.get(h.id);
            if (fh != null) {
                h.restoreFrom(fh);
            }
        }
        tech.occupancy.clear();
        tech.occupancy.putAll(fresh.tech.occupancy);
        tech.firstArriverClaimed.clear();
        tech.firstArriverClaimed.putAll(fresh.tech.firstArriverClaimed);
        tech.stepOneArrivals.clear();
        tech.stepOneArrivals.putAll(fresh.tech.stepOneArrivals);
        decks.clear();
        decks.putAll(fresh.decks);
        round = fresh.round;
        circle = fresh.circle;
        firstPlayer = fresh.firstPlayer;
        firstPlayerHeld = fresh.firstPlayerHeld;
        marketActive = fresh.marketActive;
        superArsenalOffer.clear();
        superArsenalOffer.putAll(fresh.superArsenalOffer);
        arsenalDisplay.clear();
        arsenalDisplay.addAll(fresh.arsenalDisplay);
        redBag.clear();
        redBag.addAll(fresh.redBag);
        blueBag.clear();
        blueBag.addAll(fresh.blueBag);
        for (int side = 0; side < marketCells.length; side++) {
            System.arraycopy(fresh.marketCells[side], 0, marketCells[side], 0,
                fresh.marketCells[side].length);
        }
        finished = fresh.finished;
        winner = fresh.winner;
        winCondition = fresh.winCondition;
        spawnLeftAtEnd = fresh.spawnLeftAtEnd;
        spawnThreshold = fresh.spawnThreshold;
        journal = fresh.journal;
        rng.setSeed(pinnedSeed);
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
