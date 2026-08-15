package kelium.agents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Геном — набор ЧИСЛОВЫХ ВЕСОВ стратегического бота. Это и есть «память» бота:
 * его характер и стратегия закодированы весами, которые эволюция настраивает по
 * итогам сыгранных партий (см. {@link EvoTrainer}).
 *
 * <p>Веса делятся на две группы:
 * <ul>
 *   <li>приоритеты действий/решений ({@code action.*}, {@code move.*} и т. п.) —
 *       как в {@link HeuristicAgent}, но обучаемые;
 *   <li>коэффициенты ОЦЕНОЧНОЙ ФУНКЦИИ позиции ({@code eval.*}) — во сколько бот
 *       ценит ПО, армию в дистанции удара, убиваемые цели, экономический задел,
 *       прогресс треков/заданий.
 * </ul>
 *
 * <p>Сериализация — плоский JSON {@code {"ключ": число, ...}} (подмножество YAML,
 * читается человеком). Файл генома лежит в {@code data/genomes/}.
 */
public final class Genome {

    /** Веса генома: имя -> значение. Порядок стабильный (для читаемого JSON). */
    public final Map<String, Double> weights;

    /**
     * СУДЬЯ ПОЗИЦИИ этого бота: обученная оценка вместо линейной по весам.
     *
     * <p>Почему судья привязан к геному, а не к процессу. Раньше обученная оценка
     * включалась на весь запуск сразу, и сравнить «с ней и без неё» можно было
     * только двумя отдельными прогонами — то есть на разных партиях и с разной
     * погрешностью. Толком сравнить не получалось. Теперь судья — часть мозга
     * конкретного бота, и оба варианта сажаются ЗА ОДИН СТОЛ.
     *
     * <p>В файл геном не пишется: обученная оценка живёт отдельным файлом.
     */
    public final ValueNet judge;

    public Genome(Map<String, Double> weights) {
        this(weights, null);
    }

    public Genome(Map<String, Double> weights, ValueNet judge) {
        this.weights = new TreeMap<>(weights);
        this.judge = judge;
    }

    /** Тот же геном, но с другим судьёй позиции ({@code null} — линейная оценка). */
    public Genome withJudge(ValueNet net) {
        return new Genome(weights, net);
    }

    /**
     * Тот же геном, но с ОЦЕНКОЙ ПОЗИЦИИ, возвращённой к разумным значениям.
     *
     * <p>Зачем. Веса {@code eval.*} спрашивает только просчёт вперёд, а обучение
     * долго играло ботами без просчёта — значит отбор их мутировал, ничего в них не
     * отбирая: случайное блуждание. Такой геном, посаженный за стол просчитывающим
     * ботом, судит позицию случайными весами. Пока обучение не гоняется с
     * просчётом (это в двадцать раз дороже), честнее взять исходные значения, чем
     * итог блуждания.
     */
    public Genome withDefaultEval() {
        Map<String, Double> w = new LinkedHashMap<>(weights);
        Map<String, Double> base = defaults().weights;
        for (int i = 0; i < StateFeatures.DIM; i++) {
            String key = StateFeatures.weightKey(i);
            w.put(key, base.get(key));
        }
        return new Genome(w, judge);
    }

    /** Тот же геном с одной изменённой ручкой — для опытов и стендов. */
    public Genome with(String key, double value) {
        Map<String, Double> w = new LinkedHashMap<>(weights);
        w.put(key, value);
        return new Genome(w, judge);
    }

    /** Значение веса (или {@code fallback}, если ключа нет). */
    public double get(String key, double fallback) {
        Double v = weights.get(key);
        return v != null ? v : fallback;
    }

