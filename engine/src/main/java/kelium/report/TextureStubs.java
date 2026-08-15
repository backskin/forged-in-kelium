package kelium.report;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import kelium.core.BuildingType;
import kelium.core.UnitType;

/**
 * TextureStubs — ЗАГОТОВКИ ТЕКСТУР ЖЕТОНОВ.
 *
 * <p>Зачем. Приложение рисует жетоны авторскими силуэтами (кривые в
 * {@link FieldGeometry}), но дизайнер хочет натягивать на них свои картинки. Чтобы
 * рисовать было по чему, эта утилита раскладывает по папкам ПУСТЫШКИ: та же форма,
 * тот же размер полотна, та же точка привязки — только залитая цветом места. Поверх
 * такой пустышки можно рисовать текстуру, не думая о геометрии: если размер полотна
 * и пропорции сохранены, в игре картинка сядет ровно на место силуэта.
 *
 * <p>Заготовки НЕ являются требованием: файла нет — рисуется базовая форма и
 * оформление, как сейчас. Поэтому можно принести одну картинку и посмотреть на неё,
 * ничего больше не меняя.
 *
 * <p>Запуск: {@code java -cp <classpath> kelium.report.TextureStubs [папка]}.
 * По умолчанию — {@code <данные игры>/textures}.
 */
public final class TextureStubs {

    private TextureStubs() {
    }

    /** Самая длинная сторона заготовки в пикселях. */
    private static final int SIDE = 512;

    /** Цвета мест — те же, что у жетонов на поле. */
    private static final String[] SEAT = FieldGeometry.SEAT_TOKEN;
    private static final String[] SEAT_EDGE = FieldGeometry.SEAT_STROKE;

    /**
     * ИТОГ ПЕРЕСБОРКИ — что именно произошло с файлами. Нужен, чтобы вызывающий
     * (в том числе кнопка в меню) мог сказать это человеку, а не молча отработать.
     *
     * @param created  файлов не было — положили заготовку
     * @param restored заготовка на месте была, переписали такой же
     * @param kept     НАРИСОВАННЫЕ текстуры, которые не тронули
     * @param replaced нарисованные текстуры, снесённые обратно в заготовку
     */
    public record Result(int created, int restored, int kept, int replaced, Path folder) {
        /** Всего файлов заготовок в папке после пересборки. */
        public int total() {
            return created + restored + kept + replaced;
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 && !args[0].isBlank()
            ? Path.of(args[0])
            : kelium.dataio.GameConfig.resolveDataRoot(null).resolve("textures");
        boolean force = args.length > 1 && "--force".equals(args[1]);
        Result r = generate(root, force);
        System.out.println("создано: " + r.created() + ", восстановлено: " + r.restored()
            + ", сохранено нарисованных: " + r.kept() + ", затёрто: " + r.replaced());
        System.out.println("папка: " + r.folder());
    }

