package kelium.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.TurnJournal;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.Ctx;

/**
 * ИНДИКАТОРЫ ЗАДАНИЙ — что движок сообщает боту про карты у него в руке.
 *
 * <p>ЗАКАЗ ДИЗАЙНЕРА 17.08.2026. У бота остаётся СПЕЦ-действие, и он должен
 * решить, на что его потратить. Раньше он видел только награду и не видел пути к
 * ней, поэтому жёг карты вместо выполнения (замер каталога 1.6.0: из 6.5
 * полученных карт выполняется 1.18, сжигается 8.25). Заказаны два индикатора,
 * и оба считает ДВИЖОК, а не бот:
 *
 * <ol>
 *   <li><b>ГОТОВО</b> ({@link Hint#ready}) — условие карты выполнено прямо
 *       сейчас, СПЕЦ-действие закроет её немедленно. Отдельно горит
 *       {@link Hint#enhancedReady}: выполнено и усиленное требование, значит
 *       ждать смысла нет — награда уже максимальная.</li>
 *   <li><b>ДОСТИЖИМО + ИНСТРУКЦИЯ</b> ({@link Hint#reachable},
 *       {@link Hint#plans}) — карта ещё не готова, но В ЭТОТ ХОД её можно
 *       закрыть, и движок называет, какими действиями и в каком порядке. Планов
 *       может быть несколько; бот выбирает тот, что дешевле ему в этот ход,
 *       поэтому возвращаются ВСЕ найденные, а не первый попавшийся.</li>
 * </ol>
 *
 * <p>ПОЧЕМУ ИНСТРУКЦИЯ, А НЕ ЧИСЛО. «Задание выполнимо на 0.7» боту не помогает:
 * из этого не следует ни одного хода. Инструкция — это список шагов вида
 * «действие + что именно им сделать», то есть ровно то, что бот может исполнить.
 *
 * <p>ПОРЯДОК ШАГОВ ЗНАЧИМ у требований-происшествий: событие обязано случиться
 * ДО розыгрыша задания (СВОД §9.2). У требований-состояний порядок безразличен,
 * и индикатор ДОСТИЖИМО может загореться в любой момент хода, пока СПЕЦ-действие
 * не потрачено, — это прямо оговорено в заказе.
 *
 * <p>ЧЕГО ЗДЕСЬ НЕТ. Планировщик не перебирает партию вперёд и не обещает, что
 * шаг удастся: он отвечает на вопрос «есть ли в этом ходу законный набор
 * действий, который закрывает карту», опираясь на разбор самого требования.
 * Требования, для которых плана не выводится (чужой ответный бой, случайный
 * добор), честно возвращают {@code reachable = false} и пустой список планов —
 * вместо выдумывания несуществующего пути.
 */
public final class ObjectiveHints {

    private ObjectiveHints() {
    }

    /** Один шаг инструкции: каким действием и что именно сделать. */
    public record Step(String action, String instruction) {
    }

    /**
     * ПЛАН — упорядоченный набор шагов, закрывающий карту в этом ходу.
     *
     * @param steps    шаги по порядку; для происшествий порядок обязателен
     * @param enhanced закрывает ли план ещё и усиленное требование
     * @param summary  та же инструкция одной строкой, для журнала и подсказки
     */
    public record Plan(List<Step> steps, boolean enhanced, String summary) {
    }

    /**
     * ИНДИКАТОРЫ ОДНОЙ КАРТЫ.
     *
     * @param cardId         id карты в руке
     * @param ready          базовое требование выполнено ПРЯМО СЕЙЧАС
     * @param enhancedReady  выполнено и усиленное требование
     * @param reachable      не готово, но закрывается в этот ход (есть план)
     * @param plans          все найденные планы, сильные первыми
     * @param value          цена награды, если разыграть карту сейчас
     * @param maxValue       цена награды с усилением (потолок этой карты)
     * @param needed         чего не хватает — человеческой строкой
     */
    public record Hint(String cardId, boolean ready, boolean enhancedReady,
                       boolean reachable, List<Plan> plans,
                       double value, double maxValue, String needed) {
    }

