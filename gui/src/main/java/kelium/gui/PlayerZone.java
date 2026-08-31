package kelium.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import kelium.gui.replay2.Names;
import kelium.report.ReplayRecord;

/**
 * PlayerZone — зона одного игрока: его планшет целиком на текущем шаге.
 *
 * <p>Блоки (заказ §4.3): ресурсы, склад, здания по типам с уровнями, войска по
 * родам на поле и в резерве, треки науки, модули, арсенал в руке и установленный,
 * задания в руке, супер-задание, победные очки с разбивкой. У каждого блока своя
 * подсказка при наведении. Зона активного игрока подсвечивается рамкой и фоном.
 */
public final class PlayerZone extends JPanel {

    private static final long serialVersionUID = 1L;

    private final int seat;
    private ReplayRecord record;

    private final JLabel header = new JLabel();
    private final JPanel body = new JPanel();
    private final Map<String, JLabel> blocks = new LinkedHashMap<>();
    private final JScrollPane scroll;
    private ReplayRecord.Frame lastFrame;

    /** Порядок и подписи блоков; ключ — внутреннее имя. */
    private static final String[][] BLOCKS = {
        {"res", "Ресурсы", "Монеты, келемий, боеприпасы и трофеи (чёрные кубики) на руках."},
        {"store", "Склад", "Ячейки склада открываются постройкой добытчиков и "
            + "энергостанций. Показано «занято из открытого» и запас контейнеров."},
        {"buildings", "Здания", "Здания на поле: код, номер (уровень), прочность и энергия. "
            + "ЦУ — центр управления, Кз — казарма, Зв — завод, Ав — авиабаза, "
            + "Д — добытчик, Э — энергостанция."},
        {"units", "Войска", "Жетоны войск: сколько стоит на поле и сколько ждёт в резерве."},
        {"orders", "Приказы", "Карты приказов этого игрока: что осталось в руке, "
            + "что уже вскрыто в этом раунде и что отложено слепым сбросом."},
        {"tech", "Наука", "Достигнутый шаг на каждом треке технологий: "
            + "левый — красные модули, средний — склад, правый — синие модули."},
        {"modules", "Модули", "Модули усиления: красные (атака), синие (сборка), "
            + "золотые (двойной эффект и 1 ПО каждый)."},
        {"arsenal", "Арсенал", "Карты арсенала: закрытые в руке и установленные "
            + "(постоянные способности, до трёх слотов)."},
        {"objectives", "Задания", "Карты заданий в руке — их выполняют СПЕЦ-действием."},
        {"super", "Супер-задание", "Личное супер-задание: сколько частей внесено. "
            + "Собранное и выложенное по рисунку даёт мгновенную победу."},
        {"cu", "Жетоны ЦУ", "Жетоны разрушения центров управления: свой и захваченные. "
            + "Второй захваченный = мгновенная военная победа."},
        {"vp", "Победные очки", "Итог и разбивка по источникам на текущем шаге."},
    };

    public PlayerZone(int seat) {
        this.seat = seat;
        setLayout(new BorderLayout());
        header.setOpaque(true);
        header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        add(header, BorderLayout.NORTH);

        // Содержимое НЕ шире окошка прокрутки: иначе текст уезжает за правый край
        // (горизонтальной прокрутки у зоны нет — только вертикальная).
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        for (String[] b : BLOCKS) {
            JLabel l = new JLabel();
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            l.setToolTipText(Ui.text(b[2], 320));
            l.setFont(l.getFont().deriveFont(Font.PLAIN, 11.5f));
            blocks.put(b[0], l);
            body.add(l);
            body.add(Box.createVerticalStrut(2));
        }
        // Строки НЕ переносятся: длинную строку просто прокручиваем ползунком
        // (решение дизайнера — так короче и читается ровнее, чем перенос).
        scroll = new JScrollPane(body,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);
        setBorder(BorderFactory.createLineBorder(FieldView.seatStroke(seat), 1));
        setPreferredSize(new Dimension(300, 190));
        setMinimumSize(new Dimension(170, 110));
    }

    /** Подключить запись (нужна для человеческих названий карт). */
    public void setRecord(ReplayRecord rec) {
        this.record = rec;
    }

