package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.cards.objectives.ЗаданиеИзЯзыка;
import kelium.cards.язык.Срабатывание;
import kelium.core.GameState;
import kelium.core.TurnJournal;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.engine.cards.EngineCardContext;

/**
 * БУЛЬОН КАРТ (Карты 2.0, 30.09.2026) — все сочетания кирпичиков, из которых
 * потом отбирается набор. Программа перебирает, человек (и нейросеть) не
 * придумывают «хорошие синергии» — принцип «не подгонять задачу к ответу».
 *
 * <p>Механический отсев здесь же, до всякого измерения:
 * <ul>
 *   <li>форма карты по правилам дизайнера ({@code Лицо.жалоба});</li>
 *   <li>задание-состояние не выполнено ни у кого на подготовке (иначе даром);</li>
 *   <li>карты не сыплются сами: срабатывание, дающее карту, — только на
 *       редком событии и не чаще раза за ход (заказ дизайнера 30.09.2026);</li>
 *   <li>награды предсказуемы: контейнеров в наградах и эффектах нет.</li>
 * </ul>
 *
 * <p>Итог — {@code design-docs/фигуры/бульон/задания.yaml} и
 * {@code арсенал.yaml}: запись каждой карты на языке карт и её печатный текст.
 */
public final class Бульон {

    private Бульон() {
    }

    private static final List<String> ВЕТКИ = List.of("mining", "build_miner", "energy_swap",
        "build_plant", "assembly", "build_military", "movement", "combat", "market", "science");
    private static final List<String> РАЗВИЛКИ = List.of("extract", "power", "supply", "command",
        "develop");

    public static void main(String[] args) throws Exception {
        Path папка = Path.of(args.length > 0 ? args[0] : "design-docs/фигуры/бульон");
        Files.createDirectories(папка);
        List<Map<String, Object>> требования = требования();
        List<GameState> столы = столы();
        List<Map<String, Object>> задания = new ArrayList<>();
        int номер = 0;
        int даром = 0;
        int форма = 0;
        for (Map<String, Object> т : требования) {
            Map<String, Object> язык = new LinkedHashMap<>();
            язык.put("имя", "Задание " + (номер + 1));
            язык.put("требование", т);
            язык.put("награда", Map.of("действие", "mining|combat"));
            ЗаданиеИзЯзыка карта = new ЗаданиеИзЯзыка("g_" + номер, язык);
            if (карта.лицо().жалоба() != null) {
                форма++;
                continue;
            }
            if (!карта.требование().происшествие() && выполненоНаСтарте(карта, столы)) {
                даром++;
                continue;
            }
            Map<String, Object> запись = new LinkedHashMap<>();
            запись.put("id", "g_" + номер);
            запись.put("текст", карта.лицо().условие());
            запись.put("вид", карта.требование().происшествие() ? "происшествие" : "состояние");
            запись.put("требование", т);
            задания.add(запись);
            номер++;
        }
        List<Map<String, Object>> арсенал = срабатывания();
        Files.writeString(папка.resolve("задания.yaml"), yaml(задания), StandardCharsets.UTF_8);
        Files.writeString(папка.resolve("арсенал.yaml"), yaml(арсенал), StandardCharsets.UTF_8);
        System.out.printf("требований в бульоне: %d (отсеяно: форма %d, даром на старте %d)%n",
            задания.size(), форма, даром);
        System.out.printf("срабатываний арсенала: %d%n", арсенал.size());
    }

    // ======================================================================
    //  Требования заданий
    // ======================================================================

    private static Map<String, Object> группа(String кто, boolean свои, String сост) {
        Map<String, Object> г = new LinkedHashMap<>();
        г.put("кто", кто);
        г.put("чьё", свои ? "своё" : "врага");
        if (сост != null) {
            г.put("сост", сост);
        }
        return г;
    }