    // ======================================================================
    //  ЦЕНА НАГРАДЫ
    // ======================================================================
    //  Калибровка дизайнера (шапка каталога контейнеров): монета 1, боеприпас 1,
    //  обломок 1.5, карта задания 2, контейнер 1.5. Дописаны вещи, которых в той
    //  калибровке нет, по их месту в игре: келемий равен победному очку (5
    //  монет), модуль удваивает базу сборки или атаки и потому дороже всего.

    private static final Map<String, Double> PRICE = new HashMap<>();

    static {
        PRICE.put("coin", 1.0);
        PRICE.put("ammo", 1.0);
        PRICE.put("debris", 1.5);
        PRICE.put("container", 1.5);
        PRICE.put("objective_card", 2.0);
        PRICE.put("objective_cards", 2.0);
        PRICE.put("kelium", 5.0);
        PRICE.put("arsenal", 3.0);
        PRICE.put("storage_token", 4.0);
        PRICE.put("module", 6.0);
    }

    /** Цена одной записи награды из данных карты. */
    public static double rewardValue(Object node) {
        if (!(node instanceof Map<?, ?> r)) {
            return 0.0;
        }
        double sum = 0.0;
        for (Map.Entry<?, ?> e : r.entrySet()) {
            Double price = PRICE.get(String.valueOf(e.getKey()));
            if (price == null) {
                continue;
            }
            // «module: attack» — не число: жетон один, но дорогой.
            int n = e.getValue() instanceof Number num ? num.intValue() : 1;
            sum += price * n;
        }
        return sum;
    }

    // ======================================================================
    //  ГЛАВНЫЙ ВХОД
    // ======================================================================

    /**
     * Индикаторы по ВСЕЙ руке заданий. {@code availableActions} — действия,
     * которые игрок ещё может сыграть в этом ходу (по вскрытому приказу минус
     * уже сыгранные); {@code actionsLeft} — сколько их осталось. Оба параметра
     * ограничивают планировщик: план из двух действий бесполезен, если действие
     * осталось одно.
     */
    public static List<Hint> forHand(GameState s, int seat, TurnJournal j,
                                     java.util.Collection<String> availableActions,
                                     int actionsLeft) {
        List<Hint> out = new ArrayList<>();
        for (String cid : List.copyOf(s.player(seat).objectiveHand)) {
            Hint h = forCard(s, seat, j, cid, availableActions, actionsLeft);
            if (h != null) {
                out.add(h);
            }
        }
        // Сильные первыми: сперва то, что можно закрыть прямо сейчас, внутри —
        // по цене награды. Бот читает список сверху и берёт первое, что ему по
        // карману действиями.
        out.sort((x, y) -> {
            int c = Boolean.compare(y.ready(), x.ready());
            if (c != 0) {
                return c;
            }
            c = Boolean.compare(y.reachable(), x.reachable());
            if (c != 0) {
                return c;
            }
            return Double.compare(y.value(), x.value());
        });
        return out;
    }

    /** Индикаторы одной карты руки. */
    @SuppressWarnings("unchecked")
    public static Hint forCard(GameState s, int seat, TurnJournal j, String cid,
                               java.util.Collection<String> availableActions, int actionsLeft) {
        Map<String, Object> card;
        try {
            card = Ctx.cards(s, "objectives").byId(cid);
        } catch (RuntimeException e) {
            return null;
        }
        boolean ready = Objectives.playableObjectives(s, seat, j).contains(cid);
        boolean enhancedReady = ready && enhancedMet(s, seat, j, cid, card);

        double base = rewardValue(card.get("base_reward"));
        double special = rewardValue(card.get("special_reward"));
        double value = ready ? base + (enhancedReady ? special : 0.0) : 0.0;

        List<Plan> plans = new ArrayList<>();
        if (!ready) {
            plans.addAll(planFor(s, seat, j, card, availableActions, actionsLeft));
        } else if (!enhancedReady) {
            // Готово по базовому — стоит ли ждать? Только если усиление
            // закрывается тем же ходом; иначе бот играет карту сейчас.
            plans.addAll(planFor(s, seat, j, card, availableActions, actionsLeft));
            plans.removeIf(pl -> !pl.enhanced());
        }
        return new Hint(cid, ready, enhancedReady, !ready && !plans.isEmpty(),
            List.copyOf(plans), value, base + special, needed(s, seat, j, cid, card));
    }

