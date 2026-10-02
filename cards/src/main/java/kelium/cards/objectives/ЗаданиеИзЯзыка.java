package kelium.cards.objectives;

import java.util.LinkedHashMap;
import java.util.Map;

import kelium.cards.Награда;
import kelium.cards.язык.Требование;
import kelium.engine.cards.CardContext;

/**
 * ЗАДАНИЕ, ЗАПИСАННОЕ ЯЗЫКОМ КАРТ (Карты 2.0, 30.09.2026).
 *
 * <p>Требование, усиление, награда и верх — данные записи {@code язык}; текст
 * карты печатается из узлов требования ({@link Требование#текст()}), руками
 * не пишется. Так карты собирает программа: новая карта — новая запись, без
 * класса под неё.
 *
 * <pre>
 *   - id: g_0001
 *     язык:
 *       имя: Двойной удар
 *       требование: {узел: обе_ветки, развилка: command}
 *       усиление: {узел: уничтожь, цель: {кто: ЗДАНИЕ, чьё: врага}, сколько: 1}
 *       награда: {действие: "mining|build_miner", монеты: 1}
 *       сверх: {спецДействий: 1}
 *       верх: ДВИЖЕНИЕ
 *       значок: command
 * </pre>
 *
 * <p>Усиленное требование — обычное И усиление («Дополнительно — …»).
 */
public final class ЗаданиеИзЯзыка extends ЗаданиеВКоде {

    private final Map<String, Object> запись;
    private final String имя;
    private final Требование требование;
    private final Требование усиление;
    private final Награда награда;
    private final Награда сверх;
    private final Утиль верх;
    private final String значок;

    public ЗаданиеИзЯзыка(String id, Map<String, Object> язык) {
        super(id);
        this.запись = new LinkedHashMap<>(язык);
        this.имя = String.valueOf(язык.getOrDefault("имя", id));
        this.требование = Требование.из(язык.get("требование"));
        this.усиление = язык.get("усиление") == null ? null : Требование.из(язык.get("усиление"));
        this.награда = награда(язык.get("награда"));
        this.сверх = награда(язык.get("сверх"));
        this.верх = язык.get("верх") == null ? null : Утиль.valueOf(String.valueOf(язык.get("верх")));
        this.значок = язык.get("значок") == null ? null : String.valueOf(язык.get("значок"));
    }

    /** Награда из записи: ключи — имена полей {@link Награда}. */
    static Награда награда(Object o) {
        if (!(o instanceof Map<?, ?> m)) {
            return Награда.нет();
        }
        return new Награда(число(m, "монеты"), число(m, "боеприпасы"), число(m, "трофеи"),
            число(m, "келемий"), число(m, "картыЗаданий"), число(m, "картыАрсенала"),
            число(m, "картыСВитрины"), m.get("модуль") == null ? null : String.valueOf(m.get("модуль")),
            m.get("действие") == null ? null : String.valueOf(m.get("действие")),
            Boolean.TRUE.equals(m.get("позолота")), число(m, "спецДействий"));
    }

    private static int число(Map<?, ?> m, String ключ) {
        return m.get(ключ) instanceof Number n ? n.intValue() : 0;
    }

    @Override
    public Лицо лицо() {
        // Природу карты задаёт основное требование: усиление проверяется в тот же
        // миг, что и оно, и печатается продолжением («Дополнительно — …»).
        Лицо.Природа природа = требование.происшествие()
            ? Лицо.Природа.ПРОИСШЕСТВИЕ : Лицо.Природа.СОСТОЯНИЕ;
        String доп = усиление == null ? null
            : (усиление.происшествие() && !требование.происшествие() ? "в этот ход " : "")
                + усиление.суть();
        return печатное(имя, природа, требование.текст(), доп, награда, сверх, верх);
    }

    @Override
    public boolean satisfied(CardContext ctx) {
        return требование.выполнено(ctx);
    }

    @Override
    public boolean satisfiedEnhanced(CardContext ctx) {
        return усиление != null && требование.выполнено(ctx) && усиление.выполнено(ctx);
    }

    /**
     * ЧЕГО НЕ ХВАТАЕТ — словами карты. Прежде у карт языка этого не было вовсе
     * (пустая строка по умолчанию): подсказка в окне и договор карт молчали.
     */
    @Override
    public String needed(CardContext ctx) {
        if (требование.выполнено(ctx)) {
            return усиление != null && !усиление.выполнено(ctx)
                ? "выполнено; дополнительно — " + усиление.суть() : "готово";
        }
        return требование.текст() + String.format(" (пройдено %d%%)",
            Math.round(100 * Math.max(0, Math.min(1, требование.близость(ctx)))));
    }

    @Override
    public double progress(CardContext ctx) {
        return Math.max(0, Math.min(1, требование.близость(ctx)));
    }

    @Override
    protected String действие() {
        return требование.действие();
    }

    /** Значок развилки карты ({@code extract}, {@code command}…) или {@code null}. */
    public String значок() {
        return значок;
    }

    /** Требование карты — для генератора и измерителя. */
    public Требование требование() {
        return требование;
    }

    /** Выгрузка несёт и запись языка — чтобы карту можно было собрать заново. */
    public Map<String, Object> данныеЯзыка() {
        Map<String, Object> out = new LinkedHashMap<>(super.data());
        out.put("язык", запись);
        if (значок != null) {
            out.put("значок", значок);
        }
        return out;
    }
}
