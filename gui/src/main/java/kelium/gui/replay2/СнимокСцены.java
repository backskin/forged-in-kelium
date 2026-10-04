package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.yaml.snakeyaml.Yaml;

import kelium.dataio.GameConfig;
import kelium.report.FieldGeometry;
import kelium.report.FieldPainter;
import kelium.report.Java2DCanvas;
import kelium.report.ReplayRecord;
import kelium.report.Textures;

/**
 * СНИМОК СЦЕНЫ ДЛЯ КНИГИ ПРАВИЛ — кусок поля по описанию в YAML (04.10.2026).
 *
 * <p>Примеры книги («последний келемий снят — тайл уходит», «здание
 * уничтожено — сектор свободен») рисуются тем же {@link FieldPainter}, что
 * и настоящая партия, но описываются данными, а не отдельным классом на
 * каждый пример. Описание:
 * <pre>
 * size: 300                       # радиус гекса, точек
 * hexes:
 *   - {q: 0, r: 0, spawn: {kelium: 1, start: false, flipped: true}}
 *   - {q: 1, r: 0, neutral: {big: true, corners: [1, 2, 3], hp: 2}}
 * tokens:
 *   - {type: miner, owner: 0, q: 1, r: -1, building: true, sides: [3, 4], level: 2,
 *      energySlots: 2, energyPlaced: 2}
 *   - {type: infantry, owner: 1, q: 0, r: 0, damage: 1, sides: [0]}
 * outline: [{q: 0, r: 0, color: "#B03A2E"}]      # пунктир вокруг гекса
 * arrows:  [{path: [[1, -1], [0, 0]], color: "#1F5FA8", label: "1"}]
 * crosses: [{q: 0, r: 0}]                         # крест: «этого больше нет»
 * </pre>
 *
 * <p>Запуск: {@code java -cp gui/target/kelium-runner.jar
 * kelium.gui.replay2.СнимокСцены <сцена.yaml> <куда.png>}
 */
public final class СнимокСцены {

