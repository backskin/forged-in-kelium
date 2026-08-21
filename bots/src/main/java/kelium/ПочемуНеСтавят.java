package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.cards.ArsenalCard;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.EngineCardContext;

/**
 * ПОЧЕМУ КАРТУ АРСЕНАЛА НЕ СТАВЯТ — по мнению самой карты.
 *
 * <p>Замер покрытия говорит, ЧТО карту не ставят, но не говорит, ПОЧЕМУ. А причин
 * всего три, и они лечатся по-разному:
 * <ul>
 *   <li>карта считает свою установку дешевле утиля — тогда виноват утиль
 *       (сильный верх перебивает низ) либо слабое самоописание способности;</li>
 *   <li>условие способности не выполняется почти никогда ({@code Hint.needs}) —
 *       тогда карта просит того, чего в партии не бывает;</li>
 *   <li>карта до игрока не доходит — это уже к колоде, а не к оценке.</li>
 * </ul>
 *
 * <p>Стенд играет партии и в СЛУЧАЙНЫЕ моменты спрашивает каждую карту, чего она
 * стоит В ЭТОМ положении — установка против утиля. Так видно, у каких карт
 * установка не побеждает НИКОГДА, и это и есть список «задачек»: либо чинить
 * самоописание, либо ослаблять верх, либо менять условие.
 *
 * <p>Запуск: {@code kelium.ПочемуНеСтавят [игроков] [партий] [замеров за партию]}.
 */
public final class ПочемуНеСтавят {

    private ПочемуНеСтавят() {
    }

    private record Счёт(String id, String имя, double установка, double утиль,
                        int замеров, int разУстановкаЛучше, boolean условиеБываетВерным) {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        int замеровЗаПартию = args.length > 2 ? Integer.parseInt(args[2]) : 6;

        Map<String, double[]> сумма = new LinkedHashMap<>();   // id -> [устан, утиль, n, лучше, needsОК]
        Map<String, String> имена = new LinkedHashMap<>();

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, players,
                31000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create("builder:2", i,
                    new Random(i * 31L + g), players));
            }
            Random когда = new Random(777L + g);
            int[] шагов = {0};
            GameEngine.playGame(s, ags, ev -> {
                shots(s, cfg, сумма, имена, когда, шагов, замеровЗаПартию);
            });
        }

        List<Счёт> строки = new ArrayList<>();
        for (Map.Entry<String, double[]> e : сумма.entrySet()) {
            double[] v = e.getValue();
            int n = (int) v[2];
            if (n == 0) {
                continue;
            }
            строки.add(new Счёт(e.getKey(), имена.getOrDefault(e.getKey(), "?"),
                v[0] / n, v[1] / n, n, (int) v[3], v[4] > 0));
        }
        строки.sort((x, y) -> Double.compare(
            x.установка() - x.утиль(), y.установка() - y.утиль()));

        StringBuilder b = new StringBuilder();
        b.append("# Почему карту арсенала не ставят — по мнению самой карты\n\n");
        b.append("Свод **").append(GameConfig.DEFAULT_RULESET).append("**, партий ")
            .append(games).append(", замеров на партию ").append(замеровЗаПартию)
            .append(". В случайные моменты партии у КАЖДОЙ карты спрашивается, ")
            .append("чего она стоит в этом положении: установка против утиля.\n\n");
        b.append("| карта | имя | установка | утиль | установка лучше | условие ")
            .append("способности бывает верным |\n|---|---|---:|---:|---:|---|\n");
        for (Счёт c : строки) {
            b.append("| `").append(c.id()).append("` | ").append(c.имя())
                .append(" | ").append(String.format("%.2f", c.установка()))
                .append(" | ").append(String.format("%.2f", c.утиль()))
                .append(" | ").append(String.format("%d%%",
                    Math.round(100.0 * c.разУстановкаЛучше() / c.замеров())))
                .append(" | ").append(c.условиеБываетВерным() ? "да" : "**НЕТ НИ РАЗУ**")
                .append(" |\n");
        }
        b.append("\n## Как читать\n\n");
        b.append("Обе оценки — от 0 до 1, это ответ самой карты на вопрос «чего я ")
            .append("стою прямо сейчас».\n\n");
        b.append("* «установка лучше» близко к нулю — карту не поставят никогда: ")
            .append("либо верх слишком силён, либо способность себя недооценивает.\n");
        b.append("* «условие бывает верным: НЕТ НИ РАЗУ» — способность просит ")
            .append("того, чего в партии не случается, и её оценка всегда 0.05. ")
            .append("Это самая дешёвая для починки причина.\n");

        Path out = Path.of("reports", "balance", "почему-не-ставят-арсенал.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println(b);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    /** Спросить все карты в случайный момент партии. */
    private static void shots(GameState s, GameConfig cfg, Map<String, double[]> сумма,
                              Map<String, String> имена, Random когда, int[] шагов,
                              int замеровЗаПартию) {
        шагов[0]++;
        // Замеры РЕДКИЕ и в случайные моменты: спрашивать каждую карту на каждом
        // событии — это десятки тысяч опросов на партию, и все они об одном и том
        // же положении.
        if (шагов[0] % 40 != 0 || когда.nextInt(3) != 0) {
            return;
        }
        for (Map<String, Object> entry : cfg.content.get("arsenal").entries) {
            String id = String.valueOf(entry.get("id"));
            ArsenalCard card = CardRegistry.arsenal(id);
            if (card == null) {
                continue;
            }
            имена.putIfAbsent(id, String.valueOf(entry.get("name")));
            double уст;
            double утиль;
            try {
                var ctx = new EngineCardContext(s, 0);
                уст = card.usefulness(ctx, true);
                утиль = card.usefulness(ctx, false);
            } catch (RuntimeException e) {
                continue;
            }
            double[] v = сумма.computeIfAbsent(id, k -> new double[5]);
            v[0] += уст;
            v[1] += утиль;
            v[2] += 1;
            if (уст > утиль) {
                v[3] += 1;
            }
            // 0.05 — ровно то, что карта отвечает, когда условие способности
            // неисполнимо (см. ArsenalCardBase.installValue). Значение выше
            // означает, что условие хотя бы раз было верным.
            if (уст > 0.06) {
                v[4] = 1;
            }
        }
    }
}
