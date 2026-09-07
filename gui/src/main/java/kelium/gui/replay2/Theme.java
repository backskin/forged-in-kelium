package kelium.gui.replay2;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

import javax.swing.JComponent;
import javax.swing.UIManager;

import kelium.report.FieldGeometry;

/**
 * Theme — ЕДИНЫЕ ЗНАЧЕНИЯ ОБЛИКА проигрывателя 2.0: цвета, шрифты, отступы.
 *
 * <p>Зачем отдельный класс. В версии 1.0 цвета и размеры были рассыпаны по
 * пятнадцати файлам: {@code new Color(0x77,0x77,0x77)} в одном месте,
 * {@code 0x8A857A} в другом, шрифт 11,5 здесь и 13 там. Поменять облик было
 * нельзя — только переписать всё. Теперь любое значение берётся отсюда.
 *
 * <p><b>Тема по умолчанию тёмная</b> — и это не мода. У поля свои АВТОРСКИЕ цвета,
 * и они светлые: белые гексы, зелёные тайлы зарождения, бежевые контейнеры. Поле
 * должно быть самым светлым объектом на экране; светло-серый интерфейс 1.0 сливался
 * с ним и отбирал первенство. Тёмная рама работает как подсветка стола. Фон при этом
 * НЕ чёрный: на чистом чёрном пастель начинает светиться и цвета читаются искажённо.
 *
 * <p>Светлая тема обязательна и полная — в ней снимают картинки для правил.
 *
 * <p>Цвета мест и смыслов НЕ выдуманы: они взяты из {@link FieldGeometry} и из
 * авторских цветов жетонов, чтобы интерфейс не спорил с полем.
 */
public final class Theme {

    private Theme() {
    }

    // ==================== состояние ====================
    private static boolean dark = true;

    public static boolean isDark() {
        return dark;
    }

    // ==================== масштаб интерфейса ====================
    //
    // ТРИ РАЗНЫХ МНОЖИТЕЛЯ, и путать их нельзя — на этом уже обожглись.
    //
    //   1) ПОПРАВКА НА DPI ({@link #dpiFactor}). Windows на 125 % просит рисовать
    //      всё в 1,25 раза крупнее. НО Java 9 и старше делает это САМА: она
    //      растягивает всю графику окна, а нам отдаёт «логические» пиксели. Прежний
    //      код умножал на DPI ещё раз — выходило 125 % × 125 % = 156 %, и на
    //      ноутбуке 15 дюймов верхняя строка настроек не влезала в экран (жалоба
    //      дизайнера 14.08.2026). Теперь мы СПРАШИВАЕМ У JAVA, сколько она уже
    //      увеличила, и добавляем только недостающее — обычно ничего.
    //
    //   2) АВТОПОДБОР ПОД ЭКРАН ({@link #autoScale}). Экран бывает не только
    //      плотный, но и маленький: 1920×1080 при 125 % — это всего 1536×864
    //      логических точки, куда вёрстка, рассчитанная на 1500×950, влезает
    //      впритык. Автоподбор мягко ужимает всё, если места меньше расчётного, и
    //      НИКОГДА не растягивает: на большом мониторе размеры остаются авторскими.
    //
    //   3) РУЧНОЙ МНОЖИТЕЛЬ ({@link #setUserScale}) — «Вид → Масштаб интерфейса».
    //      Последнее слово всегда за человеком: выбранный руками процент отменяет
    //      автоподбор целиком.
    //
    // Итог = поправка на DPI × (ручной, а если его нет — автоподбор).

    /** Вёрстка рассчитана на такое окно (в точках сетки) — от него считается автоподбор. */
    private static final int DESIGN_W = 1500;
    private static final int DESIGN_H = 950;

    /** Насколько ещё надо увеличить сверх того, что уже сделала Java. */
    private static final double DPI = dpiFactor();
    /** Автоподбор под размер экрана: 1.0 — экран просторный, меньше — тесный. */
    private static final double AUTO = autoScale();

    /** Ручной множитель: 0 — «авто», иначе доля (1.0 = 100 %). */
    private static double userScale;
    /** Итог, которым множатся все размеры. Пересчитывается при смене настройки. */
    private static double scale = DPI * AUTO;

