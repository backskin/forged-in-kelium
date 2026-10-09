package kelium;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kelium.agents.Bots;
import kelium.agents.сеть.Сеть;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * НЕ МЕНЯЕТ ЛИ ОЦЕНКА СЕТЬЮ САМУ ПАРТИЮ (30.09.2026). Стратег с ничтожной
 * долей сети обязан играть ровно как прежний гроссмейстер: сеть почти не
 * влияет на выбор, а побочных действий у оценки быть не должно. Если партии
 * расходятся — подсчёт признаков что-то меняет на копии стола.
 *
 * <p>Запуск: {@code kelium.ПроверкаСтратега <value.bin> [партий]}.
 */
public final class ПроверкаСтратега {

    private ПроверкаСтратега() {
    }

    public static void main(String[] args) throws Exception {
        // 1. Подсчёт признаков не должен трогать стол и генератор случайностей.
        GameState s = Setup.buildGame(LayoutLibrary.configFor(4, 7_100_000L));
        List<Agent> быстрые = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            быстрые.add(Bots.create(Bots.ROSTER_4.get(i), Bots.Level.НОВИЧОК, i, new Random(i), 4));
        }
        GameEngine движок = new GameEngine(s, быстрые, null);
        движок.withRoundLimit(4);
        движок.resume();
        List<String> до = ПроверкаПовтора.отпечаток(s);
        byte[] rngДо = байты(s.rng);
        for (int k = 0; k < 4; k++) {
            kelium.agents.сеть.КодировщикСтратега.закодировать(s, k);
        }
        List<String> после = ПроверкаПовтора.отпечаток(s);
        byte[] rngПосле = байты(s.rng);
        System.out.println("признаки: стол " + (до.equals(после) ? "не тронут" : "ИЗМЕНЁН")
            + ", генератор " + (java.util.Arrays.equals(rngДо, rngПосле) ? "не тронут" : "ИЗРАСХОДОВАН"));
        for (int i = 0; i < до.size() && i < после.size(); i++) {
            if (!до.get(i).equals(после.get(i))) {
                System.out.println("  было: " + до.get(i) + "\n  стало: " + после.get(i));
            }
        }
        Сеть сеть = Сеть.загрузить(Path.of(args[0]));
        int партий = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        for (int g = 0; g < партий; g++) {
            long seed = 7_100_000L + g;
            int[] a = сыграть(seed, null, 0);
            int[] a2 = сыграть(seed, null, 0);
            int[] b = сыграть(seed, сеть, 1e-9);
            System.out.println("раздача " + seed + ": формула дважды " + java.util.Arrays.toString(a)
                + " / " + java.util.Arrays.toString(a2)
                + (java.util.Arrays.equals(a, a2) ? " (повторяется)" : " (НЕ ПОВТОРЯЕТСЯ)"));
            System.out.println("раздача " + seed + ": формула " + java.util.Arrays.toString(a)
                + ", стратег с долей 1e-9 " + java.util.Arrays.toString(b)
                + (java.util.Arrays.equals(a, b) ? "  — совпало" : "  — РАЗОШЛОСЬ"));
        }
    }

    private static byte[] байты(Random r) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream o = new java.io.ObjectOutputStream(bo)) {
            o.writeObject(r);
        }
        return bo.toByteArray();
    }

    private static int[] сыграть(long seed, Сеть сеть, double доля) {
        GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Random r = new Random(seed * 31 + i);
            agents.add(i == 0 && сеть != null
                ? ЦиклСтратега.стратег(Bots.ROSTER_4.get(i), i, r, сеть, доля)
                : Bots.create(Bots.ROSTER_4.get(i), Bots.Level.ГРОССМЕЙСТЕР, i, r, 4));
        }
        GameEngine.playGame(s, agents, null);
        int[] очки = new int[4];
        for (int i = 0; i < 4; i++) {
            очки[i] = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
        }
        return очки;
    }
}
