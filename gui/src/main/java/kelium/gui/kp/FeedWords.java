package kelium.gui.kp;

import java.util.regex.Pattern;

/**
 * ЛЕНТА ПАРТИИ ПО-РУССКИ (сдача под ключ 25.09.2026). Отчёты движка о
 * действиях пишутся для журнала и разбора — «moved 3 tokens», «energy swap: 1
 * activation(s)…», коды зданий «miner L2». Игроку в ленте они показываются
 * словами; журнал на диске остаётся как был.
 */
public final class FeedWords {

    private FeedWords() {
    }

    private static final Object[][] RULES = {
        {"moved (\\d+) tokens", "сходило жетонов: $1"},
        {"moved (\\d+) steps", "шагов: $1"},
        {"moved ([a-z_]+) \\S+ -> \\S+", "здание $1 перенесено"},
        {"energy swap: (\\d+) activation(?:\\(s\\))?, (\\d+) placed, (\\d+) taken back",
            "запитано зданий: $1, кубиков поставлено: $2, снято: $3"},
        {"energy swap: nothing to do", "менять нечего"},
        {"(?:рынок|market): -(\\d+) kelium -> \\+(\\d+) coin", "рынок: −$1 келемия → +$2 монет"},
        {"market: no kelium", "рынок: нет келемия"},
        {"market: no trade", "рынок: без сделки"},
        {"market: ", "рынок: "},
        {"science: ", "наука: "},
        {"exchange draw_arsenal:\\S+", "обмен — взята карта арсенала"},
        {"exchange move_module\\S*", "обмен — модуль переставлен"},
        {"exchange gild\\S*", "обмен — модуль позолочен"},
        {"combat resolved x(\\d+)", "боёв: $1"},
        {"combat: ", ""},
        {"mined (\\d+) kelium, (\\d+) containers", "добыто келемия: $1, контейнеров: $2"},
        {"assembled (\\d+) units, (\\d+) ammo", "нанято войск: $1, боеприпасов: $2"},
        {"built nothing", "ничего не построено"},
        {"no room to build ", "некуда поставить "},
        {"repaired ", "починено: "},
        {"demolished ", "снесено: "},
        {"\\((\\d+) max: ", "("},
        {"ПРИКАЗ: JOKER", "ПРИКАЗ: ЗАТАИТЬСЯ"},
        {"\\.specialized\\b", " (спец-атака)"},
        {"\\.universal\\b", ""},
        {" (\\d+) trophy разом -> (\\d+) coin", " $1 трофея разом → $2 монет"},
        {"(\\d+) trophy -> (\\d+) coin", "$1 трофей → $2 монета"},
        {"\\s*\\[([^\\[\\]]*)\\]", " — $1"},
    };

    private static final Pattern[] PATTERNS = new Pattern[RULES.length];

    static {
        for (int i = 0; i < RULES.length; i++) {
            PATTERNS[i] = Pattern.compile((String) RULES[i][0]);
        }
    }

    /** Строка ленты словами. */
    public static String ru(String text) {
        if (text == null) {
            return "";
        }
        String s = text;
        for (int i = 0; i < RULES.length; i++) {
            s = PATTERNS[i].matcher(s).replaceAll((String) RULES[i][1]);
        }
        // коды зданий и войск, гексы — той же чисткой, что подписи вариантов
        return ChoiceWords.tidy(s);
    }
}
