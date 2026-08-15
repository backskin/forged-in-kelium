package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.agents.Phrasebook;
import kelium.dataio.GameConfig;
import kelium.gui.GameRecorder;
import kelium.report.ReplayRecord;


/**
 * Словарь реплик ботов: он обязан быть на месте, покрывать все ситуации,
 * которые строит {@code StrategicAgent}, давать на каждую несколько разных
 * вариантов — и реально звучать по-разному в живой партии.
 */
class PhrasebookTest {

    /** Ситуации, ключи которых строит код агента. */
    private static final List<String> ORDERS =
        List.of("разработка", "инфраструктура", "операция", "приобретения", "безопасность");
    private static final List<String> ACTIONS =
        List.of("сборка", "добыча", "стройка", "энергия", "движение", "бой", "рынок", "наука");
    private static final List<String> BUILDINGS =
        List.of("добытчик", "энергостанция", "казарма", "завод", "авиабаза", "цу");
    private static final List<String> UNITS =
        List.of("пехота", "техника", "авиация", "вышка");
    private static final List<String> GRADES = List.of("сильно", "норма", "слабо");
    private static final List<String> MOVE_GRADES =
        List.of("удар", "сближение", "давление", "просто");
    private static final List<String> TARGETS =
        List.of("цу", "здание", "техника", "пехота", "авиация", "вышка", "нейтрал", "цель");
    private static final List<String> SPECS = List.of("задание", "сжечь_задание",
        "сжечь_арсенал", "установить_арсенал", "супер", "развернуть_супер", "контейнеры");

    private static List<String> everyKeyTheCodeCanAskFor() {
        List<String> keys = new ArrayList<>();
        for (String o : ORDERS) {
            for (String g : GRADES) {
                keys.add("приказ." + o + "." + g);
            }
        }
        for (String a : ACTIONS) {
            for (String g : GRADES) {
                keys.add("действие." + a + "." + g);
            }
        }
        for (String b : BUILDINGS) {
            for (String g : GRADES) {
                keys.add("стройка." + b + "." + g);
            }
        }
        for (String u : UNITS) {
            for (String g : MOVE_GRADES) {
                keys.add("движение." + u + "." + g);
            }
        }
        for (String t : TARGETS) {
            keys.add("бой." + t + ".убью");
            keys.add("бой." + t + ".пораню");
        }
        for (String s : SPECS) {
            keys.add("спец." + s);
        }
        for (String u : UNITS) {
            keys.add("манёвр." + u);
        }
        keys.add("пас.действие");
        keys.add("пас.бой");
        keys.add("пас.стройка");
        return keys;
    }

    @Test
    void theDictionaryIsFoundAndLoaded() {
        assertNotNull(Phrasebook.source(),
            "файл словаря реплик не найден в data/phrases");
        assertTrue(Phrasebook.all().size() >= 90,
            "словарь подозрительно маленький: " + Phrasebook.all().size() + " ситуаций");
    }

    @Test
    void everySituationTheAgentUsesHasItsOwnPhrases() {
        List<String> missing = new ArrayList<>();
        for (String key : everyKeyTheCodeCanAskFor()) {
            if (!Phrasebook.hasExact(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(),
            "в словаре нет реплик на ситуации: " + missing);
    }

    @Test
    void everySituationOffersAtLeastFourDifferentLines() {
        List<String> thin = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : Phrasebook.all().entrySet()) {
            Set<String> unique = new HashSet<>(e.getValue());
            if (unique.size() < 4) {
                thin.add(e.getKey() + " (" + unique.size() + ")");
            }
        }
        assertTrue(thin.isEmpty(), "меньше четырёх РАЗНЫХ реплик: " + thin);
    }

    @Test
    void placeholdersAreAlwaysFilledIn() {
        Random rng = new Random(1);
        for (String key : Phrasebook.all().keySet()) {
            for (int i = 0; i < 8; i++) {
                String phrase = Phrasebook.pick(key, rng,
                    "гекс", "h1_2", "род", "пехоту", "n", "3", "келемий", "2",
                    "цель", "техника", "здание", "завод");
                assertNotNull(phrase, key);
                assertFalse(phrase.contains("{"),
                    "в реплике осталась незаполненная подстановка: " + phrase);
            }
        }
    }

    @Test
    void aRealGameSoundsVariedRatherThanRepetitive() {
        ReplayRecord rec = GameRecorder.play(GameConfig.DEFAULT_RULESET, 4, 4242,
            List.of("strat:hawk", "strat:dove", "explorer", "strat:opportunist"), null);
        Set<String> unique = new HashSet<>();
        int total = 0;
        for (ReplayRecord.Frame f : rec.frames) {
            for (ReplayRecord.Thought t : f.thoughts) {
                unique.add(t.text);
                total++;
            }
        }
        assertTrue(total > 50, "мыслей за партию слишком мало: " + total);
        assertTrue(unique.size() >= 40,
            "боты повторяются: разных реплик всего " + unique.size() + " из " + total);
        // Печать латиницей: кириллица в консоли Windows (cp1251) ломается.
        System.out.println("[phrases] said=" + total + " unique=" + unique.size()
            + " dictionary=" + Phrasebook.all().size());
    }
}