    /** Обновить зону под конкретный шаг партии. */
    public void showFrame(ReplayRecord.Frame f) {
        if (f != null && lastFrame != null && f.snapshot == lastFrame.snapshot
                && f.snapshot != null) {
            return;        // состояние не изменилось — 11 меток пересобирать незачем
        }
        lastFrame = f;
        if (record == null || f == null || f.snapshot == null
                || seat >= f.snapshot.players.size()) {
            header.setText(" ");
            for (JLabel l : blocks.values()) {
                l.setText(" ");
            }
            return;
        }
        ReplayRecord.Snapshot s = f.snapshot;
        ReplayRecord.Player p = s.players.get(seat);
        boolean active = s.active != null && s.active == seat;

        header.setBackground(FieldView.seatFill(seat));
        header.setForeground(new Color(0x22, 0x22, 0x22));
        header.setText(record.playerName(seat) + " · сторона " + p.side
            + "   —   " + p.vp.getOrDefault("total", 0) + " ПО");
        header.setToolTipText(Ui.text("Место " + (seat + 1) + ", бот: "
            + (seat < record.seatLabels.size() ? record.seatLabels.get(seat) : "?")
            + ". Сторона планшета войск: " + p.side + "."
            + (active ? " Сейчас ходит именно он." : ""), 320));
        setBorder(BorderFactory.createLineBorder(FieldView.seatStroke(seat), active ? 3 : 1));
        setBackground(active ? tint(FieldView.seatFill(seat)) : java.awt.Color.WHITE);
        setOpaque(true);
        body.setBackground(getBackground());
        scroll.getViewport().setBackground(getBackground());

        set("res", "монеты " + p.coin + " · келемий " + p.kelium
            + " · боеприпасы " + p.ammo + " · трофеи " + p.trophy
            + (p.trophyTokens > 0 ? " (жетонов-трофеев " + p.trophyTokens
                + " на " + p.trophyPoints + " очк., ещё не сданы)" : ""));

        String cont = p.containerCap < 0
            ? String.valueOf(p.containers)
            : p.containers + " из " + p.containerCap;
        set("store", "занято " + (p.kelium + p.ammo) + " из " + p.storeCap
            + " ячеек (келемий ≤ " + p.keliumCap + ", боеприпасы ≤ " + p.ammoCap + ")"
            + " · контейнеры " + cont
            + (p.storageTokens.isEmpty() ? "" : " · жетоны склада: "
                + String.join(", ", p.storageTokens)));

        set("buildings", describeBuildings(s, p.seat));
        set("units", describeUnits(s, p.seat));

        set("orders", describeOrders(p) + " · карта рынка: " + Names.card(record, s.market));
        set("tech", p.tech.isEmpty() ? "шагов пока нет" : describeTech(p.tech));
        set("modules", "красные " + p.redModules + " · синие " + p.blueModules
            + " · золотые " + p.goldModules
            + (p.redHalves + p.blueHalves > 0
                ? " · половинки: кр. " + p.redHalves + ", син. " + p.blueHalves : ""));

        set("arsenal", "в руке " + p.arsenalHand.size()
            + (p.arsenalHand.isEmpty() ? "" : " (" + names(p.arsenalHand) + ")")
            + " · установлено " + p.arsenalInstalled.size()
            + (p.arsenalInstalled.isEmpty() ? "" : ": " + names(p.arsenalInstalled))
            + (p.superArsenal.isEmpty() ? "" : " · супер-арсенал: " + names(p.superArsenal)));

        set("objectives", p.objectiveHand.isEmpty() ? "рука пуста" : names(p.objectiveHand));

        set("super", p.superObjective == null ? "не выдано"
            : "«" + Names.card(record, p.superObjective) + "» — частей внесено "
              + p.superProgress + (p.superComplete ? ", СОБРАНО" : ""));

        set("cu", "свой жетон " + (p.ownCuToken ? "цел" : "потерян")
            + " · захвачено чужих " + p.cuTokens);

        set("vp", describeVp(p));
    }

    private void set(String key, String value) {
        JLabel l = blocks.get(key);
        String caption = "";
        String hint = "";
        for (String[] b : BLOCKS) {
            if (b[0].equals(key)) {
                caption = b[1];
                hint = b[2];
                break;
            }
        }
        l.setText("<html><b>" + caption + ":</b> " + esc(value) + "</html>");
        // Строка обрезается по ширине зоны, поэтому ПОЛНЫЙ текст всегда доступен
        // в подсказке — иначе спрятанное значение было бы не достать никак.
        l.setToolTipText(Ui.text(caption + ": " + value + "\n\n" + hint, 430));
    }

