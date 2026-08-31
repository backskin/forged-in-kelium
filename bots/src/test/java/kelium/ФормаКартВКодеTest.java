package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.cards.objectives.ЗаданиеВКоде;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.engine.cards.Card;
import kelium.engine.cards.CardRegistry;

/**
 * СТОРОЖ ПЕРЕЕЗДА КАРТ В КОД.
 *
 * <p>Проверяет три вещи, каждую из которых уже ломали:
 *
 * <ol>
 *   <li><b>ФОРМА КАРТЫ.</b> Правила дизайнера о наградах и о приписке «В ЭТОТ
 *       ХОД» должны быть ошибкой сборки каталога, а не замечанием в ревью через
 *       полгода. Карта сама на себя жалуется — здесь эти жалобы собираются.</li>
 *   <li><b>КОД НАКРЫВАЕТ ФАЙЛ.</b> Карта, живущая в коде, обязана вытеснить
 *       запись из YAML целиком. Если этого не случилось, движок продолжит
 *       раздавать награду по старым числам из файла, а печататься карта будет по
 *       новым — расхождение, которое и затевались убрать.</li>
 *   <li><b>НИ ОДНОГО ПРЕДИКАТА У ПЕРЕЕХАВШИХ.</b> У карты в коде не может быть
 *       записи {@code predicate}: вторая, декларативная копия условия рядом с
 *       настоящим кодом — это ровно тот разрыв, из-за которого три карты лежали
 *       в колоде мёртвыми.</li>
 * </ol>
 */
class ФормаКартВКодеTest {

    /** Карты заданий, уже переехавшие в код. */
    private List<ЗаданиеВКоде> переехавшие() {
        List<ЗаданиеВКоде> out = new ArrayList<>();
        for (Card c : CardRegistry.all()) {
            if (c instanceof ЗаданиеВКоде з) {
                out.add(з);
            }
        }
        return out;
    }

    /** ФОРМА: карта сама называет, чем нарушает правила дизайнера. */
    @Test
    void формаКартСоблюдена() {
        List<String> жалобы = new ArrayList<>();
        for (ЗаданиеВКоде з : переехавшие()) {
            String ж = з.лицо().жалоба();
            if (ж != null) {
                жалобы.add(з.id() + " «" + з.лицо().имя() + "»: " + ж);
            }
        }
        assertTrue(жалобы.isEmpty(), "карты нарушают правила формы:\n" + String.join("\n", жалобы));
    }

    /**
     * ПЕРЕЕЗД НЕ ПОТЕРЯН: переехавших карт не меньше, чем уже сделано.
     *
     * <p>Порог 63 — весь активный каталог заданий (40 обычных + 12 начальных +
     * o22 из числа обычных уже входит в 40), плюс запас на будущие карты.
     * Скамейка (8 карт сверх сорока) в раздачу не попадает и не считается.
     */
    @Test
    void переездИдёт() {
        assertTrue(переехавшие().size() >= 52,
            "в код переведено меньше карт, чем было: " + переехавшие().size());
    }

    /** КОД НАКРЫВАЕТ ФАЙЛ: запись каталога совпадает с выгрузкой из класса. */
    @Test
    void записьКаталогаВзятаИзКода() {
        var cfg = LayoutLibrary.configFor(4, 1L);
        Setup.buildGame(cfg);
        var набор = cfg.content.get("objectives");
        List<String> расхождения = new ArrayList<>();
        for (ЗаданиеВКоде з : переехавшие()) {
            Map<String, Object> вФайле;
            try {
                вФайле = набор.byId(з.id());
            } catch (RuntimeException e) {
                расхождения.add(з.id() + ": карты нет в наборе каталога");
                continue;
            }
            Map<String, Object> изКода = з.data();
            if (!изКода.equals(вФайле)) {
                расхождения.add(з.id() + ":\n  из кода: " + изКода + "\n  в наборе: " + вФайле);
            }
        }
        assertTrue(расхождения.isEmpty(),
            "запись каталога не накрыта выгрузкой из кода:\n" + String.join("\n", расхождения));
    }

    /** У ПЕРЕЕХАВШЕЙ КАРТЫ НЕТ ПРЕДИКАТОВ — условие живёт только в коде. */
    @Test
    void ниОдногоПредикатаУПереехавших() {
        List<String> плохие = new ArrayList<>();
        for (ЗаданиеВКоде з : переехавшие()) {
            Map<String, Object> d = з.data();
            // ЕДИНСТВЕННОЕ ЗАКОННОЕ ИСКЛЮЧЕНИЕ — доплата за усиленную жертву.
            // Она остаётся декларативным протоколом ДВИЖКА (Objectives.
            // playObjective списывает разницу сам, по этой самой записи), а не
            // условием карты: карта по-прежнему проверяет себя кодом
            // (satisfied/satisfiedEnhanced), просто оплата разницы — общая
            // услуга движка, а не поведение одной карты. См. ЗаданиеВКоде.
            // усиленнаяЖертваВЗаписи().
            boolean жертва = з.лицо().природа() == kelium.cards.objectives.Лицо.Природа.ЖЕРТВА;
            for (String ветка : List.of("requirement", "enhanced")) {
                if (d.get(ветка) instanceof Map<?, ?> m && m.containsKey("predicate")) {
                    boolean законно = жертва && "enhanced".equals(ветка)
                        && "sacrifice_enhanced".equals(m.get("predicate"));
                    if (!законно) {
                        плохие.add(з.id() + " держит предикат в ветке " + ветка);
                    }
                }
            }
            assertEquals("card", d.get("checked_by"),
                з.id() + ": карта в коде обязана быть помечена checked_by: card, "
                + "иначе движок спросит условие у реестра предикатов, а не у неё");
        }
        assertTrue(плохие.isEmpty(), String.join("\n", плохие));
    }

    /** НАЧАЛЬНЫЕ КАРТЫ: у всех одна награда и ни у одной нет усиления. */
    @Test
    void начальныеОдинаковыПоФорме() {
        int начальных = 0;
        for (ЗаданиеВКоде з : переехавшие()) {
            var л = з.лицо();
            if (л.вид() != kelium.cards.objectives.Лицо.Вид.НАЧАЛЬНАЯ) {
                continue;
            }
            начальных++;
            assertNull(л.усиление(), з.id() + ": у начального задания не бывает усиления");
            assertEquals(1, л.награда().трофеи(), з.id() + ": награда начальной — трофей");
            assertEquals(1, л.награда().картыЗаданий(),
                з.id() + ": награда начальной — ещё и карта задания");
            assertFalse(л.верх() == null, з.id() + ": утиль есть у всех начальных");
        }
        assertEquals(12, начальных, "начальных карт в коде должно быть двенадцать");
    }
}
