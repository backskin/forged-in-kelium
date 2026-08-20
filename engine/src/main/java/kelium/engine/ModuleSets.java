package kelium.engine;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import kelium.core.GameState;
import kelium.dataio.Ctx;

/**
 * ModuleSets — НАБОРЫ ЖЕТОНОВ МОДУЛЕЙ И МЕШКИ («Модули 2.0», 12.08.2026).
 *
 * <p>Два изменения против прежнего устройства:
 * <ol>
 *   <li>жетоны больше не вшиты в код (M1–M4 в {@link Modules}), а описаны
 *       данными: {@code data/modules/modules.<версия>.yaml}. Наборов может быть
 *       несколько — R0 (прежний), R1 «размен целей», R2 «характеристики»;</li>
 *   <li>награда «модуль» = ТЯНУТЬ СЛУЧАЙНЫЙ ЖЕТОН ИЗ МЕШКА. В мешок со старта
 *       кладутся полные наборы по числу игроков; вытянутый жетон из мешка
 *       уходит навсегда. Игрок решает только, куда его вставить.</li>
 * </ol>
 *
 * <p>Файл — не колода карт (в нём отображения наборов и мешков), поэтому общий
 * загрузчик {@link kelium.dataio.ContentSet} к нему не применяется.
 */
public final class ModuleSets {

    private ModuleSets() {
    }

    /**
     * Один жетон модуля.
     *
     * @param id      идентификатор ({@code R1-1})
     * @param targets цели обычной стороны (для красных «атака по…»), может быть пусто
     * @param gold    цели золотой стороны; пусто — золото задано режимом
     * @param goldBoth золотая сторона бьёт ОБЕ цели (иначе — выбор из списка)
     * @param stat    характеристика вместо цели: {@code hp} | {@code speed} | null
     * @param plus    насколько растёт характеристика на обычной стороне
     * @param ammo    цена атаки этого жетона в боеприпасах
     * @param effect  особый эффект вместо цели (null — нет)
     */
    public record ModuleToken(String id, List<String> targets, List<String> gold,
                              boolean goldBoth, String stat, int plus, int ammo,
                              String effect) {
    }

    /**
     * Набор жетонов: {@code R1}, {@code C} и т. п.
     *
     * @param fixed набор УЖЕ полный сам по себе (например, R30 — все шесть пар
     *              целей по два жетона): в мешок кладётся РОВНО он, без копий
     *              на каждого игрока. Раньше мешок всегда масштабировался по
     *              числу игроков (наследие модели, где комплект был личным);
     *              решение дизайнера 20.08.2026 — раз мешок общий, а не
     *              раздаётся по игрокам, полные наборы множить незачем.
     */
    public record ModuleSet(String id, String name, boolean proposal, boolean fixed,
                            int ammo, List<ModuleToken> tokens) {
    }

    /** Всё, что прочитано из файла модулей. */
    public record Library(Map<String, ModuleSet> redSets, Map<String, ModuleSet> blueSets,
                          Map<String, List<String>> redBags,
                          Map<String, List<String>> blueBags) {

        public boolean isEmpty() {
            return redSets.isEmpty() && blueSets.isEmpty();
        }
    }

    private static final Library EMPTY = new Library(Map.of(), Map.of(), Map.of(), Map.of());
    private static final Map<String, Library> CACHE = new ConcurrentHashMap<>();

