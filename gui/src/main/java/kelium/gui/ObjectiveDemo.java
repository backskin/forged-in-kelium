package kelium.gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import kelium.dataio.GameConfig;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.ObjectiveCard;
import kelium.report.ReplayRecord;

/**
 * ДЕМОНСТРАЦИЯ ВЫПОЛНЕННЫХ ЗАДАНИЙ — по картинке на каждую карту (заказ
 * дизайнера 20.08.2026).
 *
 * <p>ЗАЧЕМ. Про карты-фигуры нельзя понять по тексту, совпадает ли то, что
 * ТРЕБУЕТ карта, с тем, что движок считает выполнением. Замер говорит только
 * «выполнено 0 раз», но не отвечает, невыполнима карта или выполняется не тем,
 * что нарисовано. Поэтому инструмент ищет в настоящих партиях МОМЕНТ ВЫПОЛНЕНИЯ
 * каждой карты, снимает поле ровно на этом кадре и кладёт рядом текст
 * требования. Дизайнер сравнивает картинку с текстом и говорит, сходится ли.
 *
 * <p>КАРТА ОПОЗНАЁТСЯ ПО ИМЕНИ ИЗ СТРОКИ ЛОГА. Кадр записи хранит тип события и
 * человеческую строку, но не идентификатор карты, поэтому имя вынимается из
 * кавычек и переводится в id по каталогу. Это единственное слабое место
 * инструмента, и оно проверяемое: карты, для которых имя не разошлось, будут
 * названы в итоговом отчёте.
 *
 * <p>Запуск: {@code java kelium.gui.ObjectiveDemo [сколько партий]}. Складывает
 * всё в {@code reports/demo-заданий/}.
 */
public final class ObjectiveDemo {

    private static final int W = 1100;
    private static final int H = 820;
    /** Из строки лога: ЗАДАНИЕ. Игрок … выполнил задание «ИМЯ» … */
    private static final Pattern NAME = Pattern.compile("«([^»]+)»");

    private ObjectiveDemo() {
    }

    public static void main(String[] args) throws IOException {
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 120;
        Path dir = Path.of("reports", "demo-заданий");
        Files.createDirectories(dir);

        Map<String, String> byName = namesToIds();
        Set<String> done = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();

        for (int g = 0; g < games; g++) {
            long seed = 5000L + g * 37L;
            ReplayRecord rec = GameRecorder.play(GameConfig.DEFAULT_RULESET, 4, seed,
                // Только играющие на победу и по-разному: у прибора обстановка на
                // поле нетипичная, и снимок с неё сбивал бы с толку.
                List.of("trained:quester", "trained:balanced", "search:balanced",
                    "trained:hawk"), null);
            for (int i = 0; i < rec.frames.size(); i++) {
                ReplayRecord.Frame f = rec.frames.get(i);
                if (!"objective".equals(f.type) || f.log == null) {
                    continue;
                }
                Matcher m = NAME.matcher(f.log);
                if (!m.find()) {
                    continue;
                }
                String id = byName.get(m.group(1));
                if (id == null || !done.add(id)) {
                    continue;
                }
                String base = id + " — " + m.group(1).replace('/', '-');
                shoot(rec, i, dir.resolve(base + ".png"));
                Files.writeString(dir.resolve(base + ".txt"), describe(id, m.group(1), rec, i),
                    StandardCharsets.UTF_8);
                lines.add(base);
            }
            if (done.size() >= byName.size()) {
                break;
            }
        }

        // ОТЧЁТ ЧЕСТНЫЙ: кого снять не удалось — тоже результат, и именно он
        // показывает, какие карты не выполняются вовсе.
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : byName.entrySet()) {
            if (!done.contains(e.getValue())) {
                missing.add(e.getValue() + " «" + e.getKey() + "»");
            }
        }
        StringBuilder report = new StringBuilder();
        report.append("ДЕМОНСТРАЦИЯ ВЫПОЛНЕННЫХ ЗАДАНИЙ\n");
        report.append("Партий сыграно: ").append(games).append("\n");
        report.append("Снято карт: ").append(done.size()).append(" из ")
            .append(byName.size()).append("\n\n");
        report.append("СНЯТО:\n");
        for (String s : lines) {
            report.append("  ").append(s).append("\n");
        }
        report.append("\nНЕ ВЫПОЛНИЛИСЬ НИ РАЗУ (снимка нет):\n");
        for (String s : missing) {
            report.append("  ").append(s).append("\n");
        }
        Files.writeString(dir.resolve("ОТЧЁТ.txt"), report.toString(), StandardCharsets.UTF_8);
        System.out.println("снято " + done.size() + " из " + byName.size()
            + ", папка: " + dir.toAbsolutePath());
        System.out.println("без снимка: " + missing.size());
    }

    /** Снять поле на кадре {@code idx} в файл. */
    private static void shoot(ReplayRecord rec, int idx, Path file) throws IOException {
        FieldView view = new FieldView();
        view.setRecord(rec);
        view.setSize(W, H);
        view.setFrame(rec.frames.get(idx));
        view.fitToWindow();
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        view.paint(g);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }

    /**
     * Текст рядом со снимком: ЧТО КАРТА ТРЕБУЕТ — словами самой карты.
     *
     * <p>Берётся из класса карты, а не из YAML: класс — источник правды, и
     * сравнивать картинку надо именно с тем, что напечатано на карте.
     */
    private static String describe(String id, String name, ReplayRecord rec, int idx) {
        StringBuilder b = new StringBuilder();
        b.append("КАРТА ").append(id).append(" «").append(name).append("»\n");
        b.append("=".repeat(70)).append("\n\n");
        ObjectiveCard oc = CardRegistry.objective(id);
        if (oc instanceof kelium.cards.objectives.ЗаданиеВКоде з) {
            var л = з.лицо();
            b.append("ТРЕБОВАНИЕ:\n  ").append(л.условие()).append("\n\n");
            b.append("УСИЛЕНИЕ:\n  ")
                .append(л.усиление() == null ? "нет" : л.усиление()).append("\n\n");
            b.append("ВЕРХ (сжигание):\n  ")
                .append(л.верх() == null ? "нет" : л.верх()).append("\n\n");
            b.append("ПЕЧАТНЫЙ ТЕКСТ КАРТЫ:\n  ").append(л.описание()).append("\n\n");
        } else {
            b.append("(карта живёт не классом — текста требования нет)\n\n");
        }
        b.append("СНИМОК СДЕЛАН:\n");
        ReplayRecord.Frame f = rec.frames.get(idx);
        b.append("  партия: свод ").append(rec.ruleset)
            .append(", сид ").append(rec.seed).append(", игроков ").append(rec.players).append("\n");
        b.append("  шаг ").append(idx).append(" из ").append(rec.frames.size())
            .append(", раунд ").append(f.round).append(", круг ").append(f.circle).append("\n");
        b.append("  событие: ").append(f.log).append("\n");
        b.append("\nЧТО СРАВНИТЬ: видно ли на снимке ту фигуру (или то состояние),\n");
        b.append("которое требует карта. Если нет — движок считает выполнением не то,\n");
        b.append("что написано на карте.\n");
        return b.toString();
    }

    /** Имя карты → её id: кадр записи хранит только имя. */
    private static Map<String, String> namesToIds() {
        Map<String, String> out = new LinkedHashMap<>();
        for (var c : CardRegistry.all()) {
            if (c instanceof kelium.cards.objectives.ЗаданиеВКоде з) {
                out.put(з.лицо().имя(), з.id());
            }
        }
        return out;
    }
}
