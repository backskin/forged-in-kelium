package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ВОРОНКА ЗАДАНИЙ: где теряются карты между «пришла в руку» и «выполнена».
 *
 * <p>ТРЕБОВАНИЕ ДИЗАЙНЕРА 28.08.2026: бот обязан выполнять пять-шесть заданий за
 * партию. Сейчас выходит меньше двух. Разница может прятаться в четырёх разных
 * местах, и лечатся они по-разному, поэтому сперва надо разделить:
 * <ol>
 *   <li><b>КАРТ МАЛО</b> — на руку приходит меньше, чем можно выполнить;</li>
 *   <li><b>УСЛОВИЕ НЕ ЗАГОРАЕТСЯ</b> — «ГОТОВО» не вспыхивает ни разу;</li>
 *   <li><b>ГОРЕЛО, НО НЕ СЫГРАНО</b> — СПЕЦ-действие ушло на что-то другое;</li>
 *   <li><b>СОЖЖЕНО</b> — бот предпочёл верхний утиль выполнению низа.</li>
 * </ol>
 *
 * <p>Середина воронки берётся не из догадок: на каждом предложении СПЕЦ-действия
 * движок и так шлёт {@code objective_hints} с разбором всей руки — какие карты
 * горят «ГОТОВО» и какие «ДОСТИЖИМО в этот ход».
 *
 * <p>Запуск: {@code kelium.ВоронкаЗаданий [партий] [игроков] [свод]}
 */
public final class ВоронкаЗаданий {

    private ВоронкаЗаданий() {
    }

    /** Накопитель по столу за все партии. */
    private static final class Счёт {
        long пришло;
        long выполнено;
        long усиленно;
        long сожжено;
        long предложенийСпец;
        long предложенийСГотовой;
        long предложенийСДостижимой;
        long готовыхКартВСумме;
        final Map<String, Long> награды = new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 150;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        kelium.engine.ObjectiveTargeting.resetCounters();
        Счёт c = new Счёт();
        long игроков = 0;

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 91000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 911L + g), players));
            }
            GameEngine.playGame(s, ags, ev -> {
                switch (String.valueOf(ev.get("type"))) {
                    case "objective_drawn" -> c.пришло++;
                    case "objective_burn" -> c.сожжено++;
                    case "objective" -> {
                        c.выполнено++;
                        if (Boolean.TRUE.equals(ev.get("enhanced"))) {
                            c.усиленно++;
                        }
                        if (ev.get("granted") instanceof Map<?, ?> gr) {
                            учестьНаграду(c, (Map<String, Object>) gr.get("base"));
                            учестьНаграду(c, (Map<String, Object>) gr.get("special"));
                        }
                    }
                    case "objective_hints" -> {
                        c.предложенийСпец++;
                        if (!(ev.get("hints") instanceof List<?> hs)) {
                            return;
                        }
                        boolean готово = false;
                        boolean достижимо = false;
                        for (Object h : hs) {
                            if (!(h instanceof Map<?, ?> m)) {
                                continue;
                            }
                            if (Boolean.TRUE.equals(m.get("ready"))) {
                                готово = true;
                                c.готовыхКартВСумме++;
                            }
                            if (Boolean.TRUE.equals(m.get("reachable"))) {
                                достижимо = true;
                            }
                        }
                        if (готово) {
                            c.предложенийСГотовой++;
                        }
                        if (достижимо) {
                            c.предложенийСДостижимой++;
                        }
                    }
                    default -> { }
                }
            });
            игроков += players;
        }

        StringBuilder b = new StringBuilder();
        b.append("# Воронка заданий: где теряются карты\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players)
            .append(". Места и характеры ротируются.\n\n");
        b.append("Цель дизайнера — **5–6 выполненных заданий на игрока за партию**.\n\n");

        b.append("| ступень воронки | на игрока за партию |\n|---|---:|\n");
        b.append("| карт пришло в руку, шт | ").append(окр(c.пришло, игроков)).append(" |\n");
        b.append("| **ВЫПОЛНЕНО, шт** | **").append(окр(c.выполнено, игроков))
            .append("** |\n");
        b.append("| из них усиленно, шт | ").append(окр(c.усиленно, игроков)).append(" |\n");
        b.append("| сожжено ради верха, шт | ").append(окр(c.сожжено, игроков)).append(" |\n");

        b.append("\n## Что видел бот в момент СПЕЦ-действия\n\n");
        b.append("| показатель | значение |\n|---|---:|\n");
        b.append("| предложений СПЕЦ-действия за партию на игрока, шт | ")
            .append(окр(c.предложенийСпец, игроков)).append(" |\n");
        b.append("| из них с хотя бы одной ГОТОВОЙ картой | ")
            .append(проц(c.предложенийСГотовой, c.предложенийСпец)).append(" |\n");
        b.append("| из них с хотя бы одной ДОСТИЖИМОЙ картой | ")
            .append(проц(c.предложенийСДостижимой, c.предложенийСпец)).append(" |\n");
        b.append("| выполнений на одно предложение с ГОТОВОЙ картой | ")
            .append(проц(c.выполнено, c.предложенийСГотовой)).append(" |\n");

        b.append("\n## Что задания принесли\n\n");
        b.append("| награда | всего за все партии, шт | на игрока за партию |\n");
        b.append("|---|---:|---:|\n");
        for (var e : c.награды.entrySet()) {
            b.append("| ").append(e.getKey()).append(" | ").append(e.getValue())
                .append(" | ").append(окр(e.getValue(), игроков)).append(" |\n");
        }

        b.append("\n## Как читать\n\n");
        b.append("Если **предложений с ГОТОВОЙ картой мало** — беда в условиях: ")
            .append("бот не доводит партию до состояния, которого просит карта. ")
            .append("Если их много, а **выполнений на предложение мало** — беда в ")
            .append("выборе: СПЕЦ-действие уходит на арсенал, сжигание или манёвр.\n");

        b.append("\n## Работает ли наведение\n\n");
        b.append("`").append(kelium.engine.ObjectiveTargeting.countersLine())
            .append("`\n\n");
        b.append("Наводка, которая ни разу не нашла лучшего выбора, и наводка, ")
            .append("которая находит его и проигрывает жадной оценке, лечатся ")
            .append("совершенно по-разному — поэтому счётчик стоит здесь.\n");

        Path out = Path.of("reports", "balance", "воронка-заданий.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println(kelium.engine.ObjectiveTargeting.countersLine());
        System.out.println("выполнено на игрока: " + окр(c.выполнено, игроков)
            + ", пришло: " + окр(c.пришло, игроков)
            + ", предложений с готовой: " + проц(c.предложенийСГотовой, c.предложенийСпец));
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static void учестьНаграду(Счёт c, Map<String, Object> reward) {
        if (reward == null) {
            return;
        }
        for (var e : reward.entrySet()) {
            long n = e.getValue() instanceof Number num ? num.longValue()
                : Boolean.TRUE.equals(e.getValue()) ? 1L : 0L;
            if (n != 0) {
                c.награды.merge(e.getKey(), n, Long::sum);
            }
        }
    }

    private static String окр(long часть, long всего) {
        return всего == 0 ? "—"
            : String.format(Locale.ROOT, "%.2f", (double) часть / всего);
    }

    private static String проц(long часть, long всего) {
        return всего == 0 ? "—"
            : String.format(Locale.ROOT, "%.1f%%", 100.0 * часть / всего);
    }
}
