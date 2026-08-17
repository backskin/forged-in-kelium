package kelium.engine;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import kelium.core.GameState;
import kelium.dataio.Ctx;
import kelium.core.PlayerState;

/**
 * Symbols — СИМВОЛЫ СУПЕР ЗАДАНИЙ (правило «Супер задания 2.0», 12.08.2026).
 *
 * <p>Четыре формы (круг, квадрат, треугольник, песочные часы) напечатаны на
 * картах: на КАЖДОЙ обычной карте арсенала и на ЧАСТИ карт контейнеров. Чтобы
 * выполнить вторую часть супер задания, игрок подсовывает карты под планшет
 * войск и вскрывает их СПЕЦ-действиями, пока не откроет требуемый набор.
 *
 * <p>Разметка живёт отдельным файлом данных ({@code cards/symbols.<версия>.yaml}),
 * а не полем в колодах: символы — это модуль супер заданий, их можно менять и
 * выключать, не трогая сами карты. Файл — отображение «форма → список карт»,
 * поэтому обычный загрузчик колод ({@link kelium.dataio.ContentSet}) к нему не
 * применяется, и читаем мы его здесь.
 */
public final class Symbols {

    private Symbols() {
    }

    /** Разметка: «арсенал»/«контейнеры» → id карты → форма. */
    public record Marking(Map<String, String> arsenal, Map<String, String> containers,
                          Map<String, String> glyphs) {

        /** Форма на карте арсенала (null — символа нет: стартовые карты). */
        public String ofArsenal(String cardId) {
            return arsenal.get(cardId);
        }

        /** Форма на карте контейнера (null — эта карта символа не несёт). */
        public String ofContainer(String cardId) {
            return containers.get(cardId);
        }

        public String glyph(String form) {
            return glyphs.getOrDefault(form, form);
        }

        /**
         * ВСЕ ФОРМЫ, КОТОРЫЕ ВСТРЕЧАЮТСЯ НА КАРТАХ АРСЕНАЛА.
         *
         * <p>Нужно, чтобы раскладывать символы по ячейкам супер-задания: класть
         * туда форму, которой нет ни на одной карте арсенала, значит выдать
         * игроку ячейку, которую нечем снять. Список берётся из самой разметки —
         * поменяли колоду, поменялся и набор форм, без правки кода.
         */
        public List<String> allForms() {
            java.util.LinkedHashSet<String> forms =
                new java.util.LinkedHashSet<>(arsenal.values());
            forms.remove(null);
            return List.copyOf(forms);
        }
    }

    private static final Map<String, Marking> CACHE = new ConcurrentHashMap<>();
    private static final Marking EMPTY =
        new Marking(Map.of(), Map.of(), Map.of());

    /**
     * Прочитать разметку символов. Нет файла или версии — возвращается ПУСТАЯ
     * разметка: партия просто играется без символов, а не падает.
     */
    @SuppressWarnings("unchecked")
    public static Marking load(Path dataRoot, String version) {
        if (dataRoot == null || version == null) {
            return EMPTY;
        }
        String key = dataRoot + "@" + version;
        return CACHE.computeIfAbsent(key, k -> {
            Path p = dataRoot.resolve("cards").resolve("symbols." + version + ".yaml");
            if (!Files.exists(p)) {
                return EMPTY;
            }
            try (InputStream in = Files.newInputStream(p)) {
                Map<String, Object> doc = new org.yaml.snakeyaml.Yaml()
                    .load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                Map<String, String> glyphs = new LinkedHashMap<>();
                for (Object fo : (List<Object>) doc.getOrDefault("forms", List.of())) {
                    Map<String, Object> f = (Map<String, Object>) fo;
                    glyphs.put(String.valueOf(f.get("id")), String.valueOf(f.get("glyph")));
                }
                return new Marking(flatten(doc.get("arsenal")),
                    flatten(doc.get("containers")), glyphs);
            } catch (Exception e) {
                System.err.println("[SETUP] символы не прочитаны (" + p + "): " + e.getMessage());
                return EMPTY;
            }
        });
    }