    /**
     * Стартовый геном («сбалансированный»). Значения совпадают по духу с
     * {@link HeuristicAgent#DEFAULT_WEIGHTS} + добавлены веса оценочной функции.
     */
    public static Genome defaults() {
        Map<String, Double> w = new LinkedHashMap<>();
        // --- приоритеты действий (reveal/action) ---
        w.put("action.build", 7.0);
        w.put("action.assembly", 6.5);
        w.put("action.mining", 6.0);
        w.put("action.energy_swap", 6.0);
        w.put("action.movement", 5.0);
        w.put("action.combat", 5.5);
        w.put("action.science", 4.0);
        w.put("action.market", 2.0);
        // --- поведенческие множители ---
        w.put("aggression", 1.0);
        w.put("military_build", 1.0);
        // --- движение/бой (осознанное прицеливание) ---
        w.put("move.toward_killable", 12.0);   // идти к цели, которую МОГУ убить
        w.put("move.strike_range", 8.0);        // встать в дистанцию удара (d==1)
        w.put("move.toward_enemy", 3.0);        // просто к врагу (fallback)
        w.put("combat.kill_value", 6.0);        // ценность реального уничтожения
        w.put("combat.building_bonus", 2.0);    // добить здание/вышку (→трофей крупнее)
        w.put("combat.cu_bonus", 8.0);          // бить чужое ЦУ (путь к военной победе)
        w.put("combat.raze_neutral", 3.0);      // снос нейтрала (трофеи + место в мид-гейме)
        w.put("build.strike_building", 5.0);    // строить завод/авиабазу (техника/авиация бьют ЦУ)
        w.put("assemble.strike_unit", 4.0);     // производить технику/авиацию (единственные, кто бьёт ЦУ)
        w.put("combat.hit_leader", 5.0);        // бонус за удар по токенам лидера
        // --- ЦЕННОСТЬ ПРОМЕЖУТОЧНЫХ ЦЕЛЕЙ (см. Plan) ---
        // Раньше эти числа были константами внутри Plan, и отбор НЕ МОГ до них
        // дотянуться: «что важнее в раунде 3 — наука или армия» решал автор кода
        // раз и навсегда. Теперь это гены, и линии характеров расходятся именно
        // здесь — там, где разницу видно зрителю.
        w.put("plan.value.kelium", 9.0);
        w.put("plan.value.sell", 6.0);
        w.put("plan.value.tech", 6.0);
        w.put("plan.value.army", 7.0);
        w.put("plan.value.economy", 4.0);
        w.put("plan.value.objective", 8.0);
        w.put("plan.chain_penalty", 0.5);       // штраф за каждый недостающий шаг
        w.put("plan.focus", 1.0);               // вес плановых надбавок к оценкам
        // --- ПРОСЧЁТ ВПЕРЁД ---
        // Насколько доверять просчёту против сиюминутной эвристики и какой
        // прирост оценки считать достойным того, чтобы вообще делать ход.
        w.put("search.trust", 1.0);
        w.put("search.pass_threshold", 0.4);
        w.put("search.hollow_penalty", 3.0);
        // Чем судить позицию, до которой просчёт доиграл и оборвался: очками на
        // столе или обещанием положения. Ноль — только очки (жадно и близоруко,
        // именно из-за этого просчёт вперёд долго не давал прибавки).
        w.put("search.horizon_pos", 0.5);
        // Играть ли за себя внутри доигрывания внимательно (отсев холостых ходов)
        // или жадной формулой. Больше 0.5 — внимательно; цена растёт кратно.
        w.put("search.rollout_smart", 0.0);
        // Насколько оценка позиции на ОДИН шаг вперёд поправляет выбор действия.
        // ЗАМЕРЕНО: ноль лучше. Вес 0.20 проигрывает нулю 44% на 192 очных
        // сравнениях, вес 0.60 — 40%. Причина та же, по которой оценку нельзя
        // делать главным судьёй: на один шаг вперёд любое вложение (Стройка,
        // Маркет) выглядит убытком. Веса оценки позиции работают не здесь, а в
        // доигрывании приказов (Lookahead.horizonScore) — там они и отбираются.
        w.put("search.value_weight", 0.0);
        // Учитывать ли в просчёте ПРИВЫЧКИ соперников по приказам (открытая
        // информация: что кто вскрывал). ЗАМЕРЕНО: силы не добавляет — 47% против
        // 53% на 224 очных сравнениях, то есть в пределах погрешности, скорее
        // немного хуже. Оставлено выключенным и с кодом на месте: догадка «бот
        // должен предсказывать приказ соседа» выглядит очевидной и будет приходить
        // снова, а тут уже видно, что 66% заблокированных ходов этим не лечатся.
        w.put("search.opponent_habits", 0.0);
        // --- ОЦЕНОЧНАЯ ФУНКЦИЯ ПОЗИЦИИ eval.* (признаки: StateFeatures) ---
        // По одному весу на признак. Отрицательные веса РАЗРЕШЕНЫ и осмысленны:
        // «незапитанные ячейки» и «мои жетоны под ударом» — это плохо, и бот
        // обязан уметь это выучить.
        w.put("eval.vp", 6.0);
        w.put("eval.margin", 3.0);
        w.put("eval.coin", 0.25);
        w.put("eval.kelium", 0.9);
        w.put("eval.ammo", 0.35);
        w.put("eval.trophy_pool", 0.7);
        w.put("eval.miners_working", 2.0);
        w.put("eval.kelium_reachable", 0.35);
        w.put("eval.storage_room", 0.15);
        w.put("eval.power_plants", 0.6);
        w.put("eval.energy_idle", 0.4);
        w.put("eval.energy_hungry", -0.5);
        w.put("eval.military_powered", 1.2);
        w.put("eval.strike_buildings", 1.5);
        w.put("eval.units", 0.6);
        w.put("eval.strike_units", 0.9);
        w.put("eval.tech_steps", 1.8);
        w.put("eval.tech_peaks", 2.0);
        w.put("eval.objectives_hand", 0.5);
        w.put("eval.super_progress", 0.8);
        w.put("eval.arsenal_installed", 0.5);
        w.put("eval.containers", 0.6);
        w.put("eval.cu_tokens", 5.0);
        w.put("eval.enemy_cu_damage", 1.2);
        w.put("eval.killable_in_range", 0.8);
        w.put("eval.my_exposed", -0.4);
        w.put("eval.tiles_flipped", 1.0);
        w.put("eval.tempo_economy", 0.5);
        w.put("eval.buildings", 0.3);
        return new Genome(w);
    }