    @SuppressWarnings("unchecked")
    private static boolean enhancedMet(GameState s, int seat, TurnJournal j, String cid,
                                       Map<String, Object> card) {
        Object enh = card.get("enhanced");
        if (enh == null) {
            // У начальных заданий усиления нет вовсе — потолок равен базовому.
            return true;
        }
        if (!(enh instanceof Map<?, ?> m)) {
            return false;
        }
        if ("card".equals(card.get("checked_by"))) {
            kelium.engine.cards.ObjectiveCard oc = kelium.engine.cards.CardRegistry.objective(cid);
            return oc != null
                && oc.satisfiedEnhanced(new kelium.engine.cards.EngineCardContext(s, seat));
        }
        Object pid = m.get("predicate");
        if (pid == null || !Predicates.isRegistered(pid.toString())) {
            return false;
        }
        Map<String, Object> params = m.get("params") instanceof Map<?, ?> pm
            ? (Map<String, Object>) pm : Map.of();
        try {
            return Predicates.check(pid.toString(), s, seat, j, params);
        } catch (Predicates.PredicateError e) {
            return false;
        }
    }

    /** Человеческая строка «чего не хватает». */
    private static String needed(GameState s, int seat, TurnJournal j, String cid,
                                 Map<String, Object> card) {
        if ("card".equals(card.get("checked_by"))) {
            kelium.engine.cards.ObjectiveCard oc = kelium.engine.cards.CardRegistry.objective(cid);
            if (oc != null) {
                return oc.needed(new kelium.engine.cards.EngineCardContext(s, seat));
            }
        }
        Gap gap = gap(s, seat, j, card);
        return gap == null ? "" : gap.text;
    }

    // ======================================================================
    //  ПЛАНИРОВЩИК
    // ======================================================================
    //  Устройство простое и намеренно не «умное»: движок разбирает ТРЕБОВАНИЕ
    //  карты (id предиката + параметры), считает РАЗРЫВ между тем, что уже есть,
    //  и тем, что нужно, и называет действия, которыми этот разрыв закрывается.
    //  Действие берётся не с потолка: у каждого требования есть ровно одно-два
    //  действия, которые на него влияют, и это записано в таблице ниже.

    /** Разрыв до выполнения: сколько не хватает и чем это закрывается. */
    private record Gap(int missing, List<String> actions, String text) {
    }

    /**
     * ПЛАН ДЛЯ КАРТЫ, ЧЬЁ УСЛОВИЕ В КОДЕ. Карта называет действие сама
     * ({@code suggestedAction}) и сама говорит, чего не хватает
     * ({@code needed}) — планировщику остаётся проверить, что это действие
     * игроку в этом ходу вообще доступно.
     *
     * <p>План всегда в ОДИН шаг: карта не сообщает, сколько единиц осталось
     * добрать, а выдумывать это число значило бы врать боту. Один шаг — это
     * честное «сыграй вот это действие в сторону вот этого требования»; если
     * одного мало, индикатор загорится снова на следующем действии.
     *
     * <p>Усиленный план отдельным пунктом не выводится: код-карта отвечает про
     * усиление только «да/нет» ({@code satisfiedEnhanced}), а действие у базы и
     * усиления одно и то же — второй такой же план был бы дубликатом.
     */
    private static List<Plan> planFromCodeCard(GameState s, int seat,
                                               Map<String, Object> card,
                                               java.util.Collection<String> available,
                                               int actionsLeft) {
        Object idObj = card.get("id");
        if (idObj == null || actionsLeft < 1) {
            return List.of();
        }
        kelium.engine.cards.ObjectiveCard oc =
            kelium.engine.cards.CardRegistry.objective(String.valueOf(idObj));
        if (oc == null) {
            return List.of();
        }
        kelium.engine.cards.CardContext ctx =
            new kelium.engine.cards.EngineCardContext(s, seat);
        String action;
        String what;
        try {
            action = oc.suggestedAction(ctx);
            what = oc.needed(ctx);
        } catch (RuntimeException e) {
            // Условие карты считает произвольный код; сломанная карта не должна
            // ронять подсказки по ВСЕЙ руке — она просто остаётся без плана.
            return List.of();
        }
        if (action == null || action.isBlank()) {
            return List.of();
        }
        if (available != null && !available.contains(action)) {
            return List.of();
        }
        String text = what == null || what.isBlank() ? "приблизиться к условию карты" : what;
        return List.of(new Plan(List.of(new Step(action, text)), false,
            "сыграй " + action + ": " + text));
    }

