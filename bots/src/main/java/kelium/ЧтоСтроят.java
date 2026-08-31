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
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ЧТО БОТЫ УСПЕВАЮТ ПОСТРОИТЬ И ЧЕМ ЭТО ЗАПИТАНО.
 *
 * <p>ЗАЧЕМ. На поле к концу партии 0.24 авиации на игрока — авиабаза не
 * строится почти никогда. Причин может быть три, и лечатся они по-разному:
 * <ul>
 *   <li>ЗДАНИЯ НЕТ — не хватило монет или бот не выбрал его;</li>
 *   <li>ЗДАНИЕ ЕСТЬ, НО ОБЕСТОЧЕНО — построили, а кубиков не нашлось;</li>
 *   <li>ЗДАНИЕ ЗАПИТАНО, НО ЖЕТОНОВ НЕТ — Сборкой брали боеприпасы.</li>
 * </ul>
 *
 * <p>Поэтому считается всё три разом: сколько зданий каждого вида стоит на поле,
 * сколько из них запитано, каков энергобаланс игрока и сколько монет он не
 * потратил. Последнее важно отдельно: если монеты остаются, дело не в бедности.
 *
 * <p>Запуск: {@code kelium.ЧтоСтроят [партий] [игроков] [свод]}
 */
public final class ЧтоСтроят {

    private ЧтоСтроят() {
    }

    private static final class Строка {
        long наПоле;
        long запитано;
        long игроковСним;      // у скольких игроков есть хотя бы одно
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 60;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        Map<BuildingType, Строка> по = new LinkedHashMap<>();
        for (BuildingType t : BuildingType.values()) {
            по.put(t, new Строка());
        }
        long игроков = 0;
        long монетОсталось = 0;
        long выработка = 0;
        long ячеекПотребителей = 0;
        long кубиковНаПотребителях = 0;

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 55000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 551L + g), players));
            }
            GameEngine.playGame(s, ags, ev -> { });
            for (PlayerState p : s.players) {
                игроков++;
                монетОсталось += p.resources.coin();
                java.util.Set<BuildingType> есть = new java.util.HashSet<>();
                for (BuildingToken b : p.buildingsOnField()) {
                    Строка ст = по.get(b.type);
                    ст.наПоле++;
                    есть.add(b.type);
                    int slots = b.energySlots;
                    if (slots > 0) {
                        ячеекПотребителей += slots;
                        кубиковНаПотребителях += b.energyPlaced;
                        if (b.energyPlaced >= slots) {
                            ст.запитано++;
                        }
                    } else {
                        ст.запитано++;      // потребления нет — считаем рабочим
                    }
                    выработка += b.energyIdle;
                }
                for (BuildingType t : есть) {
                    по.get(t).игроковСним++;
                }
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("# Что боты успевают построить\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players)
            .append(". Места и характеры ротируются. Состояние на конец партии.\n\n");

        b.append("| здание | на поле, на игрока | запитано, доля | у скольких игроков есть |\n");
        b.append("|---|---:|---:|---:|\n");
        for (var e : по.entrySet()) {
            Строка ст = e.getValue();
            if (ст.наПоле == 0) {
                continue;
            }
            b.append("| ").append(имя(e.getKey()))
                .append(" | ").append(окр((double) ст.наПоле / игроков))
                .append(" | ").append(проц(ст.запитано, ст.наПоле))
                .append(" | ").append(проц(ст.игроковСним, игроков))
                .append(" |\n");
        }

        b.append("\n## Энергия и деньги\n\n");
        b.append("| показатель | на игрока |\n|---|---:|\n");
        b.append("| простаивает кубиков на источниках, шт | ")
            .append(окр((double) выработка / игроков)).append(" |\n");
        b.append("| ячеек у потребителей, шт | ")
            .append(окр((double) ячеекПотребителей / игроков)).append(" |\n");
        b.append("| кубиков лежит на потребителях, шт | ")
            .append(окр((double) кубиковНаПотребителях / игроков)).append(" |\n");
        b.append("| **не потрачено монет к концу партии** | **")
            .append(окр((double) монетОсталось / игроков)).append("** |\n");

        b.append("\n## Как читать\n\n");
        b.append("Если здания нет вовсе — упор в монеты или в выбор бота. Если оно ")
            .append("стоит, но не запитано — упор в энергию. Если монеты к концу ")
            .append("партии остаются, значит бедность ни при чём и дело в выборе.\n");

        Path out = Path.of("reports", "balance", "что-строят-" + ruleset + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static String имя(BuildingType t) {
        return switch (t) {
            case BARRACKS -> "казарма";
            case FACTORY -> "завод";
            case AIRBASE -> "авиабаза";
            case COMMAND_CENTER -> "ЦУ";
            case MINER -> "добытчик";
            case POWER_PLANT -> "энергостанция";
            default -> t.name().toLowerCase(Locale.ROOT);
        };
    }

    private static String проц(long часть, long всего) {
        return всего == 0 ? "—"
            : String.format(Locale.ROOT, "%.1f%%", 100.0 * часть / всего);
    }

    private static String окр(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
