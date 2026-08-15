package kelium.gui.replay2;

import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import kelium.dataio.AppSettings;
import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.dataio.Locations;

/**
 * SettingsDialog — ОКНО НАСТРОЕК ПРИЛОЖЕНИЯ: где лежат данные, где раскладки, где
 * память ботов, какой масштаб, и — главное — ЧТО ИЗ ЭТОГО СЕЙЧАС НЕ НА МЕСТЕ.
 *
 * <p>Заказ дизайнера 14.08.2026, и повод был неприятный. Приложение открылось
 * пустым: ни полей, ни версий правил, ни описаний карт. Понять причину из окна
 * было нельзя — оно просто молчало. А причина оказалась внешняя: из папки данных
 * пропал один файл ({@code boards/boards.1.0.0.yaml}), после чего движок не мог
 * собрать НИ ОДНОЙ версии правил и всё разом становилось пустым.
 *
 * <p>Отсюда два требования, которые здесь и выполнены:
 * <ol>
 *   <li>любую зависимость — папку данных, папки раскладок, память ботов — можно
 *       прописать РУКАМИ, в запущенном приложении, без пересборки exe;</li>
 *   <li>окно само проверяет данные и говорит, какого файла не хватает, вместо
 *       того чтобы показывать пустоту.</li>
 * </ol>
 *
 * <p>Всё, что здесь выбрано, ложится в {@code kelium.cfg} (см.
 * {@link AppSettings}) — файл текстовый, его видно и можно править блокнотом.
 */
public final class SettingsDialog {

    private SettingsDialog() {
    }

    /** Что изменилось — по этому решают, пересобирать ли окно. */
    public record Result(boolean scaleChanged, boolean dataChanged, boolean foldersChanged) {
        public boolean any() {
            return scaleChanged || dataChanged || foldersChanged;
        }
    }

