package kelium.gui.replay2;


import javax.swing.SwingUtilities;

/**
 * HelpApp — СПРАВОЧНИК ОТДЕЛЬНЫМ ПРИЛОЖЕНИЕМ.
 *
 * <p>Справочник живёт в разборе партии по клавише F1, но нужен и сам по себе:
 * дизайнер читает правила и каталог карт, не собираясь смотреть партию, а
 * запускать ради этого весь проигрыватель — лишний шаг и лишняя память
 * (просьба дизайнера 13.08.2026). Отсюда свой запуск и свой exe
 * (см. {@code make-exe.ps1}, лончер {@code KeliumHelp}).
 *
 * <p>Содержание то же самое: {@link HelpBook} и {@link HelpWindow}. Записи партии
 * здесь нет, поэтому справочник собирается под версию правил по умолчанию — она
 * названа в первой же статье, чтобы не было сомнений, чьи числа показаны.
 *
 * <p>Запуск: {@code java -cp <classpath> kelium.gui.replay2.HelpApp}.
 */
public final class HelpApp {

    private HelpApp() {
    }

    public static void main(String[] args) {
        // Тема — та же, что запомнил разбор партии: приложения разные, а глаза у
        // дизайнера одни. Пересилить можно запуском: -Dkelium.theme=light|dark.
        kelium.dataio.AppSettings prefs = kelium.dataio.AppSettings.of("replay2");
        // Папка данных и масштаб — из того же файла настроек, что у
        // проигрывателя: справочник читает те же карты и должен выглядеть так же.
        kelium.dataio.Locations.applyDataFolder();
        Theme.loadScale(prefs);
        String forced = System.getProperty("kelium.theme", "");
        Theme.apply(forced.isBlank() ? prefs.getBoolean("dark", true) : !"light".equals(forced));
        SwingUtilities.invokeLater(HelpWindow::standalone);
    }
}
