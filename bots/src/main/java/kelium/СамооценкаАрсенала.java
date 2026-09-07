package kelium;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.engine.cards.ArsenalCard;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.EngineCardContext;

/**
 * ЧТО КАРТА АРСЕНАЛА ДУМАЕТ О СЕБЕ — установка против утиля, по каждой карте.
 *
 * <p>ЗАЧЕМ. Замер «карты в деле» показывает, что арсенал жгут в 3.4 раза чаще,
 * чем ставят, и это не меняется от пересборки колоды. Решение «поставить или
 * сжечь» бот принимает, сравнивая две САМООЦЕНКИ карты, и пока не видно, какая
 * из них перекошена, любая правка будет угадыванием. Здесь обе печатаются рядом.
 *
 * <p>Сцена — настоящая партия, доведённая до названного раунда: самооценка
 * зависит от позиции (насколько жмёт узкое место, сколько раундов осталось), и
 * считать её на пустом столе бессмысленно.
 *
 * <p>Запуск: {@code kelium.СамооценкаАрсенала [свод] [раунд] [партий]}
 */
public final class СамооценкаАрсенала {

    private СамооценкаАрсенала() {
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        String свод = args.length > 0 ? args[0] : "1.33.0";
        int раунд = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        int партий = args.length > 2 ? Integer.parseInt(args[2]) : 12;
        LayoutLibrary.setRulesetOverride(свод);

        var набор = GameConfig.buildCached(свод, 4, 1L, null, null).content.get("arsenal");
        java.util.Map<String, double[]> итог = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> имена = new java.util.LinkedHashMap<>();

        for (int i = 0; i < партий; i++) {
            GameConfig cfg = LayoutLibrary.configFor(4, 900L + i);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < 4; seat++) {
                agents.add(Bots.create("builder", seat, new java.util.Random(seat), 4));
            }
            GameEngine engine = new GameEngine(s, agents, null);
            engine.runToRound(раунд);
            if (s.finished) {
                continue;
            }
            EngineCardContext ctx = new EngineCardContext(s, 0);
            for (var запись : набор.entries) {
                String id = String.valueOf(запись.get("id"));
                var card = CardRegistry.find(id);
                if (!(card instanceof ArsenalCard ac)) {
                    continue;
                }
                имена.put(id, String.valueOf(запись.get("name")));
                double[] acc = итог.computeIfAbsent(id, k -> new double[3]);
                acc[0] += ac.usefulness(ctx, true);
                acc[1] += ac.usefulness(ctx, false);
                acc[2] += 1;
            }
        }

        System.out.printf(Locale.ROOT, "свод %s, раунд %d, партий %d%n", свод, раунд, партий);
        System.out.println();
        System.out.println("| карта | установка | утиль | что выберет бот |");
        System.out.println("|---|---:|---:|---|");
        int ставит = 0;
        int жжёт = 0;
        for (var e : итог.entrySet()) {
            double[] a = e.getValue();
            if (a[2] == 0) {
                continue;
            }
            double уст = a[0] / a[2];
            double ут = a[1] / a[2];
            String выбор;
            if (уст > ут + 0.05) {
                выбор = "ПОСТАВИТ";
                ставит++;
            } else if (ут > уст + 0.05) {
                выбор = "сожжёт";
                жжёт++;
            } else {
                выбор = "поровну";
            }
            System.out.printf(Locale.ROOT, "| %-26s | %.2f | %.2f | %s |%n",
                имена.getOrDefault(e.getKey(), e.getKey()), уст, ут, выбор);
        }
        System.out.println();
        System.out.printf(Locale.ROOT,
            "карт, которые бот скорее ПОСТАВИТ: %d; скорее сожжёт: %d; поровну: %d%n",
            ставит, жжёт, итог.size() - ставит - жжёт);
    }
}