    /** Какие веса эволюционируют (мутируются/скрещиваются). */
    public static final List<String> TUNABLE_KEYS = tunableKeys();

    private static List<String> tunableKeys() {
        List<String> keys = new ArrayList<>(List.of(
            "action.build", "action.assembly", "action.mining", "action.energy_swap",
            "action.movement", "action.combat", "action.science", "action.market",
            "aggression", "military_build",
            "move.toward_killable", "move.strike_range", "move.toward_enemy",
            "combat.kill_value", "combat.building_bonus", "combat.cu_bonus",
            "combat.raze_neutral", "combat.hit_leader",
            "build.strike_building", "assemble.strike_unit",
            "plan.value.kelium", "plan.value.sell", "plan.value.tech",
            "plan.value.army", "plan.value.economy", "plan.value.objective",
            "plan.chain_penalty", "plan.focus",
            "search.trust", "search.pass_threshold", "search.hollow_penalty",
            "search.horizon_pos", "search.value_weight"));
        // По одному гену на каждый признак позиции — оценочная функция целиком
        // отдана отбору.
        for (int i = 0; i < StateFeatures.DIM; i++) {
            keys.add(StateFeatures.weightKey(i));
        }
        return List.copyOf(keys);
    }

    /**
     * Веса, которым ЗАПРЕЩЕНО уходить в минус: приоритет действия или множитель
     * агрессии со знаком минус бессмыслен и ломает выбор. Оценочные веса
     * ({@code eval.*}) сюда НЕ входят — там минус несёт смысл «это плохо».
     */
    private static boolean mustBeNonNegative(String key) {
        return key.startsWith("action.") || key.startsWith("move.")
            || key.startsWith("combat.") || key.startsWith("build.")
            || key.startsWith("assemble.") || key.startsWith("plan.")
            || key.startsWith("search.")
            || "aggression".equals(key) || "military_build".equals(key);
    }

