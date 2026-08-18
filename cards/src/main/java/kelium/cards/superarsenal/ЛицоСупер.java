package kelium.cards.superarsenal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ВСЁ, ЧТО НА КАРТЕ СУПЕР-АРСЕНАЛА НАПЕЧАТАНО, ОБЪЯВЛЕННОЕ В КОДЕ.
 *
 * <p>Две формы в одном каталоге: {@code troop} (супер-войско — есть род и
 * прибавка к прочности) и {@code power} (супер-способность — набор полей
 * свой у каждой карты: passive, vp_flat, card_slots). Записаны сюда только
 * непустые поля — движок ({@code kelium.engine.ability.Abilities},
 * {@code Scoring}) читает их по имени, отсутствующее поле для него означает
 * «не применимо», как и раньше в YAML.
 */
public record ЛицоСупер(String имя, String вид, String родВойска, Integer прибавкаПрочности,
                        Integer очкиЗаКарту, String пассивка, String метка,
                        Integer фиксированныеОчки, Boolean ячейкиПодКарты,
                        String текстКарты, String описание) {

    /** Супер-войско: род, прибавка к прочности, способность, ПО за карту. */
    public static ЛицоСупер войско(String имя, String род, int прибавка, String пассивка,
                                   String метка, String текстКарты, String описание) {
        return new ЛицоСупер(имя, "troop", род, прибавка, 1, пассивка, метка,
            null, null, текстКарты, описание);
    }

    /** Супер-способность с постоянным правилом (passive). */
    public static ЛицоСупер способность(String имя, String пассивка, String метка,
                                        String текстКарты, String описание) {
        return new ЛицоСупер(имя, "power", null, null, null, пассивка, метка,
            null, null, текстКарты, описание);
    }

    /** «Мандат совета» — фиксированные очки и ячейки под карты, без пассивки. */
    public static ЛицоСупер мандат(String имя, int очки, String метка,
                                   String текстКарты, String описание) {
        return new ЛицоСупер(имя, "power", null, null, null, null, метка,
            очки, true, текстКарты, описание);
    }

    public Map<String, Object> выгрузить(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", имя);
        out.put("kind", вид);
        if (родВойска != null) {
            out.put("unit", родВойска);
        }
        if (прибавкаПрочности != null) {
            out.put("hp_bonus", прибавкаПрочности);
        }
        if (очкиЗаКарту != null) {
            out.put("vp_on_card", очкиЗаКарту);
        }
        if (пассивка != null) {
            out.put("passive", пассивка);
        }
        if (фиксированныеОчки != null) {
            out.put("vp_flat", фиксированныеОчки);
        }
        if (Boolean.TRUE.equals(ячейкиПодКарты)) {
            out.put("card_slots", true);
        }
        out.put("label", метка);
        out.put("текст_карты", текстКарты);
        out.put("описание", описание);
        return out;
    }
}
