package kelium.cards.containers;

import java.util.Map;

/**
 * КОНТЕЙНЕРЫ 3.0 — хорошие (c21-c28): 4 с выбором, 4 без; редкие (c29-c32):
 * 2 с выбором, 2 без.
 *
 * <p>c26 и c28 — СПОРНЫЕ КАРТЫ (backlog E12/E13/E14). НАЙДЕНО ПРИ ПЕРЕЕЗДЕ
 * 18.08.2026: переключатели {@code contested_cards.energy_without_source_enabled}
 * и {@code contested_cards.effect_survives_round_enabled} существовали в
 * своде годами (по умолчанию {@code false}), но ни разу не были сверены с
 * этими двумя картами — обе играли ВСЕГДА. Починка — в {@code Setup}, здесь
 * карта только объявляет, к какому переключателю она привязана.
 */
public final class КонтейнерыХорошиеИРедкие {

    private КонтейнерыХорошиеИРедкие() {
    }

    private static Map<String, Object> эфф(String effect, Map<String, Object> params, String label) {
        return Map.of("effect", effect, "params", params, "label", label);
    }

    public static final class Сейф extends КонтейнерВКоде {
        public Сейф() {
            super("c21");
        }

        @Override
        public ЛицоКонтейнера лицо() {
            return new ЛицоКонтейнера("Сейф", "good", null,
                эфф("gain", Map.of("coin", 3), "3 монеты"),
                эфф("gain", Map.of("debris", 2), "2 обломка"),
                "Взломанный сейф с содержимым на любой вкус. Заберите 3 монеты "
                + "или 2 обломка.");
        }
    }

    public static final class СкладБоеприпасов extends КонтейнерВКоде {
        public СкладБоеприпасов() {
            super("c22");
        }

        @Override
        public ЛицоКонтейнера лицо() {
            return new ЛицоКонтейнера("Склад боеприпасов", "good", null,
                эфф("gain", Map.of("ammo", 3), "3 боеприпаса"), null,
                "Целый склад снаряжения, оставленный при отходе. Заберите 3 "
                + "боеприпаса.");
        }
    }

    public static final class Чертежи extends КонтейнерВКоде {
        public Чертежи() {
            super("c23");
        }

        @Override
        public ЛицоКонтейнера лицо() {
            return new ЛицоКонтейнера("Чертежи", "good", null,
                эфф("gain", Map.of("objective_cards", 2), "2 карты задания"),
                эфф("gain", Map.of("debris", 2), "2 обломка"),
                "Инженерные чертежи, за которые дорого платят и штаб, и "
                + "коллекционеры. Обменяйте их на 2 карты задания или на 2 "
                + "обломка.");
        }
    }

    public static final class ОружейныйСклад extends КонтейнерВКоде {
        public ОружейныйСклад() {
            super("c24");
        }

        @Override
        public ЛицоКонтейнера лицо() {
            return new ЛицоКонтейнера("Оружейный склад", "good", null,
                эфф("gain", Map.of("ammo", 2, "coin", 1), "2 боеприпаса и 1 монета"),
                null,
                "Оружейка с остатками довольствия. Заберите 2 боеприпаса и 1 "
                + "монету.");
        }
    }

    public static final class ТрофейныйТягач extends КонтейнерВКоде {
        public ТрофейныйТягач() {
            super("c25");
        }

        @Override
        public ЛицоКонтейнера лицо() {
            return new ЛицоКонтейнера("Трофейный тягач", "good", null,
                эфф("free_action", Map.of("action", "movement"), "одиночное движение"),
                эфф("gain", Map.of("containers", 1), "1 контейнер"),
                "Захваченный тягач тащит либо ваши войска, либо трофеи прежнего "
                + "владельца. Проведите Движение из одного своего гекса "
                + "бесплатно или возьмите ещё 1 контейнер.");
        }
    }

    /** c26 «Резервный генератор» — спорная карта, привязана к energy_without_source. */
    public static final class РезервныйГенератор extends КонтейнерВКоде {
        public РезервныйГенератор() {
            super("c26");
        }