    @SuppressWarnings("unchecked")
    private static List<Plan> planFor(GameState s, int seat, TurnJournal j,
                                      Map<String, Object> card,
                                      java.util.Collection<String> available, int actionsLeft) {
        List<Plan> out = new ArrayList<>();
        // КАРТЫ, ЧЬЁ УСЛОВИЕ ЖИВЁТ В КОДЕ, — отдельной ветвью и ПЕРВЫМИ.
        //
        // НАЙДЕНО ЗАМЕРОМ 19.08.2026 (CardCoverage, 300 партий): планировщик был
        // мёртв для ВСЕГО каталога. Он разбирает requirement.predicate, а
        // переехавшая в код карта этого поля не пишет вовсе — в её requirement
        // остался только человеческий текст. Значит gap() возвращал null для
        // всех 52 заданий, reachable не загорался НИ РАЗУ, и индикатор
        // «ДОСТИЖИМО + инструкция», заказанный ровно чтобы боты перестали жечь
        // карты, не работал с самого переезда карт в код. Отсюда и замер: на
        // руки приходит 250 карт, выполняется 5–90, остальное в костёр.
        //
        // Карта в коде уже умеет отвечать на оба нужных вопроса сама:
        // suggestedAction() — каким действием к ней приближаться, needed() —
        // чего не хватает. Этого достаточно для плана в один шаг; считать
        // «сколько именно единиц не хватает» здесь нечем и не нужно — цена
        // ошибки в числе шагов ниже, чем цена отсутствия плана вообще.
        if ("card".equals(card.get("checked_by"))) {
            out.addAll(planFromCodeCard(s, seat, card, available, actionsLeft));
            return out;
        }
        Gap gap = gap(s, seat, j, card);
        if (gap == null || gap.missing <= 0 || gap.actions.isEmpty()) {
            return out;
        }
        // Сколько ДЕЙСТВИЙ требует закрытие разрыва. Одно действие закрывает
        // столько единиц, сколько объектов в нём участвует: Сборка производит
        // всеми зданиями сразу, Стройка делает несколько операций, Бой бьёт
        // одним гексом. Поэтому по умолчанию одно действие = один шаг разрыва,
        // а «пакетные» действия отмечены отдельно.
        for (String action : gap.actions) {
            if (available != null && !available.contains(action)) {
                continue;
            }
            int need = batched(action) ? 1 : gap.missing;
            if (need > actionsLeft) {
                continue;
            }
            List<Step> steps = new ArrayList<>();
            for (int i = 0; i < need; i++) {
                steps.add(new Step(action, gap.text));
            }
            String summary = "сыграй " + action
                + (need > 1 ? " ×" + need : "") + ": " + gap.text;
            out.add(new Plan(List.copyOf(steps), false, summary));
        }
        // Усиленная ветка: тот же разбор, но по блоку enhanced. Если её разрыв
        // тоже закрывается оставшимися действиями — это отдельный, более дорогой
        // план, и бот должен видеть оба.
        Object enh = card.get("enhanced");
        if (enh instanceof Map<?, ?>) {
            Map<String, Object> asEnhanced = new HashMap<>(card);
            asEnhanced.put("requirement", enh);
            Gap eg = gap(s, seat, j, asEnhanced);
            if (eg != null && eg.missing > 0 && !eg.actions.isEmpty()) {
                for (String action : eg.actions) {
                    if (available != null && !available.contains(action)) {
                        continue;
                    }
                    int need = batched(action) ? 1 : eg.missing;
                    if (need > actionsLeft) {
                        continue;
                    }
                    List<Step> steps = new ArrayList<>();
                    for (int i = 0; i < need; i++) {
                        steps.add(new Step(action, eg.text));
                    }
                    out.add(new Plan(List.copyOf(steps), true,
                        "усиленно: сыграй " + action + (need > 1 ? " ×" + need : "")
                            + ": " + eg.text));
                }
            }
        }
        // Сильные первыми: усиленные планы дороже, короткие — дешевле.
        out.sort((x, y) -> {
            int c = Boolean.compare(y.enhanced(), x.enhanced());
            return c != 0 ? c : Integer.compare(x.steps().size(), y.steps().size());
        });
        return out;
    }

