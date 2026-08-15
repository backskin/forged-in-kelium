package kelium.gui;

import javax.swing.UIManager;

import java.awt.Image;
import java.awt.Window;

/**
 * Ui — общие настройки внешнего вида для обоих приложений.
 *
 * <p>Решает две проблемы стандартных диалогов Swing:
 * <ul>
 *   <li><b>Английские кнопки.</b> В составе JDK нет русского перевода интерфейса
 *       (переведены лишь несколько языков), поэтому «Да / Нет / Отмена» надо
 *       задавать вручную — иначе будет Yes / No / Cancel.</li>
 *   <li><b>Обрезанный текст.</b> Диалог сам подбирает ширину и режет длинную
 *       строку многоточием. Обёртка {@link #text(String)} задаёт ширину явно.</li>
 * </ul>
 */
public final class Ui {

    private Ui() {
    }

    /** Системная тема + русские надписи стандартных диалогов. */
    public static void init() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // системная тема недоступна — останется стандартная
        }
        init0();
    }

    /**
     * ТОЛЬКО русские надписи, без выбора темы. Нужен приложениям, которые ставят
     * тему сами (проигрыватель 2.0 поднимает FlatLaf), — иначе системная тема
     * перебивала бы её.
     */
    public static void init0() {
        // СВОЙ ШРИФТ ВО ВСЕХ ПРИЛОЖЕНИЯХ. Семейство Tektur лежит в ресурсах и
        // регистрируется темой; отсюда его получают конструктор, прогонщик и все
        // общие диалоги, чтобы вид был один (решение дизайнера 13.08.2026).
        try {
            java.awt.Font f = new java.awt.Font(
                kelium.gui.replay2.Theme.uiFamily(), java.awt.Font.PLAIN, 13);
            UIManager.put("defaultFont", f);
            for (Object key : new Object[]{"Label.font", "Button.font", "ComboBox.font",
                    "TextField.font", "TextArea.font", "TextPane.font", "List.font",
                    "Table.font", "Tree.font", "Menu.font", "MenuItem.font",
                    "MenuBar.font", "CheckBox.font", "RadioButton.font",
                    "TabbedPane.font", "ToolTip.font", "TitledBorder.font",
                    "OptionPane.messageFont", "OptionPane.buttonFont"}) {
                UIManager.put(key, f);
            }
        } catch (RuntimeException ignored) {
            // шрифт не поднялся — останется системный, приложение работает
        }

        // кнопки диалогов
        UIManager.put("OptionPane.yesButtonText", "Да");
        UIManager.put("OptionPane.noButtonText", "Нет");
        UIManager.put("OptionPane.cancelButtonText", "Отмена");
        UIManager.put("OptionPane.okButtonText", "ОК");
        UIManager.put("OptionPane.inputDialogTitle", "Ввод");
        UIManager.put("OptionPane.messageDialogTitle", "Сообщение");

        // выбор файлов
        UIManager.put("FileChooser.openDialogTitleText", "Открыть файл");
        UIManager.put("FileChooser.saveDialogTitleText", "Сохранить файл");
        UIManager.put("FileChooser.lookInLabelText", "Папка:");
        UIManager.put("FileChooser.saveInLabelText", "Сохранить в:");
        UIManager.put("FileChooser.fileNameLabelText", "Имя файла:");
        UIManager.put("FileChooser.filesOfTypeLabelText", "Тип файлов:");
        UIManager.put("FileChooser.openButtonText", "Открыть");
        UIManager.put("FileChooser.saveButtonText", "Сохранить");
        UIManager.put("FileChooser.cancelButtonText", "Отмена");
        UIManager.put("FileChooser.openButtonToolTipText", "Открыть выбранный файл");
        UIManager.put("FileChooser.saveButtonToolTipText", "Сохранить в выбранный файл");
        UIManager.put("FileChooser.cancelButtonToolTipText", "Закрыть без выбора");
        UIManager.put("FileChooser.upFolderToolTipText", "На уровень вверх");
        UIManager.put("FileChooser.homeFolderToolTipText", "Домашняя папка");
        UIManager.put("FileChooser.newFolderToolTipText", "Создать папку");
        UIManager.put("FileChooser.newFolderButtonText", "Создать папку");
        UIManager.put("FileChooser.listViewButtonToolTipText", "Список");
        UIManager.put("FileChooser.detailsViewButtonToolTipText", "Таблица");
        UIManager.put("FileChooser.acceptAllFileFilterText", "Все файлы");
        UIManager.put("FileChooser.fileNameHeaderText", "Имя");
        UIManager.put("FileChooser.fileSizeHeaderText", "Размер");
        UIManager.put("FileChooser.fileTypeHeaderText", "Тип");
        UIManager.put("FileChooser.fileDateHeaderText", "Изменён");
    }

    /**
     * Обернуть текст сообщения в HTML с явной шириной: иначе длинные строки
     * обрезаются многоточием. Переводы строк сохраняются.
     */
    public static String text(String s) {
        return text(s, 380);
    }

    /** То же с заданной шириной в пикселях. */
    public static String text(String s, int widthPx) {
        String body = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\n", "<br>");
        return "<html><div style='width:" + widthPx + "px'>" + body + "</div></html>";
    }

    /**
     * ЗНАЧОК ОКНА В ПАНЕЛИ ЗАДАЧ И ДИСПЕТЧЕРЕ (просьба дизайнера 14.08.2026):
     * без {@code setIconImage} Windows подставляет свой значок по умолчанию —
     * кофейную чашку Java, у каждого из четырёх приложений одну и ту же, не
     * отличить друг от друга ни на панели задач, ни в диспетчере задач.
     *
     * <p>Значок берётся из ресурсов классов ({@code src/main/resources/icons}),
     * а не с диска рядом с exe: так он есть при ЛЮБОМ способе запуска —
     * {@code mvn exec:java}, .bat-лончер или собранный exe, — файлы на диске
     * при упаковке в exe не копируются, а ресурсы внутри jar всегда с собой.
     * Нет файла — окно остаётся с тем значком, что дала система: не падаем.
     */
    public static void setAppIcon(Window window, String iconName) {
        try {
            java.net.URL url = Ui.class.getResource("/icons/" + iconName + ".png");
            if (url == null) {
                return;
            }
            Image img = java.awt.Toolkit.getDefaultToolkit().createImage(url);
            window.setIconImage(img);
        } catch (RuntimeException ignored) {
            // значок не поднялся — окно работает и со стандартным
        }
    }
}