        @Override
        public ЛицоКонтейнера лицо() {
            return new ЛицоКонтейнера("Резервный генератор", "good", "energy_without_source",
                эфф("permanent_energy", Map.of(), "кубик энергии навсегда в ячейку здания"),
                null,
                "Аварийный генератор питает здание без всякой энергостанции. "
                + "Поставьте кубик энергии навсегда в ячейку одного своего "
                + "здания: Смена энергии его не снимет, при сносе он не "
                + "вернётся.");
        }
    }

    public static final class ПолеваяМастерская extends КонтейнерВКоде {
        public ПолеваяМастерская() {
            super("c27");
        }

        @Override
        public ЛицоКонтейнера лицо() {
            return new ЛицоКонтейнера("Полевая мастерская", "good", null,
                эфф("free_action", Map.of("action", "assembly"), "малое снаряжение: одно здание"),
                эфф("gain", Map.of("ammo", 2), "2 боеприпаса"),
                "Передвижная мастерская, работающая без чертежей. Проведите "
                + "Снаряжение одним своим зданием бесплатно или заберите 2 "
                + "боеприпаса.");
        }
    }

    /** c28 «Шифровка» — спорная карта, привязана к effect_survives_round. */
    public static final class Шифровка extends КонтейнерВКоде {
        public Шифровка() {
            super("c28");
        }

        @Override
        public ЛицоКонтейнера лицо() {
            return new ЛицоКонтейнера("Шифровка", "good", "effect_survives_round",
                эфф("grab_first_player", Map.of(), "жетон первого игрока на следующий раунд"),
                null,
                "Перехваченная шифровка отдаёт вам инициативу. Возьмите жетон "
                + "первого игрока на следующий раунд.");
        }
    }

    public static final class ОружейныйКонтейнер extends КонтейнерВКоде {
        public ОружейныйКонтейнер() {
            super("c29");
        }

        @Override
        public ЛицоКонтейнера лицо() {
            return new ЛицоКонтейнера("Оружейный контейнер", "rare", null,
                эфф("gain", Map.of("ammo", 3, "coin", 1), "3 боеприпаса и 1 монета"),
                эфф("gain", Map.of("debris", 2, "ammo", 1), "2 обломка и 1 боеприпас"),
                "Армейская укладка, набитая под завязку. Заберите 3 боеприпаса "
                + "и 1 монету или 2 обломка и 1 боеприпас.");
        }
    }

    public static final class ЗаводскойКонтейнер extends КонтейнерВКоде {
        public ЗаводскойКонтейнер() {
            super("c30");
        }

        @Override
        public ЛицоКонтейнера лицо() {
            return new ЛицоКонтейнера("Заводской контейнер", "rare", null,
                эфф("gain", Map.of("coin", 3, "objective_cards", 1),
                    "3 монеты и 1 карта задания"),
                null,
                "Заводская укладка с чистой прибылью и папкой заказов. "
                + "Заберите 3 монеты и 1 карту задания.");
        }
    }

    public static final class ИнженерныйЯщик extends КонтейнерВКоде {
        public ИнженерныйЯщик() {
            super("c31");
        }

        @Override
        public ЛицоКонтейнера лицо() {
            return new ЛицоКонтейнера("Инженерный ящик", "rare", null,
                эфф("gain", Map.of("debris", 3), "3 обломка"),
                эфф("gain", Map.of("objective_cards", 2), "2 карты задания"),
                "Герметичный бокс с редкими деталями. Заберите 3 обломка или "
                + "2 карты задания.");
        }
    }

    public static final class ЧёрныйЯщик extends КонтейнерВКоде {
        public ЧёрныйЯщик() {
            super("c32");
        }

        @Override
        public ЛицоКонтейнера лицо() {
            return new ЛицоКонтейнера("Чёрный ящик", "rare", null,
                эфф("gain", Map.of("debris", 2, "coin", 2), "2 обломка и 2 монеты"),
                null,
                "Загадочный чёрный ящик выплачивает трофейную премию тому, кто "
                + "до него добрался. Заберите 2 обломка и 2 монеты.");
        }
    }
}