    /**
     * ТЕКСТ НА 5 % МЕЛЬЧЕ ВСЕГО ОСТАЛЬНОГО (просьба дизайнера 14.08.2026).
     *
     * <p>Отдельный множитель, а не правка полусотни чисел по файлам: кегли
     * подобраны друг к другу (13 основной, 11 подпись, 19 число в плитке), и
     * трогать их поодиночке — значит рассыпать эту соразмерность. Здесь же
     * меняется ОДНА цифра, а все соотношения сохраняются.
     *
     * <p>На отступы и коробки НЕ действует: ужать нужно было именно буквы, плитки
     * и так стоят плотно.
     */
    private static final double TEXT = 0.95;

    /**
     * Сколько система просит добавить СВЕРХ того, что Java уже нарисовала сама.
     *
     * <p>{@code getDefaultTransform().getScaleX()} — это ровно то увеличение,
     * которое Java применяет к окну. Если оно совпадает с запросом системы
     * (обычный случай на Java 9+), добавлять нечего и здесь выходит 1.0. Если Java
     * почему-то не масштабирует (старая среда, {@code -Dsun.java2d.uiScaleEnabled=false}),
     * недостающее берём на себя — иначе на плотном экране всё станет мелким.
     */
    private static double dpiFactor() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return 1.0;
            }
            int dpi = java.awt.Toolkit.getDefaultToolkit().getScreenResolution();
            double asked = Math.max(1.0, dpi / 96.0);
            double done = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration()
                .getDefaultTransform().getScaleX();
            if (done <= 0) {
                done = 1.0;
            }
            return Math.max(1.0, Math.min(2.0, asked / done));
        } catch (RuntimeException | Error e) {
            return 1.0;
        }
    }

    /**
     * Подбор под РАЗМЕР рабочего стола: если места меньше расчётного, ужимаем.
     * Ниже 0,7 не опускаемся — там уже нечитаемо.
     */
    private static double autoScale() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return 1.0;
            }
            java.awt.Rectangle b = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
            // Считаем в ТОЧКАХ ВЁРСТКИ: экранные пиксели делим на поправку DPI,
            // иначе на среде без своего масштабирования сравнение шло бы в другой
            // единице и автоподбор врал бы ровно на величину DPI.
            double w = b.width / DPI;
            double h = b.height / DPI;
            if (w <= 0 || h <= 0) {
                return 1.0;
            }
            double fit = Math.min(w / DESIGN_W, h / DESIGN_H);
            return Math.max(0.7, Math.min(1.0, fit));
        } catch (RuntimeException | Error e) {
            return 1.0;
        }
    }

    /**
     * Поставить ручной множитель: доля (1.0 = 100 %) или 0 — вернуться к
     * автоподбору. Размеры уже созданных окон при этом НЕ меняются: их надо
     * пересобрать (см. {@code Replay2Gui.rebuildForScale}).
     */
    public static void setUserScale(double value) {
        userScale = value <= 0 ? 0 : Math.max(0.6, Math.min(2.0, value));
        scale = DPI * (userScale == 0 ? AUTO : userScale);
    }

    /** Ручной множитель: 0 — включён автоподбор. */
    public static double userScale() {
        return userScale;
    }

    /** Какой множитель действует сейчас, без поправки на DPI (её человек не выбирал). */
    public static double effectiveScale() {
        return userScale == 0 ? AUTO : userScale;
    }

    /** Что предлагает автоподбор — показывается в меню рядом с пунктом «Авто». */
    public static double autoScaleValue() {
        return AUTO;
    }

    /** Ключ настройки масштаба — один на все окна приложения. */
    public static final String SCALE_KEY = "ui.scale";

    /**
     * Взять масштаб из настроек пользователя. Вызывать ДО сборки первого окна:
     * размеры запекаются в компоненты при создании.
     */
    public static void loadScale(kelium.dataio.AppSettings settings) {
        setUserScale(settings.getDouble(SCALE_KEY, 0));
    }

    /** Размер в точках сетки → в пикселях экрана. */
    public static int px(int design) {
        return (int) Math.round(design * scale);
    }

    /** То же для дробных величин: толщина обводки, радиус точки. */
    public static float pxf(double design) {
        return (float) (design * scale);
    }

    /** Кегль шрифта в точках сетки → в пикселях. Текст мельче коробок на {@link #TEXT}. */
    private static float textPx(double design) {
        return (float) (design * scale * TEXT);
    }

    /**
     * ПОЛОСЫ ИГРОКОВ — НА 10 % КРУПНЕЕ прочего интерфейса (просьба дизайнера
     * 14.08.2026).
     *
     * <p>Причина не в капризе, а в том, как на них смотрят. Полосы читают БЫСТРО и
     * КОСЯСЬ, не отрываясь от поля: сколько у соседа келемия, не пора ли ему на
     * науку. Всё остальное — меню, настройки, справочник — читают в упор и
     * осознанно. После общего уменьшения текста полосы просели ниже порога такого
     * взгляда: «личная зона» стала милипиздрической.
     *
     * <p>Множитель, а не свои кегли: соотношения внутри полосы (крупное число,
     * мелкий предел, подпись) подобраны и должны сохраниться целиком.
     */
    public static final double STRIP_TEXT = 1.10;

    // ==================== сетка ====================
    /** Шаг сетки — 4 пикселя; допустимые отступы кратны ему. */
    public static final int GRID = 4;

    public static final int PAD_PANEL = 12;
    public static final int PAD_TILE = 8;
    public static final int GAP_TILE = 8;
    public static final int GAP_BLOCK = 16;

    public static final int R_TILE = 4;
    public static final int R_PANEL = 6;
    public static final int R_OVERLAY = 8;

    /** Высоты постоянных жильцов экрана (в точках вёрстки, до масштаба). */
    public static final int H_CONTEXT = 40;
    // Полоса игрока стала выше: мелкие значки в её нижних строках увеличены вдвое
    // — на прежней высоте они бы налезали друг на друга (просьба дизайнера
    // 13.08.2026: «милипиздрические значки»).
    // Ещё выше с 14.08.2026: под именем игрока появилась строка расклада —
    // колода приказов и стороны обоих его планшетов.
    public static final int H_STRIP = 140;
    public static final int H_STRIP_TIGHT = 120;
    public static final int H_TIMELINE = 76;
    public static final int H_TIMELINE_TIGHT = 52;
    public static final int H_TRANSPORT = 48;
    public static final int H_STATUS = 22;
    /** Меньше этого поле не сжимаем никогда. */
    public static final int FIELD_MIN_W = 560;
    public static final int FIELD_MIN_H = 420;

    // ==================== основа ====================
    private static final Color[] DARK = {
        new Color(0x14171C),   // 0 фон приложения
        new Color(0x1B1F26),   // 1 панели, ящик, пульт
        new Color(0x232830),   // 2 плитки
        new Color(0x2A313B),   // 3 наведение
        new Color(0x2E353F),   // 4 границы
        new Color(0x3A424E),   // 5 сильные разделители
        new Color(0xECEFF4),   // 6 текст главный
        new Color(0xA8B1BF),   // 7 текст второй
        new Color(0x6F7986),   // 8 текст тихий
        // 9 ПОДЛОЖКА ПОЛЯ. Была светлой «бумагой» при любой теме: поле лежало
        // белым листом посреди тёмного окна (замечание дизайнера 13.08.2026).
        // Теперь она тоже тёмная, но СВЕТЛЕЕ фона окна — поле остаётся отдельным
        // предметом на столе, а не сливается со всем подряд. Гексы на ней ещё
        // темнее, а жетоны наоборот светлее (см. FieldPainter.dark).
        new Color(0x1F242B),   // 9 подложка поля
        new Color(0x4C9BE8),   // 10 акцент
    };

    private static final Color[] LIGHT = {
        new Color(0xEEF0F3),
        new Color(0xFFFFFF),
        new Color(0xF5F6F8),
        new Color(0xE9ECF1),
        new Color(0xDCE0E6),
        new Color(0xC6CCD4),
        new Color(0x171A1F),
        new Color(0x525C69),
        new Color(0x818B98),
        new Color(0xFFFFFF),
        new Color(0x1F6FC4),
    };

    private static Color base(int i) {
        return (dark ? DARK : LIGHT)[i];
    }

    public static Color bg() {
        return base(0);
    }

    public static Color panel() {
        return base(1);
    }

    public static Color tile() {
        return base(2);
    }

    public static Color hover() {
        return base(3);
    }

    public static Color border() {
        return base(4);
    }

    public static Color divider() {
        return base(5);
    }

    public static Color ink() {
        return base(6);
    }

    public static Color ink2() {
        return base(7);
    }

    public static Color ink3() {
        return base(8);
    }

    /** Подложка, на которой лежит поле: авторские цвета видно как на бумаге. */
    public static Color paper() {
        return base(9);
    }

    public static Color accent() {
        return base(10);
    }

    // ==================== цвета мест ====================
    private static final Color[] SEAT = parse(FieldGeometry.SEAT_TOKEN);
    private static final Color[] SEAT_STROKE = parse(FieldGeometry.SEAT_STROKE);

    private static Color[] parse(String[] hex) {
        Color[] out = new Color[hex.length];
        for (int i = 0; i < hex.length; i++) {
            out[i] = Color.decode(hex[i]);
        }
        return out;
    }

    /**
     * Цвет места — тот же, что у жетона на поле, и тем же путём: через гнездо
     * цвета партии ({@link FieldGeometry#seatColor}). Иначе фишки и полосы
     * игроков красились бы по номеру места, а жетоны на поле — по выбранному
     * цвету, и одно место оказалось бы двух разных цветов сразу.
     */
    public static Color seat(int i) {
        return SEAT[FieldGeometry.seatColor(i)];
    }

    public static Color seatStroke(int i) {
        return SEAT_STROKE[FieldGeometry.seatColor(i)];
    }

    /**
     * Цвет места, ПРИГОДНЫЙ ДЛЯ ТЕКСТА на текущем фоне. Авторские краски жетонов
     * рассчитаны на белую бумагу: на тёмном фоне синий #3B82D0 читается плохо.
     */
    public static Color seatInk(int i) {
        Color c = seat(i);
        return dark ? lighten(c, 0.34) : darken(c, 0.16);
    }

    /** Бледная подложка цветом места (например у активного игрока). */
    public static Color seatWash(int i, double alpha) {
        Color c = seat(i);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
            (int) Math.round(255 * alpha));
    }

    // ==================== цвета смыслов ====================
    // Взяты из самого поля: келемий — цвет тайлов зарождения, энергия — кубиков
    // энергии, урон — кубиков урона, склад — зоны хранения планшета.
    public static Color kelium() {
        return dark ? new Color(0x6BC26F) : new Color(0x43A047);
    }

    public static Color energy() {
        return dark ? new Color(0xFFCE3D) : new Color(0xFFC400);
    }

    public static Color damage() {
        return dark ? new Color(0xE5584F) : new Color(0xD32F2F);
    }

    public static Color storage() {
        return dark ? new Color(0xD4B858) : new Color(0xB99A2E);
    }

    public static Color container() {
        return dark ? new Color(0xBFA779) : new Color(0x9A8455);
    }

    /**
     * УНИЧТОЖЕННЫЙ ЖЕТОН — приглушённо-серый: снесённый жетон противника, что
     * лежит у игрока трофейной стороной вверх до Возврата. Это НЕ трофей
     * ({@link #trophy()}, чёрный кубик хранилища): жетон только превратится в
     * трофеи, когда вернётся владельцу.
     */
    public static Color destroyed() {
        return dark ? new Color(0xA9B7C6) : new Color(0x93A3B5);
    }

    /**
     * ТРОФЕЙ — чёрный кубик хранилища, плата за науку. Отличается от
     * приглушённо-серого {@link #destroyed()}: тот — целый уничтоженный жетон,
     * ещё не вернувшийся владельцу.
     * Иконографика: чёрный квадрат с серой шестерёнкой в центре.
     */
    public static Color trophy() {
        return dark ? new Color(0x3A3D42) : new Color(0x1C1E21);
    }

    /** Победные очки — единственный «наградной» цвет, золото. */
    public static Color points() {
        return new Color(0xE8B84B);
    }

    public static Color neutral() {
        return new Color(0x8B93A0);
    }

    public static Color good() {
        return dark ? new Color(0x6BC26F) : new Color(0x2E7D32);
    }

    public static Color bad() {
        return dark ? new Color(0xE5584F) : new Color(0xC62828);
    }

    // ==================== шрифты ====================
    //
    // СВОИ ШРИФТЫ, ВШИТЫЕ В ПРОГРАММУ (решение дизайнера 13.08.2026). Раньше брался
    // системный Segoe UI: на чужой машине его может не быть, и вид разъезжался.
    // Теперь семейство Tektur лежит в ресурсах и регистрируется при запуске —
    // приложение выглядит одинаково везде, включая тесты и снимки.
    //
    // ОСНОВНОЙ ШРИФТ — TEKTUR NARROW (решение дизайнера 13.08.2026). Узкое
    // начертание везде: интерфейс плотный, а узкие буквы дают больше текста на той
    // же ширине и не мельчат. Взято пять файлов, каждый со своей работой:
    //
    //   Narrow Medium    — основной текст;
    //   Narrow SemiBold  — мелкие подписи: на 10–11 пикселях среднее начертание
    //                      бледнит, полужирное читается;
    //   Narrow Bold      — заголовки и акценты;
    //   Tektur Bold      — ЧИСЛА: широкое полужирное. Счёт — главное на экране, и
    //                      широкие цифры рядом с узким текстом сами становятся
    //                      акцентом (решение дизайнера 13.08.2026);
    //   (заголовки и названия карт берут тот же широкий Tektur Bold, что и числа)
    //
    // Курсива у семейства нет — там, где нужен (пояснения, описания), Swing
    // наклоняет начертание сам.
    //
    // Если ресурс не прочитался (собрано без ресурсов), всё честно откатывается на
    // системный шрифт — приложение не должно падать из-за шрифта.

    // ВАЖНО: начертания держим ОБЪЕКТАМИ Font, а не именами семейств. У всех
    // начертаний Tektur Narrow семейство ОДНО («Tektur Narrow»), и обращение по
    // имени семейства возвращало не то начертание: цифры, задуманные полужирными,
    // рисовались обычными (замечание дизайнера 13.08.2026 — «цифры можно пожирнее»).
    // Через deriveFont начертание сохраняется точно.

    // ЖИРНОЕ НАЧЕРТАНИЕ СТАЛО НА СТУПЕНЬ ЛЕГЧЕ (просьба дизайнера 15.08.2026:
    // «жирные шрифты слишком жирные»). Заголовки и числа набирались Bold, и на
    // экране это читалось как крик: полужирного (SemiBold) хватает, чтобы
    // выделить, и текст перестаёт быть тяжёлым. Файлы Bold оставлены в ресурсах —
    // вернуть прежний вес можно одной строкой здесь.
    private static final Font UI = load("TekturNarrow-Medium.ttf", Font.SANS_SERIF);
    private static final Font UI_SEMI = load("TekturNarrow-Medium.ttf", null);
    private static final Font UI_BOLD = load("TekturNarrow-SemiBold.ttf", null);
    private static final Font NUM = load("Tektur-SemiBold.ttf", null);
    // ОПИСАНИЯ — САМОЕ ТОНКОЕ НАЧЕРТАНИЕ. Пояснительный текст набран тем же
    // семейством, но легче заголовков: рядом с полужирным заголовком среднее
    // начертание спорило с ним по весу и абзац читался как второй заголовок
    // (просьба дизайнера 13.08.2026 — «снизить чуть толщину описывающего текста»).
    private static final Font UI_LIGHT = load("TekturNarrow-Regular.ttf", null);
    // ШИРОКИЙ ОБЫЧНЫЙ — для надписей на кнопках и всего, что надо прочесть сразу,
    // но жирным набирать нельзя: NUM (Tektur Bold) там слишком тяжёл.
    private static final Font WIDE = load("Tektur-Regular.ttf", null);
    // ЗАГОЛОВКИ И НАЗВАНИЯ КАРТ — ШИРОКИМ ПОЛУЖИРНЫМ Tektur, тем же файлом, что и
    // числа. Пробовали плакатный Molot — не пошло: на мелком кегле он расплывался
    // и название карты просто не читалось (решение дизайнера 13.08.2026).
    private static final Font DISPLAY = NUM;

    /** Зарегистрировать шрифт из ресурсов. Не вышло — берём запасной. */
    private static Font load(String file, String fallbackFamily) {
        try (java.io.InputStream in =
                 Theme.class.getResourceAsStream("/fonts/" + file)) {
            if (in != null) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
                return f.deriveFont(13f);
            }
        } catch (Exception ignored) {
            // ниже — запасной вариант
        }
        return new Font(fallbackFamily == null ? Font.SANS_SERIF : fallbackFamily,
            Font.PLAIN, 13);
    }

    /** Семейство основного текста — нужно ещё и оформлению окон (FlatLaf). */
    public static String uiFamily() {
        return UI.getFamily();
    }

    /**
     * ЧИСЛА В СТОЛБЦАХ. Одноширинного шрифта здесь больше нет: цифры набираются
     * широким полужирным Tektur, они одной ширины сами по себе — столбцы не
     * пляшут, а вид остаётся единым, без вкраплений консольного шрифта.
     */

    public static Font font(double size, int style) {
        // ЖИРНОЕ НАЧЕРТАНИЕ — ОТДЕЛЬНЫМ ФАЙЛОМ, а не «утолщением» на лету: Swing
        // подделывает жирность растягиванием контура, и на мелком кегле буквы
        // замыливаются. МЕЛКИЙ ТЕКСТ (10–11) берёт полужирное: среднее на таком
        // кегле бледнит и читается хуже (замечание дизайнера 13.08.2026).
        Font face = UI;
        int swing = style;
        if ((style & Font.BOLD) != 0) {
            face = UI_BOLD;
            swing = style & ~Font.BOLD;
        } else if (size <= 11) {
            face = UI_SEMI;
        }
        Font f = face.deriveFont(textPx(size));
        if (swing != Font.PLAIN) {
            f = f.deriveFont(swing);        // курсив семейство не несёт — наклоняем
        }
        return tracked(f, TRACK_UI);
    }

    /**
     * РАЗРЯДКА (расстояние между буквами). У Tektur Narrow буквы стоят вплотную —
     * это его характер, но сплошной строкой он читается как забор (замечание
     * дизайнера 13.08.2026). Небольшая разрядка возвращает воздух, не ломая узость.
     */
    private static final double TRACK_UI = 0.035;
    private static final double TRACK_DISPLAY = 0.06;

    private static Font tracked(Font f, double track) {
        java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>();
        attrs.put(java.awt.font.TextAttribute.TRACKING, track);
        return f.deriveFont(attrs);
    }

    /** Узкое начертание — оно же основное; метод оставлен для явности намерения. */
    public static Font narrow(double size, int style) {
        return font(size, style);
    }

    /**
     * ПЛАКАТНЫЙ ШРИФТ (Molot): супер-заголовки и НАЗВАНИЯ КАРТ. Только там —
     * тяжёлое начертание должно оставаться редким, иначе перестаёт выделять.
     */
    public static Font display(double size) {
        return tracked(DISPLAY.deriveFont(textPx(size)), TRACK_DISPLAY);
    }

    /** Семейство плакатного шрифта — для html-разворотов карт. */
    public static String displayFamily() {
        return DISPLAY.getFamily();
    }

    /**
     * ШИРОКОЕ начертание — там, где текста немного, а прочесть надо сразу: титр
     * события в углу поля. Наклонное (просьба дизайнера 13.08.2026): titre звучит
     * как реплика диктора, а не как подпись интерфейса.
     */
    public static Font wide(double size, boolean slanted) {
        java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>();
        attrs.put(java.awt.font.TextAttribute.TRACKING, TRACK_UI);
        Font f = NUM.deriveFont(textPx(size)).deriveFont(attrs);
        if (slanted) {
            // НАКЛОН — СДВИГОМ. У Tektur нет курсивного начертания; ни
            // deriveFont(ITALIC), ни POSTURE на таком шрифте ничего не меняют,
            // а вот сдвиг работает всегда и даёт ровно тот наклон, который задан.
            f = f.deriveFont(java.awt.geom.AffineTransform.getShearInstance(-0.18, 0));
        }
        return f;
    }

    /**
     * ОПИСАНИЯ И ПОЯСНЕНИЯ — узкое ОБЫЧНОЕ начертание, легче основного текста.
     * Ставить его надо только там, где рядом есть заголовок: сам по себе, длинной
     * простынёй, он бледноват.
     */
    public static Font note(double size) {
        return tracked(UI_LIGHT.deriveFont(textPx(size)), TRACK_UI);
    }

    /**
     * ШИРОКОЕ ОБЫЧНОЕ начертание — надписи на кнопках и короткие ярлыки. От
     * {@link #wide(int, boolean)} отличается весом: то полужирное, для титров.
     */
    public static Font wideText(double size) {
        return tracked(WIDE.deriveFont(textPx(size)), TRACK_UI);
    }

    /** Числа: широкое полужирное начертание, стиль игнорируется — оно уже жирное. */
    public static Font mono(double size, int style) {
        return tracked(NUM.deriveFont(textPx(size)), TRACK_UI);
    }

    /** Крупное число: победные очки, итоги. */
    public static Font numberBig() {
        return mono(26, Font.BOLD);
    }

    /** Число в плитке показателя. */
    public static Font numberTile() {
        return mono(19, Font.BOLD);
    }

    /** Заголовок экрана — плакатным Molot: он тут главный. */
    public static Font title() {
        return display(19);
    }

    public static Font subtitle() {
        return font(14, Font.BOLD);
    }

    public static Font body() {
        return font(13, Font.PLAIN);
    }

    public static Font italic() {
        return font(12, Font.ITALIC);
    }

    /** Подпись: мелкая, полужирная, ЗАГЛАВНЫМИ с разрядкой. */
    public static Font caption() {
        return font(11, Font.BOLD);
    }

    // ==================== применение ====================
    /**
     * Поставить тему. Вызывать ДО создания окна; для переключения на ходу — вместе
     * с {@link javax.swing.SwingUtilities#updateComponentTreeUI}.
     */
    public static void apply(boolean darkTheme) {
        dark = darkTheme;
        // СТРОКУ ЗАГОЛОВКА рисует сама FlatLaf, а не Windows: иначе при тёмной теме
        // в светлом режиме системы (и наоборот) заголовок оставался цветов системы
        // и окно выглядело склеенным из двух программ. Свойства читаются при
        // установке темы, поэтому ставим их ДО неё.
        System.setProperty("flatlaf.useWindowDecorations", "true");
        System.setProperty("flatlaf.menuBarEmbedded", "true");
        try {
            if (darkTheme) {
                com.formdev.flatlaf.FlatDarkLaf.setup();
            } else {
                com.formdev.flatlaf.FlatLightLaf.setup();
            }
        } catch (RuntimeException e) {
            // FlatLaf не поднялся — останется системная тема, приложение работает
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // ничего не поделать, идём со стандартной
            }
        }
        // ТОКЕНЫ поверх темы: скругления, отступы, шрифт, цвета акцента. Всё в
        // одном месте — так облик правится значениями, а не переписыванием кода.
        UIManager.put("defaultFont", body());
        UIManager.put("Button.arc", R_TILE * 2);
        UIManager.put("Component.arc", R_TILE * 2);
        UIManager.put("ProgressBar.arc", R_TILE * 2);
        UIManager.put("TextComponent.arc", R_TILE * 2);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 1);
        UIManager.put("Component.focusColor", accent());
        UIManager.put("Component.borderColor", border());
        // ПОВЕРХНОСТИ ОДНОГО ЦВЕТА. Раньше «Panel.background» был цветом ФОНА ОКНА,
        // и любая обычная панель внутри цветной полосы рисовалась другим цветом —
        // в одном блоке получались две краски (жалоба дизайнера 13.08.2026: «белая
        // полоса не в цвет панели на светлой теме и чёрная на тёмной»). Теперь
        // обычная панель — это ПАНЕЛЬ, а фон окна остаётся только там, где мы
        // просим его явно (сцена с полем и ряд полос игроков).
        UIManager.put("Panel.background", panel());
        UIManager.put("ScrollPane.background", panel());
        UIManager.put("Viewport.background", panel());
        UIManager.put("Tree.background", panel());
        UIManager.put("List.background", panel());
        // ТЕКСТОВЫЕ ПОВЕРХНОСТИ — ТОЖЕ ПАНЕЛЬ (правка 14.08.2026). Дерево лога,
        // планшет игрока и статьи живут в текстовых компонентах, а у них своя
        // ветка ключей: без неё они брали БЕЛЫЙ фон текстового поля независимо от
        // темы. На тёмной теме получалась белая простыня с белым же текстом, на
        // светлой — чёрная («фон логов чёрный на светлой теме и наоборот»).
        // Ключей несколько, потому что Swing спрашивает разные в зависимости от
        // того, чем именно нарисован компонент.
        UIManager.put("TextPane.background", panel());
        UIManager.put("EditorPane.background", panel());
        UIManager.put("TextArea.background", panel());
        UIManager.put("Tree.textBackground", panel());
        UIManager.put("List.textBackground", panel());
        UIManager.put("Tree.foreground", ink());
        UIManager.put("List.foreground", ink());
        UIManager.put("TextPane.foreground", ink());
        UIManager.put("EditorPane.foreground", ink());
        UIManager.put("TextArea.foreground", ink());
        UIManager.put("Label.foreground", ink());
        UIManager.put("SplitPane.background", bg());
        UIManager.put("SplitPaneDivider.draggingColor", accent());
        // Полоса разделителя — тоже поверхность, и её цвет тема задаёт отдельным
        // ключом: без него она оставалась светлой на тёмной теме («панелька справа
        // всё ещё белая» — жалоба дизайнера 13.08.2026).
        UIManager.put("SplitPaneDivider.background", bg());
        UIManager.put("SplitPaneDivider.gripColor", ink3());
        UIManager.put("SplitPaneDivider.oneTouchArrowColor", ink3());
        UIManager.put("SplitPaneDivider.oneTouchHoverArrowColor", ink());
        UIManager.put("SplitPaneDivider.border", javax.swing.BorderFactory.createEmptyBorder());
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", px(11));
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("ToolTip.background", panel());
        UIManager.put("ToolTip.foreground", ink());
        // Русские подписи стандартных диалогов — как в 1.0 (в JDK их нет).
        kelium.gui.Ui.init0();
    }

    // ==================== перекраска при смене темы ====================
    /**
     * ТОТ ЖЕ ЦВЕТ, НО В ДЕЙСТВУЮЩЕЙ ТЕМЕ. Возвращает {@code null}, если краска
     * не из нашей палитры — такую трогать нельзя (цвета мест, келемия, поля).
     *
     * <p>Зачем. Цвет, выставленный кодом ({@code setBackground(Theme.panel())}),
     * ЗАПЕКАЕТСЯ в компонент: смена темы его не трогает, потому что Swing
     * обновляет только те краски, что пришли от оформления. Отсюда жалоба
     * дизайнера 14.08.2026 — часть экранов оставалась в прежней теме до
     * перезапуска. Перебирать все такие места руками бесполезно: их находят по
     * одному, а забывают пачками. Здесь краска УЗНАЁТСЯ по палитре
     * противоположной темы и меняется на равную ей по смыслу.
     */
    public static Color counterpart(Color c) {
        if (c == null) {
            return null;
        }
        // ОГОВОРКА: в светлой палитре панель и подложка поля — обе белые, и
        // отличить их по краске нельзя. Такая подложка перекрасится в цвет
        // панели; разница между ними на тёмной теме — четыре единицы яркости,
        // глазом не видна. Кому важно точно (сцена с полем) — красит себя сам.
        Color[] other = dark ? LIGHT : DARK;
        for (int i = 0; i < other.length; i++) {
            if (other[i].getRGB() == c.getRGB()) {
                return base(i);
            }
        }
        return null;
    }

    /**
     * ПРОЙТИ ПО ДЕРЕВУ И ПЕРЕКРАСИТЬ всё, что осталось в прежней теме. Красится
     * только то, что кто-то выставил кодом: краски, пришедшие от оформления,
     * помечены {@code UIResource} и уже обновлены самим FlatLaf.
     *
     * @return сколько красок поменяли (для тестов и отладки)
     */
    public static int restyleTree(java.awt.Component c) {
        int changed = 0;
        if (c == null) {
            return 0;
        }
        if (!(c.getBackground() instanceof javax.swing.plaf.UIResource)) {
            Color swap = counterpart(c.getBackground());
            if (swap != null) {
                c.setBackground(swap);
                changed++;
            }
        }
        if (!(c.getForeground() instanceof javax.swing.plaf.UIResource)) {
            Color swap = counterpart(c.getForeground());
            if (swap != null) {
                c.setForeground(swap);
                changed++;
            }
        }
        if (c instanceof java.awt.Container box) {
            for (java.awt.Component ch : box.getComponents()) {
                changed += restyleTree(ch);
            }
        }
        // У прокрутки полоса и уголки — отдельные дети, но ещё у неё есть
        // видовое окно с собственным фоном, до которого getComponents доходит
        // не всегда: спрашиваем явно.
        if (c instanceof javax.swing.JScrollPane sp) {
            changed += restyleTree(sp.getViewport());
            changed += restyleTree(sp.getVerticalScrollBar());
            changed += restyleTree(sp.getHorizontalScrollBar());
        }
        return changed;
    }

    // ==================== мелочи ====================
    public static Color lighten(Color c, double k) {
        return new Color(
            (int) Math.min(255, c.getRed() + (255 - c.getRed()) * k),
            (int) Math.min(255, c.getGreen() + (255 - c.getGreen()) * k),
            (int) Math.min(255, c.getBlue() + (255 - c.getBlue()) * k));
    }

    public static Color darken(Color c, double k) {
        return new Color((int) (c.getRed() * (1 - k)), (int) (c.getGreen() * (1 - k)),
            (int) (c.getBlue() * (1 - k)));
    }

    public static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
            (int) Math.round(255 * Math.max(0, Math.min(1, a))));
    }

    /** Панель-карточка: фон панели, рамка в один пиксель, скругление. */
    public static void card(JComponent c) {
        c.setBackground(panel());
        c.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(border(), 1, true),
            javax.swing.BorderFactory.createEmptyBorder(
                PAD_TILE, PAD_PANEL, PAD_TILE, PAD_PANEL)));
        c.setOpaque(true);
    }
}
