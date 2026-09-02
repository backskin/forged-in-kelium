package kelium;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * КАКИМИ ПЕЧАТНЫМИ ОБМЕНАМИ ИГРАЛИ — замер по журналу партий.
 *
 * <p>Заказ дизайнера 02.09.2026 снял с планшета рынка обмены на боеприпасы и на
 * ячейку энергии, а с планшета науки — обмен трофеев на монеты. Правка была
 * сделана данными, и проверить её можно только так: сыграть партии и посмотреть,
 * встречается ли снятый обмен в журнале. Если встречается — значит движок берёт
 * обмены не из свода, а из кода, и правка до игры не дошла.
 *
 * <p>Запуск: {@code kelium.ОбменыПланшетов [свод] [партий] [игроков]}
 */
public final class ОбменыПланшетов {

    private ОбменыПланшетов() {
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        String свод = args.length > 0 ? args[0] : "1.33.0";
        int партий = args.length > 1 ? Integer.parseInt(args[1]) : 40;
        int игроков = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        LayoutLibrary.setRulesetOverride(свод);

        Map<String, Integer> рынок = new LinkedHashMap<>();
        Map<String, Integer> наука = new LinkedHashMap<>();
        for (int i = 0; i < партий; i++) {
            GameConfig cfg = LayoutLibrary.configFor(игроков, 500L + i);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < игроков; seat++) {
                agents.add(Bots.create("builder", seat,
                    new java.util.Random(11L * i + seat), игроков));
            }
            GameEngine.playGame(s, agents, ev -> {
                if (!"action".equals(ev.get("type"))) {
                    return;
                }
                Object t = ev.get("telemetry");
                if (!(t instanceof Map<?, ?> m)) {
                    return;
                }
                // Рынок пишет каждый обмен своим ключом «deal_<что>».
                for (var e : m.entrySet()) {
                    String k = String.valueOf(e.getKey());
                    if (k.startsWith("deal_") && e.getValue() instanceof Number n) {
                        рынок.merge(k.substring("deal_".length()), n.intValue(), Integer::sum);
                    }
                }
                Object ex = m.get("exchange");
                if (ex != null && !String.valueOf(ex).isBlank()) {
                    for (String part : String.valueOf(ex).split("\\+")) {
                        наука.merge(part, 1, Integer::sum);
                    }
                }
            });
        }
        System.out.printf(Locale.ROOT, "свод %s, партий %d, игроков %d%n",
            свод, партий, игроков);
        System.out.println("РЫНОК — печатные обмены:");
        печать(рынок);
        System.out.println("НАУКА — печатные обмены:");
        печать(наука);
    }

    private static void печать(Map<String, Integer> m) {
        if (m.isEmpty()) {
            System.out.println("    ни одного не взяли");
            return;
        }
        m.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> System.out.printf(Locale.ROOT, "    %-22s %d%n",
                e.getKey(), e.getValue()));
    }
}
