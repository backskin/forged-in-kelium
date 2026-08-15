package kelium.report;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.yaml.snakeyaml.Yaml;

/**
 * ASCII-визуализатор раскладок поля — для СВЕРКИ сценариев с печатной доской.
 *
 * <p>Порт из forge/report/render_field.py. Дизайнер не может показать
 * напечатанную доску коду, поэтому мы рисуем каждый сценарий текстом: осевые
 * координаты (q,r), вид гекса и содержимое. Так можно глазами сверить каждый
 * гекс с распечаткой и поправить YAML-сценарий.
 *
 * <p>Обозначения содержимого:
 * <pre>
 *   CU# — стартовый гекс игрока # (сюда ставится ЦУ)
 *   K4/K3 — центральная грядка (лицо/оборот), Ks — стартовая грядка
 *   Kx2 — стопка из 2 грядок (модификатор x2), K+1 — грядка с +1 при подготовке
 *   C / C2 — контейнер(ы)
 *   ### — запрещённый гекс
 *   ... — обычный проходимый гекс
 * </pre>
 */
public final class FieldRenderer {

    private FieldRenderer() {
    }

    @SuppressWarnings("unchecked")
    private static String cellLabel(Map<String, Object> hx) {
        String c = (String) hx.getOrDefault("content", "normal");
        Object mod = hx.get("modifier");
        switch (c) {
            case "player_start":
                return "CU" + hx.getOrDefault("seat", "?");
            case "spawn_start":
                return "Ks";
            case "kelium_tile":
                if ("x2".equals(mod)) {
                    return "Kx2";
                }
                if ("+1".equals(mod)) {
                    return "K+1";
                }
                return "K4";
            case "container": {
                int n = hx.get("count") instanceof Number num ? num.intValue() : 1;
                return n > 1 ? "C" + n : "C";
            }
            case "forbidden":
                return "###";
            case "neutral_building":
                return "NEU";
            default:
                return "...";
        }
    }

    /** Собрать ASCII-картинку раскладки из файла сценария. */
    @SuppressWarnings("unchecked")
    public static String renderScenario(Path path) {
        Map<String, Object> data;
        try (InputStream in = Files.newInputStream(path)) {
            data = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("ошибка чтения " + path + ": " + e.getMessage());
        }
        List<Object> scns = (List<Object>) data.get("scenarios");
        Map<String, Object> scn = (Map<String, Object>) scns.get(0);
        List<Object> hexList = (List<Object>) scn.get("hexes");

        // Индекс гексов по (q,r) в упорядоченной карте для стабильного вывода.
        TreeMap<Long, Map<String, Object>> hexes = new TreeMap<>();
        int minq = Integer.MAX_VALUE, maxq = Integer.MIN_VALUE;
        int minr = Integer.MAX_VALUE, maxr = Integer.MIN_VALUE;
        for (Object o : hexList) {
            Map<String, Object> hx = (Map<String, Object>) o;
            int q = ((Number) hx.get("q")).intValue();
            int r = ((Number) hx.get("r")).intValue();
            hexes.put(key(q, r), hx);
            minq = Math.min(minq, q);
            maxq = Math.max(maxq, q);
            minr = Math.min(minr, r);
            maxr = Math.max(maxr, r);
        }
        if (hexes.isEmpty()) {
            return "(пусто)";
        }

        List<String> lines = new ArrayList<>();
        Map<String, Object> meta = (Map<String, Object>) data.getOrDefault("meta", Map.of());
        boolean needs = Boolean.TRUE.equals(meta.get("_needs_verification"));
        lines.add("=== " + scn.getOrDefault("id", "?") + "  игроков: " + meta.getOrDefault("players", "?")
            + (needs ? "  [ТРЕБУЕТ СВЕРКИ]" : "") + " ===");
        lines.add("гексов: " + hexes.size() + "   (оси q=" + minq + ".." + maxq
            + ", r=" + minr + ".." + maxr + ")");
        lines.add("");
        // Рисуем по строкам r; каждую строку сдвигаем вправо на (r-minr) для гекс-эффекта.
        for (int r = minr; r <= maxr; r++) {
            List<String> rowCells = new ArrayList<>();
            for (int q = minq; q <= maxq; q++) {
                Map<String, Object> hx = hexes.get(key(q, r));
                rowCells.add(hx != null ? String.format("%4s", cellLabel(hx)) : "    ");
            }
            String indent = "  ".repeat(r - minr);
            lines.add(indent + String.join(" ", rowCells));
        }
        lines.add("");
        lines.add("Гексы (q,r -> содержимое):");
        for (Map<String, Object> hx : hexes.values()) {
            int q = ((Number) hx.get("q")).intValue();
            int r = ((Number) hx.get("r")).intValue();
            StringBuilder extra = new StringBuilder();
            if (hx.get("modifier") != null) {
                extra.append(" mod=").append(hx.get("modifier"));
            }
            if (hx.get("count") != null) {
                extra.append(" x").append(hx.get("count"));
            }
            if (hx.get("seat") != null) {
                extra.append(" seat=").append(hx.get("seat"));
            }
            lines.add(String.format("  (%+d,%+d) -> %s%s", q, r,
                hx.getOrDefault("content", "normal"), extra));
        }
        return String.join("\n", lines);
    }

    /** Отрисовать все сценарии (2p/3p/4p) заданной версии из каталога. */
    public static String renderAll(Path scenariosDir, String version) {
        List<String> out = new ArrayList<>();
        for (int n : new int[]{2, 3, 4}) {
            Path f = scenariosDir.resolve("scenario_" + n + "p." + version + ".yaml");
            if (Files.exists(f)) {
                out.add(renderScenario(f));
                out.add("\n" + "=".repeat(60) + "\n");
            }
        }
        return String.join("\n", out);
    }

    // Ключ сортировки по (q,r): q в старших битах, r в младших (как Python sorted).
    private static long key(int q, int r) {
        return (((long) q + 0x40000000L) << 32) | ((long) r + 0x40000000L);
    }
}
