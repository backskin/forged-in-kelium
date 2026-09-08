package kelium.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.cards.objectives.ObjectivePack;
import kelium.cards.objectives.ЗаданиеВКоде;
import kelium.cards.objectives.Щедрость;
import kelium.engine.cards.Card;

/**
 * СТОРОЖ ЩЕДРОСТИ: жирная награда обязана попасть и в запись каталога, и в
 * печатный текст карты.
 *
 * <p>ЗАЧЕМ. Награда задания напечатана на карте СЛОВАМИ («— награда 3
 * боеприпаса»), а версия каталога вправе издать её жирнее. Стоит поднять числа
 * молча — и карта обещает одно, а выдаёт другое; за столом это спор, а в замере
 * — молчаливая ложь. Ровно тот сорт расхождения, из-за которого карты и
 * переехали в код.
 *
 * <p>Проверяется на КАЖДОЙ карте кода при нескольких множителях, включая
 * дробный: округление тоже обязано быть одинаковым в записи и в тексте.
 */
class ЩедростьЗаданийTest {

    /**
     * ЧЕГО ИЗ НАГРАДЫ НЕТ В ПЕЧАТНОМ ТЕКСТЕ — по каждому виду добра отдельно.
     *
     * <p>Проверять целой формулировкой нельзя: текст карты писали руками, и
     * порядок слов там свой («жетон модуля атаки и 3 трофея» вместо «3 трофея и
     * жетон модуля атаки»). Требовать канонического порядка значило бы
     * переписывать авторскую прозу ради удобства сторожа. Поэтому сверяются
     * ЧИСЛА: каждое количество обязано стоять в тексте — в своём месте и в своём
     * падеже. Единицу карты часто не пишут («карта задания»), это допускается.
     */
    private static List<String> чегоНетВТексте(String id, double k, String чем,
                                               String текст, Награда н) {
        List<String> out = new ArrayList<>();
        for (var пара : java.util.List.of(
                Map.entry(Награда.монеты(н.монеты()), н.монеты()),
                Map.entry(Награда.боеприпасы(н.боеприпасы()), н.боеприпасы()),
                Map.entry(Награда.трофеи(н.трофеи()), н.трофеи()),
                Map.entry(Награда.нет().иКелемий(н.келемий()), н.келемий()),
                Map.entry(Награда.картыЗаданий(н.картыЗаданий()), н.картыЗаданий()))) {
            int n = пара.getValue();
            if (n <= 0) {
                continue;
            }
            String штука = пара.getKey().печатно();
            String безЕдиницы = штука.startsWith("1 ") ? штука.substring(2) : штука;
            if (!текст.contains(штука) && !(n == 1 && текст.contains(безЕдиницы))) {
                out.add(id + " (×" + k + "): в тексте нет «" + штука + "» из " + чем
                    + ": " + текст);
            }
        }
        return out;
    }

    private static List<ЗаданиеВКоде> задания() {
        List<ЗаданиеВКоде> out = new ArrayList<>();
        for (Card c : new ObjectivePack().cards()) {
            if (c instanceof ЗаданиеВКоде з) {
                out.add(з);
            }
        }
        return out;
    }

