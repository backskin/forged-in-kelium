package kelium.cards.objectives;

import java.util.LinkedHashMap;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.PlayerState;
import kelium.core.TurnJournal;
import kelium.engine.cards.CardContext;
import kelium.engine.cards.ObjectiveCard;

/**
 * КАРТА ЗАДАНИЯ, ЖИВУЩАЯ ЦЕЛИКОМ В КОДЕ.
 *
 * <p>ЧЕМ ЭТО ОТЛИЧАЕТСЯ ОТ ПРЕЖНЕГО УСТРОЙСТВА. Прежде карта была разорвана на
 * три куска: условие — строкой-предикатом в общем реестре движка, числа и награда
 * — в YAML, печатный текст — там же. Каждый кусок можно было править отдельно, и
 * они расходились молча. Цена известна: три карты ссылались на условие, которого
 * в реестре нет, лежали в колоде и не могли быть выполнены НИКОГДА; двадцать две
 * карты были пустыми обёртками над реестром и своего кода не имели вовсе.
 *
 * <p>Здесь у карты один источник: класс. {@link #лицо()} объявляет всё, что на
 * карте напечатано; {@link #satisfied} и {@link #satisfiedEnhanced} проверяют
 * условие своим кодом; {@link #burn} исполняет одноразовый эффект. Запись
 * каталога не читается, а ВЫГРУЖАЕТСЯ методом {@link #data()} — то есть движок,
 * визуализатор и справочник видят ровно то, что объявил класс.
 *
 * <p>ПОЧЕМУ ВЫГРУЗКА, А НЕ СВОЙ ФОРМАТ. Движок уже умеет раздавать награду,
 * брать жертву и показывать карту по записи каталога. Ломать это ради нового
 * формата — вторая большая переделка сверх нужной. Поэтому запись остаётся, но
 * перестаёт быть источником: её печатает карта.
 */
public abstract class ЗаданиеВКоде implements ObjectiveCard {

    private final String id;

    protected ЗаданиеВКоде(String id) {
        this.id = id;
    }

    /** ВСЁ, ЧТО НА КАРТЕ НАПЕЧАТАНО. Объявляется каждой картой в её классе. */
    public abstract Лицо лицо();

    /** Источник правды — этот класс; запись в файле он накрывает своей выгрузкой. */
    @Override
    public final boolean describesItself() {
        return true;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String name() {
        return лицо().имя();
    }

    @Override
    public final String describe() {
        return лицо().описание();
    }

    /**
     * Данные больше НЕ ПОДКЛЮЧАЮТСЯ: карта не читает YAML. Метод остаётся, потому
     * что его зовёт реестр для всех семейств карт, и пустое тело здесь — прямое
     * заявление «этой карте данные не нужны».
     */
    @Override
    public final void bind(Map<String, Object> entry) {
        // намеренно пусто: источник — класс, а не запись в файле
    }

    /**
     * ВЫГРУЗКА КАРТЫ В ЗАПИСЬ КАТАЛОГА.
     *
     * <p>Ключи — те же, которыми движок уже раздаёт награду и берёт жертву,
     * поэтому выгруженный каталог работает без правок в движке. Условие
     * выгружается не предикатом, а ПЕЧАТНЫМ ТЕКСТОМ: проверку делает код карты,
     * и держать рядом вторую, декларативную копию условия — это ровно тот разрыв,
     * из-за которого всё и переделано. Пометка {@code checked_by: card} говорит
     * движку, что условие спрашивать у карты.
     */
    @Override
    public final Map<String, Object> data() {
        Лицо л = лицо();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", л.имя());
        out.put("kind", л.вид() == Лицо.Вид.НАЧАЛЬНАЯ ? "starting" : "regular");
        out.put("type", switch (л.природа()) {
            case СОСТОЯНИЕ -> "state";
            case ПРОИСШЕСТВИЕ -> "incident";
            case ЖЕРТВА -> "sacrifice";
        });
        out.put("checked_by", "card");
        out.put("requirement", Map.of("условие", л.условие()));
        if (л.усиление() != null) {
            out.put("enhanced", Map.of("условие", л.усиление()));
        }
        if (!л.награда().пусто()) {
            out.put("base_reward", л.награда().выгрузить());
        }
        if (!л.сверх().пусто()) {
            out.put("special_reward", л.сверх().выгрузить());
        }
        if (л.верх() != null) {
            out.put("top", Map.of("label", л.верх()));
        }
        Map<String, Object> жертва = жертваВЗаписи();
        if (жертва != null) {
            out.put("sacrifice", жертва);
        }
        out.put("описание", л.описание());
        return out;
    }

    /**
     * ПЛАТА КАРТЫ-ЖЕРТВЫ в виде записи {@code {resource, amount}} — движок берёт
     * её сам, в момент розыгрыша. {@code null} у карт, которые ничего не требуют
     * сдавать.
     */
    protected Map<String, Object> жертваВЗаписи() {
        return null;
    }

    // ==================================================================
    //  ОБЩИЕ ВОПРОСЫ, КОТОРЫЕ ЗАДАЁТ ПОЛОВИНА КАРТ
    // ==================================================================

    /** Журнал этого хода — им проверяются требования-происшествия. */
    protected static TurnJournal.TurnFacts ход(CardContext ctx) {
        TurnJournal j = ctx.state().journal;
        return j == null ? new TurnJournal(ctx.state().numPlayers()).of(ctx.seat())
            : j.of(ctx.seat());
    }

    /** Гексы, на которых стоит ЦУ игрока. */
    protected static java.util.Set<String> гексыЦУ(PlayerState p) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                out.add(b.hexId);
            }
        }
        return out;
    }

    /** Доля пути до выполнения: сколько есть из скольких надо. */
    protected static double доля(double есть, double надо) {
        if (надо <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, есть / надо));
    }

    /**
     * Сжигание карты ради одноразового эффекта. Большинству заданий верх выдаёт
     * добро или бесплатное действие — это и есть общий случай.
     */
    @Override
    public boolean burn(CardContext ctx) {
        return false;
    }
}
