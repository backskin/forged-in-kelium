package kelium.gui.cardshop;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * БИБЛИОТЕКА КАРТ МАСТЕРСКОЙ (дизайнер 03.10.2026): у каждого типа — свой
 * каталог, упорядоченный список карт. Хранится файлом
 * {@code мастерская карт/библиотека/<ТИП>.yaml} — список описаний карт.
 *
 * <p>АВТОНУМЕРАЦИЯ: номер карты — её место в списке; вставили в середину —
 * следующие сдвинулись. Номер пишется в поле «номер» каждой карты, поэтому и
 * лицо, и каталог, и выгрузка в игру видят одно и то же.
 */
public final class Library {

    private final File dir;
    private final Map<CardSpec.Type, List<CardSpec>> lists = new EnumMap<>(CardSpec.Type.class);
    /** Правки раскладки на весь каталог типа: {элемент: {dx, dy, масштаб, …}}. */
    private final Map<CardSpec.Type, Map<String, Object>> layouts = new EnumMap<>(CardSpec.Type.class);

    public Library(File workFolder) {
        this.dir = new File(workFolder, "библиотека");
    }

    public File folder() {
        dir.mkdirs();
        return dir;
    }

    /** Список карт типа (загружается при первом обращении). */
    public List<CardSpec> list(CardSpec.Type t) {
        return lists.computeIfAbsent(t, this::load);
    }

    private File file(CardSpec.Type t) {
        return new File(folder(), t.name() + ".yaml");
    }

