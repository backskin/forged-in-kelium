package kelium.gui;

import kelium.dataio.AppSettings;
import kelium.rules.Ruleset;

/**
 * ДОПОЛНЕНИЯ — что включено в партии сверх базовой игры.
 *
 * <p>Решение дизайнера 17.08.2026: супер задания, начальные задания и
 * супер-арсенал — три ОТДЕЛЬНЫХ дополнения, каждое со своим выключателем, и
 * включать можно любое сочетание. Прежде это был один режим на три значения, и
 * супер задания с начальными исключали друг друга.
 *
 * <p>Выбор живёт в настройках приложения и переживает перезапуск: дополнение —
 * это не настройка одной партии, а то, во что человек сейчас играет.
 *
 * <p>Правилам он передаётся накладкой на свод ({@link #applyTo}) в момент
 * сборки партии — сами файлы правил при этом не трогаются: свод говорит, что
 * дополнения существуют и включены по умолчанию, а игрок решает, брать ли их на
 * стол.
 */
public final class Expansions {

    private Expansions() {
    }

    /** Ключи в настройках приложения и в своде правил — намеренно одинаковые. */
    public static final String SUPER_OBJECTIVES = "super_objectives";
    public static final String STARTING_OBJECTIVES = "starting_objectives";
    public static final String SUPER_ARSENAL = "super_arsenal";
    /**
     * КАРТЫ ПРЕДЛОЖЕНИЙ РЫНКА (заказ дизайнера 17.08.2026).
     *
     * <p>Выключено — на планшете рынка остаётся только НАПЕЧАТАННЫЙ обмен, а
     * колода карт рынка не раздаётся вовсе. Вместе с ними выключаются карты
     * заданий и арсенала, которые ссылаются на предложение с карты: без карт
     * рынка их условие невыполнимо, и держать их в колоде значит подсовывать
     * игроку мёртвую карту.
     */
    public static final String MARKET_CARDS = "market_cards";

    /** Все дополнения — нужно и записи партии, чтобы сохранить их состояние. */
    public static final String[] ALL =
        {SUPER_OBJECTIVES, STARTING_OBJECTIVES, SUPER_ARSENAL, MARKET_CARDS};

    /** Человеческое имя дополнения — для тумблера и для журнала. */
    public static String title(String name) {
        return switch (name) {
            case SUPER_OBJECTIVES -> "Супер задания";
            case STARTING_OBJECTIVES -> "Начальные задания";
            case SUPER_ARSENAL -> "Супер-арсенал";
            case MARKET_CARDS -> "Карты рынка";
            default -> name;
        };
    }

    /** Что это дополнение добавляет в партию — текст подсказки тумблера. */
    public static String tip(String name) {
        return switch (name) {
            case SUPER_OBJECTIVES ->
                "Проекты супероружия. Игроку раздаются две карты, одну он оставляет. "
                    + "Вскрытая карта даёт 3 победных очка и жетон супероружия; снятый "
                    + "до конца счётчик запуска — немедленная победа.";
            case STARTING_OBJECTIVES ->
                "Простые задания первого раунда для тех, кто садится за игру впервые. "
                    + "Играются вместе с супер заданиями или отдельно — как решите.";
            case SUPER_ARSENAL ->
                "Карты на вершинах треков технологий: супер-войска и постоянные "
                    + "способности. Выключено — вершина трека даёт ещё один свой жетон "
                    + "(красный трек красный модуль, синий синий, зелёный позолоту).";
            case MARKET_CARDS ->
                "Восемь карт с уникальными предложениями: каждый раунд действует одна. "
                    + "Выключено — на планшете рынка остаётся только напечатанный обмен, "
                    + "а карты заданий и арсенала, требующие предложение с карты, "
                    + "изымаются из колод.";
            default -> name;
        };
    }

    /** Включено ли дополнение сейчас (по умолчанию — да, все три). */
    public static boolean on(AppSettings settings, String name) {
        return settings == null || settings.getBoolean("expansions." + name, true);
    }

    public static void set(AppSettings settings, String name, boolean value) {
        if (settings != null) {
            settings.putBoolean("expansions." + name, value);
        }
    }

    /**
     * НАЛОЖИТЬ ВЫБОР ИГРОКА НА СВОД ПРАВИЛ. Зовётся в момент сборки партии, до
     * {@code Setup.buildGame}: движок читает те же ключи {@code expansions.*}.
     */
    public static void applyTo(Ruleset ruleset, AppSettings settings) {
        if (ruleset == null) {
            return;
        }
        for (String name : ALL) {
            ruleset.override("expansions." + name, on(settings, name));
        }
        // РАУНДЫ ПАРТИИ (заказ дизайнера 25.09.2026): подготовительный раунд
        // без карты рынка и число карт рынка на планшете (= раундов с рынком).
        // Не задано — как в своде.
        if (settings != null) {
            String prep = settings.get(PREP_ROUND, null);
            if (prep != null) {
                ruleset.override("market.preparatory_round", Boolean.parseBoolean(prep));
            }
            int cards = settings.getInt(MARKET_CARDS_COUNT, 0);
            if (cards > 0) {
                // от 4 до 10 (решение 25.09.2026); старые настройки могли хранить меньше
                ruleset.override("market.deck_size",
                    Math.max(StartMenuWindow.MIN_MARKET_CARDS, Math.min(10, cards)));
            }
        }
    }

    /** Ключ настроек: подготовительный раунд (true/false; нет ключа — как в своде). */
    public static final String PREP_ROUND = "rounds.preparatory";
    /** Ключ настроек: сколько карт рынка на планшете (0 — как в своде). */
    public static final String MARKET_CARDS_COUNT = "rounds.market_cards";

    /** Подготовительный раунд по настройкам (по умолчанию — выключен). */
    public static boolean prepRound(AppSettings settings) {
        return settings != null && Boolean.parseBoolean(settings.get(PREP_ROUND, "false"));
    }

    /** Короткая строка «что включено» — для журнала подготовки. */
    public static String summary(AppSettings settings) {
        StringBuilder sb = new StringBuilder();
        for (String name : ALL) {
            if (on(settings, name)) {
                sb.append(sb.length() == 0 ? "" : ", ").append(title(name));
            }
        }
        return sb.length() == 0 ? "без дополнений" : sb.toString();
    }
}
