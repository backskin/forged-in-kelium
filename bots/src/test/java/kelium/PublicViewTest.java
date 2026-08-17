package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.observe.PublicView;
import kelium.report.ReplayRecord;

/**
 * ПУБЛИЧНЫЙ ВИД СТОЛА — сторож двух обещаний: «ничего открытого не потеряно» и
 * «ничего закрытого не просочилось».
 *
 * <p>Оба обещания легко нарушить молча. Первое — добавив поле в состояние и забыв
 * про вид: бот просто никогда не узнает, что такое поле есть, и никакой тест это
 * не заметит. Второе — скопировав руку соседа «на всякий случай»: бот станет
 * играть сильнее, замеры станут лучше, и понять, что он подглядывает, будет
 * нельзя ничем, кроме чтения кода.
 *
 * <p>Поэтому первая проверка идёт ОТРАЖЕНИЕМ по полям снимка: каждое поле игрока
 * обязано быть либо перенесено в вид, либо названо закрытым в списке ниже. Новое
 * поле, не попавшее ни туда, ни туда, валит тест — и это единственный способ
 * заставить того, кто его добавил, решить, публично оно или нет.
 */
class PublicViewTest {

    /**
     * ЗАКРЫТОЕ ПО ПРАВИЛАМ. Каждая строка — решение правил, а не удобство:
     * рука приказов (4 карты после слепого сброса), рука заданий («карте задания
     * негде лежать открытой»), рука арсенала, отложенный слепым сбросом приказ
     * (лежит рубашкой вверх — что именно, видит только владелец).
     */
    private static final Set<String> ЗАКРЫТО = Set.of(
        "orderHand", "objectiveHand", "arsenalHand", "orderSetAside");

    /**
     * ПЕРЕИМЕНОВАННОЕ В ВИДЕ: поле снимка → поле вида. Имена разошлись там, где
     * снимок называл вещь короче, чем нужно для чужого читателя.
     */
    private static final java.util.Map<String, String> ПЕРЕИМЕНОВАНО = java.util.Map.of(
        "side", "troopSide");

    /**
     * ВЫВЕДЕНО ИЗ ОБИХОДА и потому в вид не переносится: половинки модулей
     * отменены правилом 13.08.2026 (теперь тянется целый жетон), а «мандат»
     * контейнеров — остаток прежнего механизма контейнеров.
     */
    private static final Set<String> НЕ_В_ИГРЕ = Set.of(
        "redHalves", "blueHalves", "mandateArsenalCard", "mandateContainers");