    static List<Map<String, Object>> требования() {
        List<Map<String, Object>> out = new ArrayList<>();
        // ЖЕТОНЫ НА ПОЛЕ
        for (String кто : List.of("ДОБЫТЧИК", "ЭНЕРГОСТАНЦИЯ", "ВОЕННОЕ", "ЗДАНИЕ")) {
            for (String сост : new String[]{null, "ЗАПИТАН"}) {
                for (int n = 2; n <= Math.min(4, kelium.cards.язык.Кто.valueOf(кто).наибольшее()); n++) {
                    out.add(Map.of("узел", "жетоны", "группа", группа(кто, true, сост), "сколько", n,
                        "разных_гексов", false));
                }
            }
        }
        for (String кто : List.of("ПЕХОТА", "ТЕХНИКА", "АВИАЦИЯ", "ВЫШКА", "ВОЙСКО")) {
            for (int n = 2; n <= 4; n++) {
                for (boolean разных : new boolean[]{false, true}) {
                    out.add(Map.of("узел", "жетоны", "группа", группа(кто, true, null), "сколько", n,
                        "разных_гексов", разных));
                }
            }
        }
        // ОТНОШЕНИЯ СО ВРАГОМ
        for (String а : List.of("ВОЕННОЕ", "ДОБЫТЧИК", "ЭНЕРГОСТАНЦИЯ", "ВОЙСКО", "ТЕХНИКА", "ПЕХОТА",
                "АВИАЦИЯ")) {
            for (String б : List.of("ВОЙСКО", "ЗДАНИЕ", "ДОБЫТЧИК", "ЦУ")) {
                for (boolean соседний : new boolean[]{true, false}) {
                    boolean зданиеА = List.of("ВОЕННОЕ", "ДОБЫТЧИК", "ЭНЕРГОСТАНЦИЯ").contains(а);
                    if (!соседний && зданиеА && !б.equals("ВОЙСКО")) {
                        continue;   // своё здание на гексе с чужим зданием — редкость без смысла
                    }
                    for (int n = 1; n <= 2; n++) {
                        out.add(Map.of("узел", "рядом", "а", группа(а, true, null),
                            "б", группа(б, false, null), "соседний", соседний, "гексов", n));
                    }
                }
            }
        }
        // РЕСУРСЫ, АРСЕНАЛ, СВАЛКА
        for (int n : new int[]{6, 8, 10}) {
            out.add(Map.of("узел", "ресурс", "ресурс", "COIN", "сколько", n));
        }
        for (String р : List.of("AMMO", "KELIUM", "TROPHY")) {
            for (int n = 2; n <= 4; n++) {
                out.add(Map.of("узел", "ресурс", "ресурс", р, "сколько", n));
            }
        }
        for (int n = 2; n <= 3; n++) {
            out.add(Map.of("узел", "арсенал", "сколько", n));
            for (String кто : List.of("ЖЕТОН", "ЗДАНИЕ", "ВОЙСКО")) {
                out.add(Map.of("узел", "свалка", "группа", группа(кто, false, null), "сколько", n));
            }
        }
        // ПРОИСШЕСТВИЯ ХОДА — связки карт и веток
        for (int i = 0; i < ВЕТКИ.size(); i++) {
            for (int k = i + 1; k < ВЕТКИ.size(); k++) {
                if (развилка(ВЕТКИ.get(i)).equals(развилка(ВЕТКИ.get(k)))) {
                    continue;       // две ветки одной развилки — это узел «обе ветки»
                }
                out.add(Map.of("узел", "ветки", "ветки", List.of(ВЕТКИ.get(i), ВЕТКИ.get(k))));
            }
        }
        for (String р : РАЗВИЛКИ) {
            out.add(Map.of("узел", "обе_ветки", "развилка", р));
        }
        for (int k = 2; k <= 3; k++) {
            out.add(Map.of("узел", "очередь_задания", "k", k));
            out.add(Map.of("узел", "очередь_спец", "k", k));
        }
        for (int n = 1; n <= 2; n++) {
            out.add(Map.of("узел", "сожги", "сколько", n));
        }
        out.add(Map.of("узел", "установи"));
        for (String цель : List.of("ЖЕТОН", "ЗДАНИЕ", "ВОЙСКО", "ДОБЫТЧИК", "ЭНЕРГОСТАНЦИЯ", "ВОЕННОЕ")) {
            for (String кем : new String[]{null, "ПЕХОТА", "ТЕХНИКА", "АВИАЦИЯ"}) {
                for (int n = 1; n <= 2; n++) {
                    Map<String, Object> у = new LinkedHashMap<>();
                    у.put("узел", "уничтожь");
                    у.put("цель", группа(цель, false, null));
                    if (кем != null) {
                        у.put("кем", кем);
                    }
                    у.put("сколько", n);
                    out.add(у);
                }
            }
        }
        for (String вид : new String[]{null, "miner", "plant", "military"}) {
            for (int n = 1; n <= 2; n++) {
                Map<String, Object> п = new LinkedHashMap<>();
                п.put("узел", "построй");
                if (вид != null) {
                    п.put("вид", вид);
                }
                п.put("сколько", n);
                out.add(п);
            }
        }
        for (int n = 2; n <= 3; n++) {
            out.add(Map.of("узел", "найми", "сколько", n));
        }
        // ПРОИСШЕСТВИЯ ЭКОНОМИЧЕСКИХ РАЗВИЛОК (01.10.2026): без них у Добычи и
        // Энергии в бульоне были только «имей N зданий». Дописаны В КОНЕЦ —
        // номера прежних требований (и счёт прогонов по ним) не сдвигаются.
        for (int n = 2; n <= 4; n++) {
            out.add(Map.of("узел", "добудь", "сколько", n));
        }
        for (int n = 2; n <= 3; n++) {
            out.add(Map.of("узел", "запитай", "сколько", n));
            out.add(Map.of("узел", "выпусти", "сколько", n));
        }
        return out;
    }