    /**
     * ПЕРЕСОБРАТЬ ЗАГОТОВКИ. Кладёт голые заготовки и заново считает их отпечатки,
     * чтобы приложение опять узнавало «эту картинку ещё не рисовали».
     *
     * <p><b>Нарисованные текстуры по умолчанию не трогаются.</b> Утилита сверяет
     * каждый существующий файл с отпечатком заготовки: совпал — файл никто не
     * рисовал, его можно спокойно переписать; не совпал — это авторская работа, и
     * она остаётся на месте. Иначе одно нажатие кнопки стирало бы всё
     * нарисованное, а восстановить это неоткуда.
     *
     * @param root  папка текстур; {@code null} — взять из каталога данных
     * @param force затирать и нарисованные тоже — осознанный сброс к чистому виду
     */
    public static Result generate(Path root, boolean force) throws IOException {
        if (root == null) {
            root = kelium.dataio.GameConfig.resolveDataRoot(null).resolve("textures");
        }
        counter = new Counter(force);
        Path tokens = root.resolve("token");
        Path guides = root.resolve("_образцы");
        Files.createDirectories(tokens);
        Files.createDirectories(guides);

        int made = 0;
        List<String> lines = new ArrayList<>();
        // ОТПЕЧАТКИ ЗАГОТОВОК: по ним приложение отличает «ещё не рисовали» от
        // настоящей текстуры и не показывает пустышку вместо силуэта.
        List<String> prints = new ArrayList<>();

        // ---- ЗДАНИЯ: у каждого игрока свои. Добытчик и энергостанция ещё и по
        // уровням: на них напечатан номер, значит картинка тоже своя.
        for (BuildingType t : BuildingType.values()) {
            FieldGeometry.Shape sh = FieldGeometry.building(t);
            boolean levels = t == BuildingType.MINER || t == BuildingType.POWER_PLANT;
            boolean source = t == BuildingType.COMMAND_CENTER
                || t == BuildingType.POWER_PLANT;
            for (int seat = 0; seat < 4; seat++) {
                if (levels) {
                    for (int level = 1; level <= 4; level++) {
                        String name = t.code + "_l" + level + "_p" + (seat + 1) + ".png";
                        made += put(tokens, name, stub(sh, seat), prints);
                        made += put(tokens, name.replace(".png", ".zones.png"),
                            maskStub(sh, source), prints);
                    }
                } else {
                    String name = t.code + "_p" + (seat + 1) + ".png";
                    made += put(tokens, name, stub(sh, seat), prints);
                    made += put(tokens, name.replace(".png", ".zones.png"),
                        maskStub(sh, source), prints);
                }
            }
            lines.add("| `" + t.code + "` | здание | "
                + (levels ? "по уровням 1–4 и по местам" : "по местам")
                + " | " + (int) sh.vbW() + "×" + (int) sh.vbH() + " |");
        }

        // ---- ВОЙСКА: тоже по местам (цвет игрока напечатан на жетоне)
        for (UnitType t : UnitType.values()) {
            FieldGeometry.Shape sh = FieldGeometry.unit(t);
            for (int seat = 0; seat < 4; seat++) {
                made += put(tokens, t.code + "_p" + (seat + 1) + ".png",
                    stub(sh, seat), prints);
            }
            lines.add("| `" + t.code + "` | войско | по местам | "
                + (int) sh.vbW() + "×" + (int) sh.vbH() + " |");
        }

        // ---- КАРТОН ПОЛЯ: сам гекс, запретный, тайлы, нейтралы, ячейка контейнера.
        // Заготовка повторяет нынешний вид: её можно открыть и рисовать поверх,
        // ничего не вымеряя. Двойного тайла тут нет — это две картонки одна на
        // другой, приложение рисует их сдвигом (уточнение дизайнера 13.08.2026).
        Path field = Files.createDirectories(root.resolve("field"));
        made += put(field, "hex.png", hexStub("#ffffff", "#8a8778", null, null), prints);
        made += put(field, "hex_forbidden.png",
            hexStub("#3a3a3a", "#111111", "X", "#eeeeee"), prints);
        made += put(field, "spawn.png", hexStub("#2E7D32", "#1B5E20", null, null), prints);
        made += put(field, "spawn_start.png",
            hexStub("#A5D6A7", "#2E5D33", null, null), prints);
        made += put(field, "spawn_flipped.png",
            hexStub("#2E7D32", "#E8A33D", null, null), prints);
        made += put(field, "spawn_start_flipped.png",
            hexStub("#A5D6A7", "#E8A33D", null, null), prints);
        made += put(field, "neutral_small.png", wallStub(1), prints);
        made += put(field, "neutral_big.png", wallStub(2), prints);
        made += put(field, "container.png", containerStub(), prints);
        lines.add("| `field/hex`, `field/hex_forbidden` | картон гекса | одна на всех "
            + "| рамка гекса |");
        lines.add("| `field/spawn`, `_start`, `_flipped`, `_start_flipped` | тайл "
            + "зарождения | по состоянию | рамка гекса |");
        lines.add("| `field/neutral_small`, `field/neutral_big` | нейтральная постройка "
            + "| по размеру | коробка стенки |");
        lines.add("| `field/container` | печатная ячейка контейнера | одна на всех "
            + "| квадрат |");

        // ---- ОБРАЗЦЫ: та же форма, но с разметкой — гекс, центр, направление
        Map<String, FieldGeometry.Shape> shapes = new LinkedHashMap<>();
        for (BuildingType t : BuildingType.values()) {
            shapes.put(t.code, FieldGeometry.building(t));
        }
        for (UnitType t : UnitType.values()) {
            shapes.put(t.code, FieldGeometry.unit(t));
        }
        for (Map.Entry<String, FieldGeometry.Shape> e : shapes.entrySet()) {
            write(guides.resolve(e.getKey() + ".png"), guide(e.getValue(), e.getKey()));
        }

        // СПИСОК ОТПЕЧАТКОВ: приложение сверяет с ним каждую найденную картинку и
        // молча пропускает те, которые так и остались заготовками.
        List<String> manifest = new ArrayList<>();
        manifest.add("# Отпечатки ЗАГОТОВОК (пиксели, SHA-256).");
        manifest.add("# Совпал отпечаток — приложение считает файл нетронутой");
        manifest.add("# заготовкой и рисует обычный векторный жетон.");
        manifest.add("# Файл создаётся утилитой kelium.report.TextureStubs, править руками не надо.");
        manifest.addAll(prints);
        Files.write(root.resolve(Textures.MANIFEST), manifest, StandardCharsets.UTF_8);

        Files.writeString(root.resolve("ЧИТАЙ.md"), readme(lines),
            StandardCharsets.UTF_8);
        // ПАПКА ТЕКСТУР МОГЛА СМЕНИТЬСЯ ИЛИ ОТПЕЧАТКИ ОБНОВИТЬСЯ — заставляем
        // приложение перечитать их, иначе до перезапуска оно продолжит считать
        // заготовками старые файлы.
        Textures.useFolder(root);
        return new Result(counter.created, counter.restored, counter.kept,
            counter.replaced, root.toAbsolutePath());
    }