    private static GameState midGame(int players, long seed) {
        GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            agents.add(Bots.create(Bots.PLAYERS.get(i % Bots.PLAYERS.size()), i,
                new Random(seed * 31 + i), players));
        }
        new GameEngine(s, agents, ev -> { }).runToRound(5);
        return s;
    }

    /**
     * НИ ОДНО ПОЛЕ СНИМКА НЕ ПОТЕРЯНО. Либо оно есть в виде (своим именем или
     * переименованным, либо счётчиком {@code ...Count}), либо оно названо закрытым
     * или выведенным из обихода.
     */
    @Test
    void видНеТеряетНиОдногоПоляСостояния() {
        Set<String> вВиде = new LinkedHashSet<>();
        for (Field f : PublicView.Seat.class.getFields()) {
            вВиде.add(f.getName());
        }
        List<String> потеряно = new ArrayList<>();
        for (Field f : ReplayRecord.Player.class.getFields()) {
            String имя = f.getName();
            if (ЗАКРЫТО.contains(имя) || НЕ_В_ИГРЕ.contains(имя)) {
                continue;       // решение принято осознанно
            }
            String ждём = ПЕРЕИМЕНОВАНО.getOrDefault(имя, имя);
            if (вВиде.contains(ждём) || вВиде.contains(ждём + "Count")) {
                continue;
            }
            потеряно.add(имя);
        }
        assertTrue(потеряно.isEmpty(),
            "поля состояния не попали в публичный вид и не названы закрытыми: "
            + потеряно + " — реши по каждому, публично оно или нет");
    }

    /** У ЗАКРЫТОГО ЕСТЬ ЧИСЛО. Иначе «публично только количество» — пустые слова. */
    @Test
    void укаждогоЗакрытогоСпискаЕстьСчётчик() {
        Set<String> вВиде = new LinkedHashSet<>();
        for (Field f : PublicView.Seat.class.getFields()) {
            вВиде.add(f.getName());
        }
        for (String скрытое : ЗАКРЫТО) {
            if ("orderSetAside".equals(скрытое)) {
                assertTrue(вВиде.contains("hasSetAsideOrder"),
                    "факт отложенного приказа публичен — нужен признак");
                continue;
            }
            assertTrue(вВиде.contains(скрытое + "Count"),
                "у закрытого списка " + скрытое + " нет публичного числа");
        }
    }

    /**
     * СОСЕД НЕ ПОКАЗЫВАЕТ РУКУ, А ЧИСЛО ПОКАЗЫВАЕТ. Это главная проверка: именно
     * здесь бот может начать подглядывать, и заметить это иначе нечем.
     */
    @Test
    void чужиеРукиЗакрыты() {
        GameState s = midGame(4, 90210L);
        boolean виделиКарты = false;
        for (int seat = 0; seat < 4; seat++) {
            PublicView v = PublicView.of(s, seat);
            assertNotNull(v.me, "свой планшет обязан быть");
            assertEquals(seat, v.me.seat, "свой планшет — это мой планшет");
            assertEquals(3, v.others.size(), "на четверых соседей трое");
            // своя рука видна
            assertNotNull(v.me.orderHand, "свою руку приказов игрок видит");
            assertNotNull(v.me.objectiveHand, "свою руку заданий игрок видит");
            assertNotNull(v.me.arsenalHand, "свою руку арсенала игрок видит");
            for (PublicView.Seat o : v.others) {
                assertNull(o.orderHand, "рука приказов соседа закрыта");
                assertNull(o.objectiveHand, "рука заданий соседа закрыта");
                assertNull(o.arsenalHand, "рука арсенала соседа закрыта");
                assertNull(o.setAsideOrder, "отложенный приказ соседа закрыт");
                assertTrue(o.orderHandCount >= 0 && o.objectiveHandCount >= 0
                    && o.arsenalHandCount >= 0, "числа карт соседа публичны");
                if (o.objectiveHandCount > 0 || o.arsenalHandCount > 0) {
                    виделиКарты = true;
                }
            }
        }
        assertTrue(виделиКарты,
            "за партию у соседей ни разу не было карт — проверка ничего не проверила");
    }

    /**
     * СОСЕДИ В ПОРЯДКЕ ХОДА ОТ МЕНЯ. Номер места в игре не значит ничего, значение
     * имеет только очерёдность; иначе тот, кто читает вид, выучит номера вместо
     * расстановки.
     */
    @Test
    void соседиИдутПоКругуОтМеня() {
        GameState s = midGame(4, 90211L);
        for (int seat = 0; seat < 4; seat++) {
            PublicView v = PublicView.of(s, seat);
            assertEquals(0, v.me.order, "я — нулевой по очереди от себя");
            for (int i = 0; i < v.others.size(); i++) {
                PublicView.Seat o = v.others.get(i);
                assertEquals(i + 1, o.order, "порядок по кругу подряд");
                assertEquals((seat + i + 1) % 4, o.seat,
                    "следующий по кругу от места " + seat);
            }
        }
    }

    /**
     * ПОЛЕ ОТДАНО ЦЕЛИКОМ — с геометрией, а не счётчиками. Ради этого вид и
     * заводился: из «сколько целей в досягаемости» нельзя узнать, ГДЕ они.
     */
    @Test
    void полеОтданоЦеликом() {
        GameState s = midGame(4, 90212L);
        PublicView v = PublicView.of(s, 0);
        assertEquals(s.field.hexes.size(), v.hexes.size(), "все гексы поля");
        for (ReplayRecord.HexState h : v.hexes) {
            assertNotNull(h.id, "у гекса есть имя");
            assertEquals(6, h.sideOwner.length, "шесть секторов земли у каждого гекса");
        }
        // ЖЕТОНЫ В ЗАПАСЕ ТОЖЕ ПУБЛИЧНЫ: они лежат перед игроком на столе, и
        // сколько у кого осталось непоставленного — открытая информация. Поэтому
        // считаем ВСЕ жетоны, а координату требуем только у стоящих на поле.
        int всего = 0;
        int наПоле = 0;
        for (var p : s.players) {
            всего += p.buildings.size() + p.units.size();
            наПоле += p.buildingsOnField().size() + p.unitsOnField().size();
        }
        assertEquals(всего, v.tokens.size(), "все жетоны игроков, включая запас");
        int сКоординатой = 0;
        for (ReplayRecord.Tok t : v.tokens) {
            if (t.hexId != null) {
                сКоординатой++;
            }
        }
        assertEquals(наПоле, сКоординатой, "у каждого жетона на поле есть координата");
    }

    /** ВИД ВЫГРУЖАЕТСЯ В JSON — им будет питаться обучение на питоне. */
    @Test
    void видПишетсяВJson() {
        GameState s = midGame(3, 90213L);
        String json = PublicView.of(s, 1).toJson();
        assertTrue(json.length() > 2000, "вид подозрительно короткий: " + json.length());
        assertTrue(json.contains("\"hexes\""), "в выгрузке есть поле");
        assertTrue(json.contains("\"tokens\""), "в выгрузке есть жетоны");
        assertTrue(json.contains("\"others\""), "в выгрузке есть соседи");
        assertTrue(json.contains("\"orderHandCount\""), "в выгрузке есть числа карт");
    }
}