    @Test
    void текстСледуетЗаНаградойПриЛюбомМножителе() {
        try {
            for (double k : new double[]{1.0, 1.5, 2.0, 2.5}) {
                System.setProperty("kelium.objectives.scale", String.valueOf(k));
                // Трофей выключен явно: этот сторож про МНОЖИТЕЛЬ, и таблица
                // версий не должна подмешивать сюда вторую правку.
                System.setProperty("kelium.objectives.trophy", "0");
                List<String> жалобы = new ArrayList<>();
                for (ЗаданиеВКоде з : задания()) {
                    var л = з.лицо();
                    if (л.вид() == kelium.cards.objectives.Лицо.Вид.НАЧАЛЬНАЯ) {
                        // Начальные не масштабируются вовсе: их награда фиксирована.
                        assertEquals(л.награда().выгрузить(), з.наградаВВерсии().выгрузить(),
                            з.id() + ": начальное задание не масштабируется");
                        continue;
                    }
                    Map<String, Object> запись = з.data();
                    String текст = String.valueOf(запись.get("описание"));
                    Награда база = з.наградаВВерсии();
                    if (!база.пусто()) {
                        assertEquals(база.выгрузить(), запись.get("base_reward"),
                            з.id() + ": запись каталога расходится с наградой версии");
                        жалобы.addAll(чегоНетВТексте(з.id(), k, "наградой", текст, база));
                    }
                    Награда сверх = з.сверхВВерсии();
                    if (!сверх.пусто()) {
                        assertEquals(сверх.выгрузить(), запись.get("special_reward"),
                            з.id() + ": запись каталога расходится с добавкой версии");
                        жалобы.addAll(чегоНетВТексте(з.id(), k, "добавкой", текст, сверх));
                    }
                }
                assertTrue(жалобы.isEmpty(),
                    "печатный текст разошёлся с наградой:\n" + String.join("\n", жалобы));
            }
        } finally {
            System.clearProperty("kelium.objectives.scale");
            System.clearProperty("kelium.objectives.trophy");
        }
    }

    /**
     * ТРОФЕЙ В БАЗОВОЙ НАГРАДЕ тоже обязан попасть и в запись, и в текст — и не
     * добавить третьего вида добра сверх правила дизайнера.
     */
    @Test
    void трофейВБазеПопадаетВЗаписьИТекст() {
        try {
            System.setProperty("kelium.objectives.scale", "1");
            System.setProperty("kelium.objectives.trophy", "1");
            List<String> жалобы = new ArrayList<>();
            for (ЗаданиеВКоде з : задания()) {
                var л = з.лицо();
                if (л.вид() == kelium.cards.objectives.Лицо.Вид.НАЧАЛЬНАЯ || л.награда().пусто()) {
                    continue;
                }
                Награда база = з.наградаВВерсии();
                if (база.трофеи() < 1) {
                    жалобы.add(з.id() + ": в базовой награде нет трофея");
                }
                if (база.выгрузить().size() > 2) {
                    жалобы.add(з.id() + ": в базовой награде больше двух видов добра — "
                        + база.печатно());
                }
                if (!String.valueOf(з.data().get("описание")).contains(база.печатно())) {
                    жалобы.add(з.id() + ": в тексте нет награды «" + база.печатно() + "»");
                }
            }
            assertTrue(жалобы.isEmpty(), "трофей в базе разошёлся с картой:\n"
                + String.join("\n", жалобы));
        } finally {
            System.clearProperty("kelium.objectives.scale");
            System.clearProperty("kelium.objectives.trophy");
        }
    }

    /** Множитель единица без трофея = каталог ведёт себя так, будто щедрости нет. */
    @Test
    void единицаНеМеняетНичего() {
        try {
            System.setProperty("kelium.objectives.scale", "1");
            System.setProperty("kelium.objectives.trophy", "0");
            for (ЗаданиеВКоде з : задания()) {
                assertEquals(з.лицо().описание(), з.describe(),
                    з.id() + ": при множителе 1 текст обязан остаться авторским");
                assertEquals(з.лицо().награда().выгрузить(), з.наградаВВерсии().выгрузить(),
                    з.id() + ": при множителе 1 награда обязана остаться авторской");
            }
        } finally {
            System.clearProperty("kelium.objectives.scale");
            System.clearProperty("kelium.objectives.trophy");
        }
    }

    /** Награда не исчезает при малом множителе и не теряет предметы. */
    @Test
    void предметыНеУмножаются() {
        Награда н = Награда.боеприпасы(3).иКартыАрсенала(1).иМодуль("attack");
        Награда ж = Щедрость.жирнее(н, 2.0);
        assertEquals(6, ж.боеприпасы(), "счётное добро умножается");
        assertEquals(1, ж.картыАрсенала(), "карта арсенала — предмет, а не число");
        assertEquals("attack", ж.модуль(), "жетон модуля — предмет, а не число");
        assertEquals(1, Щедрость.жирнее(Награда.монеты(1), 0.4).монеты(),
            "напечатанное добро не может обратиться в ноль");
    }
}
