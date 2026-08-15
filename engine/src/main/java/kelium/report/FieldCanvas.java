package kelium.report;

/**
 * FieldCanvas — ПОВЕРХНОСТЬ, на которой рисуется поле. Ровно те примитивы,
 * которые нужны отрисовке гекса, и ни одного лишнего.
 *
 * <p>Смысл этого интерфейса — чтобы код отрисовки был ОДИН ({@link FieldPainter}),
 * а выходов было два: картинка в отчёт ({@link SvgCanvas}) и окно приложения
 * ({@link Java2DCanvas}). Раньше отрисовка была написана дважды, и правка в
 * одном месте молча расходилась с другим.
 *
 * <p>Договорённости:
 * <ul>
 *   <li>цвета — строки {@code "#rrggbb"} либо {@code "none"};</li>
 *   <li>толщина линии задаётся в ЭКРАННЫХ пикселях: реализация сама приведёт её
 *       к своему масштабу;</li>
 *   <li>размер шрифта — в тех же единицах, что координаты (мировых);</li>
 *   <li>жетон рисуется не готовой матрицей, а её СОСТАВЛЯЮЩИМИ — так обе
 *       реализации собирают одно и то же преобразование, и текст SVG остаётся
 *       разбираемым тестами геометрии.</li>
 * </ul>
 */
public interface FieldCanvas {

    /** Замкнутый многоугольник по точкам {x,y}. */
    void polygon(double[][] points, String fill, String stroke, double strokeWidth);

    /**
     * Силуэт жетона: {@code translate(cx,cy) rotate(rotDeg) scale(k)
     * translate(-anchorX,-anchorY)} — именно в этом порядке.
     */
    void shape(FieldGeometry.Shape shape, double cx, double cy, double rotDeg,
               double k, double anchorX, double anchorY,
               String fill, String stroke, double strokeWidth);

    /**
     * КАРТИНКА-ТЕКСТУРА жетона — теми же составляющими преобразования, что и
     * {@link #shape}: {@code translate(cx,cy) rotate(rotDeg) scale(k)
     * translate(-anchorX,-anchorY)}. Здесь {@code k} — сколько мировых единиц в
     * одном пикселе картинки, а точка привязки задаётся В ПИКСЕЛЯХ картинки.
     * Так текстура садится ровно туда же, где стоял бы силуэт.
     */
    void image(java.awt.image.BufferedImage img, double cx, double cy, double rotDeg,
               double k, double anchorX, double anchorY);

    /** Прямоугольник со скруглением (кубики, контейнеры). */
    void roundRect(double x, double y, double w, double h, double radius,
                   String fill, String stroke, double strokeWidth);

    /** Круг (значок укрытого войска). */
    void circle(double cx, double cy, double r, String fill, String stroke, double strokeWidth);

    /** Текст, выровненный по центру относительно {@code cx}. */
    void text(String text, double cx, double baselineY, double size, boolean bold, String fill);

    /** Текст с контрастной обводкой — читается на любом цвете жетона. */
    void outlinedText(String text, double cx, double baselineY, double size,
                      String fill, String outline);

    /**
     * То же, но ПОВЁРНУТЫЙ вместе с жетоном. Точка {@code (cx, cy)} — середина
     * надписи, поворот считается вокруг неё. Нужен, чтобы подпись на жетоне
     * поворачивалась вместе с ним, а не висела горизонтально поперёк рисунка.
     */
    void outlinedTextRotated(String text, double cx, double cy, double size,
                             String fill, String outline, double rotDeg);

    /** Прозрачность последующих заливок (1 — непрозрачно). Влияет до сброса. */
    void alpha(double value);

    /**
     * ОБРЕЗКА ПО ФИГУРЕ. Всё, что рисуется дальше, видно только внутри
     * многоугольника; {@link #clipOff()} возвращает как было.
     *
     * <p>Зачем: штриховки (зона свободной энергии, выработанный наполовину тайл
     * зарождения) раньше подгонялись под фигуру арифметикой — по хорде вписанной
     * окружности. По углам полосы не доставали до края, а крайняя, наоборот,
     * вылезала наружу (замечание дизайнера 13.08.2026). Правильный приём один:
     * рисовать штрихи с запасом и обрезать по самой фигуре.
     *
     * <p>По умолчанию — ничего не делает: холсты в тестах геометрии обрезка не
     * интересует, и им не нужно её реализовывать.
     */
    default void clipTo(double[][] points) {
    }

    /**
     * ОБРЕЗКА ПО САМОМУ СИЛУЭТУ ЖЕТОНА — теми же составляющими преобразования, что
     * и {@link #shape}. Нужна там, где напечатанное на жетоне должно доходить
     * ровно до его кромки: подбирать под неё радиусы и отступы вручную бесполезно,
     * у каждого силуэта своя форма.
     */
    default void clipToShape(FieldGeometry.Shape shape, double cx, double cy, double rotDeg,
                             double k, double anchorX, double anchorY) {
    }

    /** Снять обрезку, поставленную {@link #clipTo} или {@link #clipToShape}. */
    default void clipOff() {
    }
}
