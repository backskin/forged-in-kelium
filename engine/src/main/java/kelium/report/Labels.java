package kelium.report;

import kelium.core.BuildingType;
import kelium.core.UnitType;

/**
 * Labels — русские подписи и короткие коды зданий/войск, ОДИН источник для
 * поля, планшета, подсказок и SVG/Java2D-рендера. Живёт в {@code engine}, а не
 * в {@code gui}: {@link FieldPainter} (рендер жетонов для отчётов и SVG) тоже
 * нуждается в этих подписях, а движок не имеет права зависеть от GUI —
 * поэтому текст не может лежать в {@code kelium.gui.GameRecorder}, хотя
 * исторически там и появился первым. {@code GameRecorder} делегирует сюда же.
 */
public final class Labels {

    private Labels() {
    }

    /** Короткий код здания (тот же, что на жетоне в SVG). */
    public static String buildingCode(String typeCode) {
        return switch (BuildingType.fromCode(typeCode)) {
            // ТРИ БУКВЫ у военных зданий (просьба дизайнера 13.08.2026): «Зв» и
            // «Ав» на жетоне читались как обрубки, а места хватает.
            case COMMAND_CENTER -> "ЦУ";
            case FACTORY -> "Звд";
            case AIRBASE -> "Авб";
            case BARRACKS -> "Каз";
            // ДОБЫТЧИК И ЭНЕРГОСТАНЦИЯ — ДВУМЯ БУКВАМИ И ЧЕРЕЗ ДЕФИС перед номером
            // («Эн-3», «Дб-1»). Односимвольные «Э» и «З» в выбранном шрифте почти
            // неотличимы, а рядом ещё и цифра уровня — читалось наугад (замечание
            // дизайнера 13.08.2026).
            case MINER -> "Дб";
            case POWER_PLANT -> "Эн";
        };
    }

    /**
     * Код здания ВМЕСТЕ С УРОВНЕМ, как он подписывается на жетоне: «Эн-3», «Дб-1»,
     * «ЦУ». Один метод на поле, планшет и подсказки — иначе подписи разойдутся.
     */
    public static String buildingLabel(String typeCode, Integer level) {
        String code = buildingCode(typeCode);
        return level == null ? code : code + "-" + level;
    }

    /** Полное русское название здания (для подсказок и зон игроков). */
    public static String buildingName(String typeCode) {
        return switch (BuildingType.fromCode(typeCode)) {
            case COMMAND_CENTER -> "центр управления";
            case FACTORY -> "завод";
            case AIRBASE -> "авиабаза";
            case BARRACKS -> "казарма";
            case MINER -> "добытчик";
            case POWER_PLANT -> "энергостанция";
        };
    }

    /**
     * ПОЛНОЕ НАЗВАНИЕ ЗДАНИЯ С УРОВНЕМ: «энергостанция, уровень 3». У добытчика и
     * энергостанции уровень — часть имени, а не приписка сбоку (уточнение
     * дизайнера 13.08.2026); у остальных зданий уровня нет.
     */
    public static String buildingName(String typeCode, Integer level) {
        String name = buildingName(typeCode);
        return level == null ? name : name + ", уровень " + level;
    }

    /** Полное русское название рода войск. */
    public static String unitName(String typeCode) {
        return switch (UnitType.fromCode(typeCode)) {
            case INFANTRY -> "пехота";
            case VEHICLE -> "техника";
            case AIRCRAFT -> "авиация";
            case TOWER -> "вышка";
        };
    }
}
