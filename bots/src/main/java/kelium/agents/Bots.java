package kelium.agents;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import kelium.core.Agent;
import kelium.dataio.Locations;

/**
 * Bots — ЕДИНАЯ фабрика ботов по имени характера.
 *
 * <p>Решение дизайнера 12.08.2026: ВСЕ характеры — обученные ГЕНОМЫ, включая
 * «Исследователя» и «Хаос», которые раньше были жёстко прошитыми правилами.
 * Значит бот любого характера — это {@link StrategicAgent} с геномом своей
 * линии; отличаются только веса, а не код. Прошитых характеров больше нет.
 *
 * <p>Геном линии ищется в памяти ботов как {@code strategic_<N>p_<характер>.json};
 * нет файла — берётся базовый геном линии ({@code strategic_<N>p.json}) с
 * наложенным перекосом характера, а если и его нет — {@link Genome#defaults()}
 * с перекосом. Так свежесобранная сборка играет сразу, пусть и слабее.
 */
public final class Bots {

    private Bots() {
    }

    /** Все характеры, у которых есть своя линия эволюции. */
    public static final List<String> CHARACTERS =
        List.of("hawk", "dove", "opportunist", "balanced", "explorer", "chaos",
            // ВОИТЕЛЬ (заказ дизайнера 13.08.2026): единственная линия, которую
            // отбирают НЕ за победу вообще, а за отрыв, добытый агрессией, — плюс
            // за насыщение поля разными родами войск. Цель линии живёт в
            // Fitness.Goal.ВОЙНА; остальные характеры — это только перекос весов.
            "warlord",
            // АКСИОМА (заказ дизайнера 2026-08-15): очки и победа в отборе НЕ
            // УЧАСТВУЮТ ВООБЩЕ — линию отбирают за три заповеди напрямую: высота
            // сразу на всех трёх треках науки, уничтожения по трофейной цене (в
            // обломках, не «штука за штуку») плюс сырое число попаданий/убийств,
            // и разнообразие жетонов на поле (войска И здания). Цель линии живёт
            // в Fitness.Goal.АКСИОМА. Смысл линии — сравнить очки, которые
            // получаются САМИ, если слепо следовать этим трём заповедям, с
            // очками линии, обученной прямо на победу.
            "axiom",
            // ЖНЕЦ (заказ дизайнера 2026-08-15): линию учат уничтожать как можно
            // больше чужих жетонов за партию и при этом уцелеть. Очки и победа в
            // её отборе не участвуют вовсе — это измерительный прибор: сколько
            // жетонов в игре ВООБЩЕ можно снести, если ничего другого не хотеть.
            // Обучается ступенями: жатва → плюс наука на всех треках → плюс
            // усиленные задания (Fitness.Goal.ЖНЕЦ, ЖНЕЦ_НАУКА, ЖНЕЦ_ЗАДАНИЯ).
            "reaper");

    // Геномы читаются с диска один раз на процесс: батчи в тысячи партий иначе
    // тратят на чтение JSON больше, чем на саму игру.
    private static final Map<String, Genome> CACHE = new ConcurrentHashMap<>();

    /**
     * Бот характера {@code character} на месте {@code seat}.
     *
     * <p>ПРОСЧЁТ ВПЕРЁД включается настройкой запуска {@code -Dkelium.bots=просчёт}.
     * Такой бот заметно сильнее (перевес 69% по очкам против бота без просчёта на
     * 224 очных сравнениях) и заметно медленнее — он доигрывает копии партии. По
     * умолчанию оставлен быстрый: этой фабрикой пользуются и балансовые стенды на
     * десятки тысяч партий, где просчёт удорожает прогон в разы. Для показательной
     * партии, разбора и игры с живым человеком включать стоит.
     */
    public static Agent create(String character, int seat, Random rng, int players) {
        Genome g = genome(character, players);
        return switch (search) {
            case ГЛУБОКИЙ -> SearchAgent.deep(seat, rng, g, character);
            case СРЕДНИЙ -> SearchAgent.mid(seat, rng, g, character);
            case ОТСЕВ -> SearchAgent.fast(seat, rng, g, character);
            case НЕТ -> new StrategicAgent(seat, rng, g, character);
        };
    }

    /**
     * ГЛУБИНА ПРОСЧЁТА ВПЕРЁД — насколько дорого бот думает.
     *
     * <ul>
     *   <li>{@code НЕТ} — только формула по весам. Быстро; этим гоняются
     *       балансовые стенды на десятки тысяч партий;</li>
     *   <li>{@code ОТСЕВ} — ходы проверяются на копии, холостые вычёркиваются;</li>
     *   <li>{@code СРЕДНИЙ} — плюс доигрывание на раунд вперёд. Отличает вложение
     *       от растраты: Стройка и Маркет на один ход выглядят убытком;</li>
     *   <li>{@code ГЛУБОКИЙ} — плюс доигрывание партии на выборе приказа. Состав
     *       подобран замером; перевес 69% по очкам против бота без просчёта. Заметно
     *       медленнее — для показательных партий и разбора.</li>
     * </ul>
     */
    public enum Search { НЕТ, ОТСЕВ, СРЕДНИЙ, ГЛУБОКИЙ }

    /**
     * ПЕРЕКЛЮЧАЕТСЯ НА ХОДУ. Раньше это была константа, вычисленная при загрузке
     * класса из {@code -Dkelium.bots}: из окна её поменять было нельзя вообще, а
     * настройка запуска доступна не всякому. Начальное значение по-прежнему берётся
     * из того же свойства, поэтому старые командные строки работают как работали.
     */
    private static volatile Search search =
        System.getProperty("kelium.bots", "").toLowerCase(java.util.Locale.ROOT)
            .startsWith("просч") ? Search.ГЛУБОКИЙ : Search.НЕТ;

    /** Какой просчёт сейчас действует. */
    public static Search search() {
        return search;
    }

    /** Задать глубину просчёта для всех ботов, которых выдаст эта фабрика дальше. */
    public static void setSearch(Search s) {
        search = s == null ? Search.НЕТ : s;
    }

    /** Играют ли боты этой фабрики с просчётом вперёд. */
    public static boolean searchByDefault() {
        return search != Search.НЕТ;
    }

    /** Одной строкой: чем играют боты — для шапки любого отчёта. */
    public static String describe() {
        return switch (search) {
            case ГЛУБОКИЙ -> "С ГЛУБОКИМ ПРОСЧЁТОМ ВПЕРЁД";
            case СРЕДНИЙ -> "со средним просчётом вперёд";
            case ОТСЕВ -> "с отсевом холостых ходов";
            case НЕТ -> "обычные (без просчёта)";
        };
    }

    /** Геном линии характера для состава на {@code players} игроков. */
    public static Genome genome(String character, int players) {
        String key = players + "/" + character;
        return CACHE.computeIfAbsent(key, k -> load(character, players));
    }

    /** Забыть прочитанные геномы (нужно обучению: файлы меняются на ходу). */
    public static void forgetCache() {
        CACHE.clear();
    }

    private static Genome load(String character, int players) {
        Path dir = Locations.botMemory();
        String prof = character == null || character.isBlank() ? "balanced" : character;
        Path own = dir.resolve("strategic_" + players + "p_" + prof + ".json");
        try {
            return Genome.loadJson(own);
        } catch (Exception ignored) {
            // линия ещё не обучена — берём базовую с перекосом характера
        }
        try {
            return Genome.loadJson(dir.resolve("strategic_" + players + "p.json"))
                .withProfile(prof);
        } catch (Exception ignored) {
            return Genome.defaults().withProfile(prof);
        }
    }
}
