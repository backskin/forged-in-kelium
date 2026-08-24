package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * СНЕСЛИ ЦУ — ЧТО БЫЛО ДАЛЬШЕ.
 *
 * <p>ВОПРОС ДИЗАЙНЕРА 24.08.2026. Военная победа требует ДВУХ жетонов разрушения
 * ЦУ. Если снесённое ЦУ владелец ставит обратно медленно или не ставит вовсе, то
 * второго ЦУ для сноса на поле физически нет — и военная победа перестаёт быть
 * путём к победе, сколько бы ботов ни учить воевать. Проверяется это ОДНИМ
 * числом: сколько ходов ЦУ лежит в запасе.
 *
 * <p>ЧТО СЧИТАЕТСЯ. Состояние поля проверяется в конце КАЖДОГО хода: стоит ли у
 * игрока ЦУ на поле. Отсюда:
 * <ul>
 *   <li>сколько ходов прошло от сноса до возвращения ЦУ на поле;
 *   <li>сколько сносов так и не были отстроены до конца партии;
 *   <li>какую долю всех ходов игрок провёл вообще без ЦУ на поле;
 *   <li>сколько ЦУ стоит на поле в тот момент, когда у кого-то уже есть жетон
 *       разрушения — то есть есть ли вообще что сносить для военной победы.
 * </ul>
 *
 * <p>Запуск: {@code kelium.ЦУПослеСноса [партий] [игроков] [свод]}
 */
public final class ЦУПослеСноса {

    private ЦУПослеСноса() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 150;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : GameConfig.DEFAULT_RULESET;
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        long сносов = 0;
        long отстроено = 0;
        long такИНеОтстроено = 0;
        List<Integer> задержки = new ArrayList<>();
        long ходовВсего = 0;
        long ходовБезЦУ = 0;
        long военныхПобед = 0;
        Map<Integer, Long> сносовПоРаундам = new TreeMap<>();
        // Сколько ЧУЖИХ ЦУ стояло на поле в момент, когда у игрока уже есть жетон
        // разрушения: это и есть выбор цели для второго сноса.
        List<Integer> целейДляВторого = new ArrayList<>();

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 77000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 191L + g), players));
            }
            // Номер хода, на котором у игрока снесли ЦУ и оно ещё не вернулось.
            int[] ждётСХода = new int[players];
            java.util.Arrays.fill(ждётСХода, -1);
            int[] ходСчёт = {0};
            // Свои ходы каждого игрока: задержка отстройки считается в них.
            int[] своихХодов = new int[players];
            // Ходы, проведённые игроками без своего ЦУ на поле. Считается в
            // ходо-игроках: один ход одного игрока — одна единица.
            long[] безЦУ = {0};

            GameEngine.playGame(s, ags, ev -> {
                String тип = String.valueOf(ev.get("type"));
                if ("cu_destroyed".equals(тип)) {
                    int seat = ((Number) ev.get("seat")).intValue();
                    сносовПоРаундам.merge(((Number) ev.get("round")).intValue(), 1L, Long::sum);
                    ждётСХода[seat] = своихХодов[seat];
                    return;
                }
                if (!"turn_end".equals(тип)) {
                    return;
                }
                ходСчёт[0]++;
                int ходил = ev.get("seat") instanceof Number n ? n.intValue() : -1;
                if (ходил >= 0 && ходил < players) {
                    своихХодов[ходил]++;
                }
                for (int seat = 0; seat < players; seat++) {
                    if (цуНаПоле(s, seat)) {
                        // Вернулось — записываем задержку в СВОИХ ходах владельца.
                        if (ждётСХода[seat] >= 0) {
                            задержки.add(своихХодов[seat] - ждётСХода[seat]);
                            ждётСХода[seat] = -1;
                        }
                    } else if (seat == ходил) {
                        // Ход без ЦУ считается только тому, кто ходил: иначе
                        // одно и то же отсутствие считалось бы по числу игроков.
                        безЦУ[0]++;
                    }
                }
            });

            // Итоги партии
            for (int seat = 0; seat < players; seat++) {
                if (ждётСХода[seat] >= 0) {
                    такИНеОтстроено++;
                }
            }
            if ("military".equals(s.winCondition)) {
                военныхПобед++;
            }
            // Сколько ЦУ стояло на поле к концу партии — грубая мера того, есть
            // ли что сносить вообще.
            int стоит = 0;
            for (int seat = 0; seat < players; seat++) {
                if (цуНаПоле(s, seat)) {
                    стоит++;
                }
            }
            целейДляВторого.add(стоит);
            сносов += сносовВПартии(s);
            ходовВсего += ходСчёт[0];
            ходовБезЦУ += безЦУ[0];
        }
        отстроено = задержки.size();

        StringBuilder b = new StringBuilder();
        b.append("# Снесли ЦУ — что было дальше\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players).append(". Места ротируются.\n\n");
        b.append("| показатель | значение |\n|---|---:|\n");
        b.append("| сносов ЦУ за партию, шт | ")
            .append(окр((double) сносов / games)).append(" |\n");
        b.append("| из них отстроено обратно, шт | ")
            .append(окр((double) отстроено / games)).append(" |\n");
        b.append("| так и не отстроено до конца партии, шт | ")
            .append(окр((double) такИНеОтстроено / games)).append(" |\n");
        b.append("| СВОИХ ходов от сноса до возвращения ЦУ, среднее | ")
            .append(задержки.isEmpty() ? "—" : окр(задержки.stream()
                .mapToInt(Integer::intValue).average().orElse(0))).append(" |\n");
        b.append("| СВОИХ ходов от сноса до возвращения ЦУ, максимум | ")
            .append(задержки.isEmpty() ? "—" : String.valueOf(задержки.stream()
                .mapToInt(Integer::intValue).max().orElse(0))).append(" |\n");
        b.append("| доля ходов, проведённых БЕЗ своего ЦУ на поле | ")
            .append(String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * ходовБезЦУ / Math.max(1, ходовВсего)))
            .append(" |\n");
        b.append("| ЦУ на поле к концу партии, шт из ").append(players).append(" | ")
            .append(окр(целейДляВторого.stream().mapToInt(Integer::intValue)
                .average().orElse(0))).append(" |\n");
        b.append("| военных побед | ").append(военныхПобед).append(" из ")
            .append(games).append(" (")
            .append(String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * военныхПобед / games)).append(") |\n");

        b.append("\n## В каком раунде сносят ЦУ\n\n");
        b.append("| раунд | сносов, шт | на партию |\n|---:|---:|---:|\n");
        for (var e : сносовПоРаундам.entrySet()) {
            b.append("| ").append(e.getKey()).append(" | ").append(e.getValue())
                .append(" | ").append(окр((double) e.getValue() / games)).append(" |\n");
        }

        Path out = Path.of("reports", "balance", "цу-после-сноса-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static long сносовВПартии(GameState s) {
        long n = 0;
        for (var p : s.players) {
            n += p.cuKills;
        }
        return n;
    }

    private static boolean цуНаПоле(GameState s, int seat) {
        for (BuildingToken b : s.player(seat).buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                return true;
            }
        }
        return false;
    }

    private static String окр(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v).replace(',', '.');
    }
}
