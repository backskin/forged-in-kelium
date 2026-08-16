package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import kelium.report.ReplayRecord;

/**
 * ModuleSlot — МЕСТО ПОД ЖЕТОН МОДУЛЯ И САМ ЖЕТОН.
 *
 * <p>Планшет показывал модули одной кучкой цветных квадратиков со счётчиком:
 * «красных два, синих один». По ней нельзя было прочитать ни КАКОЙ это жетон,
 * ни КУДА он поставлен, ни какой стороной лежит (замечание дизайнера
 * 15.08.2026). Здесь жетон рисуется на своём месте — там же, где он лежит на
 * столе, — и каждое место видно даже пустым.
 *
 * <p>ГДЕ ЧЕЙ СЛОТ — по правилам, а не по симметрии: <b>красный</b> ложится на
 * вторичный ряд атаки РОДА ВОЙСК, поэтому его место — под родом; <b>синий</b>
 * накрывает зону сборки ЗДАНИЯ, поэтому его место — под военным зданием.
 * Рисовать «под каждым зданием красный и синий» было бы наглядно, но неверно:
 * красному под зданием лежать негде.
 *
 * <p>ЗОЛОТО НЕ ПОДМЕНЯЕТ ЦВЕТ. Позолочённая сторона показана золотой каймой и
 * уголком поверх жетона СВОЕГО цвета, а не заливкой золотом целиком: иначе
 * золотой синий и золотой красный выглядели бы одинаково, и по планшету нельзя
 * было бы сказать, что именно усилено.
 */
final class ModuleSlot {

    private ModuleSlot() {
    }

    /** Красный — атака рода войск. */
    static Color red() {
        return Theme.isDark() ? new Color(0xD1453B) : new Color(0xC0392B);
    }

    /** Синий — сборка здания. */
    static Color blue() {
        return Theme.isDark() ? new Color(0x3D7CC9) : new Color(0x2C62A8);
    }

    /** Серый — жетон модуля хранилища: он не красный и не синий. */
    static Color store() {
        return Theme.isDark() ? new Color(0x8C93A1) : new Color(0x6E7484);
    }

    /**
     * Нарисовать место под модуль. {@code m} равен null — место пустое, и тогда
     * рисуется только штриховая обводка: место есть, жетона на нём нет.
     *
     * @param side длина стороны квадратного места
     */
    static void paint(Graphics2D g, ReplayRecord.Module m, Color colour,
                      double x, double y, double side) {
        // МЕСТО И ЖЕТОН — ДВЕ РАЗНЫЕ ВЕЩИ (просьба дизайнера 15.08.2026). Раньше
        // жетон заполнял место целиком, и на планшете нельзя было отличить
        // «место, на котором лежит жетон» от «просто квадратик». Теперь у места
        // своя тонкая рамка, а жетон лежит ВНУТРИ неё с зазором — как на столе,
        // где картонный жетон меньше напечатанной под него площадки.
        paintPlace(g, x, y, side);
        double in = side * 0.15;
        double t = side - 2 * in;
        double tx = x + in;
        double ty = y + in;
        double arc = t * 0.22;
        if (m == null || m.id == null || m.id.isBlank()) {
            return;                          // место есть, жетона на нём нет
        }
        // ТЕЛО ЖЕТОНА — своим цветом всегда, в том числе на золотой стороне.
        g.setColor(colour);
        g.fill(new RoundRectangle2D.Double(tx, ty, t, t, arc, arc));
        g.setColor(Theme.alpha(Color.BLACK, 0.4));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(tx, ty, t, t, arc, arc));
        x = tx;
        y = ty;
        side = t;

        if (m.gold) {
            paintGildMark(g, x, y, side, arc);
        }

