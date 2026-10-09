package kelium.gui.cardshop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * КАРТА МАСТЕРСКОЙ → ЗАПИСЬ КОЛОДЫ ИГРЫ (дизайнер 03.10.2026: «чтобы можно
 * было писать не только текстовые требования, но и фигуры», «свойства арсенала,
 * которые срабатывают при наличии нужной фигуры»).
 *
 * <p>Задание — запись языка карт ({@code язык: имя, требование, награда, сверх,
 * верх, значок}); требование — узор из фигуры. Арсенал — {@code top} и
 * {@code bottom: {когда, эффект, предел, если}}; «если» — узор фигуры.
 * Текст условия, написанный словами без фигуры, игра не проверяет — такая
 * карта выгружается с пометкой.
 */
public final class GameEntry {

    private GameEntry() {
    }

    /** Ветки: код → печатное имя. */
    public static final String[][] BRANCHES = {
        {"mining", "Добыть"}, {"build_miner", "Построить добытчик"},
        {"energy_swap", "Переложить энергию"}, {"build_plant", "Построить энергостанцию"},
        {"assembly", "Выпустить"}, {"build_military", "Построить военное здание"},
        {"movement", "Манёвр"}, {"combat", "Бой"}, {"market", "Рынок"}, {"science", "Наука"},
    };
    /** Развилки: код → имя. */
    public static final String[][] FORKS = {
        {"extract", "Добыча"}, {"power", "Питание"}, {"supply", "Снабжение"},
        {"command", "Командование"}, {"develop", "Развитие"},
    };
    /** События свойства: код языка → подпись. */
    public static final String[][] EVENTS = {
        {"ход", "в начале своего хода"}, {"ветка", "играешь ветку"},
        {"развилка", "играешь любую ветку развилки"}, {"задание", "выполняешь задание"},
        {"сжёг", "сжигаешь карту"}, {"установил", "устанавливаешь арсенал"},
        {"контейнер", "вскрываешь контейнер"}, {"уничтожил", "уничтожаешь жетон врага"},
        {"потерял", "теряешь свой жетон"}, {"совпадение", "приказ совпал с чужим"},
    };
    /** Эффекты: код → подпись. */
    public static final String[][] EFFECTS = {
        {"спец", "спец-действия"}, {"coin", "монеты"}, {"ammo", "боеприпасы"},
        {"kelium", "келемий"}, {"trophy", "трофеи"}, {"free_action", "сыграй ветку"},
        {"heal_one", "сними 1 урон"}, {"move_unit", "шаг войска"},
        {"place_damage", "1 урон врагу рядом"},
    };

    /** Иконка награды → код действия (номера выгрузки 03.10.2026). */
    static String actionOf(String icon) {
        return switch (CardAssets.key(icon)) {
            case "36" -> "extract";
            case "34" -> "power";
            case "35" -> "supply";
            case "37" -> "command";
            case "40" -> "develop";
            case "38" -> "movement";
            case "39" -> "combat";
            case "41" -> "market";
            case "42" -> "science";
            default -> null;
        };
    }

    /** Иконка ресурса → ключ награды языка карт. */
    static String resourceOf(String icon) {
        return switch (CardAssets.key(icon)) {
            case "1" -> "монеты";
            case "3" -> "боеприпасы";
            case "5" -> "келемий";
            case "9" -> "трофеи";
            case "15" -> "картыАрсенала";
            case "17" -> "картыЗаданий";
            case "26" -> "спецДействий";
            case "48" -> "позолота";
            default -> null;
        };
    }

    /** Награда мастерской → запись награды языка карт. */
    static Map<String, Object> reward(Object raw, boolean main, List<String> warn) {
        Reward r = Reward.of(raw, main);
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> acts = new ArrayList<>();
        for (Reward.Item it : r.items) {
            String a = actionOf(it.icon());
            String res = resourceOf(it.icon());
            if (a != null) {
                acts.add(a);
            } else if ("позолота".equals(res)) {
                out.put("позолота", true);
            } else if (res != null) {
                out.merge(res, it.count(), (x, y) -> (Integer) x + (Integer) y);
            } else {
                warn.add("иконку {" + it.icon() + "} в награде игра не знает");
            }
        }
        if (!acts.isEmpty()) {
            out.put("действие", String.join("|", acts));
        }
        return out;
    }

