package kelium.gui.replay2;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;

import kelium.dataio.ContentLibrary;

/**
 * CardReader — ЧИТАЛКА КАРТ игрока: арсенал, задания, супер-задания.
 *
 * <p>Зачем отдельным окном. В личной зоне карты нужны СПИСКОМ — чтобы не
 * перегружать планшет: там их роль «сколько чего на руках». А когда захотелось
 * прочитать саму карту, нужен разворот: название, что она делает, из чего
 * состоит (просьба дизайнера 13.08.2026).
 *
 * <p>Слева — список карт этой стопки, справа — разворот выбранной. Содержимое
 * берётся из КАРТОЧНОГО НАБОРА той версии, в которой сыграна партия, и
 * показывается как есть: ничего не пересказываем своими словами, иначе разбор
 * начнёт спорить с самой картой.
 */
public final class CardReader {

    private CardReader() {
    }

    /** Открыть читалку на стопке карт. */
    public static void show(Window owner, ContentLibrary content, String kind,
                            String title, List<String> ids, ReplayRecordNames names) {
        JFrame f = new JFrame(title);
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String id : ids) {
            model.addElement(id);
        }
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(Theme.body());
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object value,
                    int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(l, value, index, sel, focus);
                setText(names.name(String.valueOf(value)));
                setBorder(BorderFactory.createEmptyBorder(Theme.px(4), Theme.px(8),
                    Theme.px(4), Theme.px(8)));
                return this;
            }
        });

        JEditorPane text = new JEditorPane("text/html", "");
        text.setEditable(false);
        text.setFont(Theme.body());
        JScrollPane textScroll = new JScrollPane(text);
        textScroll.setBorder(null);

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String id = list.getSelectedValue();
                text.setText(id == null ? "" : describe(content, kind, id, names));
                text.setCaretPosition(0);
            }
        });
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }

        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(Theme.px(230), Theme.px(360)));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, textScroll);
        split.setDividerLocation(Theme.px(230));
        f.add(split, BorderLayout.CENTER);
        f.setSize(Theme.px(720), Theme.px(420));
        f.setLocationRelativeTo(owner);
        f.setVisible(true);
    }

    /** Откуда брать человеческие названия карт (обычно сама запись партии). */
    public interface ReplayRecordNames {
        String name(String id);
    }

    /** Разворот карты: всё, что о ней знает карточный набор. */
    static String describe(ContentLibrary content, String kind, String id,
                           ReplayRecordNames names) {
        return describe(find(content, kind, id), id, names);
    }

    /**
     * Тот же разворот, но карта передана записью НАБОРА.
     *
     * <p>Нужно каталогу справочника: он показывает не только тот набор, по
     * которому идёт партия, а все модули. Одинаковые id живут в разных модулях
     * (задание {@code o01} есть в каждой версии), и поиск по загруженному набору
     * показал бы не ту карту, которую выбрали в дереве.
     */
    static String describe(Map<String, Object> card, String id, ReplayRecordNames names) {
        StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif'>");
        // НАЗВАНИЕ КАРТЫ — плакатным шрифтом, как на самой карте
        sb.append("<div style='font-family:").append(Theme.displayFamily())
            .append(";font-size:20pt;margin:0 0 2px 0'>")
            .append(esc(names.name(id))).append("</div>");
        if (card == null) {
            sb.append("<p><i>Карточный набор этой версии не загрузился — показать "
                + "содержимое карты нечем.</i></p></body></html>");
            return sb.toString();
        }
        Object sub = card.get("subtitle");
        if (sub != null) {
            sb.append("<div style='color:#777;margin-bottom:8px'>")
                .append(esc(String.valueOf(sub))).append("</div>");
        }
        // ЧЕЛОВЕЧЕСКОЕ ОПИСАНИЕ — первым и крупно. Это текст, который стоял бы на
        // настоящей бумажной карте: что игрок делает и зачем. Живёт в том же
        // карточном наборе, ключом `описание`, — источник один и для движка, и
        // для справочника (просьба дизайнера 13.08.2026).
        Object human = card.get("описание");
        if (human == null) {
            human = card.get("text_ru");
        }
        if (human != null) {
            sb.append("<div style='font-size:110%;line-height:1.45;margin:6px 0 12px 0'>")
                .append(esc(String.valueOf(human))).append("</div>");
        } else {
            sb.append("<div style='color:#a06000;margin:6px 0 12px 0'><i>Описание для "
                + "этой карты ещё не написано — в наборе нет ключа "
                + "<b>описание</b>.</i></div>");
        }
        Object label = card.get("label");
        if (label != null) {
            sb.append("<p style='color:#777'>").append(esc(String.valueOf(label)))
                .append("</p>");
        }
        sb.append("<div style='color:#777;margin-top:10px'>ТЕХНИЧЕСКОЕ ОПИСАНИЕ</div>");
        sb.append("<table cellspacing='0' cellpadding='4'>");
        for (Map.Entry<String, Object> e : card.entrySet()) {
            String k = e.getKey();
            // «описание» и «text_ru» уже показаны крупно сверху — второй раз, да ещё
            // мелким в технической таблице, они только загромождают разворот.
            if ("name".equals(k) || "id".equals(k) || "subtitle".equals(k)
                    || "label".equals(k) || "описание".equals(k) || "text_ru".equals(k)) {
                continue;
            }
            sb.append("<tr><td valign='top' style='color:#777'>").append(esc(fieldName(k)))
                .append("</td><td valign='top'>").append(esc(value(e.getValue())))
                .append("</td></tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    /** Имя поля карты по-человечески; неизвестное показываем как есть. */
    private static String fieldName(String key) {
        return switch (key) {
            case "effect" -> "эффект";
            case "params" -> "значения";
            case "cost" -> "цена";
            case "vp" -> "победные очки";
            case "condition" -> "условие";
            case "conditions" -> "условия";
            case "target" -> "цель";
            case "kind" -> "вид";
            case "deck" -> "колода";
            case "left" -> "левое предложение";
            case "right" -> "правое предложение";
            case "parts" -> "части";
            case "pattern" -> "рисунок развёртывания";
            case "ability" -> "способность";
            case "trigger" -> "когда срабатывает";
            case "slot" -> "куда ставится";
            // Поля, встречающиеся в наборах: без подписи они выходили на экран
            // внутренним именем (замечено при сборке каталога справочника).
            case "type" -> "разновидность";
            case "requirement" -> "что требуется";
            case "enhanced" -> "усиленное условие";
            case "base_reward" -> "награда";
            case "special_reward" -> "особая награда";
            case "top" -> "верх карты";
            case "bottom" -> "низ карты";
            case "predicate" -> "проверка";
            case "passive" -> "пассивная способность";
            case "tier" -> "тир";
            case "joker" -> "джокер";
            case "maneuver" -> "есть плашка манёвра";
            case "inert" -> "движком ещё не исполняется";
            case "container_slot" -> "ячейка под контейнер";
            case "a" -> "вариант А";
            case "b" -> "вариант Б";
            case "unit" -> "род войск";
            case "hp_bonus" -> "надбавка прочности";
            case "vp_on_card" -> "победных очков за карту";
            case "deploy" -> "развёртывание";
            case "objects" -> "из чего рисунок";
            case "relation" -> "как расположены";
            case "anchor" -> "условие на середину";
            case "symbols" -> "набор символов";
            case "what" -> "что именно";
            case "count" -> "сколько";
            default -> key;
        };
    }

    private static String value(Object v) {
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(fieldName(String.valueOf(e.getKey()))).append(": ")
                    .append(value(e.getValue()));
            }
            return sb.toString();
        }
        if (v instanceof List<?> l) {
            StringBuilder sb = new StringBuilder();
            for (Object o : l) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("• ").append(value(o));
            }
            return sb.toString();
        }
        return String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> find(ContentLibrary content, String kind, String id) {
        if (content == null) {
            return null;
        }
        try {
            return content.get(kind).find(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\n", "<br>");
    }
}