        // ИМЯ ЖЕТОНА ПРЯМО НА НЁМ: «C1» у жетонов комплекта, «R1-2» у жетонов из
        // набора «Модули 2.0». Длина разная, а место одно, поэтому кегль
        // ПОДБИРАЕТСЯ под самую длинную строку: фиксированный либо не влезал бы
        // четырьмя знаками, либо был бы напрасно мелким для двух.
        String label = m.id;
        int size = (int) Math.round(side * 0.44);
        java.awt.FontMetrics fm;
        double room = side * 0.86;
        while (true) {
            g.setFont(Theme.mono(size, Font.BOLD));
            fm = g.getFontMetrics();
            if (size <= 7 || fm.stringWidth(label) <= room) {
                break;
            }
            size--;
        }
        // Позолочённый уголок съедает правый верхний угол — подпись сдвигается
        // от него влево, иначе последняя буква тонет в золоте.
        double shift = m.gold ? -side * 0.06 : 0;
        g.setColor(Color.WHITE);
        g.drawString(label, (float) (x + (side - fm.stringWidth(label)) / 2 + shift),
            (float) (y + side / 2 + fm.getAscent() * 0.36));
    }

    /**
     * МЕСТО ПОД ЖЕТОН — напечатанная на планшете площадка. Рисуется ВСЕГДА, и
     * когда жетон на ней лежит, и когда место пустует: так видно, сколько мест
     * вообще есть и сколько ещё свободно.
     */
    static void paintPlace(Graphics2D g, double x, double y, double side) {
        double arc = side * 0.22;
        g.setColor(Theme.alpha(Theme.ink3(), 0.10));
        g.fill(new RoundRectangle2D.Double(x, y, side, side, arc, arc));
        g.setColor(Theme.alpha(Theme.border(), 0.85));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10f, new float[]{Theme.pxf(3), Theme.pxf(3)}, 0f));
        g.draw(new RoundRectangle2D.Double(x, y, side, side, arc, arc));
        g.setStroke(new BasicStroke(1f));
    }

    /**
     * ЗНАК ЗОЛОТОЙ СТОРОНЫ: кайма по краю и залитый уголок сверху справа. Двух
     * признаков сразу нужно потому, что одна кайма на 18 px читается как обычная
     * обводка, а один уголок теряется на пёстром фоне планшета.
     */
    private static void paintGildMark(Graphics2D g, double x, double y, double side,
                                      double arc) {
        Color gold = Theme.points();
        double c = side * 0.42;
        Path2D corner = new Path2D.Double();
        corner.moveTo(x + side - c, y);
        corner.lineTo(x + side, y);
        corner.lineTo(x + side, y + c);
        corner.closePath();
        g.setColor(gold);
        g.fill(corner);
        g.setStroke(new BasicStroke(Theme.pxf(1.6)));
        g.draw(new RoundRectangle2D.Double(x + 0.8, y + 0.8, side - 1.6, side - 1.6,
            arc, arc));
        g.setStroke(new BasicStroke(1f));
    }

    /**
     * ЖЕТОН МОДУЛЯ ХРАНИЛИЩА — круг, а не квадрат: он не из комплекта модулей
     * сборки и атаки, у него свой источник (только зелёный трек) и своя пара
     * сторон, и путать его с ними на планшете нельзя. Сторона выбирается при
     * установке навсегда, поэтому она и подписана знаком: «+» — лишняя ячейка
     * склада, молния — вечный кубик энергии.
     *
     * @param side  сторона места; сам круг вписан в него
     * @param token строка стороны из записи, либо null для пустого места
     */
    static void paintStorageToken(Graphics2D g, String token, double x, double y,
                                  double side) {
        // Место — КВАДРАТНОЕ (как напечатано на планшете), сам жетон — круглый и
        // лежит внутри с зазором (просьба дизайнера 15.08.2026).
        paintPlace(g, x, y, side);
        double d = side * 0.70;
        double cx = x + side / 2;
        double cy = y + side / 2;
        if (token == null || token.isBlank()) {
            return;                          // место есть, жетон на него не положен
        }
        boolean energy = token.contains("energy");
        g.setColor(energy ? Theme.points() : store());
        g.fill(new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d));
        g.setColor(Theme.alpha(Color.BLACK, 0.4));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d));
        g.setColor(energy ? new Color(0x20, 0x20, 0x20) : Color.WHITE);
        if (energy) {
            double h = d * 0.46;
            double wq = d * 0.17;
            Path2D bolt = new Path2D.Double();
            bolt.moveTo(cx + wq * 0.4, cy - h / 2);
            bolt.lineTo(cx - wq, cy + h * 0.06);
            bolt.lineTo(cx - wq * 0.05, cy + h * 0.06);
            bolt.lineTo(cx - wq * 0.4, cy + h / 2);
            bolt.lineTo(cx + wq, cy - h * 0.06);
            bolt.lineTo(cx + wq * 0.05, cy - h * 0.06);
            bolt.closePath();
            g.fill(bolt);
        } else {
            double a = d * 0.42;
            double t = Math.max(Theme.pxf(1.6), d * 0.13);
            g.fill(new RoundRectangle2D.Double(cx - a / 2, cy - t / 2, a, t, t, t));
            g.fill(new RoundRectangle2D.Double(cx - t / 2, cy - a / 2, t, a, t, t));
        }
    }

    // ==================== подписи ====================

    /** Человеческое имя стороны жетона хранилища. */
    static String storageTokenName(String token) {
        if (token == null || token.isBlank()) {
            return "место под жетон хранилища пусто";
        }
        return token.contains("energy")
            ? "жетон хранилища, сторона ЭНЕРГИЯ — вечный универсальный кубик энергии"
            : "жетон хранилища, сторона СКЛАД — ещё одна универсальная ячейка";
    }

    /**
     * ПОДРОБНО О ЖЕТОНЕ — для подсказки под курсором. Пишется по тем полям,
     * которые запись реально хранит: у красного это либо пара целей, либо
     * прибавка к характеристике, у синего — выходы сборки.
     */
    static String describe(ReplayRecord.Module m, boolean redSlot, String slotName) {
        if (m == null || m.id == null || m.id.isBlank()) {
            return (redSlot ? "Красный модуль (атака)" : "Синий модуль (сборка)")
                + "\nМЕСТО ПУСТО — жетон сюда не поставлен.\n"
                + (redSlot
                    ? "Красный ложится на вторичный ряд атаки рода «" + slotName + "»."
                    : "Синий накрывает зону сборки здания «" + slotName + "».");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(redSlot ? "Красный модуль " : "Синий модуль ").append(m.id);
        sb.append(m.gold ? "  ·  ПОЗОЛОЧЁННАЯ сторона" : "  ·  обычная сторона");
        sb.append("\nстоит на: ").append(slotName);
        if (redSlot) {
            if (!m.targets.isEmpty()) {
                sb.append("\nоткрывает цели: ");
                for (int i = 0; i < m.targets.size(); i++) {
                    sb.append(i > 0 ? ", " : "").append(targetName(m.targets.get(i)));
                }
                sb.append(m.gold
                    ? "\nзолото: стреляют ОБЕ цели, каждая оплачивается отдельно"
                    : "\nвыбирается ОДНА из двух целей за атаку");
            } else if (m.stat != null) {
                sb.append("\nподнимает ").append(statName(m.stat)).append(" на +")
                  .append(m.plus);
            }
            if (m.ammo > 0) {
                sb.append("\nцена атаки: ").append(m.ammo).append(" БПР");
            }
        } else {
            sb.append("\nсборка даёт: ").append(m.ammo).append(" БПР ИЛИ ")
              .append(m.units).append(" войск — выбор один за действие");
            if (m.gold) {
                sb.append("\nзолото: помеченный стрелкой параметр уже увеличен на +1");
            }
        }
        return sb.toString();
    }

    private static String targetName(String code) {
        return switch (code) {
            case "infantry" -> "пехота";
            case "vehicle" -> "техника";
            case "aircraft" -> "авиация";
            case "buildings_towers" -> "здания и вышки";
            default -> code;
        };
    }

    private static String statName(String code) {
        return switch (code) {
            case "hp" -> "прочность рода";
            case "speed" -> "скорость рода";
            default -> code;
        };
    }
}