    private String describeBuildings(ReplayRecord.Snapshot s, int owner) {
        List<String> out = new ArrayList<>();
        for (ReplayRecord.Tok t : s.tokens) {
            if (!t.building || t.owner != owner || t.hexId == null || !t.alive) {
                continue;
            }
            StringBuilder sb = new StringBuilder(GameRecorder.buildingCode(t.type));
            if (t.level != null) {
                sb.append(t.level);
            }
            sb.append('@').append(t.hexId);
            if (t.damage > 0) {
                sb.append(" (урон ").append(t.damage).append(')');
            }
            if (t.energySlots > 0) {
                sb.append(t.energyPlaced >= t.energySlots ? " [запитан]" : " [без энергии]");
            }
            out.add(sb.toString());
        }
        return out.isEmpty() ? "на поле пусто" : String.join(" · ", out);
    }

    private String describeUnits(ReplayRecord.Snapshot s, int owner) {
        Map<String, Integer> field = new LinkedHashMap<>();
        Map<String, Integer> reserve = new LinkedHashMap<>();
        for (ReplayRecord.Tok t : s.tokens) {
            if (t.building || t.owner != owner || !t.alive) {
                continue;
            }
            String name = GameRecorder.unitName(t.type);
            if (t.hexId != null) {
                field.merge(name, 1, Integer::sum);
            } else {
                reserve.merge(name, 1, Integer::sum);
            }
        }
        String f = field.isEmpty() ? "никого" : joinMap(field);
        String r = reserve.isEmpty() ? "пусто" : joinMap(reserve);
        return "на поле — " + f + " · в резерве — " + r;
    }

    /** Приказы: рука, вскрытые в этом раунде и отложенный слепым сбросом. */
    private String describeOrders(ReplayRecord.Player p) {
        StringBuilder sb = new StringBuilder();
        sb.append("в руке ").append(p.orderHand.size());
        if (!p.orderPlayed.isEmpty()) {
            sb.append(" · вскрыто: ").append(names(p.orderPlayed));
        }
        if (p.orderSetAside != null) {
            sb.append(" · отложен: «").append(Names.card(record, p.orderSetAside)).append('»');
        }
        if (p.orderColor != null) {
            sb.append(" · колода ").append(orderColour(p.orderColor));
        }
        return sb.toString();
    }

    /** Цвет колоды — общим словарём: свой список молча показывал бы код колоды. */
    private static String orderColour(String code) {
        return Names.orderDeck(code);
    }

    /** Треки науки по-русски: в данных они называются left/middle/right. */
    private static String describeTech(Map<String, Integer> tech) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : tech.entrySet()) {
            out.add(trackName(e.getKey()) + " " + e.getValue());
        }
        return String.join(" · ", out);
    }

    private static String trackName(String id) {
        return switch (id) {
            case "left" -> "левый (красные модули)";
            case "middle" -> "средний (склад)";
            case "right" -> "правый (синие модули)";
            default -> id;
        };
    }

    private String describeVp(ReplayRecord.Player p) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : p.vp.entrySet()) {
            if ("total".equals(e.getKey()) || e.getValue() == 0) {
                continue;
            }
            parts.add(vpName(e.getKey()) + " " + e.getValue());
        }
        return "всего " + p.vp.getOrDefault("total", 0)
            + (parts.isEmpty() ? "" : " = " + String.join(" + ", parts));
    }

    private static String vpName(String key) {
        return switch (key) {
            case "kelium" -> "келемий";
            case "coins" -> "монеты";
            case "debris" -> "трофеи";
            case "buildings_on_field" -> "здания";
            case "units_on_field" -> "войска";
            case "tech" -> "наука";
            case "gold_modules" -> "зол. модули";
            case "spawn_tiles" -> "тайлы";
            case "cu_tokens" -> "жетоны ЦУ";
            case "war_track" -> "воен. трек";
            case "super_arsenal" -> "супер-арсенал";
            case "level4_stars" -> "звёзды ур. 4";
            case "super_first_part" -> "1-я часть супер-задания";
            case "kills" -> "уничтожения";
            default -> key;
        };
    }

    private String names(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            out.add("«" + Names.card(record, id) + "»");
        }
        return String.join(", ", out);
    }

    private static String joinMap(Map<String, Integer> m) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            out.add(e.getKey() + " " + e.getValue());
        }
        return String.join(" · ", out);
    }

    private static Color tint(Color c) {
        return new Color(Math.min(255, c.getRed() + 18),
            Math.min(255, c.getGreen() + 18), Math.min(255, c.getBlue() + 18));
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
