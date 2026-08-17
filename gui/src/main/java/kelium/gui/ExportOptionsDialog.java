package kelium.gui;

import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import kelium.dataio.AppSettings;
import kelium.gui.replay2.Theme;

/**
 * ExportOptionsDialog — НАСТРОЙКИ ЭКСПОРТА РАСКЛАДКИ В PNG.
 *
 * <p>Заказ дизайнера 14.08.2026: отдельное маленькое окошко с чекбоксами, какие
 * разделы класть под картинку поля — общая легенда, личная легенда/показатели
 * каждого игрока, статистика карты. Само изображение поля выключить нельзя: это
 * не второстепенная деталь, а причина, ради которой вообще экспортируют.
 *
 * <p>Как {@link kelium.gui.replay2.SettingsDialog} — каждый чекбокс сохраняется
 * СРАЗУ по клику, отдельной кнопки «применить» нет. Следующий экспорт просто
 * читает те же ключи.
 */
public final class ExportOptionsDialog {

    private ExportOptionsDialog() {
    }

    private static final String KEY_LEGEND = "export.legend";
    private static final String KEY_PLAYERS = "export.players";
    private static final String KEY_STATS = "export.stats";
    private static final String KEY_LAYOUT = "export.layout";
    private static final String KEY_CONTAINERS = "export.containers";
    private static final String KEY_HEXGRID = "export.hexgrid";

    /** Что сейчас выбрано (используется при самом экспорте). */
    public static PngExport.Options current(AppSettings settings) {
        PngExport.Layout layout;
        try {
            layout = PngExport.Layout.valueOf(
                settings.get(KEY_LAYOUT, PngExport.Layout.SEPARATE.name()));
        } catch (IllegalArgumentException e) {
            layout = PngExport.Layout.SEPARATE;
        }
        return new PngExport.Options(
            settings.getBoolean(KEY_LEGEND, true),
            settings.getBoolean(KEY_PLAYERS, true),
            settings.getBoolean(KEY_STATS, true),
            layout,
            settings.getBoolean(KEY_CONTAINERS, true),
            // Сетка имеет смысл только в слиянии — там обычных гексов нет вовсе.
            // В остальных раскладках галочка недоступна, и читать её нельзя:
            // включённая однажды, она иначе тянулась бы в чужие режимы.
            layout == PngExport.Layout.FUSION && settings.getBoolean(KEY_HEXGRID, false));
    }

    public static void show(Window owner, AppSettings settings) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createEmptyBorder(
            Theme.px(14), Theme.px(16), Theme.px(10), Theme.px(16)));

        JLabel head = new JLabel("Что класть под картинку поля");
        head.setFont(Theme.wideText(13));
        head.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        box.add(head);
        box.add(Box.createVerticalStrut(Theme.px(4)));

        JLabel always = new JLabel("Изображения поля и сборки из блоков — печатаются всегда.");
        always.setFont(Theme.note(11));
        always.setForeground(Theme.ink3());
        always.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        box.add(always);
        box.add(Box.createVerticalStrut(Theme.px(10)));

        box.add(checkbox(settings, KEY_LEGEND,
            "Общие обозначения", "Что значат гексы, тайлы, нейтралы и контейнеры на поле."));
        box.add(checkbox(settings, KEY_PLAYERS,
            "Игроки", "Цветной кружок и показатели каждого игрока по отдельности "
                + "(келемий поблизости, соседей у старта)."));
        box.add(checkbox(settings, KEY_STATS,
            "Статистика поля", "Размер поля, число зарождений, контейнеров, "
                + "расстояние между стартами."));

        box.add(Box.createVerticalStrut(Theme.px(14)));
        JLabel fieldHead = new JLabel("Что показывать на самом поле");
        fieldHead.setFont(Theme.wideText(13));
        fieldHead.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        box.add(fieldHead);
        box.add(Box.createVerticalStrut(Theme.px(6)));

        box.add(checkbox(settings, KEY_CONTAINERS, "Контейнеры",
            "Контейнеры — свой слой поверх всего. Печатнику они нужны, "
                + "а на обзорной картинке раскладки часто мешают."));

        JCheckBox grid = checkbox(settings, KEY_HEXGRID, "Показывать гексагональную сетку",
            "Тонкая сетка по всей площади кадра, угасающая по мере удаления от поля. "
                + "Имеет смысл только при слиянии: там обычных гексов не рисуют вовсе, "
                + "и поле иначе висит в пустоте.");
        box.add(grid);

        box.add(Box.createVerticalStrut(Theme.px(14)));
        JLabel layoutHead = new JLabel("Как показать поле и сборку из блоков вместе");
        layoutHead.setFont(Theme.wideText(13));
        layoutHead.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        box.add(layoutHead);
        box.add(Box.createVerticalStrut(Theme.px(6)));

        ButtonGroup group = new ButtonGroup();
        PngExport.Layout current = current(settings).layout();
        // Сетка доступна ТОЛЬКО при слиянии — галочка гаснет вместе с выбором
        // раскладки, а не молча ничего не делает.
        grid.setEnabled(current == PngExport.Layout.FUSION);
        for (PngExport.Layout mode : PngExport.Layout.values()) {
            JRadioButton rb = new JRadioButton(mode.label, mode == current);
            rb.setFont(Theme.body());
            rb.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            rb.addActionListener(e -> {
                settings.put(KEY_LAYOUT, mode.name());
                grid.setEnabled(mode == PngExport.Layout.FUSION);
            });
            group.add(rb);
            box.add(rb);
        }
        JLabel fusionHint = new JLabel(
            "<html><div style='width:340px'>Слияние — одна картинка: блоки картона "
                + "белым с тёмной обводкой как фон, а поверх них по слоям — тайлы "
                + "зарождения, игроки, стартовые здания, запретные гексы и контейнеры. "
                + "Обычных гексов и цвета блоков в слиянии нет: их роль играют сами "
                + "блоки.</div></html>");
        fusionHint.setFont(Theme.note(11));
        fusionHint.setForeground(Theme.ink3());
        fusionHint.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        fusionHint.setBorder(BorderFactory.createEmptyBorder(2, 22, 0, 0));
        box.add(fusionHint);

        JDialog d = new JDialog(owner, "Настройки экспорта", JDialog.ModalityType.MODELESS);
        d.setResizable(false);
        d.add(box);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    private static JCheckBox checkbox(AppSettings settings, String key, String label,
                                      String tip) {
        JCheckBox cb = new JCheckBox(label, settings.getBoolean(key, true));
        cb.setFont(Theme.body());
        cb.setToolTipText(Ui.text(tip, 320));
        cb.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        cb.addActionListener(e -> settings.putBoolean(key, cb.isSelected()));
        return cb;
    }
}
