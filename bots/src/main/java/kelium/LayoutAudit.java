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
import java.util.TreeMap;

import kelium.core.Field;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.dataio.GameConfig;
import kelium.engine.LayoutLibrary;

/**
 * РЕВИЗИЯ БИБЛИОТЕКИ ПОЛЕЙ: дубли, поломанные раскладки, перекосы.
 *
 * <p>Зачем. Раскладки копятся: авторские, нарисованные конструктором, мои
 * подобранные. Метрики двух полей совпали до последнего числа — значит одна
 * раскладка, скорее всего, лежит в библиотеке ДВАЖДЫ. Для обучения это не
 * безобидно: партии обучения берут раскладку по сиду из всей библиотеки, и
 * дубль получает двойной вес. Для дизайнера — просто лишняя строка в выборе.
 *
 * <p>Отдельно ищутся поля, на которых партия не может нормально идти: главное —
 * мало тайлов зарождения на игрока (партия кончается «по последнему тайлу» через
 * пару раундов) и сильно неравные старты.
 *
 * <p>Запуск: {@code kelium.LayoutAudit [игроков...]} (по умолчанию 2 3 4).
 */
public final class LayoutAudit {

    private LayoutAudit() {
    }

    /**
     * ОТПЕЧАТОК ГЕОМЕТРИИ: из чего поле состоит и как расставлено. Два поля с
     * одинаковым отпечатком — одно и то же поле, как бы оно ни называлось.
     */
    private static String fingerprint(Field f) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Hex> e : new TreeMap<>(f.hexes).entrySet()) {
            Hex h = e.getValue();
            StringBuilder sb = new StringBuilder(e.getKey()).append(':');
            sb.append(h.kind == null ? "?" : h.kind.name().charAt(0));
            if (h.spawnTile != null) {
                sb.append("S").append(h.spawnTile.isStart ? "start" : "norm")
                  .append(h.spawnTile.faceKelium).append('/')
                  .append(h.spawnTile.backKelium).append('x').append(h.spawnTile.stack);
            }
            if (h.hasNeutral()) {
                sb.append("N").append(h.neutrals.size());
            }
            parts.add(sb.toString());
        }
        return String.join("|", parts);
    }

    private record Info(String id, String folder, Path file, int hexes, int spawnTiles,
                        int neutrals, String fingerprint) {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int[] seats = args.length > 0 ? new int[args.length] : new int[]{2, 3, 4};
        for (int i = 0; i < args.length; i++) {
            seats[i] = Integer.parseInt(args[i]);
        }

        StringBuilder md = new StringBuilder();
        md.append("# Ревизия библиотеки полей\n\n");
        md.append("Папки библиотеки: ").append(LayoutLibrary.folders()).append("\n\n");

        for (int players : seats) {
            List<String> problems = new ArrayList<>();
            List<LayoutLibrary.Entry> entries = LayoutLibrary.scan(players, problems);
            List<Info> infos = new ArrayList<>();
            for (LayoutLibrary.Entry e : entries) {
                GameConfig base = GameConfig.buildCached(GameConfig.DEFAULT_RULESET,
                    players, 1L, null, null);
                GameConfig cfg = new GameConfig(base.ruleset, base.content, players, 1L,
                    base.dataRoot, base.boardSides, e.id(), base.cuFacing, e.file());
                // Поле берём через обычную сборку партии: так же, как его получит
                // настоящий прогон, — иначе можно проверить не то, что играется.
                Field f;
                try {
                    f = kelium.engine.Setup.buildGame(cfg).field;
                } catch (RuntimeException ex) {
                    problems.add(e.id() + ": не читается — " + ex.getMessage());
                    continue;
                }
                int spawn = 0;
                int neutrals = 0;
                for (Hex h : f.hexes.values()) {
                    if (h.spawnTile != null) {
                        spawn += Math.max(1, h.spawnTile.stack);
                    }
                    neutrals += h.neutrals.size();
                }
                infos.add(new Info(e.id(), e.folder(), e.file(), f.hexes.size(), spawn,
                    neutrals, fingerprint(f)));
            }

            md.append("## ").append(players).append(" игрока: раскладок ")
              .append(infos.size()).append("\n\n");

            // ---- ДУБЛИ по отпечатку геометрии
            Map<String, List<Info>> byPrint = new LinkedHashMap<>();
            for (Info i : infos) {
                byPrint.computeIfAbsent(i.fingerprint(), k -> new ArrayList<>()).add(i);
            }
            boolean anyDup = false;
            for (List<Info> group : byPrint.values()) {
                if (group.size() < 2) {
                    continue;
                }
                if (!anyDup) {
                    md.append("### Дубли — одна и та же геометрия под разными именами\n\n");
                    anyDup = true;
                }
                List<String> names = new ArrayList<>();
                for (Info i : group) {
                    names.add(i.id() + " (" + i.folder() + ")");
                }
                md.append("- ").append(String.join("  ==  ", names)).append('\n');
            }
            if (!anyDup) {
                md.append("Дублей нет.\n");
            }
            md.append('\n');

            // ---- ПОЛОМАННЫЕ: слишком короткая партия
            md.append("### Тайлы зарождения на игрока — длина партии\n\n");
            md.append("| раскладка | папка | гексов | тайлов (со стопками) | на игрока | нейтралов |\n");
            md.append("|---|---|---:|---:|---:|---:|\n");
            infos.sort((a, b) -> Double.compare(
                (double) a.spawnTiles() / players, (double) b.spawnTiles() / players));
            for (Info i : infos) {
                double per = (double) i.spawnTiles() / players;
                md.append(String.format(Locale.ROOT, "| %s%s | %s | %d | %d | %.2f | %d |%n",
                    per < 1.5 ? "**" : "", i.id() + (per < 1.5 ? "** ⚠" : ""), i.folder(),
                    i.hexes(), i.spawnTiles(), per, i.neutrals()));
            }
            md.append("\nЖирным помечены раскладки, где на игрока меньше 1.5 тайла: партия "
                + "кончается «по последнему тайлу» через два-три раунда, и половина игры "
                + "не успевает случиться.\n\n");

            if (!problems.isEmpty()) {
                md.append("### Замечания разбора\n\n");
                for (String p : problems) {
                    md.append("- ").append(p).append('\n');
                }
                md.append('\n');
            }
        }

        Path file = Path.of("reports/balance/ревизия-полей.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
        out.println(md);
        out.println("записано: " + file.toAbsolutePath());
    }
}
