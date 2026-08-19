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
            Map<String, Object> уж = усиленнаяЖертваВЗаписи();
            out.put("enhanced", уж != null ? уж : Map.of("условие", л.усиление()));
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
        if (отсев() != null) {
            out.put("cull", отсев());
        }
        out.put("описание", л.описание());
        return out;
    }

    /**
     * ОТСЕВ ПО ЧИСЛУ ИГРОКОВ: {@code "[4]"} — карту не раздают на четырёх, потому
     * что там она даётся слишком легко (соседей больше, чужих жетонов рядом
     * больше). {@code null} — карта играет при любом числе игроков.
     */
    protected String отсев() {
        return null;
    }

    /**
     * ПЛАТА КАРТЫ-ЖЕРТВЫ в виде записи {@code {resource, amount}} — движок берёт
     * её сам, в момент розыгрыша. {@code null} у карт, которые ничего не требуют
     * сдавать.
     */
    protected Map<String, Object> жертваВЗаписи() {
        return null;
    }

    /**
     * ДОПЛАТА ЗА УСИЛЕННУЮ ЖЕРТВУ — {@code {predicate: "sacrifice_enhanced",
     * params: {resource, amount}}}.
     *
     * <p>ЕДИНСТВЕННОЕ МЕСТО, ГДЕ ПЕРЕЕХАВШАЯ КАРТА ВСЁ ЕЩЁ ДЕРЖИТ ДЕКЛАРАТИВНУЮ
     * ЗАПИСЬ, А НЕ СВОЙ КОД. Доплата разницы до усиленной суммы — общий протокол
     * движка ({@code Objectives.playObjective}), а не поведение конкретной
     * карты: движок сам проверяет запас и сам списывает разницу в момент
     * розыгрыша. Прогонять это через {@code satisfiedEnhanced} нельзя — это
     * запрос без побочного эффекта, а списание требует побочного эффекта, и
     * дублировать протокол оплаты в каждой карте-жертве незачем.
     */
    protected Map<String, Object> усиленнаяЖертваВЗаписи() {
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
     * БЛИЗОСТЬ КАРТЫ-ПРОИСШЕСТВИЯ: средства для рывка уже есть, но сам рывок ещё
     * не сделан. Всегда не больше половины.
     *
     * <p>ЗАЧЕМ ОТДЕЛЬНЫЙ ХЕЛПЕР. У требования-происшествия («В ЭТОТ ХОД найми
     * войско») частичного выполнения не бывает: либо нанял, либо нет. Поэтому
     * такие карты возвращали ровно 0 до самого конца — и оба механизма, которые
     * на близость смотрят, были для них слепы: наведение внутри действия не
     * видело, куда вести, а защита от сжигания считала такую карту непочатой и
     * отдавала её в костёр.
     *
     * <p>Но «непочатая» и «всё готово, осталось нажать» — совершенно разные
     * положения, и бот обязан их различать. Поэтому у карт-происшествий близость
     * означает ГОТОВНОСТЬ СРЕДСТВ: есть ли то, чем требование закрывается
     * (здание нужного рода, деньги, войско в нужном месте).
     *
     * <p>ПОТОЛОК В ПОЛОВИНУ ОБЯЗАТЕЛЕН. Без него «я могу это сделать» встало бы
     * в один ряд с «я сделал две трети» у карт со счётным прогрессом, и порядок
     * предпочтений поехал бы: бот держал бы в руке готовое-к-рывку вместо
     * почти-выполненного. Половина оставляет всю верхнюю половину шкалы тем, у
     * кого прогресс настоящий.
     *
     * @param доляСредств какая часть нужных средств собрана (0..1)
     */
    protected static double готовность(double доляСредств) {
        return 0.5 * Math.max(0.0, Math.min(1.0, доляСредств));
    }

    /** Готовность по признаку «средства есть / средств нет». */
    protected static double готовность(boolean средстваЕсть) {
        return средстваЕсть ? 0.5 : 0.0;
    }

    /**
     * ЕСТЬ ЛИ В ЗОНЕ СТРОЙКИ ГЕКС, ГОДНЫЙ ПОД ТРЕБОВАНИЕ КАРТЫ. Нужно картам,
     * чей рывок — стройка в особом месте («рядом с чужим войском», «на гексе с
     * чужим войском»): без такого гекса рывок невозможен в принципе, и близость
     * у карты честный ноль, а с ним — половина.
     *
     * <p>Зона стройки берётся у движка ({@link kelium.engine.Actions#buildableHexes}),
     * а не выводится здесь заново: правило роста зоны от своих стенок сложное и
     * обязано жить в одном месте.
     *
     * @param годен проверка гекса на требование карты
     */
    protected static boolean естьГексПодСтройку(CardContext ctx,
                                                java.util.function.Predicate<String> годен) {
        for (String hid : kelium.engine.Actions.buildableHexes(ctx.state(), ctx.seat())) {
            if (годен.test(hid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * СКОЛЬКО ЗАПИТАННЫХ ЗДАНИЙ ГОТОВЫ РАБОТАТЬ В СБОРКЕ. Нужно картам, чей
     * рывок делается Сборкой: без запитанного здания нужного рода такой рывок
     * невозможен, и близость у карты честный ноль.
     *
     * <p>Список родов берётся из {@link kelium.engine.Actions#ASSEMBLY_UNIT} —
     * ЕДИНОГО источника правды движка, а не переписывается здесь заново: стоит
     * добавить в игру здание, умеющее Сборку, и карта учтёт его сама.
     */
    protected static int готовыхКСборке(PlayerState p) {
        int n = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            if (kelium.engine.Actions.ASSEMBLY_UNIT.containsKey(b.type) && b.powered()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Сжигание карты ради одноразового эффекта. Каждая карта объявляет свой верх
     * своим кодом — записи {@code effect} в каталоге у переехавших карт нет.
     */
    @Override
    public boolean burn(CardContext ctx) {
        return false;
    }

    @Override
    public final String suggestedAction(CardContext ctx) {
        return действие();
    }

    /**
     * Действие, которым обычно закрывается условие ЭТОЙ карты — короткое имя
     * действия ({@code build}/{@code assembly}/{@code mining}/{@code combat}/
     * {@code movement}/{@code energy_swap}/{@code science}/{@code market}), не
     * зависящее от состояния партии. {@code null} по умолчанию: карта не
     * привязана к одному действию, пока подкласс не скажет иначе.
     */
    protected String действие() {
        return null;
    }

    // ==================================================================
    //  ВЕРХ КАРТЫ — короткие имена для того, что печатается на картах
    // ==================================================================

    protected static boolean свободноеДвижение(CardContext ctx) {
        ctx.freeAction("movement");
        return true;
    }

    protected static boolean свободныйБой(CardContext ctx) {
        ctx.freeAction("combat");
        return true;
    }

    protected static boolean свободнаяНаука(CardContext ctx) {
        ctx.freeAction("science");
        return true;
    }

    protected static boolean свободныйМаркет(CardContext ctx) {
        ctx.freeAction("market");
        return true;
    }

    protected static boolean свободнаяДобыча(CardContext ctx) {
        ctx.freeAction("mining");
        return true;
    }

    /** Сборка с пределом: без предела верх был сильнее выполненного задания. */
    protected static boolean свободнаяСборка(CardContext ctx, int зданий) {
        ctx.freeAction("assembly", Map.of("buildings", зданий));
        return true;
    }

    /** Стройка с пределом по числу строительных операций. */
    protected static boolean свободнаяСтройка(CardContext ctx, int операций) {
        ctx.freeAction("build", Map.of("ops", операций));
        return true;
    }
}
