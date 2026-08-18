package kelium.cards.arsenal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ВСЁ, ЧТО НА КАРТЕ АРСЕНАЛА НАПЕЧАТАНО, ОБЪЯВЛЕННОЕ В КОДЕ.
 *
 * <p>Тот же принцип, что у {@code kelium.cards.objectives.Лицо}: источник
 * правды — класс карты, а запись каталога выгружается из него, а не читается
 * из YAML. Карта арсенала устроена не так, как задание: её низ — либо
 * ПОСТОЯННАЯ способность, либо СПЕЦ, либо условие на очки в конце партии, и
 * исполняет это не сама карта, а точка правил ({@code kelium.engine.ability}) —
 * карта только НАЗЫВАЕТ, какую способность включает установка. Это законная
 * связь между кодом карты и кодом способности (обе стороны — код, не строка в
 * данных без реализации), а не тот разрыв, что чинился у заданий.
 *
 * @param имя         печатное название
 * @param вид         обычная карта колоды или стартовая (по одной каждому)
 * @param контейнер   на открытой карте нарисована ячейка под контейнер
 * @param верхЭффект  идентификатор эффекта из реестра {@code Effects} — та же
 *                    услуга движка, что уже разбирает награды и одноразовые
 *                    эффекты; проверено, что все используемые здесь id
 *                    зарегистрированы (ни одного мёртвого)
 * @param верхПараметры параметры эффекта
 * @param верх        печатная метка утиля
 * @param низВид      {@code POST} (постоянная способность), {@code SPEC} или
 *                    {@code SCORING} (очки в конце партии)
 * @param пассивка    id способности в реестре {@code Abilities}; {@code null}
 *                    у карт-целей (SCORING) — там условие в {@code scoring}
 * @param scoring     запись условия очков для карт-целей; {@code null} у
 *                    остальных
 * @param низ         печатная метка низа
 * @param описание    литературный текст карты целиком
 */
public record ЛицоАрсенала(String имя, Вид вид, boolean контейнер,
                           String верхЭффект, Map<String, Object> верхПараметры, String верх,
                           НизВид низВид, String пассивка, Map<String, Object> scoring, String низ,
                           String описание) {

    public enum Вид { ОБЫЧНАЯ, СТАРТОВАЯ }

    public enum НизВид { POST, SPEC, SCORING }

    /** Выгрузка в запись каталога — ключи те же, что читает движок. */
    public Map<String, Object> выгрузить(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", имя);
        out.put("kind", вид == Вид.СТАРТОВАЯ ? "starting" : "regular");
        if (контейнер) {
            out.put("container_slot", true);
        }
        Map<String, Object> top = new LinkedHashMap<>();
        top.put("effect", верхЭффект);
        top.put("params", верхПараметры == null ? Map.of() : верхПараметры);
        top.put("label", верх);
        out.put("top", top);

        Map<String, Object> bottom = new LinkedHashMap<>();
        bottom.put("kind", низВид.name());
        if (пассивка != null) {
            bottom.put("passive", пассивка);
        }
        if (scoring != null) {
            bottom.put("scoring", scoring);
        }
        bottom.put("label", низ);
        out.put("bottom", bottom);

        out.put("описание", описание);
        return out;
    }
}
