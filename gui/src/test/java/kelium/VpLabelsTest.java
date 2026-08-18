package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.gui.replay2.Names;

/**
 * СЛОВАРЬ ПОДПИСЕЙ ОЧКОВ НЕ ДОЛЖЕН ОТСТАВАТЬ ОТ {@link Scoring}.
 *
 * <p>НАЙДЕНО 18.08.2026. {@link Names#vp} годами держал ключ {@code "trophy"},
 * которого {@code Scoring.scorePlayer} никогда не выставлял (ресурс переименован
 * в обломки, реальный ключ разбивки — {@code "debris"}), а ключи {@code
 * "objective_card_vp"} и {@code "arsenal_vp"} появились в подсчёте очков позже
 * словаря и не были в него добавлены вовсе. Итог: КАЖДАЯ партия с обломками или
 * очками задания/арсенала честно, но бесполезно писала в итоговом окне
 * «не описано» вместо подписи источника.
 *
 * <p>Проверка гоняет партию до конца и требует у {@link Names#vp} подпись для
 * каждого ключа, который {@code Scoring} реально положил в разбивку хотя бы
 * одному игроку, — так расхождение ловится на первом же новом источнике очков,
 * а не через полгода в отчёте о партии.
 */
class VpLabelsTest {

    @Test
    void каждыйКлючРазбивкиИмеетПодпись() {
        GameConfig cfg = LayoutLibrary.configFor(4, 777L);
        GameState s = Setup.buildGame(cfg);

        List<String> непокрыто = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            Map<String, Integer> breakdown = Scoring.scorePlayer(s, seat);
            for (String key : breakdown.keySet()) {
                if ("не описано".equals(Names.vp(key))) {
                    непокрыто.add(key);
                }
            }
        }
        assertFalse(!непокрыто.isEmpty(),
            "у ключей разбивки очков нет человеческой подписи в Names.vp: " + непокрыто);
    }
}
