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
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ПОЧЕМУ ИГРОК С ЗАГЛУШКОЙ НА ЭТОМ РОДЕ ПРОИГРЫВАЕТ — РАЗБОР, А НЕ ДОГАДКА.
 *
 * <p>ВОПРОС ДИЗАЙНЕРА 27.08.2026. Замер показал: у кого закрыта авиация — 19%
 * побед, у кого техника — 31%. Логика от этого страдает: спец-атака авиации бьёт
 * технику, техники на поле почти нет, значит заглушка на авиации не отнимает
 * почти ничего — и должна бы, наоборот, помогать. Значит либо число врёт, либо
 * дело не в атаке.
 *
 * <p>ЧТО ЗДЕСЬ ЧИНИТСЯ В САМОМ ЗАМЕРЕ. Прежний стенд считал род по тому, где
 * заглушка лежала В КОНЦЕ партии. Но её МОЖНО ПЕРЕСТАВИТЬ — обменом на планшете
 * науки за трофей или утилем карты. Значит прежние строки смешивали «начал с
 * закрытой авиацией» и «переставил на авиацию к концу». Здесь род берётся из
 * события подготовки (seal_unit), то есть СТАРТОВЫЙ, и отдельно считается,
 * сколько раз заглушка вообще переезжала.
 *
 * <p>ЧТО ЕЩЁ СЧИТАЕТСЯ ПО КАЖДОМУ СТАРТОВОМУ РОДУ, чтобы ответить «почему»:
 * очки и их источники, уничтожено и потеряно жетонов, сколько войск этого рода
 * игрок вообще вывел на поле, сколько раз он бил и сколько боеприпасов потратил.
 *
 * <p>Запуск: {@code kelium.ЗаглушкаРазбор [партий] [игроков] [свод]}
 */
public final class ЗаглушкаРазбор {

    private ЗаглушкаРазбор() {
    }

