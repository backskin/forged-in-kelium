package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;

import kelium.dataio.ContentLibrary;
import kelium.report.ReplayRecord;

/**
 * ВКЛАДКА «КАРТЫ» — три колоды, лежащие перед игроками, и их сбросы.
 *
 * <p>ЗАЧЕМ. Состояние колод за партию было невидимо целиком: в записи его не
 * было вовсе, а на планшетах показывались только карты в руках и установленное.
 * Понять, что уже ушло из колоды заданий, сколько осталось контейнеров и какая
 * карта арсенала лежит открытой в витрине, было нельзя ничем.
 *
 * <p>УСТРОЙСТВО ЭКРАНА. Сверху — ряд стопок: у каждого набора своя колода
 * (рубашкой вверх, с числом карт) и рядом её сброс (лицом вверх — сверху лежит
 * та карта, которую сбросили последней). У арсенала между колодой и сбросом
 * стоят ДВЕ ОТКРЫТЫЕ КАРТЫ витрины: их берут обменом в Науке.
 *
 * <p>Щёлкнул по стопке — слева появляется список её карт В ТОМ ПОРЯДКЕ, В КОТОРОМ
 * ОНИ ЛЕЖАТ, сверху вниз. Щёлкнул по карте в списке — справа она нарисована
 * целиком, в своём формате: задание вертикальное, карта арсенала горизонтальная,
 * контейнер квадратный.
 *
 * <p>ВСЁ ЭТО — ПО КАДРАМ ЗАПИСИ. Панель рисует состояние ТОГО шага, на котором
 * стоит лента времени, поэтому историю колод можно просто прокрутить: видно, как
 * колода тает, а сброс растёт.
 */
public final class DecksPanel extends JPanel {

    /** Наборы, которые показываем, и их человеческие имена. */
    private static final String[][] НАБОРЫ = {
        {"objectives", "ЗАДАНИЯ"},
        {"arsenal", "АРСЕНАЛ"},
        {"containers", "КОНТЕЙНЕРЫ"},
    };

    private ReplayRecord record;
    private ReplayRecord.Snapshot snap;
    private ContentLibrary content;

    /** Что выбрано: набор и стопка (колода или сброс). */
    private String выбранныйНабор = "objectives";
    private boolean выбранСброс = false;

    private final Стопки стопки = new Стопки();
    private final DefaultListModel<String> модель = new DefaultListModel<>();
    private final JList<String> список = new JList<>(модель);
    private final Лицо лицо = new Лицо();
    private final JLabel подпись = new JLabel(" ");
    /** Прокрутка ряда стопок — её надо красить вместе с темой. */
    private JScrollPane рядПрокрутка;

