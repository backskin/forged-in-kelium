package kelium.cards.objectives;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.core.Resource;
import kelium.engine.cards.CardContext;

/**
 * ВЕРХ КАРТЫ ЗАДАНИЯ — то, ради чего карту сжигают вместо выполнения.
 *
 * <p>ПОЧЕМУ ЭТО ОДНО МЕСТО, А НЕ ДВА. Прежде каждая карта объявляла свой верх
 * ДВАЖДЫ: печатной строкой в {@link Лицо} и телом метода {@code burn}, где та
 * же самая строка превращалась в вызов движка. Сорок восемь карт из пятидесяти
 * двух держали такую пару, и разойтись ей было нечему помешать: напечатано
 * «СВОБОДНЫЙ БОЙ», сыграно перемещение — и никто не заметит.
 *
 * <p>ВТОРАЯ, БОЛЕЕ ДОРОГАЯ ПРИЧИНА. Печатная строка нечитаема машиной, и оценка
 * верха ({@link kelium.engine.cards.TopValue}) для заданий возвращала ровно
 * середину: у нуля карт из пятидесяти двух был объявлен эффект. Бот сравнивал
 * живую цену выполнения с плоской цифрой — и жёг задания в 4.8 раза чаще, чем
 * выполнял. Карты арсенала ту же болезнь уже пережили: там объявленный эффект
 * дал сожжений 1.70 -> 1.39 при установках 0.50 -> 0.68.
 *
 * <p>Теперь верх объявлен один раз: метка для печати, эффект с параметрами для
 * оценки и выгрузки, и {@link #сыграть} для движка.
 */
public enum Утиль {

    /** Свободное перемещение — приказ Маневр даром. */
    ДВИЖЕНИЕ("СВОБОДНОЕ ДВИЖЕНИЕ", "movement"),
    /** Свободный бой. */
    БОЙ("СВОБОДНЫЙ БОЙ", "combat"),
    /** Свободный обмен в Научном отделе. */
    НАУКА("СВОБОДНАЯ НАУКА", "science"),
    /** Свободный обмен на Рынке. */
    РЫНОК("СВОБОДНЫЙ РЫНОК", "market"),
    /** Свободная добыча келемия. */
    ДОБЫЧА("СВОБОДНАЯ ДОБЫЧА", "mining"),

    /**
     * Снаряжение ОДНИМ зданием. Предел не украшение: без него верх выходил
     * сильнее выполненного низа, и карту не имело смысла держать вовсе.
     */
    СНАРЯЖЕНИЕ("СНАРЯЖЕНИЕ ОДНИМ ЗДАНИЕМ", "free_action",
        Map.of("action", "assembly", "buildings", 1)) {
        @Override
        public boolean сыграть(CardContext ctx) {
            ctx.freeAction("assembly", Map.of("buildings", 1));
            return true;
        }
    },
    /** Одна строительная операция — тот же предел и по той же причине. */
    СТРОЙКА("ОДНА СТРОИТЕЛЬНАЯ ОПЕРАЦИЯ", "free_action",
        Map.of("action", "build", "ops", 1)) {
        @Override
        public boolean сыграть(CardContext ctx) {
            ctx.freeAction("build", Map.of("ops", 1));
            return true;
        }
    },

    /** Смена энергии или смена модулей — что из двух, выбирает игрок. */
    ЭНЕРГИЯ_ИЛИ_МОДУЛИ("СМЕНА ЭНЕРГИИ ИЛИ СМЕНА МОДУЛЕЙ",
        "energy_or_modules", Map.of()) {
        @Override
        public boolean сыграть(CardContext ctx) {
            return ctx.energyOrModules();
        }
    },

    /** Монета — верх начальных заданий; больше у них нет ничего. */
    МОНЕТА("+1 монета", "gain", Map.of("coin", 1)) {
        @Override
        public boolean сыграть(CardContext ctx) {
            ctx.gain(Resource.COIN, 1);
            return true;
        }
    },
    /** Боеприпас — второй верх начальных заданий. */
    БОЕПРИПАС("+1 боеприпас", "gain", Map.of("ammo", 1)) {
        @Override
        public boolean сыграть(CardContext ctx) {
            ctx.gain(Resource.AMMO, 1);
            return true;
        }
    },

    /** +1 к скорости одного рода войск до конца хода; род выбирает игрок. */
    СКОРОСТЬ("+1 К СКОРОСТИ ОДНОГО РОДА ДО КОНЦА ХОДА", "speed_boost", Map.of()) {
        @Override
        public boolean сыграть(CardContext ctx) {
            return ctx.speedBoost();
        }
    },
    /** Снять весь урон со всех жетонов на одном гексе. */
    РЕМОНТ_ГЕКСА("РЕМОНТ ГЕКСА", "heal_hex", Map.of()) {
        @Override
        public boolean сыграть(CardContext ctx) {
            return ctx.healHex();
        }
    },
    /** Щит на технику и авиацию: снять первое попадание до конца хода. */
    ЩИТ_ТЕХНИКА_АВИАЦИЯ("ЩИТ: техника или авиация", "shield",
        Map.of("kinds", List.of("vehicle", "aircraft"))) {
        @Override
        public boolean сыграть(CardContext ctx) {
            return ctx.shield(List.of("vehicle", "aircraft"));
        }
    },
    /** Щит на пехоту и авиацию. */
    ЩИТ_ПЕХОТА_АВИАЦИЯ("ЩИТ: пехота или авиация", "shield",
        Map.of("kinds", List.of("infantry", "aircraft"))) {
        @Override
        public boolean сыграть(CardContext ctx) {
            return ctx.shield(List.of("infantry", "aircraft"));
        }
    },
    /** Обменять свой келемий на трофей один к одному. */
    КОНВЕРСИЯ("КОНВЕРСИЯ: 1 келемий -> 1 трофей", "convert",
        Map.of("from", "kelium", "to", "trophy", "amount", 1)) {
        @Override
        public boolean сыграть(CardContext ctx) {
            return ctx.convert(Resource.KELIUM, Resource.TROPHY, 1);
        }
    };

    private final String метка;
    private final String эффект;
    private final Map<String, Object> параметры;

    /** Даровое действие: эффект один и тот же, разнится только само действие. */
    Утиль(String метка, String действие) {
        this(метка, "free_action", Map.of("action", действие));
    }

    Утиль(String метка, String эффект, Map<String, Object> параметры) {
        this.метка = метка;
        this.эффект = эффект;
        this.параметры = параметры;
    }

    /** Что напечатано на карте. */
    public String метка() {
        return метка;
    }

    /**
     * ЗАПИСЬ ВЕРХА ДЛЯ КАТАЛОГА — метка, идентификатор эффекта и параметры.
     *
     * <p>Эффект здесь НЕ для исполнения: играет верх метод {@link #сыграть}.
     * Он для того, чтобы верх можно было прочитать, не разбирая русскую строку,
     * — оценкой бота, справочником и сводом.
     */
    public Map<String, Object> запись() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", метка);
        out.put("effect", эффект);
        if (!параметры.isEmpty()) {
            out.put("params", new LinkedHashMap<>(параметры));
        }
        return out;
    }

    /** Сыграть верх. Даровое действие — общий случай, остальные переопределяют. */
    public boolean сыграть(CardContext ctx) {
        ctx.freeAction(String.valueOf(параметры.get("action")));
        return true;
    }

    /** Найти утиль по печатной метке — для чтения старых наборов и таблиц. */
    public static Утиль поМетке(String метка) {
        for (Утиль у : values()) {
            if (у.метка.equals(метка)) {
                return у;
            }
        }
        return null;
    }
}
