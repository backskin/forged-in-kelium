package kelium;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.engine.Effects;
import kelium.engine.Passives;
import kelium.engine.Predicates;

/**
 * ЦЕЛОСТНОСТЬ НОВЕЙШЕГО КОНТЕНТА — то, что {@code DataIntegrityTest} НЕ видит.
 *
 * <p>{@code DataIntegrityTest.everyPredicateNamedInDataExists} и
 * {@code everyEffectNamedInDataIsImplementedOrKnownToBeCulled} проверяют только
 * наборы, на которые ссылается {@code content_versions} хотя бы одного файла
 * ruleset. Арсенал 2.0.0 и задания 1.6.0 (диктовка дизайнера 12–13.08.2026) не
 * назначены НИ ОДНИМ сводом — их проверки молчали, и 21 из 24 карт арсенала
 * оказались изъяты из колоды (пассивки не реализованы), а заметили это только
 * ручным аудитом ({@code kelium.AuditNewestCards}) 14.08.2026, спустя сутки.
 *
 * <p>Пороги на пассивки НЕ нулевые: четыре карты арсенала 2.0.0 сознательно
 * отложены ({@code kelium_ignores_block}, {@code storage_holds_trophy_cubes},
 * {@code card_is_energy_source_upkeep}, {@code build_on_adjacent_without_wall})
 * — им нужно состояние НА КОНКРЕТНОЙ карте или вмешательство в конвейер
 * стройки/энергии, которых текущий реестр способностей не даёт. Если список
 * вырастет — тест упадёт, значит кто-то тихо добавил карту без реализации.
 */
class NewestContentIntegrityTest {

    /**
     * Осталась ОДНА нереализованная пассивка — {@code storage_holds_trophy_cubes}
     * («Трофейный склад»). Раньше это был [КОНФЛИКТ] правил: карта обещает
     * «держать трофейные кубики, но не больше трёх», а трофейные очки
     * ({@code resources.trophy}) вообще не имели предела и переживали Возврат
     * без потерь. С 2026-08-15 (переименование «трофейное очко» → «трофей» +
     * трофеи теперь ВСЕГДА ограничены складом наравне с келемием/боеприпасом,
     * {@code resources.trophy}/{@code Storage.trophyMax}) конфликт формально
     * снят — карта уже не была бы ухудшением. Но её точную механику (доп. пул на
     * 3 трофея сверх общего бюджета? Что-то ещё?) дизайнер ещё не подтвердил —
     * реализовывать самовольно нельзя, порог оставлен как есть.
     */
    private static final int MAX_KNOWN_MISSING_PASSIVES = 1;

    @SuppressWarnings("unchecked")
    private static List<String> collect(Object node, String key) {
        List<String> out = new java.util.ArrayList<>();
        collectInto(node, key, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collectInto(Object node, String key, List<String> out) {
        if (node instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                if (key.equals(e.getKey()) && e.getValue() instanceof String s) {
                    out.add(s);
                }
                collectInto(e.getValue(), key, out);
            }
        } else if (node instanceof List<?> l) {
            for (Object o : l) {
                collectInto(o, key, out);
            }
        }
    }

    private static GameConfig newestContent() {
        GameConfig.pickContentVersion("arsenal", "2.0.0");
        GameConfig.pickContentVersion("objectives", "1.6.0");
        try {
            // buildCached, НЕ build(): GameConfig.build() не читает CONTENT_PICK
            // вовсе (грабля, на которой сам обжёгся 14.08.2026 — первый прогон
            // аудита молча проверял старые версии 1.3.0/1.5.0 и не находил ничего).
            return GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 1L, null, null);
        } finally {
            GameConfig.pickContentVersion("arsenal", null);
            GameConfig.pickContentVersion("objectives", null);
        }
    }

    /** Задания-рисунки (1.6.0): все предикаты, названные в данных, реализованы. */
    @Test
    void objectives160HaveNoMissingPredicates() {
        GameConfig cfg = newestContent();
        ContentSet objectives = cfg.content.sets.get("objectives");
        TreeSet<String> missing = new TreeSet<>();
        for (Map<String, Object> card : objectives.entries) {
            for (String pid : collect(card, "predicate")) {
                if (!Predicates.isRegistered(pid)) {
                    missing.add(pid + " <- " + card.get("id"));
                }
            }
        }
        assertTrue(missing.isEmpty(), "задания 1.6.0 ссылаются на неизвестные предикаты: " + missing);
    }

    /** Арсенал 2.0.0: утиль-эффекты (верх карты) реализованы все, без исключений. */
    @Test
    void arsenal200HasNoMissingTopEffects() {
        GameConfig cfg = newestContent();
        ContentSet arsenal = cfg.content.sets.get("arsenal");
        TreeSet<String> missing = new TreeSet<>();
        for (Map<String, Object> card : arsenal.entries) {
            if (card.get("top") instanceof Map<?, ?> t && t.get("effect") != null
                    && !Effects.isImplemented(String.valueOf(t.get("effect")))) {
                missing.add(t.get("effect") + " <- " + card.get("id"));
            }
        }
        assertTrue(missing.isEmpty(), "арсенал 2.0.0 ссылается на нереализованные утиль-эффекты: "
            + missing);
    }

    /**
     * Арсенал 2.0.0: пассивки (низ карты) — не более четырёх известных отложенных.
     * Список деклар и рационал — см. javadoc класса.
     */
    @Test
    void arsenal200HasAtMostFourKnownMissingPassives() {
        GameConfig cfg = newestContent();
        ContentSet arsenal = cfg.content.sets.get("arsenal");
        TreeSet<String> missing = new TreeSet<>();
        for (Map<String, Object> card : arsenal.entries) {
            if (card.get("bottom") instanceof Map<?, ?> b && b.get("passive") != null
                    && !Passives.isImplemented(String.valueOf(b.get("passive")))) {
                missing.add(b.get("passive") + " <- " + card.get("id"));
            }
        }
        assertTrue(missing.size() <= MAX_KNOWN_MISSING_PASSIVES,
            "арсенал 2.0.0: список нереализованных пассивок вырос сверх известных "
            + MAX_KNOWN_MISSING_PASSIVES + ": " + missing);
    }
}