    private static String развилка(String ветка) {
        return kelium.engine.Срабатывания.развилка(ветка);
    }

    /** Столы сразу после подготовки: на них задание-состояние не должно быть выполнено. */
    private static List<GameState> столы() {
        List<GameState> out = new ArrayList<>();
        for (int игроков = 2; игроков <= 4; игроков++) {
            for (long seed = 1; seed <= 4; seed++) {
                GameState s = Setup.buildGame(GameConfig.build(игроков, seed * 101 + игроков));
                s.journal = new TurnJournal(игроков);
                out.add(s);
            }
        }
        return out;
    }

    private static boolean выполненоНаСтарте(ЗаданиеИзЯзыка карта, List<GameState> столы) {
        for (GameState s : столы) {
            for (int место = 0; место < s.numPlayers(); место++) {
                if (карта.satisfied(new EngineCardContext(s, место))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ======================================================================
    //  Срабатывания арсенала
    // ======================================================================

    static List<Map<String, Object>> срабатывания() {
        List<Map<String, Object>> события = new ArrayList<>();
        for (String в : ВЕТКИ) {
            события.add(Map.of("событие", "ветка", "ветка", в));
        }
        for (String р : РАЗВИЛКИ) {
            события.add(Map.of("событие", "развилка", "развилка", р));
        }
        for (String с : List.of("задание", "сжёг", "установил", "потерял", "совпадение", "низ")) {
            события.add(Map.of("событие", с));
        }
        for (String жертва : new String[]{null, "building", "unit"}) {
            for (String кем : new String[]{null, "infantry", "vehicle", "aircraft"}) {
                Map<String, Object> у = new LinkedHashMap<>();
                у.put("событие", "уничтожил");
                if (жертва != null) {
                    у.put("жертва", жертва);
                }
                if (кем != null) {
                    у.put("кем", кем);
                }
                события.add(у);
            }
        }
        List<Map<String, Object>> эффекты = new ArrayList<>();
        эффекты.add(Map.of("effect", "gain", "params", Map.of("coin", 1)));
        эффекты.add(Map.of("effect", "gain", "params", Map.of("coin", 2)));
        эффекты.add(Map.of("effect", "gain", "params", Map.of("ammo", 1)));
        эффекты.add(Map.of("effect", "gain", "params", Map.of("kelium", 1)));
        эффекты.add(Map.of("effect", "gain", "params", Map.of("trophy", 1)));
        эффекты.add(Map.of("effect", "gain", "params", Map.of("objective_cards", 1)));
        эффекты.add(Map.of("effect", "спец", "params", Map.of("n", 1)));
        эффекты.add(Map.of("effect", "heal_one", "params", Map.of()));
        for (String в : ВЕТКИ) {
            эффекты.add(Map.of("effect", "free_action", "params", Map.of("action", в)));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int номер = 0;
        for (Map<String, Object> когда : события) {
            String что = String.valueOf(когда.get("событие"));
            boolean частое = что.equals("ветка") || что.equals("развилка");
            boolean чужойХод = что.equals("потерял");
            for (Map<String, Object> эф : эффекты) {
                String имя = String.valueOf(эф.get("effect"));
                boolean карта = эф.get("params") instanceof Map<?, ?> п && п.containsKey("objective_cards");
                // карты не сыплются сами: карта — только за редкое событие
                if (карта && частое) {
                    continue;
                }
                // в чужой ход спец-действие и ветка бессмысленны: хода нет
                if (чужойХод && (имя.equals("спец") || имя.equals("free_action"))) {
                    continue;
                }
                // ветка за ветку той же развилки — вторая ветка без платы, слишком жирно
                if (имя.equals("free_action") && частое) {
                    continue;
                }
                // посреди боя играют только безопасные эффекты (см. Срабатывания)
                if (что.equals("уничтожил") && имя.equals("free_action")) {
                    continue;
                }
                for (int предел = 1; предел <= 2; предел++) {
                    if (предел == 2 && (карта || имя.equals("спец") || имя.equals("free_action"))) {
                        continue;   // сильное — не чаще раза за ход
                    }
                    Map<String, Object> низ = new LinkedHashMap<>();
                    низ.put("когда", когда);
                    низ.put("эффект", эф);
                    низ.put("предел", предел);
                    Map<String, Object> запись = new LinkedHashMap<>();
                    запись.put("id", "t_" + номер++);
                    запись.put("текст", Срабатывание.текст(низ));
                    запись.put("низ", низ);
                    out.add(запись);
                }
            }
        }
        // ЭФФЕКТЫ, МЕНЯЮЩИЕ ПРАВИЛО (01.10.2026): «ветка → ресурс» — потолок
        // прежнего словаря, арсенал выходил плоским. Дописаны В КОНЦЕ: номера
        // прежних срабатываний (и счёт прогонов по ним) не сдвигаются. Посреди
        // боя они не играют (Срабатывания.БЕЗОПАСНЫЕ), поэтому «уничтожил» и
        // «потерял» (чужой ход) их не получают.
        List<Map<String, Object>> поступки = List.of(
            Map.of("effect", "move_unit", "params", Map.of("hexes", 1)),
            Map.of("effect", "place_damage", "params", Map.of()),
            Map.of("effect", "steal_resource", "params", Map.of("resource", "kelium", "max", 1)),
            Map.of("effect", "steal_resource", "params", Map.of("resource", "coin", "max", 1)),
            Map.of("effect", "steal_resource", "params", Map.of("resource", "ammo", "max", 1)));
        for (Map<String, Object> когда : события) {
            String что = String.valueOf(когда.get("событие"));
            if (что.equals("уничтожил") || что.equals("потерял") || что.equals("низ")) {
                continue;
            }
            for (Map<String, Object> эф : поступки) {
                Map<String, Object> низ = new LinkedHashMap<>();
                низ.put("когда", когда);
                низ.put("эффект", эф);
                низ.put("предел", 1);
                Map<String, Object> запись = new LinkedHashMap<>();
                запись.put("id", "t_" + номер++);
                запись.put("текст", Срабатывание.текст(низ));
                запись.put("низ", низ);
                out.add(запись);
            }
        }
        return out;
    }

    private static String yaml(List<Map<String, Object>> записи) {
        org.yaml.snakeyaml.DumperOptions o = new org.yaml.snakeyaml.DumperOptions();
        o.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        o.setAllowUnicode(true);
        o.setWidth(120);
        // Без общих ссылок: одна и та же запись («когда», «группа») в разных
        // картах иначе пишется якорем, и файл нечитаем обычным разборщиком.
        return new org.yaml.snakeyaml.Yaml(o).dump(копия(записи));
    }

    /** Глубокая копия списков и записей — у каждой карты свои объекты. */
    private static Object копия(Object x) {
        if (x instanceof Map<?, ?> m) {
            Map<Object, Object> out = new LinkedHashMap<>();
            for (var e : m.entrySet()) {
                out.put(e.getKey(), копия(e.getValue()));
            }
            return out;
        }
        if (x instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object v : l) {
                out.add(копия(v));
            }
            return out;
        }
        return x;
    }
}