    /**
     * ЗАГОТОВКА: форма, залитая цветом места, на прозрачном фоне. Полотно точно
     * повторяет рамку формы (её viewBox), поэтому нарисованная поверх текстура
     * сядет в игре на то же место.
     */
    private static BufferedImage stub(FieldGeometry.Shape sh, int seat) {
        double k = SIDE / Math.max(sh.vbW(), sh.vbH());
        int w = (int) Math.round(sh.vbW() * k);
        int h = (int) Math.round(sh.vbH() * k);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(k, k);
        Path2D p = sh.path();
        g.setColor(Color.decode(SEAT[seat % 4]));
        g.fill(p);
        g.setColor(Color.decode(SEAT_EDGE[seat % 4]));
        g.setStroke(new BasicStroke((float) (2.4 / k)));
        g.draw(p);
        g.dispose();
        return img;
    }

    /**
     * ЗАГОТОВКА МАСКИ ЗОН. Показывает контракт: красные квадраты — ячейки под
     * энергию (можно рисовать под любым углом, поворот считается сам), синее пятно —
     * площадь появления энергии у источника, зелёное — место под подпись.
     *
     * <p>Пока маску не правили, приложение её не применяет: энергия и подпись
     * считаются по-старому. Тронул — включилась разметка.
     */
    private static BufferedImage maskStub(FieldGeometry.Shape sh, boolean source) {
        double k = SIDE / Math.max(sh.vbW(), sh.vbH());
        int w = (int) Math.round(sh.vbW() * k);
        int h = (int) Math.round(sh.vbH() * k);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // Сглаживания НЕТ намеренно: маска читается по плоским цветам
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, w, h);

        // КОНТУР САМОГО ЖЕТОНА — светло-серым. Без него на маске было три красных
        // квадрата в пустоте, и понять, где вообще жетон и куда эти квадраты
        // ставить, было невозможно (вопрос дизайнера 13.08.2026). Приложение серый
        // не читает: разметка — это только красный, синий и зелёный, всё
        // остальное на маске можно рисовать как угодно, оно игнорируется.
        java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
        at.scale(k, k);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        java.awt.Shape outline = at.createTransformedShape(sh.path());
        g.setColor(new Color(0xF0, 0xF0, 0xF0));
        g.fill(outline);
        g.setColor(new Color(0xC0, 0xC0, 0xC0));
        g.setStroke(new BasicStroke(3f));
        g.draw(outline);