    /**
     * ПАКЕТНЫЕ ДЕЙСТВИЯ закрывают несколько единиц разрыва за один розыгрыш:
     * Сборка производит всеми запитанными зданиями сразу, Добыча — всеми
     * добытчиками, Стройка делает несколько операций, Наука шагает по всем трём
     * трекам, Маркет заключает несколько сделок. Бой и Движение считаются
     * штучными: бой бьёт один соседний гекс, и второй жетон противника — это,
     * как правило, второй бой.
     */
    private static boolean batched(String action) {
        return switch (action) {
            case "assembly", "mining", "build", "science", "market", "energy_swap" -> true;
            default -> false;
        };
    }

    /**
     * РАЗБОР ТРЕБОВАНИЯ: сколько не хватает и какими действиями это закрывается.
     * Здесь собрано всё знание планировщика о каталоге; когда появляется новый
     * предикат, дописывается одна ветка, и бот сразу получает по нему инструкцию.
     */
    @SuppressWarnings("unchecked")
    private static Gap gap(GameState s, int seat, TurnJournal j, Map<String, Object> card) {
        Object reqObj = card.get("requirement");
        if (!(reqObj instanceof Map<?, ?> req)) {
            return null;
        }
        String pid = String.valueOf(req.get("predicate"));
        Map<String, Object> p = req.get("params") instanceof Map<?, ?> pm
            ? (Map<String, Object>) pm : Map.of();
        PlayerState me = s.player(seat);
        TurnJournal.TurnFacts f = j.of(seat);

        switch (pid) {
            // ---- НАЙМ И СНАРЯЖЕНИЕ ----
            case "hired_distinct_kinds" -> {
                int have = 0;
                Set<String> forbid = codes(p.get("forbid_kinds"));
                for (Map.Entry<String, Integer> e : f.producedByType.entrySet()) {
                    if (e.getValue() > 0 && !forbid.contains(e.getKey())) {
                        have++;
                    }
                }
                int need = num(p, "count", 2) - have;
                return new Gap(need, List.of("assembly"),
                    "в этот ход нанять ещё " + Math.max(need, 0) + " войско разных родов"
                        + (forbid.isEmpty() ? "" : " (нельзя: " + String.join(", ", forbid) + ")"));
            }
            case "assembly_all_chose_ammo" -> {
                int need = num(p, "count", 2) - f.assemblyAmmoBuildingTypes.size();
                return new Gap(need, List.of("assembly"),
                    "в этот ход произвести боеприпасы ещё " + Math.max(need, 0)
                        + " зданием и не нанимать войск");
            }
            case "produced_units_this_turn", "produced_units_ex_tower" -> {
                int need = num(p, "count", 1) - f.unitsProduced;
                return new Gap(need, List.of("assembly"),
                    "в этот ход нанять ещё " + Math.max(need, 0) + " войско");
            }

            // ---- ДОБЫЧА ----
            case "miner_took_container" -> {
                return new Gap(f.minerTookContainer ? 0 : 1, List.of("mining"),
                    "в этот ход забрать печатный контейнер добытчиком");
            }
            case "last_kelium_nonstart" -> {
                return new Gap(f.lastKeliumNonStart ? 0 : 1, List.of("mining"),
                    "в этот ход забрать последний келемий с нестартового тайла зарождения");
            }
            case "resource_at_least" -> {
                Resource r;
                try {
                    r = Resource.fromCode(String.valueOf(p.get("resource")));
                } catch (RuntimeException e) {
                    return null;
                }
                int need = num(p, "amount", 1) - me.resources.get(r);
                List<String> how = r == Resource.KELIUM ? List.of("mining")
                    : r == Resource.AMMO ? List.of("assembly", "market") : List.of("market");
                return new Gap(need, how,
                    "добрать ещё " + Math.max(need, 0) + " " + r.code);
            }

            // ---- СТРОЙКА ----
            case "built_bordering_enemy", "built_on_hex_with_enemy_units" -> {
                return new Gap(1, List.of("build"),
                    "в этот ход построить здание на гексе " + whereEnemy(pid));
            }
            case "cu_placed_near_enemy_cu" -> {
                return new Gap(1, List.of("build"),
                    "в этот ход перенести ЦУ ближе к гексу с ЦУ противника");
            }
            case "build_ops_on_nonadjacent_hexes" -> {
                int need = num(p, "count", 2)
                    - new LinkedHashSet<>(f.buildOpHexes).size();
                return new Gap(need, List.of("build"),
                    "в этот ход выполнить строительные операции ещё на "
                        + Math.max(need, 0) + " гексе, не соседнем с уже задетыми");
            }
            case "moved_buildings_this_turn" -> {
                int need = num(p, "count", 2) - f.movedAnyBuildingUids.size();
                return new Gap(need, List.of("build"),
                    "в этот ход перенести ещё " + Math.max(need, 0) + " своё здание"
                        + (Boolean.TRUE.equals(p.get("include_cu")) ? " (одно из них — ЦУ)" : ""));
            }
            case "buildings_on_field_count" -> {
                int need = num(p, "count", 2) - me.buildingsOnField().size();
                return new Gap(need, List.of("build"),
                    "построить ещё " + Math.max(need, 0) + " здание");
            }
            case "buildings_wall_chain", "buildings_ring_around_hex" -> {
                return new Gap(1, List.of("build"),
                    "поставить или перенести здание так, чтобы цепочка сомкнулась");
            }

            // ---- БОЙ ----
            case "destroyed_enemy_this_turn" -> {
                int need = num(p, "count", 1) - f.enemyTokensDestroyed;
                String extra = p.containsKey("min_hp")
                    ? " (хотя бы один прочностью " + num(p, "min_hp", 2) + "+)" : "";
                return new Gap(need, List.of("combat"),
                    "в этот ход уничтожить ещё " + Math.max(need, 0) + " жетон противника" + extra);
            }
            case "damaged_distinct_no_kills" -> {
                if (f.enemyTokensDestroyed > 0) {
                    return new Gap(0, List.of(), "уже кто-то уничтожен — карта в этот ход не закроется");
                }
                int need = num(p, "count", 2) - f.enemyTokensDamaged.size();
                return new Gap(need, List.of("combat"),
                    "в этот ход ранить ещё " + Math.max(need, 0)
                        + " жетон противника и никого не уничтожить");
            }
            case "damaged_distinct_enemy_buildings" -> {
                int need = num(p, "count", 2) - f.enemyBuildingsDamaged.size();
                return new Gap(need, List.of("combat"),
                    "в этот ход нанести урон ещё " + Math.max(need, 0) + " зданию противника");
            }
            case "kills_by_one_unit" -> {
                int best = 0;
                for (int v : f.killsByUnit.values()) {
                    best = Math.max(best, v);
                }
                int need = num(p, "count", 2) - best;
                return new Gap(need, List.of("combat"),
                    "в этот ход уничтожить ещё " + Math.max(need, 0)
                        + " жетон противника ТЕМ ЖЕ своим войском");
            }

            // ---- ДВИЖЕНИЕ И ПОЗИЦИЯ ----
            case "unit_on_hex_with_enemy_units", "aircraft_on_enemy_hex" -> {
                return new Gap(1, List.of("movement"),
                    "завести своё войско на гекс, где есть жетоны противника");
            }
            case "units_bordering_enemy_units_hex" -> {
                return new Gap(1, List.of("movement"),
                    "подвести войско на гекс, соседний с гексом войск противника");
            }
            case "units_off_own_hexes" -> {
                Set<String> own = new java.util.HashSet<>();
                for (BuildingToken b : me.buildingsOnField()) {
                    own.add(b.hexId);
                }
                int off = 0;
                int on = 0;
                for (UnitToken u : me.unitsOnField()) {
                    if (own.contains(u.hexId)) {
                        on++;
                    } else {
                        off++;
                    }
                }
                int need = Math.max(num(p, "count", 2) - off, 0) + on;
                return new Gap(need, List.of("movement"),
                    on > 0 ? "увести все войска с гексов своих зданий"
                        : "вывести ещё " + Math.max(num(p, "count", 2) - off, 0)
                            + " войско за пределы гексов своих зданий");
            }
            case "unit_at_distance_from_cu", "unit_off_cu_hex" -> {
                return new Gap(1, List.of("movement"),
                    "отвести войско дальше от гекса своего ЦУ");
            }
            case "picked_container_by_unit" -> {
                int need = num(p, "count", 1) - f.containersPickedByUnit;
                return new Gap(need, List.of("movement"),
                    "в этот ход накрыть своим войском ещё "
                        + Math.max(need, 0) + " печатный контейнер");
            }
            case "chain_connects" -> {
                return new Gap(1, List.of("movement", "build"),
                    "дотянуть непрерывное соседство своих жетонов до нужных гексов");
            }

            // ---- МАРКЕТ И НАУКА ----
            case "market_offers_used" -> {
                int need = num(p, "count", 3) - f.marketOffersUsed.size();
                return new Gap(need, List.of("market"),
                    "в этот ход оплатить ещё " + Math.max(need, 0)
                        + " разное предложение планшета маркета");
            }
            case "used_market_card_offer", "used_market_this_turn" -> {
                return new Gap(f.usedMarketCardOffer || f.usedMarket ? 0 : 1, List.of("market"),
                    "в этот ход воспользоваться предложением на планшете маркета");
            }
            case "science_offers_used" -> {
                int need = num(p, "count", 3) - f.scienceOffersUsed.size();
                return new Gap(need, List.of("science"),
                    "в этот ход взять ещё " + Math.max(need, 0)
                        + " разное предложение планшета технологий");
            }
            case "science_trophies_spent" -> {
                int need = num(p, "count", 2) - f.sciencePaidUnits;
                return new Gap(need, List.of("science"),
                    "в этот ход заплатить в Науку ещё " + Math.max(need, 0)
                        + " обломок");
            }
            case "tech_step_reached", "tracks_occupied" -> {
                return new Gap(1, List.of("science"),
                    "шагнуть по треку технологий");
            }

            // ---- ЭНЕРГИЯ ----
            case "no_unpowered_buildings", "idle_cube_on_each_source",
                 "powered_miners_count", "powered_miners_distinct_spawns",
                 "powered_building_off_cu_hex" -> {
                return new Gap(1, List.of("energy_swap"),
                    "перераспределить энергию Сменой энергии");
            }

            // ---- ЖЕРТВЫ ----
            case "sacrifice_paid" -> {
                // Жертва не требует действий вовсе: плата вносится в момент
                // розыгрыша. Если платить нечем, карта не в списке готовых, и
                // разрыв честно указывает, чего не хватает.
                Object sac = card.get("sacrifice");
                if (sac instanceof Map<?, ?> sm) {
                    return new Gap(1, List.of(),
                        "нечем заплатить: нужно " + sm.get("amount") + " «" + sm.get("resource") + "»");
                }
                return null;
            }

            // ---- ТРОФЕИ ----
            case "trophy_contains", "trophy_distinct_kinds" -> {
                return new Gap(1, List.of("combat"),
                    "уничтожить жетон противника, чтобы он лёг в трофеи");
            }

            case "has_unopened_container" -> {
                int need = num(p, "count", 1) - me.containers;
                return new Gap(need, List.of("market"),
                    "добрать ещё " + Math.max(need, 0) + " контейнер");
            }

            default -> {
                // Требование, для которого пути не выводится: чужой ответный бой,
                // случайный добор, совпадение приказов. Врать про достижимость
                // нельзя — бот пойдёт исполнять несуществующий план.
                return null;
            }
        }
    }

    private static String whereEnemy(String pid) {
        return "built_on_hex_with_enemy_units".equals(pid)
            ? ", где стоит войско противника"
            : ", соседнем с гексом жетонов противника";
    }

    private static int num(Map<String, Object> p, String key, int def) {
        return p.get(key) instanceof Number n ? n.intValue() : def;
    }

    private static Set<String> codes(Object listObj) {
        Set<String> out = new LinkedHashSet<>();
        if (listObj instanceof List<?> l) {
            for (Object o : l) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }
}