    @SuppressWarnings("unchecked")
    private List<CardSpec> load(CardSpec.Type t) {
        List<CardSpec> out = new ArrayList<>();
        File f = file(t);
        if (f.isFile()) {
            try {
                Object raw = new Yaml().load(Files.readString(f.toPath(), StandardCharsets.UTF_8));
                if (raw instanceof Map<?, ?> m && m.get("раскладка") instanceof Map<?, ?> lay) {
                    layouts.put(t, new LinkedHashMap<>((Map<String, Object>) lay));
                }
                if (raw instanceof Map<?, ?> m && m.get("карты") instanceof List<?> l) {
                    for (Object o : l) {
                        if (o instanceof Map<?, ?> cm) {
                            CardSpec c = new CardSpec(t);
                            c.fields.remove("иконки");
                            c.fields.putAll((Map<String, Object>) cm);
                            c.fields.put("тип", t.ru);
                            IconNumbers.migrate(c);
                            out.add(c);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("библиотека: не прочиталось " + f + ": " + e);
            }
        }
        renumber(t, out);
        return out;
    }

    /** Есть ли у типа номер на карте. */
    static boolean numbered(CardSpec.Type t) {
        return t != CardSpec.Type.CONTAINER && t != CardSpec.Type.SPAWN_HEX
            && t != CardSpec.Type.ORDER;
    }

    /** Номера по порядку списка. */
    public void renumber(CardSpec.Type t) {
        renumber(t, list(t));
    }

    private static void renumber(CardSpec.Type t, List<CardSpec> l) {
        if (!numbered(t)) {
            return;
        }
        for (int i = 0; i < l.size(); i++) {
            l.get(i).fields.put("номер", i + 1);
        }
    }

    /** Вставить карту на место at (0..size); номера пересчитываются. */
    public void insert(CardSpec.Type t, int at, CardSpec c) {
        List<CardSpec> l = list(t);
        l.add(Math.max(0, Math.min(at, l.size())), c);
        renumber(t, l);
    }

    public void remove(CardSpec.Type t, int i) {
        List<CardSpec> l = list(t);
        if (i >= 0 && i < l.size()) {
            l.remove(i);
        }
        renumber(t, l);
    }

    public void move(CardSpec.Type t, int from, int to) {
        List<CardSpec> l = list(t);
        if (from < 0 || from >= l.size() || to < 0 || to >= l.size()) {
            return;
        }
        l.add(to, l.remove(from));
        renumber(t, l);
    }

    /** Записать каталог типа на диск. */
    public void save(CardSpec.Type t) throws Exception {
        List<Object> cards = new ArrayList<>();
        for (CardSpec c : list(t)) {
            cards.add(new LinkedHashMap<>(c.fields));
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("тип", t.ru);
        Map<String, Object> lay = layouts.get(t);
        if (lay != null && !lay.isEmpty()) {
            doc.put("раскладка", lay);
        }
        doc.put("карты", cards);
        DumperOptions o = new DumperOptions();
        o.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        o.setAllowUnicode(true);
        o.setWidth(200);
        File f = file(t);
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        Files.writeString(tmp.toPath(), new Yaml(o).dump(doc), StandardCharsets.UTF_8);
        Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Название набора карт (файл «набор.yaml» в папке библиотеки). */
    public String name() {
        File f = new File(folder(), "набор.yaml");
        try {
            if (f.isFile()) {
                Object raw = new Yaml().load(Files.readString(f.toPath(), StandardCharsets.UTF_8));
                if (raw instanceof Map<?, ?> m && m.get("название") != null) {
                    return String.valueOf(m.get("название"));
                }
            }
        } catch (Exception e) {
            // нет названия — ниже по умолчанию
        }
        return "Набор без названия";
    }

    public void setName(String n) throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("название", n);
        DumperOptions o = new DumperOptions();
        o.setAllowUnicode(true);
        o.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Files.writeString(new File(folder(), "набор.yaml").toPath(), new Yaml(o).dump(doc),
            StandardCharsets.UTF_8);
    }

    /** Правки раскладки каталога типа (изменяемая запись). */
    public Map<String, Object> layout(CardSpec.Type t) {
        list(t);
        return layouts.computeIfAbsent(t, x -> new LinkedHashMap<>());
    }

    /** Название карты для каталога: имя, а у карт без имени — что на ней написано. */
    static String title(CardSpec c) {
        String n = c.text("имя");
        if (n.isBlank()) {
            n = switch (c.type()) {
                case MARKET -> first(c.text("слева")) + " / " + first(c.text("справа"));
                case SPAWN_HEX -> c.text("сторона") + " · " + c.text("число");
                default -> c.text("условие");
            };
        }
        if (c.type() == CardSpec.Type.ORDER) {
            n = c.text("цвет") + " · " + n;
        }
        n = n.replace('\n', ' ').trim();
        return n.isBlank() ? "без названия" : n;
    }

    private static String first(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    static String cardsWord(int n) {
        int a = n % 10;
        int b = n % 100;
        return a == 1 && b != 11 ? "карта" : a >= 2 && a <= 4 && (b < 12 || b > 14) ? "карты" : "карт";
    }

    /** Все карты всех типов — для выгрузки в игру. */
    public List<CardSpec> all() {
        List<CardSpec> out = new ArrayList<>();
        for (CardSpec.Type t : CardSpec.Type.values()) {
            out.addAll(list(t));
        }
        return out;
    }

    /**
     * Первый запуск: каталог типа пуст, файла нет — забрать карты из .kcard
     * папки (выпуск 27.09.2026), по возрастанию их номеров.
     */
    public void seedFrom(File folder) {
        File[] fs = folder.listFiles((d, n) -> n.endsWith(".kcard"));
        if (fs == null) {
            return;
        }
        List<CardSpec> got = new ArrayList<>();
        for (File f : fs) {
            try {
                got.add(CardSpec.load(f));
            } catch (Exception e) {
                // битый файл — пропустить
            }
        }
        got.sort((a, b) -> Integer.compare(a.integer("номер", 999), b.integer("номер", 999)));
        for (CardSpec.Type t : CardSpec.Type.values()) {
            if (file(t).isFile() || !list(t).isEmpty()) {
                continue;
            }
            boolean any = false;
            for (CardSpec c : got) {
                if (c.type() == t) {
                    list(t).add(c);
                    any = true;
                }
            }
            if (any) {
                renumber(t);
                try {
                    save(t);
                } catch (Exception e) {
                    System.err.println("библиотека: " + e);
                }
            }
        }
    }
}
