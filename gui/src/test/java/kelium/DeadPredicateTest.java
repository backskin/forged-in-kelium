package kelium;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.LayoutLibrary;
import kelium.engine.Predicates;
import kelium.engine.Setup;

/**
 * НИ ОДНА КАРТА В КОЛОДЕ НЕ ССЫЛАЕТСЯ НА НЕСУЩЕСТВУЮЩИЙ ПРЕДИКАТ.
 *
 * <p>ПОЧЕМУ ЭТО НЕ ЛОВИЛОСЬ. Отсев на подготовке
 * ({@code Setup.reportUnimplemented}) выбрасывает карты с нереализованным
 * ЭФФЕКТОМ — тем, что срабатывает при сжигании верха. А требование карты живёт в
 * другом месте: это идентификатор предиката, и если такого предиката в движке нет,
 * {@code PredicateObjective.satisfied} просто возвращает «нет» — навсегда.
 *
 * <p>Так три военные карты из двадцати — o41 «Ответный удар», o42 «Разорение» и
 * o43 «Охота на сильного» — ЛЕЖАЛИ В КОЛОДЕ и не могли быть выполнены никогда.
 * Игрок их тянул, читал условие, шёл его выполнять и не получал ничего; сжечь ради
 * верха было единственным применением. Ни один замер этого не показывал: в
 * отчётах они выглядели просто редко выполняемыми.
 *
 * <p>Проверка идёт по ДАННЫМ действующего свода, а не по списку в тесте: новая
 * карта с опечаткой в имени предиката попадёт под сторожа сама.
 */
class DeadPredicateTest {

    /** Наборы, у карт которых требование задаётся предикатом. */
    private static final String[] НАБОРЫ = {"objectives", "super_objectives"};

    @Test
    @SuppressWarnings("unchecked")
    void ниОднаКартаНеСсылаетсяНаНесуществующийПредикат() {
        GameConfig cfg = LayoutLibrary.configFor(4, 7L);
        List<String> мёртвые = new ArrayList<>();
        for (String набор : НАБОРЫ) {
            var set = cfg.content.get(набор);
            if (set == null) {
                continue;
            }
            for (Object o : set.entries) {
                Map<String, Object> c = (Map<String, Object>) o;
                for (String ветка : new String[]{"requirement", "enhanced"}) {
                    if (!(c.get(ветка) instanceof Map<?, ?> m) || m.get("predicate") == null) {
                        continue;
                    }
                    String pid = String.valueOf(m.get("predicate"));
                    if (!Predicates.isRegistered(pid)) {
                        мёртвые.add(набор + "/" + c.get("id") + " «" + c.get("name")
                            + "» · " + ветка + " · предиката «" + pid + "» в движке нет");
                    }
                }
            }
        }
        assertTrue(мёртвые.isEmpty(), "карты с несуществующим требованием: " + мёртвые);
    }

    /**
     * И ТО ЖЕ САМОЕ ДЛЯ КАРТ, РЕАЛЬНО ПОПАВШИХ В КОЛОДУ. Первая проверка смотрит
     * весь каталог, эта — то, что легло на стол: если карта отсеялась по другой
     * причине, её мёртвое требование уже никому не вредит, а вот обратное
     * («в каталоге всё хорошо, а в колоду попало плохое») пропускать нельзя.
     */
    @Test
    @SuppressWarnings("unchecked")
    void вКолодеНетКартСМёртвымТребованием() {
        GameConfig cfg = LayoutLibrary.configFor(4, 11L);
        GameState s = Setup.buildGame(cfg);
        Set<String> вИгре = new LinkedHashSet<>(s.decks.get("objectives").drawPile);
        for (var p : s.players) {
            вИгре.addAll(p.objectiveHand);
        }
        List<String> мёртвые = new ArrayList<>();
        for (Object o : cfg.content.get("objectives").entries) {
            Map<String, Object> c = (Map<String, Object>) o;
            if (!вИгре.contains(String.valueOf(c.get("id")))) {
                continue;
            }
            for (String ветка : new String[]{"requirement", "enhanced"}) {
                if (c.get(ветка) instanceof Map<?, ?> m && m.get("predicate") != null
                        && !Predicates.isRegistered(String.valueOf(m.get("predicate")))) {
                    мёртвые.add(c.get("id") + "/" + ветка + "/" + m.get("predicate"));
                }
            }
        }
        assertTrue(мёртвые.isEmpty(), "в колоде лежат невыполнимые карты: " + мёртвые);
    }

    /**
     * УСИЛЕННОЕ ТРЕБОВАНИЕ НЕ СОВПАДАЕТ С ОБЫЧНЫМ СЛОВО В СЛОВО.
     *
     * <p>Совпадение всегда означает одно из двух, и оба плохи: либо словарь не
     * читает параметр, которым усиление и отличается (так было у двадцати пяти
     * карт из сорока), либо усиление в данных и правда пустое — и тогда карта даёт
     * усиленную награду за обычное требование.
     */
    @Test
    @SuppressWarnings("unchecked")
    void усилениеНеПовторяетТребование() {
        GameConfig cfg = LayoutLibrary.configFor(4, 7L);
        List<String> совпали = new ArrayList<>();
        for (Object o : cfg.content.get("objectives").entries) {
            Map<String, Object> c = (Map<String, Object>) o;
            String треб = фраза(c.get("requirement"));
            String усил = фраза(c.get("enhanced"));
            if (!усил.isBlank() && усил.equals(треб)) {
                совпали.add(c.get("id") + " «" + c.get("name") + "»: " + треб);
            }
        }
        assertTrue(совпали.isEmpty(),
            "усиленное требование читается так же, как обычное: " + совпали);
    }

    @SuppressWarnings("unchecked")
    private static String фраза(Object ветка) {
        if (!(ветка instanceof Map<?, ?> m)) {
            return "";
        }
        Object pid = m.get("predicate");
        Map<String, Object> п = m.get("params") instanceof Map<?, ?> pm
            ? (Map<String, Object>) pm : Map.of();
        return kelium.gui.replay2.CardWords.условие(
            pid == null ? null : String.valueOf(pid), п);
    }
}