    /** «форма → [карты]» превращаем в «карта → форма». */
    @SuppressWarnings("unchecked")
    private static Map<String, String> flatten(Object byForm) {
        Map<String, String> out = new HashMap<>();
        if (!(byForm instanceof Map<?, ?> m)) {
            return out;
        }
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String form = String.valueOf(e.getKey());
            if (e.getValue() instanceof List<?> ids) {
                for (Object id : ids) {
                    out.put(String.valueOf(id), form);
                }
            }
        }
        return out;
    }

    /** Разметка для текущей партии (версия берётся из ruleset). */
    public static Marking of(GameState s) {
        Object v = Ctx.rules(s).get("content_versions.symbols", null);
        return load(Ctx.cfg(s).dataRoot, v == null ? null : v.toString());
    }

    /**
     * ОТКРЫТЫЕ символы игрока: вскрытые карты под планшетом плюс установленные
     * карты арсенала (их символ виден всегда — карта лежит лицом).
     */
    public static List<String> revealed(GameState s, PlayerState p) {
        Marking m = of(s);
        List<String> out = new ArrayList<>();
        for (PlayerState.TuckedCard t : p.tucked) {
            if (!t.revealed) {
                continue;
            }
            String form = "container".equals(t.kind) ? m.ofContainer(t.cardId)
                : m.ofArsenal(t.cardId);
            if (form != null) {
                out.add(form);
            }
        }
        if (Boolean.TRUE.equals(Ctx.rules(s).get("symbols.installed_arsenal_counts", Boolean.TRUE))) {
            for (String cid : p.allInstalledArsenal()) {
                String form = m.ofArsenal(cid);
                if (form != null) {
                    out.add(form);
                }
            }
        }
        return out;
    }

    /**
     * Хватает ли открытых символов на требование карты.
     *
     * @param slack сколько символов разрешено «дообнажить» самой проверкой
     *              (ruleset {@code super_objectives.check_reveals_last_symbol}:
     *              проверка вскрывает последний символ, значит одного не хватать
     *              МОЖЕТ, если он лежит под планшетом закрытым)
     */
    public static boolean satisfied(GameState s, PlayerState p, List<String> required, int slack) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        Map<String, Integer> have = count(revealed(s, p));
        Map<String, Integer> hidden = count(hiddenForms(s, p));
        int missing = 0;
        for (Map.Entry<String, Integer> e : count(required).entrySet()) {
            int need = e.getValue() - have.getOrDefault(e.getKey(), 0);
            if (need <= 0) {
                continue;
            }
            // недостающее можно закрыть только тем, что уже лежит под планшетом
            int fromHidden = Math.min(need, hidden.getOrDefault(e.getKey(), 0));
            missing += need - fromHidden > 0 ? need : fromHidden;
            if (need - fromHidden > 0) {
                return false;               // такого символа нет даже закрытым
            }
        }
        return missing <= slack;
    }

    /** Формы карт, лежащих под планшетом ЗАКРЫТЫМИ. */
    public static List<String> hiddenForms(GameState s, PlayerState p) {
        Marking m = of(s);
        List<String> out = new ArrayList<>();
        for (PlayerState.TuckedCard t : p.tucked) {
            if (t.revealed) {
                continue;
            }
            String form = "container".equals(t.kind) ? m.ofContainer(t.cardId)
                : m.ofArsenal(t.cardId);
            if (form != null) {
                out.add(form);
            }
        }
        return out;
    }

    private static Map<String, Integer> count(List<String> forms) {
        Map<String, Integer> out = new HashMap<>();
        for (String f : forms) {
            out.merge(f, 1, Integer::sum);
        }
        return out;
    }

    /** Требование символов с карты супер задания (пусто — требования нет). */
    @SuppressWarnings("unchecked")
    public static List<String> required(GameState s, String superCardId) {
        if (superCardId == null) {
            return List.of();
        }
        Map<String, Object> card = Ctx.cards(s, "super_objectives").find(superCardId);
        if (card == null || !(card.get("symbols") instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            out.add(String.valueOf(o));
        }
        return out;
    }
}
