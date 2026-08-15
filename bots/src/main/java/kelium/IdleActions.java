package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.LayoutLibrary;

/**
 * IdleActions — КАК ЧАСТО В КАЖДОМ ДЕЙСТВИИ БОТ НЕ ДЕЛАЕТ ВООБЩЕ НИЧЕГО.
 *
 * <p>Просьба дизайнера 13.08.2026. Важная оговорка: «ПАСА» в игре НЕТ — нет такого
 * состояния и такого правила. Игрок разыгрывает приказ и выбирает действие; если он
 * сам решил ничего в этом действии не делать, это его решение, а не отдельный ход
 * «пас». Метрика ловит именно это: действие разыграно, а на столе ничего не
 * изменилось.
 *
 * <p>Зачем такая метрика. Простой в действии — самый честный признак, что действие
 * либо бесполезно, либо недоступно: у бота нет ресурса, нет цели, нет места. Средние
 * по очкам этого не покажут, а здесь видно по каждому из восьми действий отдельно.
 *
 * <p>Как определяется простой: у разыгранного действия в телеметрии НЕТ ни одного
 * положительного числа. Телеметрия у каждого действия своя (бои, войска, келемий,
 * шаги, покупки), поэтому признак общий и не требует знания про каждое действие.
 *
 * <p>Запуск: {@code kelium.IdleActions [игроков] [партий]}.
 */
public final class IdleActions {

    private IdleActions() {
    }

    private static final Map<String, LongAdder> PLAYED = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> IDLE = new ConcurrentHashMap<>();

    private static void bump(Map<String, LongAdder> m, String key) {
        m.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    /** Есть ли в телеметрии хоть одно положительное число — значит что-то произошло. */
    private static boolean didSomething(Object telemetry) {
        if (!(telemetry instanceof Map<?, ?> tel)) {
            return false;
        }
        for (Object v : tel.values()) {
            if (v instanceof Number n && n.doubleValue() > 0) {
                return true;
            }
            if (v instanceof Boolean b && b) {
                // could_fight=true само по себе НЕ действие: это «цели были».
                continue;
            }
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 500;
        // ЧЕМ ИГРАТЬ — одной настройкой запуска на весь стенд:
        // -Dkelium.bots=просчёт. Это нужно, чтобы отделить «действие бесполезно по
        // правилам» от «бот не умеет им пользоваться»: просчёт как раз и
        // вычёркивает холостые ходы. Своего переключателя у стенда нет намеренно —
        // два способа включить одно и то же путают и врут в отчётах.
        out.println("боты: " + Bots.describe());

        for (int g = 0; g < games; g++) {
            long seed = 8_100_000L + g;
            GameState s = Setup.buildGame(
                LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            List<String> lineup = List.of("hawk", "dove", "balanced", "opportunist");
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get(i % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            GameEngine.playGame(s, agents, ev -> {
                if (!"action".equals(String.valueOf(ev.get("type")))) {
                    return;
                }
                String name = String.valueOf(ev.get("action"));
                bump(PLAYED, name);
                if (!didSomething(ev.get("telemetry"))) {
                    bump(IDLE, name);
                }
            });
        }

        StringBuilder md = new StringBuilder();
        md.append("# Простой в действиях — где бот ничего не делает\n\n");
        // Кем игралось — обязательная часть замера: у ботов с просчётом простой
        // вдвое ниже, и без этой строки два отчёта не отличить друг от друга.
        md.append("Игроков: ").append(players).append(", партий: ").append(games)
          .append(", боты: ").append(Bots.describe())
          .append(".\n\n**Паса в игре нет.** Речь не о пасе, а о том, что действие ")
          .append("разыграно, и на столе после него ничего не изменилось: у игрока не ")
          .append("нашлось ни ресурса, ни цели, ни места — либо он сам так решил.\n\n");
        md.append("| действие | разыграно | из них впустую | доля простоя |\n");
        md.append("|---|---|---|---|\n");
        for (String name : Actions.ALL_NAMES) {
            long played = PLAYED.containsKey(name) ? PLAYED.get(name).sum() : 0;
            long idle = IDLE.containsKey(name) ? IDLE.get(name).sum() : 0;
            md.append(String.format(Locale.ROOT, "| %s | %d | %d | %.1f%% |%n",
                name, played, idle, played == 0 ? 0.0 : 100.0 * idle / played));
        }
        // Действия, которых в списке нет (бесплатные с карт и прочее) — тоже показать.
        Map<String, Long> extra = new LinkedHashMap<>();
        PLAYED.forEach((k, v) -> {
            if (!Actions.ALL_NAMES.contains(k)) {
                extra.put(k, v.sum());
            }
        });
        if (!extra.isEmpty()) {
            md.append("\nПрочие записи в журнале действий: ").append(extra).append('\n');
        }

        Path outFile = Path.of("reports/balance/простой-в-действиях.md");
        Files.createDirectories(outFile.getParent());
        Files.writeString(outFile, md.toString(), StandardCharsets.UTF_8);
        out.println(md);
        out.println("записано: " + outFile.toAbsolutePath());
    }
}
