package kelium;

import java.util.Map;
import java.util.TreeSet;

import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.engine.Effects;
import kelium.engine.Passives;
import kelium.engine.Predicates;

/**
 * AuditNewestCards — ОДНОРАЗОВЫЙ РАЗВЕДЧИК: чего не хватает движку, чтобы играть
 * НАДИКТОВАННЫЙ арсенал (2.0.0) и задания-рисунки (1.6.0) целиком.
 *
 * <p>Повод. {@code DataIntegrityTest} проверяет предикаты/эффекты ТОЛЬКО у тех
 * наборов, на которые ссылается content_versions хотя бы одного файла ruleset —
 * а 2.0.0 и 1.6.0 сегодня не назначены НИ ОДНИМ сводом, поэтому тест их не видит
 * вовсе. Отсюда и вышло, что колода 2.0.0 «подключена» (лежит файлом), но не
 * реализована — никто не проверял.
 *
 * <p>Запуск: {@code kelium.AuditNewestCards}.
 */
public final class AuditNewestCards {

    private AuditNewestCards() {
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));

        // ВНИМАНИЕ: GameConfig.build() НЕ читает CONTENT_PICK (только buildCached
        // делает это) — на первом прогоне это дало ложный «missing: 0» на СТАРОЙ
        // колоде (22/48 карт вместо 24/54), пока не проверил числа.
        GameConfig.pickContentVersion("arsenal", "2.0.0");
        GameConfig.pickContentVersion("objectives", "1.6.0");
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 1L, null, null);
        GameConfig.pickContentVersion("arsenal", null);
        GameConfig.pickContentVersion("objectives", null);

        TreeSet<String> missingPredicates = new TreeSet<>();
        TreeSet<String> missingEffects = new TreeSet<>();
        TreeSet<String> missingPassives = new TreeSet<>();

        ContentSet objectives = cfg.content.sets.get("objectives");
        for (Map<String, Object> card : objectives.entries) {
            for (String pid : collect(card, "predicate")) {
                if (!Predicates.isRegistered(pid)) {
                    missingPredicates.add(pid + "  <-  objectives/" + card.get("id")
                        + " (" + card.get("name") + ")");
                }
            }
        }

        ContentSet arsenal = cfg.content.sets.get("arsenal");
        for (Map<String, Object> card : arsenal.entries) {
            Object top = card.get("top");
            if (top instanceof Map<?, ?> t) {
                Object eff = t.get("effect");
                if (eff != null && !Effects.isImplemented(String.valueOf(eff))) {
                    missingEffects.add(eff + "  <-  arsenal/" + card.get("id")
                        + " top (" + card.get("name") + ")");
                }
            }
            Object bottom = card.get("bottom");
            if (bottom instanceof Map<?, ?> b) {
                Object pas = b.get("passive");
                if (pas != null && !Passives.isImplemented(String.valueOf(pas))) {
                    missingPassives.add(pas + "  <-  arsenal/" + card.get("id")
                        + " bottom (" + card.get("name") + ")");
                }
            }
        }

        System.out.println("# Чего не хватает: арсенал 2.0.0 + задания 1.6.0");
        System.out.println();
        System.out.println("Карт в арсенале: " + arsenal.entries.size()
            + ", карт в заданиях: " + objectives.entries.size());
        System.out.println();
        System.out.println("## Отсутствующие ПРЕДИКАТЫ заданий (" + missingPredicates.size() + ")");
        missingPredicates.forEach(System.out::println);
        System.out.println();
        System.out.println("## Отсутствующие ЭФФЕКТЫ арсенала — утиль (" + missingEffects.size() + ")");
        missingEffects.forEach(System.out::println);
        System.out.println();
        System.out.println("## Отсутствующие ПАССИВКИ арсенала (" + missingPassives.size() + ")");
        missingPassives.forEach(System.out::println);
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<String> collect(Object node, String key) {
        java.util.List<String> out = new java.util.ArrayList<>();
        collectInto(node, key, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collectInto(Object node, String key, java.util.List<String> out) {
        if (node instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                if (key.equals(e.getKey()) && e.getValue() instanceof String s) {
                    out.add(s);
                }
                collectInto(e.getValue(), key, out);
            }
        } else if (node instanceof java.util.List<?> l) {
            for (Object o : l) {
                collectInto(o, key, out);
            }
        }
    }
}