    public DecksPanel() {
        super(new BorderLayout(0, 0));
        setOpaque(true);
        setBackground(Theme.bg());

        список.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        список.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                лицо.repaint();
            }
        });
        JScrollPane прокрутка = new JScrollPane(список);
        прокрутка.setBorder(javax.swing.BorderFactory.createEmptyBorder());

        JPanel слева = new JPanel(new BorderLayout());
        слева.setOpaque(false);
        подпись.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10));
        слева.add(подпись, BorderLayout.NORTH);
        слева.add(прокрутка, BorderLayout.CENTER);

        // СПИСОК ПОСТОЯННОЙ ШИРИНЫ. Доля от окна тут не годится: на широком экране
        // треть — это шестьсот пикселей под колонку имён, где хватает трёхсот. Весь
        // прирост ширины отдаём карте: её и разглядывают.
        слева.setPreferredSize(new Dimension(
            (int) Math.round(330 * Theme.effectiveScale()), 10));
        слева.setMinimumSize(new Dimension(
            (int) Math.round(200 * Theme.effectiveScale()), 10));
        JSplitPane делитель = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, слева, лицо);
        делитель.setResizeWeight(0.0);
        делитель.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        делитель.setDividerSize(6);

        // РЯД СТОПОК ПРОКРУЧИВАЕТСЯ ПО ГОРИЗОНТАЛИ (решение дизайнера): не влезло —
        // прокрути. Ужимать стопки до неразличимости хуже: их форма и есть то,
        // чем карты за столом различают.
        JScrollPane рядПрокрутка = new JScrollPane(стопки,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED) {
            @Override
            public Dimension getPreferredSize() {
                // МЕСТО ПОД ПОЛОСУ ПРОКРУТКИ ОТВОДИТСЯ ВСЕГДА. BorderLayout.NORTH
                // даёт полосе ровно высоту содержимого, и когда полоса появлялась,
                // её просто срезало: прокрутить было нечем, хотя ряд не влезал.
                Dimension d = super.getPreferredSize();
                int бар = getHorizontalScrollBar() == null ? 12
                    : Math.max(12, getHorizontalScrollBar().getPreferredSize().height);
                return new Dimension(d.width, стопки.getPreferredSize().height + бар + 2);
            }
        };
        рядПрокрутка.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        рядПрокрутка.getViewport().setOpaque(false);
        рядПрокрутка.setOpaque(false);
        рядПрокрутка.getHorizontalScrollBar().setUnitIncrement(24);
        this.рядПрокрутка = рядПрокрутка;

        add(рядПрокрутка, BorderLayout.NORTH);
        add(делитель, BorderLayout.CENTER);
        applyTheme();
    }

    /** Перекрасить под текущую тему (окно не пересобирается — см. Theme). */
    public void applyTheme() {
        setBackground(Theme.bg());
        список.setBackground(Theme.panel());
        список.setForeground(Theme.ink());
        список.setFont(Theme.font(12.5, Font.PLAIN));
        подпись.setForeground(Theme.ink2());
        подпись.setFont(Theme.font(11.5, Font.BOLD));
        лицо.setBackground(Theme.bg());
        if (рядПрокрутка != null) {
            рядПрокрутка.getViewport().setBackground(Theme.panel());
        }
        repaint();
    }

    /**
     * Выбрать стопку снаружи — нужно снимкам: посмотреть карту арсенала
     * (горизонтальную) или контейнер (квадратный) иначе можно только мышью.
     */
    public void выбрать(String набор, boolean сброс) {
        this.выбранныйНабор = набор;
        this.выбранСброс = сброс;
        список.clearSelection();
        перечитать();
    }

    /** Каталоги карт — из них берутся тексты и числа. */
    public void setContent(ContentLibrary c) {
        this.content = c;
        перечитать();
    }

    /** Показать состояние этого шага записи. */
    public void show(ReplayRecord rec, ReplayRecord.Snapshot s) {
        this.record = rec;
        this.snap = s;
        перечитать();
    }

    // ==================================================================
    //  СПИСОК ВЫБРАННОЙ СТОПКИ
    // ==================================================================

    private List<String> карты(String набор, boolean сброс) {
        if (snap == null) {
            return List.of();
        }
        ReplayRecord.DeckState d = snap.decks.get(набор);
        if (d == null) {
            return List.of();
        }
        return сброс ? d.discard : d.draw;
    }

    private void перечитать() {
        String былВыбран = список.getSelectedValue();
        модель.clear();
        List<String> ids = карты(выбранныйНабор, выбранСброс);
        for (int i = 0; i < ids.size(); i++) {
            модель.addElement((i + 1) + ".  " + имяКарты(выбранныйНабор, ids.get(i))
                + "   [" + ids.get(i) + "]");
        }
        подпись.setText(имяНабора(выбранныйНабор) + (выбранСброс ? " · СБРОС" : " · КОЛОДА")
            + " — карт " + ids.size() + ", сверху вниз");
        if (былВыбран != null) {
            int idx = модель.indexOf(былВыбран);
            if (idx >= 0) {
                список.setSelectedIndex(idx);
            }
        }
        if (список.getSelectedIndex() < 0 && !модель.isEmpty()) {
            список.setSelectedIndex(0);
        }
        стопки.repaint();
        лицо.repaint();
    }

    private static String имяНабора(String набор) {
        for (String[] n : НАБОРЫ) {
            if (n[0].equals(набор)) {
                return n[1];
            }
        }
        return набор;
    }

    private String имяКарты(String набор, String id) {
        Map<String, Object> c = данные(набор, id);
        if (c != null && c.get("name") != null) {
            return String.valueOf(c.get("name"));
        }
        return record != null ? record.cardName(id) : id;
    }

    /**
     * Запись карты из каталога НАЗВАННОГО набора; {@code null}, если её там нет.
     *
     * <p>НАБОР ПЕРЕДАЁТСЯ ЯВНО, и это не мелочь. Сперва я брал набор из того,
     * что выбрано в панели, — а ряд стопок рисует имена ВСЕХ ТРЁХ наборов сразу.
     * Карта арсенала искалась в каталоге заданий, {@code ContentSet.byId} на
     * это БРОСАЕТ исключение, а брошенное внутри отрисовки убивает отрисовку
     * всего окна. Выглядело как «сломался весь визуализатор».
     *
     * <p>Поэтому здесь ещё и перехват: у панели нет права ронять окно из-за
     * карты, которой не нашлось.
     */
    private Map<String, Object> данные(String набор, String id) {
        if (content == null || id == null || набор == null) {
            return null;
        }
        try {
            var set = content.get(набор);
            return set == null ? null : set.byId(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Идентификатор карты, выбранной в списке. */
    private String выбраннаяКарта() {
        int i = список.getSelectedIndex();
        List<String> ids = карты(выбранныйНабор, выбранСброс);
        return i >= 0 && i < ids.size() ? ids.get(i) : null;
    }

    // ==================================================================
    //  РЯД СТОПОК
    // ==================================================================

    /**
     * ФОРМА КАРТЫ НАБОРА — отношение ширины к высоте, как у настоящей карты.
     *
     * <p>Печатные размеры: контейнер 34×34 мм — КВАДРАТ, карта арсенала 68×44 мм —
     * ГОРИЗОНТАЛЬНАЯ (по ширине она равна двум контейнерам), задание —
     * вертикальная карта обычного игрального формата.
     *
     * <p>Сперва я рисовал ВСЕ стопки одной вертикальной формой, и это была именно
     * та неправильность, которую видно с первого взгляда: квадратный контейнер
     * лежал вытянутым, а горизонтальный арсенал стоял стоймя. Форма стопки — это
     * то, чем карты за столом и различают, «спутать их нельзя физически».
     */
    private static double форма(String набор) {
        return switch (набор) {
            case "arsenal" -> 68.0 / 44.0;
            case "containers" -> 1.0;
            default -> 63.0 / 88.0;
        };
    }

    /**
     * Ряд стопок: по набору — колода, витрина (только у арсенала) и сброс.
     *
     * <p>Ряд САМ СЧИТАЕТ свою ширину по формам карт, а не делит окно на три
     * равные части. Не влезло — {@link JScrollPane} даёт горизонтальную прокрутку
     * (решение дизайнера): ужимать стопки до неразличимости хуже, чем прокрутить.
     */
    private final class Стопки extends JComponent {

        /** Куда попадает щелчок: прямоугольник → (набор, сброс?). */
        private final Map<Rectangle, String[]> зоны = new LinkedHashMap<>();

        Стопки() {
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    for (var z : зоны.entrySet()) {
                        if (z.getKey().contains(e.getPoint())) {
                            выбранныйНабор = z.getValue()[0];
                            выбранСброс = "1".equals(z.getValue()[1]);
                            список.clearSelection();
                            перечитать();
                            return;
                        }
                    }
                }
            });
        }

        /**
         * ВЫСОТА КАРТЫ В СТОПКЕ — от неё считается вся раскладка ряда.
         *
         * <p>СПЕРВА УЖИМАЕМСЯ, ПОТОМ ПРОКРУЧИВАЕМ. Если ряд не влезает в окно,
         * карты уменьшаются — но не ниже предела, за которым название уже не
         * прочитать. Дальше включается горизонтальная прокрутка. Порядок именно
         * такой: прокрутка прячет половину стола, и платить ею стоит только когда
         * ужиматься больше нельзя.
         */
        private int высотаКарты() {
            double м = Theme.effectiveScale();
            int хочу = (int) Math.round(96 * м);
            int предел = (int) Math.round(68 * м);
            int доступно = getParent() == null ? 0 : getParent().getWidth();
            if (доступно <= 0) {
                return хочу;
            }
            int нужно = ширинаРяда(хочу);
            if (нужно <= доступно) {
                return хочу;
            }
            // Ширина ряда линейна по высоте карты, поэтому нужный размер считается
            // сразу, без подбора по шагам.
            int подгон = (int) Math.floor(хочу * (доступно - 14.0) / нужно);
            return Math.max(предел, Math.min(хочу, подгон));
        }

        /** Ширина всего ряда при такой высоте карты. */
        private int ширинаРяда(int высота) {
            int всего = 14;
            for (String[] n : НАБОРЫ) {
                int шир = (int) Math.round(высота * форма(n[0]));
                int стопок = 2 + ("arsenal".equals(n[0]) && snap != null
                    ? snap.arsenalDisplay.size() : 0);
                всего += стопок * шир + (стопок - 1) * зазор() + межГруппами();
            }
            return всего;
        }

        private int зазор() {
            return (int) Math.round(10 * Theme.effectiveScale());
        }

        /** Отступ группы от группы. */
        private int межГруппами() {
            return (int) Math.round(30 * Theme.effectiveScale());
        }

        /** Ширина одной группы: подпись, колода, витрина, сброс. */
        private int ширинаГруппы(String набор) {
            int выс = высотаКарты();
            int шир = (int) Math.round(выс * форма(набор));
            int стопок = 2 + ("arsenal".equals(набор) && snap != null
                ? snap.arsenalDisplay.size() : 0);
            return стопок * шир + (стопок - 1) * зазор();
        }

        @Override
        public Dimension getPreferredSize() {
            int выс = высотаКарты();
            // Высота: подпись сверху, карта, подпись снизу.
            int h = (int) Math.round(24 * Theme.effectiveScale())
                + выс + (int) Math.round(26 * Theme.effectiveScale());
            return new Dimension(ширинаРяда(выс), h);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            зоны.clear();
            int w = getWidth();
            int h = getHeight();
            g.setColor(Theme.panel());
            g.fillRect(0, 0, w, h);
            g.setColor(Theme.divider());
            g.drawLine(0, h - 1, w, h - 1);
            if (snap == null) {
                g.setColor(Theme.ink3());
                g.setFont(Theme.font(12, Font.PLAIN));
                g.drawString("Партия не сыграна и не открыта — колод пока нет.", 14, h / 2);
                g.dispose();
                return;
            }
            int x = 14;
            for (String[] n : НАБОРЫ) {
                нарисоватьГруппу(g, n[0], n[1], x, 0);
                x += ширинаГруппы(n[0]) + межГруппами();
            }
            g.dispose();
        }

        private void нарисоватьГруппу(Graphics2D g, String набор, String имя, int x, int y) {
            int выс = высотаКарты();
            int шир = (int) Math.round(выс * форма(набор));
            int cy = y + (int) Math.round(24 * Theme.effectiveScale());

            g.setFont(Theme.font(11, Font.BOLD));
            g.setColor(Theme.ink2());
            g.drawString(имя, x, cy - (int) Math.round(8 * Theme.effectiveScale()));

            ReplayRecord.DeckState d = snap.decks.get(набор);
            int вКолоде = d == null ? 0 : d.draw.size();
            int вСбросе = d == null ? 0 : d.discard.size();
            List<String> витрина = "arsenal".equals(набор) ? snap.arsenalDisplay : List.of();

            int cx = x;
            рубашка(g, cx, cy, шир, выс, вКолоде, "колода", набор, false);
            cx += шир + зазор();
            for (String id : витрина) {
                лицом(g, cx, cy, шир, выс, id, "витрина", набор);
                cx += шир + зазор();
            }
            String верхСброса = вСбросе > 0 ? d.discard.get(0) : null;
            if (верхСброса != null) {
                лицомСЧислом(g, cx, cy, шир, выс, верхСброса, "сброс", вСбросе, набор);
            } else {
                рубашка(g, cx, cy, шир, выс, 0, "сброс пуст", набор, true);
            }
        }

        /** Стопка рубашкой вверх с числом карт. */
        private void рубашка(Graphics2D g, int x, int y, int w, int h, int сколько,
                             String подпись, String набор, boolean пунктир) {
            boolean выбрана = набор.equals(выбранныйНабор)
                && ("сброс пуст".equals(подпись) == выбранСброс);
            g.setColor(пунктир ? Theme.bg() : Theme.tile());
            g.fillRoundRect(x, y, w, h, 10, 10);
            g.setStroke(пунктир
                ? new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[]{4f, 4f}, 0f)
                : new BasicStroke(выбрана ? 2.6f : 1.4f));
            g.setColor(выбрана ? Theme.accent() : Theme.border());
            g.drawRoundRect(x, y, w, h, 10, 10);
            if (!пунктир) {
                // РУБАШКА ТЕКСТУРОЙ, если она принесена, — тот же принцип, что у
                // жетонов и картона поля: есть картинка, рисуется картинка; нет —
                // своя форма. Ищем от точного к общему: рубашка этого набора,
                // потом общая.
                java.awt.image.BufferedImage тек = kelium.report.Textures.card(
                    "deck_" + набор, "deck");
                if (тек != null) {
                    java.awt.Shape было = g.getClip();
                    g.setClip(new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, 10, 10));
                    g.drawImage(тек, x, y, w, h, null);
                    g.setClip(было);
                } else {
                    // косая штриховка, чтобы стопка читалась закрытой
                    g.setColor(Theme.divider());
                    g.setStroke(new BasicStroke(1f));
                    for (int i = -h; i < w; i += 7) {
                        g.drawLine(Math.max(x, x + i), y + Math.max(0, -i),
                            Math.min(x + w, x + i + h),
                            y + Math.min(h, h - Math.max(0, i + h - w)));
                    }
                }
                // Число карт поверх рубашки — в плашке, иначе на текстуре не
                // прочитать: цифра обязана читаться на любой картинке.
                g.setFont(Theme.display(Math.max(16, w * 0.34)));
                String s = String.valueOf(сколько);
                int sw = g.getFontMetrics().stringWidth(s);
                int sh = (int) (w * 0.28);
                int bx = x + (w - sw) / 2;
                int by = y + h / 2 - sh + (int) (w * 0.06);
                g.setColor(new Color(Theme.paper().getRed(), Theme.paper().getGreen(),
                    Theme.paper().getBlue(), 215));
                g.fillRoundRect(bx - 7, by, sw + 14, sh + 8, 7, 7);
                g.setColor(Theme.ink());
                g.drawString(s, bx, y + h / 2 + (int) (w * 0.12));
            }
            g.setFont(Theme.font(10, Font.PLAIN));
            g.setColor(Theme.ink3());
            g.drawString(подпись, x, y + h + 14);
        }

        /** Открытая карта: мини-лицо — название и что она делает. */
        private void лицом(Graphics2D g, int x, int y, int w, int h, String id, String подпись,
                           String набор) {
            миниЛицо(g, x, y, w, h, id, набор, false);
            g.setFont(Theme.font(10, Font.PLAIN));
            g.setColor(Theme.ink3());
            g.drawString(подпись, x, y + h + 14);
        }

        /**
         * МИНИ-ЛИЦО ОТКРЫТОЙ КАРТЫ: название и первая строка того, что она делает.
         *
         * <p>Пустой прямоугольник с именем в углу не говорил ничего: чтобы понять,
         * что лежит в витрине или что сбросили последним, приходилось щёлкать.
         */
        private void миниЛицо(Graphics2D g, int x, int y, int w, int h, String id,
                              String набор, boolean выбрана) {
            g.setColor(Theme.paper());
            g.fillRoundRect(x, y, w, h, 10, 10);
            g.setStroke(new BasicStroke(выбрана ? 2.6f : 1.4f));
            g.setColor(выбрана ? Theme.accent() : Theme.border());
            g.drawRoundRect(x, y, w, h, 10, 10);
            g.setFont(Theme.font(9.5, Font.BOLD));
            g.setColor(Theme.ink());
            int занято = обёртка(g, имяКарты(набор, id), x + 5, y + 14, w - 10, 10, 2);
            Map<String, Object> c = данные(набор, id);
            if (c == null) {
                return;
            }
            Object часть = c.get("top") != null ? c.get("top") : c.get("a");
            String что = метка(часть);
            if (что.isBlank()) {
                return;
            }
            g.setColor(Theme.divider());
            g.drawLine(x + 5, y + 16 + занято, x + w - 5, y + 16 + занято);
            g.setFont(Theme.font(8.5, Font.PLAIN));
            g.setColor(Theme.ink2());
            int осталось = h - (занято + 26);
            обёртка(g, что, x + 5, y + 26 + занято, w - 10, 9, Math.max(1, осталось / 9));
        }

        /** Верх сброса лицом вверх плюс сколько всего в сбросе. */
        private void лицомСЧислом(Graphics2D g, int x, int y, int w, int h, String id,
                                  String подпись, int всего, String набор) {
            boolean выбрана = набор.equals(выбранныйНабор) && выбранСброс;
            миниЛицо(g, x, y, w, h, id, набор, выбрана);
            g.setFont(Theme.font(10, Font.PLAIN));
            g.setColor(Theme.ink3());
            g.drawString(подпись + " · " + всего, x, y + h + 14);
        }
    }

    // ==================================================================
    //  ЛИЦО КАРТЫ
    // ==================================================================

    /**
     * Выбранная карта, нарисованная целиком.
     *
     * <p>ФОРМАТ ПО НАБОРУ, и это не украшение: за столом задание вертикальное,
     * карта арсенала горизонтальная (68×44), контейнер квадратный (34×34).
     * Показывать их одной формой значит показывать не то, что лежит на столе.
     */
    private final class Лицо extends JComponent {

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Theme.bg());
            g.fillRect(0, 0, getWidth(), getHeight());
            String id = выбраннаяКарта();
            if (id == null) {
                g.setColor(Theme.ink3());
                g.setFont(Theme.font(12, Font.PLAIN));
                g.drawString("Выбери стопку сверху и карту в списке слева.", 20, 34);
                g.dispose();
                return;
            }
            // Форма карты — та же, что у стопки: одно место на всю панель.
            double отн = форма(выбранныйНабор);
            int поле = 24;
            // ПОТОЛОК РАЗМЕРА. Растянутая на всю панель карта оставляла внизу ладонь
            // пустоты: разделов на ней немного, а форму держать обязана. Ограничиваем
            // и ставим по центру — так она выглядит картой, а не полосой.
            int дw = Math.max(60, Math.min(getWidth() - поле * 2,
                (int) Math.round(600 * Theme.effectiveScale())));
            int дh = Math.max(60, Math.min(getHeight() - поле * 2,
                (int) Math.round(700 * Theme.effectiveScale())));
            int w = дw;
            int h = (int) Math.round(w / отн);
            if (h > дh) {
                h = дh;
                w = (int) Math.round(h * отн);
            }
            int x = (getWidth() - w) / 2;
            int y = поле + Math.max(0, (getHeight() - поле * 2 - h) / 2);
            карта(g, x, y, w, h, id);
            g.dispose();
        }

        private void карта(Graphics2D g, int x, int y, int w, int h, String id) {
            int радиус = Math.max(10, Math.min(w, h) / 14);
            g.setColor(Theme.paper());
            g.fillRoundRect(x, y, w, h, радиус, радиус);
            g.setColor(Theme.border());
            g.setStroke(new BasicStroke(1.8f));
            g.drawRoundRect(x, y, w, h, радиус, радиус);

            Map<String, Object> c = данные(выбранныйНабор, id);
            int отступ = Math.max(12, w / 22);
            int cy = y + отступ + (int) (h * 0.055);
            int шир = w - отступ * 2;

            // --- ЗАГОЛОВОК ---
            g.setFont(Theme.display(Math.max(15, Math.min(w * 0.055, h * 0.075))));
            g.setColor(Theme.ink());
            g.drawString(обрезать(g, имяКарты(выбранныйНабор, id), шир), x + отступ, cy);
            cy += (int) (h * 0.03);
            g.setFont(Theme.font(10, Font.PLAIN));
            g.setColor(Theme.ink3());
            g.drawString(видКарты(c, id), x + отступ, cy);
            cy += (int) (h * 0.035);

            if (c == null) {
                g.setColor(Theme.ink3());
                g.setFont(Theme.font(11.5, Font.ITALIC));
                g.drawString("Записи карты в каталоге нет.", x + отступ, cy + 14);
                return;
            }

            int мелкий = (int) Math.max(10.5, Math.min(w * 0.026, h * 0.032));
            // --- ВЕРХ КАРТЫ (то, ради чего её сжигают / первый эффект) ---
            String верхИмя = "objectives".equals(выбранныйНабор) ? "ВЕРХ — СЖЕЧЬ"
                : "arsenal".equals(выбранныйНабор) ? "ВЕРХ — УТИЛЬ" : "ВАРИАНТ А";
            Object верх = c.get("top") != null ? c.get("top") : c.get("a");
            cy = раздел(g, x + отступ, cy, шир, верхИмя, метка(верх), мелкий, h);
            if (c.get("b") != null) {
                cy = раздел(g, x + отступ, cy, шир, "ВАРИАНТ Б", метка(c.get("b")), мелкий, h);
            }
            if (c.get("bottom") != null) {
                cy = раздел(g, x + отступ, cy, шир, "НИЗ — УСТАНОВИТЬ",
                    метка(c.get("bottom")), мелкий, h);
            }

            // --- РАЗДЕЛИТЕЛЬ ---
            if (c.get("requirement") != null || c.get("base_reward") != null) {
                cy = черта(g, x + отступ, cy, шир);
                cy = раздел(g, x + отступ, cy, шир, "ТРЕБОВАНИЕ",
                    условие(c.get("requirement")), мелкий, h);
                cy = раздел(g, x + отступ, cy, шир, "НАГРАДА",
                    награда(c.get("base_reward")), мелкий, h);
                if (c.get("enhanced") != null) {
                    cy = раздел(g, x + отступ, cy, шир, "УСИЛЕННОЕ ТРЕБОВАНИЕ",
                        условие(c.get("enhanced")), мелкий, h);
                }
                if (c.get("special_reward") != null) {
                    cy = раздел(g, x + отступ, cy, шир, "НАГРАДА ЗА УСИЛЕНИЕ",
                        награда(c.get("special_reward")), мелкий, h);
                }
            }

            // --- ОПИСАНИЕ, курсивом, внизу ---
            Object оп = c.get("описание");
            if (оп != null) {
                cy = черта(g, x + отступ, cy, шир);
                g.setFont(Theme.font(мелкий - 0.5, Font.ITALIC));
                g.setColor(Theme.ink2());
                int осталось = (y + h - отступ) - cy;
                int строк = Math.max(1, осталось / (мелкий + 3));
                обёртка(g, String.valueOf(оп), x + отступ, cy + мелкий, шир, мелкий + 3, строк);
            }
        }

        /** Один раздел: подпись капителью и текст под ней. Возвращает новый y. */
        private int раздел(Graphics2D g, int x, int y, int w, String имя, String текст,
                           int мелкий, int h) {
            if (текст == null || текст.isBlank()) {
                return y;
            }
            g.setFont(Theme.font(мелкий - 1.5, Font.BOLD));
            g.setColor(Theme.accent());
            g.drawString(имя, x, y + мелкий);
            g.setFont(Theme.font(мелкий, Font.PLAIN));
            g.setColor(Theme.ink());
            int строк = 3;
            int занято = обёртка(g, текст, x, y + мелкий * 2 + 3, w, мелкий + 3, строк);
            return y + мелкий + 3 + занято + (int) (h * 0.012);
        }

        private int черта(Graphics2D g, int x, int y, int w) {
            g.setColor(Theme.divider());
            g.setStroke(new BasicStroke(1f));
            g.drawLine(x, y + 4, x + w, y + 4);
            return y + 12;
        }
    }

    // ==================================================================
    //  ТЕКСТЫ ИЗ ДАННЫХ
    // ==================================================================

    /** Человеческая метка эффекта: сперва {@code label}, иначе имя эффекта. */
    @SuppressWarnings("unchecked")
    private static String метка(Object part) {
        if (!(part instanceof Map<?, ?> m)) {
            return "";
        }
        Object label = m.get("label");
        if (label != null && !String.valueOf(label).isBlank()) {
            return String.valueOf(label);
        }
        Object eff = m.get("effect") != null ? m.get("effect") : m.get("passive");
        String s = eff == null ? "" : String.valueOf(eff);
        Object p = m.get("params");
        if (p instanceof Map<?, ?> pm && !pm.isEmpty()) {
            s += " (" + числа((Map<String, Object>) pm) + ")";
        }
        return s;
    }

    /**
     * Требование словами.
     *
     * <p>В данных требование — это ИДЕНТИФИКАТОР ПРЕДИКАТА и его параметры; такой
     * строкой карта и печатается в отладке. Человеческая формулировка требования
     * живёт в {@code описание} той же карты, поэтому описание показано ниже
     * целиком: выдумывать здесь фразу заново значило бы завести второй источник
     * текста и разойтись с картой.
     */
    @SuppressWarnings("unchecked")
    private static String условие(Object req) {
        if (!(req instanceof Map<?, ?> m)) {
            return "";
        }
        Object pred = m.get("predicate");
        Map<String, Object> п = m.get("params") instanceof Map<?, ?> pm
            ? (Map<String, Object>) pm : Map.of();
        return CardWords.условие(pred == null ? null : String.valueOf(pred), п);
    }

    /** Награда словами: «3 боеприпаса · жетон модуля АТАКИ · 1 карта заданий». */
    @SuppressWarnings("unchecked")
    private static String награда(Object rew) {
        return rew instanceof Map<?, ?> m
            ? CardWords.награда((Map<String, Object>) m) : "";
    }

    private static String числа(Map<String, Object> p) {
        StringBuilder sb = new StringBuilder();
        for (var e : p.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append(' ').append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * ЧТО ЭТО ЗА КАРТА — по-русски, под названием.
     *
     * <p>Идентификатор оставлен в скобках нарочно: он нужен, когда сверяют карту с
     * данными или с этой же картой в списке слева. Но первым словом идёт то, что
     * понятно без кода.
     */
    private String видКарты(Map<String, Object> c, String id) {
        String набор = switch (выбранныйНабор) {
            case "objectives" -> "задание";
            case "arsenal" -> "карта арсенала";
            case "containers" -> "контейнер";
            default -> выбранныйНабор;
        };
        String вид = c == null ? "" : switch (String.valueOf(c.get("kind"))) {
            case "regular" -> "обычное";
            case "start", "starting" -> "начальное";
            case "goal" -> "карта-цель";
            default -> "";
        };
        if (вид.isEmpty() && c != null) {
            вид = switch (String.valueOf(c.get("tier"))) {
                case "common" -> "обычный";
                case "good" -> "хороший";
                case "rare" -> "редкий";
                default -> "";
            };
        }
        return набор + (вид.isEmpty() ? "" : ", " + вид) + "  ·  " + id;
    }

    private static String строка(Object v, String иначе) {
        return v == null || String.valueOf(v).isBlank() ? иначе : String.valueOf(v);
    }

    // ==================================================================
    //  ТЕКСТ ПО СЛОВАМ
    // ==================================================================

    /** Перенос по словам; возвращает занятую высоту в пикселях. */
    private static int обёртка(Graphics2D g, String текст, int x, int y, int ширина,
                               int высотаСтроки, int максСтрок) {
        if (текст == null || текст.isBlank()) {
            return 0;
        }
        String[] слова = текст.split("\\s+");
        StringBuilder строка = new StringBuilder();
        int строк = 0;
        int cy = y;
        for (String слово : слова) {
            String проба = строка.length() == 0 ? слово : строка + " " + слово;
            if (g.getFontMetrics().stringWidth(проба) > ширина && строка.length() > 0) {
                g.drawString(строка.toString(), x, cy);
                cy += высотаСтроки;
                строк++;
                if (строк >= максСтрок) {
                    return строк * высотаСтроки;
                }
                строка = new StringBuilder(слово);
            } else {
                строка = new StringBuilder(проба);
            }
        }
        if (строка.length() > 0) {
            g.drawString(обрезать(g, строка.toString(), ширина), x, cy);
            строк++;
        }
        return строк * высотаСтроки;
    }

    private static String обрезать(Graphics2D g, String текст, int ширина) {
        if (текст == null) {
            return "";
        }
        if (g.getFontMetrics().stringWidth(текст) <= ширина) {
            return текст;
        }
        StringBuilder sb = new StringBuilder(текст);
        while (sb.length() > 1 && g.getFontMetrics().stringWidth(sb + "…") > ширина) {
            sb.setLength(sb.length() - 1);
        }
        return sb + "…";
    }
}