    private СнимокСцены() {
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Theme.apply(false);
        Map<String, Object> сц = new Yaml().load(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
        Path out = Path.of(args[1]);
        double size = ((Number) сц.getOrDefault("size", 300)).doubleValue();
        Textures.useFolder(GameConfig.resolveDataRoot(null).resolve("textures"));

        List<int[]> клетки = new ArrayList<>();
        Map<String, ReplayRecord.HexState> состояния = new LinkedHashMap<>();
        for (Map<String, Object> h : (List<Map<String, Object>>) сц.get("hexes")) {
            int q = ((Number) h.get("q")).intValue();
            int r = ((Number) h.get("r")).intValue();
            клетки.add(new int[]{q, r});
            ReplayRecord.HexState st = new ReplayRecord.HexState();
            st.id = id(q, r);
            st.energyCell = ((Number) h.getOrDefault("energyCell", -1)).intValue();
            st.containerCell = ((Number) h.getOrDefault("containerCell", -1)).intValue();
            if (h.get("spawn") instanceof Map<?, ?> sp) {
                ReplayRecord.Spawn s = new ReplayRecord.Spawn();
                s.start = Boolean.TRUE.equals(sp.get("start"));
                s.flipped = Boolean.TRUE.equals(sp.get("flipped"));
                s.kelium = ((Number) ((Map<String, Object>) sp).getOrDefault("kelium", 0)).intValue();
                st.spawn = s;
            }
            if (h.get("neutral") instanceof Map<?, ?> nm) {
                ReplayRecord.Neutral n = new ReplayRecord.Neutral();
                n.big = Boolean.TRUE.equals(nm.get("big"));
                for (Object c : (List<Object>) nm.get("corners")) {
                    n.corners.add(((Number) c).intValue());
                }
                n.hpMax = n.big ? 2 : 1;
                n.hp = ((Number) ((Map<String, Object>) nm).getOrDefault("hp", n.hpMax)).intValue();
                st.neutrals.add(n);
            }
            состояния.put(st.id, st);
        }
        List<ReplayRecord.Tok> жетоны = new ArrayList<>();
        int uid = 1;
        Object спТ = сц.get("tokens");
        for (Map<String, Object> t : спТ == null ? List.<Map<String, Object>>of()
                : (List<Map<String, Object>>) спТ) {
            ReplayRecord.Tok tk = new ReplayRecord.Tok();
            tk.uid = uid++;
            tk.owner = ((Number) t.getOrDefault("owner", 0)).intValue();
            tk.type = (String) t.get("type");
            tk.building = Boolean.TRUE.equals(t.get("building"));
            tk.hexId = id(((Number) t.get("q")).intValue(), ((Number) t.get("r")).intValue());
            tk.damage = ((Number) t.getOrDefault("damage", 0)).intValue();
            tk.hp = ((Number) t.getOrDefault("hp", 1)).intValue();
            tk.energySlots = ((Number) t.getOrDefault("energySlots", 0)).intValue();
            tk.energyPlaced = ((Number) t.getOrDefault("energyPlaced", 0)).intValue();
            tk.energyIdle = ((Number) t.getOrDefault("energyIdle", 0)).intValue();
            if (t.get("level") instanceof Number n) {
                tk.level = n.intValue();
            }
            if (t.get("sides") instanceof List<?> sides) {
                tk.sides = new ArrayList<>();
                for (Object s : sides) {
                    tk.sides.add(((Number) s).intValue());
                }
                if (tk.building) {
                    ReplayRecord.HexState st = состояния.get(tk.hexId);
                    for (int s : tk.sides) {
                        st.sideOwner[s] = tk.uid;
                    }
                }
            }
            жетоны.add(tk);
        }

        double minx = Double.MAX_VALUE, miny = Double.MAX_VALUE;
        double maxx = -Double.MAX_VALUE, maxy = -Double.MAX_VALUE;
        for (int[] qr : клетки) {
            double[] c = FieldGeometry.hexCenter(qr[0], qr[1], size);
            minx = Math.min(minx, c[0] - size);
            maxx = Math.max(maxx, c[0] + size);
            miny = Math.min(miny, c[1] - Math.sqrt(3) / 2 * size);
            maxy = Math.max(maxy, c[1] + Math.sqrt(3) / 2 * size);
        }
        int поле = (int) Math.round(size * 0.3);
        BufferedImage img = new BufferedImage((int) Math.round(maxx - minx) + поле * 2,
            (int) Math.round(maxy - miny) + поле * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        FieldPainter.dark = false;
        FieldPainter.showCardboard = false;
        Font мелкий = new Font("Tektur Narrow", Font.PLAIN, (int) Math.round(size * 0.185));
        double cx0 = поле - minx;
        double cy0 = поле - miny;
        for (int[] qr : клетки) {
            String id = id(qr[0], qr[1]);
            ReplayRecord.HexInfo hi = new ReplayRecord.HexInfo();
            hi.id = id;
            hi.q = qr[0];
            hi.r = qr[1];
            double[] c = FieldGeometry.hexCenter(qr[0], qr[1], size);
            boolean[] соседи = new boolean[6];
            for (int s = 0; s < 6; s++) {
                int[] d = kelium.core.Field.AXIAL_DIRS[s];
                соседи[s] = состояния.containsKey(id(qr[0] + d[0], qr[1] + d[1]));
            }
            List<ReplayRecord.Tok> свои = new ArrayList<>();
            for (ReplayRecord.Tok tk : жетоны) {
                if (id.equals(tk.hexId)) {
                    свои.add(tk);
                }
            }
            FieldPainter.paintHex(new Java2DCanvas(g, 1, мелкий), size, hi, состояния.get(id), свои,
                cx0 + c[0], cy0 + c[1], false, соседи);
        }
        for (Map<String, Object> o : список(сц, "outline")) {
            double[] c = центр(o, size);
            обвести(g, cx0 + c[0], cy0 + c[1], size, цвет(o, "#B03A2E"));
        }
        for (Map<String, Object> o : список(сц, "crosses")) {
            double[] c = центр(o, size);
            крест(g, cx0 + c[0], cy0 + c[1], size, цвет(o, "#B03A2E"));
        }
        for (Map<String, Object> a : список(сц, "arrows")) {
            List<List<Number>> путь = (List<List<Number>>) a.get("path");
            double[][] т = new double[путь.size()][];
            for (int i = 0; i < путь.size(); i++) {
                double[] c = FieldGeometry.hexCenter(путь.get(i).get(0).intValue(),
                    путь.get(i).get(1).intValue(), size);
                т[i] = new double[]{cx0 + c[0], cy0 + c[1]};
            }
            стрелка(g, т, size, цвет(a, "#1F5FA8"), (String) a.get("label"),
                ((Number) a.getOrDefault("trim", 0.42)).doubleValue());
        }
        g.dispose();
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        ImageIO.write(обрезать(img), "png", out.toFile());
        System.out.println("[сцена] " + out.toAbsolutePath());
    }

    private static String id(int q, int r) {
        return "h" + q + "_" + r;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> список(Map<String, Object> сц, String ключ) {
        Object o = сц.get(ключ);
        return o == null ? List.of() : (List<Map<String, Object>>) o;
    }

    private static double[] центр(Map<String, Object> o, double size) {
        return FieldGeometry.hexCenter(((Number) o.get("q")).intValue(),
            ((Number) o.get("r")).intValue(), size);
    }

    private static Color цвет(Map<String, Object> o, String дефолт) {
        return Color.decode((String) o.getOrDefault("color", дефолт));
    }

    private static void обвести(Graphics2D g, double cx, double cy, double size, Color цвет) {
        Path2D ш = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60.0 * i);
            double x = cx + size * 0.94 * Math.cos(a);
            double y = cy + size * 0.94 * Math.sin(a);
            if (i == 0) {
                ш.moveTo(x, y);
            } else {
                ш.lineTo(x, y);
            }
        }
        ш.closePath();
        g.setStroke(new BasicStroke((float) (size * 0.055), BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND, 10f, new float[]{(float) (size * 0.17), (float) (size * 0.11)}, 0f));
        g.setColor(цвет);
        g.draw(ш);
    }

    private static void крест(Graphics2D g, double cx, double cy, double size, Color цвет) {
        double r = size * 0.42;
        g.setStroke(new BasicStroke((float) (size * 0.09), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(цвет);
        g.drawLine((int) (cx - r), (int) (cy - r), (int) (cx + r), (int) (cy + r));
        g.drawLine((int) (cx - r), (int) (cy + r), (int) (cx + r), (int) (cy - r));
    }

    private static void стрелка(Graphics2D g, double[][] т, double size, Color цвет, String номер,
            double доляОтступа) {
        double отступ = size * доляОтступа;
        double[] a = сдвиг(т[0], т[1], отступ);
        double[] z = сдвиг(т[т.length - 1], т[т.length - 2], отступ);
        Path2D линия = new Path2D.Double();
        линия.moveTo(a[0], a[1]);
        for (int i = 1; i < т.length - 1; i++) {
            линия.lineTo(т[i][0], т[i][1]);
        }
        double[] пред = т.length > 2 ? т[т.length - 2] : a;
        double dx = z[0] - пред[0];
        double dy = z[1] - пред[1];
        double len = Math.hypot(dx, dy);
        double ux = dx / len;
        double uy = dy / len;
        double остриё = size * 0.26;
        линия.lineTo(z[0] - ux * остриё * 0.5, z[1] - uy * остриё * 0.5);
        g.setStroke(new BasicStroke((float) (size * 0.075), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(цвет);
        g.draw(линия);
        Path2D нос = new Path2D.Double();
        нос.moveTo(z[0], z[1]);
        нос.lineTo(z[0] - ux * остриё + uy * остриё * 0.55, z[1] - uy * остриё - ux * остриё * 0.55);
        нос.lineTo(z[0] - ux * остриё - uy * остриё * 0.55, z[1] - uy * остриё + ux * остриё * 0.55);
        нос.closePath();
        g.fill(нос);
        if (номер != null) {
            double r = size * 0.15;
            g.fill(new Ellipse2D.Double(a[0] - r, a[1] - r, 2 * r, 2 * r));
            g.setColor(Color.WHITE);
            g.setFont(new Font("Tektur Narrow", Font.BOLD, (int) Math.round(size * 0.22)));
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.drawString(номер, (float) (a[0] - fm.stringWidth(номер) / 2.0),
                (float) (a[1] + fm.getAscent() / 2.0 - fm.getDescent() / 2.0));
        }
    }

    private static double[] сдвиг(double[] от, double[] к, double на) {
        double dx = к[0] - от[0];
        double dy = к[1] - от[1];
        double len = Math.hypot(dx, dy);
        return new double[]{от[0] + dx / len * на, от[1] + dy / len * на};
    }

    private static BufferedImage обрезать(BufferedImage im) {
        int x0 = im.getWidth(), y0 = im.getHeight(), x1 = 0, y1 = 0;
        for (int y = 0; y < im.getHeight(); y++) {
            for (int x = 0; x < im.getWidth(); x++) {
                if ((im.getRGB(x, y) >>> 24) > 8) {
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                    y0 = Math.min(y0, y);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (x1 <= x0 || y1 <= y0) {
            return im;
        }
        int п = 8;
        x0 = Math.max(0, x0 - п);
        y0 = Math.max(0, y0 - п);
        x1 = Math.min(im.getWidth() - 1, x1 + п);
        y1 = Math.min(im.getHeight() - 1, y1 + п);
        return im.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }
}