    /**
     * Наложить ХАРАКТЕР на обученный геном — домножить группы весов, чтобы за
     * столом сидели разные боты (непредсказуемость: соперники не знают, чего
     * ждать). Профили:
     *   hawk — ястреб: агрессия/бой/удар по лидеру/ударные здания вверх;
     *   dove — эконом: агрессия вниз, наука/экономика вверх;
     *   opportunist — оппортунист: удар по лидеру и захват (building_bonus) вверх,
     *                 общая агрессия средняя.
     * Неизвестный профиль возвращает геном без изменений.
     */
    public Genome withProfile(String profile) {
        if (profile == null || profile.isEmpty() || "balanced".equals(profile)) {
            return this;
        }
        Map<String, Double> w = new LinkedHashMap<>(weights);
        java.util.function.BiConsumer<String, Double> mul =
            (k, f) -> w.put(k, w.getOrDefault(k, 1.0) * f);
        switch (profile) {
            case "warlord" -> {
                // ВОИТЕЛЬ: старт ещё агрессивнее ястреба, и вдобавок тянет
                // производить ВСЕ рода войск, а не только дешёвую пехоту.
                mul.accept("aggression", 2.2);
                mul.accept("action.combat", 1.9);
                mul.accept("action.assembly", 1.6);
                mul.accept("action.movement", 1.5);
                mul.accept("combat.kill_value", 1.6);
                mul.accept("combat.cu_bonus", 1.4);
                mul.accept("assemble.strike_unit", 1.6);
                mul.accept("build.strike_building", 1.5);
                mul.accept("plan.value.army", 1.8);
                mul.accept("action.science", 0.7);
                mul.accept("action.market", 0.8);
            }
            case "hawk" -> {
                mul.accept("aggression", 1.8);
                mul.accept("action.combat", 1.6);
                mul.accept("action.movement", 1.4);
                mul.accept("combat.kill_value", 1.4);
                mul.accept("combat.hit_leader", 1.6);
                mul.accept("combat.cu_bonus", 1.5);
                mul.accept("build.strike_building", 1.6);
                mul.accept("assemble.strike_unit", 1.5);
                mul.accept("eval.rival_leader", 1.5);
            }
            case "dove" -> {
                mul.accept("aggression", 0.4);
                mul.accept("action.combat", 0.5);
                mul.accept("action.science", 1.5);
                mul.accept("action.mining", 1.4);
                mul.accept("eval.tech_steps", 1.5);
                mul.accept("eval.miners_working", 1.5);
                mul.accept("eval.kelium", 1.4);
                mul.accept("plan.value.kelium", 1.4);
                mul.accept("plan.value.tech", 1.4);
            }
            case "opportunist" -> {
                mul.accept("combat.hit_leader", 2.0);
                mul.accept("combat.building_bonus", 1.8);   // добивать ослабленное = захват
                mul.accept("eval.margin", 1.8);
                mul.accept("aggression", 1.1);
            }
            // ИССЛЕДОВАТЕЛЬ и ХАОС с 12.08.2026 — такие же линии эволюции, а не
            // прошитые правила. Перекос задаёт только СТАРТ популяции: дальше
            // веса ищет отбор, а целевая функция линии (см. EvoTrainer) награждает
            // Исследователя за широту охвата, Хаос — за напор и размен.
            case "explorer" -> {
                mul.accept("action.market", 1.8);
                mul.accept("action.science", 1.4);
                mul.accept("action.energy_swap", 1.4);
                mul.accept("action.movement", 1.3);
                mul.accept("eval.containers", 1.5);
                mul.accept("eval.objectives_hand", 1.5);
                mul.accept("eval.trophy_pool", 1.3);
                mul.accept("plan.value.objective", 1.5);
            }
            case "chaos" -> {
                mul.accept("action.movement", 1.9);
                mul.accept("action.assembly", 1.5);
                mul.accept("action.combat", 1.5);
                mul.accept("aggression", 1.4);
                mul.accept("action.market", 0.5);
                mul.accept("action.science", 0.6);
                mul.accept("eval.margin", 1.6);
            }
            default -> { }
        }
        return new Genome(w, judge);
    }

