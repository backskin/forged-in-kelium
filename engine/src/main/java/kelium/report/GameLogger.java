package kelium.report;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resources;
import kelium.dataio.GameConfig;
import kelium.dataio.Ctx;

/**
 * Пофайловый лог одной партии — все ходы, события и снимки состояния. Порт из
 * forge/report/gamelog.py.
 *
 * <p>Каждая партия заводит собственный читаемый лог-файл, открываемый в начале
 * партии. Пишутся раунды, круги, вскрытия приказов, действия, события боя,
 * снимки поля и финал+счёт. Поддерживаются два языка (en/ru): русская копия
 * пишется в отдельную папку. Логгер подключается к потоку событий движка
 * методом {@link #record(Map)}.
 */
public final class GameLogger {

    private static final Map<String, String> ACTIONS_RU = Map.of(
        "assembly", "Снаряжение", "mining", "Добыча", "build", "Стройка",
        "energy_swap", "Смена энергии", "movement", "Движение", "combat", "Бой",
        "market", "Маркет", "science", "Наука");
    private static final Map<String, String> ORDERS_RU = Map.of(
        "development", "Разработка", "infrastructure", "Инфраструктура",
        "operation", "Операция", "acquisitions", "Приобретения");
    private static final Map<String, String> COND_RU = Map.of(
        "victory_points", "по победным очкам", "military", "военная победа",
        "super_objective", "супер-задание", "all_peaks_occupied", "заняты все вершины",
        "last_spawn_tile", "остался последний тайл зарождения");

    private final GameState state;
    private final Path path;
    private final boolean ru;
    private PrintWriter fh;
    private PrintWriter echo;   // необязательное зеркало в консоль (живой лог)

    public GameLogger(GameState state, Path path, String lang) {
        this.state = state;
        this.path = path;
        this.ru = "ru".equals(lang);
    }

    /**
     * Включить дублирование лога в консоль в реальном времени: каждая строка,
     * идущая в файл, тут же печатается и в {@code echo}. Возвращает this для
     * цепочечного вызова.
     */
    public GameLogger withEcho(PrintWriter echo) {
        this.echo = echo;
        return this;
    }