        // ячейки под энергию — ряд квадратов у внешнего края формы
        int side = Math.max(12, (int) (Math.min(w, h) * 0.16));
        int gap = (int) (side * 0.45);
        int total = 3 * side + 2 * gap;
        int x0 = (w - total) / 2;
        int y0 = (int) (h * 0.62);
        g.setColor(new Color(0xFF, 0x00, 0x00));
        for (int i = 0; i < 3; i++) {
            g.fillRect(x0 + i * (side + gap), y0, side, side);
        }
        // площадь появления энергии — только у источников
        if (source) {
            g.setColor(new Color(0x00, 0x80, 0xFF));
            g.fillRect((int) (w * 0.18), (int) (h * 0.28),
                (int) (w * 0.64), (int) (h * 0.16));
        }
        // место под подпись
        g.setColor(new Color(0x00, 0xC0, 0x00));
        int ls = Math.max(10, side / 2);
        g.fillOval(w / 2 - ls / 2, (int) (h * 0.46) - ls / 2, ls, ls);
        g.dispose();
        return img;
    }

    /**
     * ЗАГОТОВКА ГЕКСОВОЙ КАРТОНКИ. Полотно — рамка гекса «плашмя вверх»: ширина
     * 2·r, высота √3·r. Ровно так его и рисует приложение, поэтому нарисованная
     * поверх картинка сядет без подгонки. (Поле развёрнуто на 30° — см.
     * {@link FieldGeometry#TILT}; заготовки после этого пересозданы.)
     */
    private static BufferedImage hexStub(String fill, String edge, String mark,
                                         String markColour) {
        int w = SIDE;
        int h = (int) Math.round(SIDE * Math.sqrt(3) / 2);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double r = w / 2.0;
        Path2D hex = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60.0 * i - 90 + FieldGeometry.TILT);
            double x = w / 2.0 + r * Math.cos(a);
            double y = h / 2.0 + r * Math.sin(a);
            if (i == 0) {
                hex.moveTo(x, y);
            } else {
                hex.lineTo(x, y);
            }
        }
        hex.closePath();
        g.setColor(Color.decode(fill));
        g.fill(hex);
        g.setColor(Color.decode(edge));
        g.setStroke(new BasicStroke(6f));
        g.draw(hex);
        if (mark != null) {
            g.setColor(Color.decode(markColour));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (h * 0.3)));
            int tw = g.getFontMetrics().stringWidth(mark);
            g.drawString(mark, (float) (w / 2.0 - tw / 2.0), (float) (h / 2.0 + h * 0.1));
        }
        g.dispose();
        return img;
    }

    /**
     * ЗАГОТОВКА СТЕНКИ НЕЙТРАЛА. Полотно — коробка самой стенки: по ширине хорда
     * между крайними углами, по высоте её глубина. Поэтому одна картинка годится для
     * любой стороны гекса — приложение просто поворачивает её.
     */
    private static BufferedImage wallStub(int sides) {
        // НАСТОЯЩАЯ ФОРМА, а не прямоугольник: одна сторона — трапеция, две —
        // две трапеции, слитые боком под углом. Форма берётся из того же кода,
        // которым нейтрал рисуется на поле, поэтому картинка ляжет ровно на него.
        List<Integer> corners = sides == 1 ? List.of(1, 2) : List.of(1, 2, 3);
        double unit = 100;
        double[] box = FieldGeometry.neutralBox(0, 0, unit, corners, 0.86, 0.50);
        double k = SIDE / box[2];
        int w = SIDE;
        int h = Math.max(8, (int) Math.round(box[3] * k));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // переводим форму в оси картинки: вдоль стенки — вправо, наружу — вниз
        double a = Math.toRadians(-box[4]);
        Path2D p = new Path2D.Double();
        double[][] pts = FieldGeometry.neutralShape(0, 0, unit, corners, 0.86, 0.50);
        for (int i = 0; i < pts.length; i++) {
            double dx = pts[i][0] - box[0];
            double dy = pts[i][1] - box[1];
            double rx = dx * Math.cos(a) - dy * Math.sin(a);
            double ry = dx * Math.sin(a) + dy * Math.cos(a);
            double x = w / 2.0 + rx * k;
            double y = h / 2.0 + ry * k;
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        g.setColor(Color.decode(sides == 1 ? "#9AA0A6" : "#7C838B"));
        g.fill(p);
        g.setColor(Color.decode("#33383E"));
        g.setStroke(new BasicStroke(6f));
        g.draw(p);
        g.dispose();
        return img;
    }

    /** ЗАГОТОВКА ПЕЧАТНОЙ ЯЧЕЙКИ КОНТЕЙНЕРА: квадрат со знаком вопроса. */
    private static BufferedImage containerStub() {
        int s = SIDE / 2;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.decode("#F0E2BE"));
        g.fillRect(0, 0, s, s);
        g.setColor(Color.decode("#9A8455"));
        g.setStroke(new BasicStroke(6f));
        g.drawRect(3, 3, s - 6, s - 6);
        g.setColor(Color.decode("#7A6636"));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (s * 0.7)));
        String q = "?";
        int tw = g.getFontMetrics().stringWidth(q);
        g.drawString(q, (float) (s / 2.0 - tw / 2.0), (float) (s * 0.75));
        g.dispose();
        return img;
    }

    /**
     * ОБРАЗЕЦ: то же полотно, но с разметкой — контур формы, точка привязки (центр
     * гекса), направление «наружу» и, для зданий, сам гекс. По образцу понятно, где
     * у формы верх и как она садится на гекс; в игру образцы не идут.
     */
    private static BufferedImage guide(FieldGeometry.Shape sh, String name) {
        // Гекс у здания шире полотна формы (здание занимает лишь его часть), поэтому
        // масштаб образца считается по ВСЕЙ разметке, а не по одному полотну —
        // иначе шестиугольник уходил за край и подсказка не читалась.
        double hexR = sh.seatsOnSectors() ? sh.edgeUnit() / FieldGeometry.BUILDING_SHRINK : 0;
        double minX = Math.min(0, sh.hexCx() - hexR);
        double maxX = Math.max(sh.vbW(), sh.hexCx() + hexR);
        double minY = Math.min(0, sh.hexCy() - hexR);
        double maxY = Math.max(sh.vbH(), sh.hexCy() + hexR);
        double k = SIDE / Math.max(maxX - minX, maxY - minY);
        int w = (int) Math.round((maxX - minX) * k);
        int h = (int) Math.round((maxY - minY) * k);
        int pad = 24;
        int capH = 64;                     // место под подписи снизу
        BufferedImage img = new BufferedImage(w + 2 * pad, h + 2 * pad + capH,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xFFFFFF));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        Graphics2D gs = (Graphics2D) g.create();
        gs.translate(pad, pad + 18);
        gs.scale(k, k);
        gs.translate(-minX, -minY);

        // ГЕКС, на который садится здание: его радиус в единицах формы выводится из
        // edgeUnit — длины стороны гекса в этих же единицах.
        if (sh.seatsOnSectors()) {
            double r = hexR;
            Path2D hex = new Path2D.Double();
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(60.0 * i - 90 + sh.outward() + 90);
                double x = sh.hexCx() + r * Math.cos(a);
                double y = sh.hexCy() + r * Math.sin(a);
                if (i == 0) {
                    hex.moveTo(x, y);
                } else {
                    hex.lineTo(x, y);
                }
            }
            hex.closePath();
            gs.setColor(new Color(0x99, 0x99, 0x99));
            gs.setStroke(new BasicStroke((float) (3 / k), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 10f, new float[]{(float) (18 / k),
                    (float) (14 / k)}, 0));
            gs.draw(hex);
        }

        // рамка полотна — ровно то, что должно быть в PNG текстуры
        gs.setColor(new Color(0xE0, 0x60, 0x60));
        gs.setStroke(new BasicStroke((float) (2 / k)));
        gs.draw(new java.awt.geom.Rectangle2D.Double(0, 0, sh.vbW(), sh.vbH()));

        // сама форма
        Path2D p = sh.path();
        gs.setColor(new Color(0x33, 0x88, 0xCC, 70));
        gs.fill(p);
        gs.setColor(new Color(0x22, 0x44, 0x66));
        gs.setStroke(new BasicStroke((float) (4 / k)));
        gs.draw(p);

        // точка привязки — центр гекса
        gs.setColor(new Color(0xC0, 0x39, 0x2B));
        double cross = Math.max(sh.vbW(), sh.vbH()) * 0.05;
        gs.setStroke(new BasicStroke((float) (5 / k)));
        gs.drawLine((int) (sh.hexCx() - cross), (int) sh.hexCy(),
            (int) (sh.hexCx() + cross), (int) sh.hexCy());
        gs.drawLine((int) sh.hexCx(), (int) (sh.hexCy() - cross),
            (int) sh.hexCx(), (int) (sh.hexCy() + cross));

        // направление «наружу»: куда смотрит жетон, когда стоит на гексе
        double len = Math.max(sh.vbW(), sh.vbH()) * 0.30;
        double a = Math.toRadians(sh.outward());
        double ex = sh.hexCx() + len * Math.cos(a);
        double ey = sh.hexCy() + len * Math.sin(a);
        gs.setColor(new Color(0x2E, 0x7D, 0x32));
        gs.setStroke(new BasicStroke((float) (6 / k), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        gs.drawLine((int) sh.hexCx(), (int) sh.hexCy(), (int) ex, (int) ey);
        AffineTransform old = gs.getTransform();
        gs.translate(ex, ey);
        gs.rotate(a);
        Path2D tip = new Path2D.Double();
        double t = len * 0.16;
        tip.moveTo(0, 0);
        tip.lineTo(-t, -t * 0.55);
        tip.lineTo(-t, t * 0.55);
        tip.closePath();
        gs.fill(tip);
        gs.setTransform(old);
        gs.dispose();

        // подписи — по краю, вне полотна текстуры
        g.setColor(new Color(0x33, 0x33, 0x33));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString(name + "   полотно " + (int) sh.vbW() + "×" + (int) sh.vbH(), 10, 26);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.setColor(new Color(0xC0, 0x39, 0x2B));
        g.drawString("крест — центр гекса, рамка — полотно текстуры",
            10, img.getHeight() - 42);
        g.setColor(new Color(0x2E, 0x7D, 0x32));
        g.drawString("зелёная стрелка — «наружу»: куда жетон смотрит на поле", 10,
            img.getHeight() - 22);
        g.setColor(new Color(0x77, 0x77, 0x77));
        g.drawString("пунктир — гекс, на котором стоит жетон", 10, img.getHeight() - 4);
        g.dispose();
        return img;
    }

    private static void write(Path file, BufferedImage img) throws IOException {
        ImageIO.write(img, "png", file.toFile());
    }

    /**
     * СЧЁТЧИК ТОГО, ЧТО ПРОИСХОДИТ С ФАЙЛАМИ. Живёт на время одной пересборки:
     * {@link #put} вызывается из двух десятков мест, и таскать итог параметром
     * через все них — только зашумлять подписи.
     */
    private static final class Counter {
        final boolean force;
        int created;
        int restored;
        int kept;
        int replaced;

        Counter(boolean force) {
            this.force = force;
        }
    }

    private static Counter counter = new Counter(false);

    /**
     * Положить заготовку и запомнить её отпечаток. Возвращает 1 — так удобно считать
     * сделанное прямо в вызывающем цикле.
     *
     * <p>Отпечаток кладётся в список ВСЕГДА, даже когда файл не переписан: список
     * описывает, как выглядит ЗАГОТОВКА этого имени, а не что лежит на диске
     * сейчас. Без этого нарисованная текстура перестала бы отличаться от
     * заготовки, и приложение начало бы считать её нетронутой.
     */
    private static int put(Path dir, String name, BufferedImage img, List<String> prints)
            throws IOException {
        // КЛЮЧ В СПИСКЕ — ровно тот, по которому картинку потом ищут. Для картона
        // поля это «field/имя.png»: без подпапки отпечаток не находился, и
        // нетронутая заготовка подставлялась как настоящая текстура.
        String folder = dir.getFileName().toString();
        String key = "token".equals(folder) ? name : folder + "/" + name;
        prints.add(key + " " + Textures.fingerprint(img));

        Path file = dir.resolve(name);
        if (!Files.exists(file)) {
            write(file, img);
            counter.created++;
            return 1;
        }
        boolean drawn = isDrawnOver(file, img);
        if (drawn && !counter.force) {
            counter.kept++;                 // авторская работа — не трогаем
            return 1;
        }
        write(file, img);
        if (drawn) {
            counter.replaced++;
        } else {
            counter.restored++;
        }
        return 1;
    }

    /**
     * НАРИСОВАЛИ ЛИ ПОВЕРХ ЭТОГО ФАЙЛА. Сравнивается с заготовкой, которую мы
     * только что построили: пиксели совпали — файл так и остался пустым.
     * Нечитаемый файл считаем нарисованным: молча затирать то, что не смогли
     * прочитать, нельзя.
     */
    private static boolean isDrawnOver(Path file, BufferedImage stub) {
        try {
            BufferedImage cur = ImageIO.read(file.toFile());
            return cur == null
                || !Textures.fingerprint(cur).equals(Textures.fingerprint(stub));
        } catch (IOException e) {
            return true;
        }
    }

    private static String readme(List<String> rows) {
        return """
            # Текстуры жетонов

            Приложение рисует жетоны авторскими силуэтами (кривые в коде). Если рядом
            лежит картинка, оно нарисует её вместо силуэта. **Картинки нет — рисуется
            базовая форма и оформление, как раньше.** Поэтому можно принести один файл
            и посмотреть на него, ничего больше не меняя.

            ## Где лежит и как называется

            Папка `token/`. Имя собирается из кода жетона, номера уровня и номера места:

            ```
            token/<код>_l<уровень>_p<место>.png   самое точное
            token/<код>_p<место>.png
            token/<код>_l<уровень>.png
            token/<код>.png                        общая на всех
            ```

            Ищется от самого точного к общему; что нашлось первым, то и рисуется.
            **Здания у каждого игрока свои**, поэтому заготовки разложены по местам
            (`_p1`…`_p4`); у добытчика и энергостанции ещё и по уровням (`_l1`…`_l4`) —
            на них напечатан номер.

            ## Что должно быть в файле

            - PNG с прозрачностью;
            - **пропорции полотна — как у заготовки** (см. таблицу ниже). Размер в
              пикселях можно любой, лишь бы соотношение сторон совпадало: картинка
              растягивается на ту же рамку, в которой живёт силуэт;
            - рисунок занимает то же место в полотне, что и силуэт заготовки. Проще
              всего: открыть заготовку из `token/`, положить её нижним слоем и рисовать
              поверх;
            - никаких подписей и цифр рисовать не надо — код здания, уровень, кубики
              урона и энергии приложение подписывает само поверх текстуры.

            ## Как жетон стоит на поле

            В папке `_образцы/` на каждую форму лежит картинка с разметкой:

            - **красный крест** — точка привязки, то есть центр гекса. Вокруг неё
              жетон поворачивается;
            - **зелёная стрелка** — направление «наружу»: куда жетон смотрит, когда
              стоит на своей стенке гекса;
            - **пунктирный шестиугольник** (у зданий) — сам гекс: видно, какую его
              часть здание занимает;
            - **красная рамка** — границы полотна текстуры.

            Образцы нужны только для рисования, в игру они не идут.

            ## Маски зон: файлы `*.zones.png`

            Рядом с каждой заготовкой жетона лежит ВТОРОЙ файл — `<имя>.zones.png`.
            Это НЕ картинка жетона, а служебная разметка: где приложение ставит свои
            значки поверх твоей текстуры. Полотно у неё то же самое, что у текстуры.

            Читаются ровно три плоских цвета:

            - **красный** `#FF0000` — ЯЧЕЙКИ ПОД КУБИК ЭНЕРГИИ. Каждая ячейка — свой
              квадрат. Квадратов рисуй столько, сколько их напечатано на этом жетоне,
              и ставь где угодно: под любым углом, приложение само посчитает поворот
              и впишет в каждый кубик;
            - **синий** `#0080FF` — ЗОНА СВОБОДНОЙ ЭНЕРГИИ (только ЦУ и энергостанция):
              пятно любой формы, свободные кубики лягут внутри него;
            - **зелёный** `#00C000` — МЕСТО ПОД ПОДПИСЬ: там приложение напишет код
              здания с уровнем и поставит сердечко прочности.

            Всё остальное на маске приложение ИГНОРИРУЕТ. Поэтому на заготовке светло-
            серым нарисован сам силуэт жетона — просто чтобы было видно, где он и куда
            ставить пятна; стирать его не нужно. Сглаживание не используй: цвета должны
            быть плоскими, полупрозрачные края не читаются.

            **Пока маску не трогали, она не действует.** У каждой заготовки есть
            цифровая подпись (`_заготовки.txt`); совпала — файл считается пустой
            заготовкой, и ячейки с подписью расставляются по-старому, расчётом. Как
            только ты нарисовал в ней хоть что-то — включается твоя разметка.

            ## Заготовки

            | код | что это | варианты | полотно |
            |---|---|---|---|
            %s

            Пересобрать заготовки после правки силуэтов:
            `java -cp <classpath> kelium.report.TextureStubs`
            """.formatted(String.join("\n", rows));
    }
}
