package kelium.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import kelium.bench.CardBench;
import kelium.core.PlayerState;

/**
 * НАЧАЛЬНЫЙ АРСЕНАЛ 7.2.0 — СТАРТОВЫЙ НАБОР ВМЕСТО УТИЛЯ (решение дизайнера
 * 26.09.2026): игрок получает напечатанное сверху сразу при установке, а
 * сжечь такую карту нельзя — разрыва на печати нет.
 */
class Арсенал_7_2_СтартовыеНаборы {

    @Test
    void наборВыдаётсяПриУстановке() {
        var b = CardBench.партия(4)
            .арсеналВРуку(0, "bs72_3")
            .монеты(0, 0)
            .игратьДо(1, 0, CardBench.установить("bs72_3"));
        PlayerState p = b.состояние().player(0);
        assertTrue(p.arsenalInstalled.contains("bs72_3"), "карта не установлена. " + b.сводка());
        assertTrue(b.было("starter_kit", "card", "bs72_3"),
            "набор не выдан при установке. " + b.сводка());
    }

    @Test
    void двеКартыЗаданийПриходятВРуку() {
        var b = CardBench.партия(4)
            .очиститьРуки()
            .арсеналВРуку(0, "bs72_4")
            .игратьДо(1, 0, CardBench.установить("bs72_4"));
        assertTrue(b.было("starter_kit", "card", "bs72_4"), b.сводка());
    }

    @Test
    void стартовуюКартуНельзяСжечь() {
        var b = CardBench.партия(4)
            .арсеналВРуку(0, "bs72_1")
            .монеты(0, 6)
            .игратьДо(1, 0);
        assertTrue(b.предлагалось("spec", "install bs72_1"),
            "стартовую карту не предложили установить. " + b.сводка());
        assertFalse(b.предлагалось("spec", "burn bs72_1"),
            "стартовую карту предложили сжечь");
    }

    @Test
    void всеСемьКартВНаборе() {
        var b = CardBench.партия(4);
        int n = 0;
        for (Object e : b.настройка().content.get("arsenal").entries) {
            if (e instanceof java.util.Map<?, ?> m && String.valueOf(m.get("id")).startsWith("bs72_")) {
                n++;
            }
        }
        assertEquals(7, n);
    }
}