    private void open() {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            fh = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("не удалось открыть лог " + path + ": " + e.getMessage());
        }
        GameConfig cfg = Ctx.cfg(state);
        line("=".repeat(70));
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        if (ru) {
            line("ЛОГ ПАРТИИ  —  симулятор «Кристаллы Раздора»");
            line("открыт:    " + now);
            line("правила:   " + cfg.ruleset.id);
            line("игроков:   " + state.numPlayers());
            line("сид:       " + cfg.seed);
            line("стороны:   " + sidesStr("игрок"));
        } else {
            line("GAME LOG  —  Forged in Kelium simulator");
            line("opened:   " + now);
            line("ruleset:  " + cfg.ruleset.id);
            line("players:  " + state.numPlayers());
            line("seed:     " + cfg.seed);
            line("sides:    " + sidesStr("seat"));
        }
        line("=".repeat(70));
    }

    private String sidesStr(String pfx) {
        StringBuilder sb = new StringBuilder();
        for (PlayerState p : state.players) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(pfx).append(p.seat).append("=").append(p.board.troop.side);
        }
        return sb.toString();
    }

    /**
     * Закрыть файл лога. Обычно вызывается по событию конца партии, но вызвать
     * можно и снаружи — например при ОТМЕНЕ прогона, чтобы не оставить открытых
     * писателей (повторный вызов безопасен).
     */
    public void close() {
        if (fh != null) {
            fh.flush();
            fh.close();
            fh = null;
        }
    }

    private void line(String text) {
        if (fh != null) {
            fh.println(text);
            fh.flush();   // сразу на диск — лог пишется в реальном времени
        }
        if (echo != null) {
            echo.println(text);   // и одновременно в консоль (живой лог партии)
            echo.flush();
        }
    }

    private String seat(Object i) {
        return (ru ? "игрок" : "seat") + i;
    }

    private String act(String name) {
        return ru ? ACTIONS_RU.getOrDefault(name, name) : name;
    }

    /**
     * Человеческое имя карты приказа для лога. ВАЖНО: печатаем ИДЕНТИФИКАТОР
     * карты плюс название приказа в скобках, например "blue_dev(Разработка)".
     * Разные карты с одинаковым верхним приказом (blue_dev и red_dev обе
     * «Разработка») так различимы — видно, что за 4 круга играются 4 РАЗНЫЕ
     * карты, а не одна и та же несколько раз.
     */
    @SuppressWarnings("unchecked")
    private String card(String cid) {
        try {
            Map<String, Object> c = Ctx.cards(state, "orders").byId(cid);
            String orderName;
            if (Boolean.TRUE.equals(c.get("joker"))) {
                orderName = ru ? "БЕЗОПАСНОСТЬ" : "security";
            } else {
                String top = String.valueOf(c.get("top"));
                orderName = ru ? ORDERS_RU.getOrDefault(top, top) : top;
            }
            return cid + "(" + orderName + ")";
        } catch (RuntimeException e) {
            return cid;
        }
    }

    /** Принять одно событие движка и дописать его в лог партии. */
    @SuppressWarnings("unchecked")
    public void record(Map<String, Object> event) {
        String t = String.valueOf(event.get("type"));
        if ("game_start".equals(t)) {
            open();
            if (ru) {
                line("\n### СТАРТ ПАРТИИ — игроков: " + event.get("players")
                    + ", правила: " + event.get("ruleset"));
            } else {
                line("\n### GAME START — " + event.get("players") + " players, ruleset "
                    + event.get("ruleset"));
            }
            return;
        }
        if (fh == null) {
            return;
        }
        switch (t) {
            case "refresh" -> {
                if (Boolean.TRUE.equals(event.get("skipped"))) {
                    line("\n--- " + (ru ? "РАУНД" : "ROUND") + " " + event.get("round")
                        + "  (" + (ru ? "обновление пропущено" : "refresh skipped") + ") ---");
                } else {
                    line("\n--- " + (ru ? "РАУНД" : "ROUND") + " " + event.get("round") + "  "
                        + (ru ? "первый игрок" : "first_player") + "=" + seat(event.get("first_player")) + " ---");
                }
                line("    " + (ru ? "карта маркета" : "market card") + ": " + state.marketActive);
                writeSnapshot(false);
            }
            case "blind_discard" -> {
                Object saObj = event.get("set_aside");
                StringBuilder sb = new StringBuilder();
                if (saObj instanceof Map<?, ?> saMap) {
                    Map<Integer, String> sa = new TreeMap<>((Map<Integer, String>) saObj);
                    for (var e : sa.entrySet()) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(seat(e.getKey())).append(":").append(card(e.getValue()));
                    }
                }
                String heading = ru
                    ? "слепой сброс (под трофеи каждый отложил): "
                    : "blind discard (each set aside for trophies): ";
                line("    [" + heading + (sb.length() > 0 ? sb : (ru ? "нет" : "none")) + "]");
            }
            case "reveal" -> {
                @SuppressWarnings("unchecked")
                Map<Integer, String> rev = new TreeMap<>((Map<Integer, String>) event.get("revealed"));
                StringBuilder sb = new StringBuilder();
                for (var e : rev.entrySet()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(seat(e.getKey())).append(":").append(card(e.getValue()));
                }
                line("\n  " + (ru ? "Круг" : "Circle") + " " + event.get("circle") + "  "
                    + (ru ? "вскрытия" : "reveals") + ": " + sb);
            }
            case "action" -> {
                Object tel = event.get("telemetry");
                boolean ok = Boolean.TRUE.equals(event.get("ok"));
                String tag = ru ? (ok ? "[ок] " : "[--] ") : (ok ? "OK " : "-- ");
                String extra = tel != null ? "  " + tel : "";
                line("    " + seat(event.get("seat")) + "  " + tag
                    + String.format("%-14s", act((String) event.get("action")))
                    + " " + event.getOrDefault("detail", "") + extra);
            }
            case "combat_hit" -> {
                if (ru) {
                    String d = Boolean.TRUE.equals(event.get("destroyed")) ? "УНИЧТОЖЕН" : "урон";
                    line("      >> " + seat(event.get("seat")) + " бьёт из " + event.get("source")
                        + " по " + event.get("target") + ": " + event.get("attacker") + " -> "
                        + seat(event.get("victim_owner")) + " " + event.get("victim") + " [" + d + "]");
                } else {
                    String d = Boolean.TRUE.equals(event.get("destroyed")) ? "DESTROYED" : "damage";
                    line("      * " + seat(event.get("seat")) + " " + event.get("source") + "->"
                        + event.get("target") + ": " + event.get("attacker") + " vs "
                        + seat(event.get("victim_owner")) + " " + event.get("victim") + " [" + d + "]");
                }
            }
            case "raze_neutral" -> line("      " + (ru ? "снос нейтральной постройки" : "razed neutral")
                + (Boolean.TRUE.equals(event.get("big")) ? (ru ? " (большой)" : " (big)") : "")
                + " @ " + event.get("target") + " (" + seat(event.get("seat")) + ") +"
                + event.getOrDefault("debris", 1) + (ru ? "обломок " : "debris ")
                + "+" + event.getOrDefault("containers", 1) + (ru ? "контейнер" : "cont"));
            case "damage_neutral" -> line("      " + (ru ? "урон нейтралу" : "damaged neutral")
                + " @ " + event.get("target") + " (" + seat(event.get("seat")) + "), "
                + (ru ? "осталось HP=" : "hpLeft=") + event.get("hpLeft"));
            case "objective" -> {
                boolean enh = Boolean.TRUE.equals(event.get("enhanced"));
                line("      " + seat(event.get("seat")) + " "
                    + (ru ? "выполнил задание" : "completed objective") + " " + event.get("card")
                    + (enh ? (ru ? " (усилено)" : " (enhanced)") : ""));
            }
            case "objective_burn" -> line("      " + seat(event.get("seat")) + " "
                + (ru ? "сжёг верх задания" : "burned objective top") + " " + event.get("card")
                + " (" + event.getOrDefault("label", "") + ")");
            case "objective_drawn" -> {
                String src = String.valueOf(event.get("source"));
                String srcRu = switch (src) {
                    case "setup" -> "старт";
                    case "round_end" -> "конец раунда";
                    case "market" -> "рынок";
                    case "enhanced_reward" -> "награда за усиление";
                    default -> src;
                };
                line("      " + seat(event.get("seat")) + " "
                    + (ru ? "получил задание" : "drew objective") + " " + event.get("card")
                    + " (" + (ru ? srcRu : src) + ", " + (ru ? "в руке" : "hand") + "="
                    + event.get("hand") + ")");
            }
            case "super_deploy" -> line("      " + seat(event.get("seat")) + " "
                + (ru ? "РАЗВЕРНУЛ СУПЕР-ЗАДАНИЕ" : "DEPLOYED SUPER-OBJECTIVE") + " " + event.get("card"));
            case "turn_end" -> line("    -> " + seat(event.get("seat")) + " "
                + (ru ? "конец хода" : "end") + ": " + event.get("resources"));
            case "return" -> {
                line("  [" + (ru ? "этап Возврата, раунд" : "return step, round") + " "
                    + event.get("round") + "]");
                writeSnapshot(false);
            }
            case "game_end" -> {
                writeSnapshot(true);
                writeScores(event);
                line("\n### " + (ru ? "КОНЕЦ ПАРТИИ" : "GAME END"));
                close();
            }
            default -> { }
        }
    }

    private void writeSnapshot(boolean finalState) {
        String header = finalState ? (ru ? "ФИНАЛ" : "FINAL STATE") : (ru ? "состояние" : "state");
        line("    [" + header + "] " + (ru ? "раунд" : "round") + "=" + state.round + " "
            + (ru ? "круг" : "circle") + "=" + state.circle);
        for (PlayerState p : state.players) {
            int units = p.unitsOnField().size();
            int bld = p.buildingsOnField().size();
            String cu = p.hasCommandCenter() ? (ru ? "ЦУ" : "CU") : (ru ? "нет ЦУ" : "no-CU");
            StringBuilder tech = new StringBuilder();
            for (var e : p.techSteps.entrySet()) {
                if (tech.length() > 0) {
                    tech.append(",");
                }
                tech.append(e.getKey()).append(":").append(e.getValue());
            }
            line("      " + seat(p.seat) + "[" + p.board.troop.side + "] "
                + fmtRes(p.resources) + " | " + (ru ? "войск" : "units") + "=" + units + " "
                + (ru ? "зданий" : "bld") + "=" + bld + " " + cu + " | "
                + (ru ? "треки" : "tech") + "=" + (tech.length() > 0 ? tech : "-"));
        }
    }

    @SuppressWarnings("unchecked")
    private void writeScores(Map<String, Object> event) {
        line("\n    " + (ru ? "ОЧКИ:" : "SCORES:"));
        Map<Integer, Map<String, Integer>> scores =
            new TreeMap<>((Map<Integer, Map<String, Integer>>) event.get("scores"));
        Object winner = event.get("winner");
        for (var e : scores.entrySet()) {
            Map<String, Integer> bd = e.getValue();
            StringBuilder src = new StringBuilder();
            for (var kv : bd.entrySet()) {
                if (!"total".equals(kv.getKey()) && kv.getValue() != 0) {
                    if (src.length() > 0) {
                        src.append(" ");
                    }
                    src.append(kv.getKey()).append("=").append(kv.getValue());
                }
            }
            String win = e.getKey().equals(winner) ? (ru ? "  <-- ПОБЕДИТЕЛЬ" : "  <-- WINNER") : "";
            line("      " + seat(e.getKey()) + ": " + bd.get("total") + " " + (ru ? "ПО" : "VP")
                + "  (" + (src.length() > 0 ? src : "-") + ")" + win);
        }
        String cond = String.valueOf(event.get("condition"));
        line("    " + (ru ? "условие победы" : "win condition") + ": "
            + (ru ? COND_RU.getOrDefault(cond, cond) : cond));
    }

    private String fmtRes(Resources r) {
        if (ru) {
            return "мон=" + r.coin() + " кел=" + r.kelium() + " бпр=" + r.ammo() + " обл=" + r.debris();
        }
        return "coin=" + r.coin() + " kel=" + r.kelium() + " ammo=" + r.ammo() + " debris=" + r.debris();
    }

    /** Уникальное имя файла лога на партию: игроки + сид + метка времени. */
    public static Path defaultLogPath(GameState state, Path logDir) {
        GameConfig cfg = Ctx.cfg(state);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        String name = "game_p" + state.numPlayers() + "_seed" + cfg.seed + "_" + ts + ".log";
        return logDir.resolve(name);
    }
}
