package kelium.gui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.event.MouseInputAdapter;

import kelium.report.FieldGeometry;
import kelium.report.ReplayRecord;
import kelium.engine.Placement;

/**
 * FieldView — главный вид: поле партии на конкретном ШАГЕ.
 *
 * <p>Рисует ровно то же, что SVG-рендер отчётов, но средствами Swing и из той же
 * общей геометрии ({@link FieldGeometry}): гексы, тайлы зарождения, нейтралов,
 * контейнеры, авторские силуэты зданий и войск с цветом игрока и правильным
 * поворотом, кубики урона и энергии. Сверху — подсветка происходящего: чей ход,
 * стрелки перемещений, вспышки боя, значки постройки, мигание урона и
 * уничтожения (заказ §4.4).
 *
 * <p>Масштаб колесом мыши, перетаскивание — левой кнопкой (как в конструкторе
 * раскладок).
 */
public final class FieldView extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Базовый радиус гекса; масштаб окна умножается на него. */
    private static final double BASE = FieldGeometry.DEFAULT_SIZE;

    private ReplayRecord record;
    private ReplayRecord.Frame frame;

    private double zoom = 1.0;
    private double panX = 0;
    private double panY = 0;
    private boolean fitPending = true;
    /**
     * Пока пользователь сам не крутил колесо и не таскал поле, оно ВПИСЫВАЕТСЯ в
     * окно заново при каждом изменении размера — иначе после растягивания окна
     * поле остаётся крошечным в углу.
     */
    private boolean autoFit = true;

    /** Что показывать (меню «Вид»). */
    private boolean showIds = true;
    private boolean showHighlights = true;
    private boolean showLegendHint = true;

    /** Мигание для урона и уничтожения — «заметно, хотя бы на пару кадров». */
    private boolean blink = true;
    private final Timer blinkTimer;

    public FieldView() {
        setOpaque(true);
        setBackground(new Color(0xFB, 0xFB, 0xFB));
        // Свой шрифт, а не унаследованный: компонент рисуют и вне окна
        // (например, снимком в PNG), где родителя ещё нет и шрифт был бы null.
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        setFocusable(true);
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseInputAdapter mouse = new MouseInputAdapter() {
            private Point drag;

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                requestFocusInWindow();
                drag = e.getPoint();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                drag = null;
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (drag == null) {
                    return;
                }
                autoFit = false;
                panX += e.getX() - drag.x;
                panY += e.getY() - drag.y;
                drag = e.getPoint();
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(e -> {
            autoFit = false;
            double factor = e.getWheelRotation() < 0 ? 1.12 : 1 / 1.12;
            zoomAt(e.getX(), e.getY(), factor);
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (autoFit) {
                    fitToWindow();
                }
            }
        });
        blinkTimer = new Timer(280, e -> {
            blink = !blink;
            if (showHighlights && frame != null && needsBlink(frame)) {
                repaint();
            }
        });
    }

    // Мигание урона крутится, только пока вид РЕАЛЬНО на экране: иначе таймер
    // держал ссылку на все кадры партии и тикал при свёрнутом окне.
    @Override
    public void addNotify() {
        super.addNotify();
        blinkTimer.start();
    }

    @Override
    public void removeNotify() {
        blinkTimer.stop();
        super.removeNotify();
    }


    /**
     * Обводка толщиной {@code screenPx} ЭКРАННЫХ пикселей. Контекст рисования
     * уже отмасштабирован на {@code zoom}, поэтому голая ширина в мировых
     * единицах на мелком масштабе исчезала, а на крупном превращалась в жирный
     * контур толще самого силуэта.
     */
    private BasicStroke pen(double screenPx) {
        return new BasicStroke((float) (screenPx / Math.max(0.01, zoom)),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    private BasicStroke penDashed(double screenPx, double dash, double gap) {
        float w = (float) (screenPx / Math.max(0.01, zoom));
        return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
            new float[]{(float) (dash / Math.max(0.01, zoom)),
                        (float) (gap / Math.max(0.01, zoom))}, 0f);
    }

    private static boolean needsBlink(ReplayRecord.Frame f) {
        return !f.highlight.damaged.isEmpty() || !f.highlight.destroyed.isEmpty()
            || !f.highlight.attacks.isEmpty();
    }

    /** Задать запись (топология поля берётся из неё). */
    public void setRecord(ReplayRecord rec) {
        this.record = rec;
        this.frame = null;
        // Вписываем заново только пока масштабом распоряжаемся мы: если человек уже
        // приблизил интересный угол, смена настроек не должна его сбрасывать.
        this.fitPending = autoFit;
        repaint();
    }

    /** Сам ли вид распоряжается масштабом (пользователь ещё не крутил колесо). */
    public boolean isAutoFit() {
        return autoFit;
    }

    /** Показать конкретный шаг партии. */
    public void setFrame(ReplayRecord.Frame f) {
        this.frame = f;
        repaint();
    }

    /** Текущий масштаб (1.0 = как в отчётах). */
    public double zoom() {
        return zoom;
    }

    /** Изменить масштаб относительно центра окна. */
    public void zoomBy(double factor) {
        autoFit = false;
        zoomAt(getWidth() / 2, getHeight() / 2, factor);
    }

    private void zoomAt(int sx, int sy, double factor) {
        double next = Math.max(0.25, Math.min(4.0, zoom * factor));
        double k = next / zoom;
        // точка под курсором должна остаться на месте
        panX = sx - k * (sx - panX);
        panY = sy - k * (sy - panY);
        zoom = next;
        repaint();
    }

    /** Вписать поле в окно (и снова разрешить авто-вписывание при растягивании). */
    public void fitToWindow() {
        autoFit = true;
        if (record == null || record.hexes.isEmpty() || getWidth() < 40 || getHeight() < 40) {
            fitPending = true;
            return;
        }
        double minx = Double.MAX_VALUE;
        double maxx = -Double.MAX_VALUE;
        double miny = Double.MAX_VALUE;
        double maxy = -Double.MAX_VALUE;
        for (ReplayRecord.HexInfo h : record.hexes) {
            double[] c = FieldGeometry.hexCenter(h.q, h.r, BASE);
            minx = Math.min(minx, c[0] - BASE);
            maxx = Math.max(maxx, c[0] + BASE);
            miny = Math.min(miny, c[1] - BASE);
            maxy = Math.max(maxy, c[1] + BASE);
        }
        double margin = 24;
        double kx = (getWidth() - 2 * margin) / (maxx - minx);
        double ky = (getHeight() - 2 * margin) / (maxy - miny);
        zoom = Math.max(0.25, Math.min(4.0, Math.min(kx, ky)));
        panX = getWidth() / 2.0 - zoom * (minx + maxx) / 2;
        panY = getHeight() / 2.0 - zoom * (miny + maxy) / 2;
        fitPending = false;
        repaint();
    }

    /** Показывать ли идентификаторы гексов. */
    public void setShowIds(boolean on) {
        showIds = on;
        repaint();
    }

    /** Показывать ли подсветку последнего действия. */
    public void setShowHighlights(boolean on) {
        showHighlights = on;
        repaint();
    }

    /** Показывать ли подпись «чей ход» поверх поля. */
    public void setShowTurnCaption(boolean on) {
        showLegendHint = on;
        repaint();
    }

    // ВТОРОСТЕПЕННЫЕ ЭЛЕМЕНТЫ (меню «Вид → Что показывать на поле»). Сами
    // жетоны, тайлы и контейнеры не выключаются — только цифры и пометки
    // поверх них: их отключают, когда поле нужно рассмотреть чисто.
    /** Показывать кубики урона на жетонах. */
    public void setShowDamage(boolean on) {
        kelium.report.FieldPainter.showDamage = on;
        repaint();
    }

    /** Показывать остаток келемия на тайлах зарождения. */
    public void setShowKelium(boolean on) {
        kelium.report.FieldPainter.showKelium = on;
        repaint();
    }

    /** Показывать метку запитанности (кубик энергии) на зданиях. */
    public void setShowEnergy(boolean on) {
        kelium.report.FieldPainter.showEnergy = on;
        repaint();
    }

    /** Показывать слабую подкраску «чей гекс и чья зона стройки». */
    public void setShowOwnership(boolean on) {
        kelium.report.FieldPainter.showOwnership = on;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(700, 480);
    }

    // ==================== отрисовка ====================
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());

        if (record == null || frame == null || frame.snapshot == null) {
            g.setColor(new Color(0x88, 0x88, 0x88));
            g.setFont(getFont().deriveFont(Font.PLAIN, 14f));
            g.drawString("Партия не загружена — задай параметры и нажми «Сыграть и показать»",
                24, 40);
            g.dispose();
            return;
        }
        if (fitPending) {
            fitToWindow();
        }

        g.translate(panX, panY);
        g.scale(zoom, zoom);
        drawField(g, frame);
        g.dispose();

        if (showLegendHint) {
            Graphics2D gt = (Graphics2D) g0.create();
            gt.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawTurnCaption(gt, frame);
            gt.dispose();
        }
        // ИТОГИ ПАРТИИ — на самом последнем шаге, поверх поблёкшего поля.
        if (isLastFrame()) {
            Graphics2D gp = (Graphics2D) g0.create();
            gp.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            gp.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawPodium(gp);
            gp.dispose();
        }
    }

    /**
     * Показывать ли ИТОГИ. Только на последнем шаге ДОИГРАННОЙ партии.
     *
     * <p>Раньше условие было «это последний кадр записи», и на стартовой
     * расстановке (в ней один кадр, и он же последний) итоги показывались ещё до
     * начала матча — баг, найденный дизайнером 12.08.2026. Признак настоящего
     * финала: у записи есть победитель и в ней больше одного шага.
     */
    private boolean isLastFrame() {
        return record != null && frame != null && record.winner != null
            && record.frames.size() > 1
            && record.frames.get(record.frames.size() - 1) == frame;
    }

    /**
     * ИТОГОВАЯ ТАБЛИЦА-ПЬЕДЕСТАЛ (просьба дизайнера 12.08.2026): поле блёкнет,
     * поверх него вертикальный список — победитель наверху, остальные ниже по
     * убыванию победных очков. У победителя подписан СПОСОБ победы: по очкам,
     * военная (второе ЦУ) или по супер заданию.
     */
    private void drawPodium(Graphics2D g) {
        List<ReplayRecord.Player> ps = new ArrayList<>(frame.snapshot.players);
        ps.sort((a, b) -> Integer.compare(b.vp.getOrDefault("total", 0),
            a.vp.getOrDefault("total", 0)));
        // победитель — тот, кого объявил движок; иначе первый по очкам
        Integer winner = record.winner;
        if (winner != null) {
            ps.sort((a, b) -> {
                if (a.seat == winner) {
                    return -1;
                }
                if (b.seat == winner) {
                    return 1;
                }
                return Integer.compare(b.vp.getOrDefault("total", 0),
                    a.vp.getOrDefault("total", 0));
            });
        }

        // 1) поле уходит в тень — итоги читаются, партия видна фоном
        g.setColor(new Color(0xF2, 0xF1, 0xEC, 205));
        g.fillRect(0, 0, getWidth(), getHeight());

        int rowH = 46;
        int cardW = Math.min(520, Math.max(340, getWidth() - 80));
        // Подпись способа победы бывает длинной — заранее считаем, во сколько
        // строк она ляжет, и на столько же растягиваем табличку. Раньше текст
        // просто вылезал за рамку (замечание дизайнера 12.08.2026).
        g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        List<String> howLines = wrapText(g, winCondition(), cardW - 44);
        int cardH = 78 + howLines.size() * 15 + ps.size() * rowH;
        int x = (getWidth() - cardW) / 2;
        int y = Math.max(12, (getHeight() - cardH) / 2);

        // 2) сама табличка
        g.setColor(new Color(0xFF, 0xFF, 0xFF, 245));
        g.fillRoundRect(x, y, cardW, cardH, 18, 18);
        g.setColor(new Color(0x33, 0x33, 0x33));
        g.setStroke(new BasicStroke(2.2f));
        g.drawRoundRect(x, y, cardW, cardH, 18, 18);

        g.setFont(getFont().deriveFont(Font.BOLD, 20f));
        g.setColor(new Color(0x22, 0x22, 0x22));
        String head = "ПАРТИЯ ОКОНЧЕНА";
        g.drawString(head, x + (cardW - g.getFontMetrics().stringWidth(head)) / 2, y + 32);

        g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        g.setColor(new Color(0x77, 0x77, 0x77));
        int hy = y + 50;
        for (String line : howLines) {
            g.drawString(line, x + (cardW - g.getFontMetrics().stringWidth(line)) / 2, hy);
            hy += 15;
        }

        // 3) пьедестал: победитель крупнее и с золотой полосой
        int ry = hy + 6;
        for (int i = 0; i < ps.size(); i++) {
            ReplayRecord.Player p = ps.get(i);
            boolean champ = i == 0;
            int h = rowH - 6;
            g.setColor(champ ? new Color(0xFD, 0xF3, 0xD2) : new Color(0xF7, 0xF7, 0xF5));
            g.fillRoundRect(x + 14, ry, cardW - 28, h, 10, 10);
            g.setColor(champ ? new Color(0xD9, 0xA9, 0x18) : new Color(0xDD, 0xDD, 0xD8));
            g.setStroke(new BasicStroke(champ ? 2.0f : 1.0f));
            g.drawRoundRect(x + 14, ry, cardW - 28, h, 10, 10);

            // место в списке
            g.setFont(getFont().deriveFont(Font.BOLD, champ ? 20f : 15f));
            g.setColor(champ ? new Color(0x8A, 0x63, 0x00) : new Color(0x99, 0x99, 0x99));
            g.drawString(String.valueOf(i + 1), x + 26, ry + h / 2 + (champ ? 7 : 5));

            // цветной жетон игрока
            int chip = champ ? 26 : 20;
            int cxp = x + 52;
            int cyp = ry + (h - chip) / 2;
            g.setColor(seatColor(p.seat));
            g.fillOval(cxp, cyp, chip, chip);
            g.setColor(seatStroke(p.seat));
            g.setStroke(new BasicStroke(1.6f));
            g.drawOval(cxp, cyp, chip, chip);
            g.setColor(Color.WHITE);
            g.setFont(getFont().deriveFont(Font.BOLD, chip * 0.6f));
            String num = String.valueOf(p.seat + 1);
            g.drawString(num, cxp + (chip - g.getFontMetrics().stringWidth(num)) / 2,
                cyp + chip - chip / 4);

            // победные очки справа считаем ПЕРВЫМИ: от их ширины зависит, сколько
            // места остаётся имени бота, чтобы оно не наползало на очки
            int total = p.vp.getOrDefault("total", 0);
            g.setFont(getFont().deriveFont(Font.BOLD, champ ? 19f : 15f));
            String vpText = total + " ПО";
            int tw = g.getFontMetrics().stringWidth(vpText);

            // имя бота
            int nameX = cxp + chip + 10;
            int nameRoom = (x + cardW - 26 - tw - 12) - nameX;
            g.setFont(getFont().deriveFont(champ ? Font.BOLD : Font.PLAIN, champ ? 15f : 13f));
            g.setColor(new Color(0x22, 0x22, 0x22));
            g.drawString(clip(g, record.playerName(p.seat), nameRoom), nameX, ry + h / 2 + 5);

            g.setFont(getFont().deriveFont(Font.BOLD, champ ? 19f : 15f));
            g.setColor(champ ? new Color(0x8A, 0x63, 0x00) : new Color(0x44, 0x44, 0x44));
            g.drawString(vpText, x + cardW - 26 - tw, ry + h / 2 + (champ ? 7 : 5));

            ry += rowH;
        }
    }

    /** Разбить текст по словам на строки, влезающие в {@code width}. */
    private static List<String> wrapText(Graphics2D g, String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(probe) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    /** Человеческая подпись способа победы для итоговой таблицы. */
    private String winCondition() {
        String c = record.condition == null ? "" : record.condition;
        return switch (c) {
            case "victory_points" -> "победа по победным очкам";
            case "military" -> "ВОЕННАЯ победа: уничтожено второе ЦУ";
            case "super_objective" -> "победа по СУПЕР ЗАДАНИЮ: проект развёрнут";
            // Мирные концы: партия дошла до предела, победитель — по очкам.
            case "all_peaks_occupied" -> "конец по науке: заняты все три вершины треков "
                + "· победа по очкам";
            case "last_spawn_tile" -> "конец по келемию: на поле остался последний тайл "
                + "зарождения · победа по очкам";
            case "" -> "партия завершена";
            default -> "условие конца: " + c;
        };
    }

    /**
     * Плашка в углу: раунд, круг, чей ход И ЧТО ИМЕННО произошло на этом шаге.
     * Строку события дублируем сюда, чтобы не бегать глазами к логу внизу окна.
     */
    private void drawTurnCaption(Graphics2D g, ReplayRecord.Frame f) {
        ReplayRecord.Snapshot s = f.snapshot;
        String head = "Раунд " + s.round + (s.circle > 0 ? " · круг " + s.circle : "");
        String who = s.active != null ? "Ходит: " + record.playerName(s.active)
                                      : "Общая фаза раунда";
        int maxW = Math.max(220, getWidth() - 24);
        Font headFont = getFont().deriveFont(Font.BOLD, 13f);
        Font eventFont = getFont().deriveFont(Font.PLAIN, 13f);
        Font thoughtFont = getFont().deriveFont(Font.ITALIC, 12f);

        List<Object[]> lines = new ArrayList<>();   // {текст, шрифт, цвет}
        Color accent = s.active != null ? seatColor(s.active).darker()
                                        : new Color(0x55, 0x55, 0x55);
        lines.add(new Object[]{head, headFont, new Color(0x22, 0x22, 0x22)});
        lines.add(new Object[]{who, headFont, accent});
        if (f.log != null && !f.log.isBlank()) {
            g.setFont(eventFont);
            lines.add(new Object[]{clip(g, f.log, maxW - 24), eventFont,
                new Color(0x33, 0x33, 0x33)});
        }
        if (!f.thoughts.isEmpty()) {
            ReplayRecord.Thought t = f.thoughts.get(f.thoughts.size() - 1);
            g.setFont(thoughtFont);
            lines.add(new Object[]{clip(g, "«" + t.text + "»", maxW - 24), thoughtFont,
                seatColor(t.seat).darker()});
        }

        int w = 0;
        for (Object[] ln : lines) {
            g.setFont((Font) ln[1]);
            w = Math.max(w, g.getFontMetrics().stringWidth((String) ln[0]));
        }
        w = Math.min(maxW, w + 20);
        int h = 10 + lines.size() * 18;
        g.setColor(new Color(255, 255, 255, 225));
        g.fillRoundRect(8, 8, w, h, 10, 10);
        g.setColor(s.active != null ? seatColor(s.active) : new Color(0x66, 0x66, 0x66));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(8, 8, w, h, 10, 10);
        int y = 26;
        for (Object[] ln : lines) {
            g.setFont((Font) ln[1]);
            g.setColor((Color) ln[2]);
            g.drawString((String) ln[0], 18, y);
            y += 18;
        }
    }

    /** Обрезать строку по ширине, добавив многоточие. */
    private static String clip(Graphics2D g, String text, int maxWidth) {
        java.awt.FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String tail = "…";
        int n = text.length();
        while (n > 1 && fm.stringWidth(text.substring(0, n) + tail) > maxWidth) {
            n--;
        }
        return text.substring(0, n) + tail;
    }

    private void drawField(Graphics2D g, ReplayRecord.Frame f) {
        ReplayRecord.Snapshot s = f.snapshot;
        // ЕДИНЫЙ рендер: то же самое рисует и картинка в отчёте.
        kelium.report.FieldPainter.paintField(
            new kelium.report.Java2DCanvas(g, zoom, getFont()),
            BASE, record.hexes, s, 0, 0, showIds);

        // Поверх — то, чего нет в отчёте: рамка гексов активного игрока.
        if (s.active != null) {
            Set<String> activeHexes = new HashSet<>();
            for (ReplayRecord.Tok t : s.tokens) {
                if (t.owner == s.active && t.hexId != null && t.alive) {
                    activeHexes.add(t.hexId);
                }
            }
            Map<String, ReplayRecord.HexInfo> info = new LinkedHashMap<>();
            for (ReplayRecord.HexInfo h : record.hexes) {
                info.put(h.id, h);
            }
            g.setColor(withAlpha(seatColor(s.active), 170));
            g.setStroke(penDashed(2.4, 6, 4));
            for (String id : activeHexes) {
                ReplayRecord.HexInfo hi = info.get(id);
                if (hi == null) {
                    continue;
                }
                double[] c = FieldGeometry.hexCenter(hi.q, hi.r, BASE);
                g.draw(hexPath(c[0], c[1], BASE * 0.97));
            }
        }
        if (showHighlights) {
            Map<String, ReplayRecord.HexInfo> info = new LinkedHashMap<>();
            for (ReplayRecord.HexInfo h : record.hexes) {
                info.put(h.id, h);
            }
            drawHighlights(g, f, info);
        }
    }

    // ---------------- подсветки последнего действия ----------------
    private void drawHighlights(Graphics2D g, ReplayRecord.Frame f,
                                Map<String, ReplayRecord.HexInfo> info) {
        ReplayRecord.Highlight h = f.highlight;
        Integer seat = f.snapshot.active;
        Color accent = seat != null ? seatColor(seat) : new Color(0x44, 0x44, 0x44);

        for (String[] mv : h.moves) {
            double[] a = center(info, mv[0]);
            double[] b = center(info, mv[1]);
            if (a == null || b == null) {
                continue;
            }
            arrow(g, a, b, accent, 3.2f);
        }
        for (String[] at : h.attacks) {
            double[] a = center(info, at[0]);
            double[] b = center(info, at[1]);
            if (a == null || b == null) {
                continue;
            }
            g.setColor(withAlpha(new Color(0xD3, 0x2F, 0x2F), blink ? 235 : 120));
            g.setStroke(pen(4.5));
            g.draw(new Line2D.Double(a[0], a[1], b[0], b[1]));
            burst(g, b[0], b[1], BASE * 0.42, blink ? 235 : 110);
        }
        for (String id : h.builds) {
            double[] c = center(info, id);
            if (c == null) {
                continue;
            }
            g.setColor(withAlpha(accent, 210));
            g.setStroke(pen(3.2));
            g.draw(hexPath(c[0], c[1], BASE * 0.86));
            g.setColor(accent.darker());
            outlined(g, "+", c[0], c[1] - BASE * 0.55, font(Font.BOLD, BASE * 0.42));
        }
        for (String id : h.damaged) {
            double[] c = center(info, id);
            if (c == null) {
                continue;
            }
            g.setColor(withAlpha(new Color(0xD3, 0x2F, 0x2F), blink ? 200 : 60));
            g.setStroke(pen(3.6));
            g.draw(hexPath(c[0], c[1], BASE * 0.92));
        }
        for (String id : h.destroyed) {
            double[] c = center(info, id);
            if (c == null) {
                continue;
            }
            g.setColor(withAlpha(new Color(0xB7, 0x1C, 0x1C), blink ? 240 : 90));
            g.setStroke(pen(5));
            double r = BASE * 0.44;
            g.draw(new Line2D.Double(c[0] - r, c[1] - r, c[0] + r, c[1] + r));
            g.draw(new Line2D.Double(c[0] + r, c[1] - r, c[0] - r, c[1] + r));
        }
    }

    private void burst(Graphics2D g, double x, double y, double r, int alpha) {
        g.setColor(new Color(0xFF, 0xB0, 0x30, alpha));
        Path2D star = new Path2D.Double();
        for (int i = 0; i < 12; i++) {
            double ang = Math.toRadians(30.0 * i);
            double rr = i % 2 == 0 ? r : r * 0.45;
            double px = x + rr * Math.cos(ang);
            double py = y + rr * Math.sin(ang);
            if (i == 0) {
                star.moveTo(px, py);
            } else {
                star.lineTo(px, py);
            }
        }
        star.closePath();
        g.fill(star);
    }

    private void arrow(Graphics2D g, double[] from, double[] to, Color color, float width) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double len = Math.hypot(dx, dy);
        if (len < 1) {
            return;
        }
        double ux = dx / len;
        double uy = dy / len;
        double sx = from[0] + ux * BASE * 0.30;
        double sy = from[1] + uy * BASE * 0.30;
        double ex = to[0] - ux * BASE * 0.34;
        double ey = to[1] - uy * BASE * 0.34;
        g.setColor(withAlpha(Color.WHITE, 190));
        g.setStroke(pen(width + 2.6));
        g.draw(new Line2D.Double(sx, sy, ex, ey));
        g.setColor(color);
        g.setStroke(pen(width));
        g.draw(new Line2D.Double(sx, sy, ex, ey));
        double head = BASE * 0.20;
        Path2D tip = new Path2D.Double();
        tip.moveTo(ex, ey);
        tip.lineTo(ex - head * (ux * 0.87 - uy * 0.5), ey - head * (uy * 0.87 + ux * 0.5));
        tip.lineTo(ex - head * (ux * 0.87 + uy * 0.5), ey - head * (uy * 0.87 - ux * 0.5));
        tip.closePath();
        g.fill(tip);
    }

    private double[] center(Map<String, ReplayRecord.HexInfo> info, String hexId) {
        ReplayRecord.HexInfo hi = info.get(hexId);
        return hi == null ? null : FieldGeometry.hexCenter(hi.q, hi.r, BASE);
    }

    // ---------------- подсказка под курсором ----------------
    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        if (record == null || frame == null || frame.snapshot == null) {
            return null;
        }
        Point2D p;
        try {
            AffineTransform at = new AffineTransform();
            at.translate(panX, panY);
            at.scale(zoom, zoom);
            p = at.createInverse().transform(e.getPoint(), null);
        } catch (java.awt.geom.NoninvertibleTransformException ex) {
            return null;
        }
        int[] qr = FieldGeometry.hexAt(p.getX(), p.getY(), BASE);
        for (ReplayRecord.HexInfo hi : record.hexes) {
            if (hi.q == qr[0] && hi.r == qr[1]) {
                return hexTip(hi);
            }
        }
        return null;
    }

    private String hexTip(ReplayRecord.HexInfo hi) {
        ReplayRecord.Snapshot s = frame.snapshot;
        StringBuilder sb = new StringBuilder("<html><b>Гекс " + hi.id + "</b>");
        if ("FORBIDDEN".equals(hi.kind)) {
            sb.append("<br>запретный — сюда нельзя");
        }
        for (ReplayRecord.HexState st : s.hexes) {
            if (!st.id.equals(hi.id)) {
                continue;
            }
            if (st.spawn != null) {
                sb.append("<br>тайл зарождения")
                  .append(st.spawn.start ? " (стартовый)" : "")
                  .append(": келемия ").append(st.spawn.kelium)
                  .append(st.spawn.stack > 1 ? ", двойной" : "")
                  .append(st.spawn.flipped ? ", перевёрнут" : "");
            }
            if (st.containerCell >= 0) {
                sb.append("<br>печатный контейнер: ")
                  .append(st.containerCell == 6 ? "воздушная ячейка" : "ячейка " + st.containerCell);
            }
            if (st.energyCell >= 0) {
                sb.append("<br>жёлтая ячейка: ").append(st.energyCell)
                  .append(" (только на ней энергостанция даёт номинал)");
            }
            for (ReplayRecord.Neutral n : st.neutrals) {
                sb.append("<br>нейтральная постройка (")
                  .append(n.big ? "большая" : "малая").append(')');
            }
        }
        for (ReplayRecord.Tok t : s.tokens) {
            if (!hi.id.equals(t.hexId) || !t.alive) {
                continue;
            }
            sb.append("<br>").append(record.playerName(t.owner)).append(": ")
              .append(t.building ? GameRecorder.buildingName(t.type)
                                 : GameRecorder.unitName(t.type));
            if (t.level != null) {
                sb.append(" №").append(t.level);
            }
            sb.append(" — прочность ").append(Math.max(0, t.hp - t.damage)).append('/').append(t.hp);
            if (t.building && t.energySlots > 0) {
                sb.append(", энергия ").append(t.energyPlaced).append('/').append(t.energySlots);
            }
        }
        return sb.append("</html>").toString();
    }

    // ---------------- мелочи ----------------
    private static int footprint(String buildingTypeCode) {
        return kelium.engine.Placement.footprint(
            kelium.core.BuildingType.fromCode(buildingTypeCode));
    }

    private static String unitLetter(String type) {
        return switch (type) {
            case "infantry" -> "п";
            case "vehicle" -> "т";
            case "aircraft" -> "а";
            default -> "в";
        };
    }

    private static Path2D hexPath(double cx, double cy, double r) {
        Path2D p = new Path2D.Double();
        double[][] pts = FieldGeometry.hexCorners(cx, cy, r);
        p.moveTo(pts[0][0], pts[0][1]);
        for (int i = 1; i < 6; i++) {
            p.lineTo(pts[i][0], pts[i][1]);
        }
        p.closePath();
        return p;
    }

    /**
     * Шрифт для подписи на поле. Размер задаётся в МИРОВЫХ единицах, но снизу
     * ограничен так, чтобы на ЭКРАНЕ буква не была мельче {@code MIN_TEXT_PX}:
     * при вписывании большого поля в невысокое окно масштаб падает до 0,3, и
     * подписи зданий превращались в нечитаемые 3 пикселя.
     */
    private static final double MIN_TEXT_PX = 8.5;

    private Font font(int style, double size) {
        double world = size;
        if (zoom > 0 && world * zoom < MIN_TEXT_PX) {
            world = MIN_TEXT_PX / zoom;
        }
        return getFont().deriveFont(style, (float) Math.max(4, world));
    }

    private void centered(Graphics2D g, String text, double cx, double baselineY, Font f) {
        Font old = g.getFont();
        g.setFont(f);
        Rectangle2D b = g.getFontMetrics().getStringBounds(text, g);
        g.drawString(text, (float) (cx - b.getWidth() / 2), (float) baselineY);
        g.setFont(old);
    }

    /** Белый текст с тёмной обводкой — читается на любом цвете жетона. */
    private void outlined(Graphics2D g, String text, double cx, double baselineY, Font f) {
        Font old = g.getFont();
        g.setFont(f);
        Rectangle2D b = g.getFontMetrics().getStringBounds(text, g);
        float x = (float) (cx - b.getWidth() / 2);
        java.awt.font.GlyphVector gv = f.createGlyphVector(g.getFontRenderContext(), text);
        Shape outline = gv.getOutline(x, (float) baselineY);
        g.setColor(new Color(0, 0, 0, 160));
        g.setStroke(new BasicStroke((float) (f.getSize2D() * 0.28), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        g.draw(outline);
        g.setColor(Color.WHITE);
        g.fill(new Area(outline));
        g.setFont(old);
    }

    static Color parse(String hex) {
        return new Color(Integer.parseInt(hex.substring(1), 16));
    }

    // Цвета разбираются ОДИН раз: seatColor вызывается десятки раз на кадр и
    // ещё на каждую видимую строку лога.
    private static final Color[] TOKEN_COLORS = colors(FieldGeometry.SEAT_TOKEN);
    private static final Color[] STROKE_COLORS = colors(FieldGeometry.SEAT_STROKE);
    private static final Color[] FILL_COLORS = colors(FieldGeometry.SEAT_FILL);

    private static Color[] colors(String[] hex) {
        Color[] out = new Color[hex.length];
        for (int i = 0; i < hex.length; i++) {
            out[i] = parse(hex[i]);
        }
        return out;
    }

    /** Цвет силуэта жетона игрока. */
    public static Color seatColor(int seat) {
        return TOKEN_COLORS[seat % 4];
    }

    /** Обводка жетонов и рамок игрока. */
    public static Color seatStroke(int seat) {
        return STROKE_COLORS[seat % 4];
    }

    /** Бледная заливка игрока (подложки, панели). */
    public static Color seatFill(int seat) {
        return FILL_COLORS[seat % 4];
    }

    static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