    public static Result show(java.awt.Component parent, AppSettings settings) {
        Path dataWas = GameConfig.resolveDataRoot(null);
        double scaleWas = Theme.userScale();
        // БАГ (найден дизайнером 14.08.2026): добавленная здесь папка раскладок
        // не появлялась в списке «поле:» настройки партии до тех пор, пока
        // что-нибудь ДРУГОЕ (например, число игроков) случайно не дёргало
        // reloadFields() как побочный эффект. Окно настроек закрывалось, а
        // список полей оставался старым. Флаг ловит сам факт «Добавить папку…»/
        // «Убрать выбранную», а не пытается угадывать по содержимому списка —
        // так проще и не разъезжается, если папка добавлена и тут же убрана.
        boolean[] foldersChanged = {false};

        javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        tabs.setFont(Theme.body());

        // Вкладка проверки пересобирается каждый раз, когда меняют папку данных:
        // её содержимое — это ответ на вопрос «что лежит ВОТ ЗДЕСЬ», и после
        // смены папки прежний ответ уже неверен.
        JPanel checkTab = new JPanel(new java.awt.BorderLayout());
        Runnable[] recheck = {() -> fill(checkTab)};
        recheck[0].run();

        tabs.addTab("Папки и данные", pathsTab(parent, recheck, foldersChanged));
        tabs.addTab("Проверка данных", checkTab);
        tabs.addTab("Вид", viewTab(parent));
        tabs.addTab("Файл настроек", fileTab());

        JPanel box = new JPanel(new java.awt.BorderLayout());
        box.add(tabs, java.awt.BorderLayout.CENTER);
        box.setPreferredSize(new Dimension(Theme.px(720), Theme.px(520)));

        JDialog d = new JDialog(javax.swing.SwingUtilities.getWindowAncestor(parent),
            "Настройки приложения", JDialog.ModalityType.APPLICATION_MODAL);
        JButton close = Ui2.textButton("Закрыть",
            "Настройки уже сохранены — каждая применяется сразу.", d::dispose);
        JPanel bottom = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(8) + ", gapx " + Theme.px(8), "[grow,fill][]"));
        JLabel hint = new JLabel("Каждая настройка сохраняется сразу, отдельного «применить» нет.");
        hint.setFont(Theme.note(11));
        hint.setForeground(Theme.ink3());
        bottom.add(hint);
        bottom.add(close);
        box.add(bottom, java.awt.BorderLayout.SOUTH);

        d.add(box);
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);

        Path dataNow = GameConfig.resolveDataRoot(null);
        return new Result(Math.abs(Theme.userScale() - scaleWas) > 0.005,
            !dataNow.equals(dataWas), foldersChanged[0]);
    }

    // ==================== вкладка «Папки и данные» ====================

    private static JPanel pathsTab(java.awt.Component parent, Runnable[] recheck,
                                   boolean[] foldersChanged) {
        JPanel p = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(12) + ", gapy " + Theme.px(6), "[grow,fill]"));

        // ---- папка данных игры ----
        p.add(Ui2.caption("папка данных игры"), "wrap");
        JTextField dataField = new JTextField(GameConfig.resolveDataRoot(null).toString());
        dataField.setFont(Theme.body());
        dataField.setEditable(false);
        JLabel dataState = new JLabel();
        dataState.setFont(Theme.note(11));

        Runnable refreshData = () -> {
            Path root = GameConfig.resolveDataRoot(null);
            dataField.setText(root.toString());
            List<String> rs = GameConfig.availableRulesets(null);
            boolean own = Locations.dataFolder() != null;
            if (!Files.isDirectory(root)) {
                dataState.setText("ПАПКИ НЕТ — приложение не найдёт ни правил, ни карт.");
                dataState.setForeground(Theme.bad());
            } else if (rs.isEmpty()) {
                dataState.setText("Папка есть, но версий правил в ней не видно "
                    + "(ожидается подпапка rulesets).");
                dataState.setForeground(Theme.bad());
            } else {
                dataState.setText("Найдено версий правил: " + rs.size()
                    + (own ? "  ·  путь задан вручную" : "  ·  путь найден сам"));
                dataState.setForeground(Theme.good());
            }
        };
        refreshData.run();

        p.add(dataField, "split 3, growx");
        p.add(Ui2.textButton("Обзор…", "Выбрать папку данных игры.", () -> {
            Path dir = chooseFolder(parent, "Папка данных игры",
                GameConfig.resolveDataRoot(null));
            if (dir != null) {
                Locations.setDataFolder(dir);
                refreshData.run();
                if (recheck[0] != null) {
                    recheck[0].run();
                }
            }
        }), "");
        p.add(Ui2.textButton("Сама", "Забыть выбранный путь и снова искать папку "
            + "данных как обычно.", () -> {
                Locations.setDataFolder(null);
                refreshData.run();
                if (recheck[0] != null) {
                    recheck[0].run();
                }
            }), "wrap");
        p.add(dataState, "wrap, gapbottom " + Theme.px(10));
        note(p, "Здесь лежат правила, карты, раскладки и текстуры — подпапки rulesets, "
            + "cards, boards, scenarios. Без неё приложение пустое.");

        // ---- папки раскладок ----
        p.add(Ui2.caption("папки раскладок полей"), "wrap, gaptop " + Theme.px(12));
        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> list = new JList<>(model);
        list.setFont(Theme.body());
        Runnable refreshFolders = () -> {
            model.clear();
            model.addElement(Locations.builtinLayoutFolder() + "   (авторская, всегда)");
            for (Path f : Locations.userLayoutFolders()) {
                model.addElement(f.toString());
            }
        };
        refreshFolders.run();
        JScrollPane sc = new JScrollPane(list);
        sc.setPreferredSize(new Dimension(Theme.px(600), Theme.px(90)));
        p.add(sc, "wrap");
        p.add(Ui2.textButton("Добавить папку…", "Подключить свою папку с раскладками: "
            + "поля из неё появятся в списке настройки партии.", () -> {
                Path dir = chooseFolder(parent, "Папка с раскладками",
                    Locations.builtinLayoutFolder());
                if (dir != null) {
                    Locations.addLayoutFolder(dir);
                    refreshFolders.run();
                    foldersChanged[0] = true;
                }
            }), "split 2");
        p.add(Ui2.textButton("Убрать выбранную", "Убрать папку из списка. Сами файлы "
            + "не трогаются, авторскую папку убрать нельзя.", () -> {
                String s = list.getSelectedValue();
                if (s != null && !s.contains("(авторская")) {
                    Locations.removeLayoutFolder(Paths.get(s));
                    refreshFolders.run();
                    foldersChanged[0] = true;
                }
            }), "wrap, gapbottom " + Theme.px(6));

        // ---- память ботов ----
        p.add(Ui2.caption("память ботов"), "wrap, gaptop " + Theme.px(12));
        JTextField memField = new JTextField(Locations.botMemory().toString());
        memField.setFont(Theme.body());
        memField.setEditable(false);
        p.add(memField, "split 3, growx");
        p.add(Ui2.textButton("Обзор…", "Выбрать папку с геномами и моделями ботов.",
            () -> {
                Path dir = chooseFolder(parent, "Память ботов", Locations.botMemory());
                if (dir != null) {
                    Locations.setBotMemory(dir);
                    memField.setText(Locations.botMemory().toString());
                }
            }), "");
        p.add(Ui2.textButton("Обычная", "Вернуться к обычной папке памяти ботов.", () -> {
            Locations.setBotMemory(null);
            memField.setText(Locations.botMemory().toString());
        }), "wrap");
        note(p, "Геномы стратегов и модели нейросетей. Задаётся отдельно, потому что "
            + "обучения удобно сравнивать, держа их память в разных папках.");
        return p;
    }

    // ==================== вкладка «Проверка данных» ====================

    /**
     * ЧЕСТНЫЙ СПИСОК ТОГО, ЧТО ДВИЖОК ИЩЕТ И ЧТО НАХОДИТ.
     *
     * <p>Каждая версия правил называет версии своих наборов карт
     * ({@code content_versions}), и каждому набору отвечает файл на диске. Если
     * нет хоть одного — не собирается ВСЯ версия правил целиком: ни поля, ни
     * карты, ни описания в справочнике. Здесь видно, какого именно файла нет.
     */
    private static JPanel dataCheck() {
        JPanel p = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(12) + ", gapy " + Theme.px(4), "[grow,fill]"));
        Path root = GameConfig.resolveDataRoot(null);
        p.add(Ui2.caption("папка данных"), "wrap");
        JLabel where = new JLabel(root.toString());
        where.setFont(Theme.body());
        p.add(where, "wrap, gapbottom " + Theme.px(8));

        List<String> rulesets = GameConfig.availableRulesets(null);
        if (rulesets.isEmpty()) {
            p.add(bad("Версий правил не найдено — проверь папку данных на вкладке рядом."),
                "wrap");
            return p;
        }

        StringBuilder html = new StringBuilder("<html><table cellspacing='3'>");
        int broken = 0;
        for (String rid : rulesets) {
            Map<String, String> want;
            try {
                want = wanted(rid, root);
            } catch (RuntimeException e) {
                html.append("<tr><td colspan='3'>").append(esc(rid))
                    .append(" — правила не читаются: ").append(esc(e.getMessage()))
                    .append("</td></tr>");
                broken++;
                continue;
            }
            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, String> e : want.entrySet()) {
                if (!Files.exists(Paths.get(e.getValue()))) {
                    missing.add(e.getKey() + " → " + Paths.get(e.getValue()).getFileName());
                }
            }
            if (missing.isEmpty()) {
                html.append("<tr><td><b>").append(esc(rid))
                    .append("</b></td><td>—</td><td>всё на месте (наборов: ")
                    .append(want.size()).append(")</td></tr>");
            } else {
                broken++;
                html.append("<tr><td><b>").append(esc(rid))
                    .append("</b></td><td>НЕ ХВАТАЕТ</td><td>")
                    .append(esc(String.join("<br>", missing))).append("</td></tr>");
            }
        }
        html.append("</table></html>");

        if (broken > 0) {
            p.add(bad("Не собирается версий правил: " + broken + " из " + rulesets.size()
                + ". Пока файла нет, эта версия не даст ни полей, ни карт, ни описаний "
                + "в справочнике — окно будет пустым."), "wrap, gapbottom " + Theme.px(6));
        } else {
            JLabel ok = new JLabel("Все версии правил собираются, данные на месте.");
            ok.setFont(Theme.body());
            ok.setForeground(Theme.good());
            p.add(ok, "wrap, gapbottom " + Theme.px(6));
        }
        javax.swing.JEditorPane pane = new javax.swing.JEditorPane("text/html",
            html.toString().replace("<html>", "<html><body style='font-family:"
                + Theme.uiFamily() + ";font-size:" + Theme.px(11) + "pt'>"));
        pane.setEditable(false);
        pane.setOpaque(false);
        JScrollPane sc = new JScrollPane(pane);
        sc.setPreferredSize(new Dimension(Theme.px(660), Theme.px(300)));
        p.add(sc, "wrap");
        return p;
    }

    /** Тип набора → ожидаемый файл на диске, для одной версии правил. */
    private static Map<String, String> wanted(String rulesetId, Path root) {
        kelium.rules.Ruleset rs = kelium.rules.Ruleset.loadById(rulesetId,
            root.resolve("rulesets"));
        @SuppressWarnings("unchecked")
        Map<String, Object> versions = (Map<String, Object>) rs.raw.get("content_versions");
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (versions == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : versions.entrySet()) {
            String subdir = ContentSet.ALL_TYPES.get(e.getKey());
            if (subdir == null) {
                continue;              // сценарии, символы, модули читаются иначе
            }
            out.put(e.getKey(), root.resolve(subdir)
                .resolve(e.getKey() + "." + e.getValue() + ".yaml").toString());
        }
        return out;
    }

    // ==================== вкладка «Вид» ====================

    private static JPanel viewTab(java.awt.Component parent) {
        JPanel p = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(12) + ", gapy " + Theme.px(6), "[grow,fill]"));
        p.add(Ui2.caption("масштаб интерфейса"), "wrap");
        JLabel now = new JLabel();
        now.setFont(Theme.body());
        Runnable refresh = () -> now.setText("Сейчас: "
            + Math.round(Theme.effectiveScale() * 100) + " %"
            + (Theme.userScale() == 0
                ? "  (подобран под экран автоматически)"
                : "  (выбран вручную)"));
        refresh.run();
        p.add(now, "wrap");
        p.add(Ui2.textButton("Настроить…", "Ползунок и точное число процентов.", () -> {
            Double v = ScaleDialog.show(parent, Theme.effectiveScale());
            if (v != null) {
                Theme.setUserScale(v);
                AppSettings.of("replay2").putDouble(Theme.SCALE_KEY, v);
                refresh.run();
            }
        }), "split 2");
        p.add(Ui2.textButton("Авто", "Подбирать масштаб под размер экрана.", () -> {
            Theme.setUserScale(0);
            AppSettings.of("replay2").putDouble(Theme.SCALE_KEY, 0);
            refresh.run();
        }), "wrap");
        note(p, "Новый масштаб применяется, когда окно пересобирается: закрой это окно "
            + "и приложение перестроится само. Автоподбор смотрит на размер рабочего "
            + "стола и ужимает вёрстку, если экран тесный — на масштаб Windows "
            + "приложение больше не накручивает свой поверх системного.");
        return p;
    }

    // ==================== вкладка «Файл настроек» ====================

    private static JPanel fileTab() {
        JPanel p = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(12) + ", gapy " + Theme.px(6), "[grow,fill]"));
        Path f = AppSettings.location();
        p.add(Ui2.caption("где хранятся настройки"), "wrap");
        JTextField field = new JTextField(f.toString());
        field.setFont(Theme.body());
        field.setEditable(false);
        p.add(field, "split 2, growx");
        p.add(Ui2.textButton("Открыть папку", "Показать файл настроек в проводнике.",
            () -> {
                try {
                    AppSettings.flush();
                    java.awt.Desktop.getDesktop().open(f.getParent().toFile());
                } catch (Exception e) {
                    // проводник не открылся — путь всё равно виден в поле выше
                }
            }), "wrap");
        note(p, "Обычный текстовый файл: его видно, можно править блокнотом и "
            + "переносить между машинами. Если положить kelium.cfg рядом с самой "
            + "программой, настройки станут переносными и будут браться оттуда.");
        p.add(Ui2.caption("что в нём лежит"), "wrap, gaptop " + Theme.px(12));
        note(p, "replay2.* — окно, тема, масштаб, ширина ящика; "
            + "paths.* — папка данных, папки раскладок, память ботов.");
        p.add(Ui2.textButton("Записать сейчас", "Сбросить настройки на диск, не "
            + "дожидаясь закрытия программы.", AppSettings::flush), "wrap");
        return p;
    }

    // ==================== мелочи ====================

    private static void note(JPanel p, String text) {
        JLabel l = new JLabel("<html>" + esc(text) + "</html>");
        l.setFont(Theme.note(11));
        l.setForeground(Theme.ink3());
        p.add(l, "wrap");
    }

    private static JLabel bad(String text) {
        JLabel l = new JLabel("<html>" + esc(text) + "</html>");
        l.setFont(Theme.font(12, Font.BOLD));
        l.setForeground(Theme.bad());
        return l;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("&lt;br&gt;", "<br>");
    }

    private static Path chooseFolder(java.awt.Component parent, String title, Path start) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (start != null && Files.exists(start)) {
            fc.setCurrentDirectory(start.toFile());
        }
        return fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
            ? fc.getSelectedFile().toPath() : null;
    }

    /** Собрать вкладку проверки заново — папку данных могли только что сменить. */
    private static void fill(JPanel host) {
        host.removeAll();
        host.add(dataCheck(), java.awt.BorderLayout.CENTER);
        host.revalidate();
        host.repaint();
    }
}