    /** Всё, что накопилось по одному стартовому роду заглушки. */
    private static final class Итог {
        long игроков;
        long побед;
        long очков;
        long убил;
        long потерял;
        long ударов;
        long боеприпасов;
        long войскЗакрытогоРода;   // сколько жетонов ЗАКРЫТОГО рода вывел на поле
        long войскВсего;
        long переставил;           // заглушка к концу лежит на другом роде
        long лишилсяЖетона;        // ЦУ снесли, заглушка уехала
        final Map<String, Long> очкиПоИсточникам = new LinkedHashMap<>();
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        Map<String, Итог> поСтарту = new LinkedHashMap<>();

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 61000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 523L + g), players));
            }
            String[] старт = new String[players];
            long[][] бой = new long[players][2];      // ударов, боеприпасов
            long[] потери = new long[players];

            GameEngine.playGame(s, ags, ev -> {
                String тип = String.valueOf(ev.get("type"));
                if ("seal_unit".equals(тип)) {
                    int seat = ((Number) ev.get("seat")).intValue();
                    старт[seat] = String.valueOf(ev.get("unit"));
                    return;
                }
                if (!"combat_hit".equals(тип)) {
                    return;
                }
                int seat = ((Number) ev.get("seat")).intValue();
                бой[seat][0]++;
                бой[seat][1] += ev.get("ammo") instanceof Number n ? n.intValue() : 0;
                if (Boolean.TRUE.equals(ev.get("destroyed"))
                        && ev.get("victim_owner") instanceof Number vo) {
                    потери[vo.intValue()]++;
                }
            });

            for (PlayerState p : s.players) {
                String род = старт[p.seat] == null ? "без заглушки" : старт[p.seat];
                Итог t = поСтарту.computeIfAbsent(род, k -> new Итог());
                t.игроков++;
                Map<String, Integer> счёт = Scoring.scorePlayer(s, p.seat);
                t.очков += счёт.getOrDefault("total", 0);
                счёт.forEach((k, v) -> {
                    if (!"total".equals(k) && v != 0) {
                        t.очкиПоИсточникам.merge(k, (long) (int) v, Long::sum);
                    }
                });
                if (s.winner != null && s.winner == p.seat) {
                    t.побед++;
                }
                t.убил += бой[p.seat][0];
                t.ударов += бой[p.seat][0];
                t.боеприпасов += бой[p.seat][1];
                t.потерял += потери[p.seat];
                for (UnitToken u : p.units) {
                    if (u.hexId() != null) {
                        t.войскВсего++;
                        if (u.type.code.equals(старт[p.seat])) {
                            t.войскЗакрытогоРода++;
                        }
                    }
                }
                // Где заглушка лежит сейчас — и лежит ли вообще.
                String сейчас = null;
                for (var e : p.redPlacements.entrySet()) {
                    if (Boolean.TRUE.equals(e.getValue().get("blocks"))) {
                        сейчас = e.getKey().code;
                        break;
                    }
                }
                if (сейчас == null) {
                    t.лишилсяЖетона++;
                } else if (!сейчас.equals(старт[p.seat])) {
                    t.переставил++;
                }
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("# Заглушка: почему проигрывают — разбор по СТАРТОВОМУ роду\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players)
            .append(". Места и характеры ротируются.\n\n");
        b.append("Род берётся из подготовки, а не из конца партии: заглушку можно ")
            .append("переставить обменом на планшете науки или утилем карты, и ")
            .append("считать по конечному месту значит смешивать разные истории.\n\n");

        b.append("| закрыт на старте | игроков | побед | доля побед | очки | ")
            .append("убил | потерял | ударов | БПР | своих войск ЗАКРЫТОГО рода |\n");
        b.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var e : поСтарту.entrySet()) {
            Итог t = e.getValue();
            b.append("| ").append(имя(e.getKey())).append(" | ").append(t.игроков)
                .append(" | ").append(t.побед)
                .append(" | ").append(проц(t.побед, t.игроков))
                .append(" | ").append(окр((double) t.очков / t.игроков))
                .append(" | ").append(окр((double) t.убил / t.игроков))
                .append(" | ").append(окр((double) t.потерял / t.игроков))
                .append(" | ").append(окр((double) t.ударов / t.игроков))
                .append(" | ").append(окр((double) t.боеприпасов / t.игроков))
                .append(" | ").append(окр((double) t.войскЗакрытогоРода / t.игроков))
                .append(" |\n");
        }

        b.append("\n## Двигают ли заглушку вообще\n\n");
        b.append("| закрыт на старте | переставили, раз | доля | лишились (снесли ЦУ) |\n");
        b.append("|---|---:|---:|---:|\n");
        for (var e : поСтарту.entrySet()) {
            Итог t = e.getValue();
            b.append("| ").append(имя(e.getKey())).append(" | ").append(t.переставил)
                .append(" | ").append(проц(t.переставил, t.игроков))
                .append(" | ").append(t.лишилсяЖетона).append(" |\n");
        }

        b.append("\n## Откуда очки у каждого\n\n");
        List<String> источники = new ArrayList<>();
        for (Итог t : поСтарту.values()) {
            for (String k : t.очкиПоИсточникам.keySet()) {
                if (!источники.contains(k)) {
                    источники.add(k);
                }
            }
        }
        b.append("| источник |");
        for (String род : поСтарту.keySet()) {
            b.append(" ").append(имя(род)).append(" |");
        }
        b.append("\n|---|");
        for (int i = 0; i < поСтарту.size(); i++) {
            b.append("---:|");
        }
        b.append("\n");
        for (String ист : источники) {
            b.append("| ").append(ист).append(" |");
            for (Итог t : поСтарту.values()) {
                b.append(" ").append(окр(
                    (double) t.очкиПоИсточникам.getOrDefault(ист, 0L) / t.игроков))
                    .append(" |");
            }
            b.append("\n");
        }

        Path out = Path.of("reports", "balance", "заглушка-разбор-" + ruleset + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static String имя(String code) {
        return switch (code) {
            case "infantry" -> "пехота";
            case "vehicle" -> "техника";
            case "aircraft" -> "авиация";
            case "tower" -> "вышка";
            default -> code;
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
