package kelium.report;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.dataio.Ctx;

/**
 * NarrativeLog — «живой» рассказ партии на русском. В отличие от {@link GameLogger}
 * (сухой протокол ходов) сюда пишут:
 * <ul>
 *   <li>сами боты — от ПЕРВОГО ЛИЦА, объясняя свои решения («Веду танк на h3 —
 *       там вражеская пехота, снесу её ради трофея»);
 *   <li>КОММЕНТАТОР ({@link Commentator}) — рассуждает, почему игрок так поступил,
 *       хвалит удачные ходы и ругает нелогичные.
 * </ul>
 *
 * <p>Формат — читаемый текст с отступами по раундам/кругам. Файл открывается на
 * событии {@code game_start} и закрывается на {@code game_end}. Игроки в тексте
 * названы по месту и характеру («игрок 0 [стратег, сторона A]»).
 */
public final class NarrativeLog {

    private final GameState state;
    private final Path path;
    private PrintWriter fh;
    private PrintWriter echo;
    private final boolean md;           // писать в Markdown-оформлении
    private int curRound = 0;           // текущий раунд (для имён SVG)

    /** Отображаемые имена игроков (место -> «игрок N [роль, сторона]»). */
    private final String[] names;

    public NarrativeLog(GameState state, Path path) {
        this.state = state;
        this.path = path;
        this.md = path.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".md");
        this.names = new String[state.numPlayers()];
    }

    /** Задать читаемое имя-роль игрока (например «стратег», «агрессор»). */
    public NarrativeLog withRole(int seat, String role) {
        if (seat >= 0 && seat < names.length) {
            PlayerState p = state.player(seat);
            names[seat] = "игрок " + seat + " [" + role + ", сторона " + p.board.troop.side + "]";
        }
        return this;
    }

    /** Дублировать рассказ в консоль в реальном времени. */
    public NarrativeLog withEcho(PrintWriter echo) {
        this.echo = echo;
        return this;
    }

    /** Читаемое имя игрока (роль подставится, если задана через withRole). */
    public String who(int seat) {
        if (seat >= 0 && seat < names.length && names[seat] != null) {
            return names[seat];
        }
        PlayerState p = state.player(seat);
        return "игрок " + seat + " [сторона " + p.board.troop.side + "]";
    }

    public void open() {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            fh = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("не удалось открыть рассказ " + path, e);
        }
        GameConfig cfg = Ctx.cfg(state);
        if (md) {
            line("# Рассказ партии — «Кристаллы Раздора»");
            line("");
            line("**Игроков:** " + state.numPlayers() + " · **сид:** " + cfg.seed
                + " · **правила:** " + cfg.ruleset.id);
            line("");
            line("**За столом:**");
            for (int seat = 0; seat < state.numPlayers(); seat++) {
                line("- " + who(seat));
            }
        } else {
            line("═".repeat(72));
            line("  РАССКАЗ ПАРТИИ — «Кристаллы Раздора»");
            line("  игроков: " + state.numPlayers() + "   сид: " + cfg.seed
                + "   правила: " + cfg.ruleset.id);
            line("  за столом:");
            for (int seat = 0; seat < state.numPlayers(); seat++) {
                line("    • " + who(seat));
            }
            line("═".repeat(72));
        }
    }

    public void close() {
        if (fh != null) {
            fh.flush();
            fh.close();
            fh = null;
        }
    }

    /** Заголовок раунда. */
    public void round(int rnd, int firstPlayer) {
        curRound = rnd;
        line("");
        if (md) {
            line("---");
            line("## Раунд " + rnd + "  ·  первым ходит " + who(firstPlayer));
        } else {
            line("┌── РАУНД " + rnd + "  (первым ходит " + who(firstPlayer) + ")");
        }
    }

    /** Заголовок круга (одновременное вскрытие приказов). */
    public void circle(int circle) {
        if (md) {
            line("");
            line("### Круг " + circle);
        } else {
            line("│");
            line("├─ круг " + circle);
        }
    }

    /** Реплика бота от первого лица (с отступом под кругом). */
    public void say(int seat, String text) {
        line(md ? "- 🗣 **" + who(seat) + ":** «" + text + "»"
               : "│    🗣 " + who(seat) + ": «" + text + "»");
    }

    /** Строка комментатора. */
    public void commentator(String text) {
        line(md ? "- 🎤 *Комментатор:* " + text
               : "│    🎤 Комментатор: " + text);
    }

    /** Похвала комментатора. */
    public void praise(String text) {
        line(md ? "- 👏 *Комментатор (одобряет):* " + text
               : "│    👏 Комментатор (одобряет): " + text);
    }

    /** Критика/ругань комментатора. */
    public void criticize(String text) {
        line(md ? "- 😠 *Комментатор (критикует):* " + text
               : "│    😠 Комментатор (критикует): " + text);
    }

    /** Нейтральное событие рассказа (бой, выполнение задания и т. п.). */
    public void event(String text) {
        line(md ? "- ▸ " + text : "│    ▸ " + text);
    }

    /** Финальный блок с итогом. */
    public void ending(String text) {
        if (md) {
            line("");
            line("---");
            line("### " + text);
        } else {
            line("│");
            line("└── " + text);
        }
    }

    // ==================== БОГАТЫЕ ПАНЕЛИ (обзор партии) ====================

    /** Снимки статуса игроков — чтобы печатать статус только при ИЗМЕНЕНИИ. */
    private final String[] statusSnap = new String[8];

    /** Карта текущего поля: в md — SVG-картинка + ASCII в код-блоке, иначе ASCII. */
    public void fieldMap() {
        if (md) {
            // сохранить SVG раунда рядом с рассказом и вставить картинку
            String svgName = "round" + curRound + ".svg";
            try {
                Path svgPath = path.getParent().resolve(svgName);
                Files.writeString(svgPath,
                    kelium.report.SvgFieldRenderer.render(state, curRound), StandardCharsets.UTF_8);
                line("");
                line("**Поле:**");
                line("");
                line("![поле раунда " + curRound + "](" + svgName + ")");
            } catch (IOException e) {
                line("_(не удалось сохранить SVG поля: " + e.getMessage() + ")_");
            }
            // плюс ASCII в свёрнутом код-блоке (запасной вид)
            line("");
            line("<details><summary>поле текстом (ASCII)</summary>");
            line("");
            line("```");
            for (String row : LiveFieldRenderer.render(state).split("\n")) {
                line(row);
            }
            line("```");
            line("</details>");
            return;
        }
        line("│");
        line("│  ── ПОЛЕ ──");
        for (String row : LiveFieldRenderer.render(state).split("\n")) {
            line("│  " + row);
        }
    }

    /** Планшет науки: 3 трека и позиции фишек игроков. */
    public void scienceBoard() {
        line(md ? "**Наука (треки):**" : "│  ── НАУКА (треки) ──");
        var tech = state.tech;
        for (String track : tech.tracks) {
            StringBuilder sb = new StringBuilder();
            for (PlayerState p : state.players) {
                int step = p.techSteps.getOrDefault(track, 0);
                if (step > 0) {
                    sb.append("игрок").append(p.seat).append("=шаг").append(step).append("  ");
                }
            }
            String body = sb.length() > 0 ? sb.toString().stripTrailing() : "—";
            line(md ? "- " + trackRu(track) + ": " + body
                   : "│    " + trackRu(track) + ": " + body);
        }
    }

    /** Активная карта рынка + краткая расшифровка сделок за келемий. */
    public void marketCard() {
        String cid = state.marketActive;
        String body = "активная карта: " + (cid != null ? cid : "нет")
            + " (базовые курсы: келемий→монеты/боеприпасы/карты заданий/энергия-навсегда)";
        if (md) {
            line("**Рынок:** " + body);
        } else {
            line("│  ── РЫНОК ──");
            line("│    " + body);
        }
    }

    /** Отложенный приказ под трофеи — какую карту каждый убрал рубашкой вверх. */
    public void blindDiscard(Map<Integer, String> setAside) {
        if (md) {
            line("");
            line("**🃏 Отложенный приказ под трофеи** (рубашкой вверх, не разыгрывается; тайна для остальных, но не для себя):");
            for (int seat = 0; seat < state.numPlayers(); seat++) {
                String cid = setAside.get(seat);
                line("- " + who(seat) + ": отложил " + (cid != null ? cardName(cid) : "—"));
            }
            return;
        }
        line("│");
        line("│  🃏 СЛЕПОЙ СБРОС под трофеи (карта уходит рубашкой вверх, не разыгрывается):");
        for (int seat = 0; seat < state.numPlayers(); seat++) {
            String cid = setAside.get(seat);
            line("│    " + who(seat) + ": отложил " + (cid != null ? cardName(cid) : "—"));
        }
    }

    /** Вскрытие приказов круга — кто какую карту вскрыл. */
    public void reveal(int circle, Map<Integer, String> revealed) {
        if (md) {
            line("");
            line("### Круг " + circle + " · вскрытие приказов");
            for (int seat = 0; seat < state.numPlayers(); seat++) {
                String cid = revealed.get(seat);
                if (cid != null) {
                    line("- " + who(seat) + " вскрыл: " + cardName(cid));
                }
            }
            return;
        }
        line("│");
        line("├─ круг " + circle + " · ВСКРЫТИЕ ПРИКАЗОВ");
        for (int seat = 0; seat < state.numPlayers(); seat++) {
            String cid = revealed.get(seat);
            if (cid != null) {
                line("│    " + who(seat) + " вскрыл: " + cardName(cid));
            }
        }
    }

    /** Полный статус игрока. Печатает всегда (в начале раунда). */
    public void playerStatus(int seat) {
        for (String row : statusLines(seat)) {
            line(row);
        }
        statusSnap[seat] = String.join("\n", statusLines(seat));
    }

    /** Статус игрока ТОЛЬКО если он изменился с прошлого показа. */
    public void playerStatusIfChanged(int seat) {
        String now = String.join("\n", statusLines(seat));
        if (!now.equals(statusSnap[seat])) {
            for (String row : statusLines(seat)) {
                line(row);
            }
            statusSnap[seat] = now;
        }
    }

    private List<String> statusLines(int seat) {
        PlayerState p = state.player(seat);
        // префиксы для двух режимов
        String head = md ? "**▣ " + who(seat) + "**" : "│  ▣ " + who(seat);
        String item = md ? "  - " : "│      ";
        List<String> out = new ArrayList<>();
        out.add(head);
        out.add(String.format("%sресурсы: мон=%d кел=%d бпр=%d трофеи=%d",
            item, p.resources.coin(), p.resources.kelium(), p.resources.ammo(), p.resources.trophy()));
        StringBuilder bld = new StringBuilder(item + "здания: ");
        for (BuildingToken b : p.buildingsOnField()) {
            bld.append(bldRu(b)).append("@").append(b.hexId).append(" ");
        }
        out.add(bld.toString().stripTrailing());
        StringBuilder un = new StringBuilder(item + "войска: ");
        boolean anyUnit = false;
        for (UnitToken u : p.unitsOnField()) {
            un.append(unitRu(u.type)).append("@").append(u.hexId).append(" ");
            anyUnit = true;
        }
        out.add(anyUnit ? un.toString().stripTrailing() : item + "войска: —");
        out.add(String.format("%sтрофеи: жетонов=%d (очков %d) + трофеи=%d",
            item, p.trophySpace.size(), p.trophySpacePoints(), p.resources.trophy()));
        out.add(item + "приказы(" + p.orderColor + "): " + p.orderHand
            + "   задания: " + p.objectiveHand);
        out.add(item + "арсенал: рука" + p.arsenalHand + " установлено" + p.arsenalInstalled);
        out.add(String.format("%sмодули: красн=%d син=%d золото=%d   супер=%s%s",
            item, p.redModules, p.blueModules, p.goldModules,
            p.superObjective != null ? p.superObjective : "—",
            p.superObjectiveComplete ? " (выполнено)" : ""));
        int vp = kelium.engine.Scoring.scorePlayer(state, seat).getOrDefault("total", 0);
        out.add(item + "ПО сейчас: " + vp);
        return out;
    }

    private static String trackRu(String track) {
        return switch (track) {
            case "left" -> "красный (бой)";
            case "middle" -> "хранилище (добыча)";
            case "right" -> "синий (движение)";
            default -> track;
        };
    }

    private String bldRu(BuildingToken b) {
        String base = switch (b.type) {
            case COMMAND_CENTER -> "ЦУ";
            case FACTORY -> "Завод";
            case AIRBASE -> "Авиабаза";
            case BARRACKS -> "Казарма";
            case MINER -> "Добытчик";
            case POWER_PLANT -> "Энергостанция";
        };
        return b.level != null ? base + b.level : base;
    }

    private static String unitRu(UnitType t) {
        return switch (t) {
            case INFANTRY -> "пехота";
            case VEHICLE -> "техника";
            case AIRCRAFT -> "авиация";
            case TOWER -> "вышка";
        };
    }

    /** Человеческое имя карты приказа (id + название приказа). */
    @SuppressWarnings("unchecked")
    private String cardName(String cid) {
        try {
            Map<String, Object> c = Ctx.cards(state, "orders").byId(cid);
            if (Boolean.TRUE.equals(c.get("joker"))) {
                return cid + " (БЕЗОПАСНОСТЬ)";
            }
            String top = String.valueOf(c.get("top"));
            String ru = switch (top) {
                case "development" -> "Разработка";
                case "infrastructure" -> "Инфраструктура";
                case "operation" -> "Операция";
                case "acquisitions" -> "Приобретения";
                default -> top;
            };
            return cid + " (" + ru + ")";
        } catch (RuntimeException e) {
            return cid;
        }
    }

    public boolean isOpen() {
        return fh != null;
    }

    private void line(String text) {
        if (fh != null) {
            fh.println(text);
            fh.flush();
        }
        if (echo != null) {
            echo.println(text);
            echo.flush();
        }
    }
}