    /** Прочитать наборы модулей. Нет файла — пустая библиотека (играем по-старому). */
    @SuppressWarnings("unchecked")
    public static Library load(Path dataRoot, String version) {
        if (dataRoot == null || version == null) {
            return EMPTY;
        }
        return CACHE.computeIfAbsent(dataRoot + "@" + version, k -> {
            Path p = dataRoot.resolve("modules").resolve("modules." + version + ".yaml");
            if (!Files.exists(p)) {
                return EMPTY;
            }
            try (InputStream in = Files.newInputStream(p)) {
                Map<String, Object> doc = new org.yaml.snakeyaml.Yaml()
                    .load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                return new Library(sets(doc.get("red_sets")), sets(doc.get("blue_sets")),
                    bags(doc.get("red_bags")), bags(doc.get("blue_bags")));
            } catch (Exception e) {
                System.err.println("[SETUP] наборы модулей не прочитаны (" + p + "): "
                    + e.getMessage());
                return EMPTY;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModuleSet> sets(Object raw) {
        Map<String, ModuleSet> out = new LinkedHashMap<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object so : list) {
            Map<String, Object> m = (Map<String, Object>) so;
            int setAmmo = m.get("ammo") instanceof Number n ? n.intValue() : 1;
            List<ModuleToken> tokens = new ArrayList<>();
            for (Object to : (List<Object>) m.getOrDefault("tokens", List.of())) {
                Map<String, Object> t = (Map<String, Object>) to;
                tokens.add(new ModuleToken(
                    String.valueOf(t.get("id")),
                    strings(t.get("plain")),
                    strings(t.get("gold")),
                    "both".equals(String.valueOf(t.getOrDefault("gold_mode", ""))),
                    t.get("stat") == null ? null : String.valueOf(t.get("stat")),
                    t.get("plus") instanceof Number pn ? pn.intValue() : 0,
                    setAmmo,
                    t.get("effect") == null ? null : String.valueOf(t.get("effect"))));
            }
            ModuleSet set = new ModuleSet(String.valueOf(m.get("id")),
                String.valueOf(m.getOrDefault("name", m.get("id"))),
                Boolean.TRUE.equals(m.get("proposal")), Boolean.TRUE.equals(m.get("fixed")),
                setAmmo, tokens);
            out.put(set.id(), set);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> bags(Object raw) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object bo : list) {
            Map<String, Object> b = (Map<String, Object>) bo;
            out.put(String.valueOf(b.get("id")), strings(b.get("sets")));
        }
        return out;
    }

    private static List<String> strings(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    // ==================== мешки партии ====================

    /** Библиотека наборов для этой партии (версия из ruleset). */
    public static Library of(GameState s) {
        Object v = Ctx.rules(s).get("content_versions.modules", null);
        return load(Ctx.cfg(s).dataRoot, v == null ? null : v.toString());
    }

    /** Включены ли мешки в этой версии правил. */
    public static boolean bagsEnabled(GameState s) {
        return Boolean.TRUE.equals(Ctx.rules(s).get("modules.from_bag", Boolean.FALSE))
            && !of(s).isEmpty();
    }

    /**
     * Собрать мешок. По умолчанию — по ПОЛНОМУ набору на каждого игрока: один
     * набор — 4 жетона, значит 8/12/16 жетонов на 2/3/4 игроков (числа
     * дизайнера, наследие модели, где комплект был личным). Если мешок собран
     * из ДВУХ наборов, жетонов будет вдвое больше — так и задумано («смешать
     * R1 и R2»), но состав тогда шире, чем 8/12/16.
     *
     * <p>Набор с {@link ModuleSet#fixed()} — исключение: кладётся РОВНО он,
     * без копий на игрока (решение дизайнера 20.08.2026 — R30/C30 уже полные
     * сами по себе, и раз мешок общий, а не раздаётся по игрокам, множить его
     * незачем).
     */
    public static List<String> buildBag(Library lib, Map<String, ModuleSet> sets,
                                        String bagId, int players, Random rng) {
        List<String> bag = new ArrayList<>();
        List<String> setIds = lib.redBags().containsKey(bagId) ? lib.redBags().get(bagId)
            : lib.blueBags().getOrDefault(bagId, List.of());
        for (String sid : setIds) {
            ModuleSet set = sets.get(sid);
            if (set == null || set.proposal()) {
                continue;                 // предложения в мешок не кладём
            }
            int copies = set.fixed() ? 1 : players;
            for (int copy = 0; copy < copies; copy++) {
                for (ModuleToken t : set.tokens()) {
                    bag.add(t.id());
                }
            }
        }
        java.util.Collections.shuffle(bag, rng);
        return bag;
    }

    /** Вытянуть случайный жетон (удаляется из мешка). null — мешок пуст. */
    public static String draw(List<String> bag, Random rng) {
        if (bag == null || bag.isEmpty()) {
            return null;
        }
        return bag.remove(rng.nextInt(bag.size()));
    }

    /** Найти жетон по id среди наборов (красных и синих). */
    public static ModuleToken token(Library lib, String id) {
        for (ModuleSet set : lib.redSets().values()) {
            for (ModuleToken t : set.tokens()) {
                if (t.id().equals(id)) {
                    return t;
                }
            }
        }
        for (ModuleSet set : lib.blueSets().values()) {
            for (ModuleToken t : set.tokens()) {
                if (t.id().equals(id)) {
                    return t;
                }
            }
        }
        return null;
    }
}