    /** Запись задания для колоды; null — карту выгрузить нельзя (причина в warn). */
    public static Map<String, Object> objective(CardSpec c, List<String> warn) {
        Map<String, Object> язык = new LinkedHashMap<>();
        язык.put("имя", c.text("имя"));
        Figure f = Figure.of(c.fields.get("фигура"));
        if (f.isEmpty()) {
            warn.add("условие написано словами — игра его не проверит; нарисуйте фигуру. "
                + "Карта в колоду не выгружена");
            return null;
        }
        язык.put("требование", f.node(c.text("имя")));
        язык.put("награда", reward(c.fields.get("награда"), true, warn));
        Map<String, Object> сверх = reward(c.fields.get("доп_награда"), false, warn);
        if (!сверх.isEmpty()) {
            язык.put("сверх", сверх);
        }
        if (!c.text("утиль").isBlank()) {
            язык.put("верх", c.text("утиль"));
        } else {
            warn.add("верх для игры не выбран");
        }
        Object act = ((Map<?, ?>) язык.get("награда")).get("действие");
        if (act != null) {
            String first = String.valueOf(act).split("\\|")[0];
            язык.put("значок", switch (first) {
                case "extract", "power", "supply", "command", "develop" -> first;
                case "movement", "combat" -> "command";
                case "market", "science" -> "develop";
                default -> "supply";
            });
        }
        return язык;
    }

    /** Эффект свойства / верха → запись эффекта движка. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> effect(Object raw) {
        Map<String, Object> m = raw instanceof Map<?, ?> mm ? (Map<String, Object>) mm : Map.of();
        String что = String.valueOf(m.getOrDefault("что", "спец"));
        int n = m.get("сколько") instanceof Number k ? k.intValue() : 1;
        Map<String, Object> e = new LinkedHashMap<>();
        Map<String, Object> p = new LinkedHashMap<>();
        switch (что) {
            case "спец" -> {
                e.put("effect", "спец");
                p.put("n", n);
            }
            case "coin", "ammo", "kelium", "trophy" -> {
                e.put("effect", "gain");
                p.put(что, n);
            }
            case "free_action" -> {
                e.put("effect", "free_action");
                p.put("action", String.valueOf(m.getOrDefault("ветка", "mining")));
            }
            default -> e.put("effect", что);
        }
        e.put("params", p);
        return e;
    }

    /** «Когда» свойства → запись события. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> when(Object raw) {
        Map<String, Object> m = raw instanceof Map<?, ?> mm ? (Map<String, Object>) mm : Map.of();
        String ev = String.valueOf(m.getOrDefault("событие", "ход"));
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("событие", ev);
        if ("ветка".equals(ev)) {
            w.put("ветка", String.valueOf(m.getOrDefault("ветка", "mining")));
        } else if ("развилка".equals(ev)) {
            w.put("развилка", String.valueOf(m.getOrDefault("развилка", "extract")));
        }
        return w;
    }

    /** Низ арсенала для игры (свойство), с условием-фигурой. */
    public static Map<String, Object> bottom(CardSpec c) {
        Map<String, Object> low = new LinkedHashMap<>();
        low.put("когда", when(c.fields.get("свойство_когда")));
        low.put("эффект", effect(c.fields.get("свойство_эффект")));
        low.put("предел", Math.max(1, c.integer("свойство_предел", 1)));
        Figure f = Figure.of(c.fields.get("фигура"));
        if (!f.isEmpty()) {
            low.put("если", f.node(c.text("имя")));
        }
        return low;
    }

    /** Запись арсенала для колоды. */
    public static Map<String, Object> arsenal(CardSpec c, String id, List<String> warn) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("name", c.text("имя"));
        e.put("kind", c.type() == CardSpec.Type.ARSENAL_START ? "starting" : "regular");
        if (c.type() != CardSpec.Type.ARSENAL_SUPER) {      // у супер-арсенала утиля нет
            Map<String, Object> top = effect(c.fields.get("верх_эффект"));
            top.put("label", c.text("верх").replace("\n", " "));
            e.put("top", top);
        }
        if (c.fields.get("свойство_эффект") == null) {
            warn.add("свойство для игры не задано — карта будет без срабатывания");
        } else {
            e.put("bottom", bottom(c));
        }
        e.put("описание", c.text("низ").replace("**", ""));
        return e;
    }
}