    /**
     * Мутация: каждый обучаемый вес получает гауссов шум масштаба
     * {@code rate * |вес|} (минимум {@code rate}). Значения не опускаются ниже 0
     * (отрицательные приоритеты бессмысленны и ломают выбор).
     */
    public Genome mutate(Random rng, double rate) {
        Map<String, Double> w = new LinkedHashMap<>(weights);
        for (String key : TUNABLE_KEYS) {
            double cur = w.getOrDefault(key, 1.0);
            double scale = Math.max(rate, rate * Math.abs(cur));
            double next = cur + rng.nextGaussian() * scale;
            w.put(key, mustBeNonNegative(key) ? Math.max(0.0, next) : next);
        }
        return new Genome(w, judge);
    }

    /**
     * Скрещивание двух геномов: по каждому обучаемому весу с равной вероятностью
     * берём значение одного из родителей ИЛИ их среднее (равномерный кроссовер с
     * усреднением). Прочие (необучаемые) ключи берём от первого родителя.
     */
    public static Genome crossover(Genome a, Genome b, Random rng) {
        Map<String, Double> w = new LinkedHashMap<>(a.weights);
        for (String key : TUNABLE_KEYS) {
            double va = a.getOrDefaultLocal(key);
            double vb = b.getOrDefaultLocal(key);
            int roll = rng.nextInt(3);
            w.put(key, switch (roll) {
                case 0 -> va;
                case 1 -> vb;
                default -> (va + vb) / 2.0;
            });
        }
        // Судью позиции наследуем от первого родителя — как и все необучаемые ключи.
        return new Genome(w, a.judge);
    }

    private double getOrDefaultLocal(String key) {
        return weights.getOrDefault(key, 1.0);
    }

    // ================= сериализация (плоский JSON) =======================

    /** Записать геном в файл как плоский JSON {@code {"ключ": число, ...}}. */
    public void saveJson(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, toJson(), StandardCharsets.UTF_8);
    }

    /** Сериализовать в JSON-строку (ключи отсортированы, читается человеком). */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{\n");
        List<String> keys = new ArrayList<>(weights.keySet());
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            sb.append("  \"").append(k).append("\": ").append(fmt(weights.get(k)));
            sb.append(i + 1 < keys.size() ? ",\n" : "\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static String fmt(double v) {
        // компактно, с точкой (Locale-независимо)
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return String.format(java.util.Locale.ROOT, "%.1f", v);
        }
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }

    /** Прочитать геном из файла. Недостающие ключи добираются из {@link #defaults()}. */
    public static Genome loadJson(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Map<String, Double> parsed = parseFlatJson(text);
        Map<String, Double> w = new LinkedHashMap<>(defaults().weights);
        w.putAll(parsed);
        return new Genome(w);
    }

    /**
     * Минимальный парсер плоского JSON-объекта из чисел: {@code {"k": 1.0, ...}}.
     * Только строковые ключи и числовые значения (формат нашего {@link #toJson()}).
     */
    static Map<String, Double> parseFlatJson(String text) {
        Map<String, Double> out = new LinkedHashMap<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            // найти открывающую кавычку ключа
            while (i < n && text.charAt(i) != '"') {
                i++;
            }
            if (i >= n) {
                break;
            }
            int keyStart = ++i;
            while (i < n && text.charAt(i) != '"') {
                i++;
            }
            if (i >= n) {
                break;
            }
            String key = text.substring(keyStart, i);
            i++; // закрывающая кавычка
            // найти двоеточие
            while (i < n && text.charAt(i) != ':') {
                i++;
            }
            i++; // ':'
            // пропустить пробелы
            while (i < n && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            int numStart = i;
            while (i < n && "+-.eE0123456789".indexOf(text.charAt(i)) >= 0) {
                i++;
            }
            if (i > numStart) {
                try {
                    out.put(key, Double.parseDouble(text.substring(numStart, i)));
                } catch (NumberFormatException ignore) {
                    // пропускаем нечисловое значение
                }
            }
        }
        return out;
    }
}
