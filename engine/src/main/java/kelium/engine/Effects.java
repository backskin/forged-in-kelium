package kelium.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.core.TurnJournal;
import kelium.core.Agent;
import kelium.core.Choice;

/**
 * Реестр немедленных эффектов карт (контейнеры/маркет/арсенал/задания).
 *
 * <p>Порт из forge/engine/effects.py. Эффект — это операция, изменяющая
 * состояние игрока/поля и возвращающая небольшую карту телеметрии. Эффекты,
 * требующие выбора агента (move_unit, place_damage), берут активного агента из
 * {@code state.agents} и предлагают объекты Choice, чтобы боты управляли ими
 * единообразно.
 */
public final class Effects {

    private Effects() {
    }

    /** Ошибка применения эффекта (например, неизвестный id). */
    public static final class EffectError extends RuntimeException {
        public EffectError(String msg) {
            super(msg);
        }
    }

    private static int asInt(Object o) {
        return ((Number) o).intValue();
    }

    private static Agent agentFor(GameState s, int seat) {
        if (s.agents == null || seat >= s.agents.size()) {
            return null;
        }
        return s.agents.get(seat);
    }

    /**
     * Применить эффект {@code eid} для места {@code seat} с параметрами
     * {@code params}. Возвращает карту фактически произошедшего (телеметрия).
     */
    public static Map<String, Object> apply(String eid, GameState s, int seat, Map<String, Object> params) {
        if (eid == null) {
            eid = "noop";
        }
        Map<String, Object> p = params != null ? params : Map.of();
        return switch (eid) {
            case "gain" -> gain(s, seat, p);
            case "heal_one" -> healOne(s, seat, p);
            case "heal_all_own" -> healAllOwn(s, seat, p);
            case "heal_hex" -> healHex(s, seat, p);
            case "free_action" -> freeAction(s, seat, p);
            case "move_unit" -> moveUnit(s, seat, p);
            case "deploy_units" -> deployUnits(s, seat, p);
            case "place_damage" -> placeDamage(s, seat, p);
            case "grab_containers" -> grabContainers(s, seat, p);
            case "grab_first_player" -> grabFirstPlayer(s, seat, p);
            case "power_building_free" -> powerBuildingFree(s, seat, p);
            case "permanent_energy" -> permanentEnergy(s, seat, p);
            case "cancel_attack" -> cancelAttack(s, seat, p);
            case "build_neutral" -> buildNeutral(s, seat, p);
            case "evacuate" -> evacuate(s, seat, p);
            // === ЭФФЕКТЫ РЕВЬЮ 17.08.2026 ===
            case "shield" -> shield(s, seat, p);
            case "landing" -> landing(s, seat, p);
            case "speed_boost" -> speedBoost(s, seat, p);
            case "energy_or_modules" -> energyOrModules(s, seat, p);
            case "convert" -> convert(s, seat, p);
            case "discard_enemy_arsenal" -> discardEnemyArsenal(s, seat, p);
            case "unlimited_spec" -> unlimitedSpec(s, seat, p);
            case "market_card_from_discard" -> marketCardFromDiscard(s, seat, p);
            case "swap_order_card" -> swapOrderCard(s, seat, p);
            case "noop" -> Map.of("noop", p.getOrDefault("note", "unimplemented"));
            // ПУСТОЙ КОНТЕЙНЕР (заказ дизайнера 18.08.2026, контейнеры 4.0) —
            // НАМЕРЕННО ничего не даёт, это не заглушка недоделки. Отдельно от
            // "noop": тот считается НЕреализованным и карты с ним отсеиваются
            // из колод на сетапе (см. isImplemented ниже и Setup) — "пустой
            // контейнер" реализован полностью, просто эффект действительно пуст.
            case "empty" -> Map.of();
            // E1: неизвестный эффект — ГРОМКАЯ ошибка, не тихий noop; карты с
            // такими эффектами отсеиваются из колод на сетапе (см. Setup).
            default -> throw new EffectError("нереализованный эффект: " + eid);
        };
    }

    /** E1/E2: реализован ли эффект с данным id (для отсева карт на сетапе). */
    public static boolean isImplemented(String eid) {
        if (eid == null) {
            return false;
        }
        return switch (eid) {
            case "gain", "heal_one", "heal_all_own", "heal_hex", "free_action",
                 "move_unit", "deploy_units", "place_damage", "grab_containers",
                 // Пять эффектов, дописанных 15.08.2026. До этого они стояли
                 // заглушкой noop, и ШЕСТЬ КАРТ (четыре контейнера и две карты
                 // рынка) молча изымались из колод на подготовке: игрок их не
                 // видел, а в отчётах они выглядели просто редкими.
                 "grab_first_player", "power_building_free", "permanent_energy",
                 "cancel_attack", "build_neutral", "evacuate",
                 // Девять эффектов ревью 17.08.2026: щит, десант, скорость,
                 // выбор «энергия или модули», конверсия и четыре арсенальных.
                 "shield", "landing", "speed_boost", "energy_or_modules", "convert",
                 "discard_enemy_arsenal", "unlimited_spec",
                 "market_card_from_discard", "swap_order_card",
                 "empty" -> true;   // пустой контейнер (18.08.2026) — реализован, не заглушка
            default -> false;   // включая "noop" — карта-заглушка не должна попасть в колоду
        };
    }

    // -- получение ресурсов ---------------------------------------------------
    static Map<String, Object> gain(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        Map<String, Object> got = new HashMap<>();
        if (p.containsKey("coin")) {
            int n = asInt(p.get("coin"));
            pl.resources.add(Resource.COIN, n);
            got.put("coin", n);
        }
        if (p.containsKey("debris")) {
            got.put("debris", Storage.addDebrisCapped(s, pl, asInt(p.get("debris"))));
        }
        if (p.containsKey("kelium")) {
            got.put("kelium", Storage.addKeliumCapped(s, pl, asInt(p.get("kelium"))));
        }
        if (p.containsKey("ammo")) {
            got.put("ammo", Storage.addAmmoCapped(s, pl, asInt(p.get("ammo"))));
        }
        if (p.containsKey("containers")) {
            int n = asInt(p.get("containers"));
            got.put("containers", Storage.addContainersCapped(s, pl, n, "карта/эффект"));
        }
        if (p.containsKey("objective_cards")) {
            int want = asInt(p.get("objective_cards"));
            int drawn = 0;
            for (int i = 0; i < want; i++) {
                String c = s.decks.get("objectives").draw(s.rng);
                if (c == null) {
                    break;
                }
                pl.objectiveHand.add(c);
                drawn++;
            }
            got.put("objective_cards", drawn);
            // «ТЯНИ ЧЕТЫРЕ, ОСТАВЬ ДВЕ» — карта рынка «Штаб корпуса», предложение
            // «Штабная работа». Выбор из четырёх — это совсем другое предложение,
            // чем три карты подряд: игрок берёт то, что подходит его положению, а
            // не то, что легло. Лишние уходят в сброс.
            if (p.containsKey("objective_cards_keep") && drawn > 0) {
                int keep = asInt(p.get("objective_cards_keep"));
                List<String> pool = new ArrayList<>(
                    pl.objectiveHand.subList(pl.objectiveHand.size() - drawn, pl.objectiveHand.size()));
                Agent ag = agentFor(s, seat);
                while (pool.size() > keep) {
                    List<Choice> opts = new ArrayList<>();
                    for (String c : pool) {
                        opts.add(new Choice("drop_objective", c, "сбросить " + c));
                    }
                    Choice pick = ag != null
                        ? ag.choose(s, opts, Map.of("kind", "objective_keep", "keep", keep))
                        : opts.get(opts.size() - 1);
                    String drop = pick != null && pick.payload() != null
                        ? String.valueOf(pick.payload()) : pool.get(pool.size() - 1);
                    pool.remove(drop);
                    pl.objectiveHand.remove(drop);
                    s.decks.get("objectives").discard(drop);
                }
                got.put("objective_cards", pool.size());
                got.put("objective_cards_dropped", drawn - pool.size());
            }
        }
        if (p.containsKey("arsenal")) {
            int want = asInt(p.get("arsenal"));
            int drawn = 0;
            for (int i = 0; i < want; i++) {
                String c = s.decks.get("arsenal").draw(s.rng);
                if (c == null) {
                    break;
                }
                pl.arsenalHand.add(c);
                drawn++;
            }
            got.put("arsenal", drawn);
        }
        Object module = p.get("module");
        if ("attack".equals(module)) {
            // «Модули 2.0»: с мешками жетон тянется случайно, а не выбирается
            String drew = Modules.awardModule(s, pl, "red");
            got.put("module", drew == null ? "attack" : drew);
        } else if ("assembly".equals(module)) {
            String drew = Modules.awardModule(s, pl, "blue");
            got.put("module", drew == null ? "assembly" : drew);
        } else if ("choice".equals(module)) {
            // ВЫБОР ЦВЕТА МЕШКА за игроком (покупка жетона на карте рынка). Сам
            // жетон внутри цвета всё равно тянется случайно — мешок есть мешок.
            String colour = pl.redModules <= pl.blueModules ? "red" : "blue";
            Agent ag = agentFor(s, seat);
            if (ag != null) {
                Choice pick = ag.choose(s, List.of(
                    new Choice("module_bag", "red", "жетон АТАКИ из красного мешка"),
                    new Choice("module_bag", "blue", "жетон СБОРКИ из синего мешка")),
                    Map.of("kind", "module_bag"));
                if (pick != null && pick.payload() != null) {
                    colour = String.valueOf(pick.payload());
                }
            }
            String drew = Modules.awardModule(s, pl, colour);
            got.put("module", drew == null ? colour : drew);
        }
        // ПОЛОВИНОК МОДУЛЕЙ БОЛЬШЕ НЕТ (решение дизайнера 13.08.2026). Механика
        // «две половинки складываются в жетон» отменена целиком: она требовала
        // отдельного учёта на планшете, а выгоды не давала. Всё, что раньше давало
        // половинку, теперь тянет ЦЕЛЫЙ жетон модуля из мешка — так же, как награда
        // трека или задания. Старые карты с ключом module_half продолжают работать
        // без правки данных, поэтому ключ читается, но означает целый жетон.
        Object half = p.get("module_half");
        if (half != null) {
            String colour = "assembly".equals(half.toString()) ? "blue"
                : "attack".equals(half.toString()) ? "red"
                : pl.redModules <= pl.blueModules ? "red" : "blue";
            String drew = Modules.awardModule(s, pl, colour);
            got.put("module", drew == null ? colour : drew);
        }
        if (Boolean.TRUE.equals(p.get("gild_module"))) {
            if (pl.redModules + pl.blueModules > pl.goldModules) {
                pl.goldModules += 1;
                got.put("gild_module", true);
            }
        }
        // storage_token из эффектов ОТМЕНЁН (правило 2026-08-11): жетон хранилища
        // выдаёт ТОЛЬКО зелёный трек. Старые карты с этим эффектом дают полжетона
        // сборки взамен (чтобы эффект не был пустым).
        if (Boolean.TRUE.equals(p.get("storage_token"))) {
            String drew = Modules.awardModule(s, pl, "blue");
            got.put("module", drew == null ? "blue" : drew);
        }
        return got;
    }

    // -- лечение --------------------------------------------------------------
    static Map<String, Object> healOne(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        List<Token> dmgd = damagedTokens(pl);
        if (dmgd.isEmpty()) {
            return Map.of("healed", 0);
        }
        dmgd.sort((a, b) -> Integer.compare(damageOf(b), damageOf(a)));
        Token tok = dmgd.get(0);
        int removed;
        if ("all".equals(p.get("amount"))) {
            removed = damageOf(tok);
            setDamage(tok, 0);
        } else {
            removed = Math.min(1, damageOf(tok));
            setDamage(tok, damageOf(tok) - removed);
        }
        return Map.of("healed", removed);
    }

    static Map<String, Object> healAllOwn(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        int healed = 0;
        for (Token t : damagedTokens(pl)) {
            healed += damageOf(t);
            setDamage(t, 0);
        }
        if (p != null && !p.isEmpty()) {
            gain(s, seat, p);
        }
        return Map.of("healed_total", healed);
    }

    static Map<String, Object> healHex(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        Map<String, List<Token>> byHex = new HashMap<>();
        for (Token t : damagedTokens(pl)) {
            String h = t.hexId();
            if (h != null) {
                byHex.computeIfAbsent(h, k -> new ArrayList<>()).add(t);
            }
        }
        if (byHex.isEmpty()) {
            return Map.of("healed", 0);
        }
        String best = null;
        int bestDmg = -1;
        for (var e : byHex.entrySet()) {
            int sum = 0;
            for (Token t : e.getValue()) {
                sum += damageOf(t);
            }
            if (sum > bestDmg) {
                bestDmg = sum;
                best = e.getKey();
            }
        }
        int healed = 0;
        for (Token t : byHex.get(best)) {
            healed += damageOf(t);
            setDamage(t, 0);
        }
        Map<String, Object> got = new HashMap<>();
        got.put("healed", healed);
        got.put("hex", best);
        return got;
    }

    // -- под-действия ---------------------------------------------------------
    static Map<String, Object> freeAction(GameState s, int seat, Map<String, Object> p) {
        String name = (String) p.get("action");
        Agent agent = agentFor(s, seat);
        Map<String, Object> got = new HashMap<>();
        got.put("free_action", name);
        if (name == null || agent == null || !Actions.ALL_NAMES.contains(name)) {
            got.put("ran", false);
            return got;
        }
        // ПОДАРОК К ДЕЙСТВИЮ — те же параметры, что у обычного gain: «бесплатная
        // Наука и сверху 1 обломок» (Лицензия), «бесплатный Бой и 1 боеприпас в
        // его начале» (Внезапный удар), «бесплатная Смена энергии и 2 монеты»
        // (Перекоммутация).
        //
        // ПОРЯДОК ВАЖЕН: ресурсы выдаются ДО действия, потому что на карте так и
        // написано — «в начале этого боя». Боеприпас, пришедший после боя, боем
        // уже не потратишь, и предложение стало бы пустым.
        got.putAll(gain(s, seat, p));
        // E4: бесплатное действие с карты — самостоятельное разрешение со своим
        // контекстом (наценки внутри него считаются с нуля — это дар карты, а
        // не продолжение хода), но ЖУРНАЛ и телеметрия обязаны его видеть.
        TurnContext ctx = new TurnContext(seat, 0);
        // ПРЕДЕЛ ОБЪЕКТОВ с карты: «Сборка не более чем двумя зданиями»,
        // «одна строительная операция». Без него бесплатное действие было
        // сильнее выполненного задания, и карту выгоднее было сжечь.
        if (p.get("buildings") instanceof Number bn) {
            ctx.objectLimits.put(name, bn.intValue());
        }
        if (p.get("ops") instanceof Number on) {
            ctx.objectLimits.put(name, on.intValue());
        }
        // МОБИЛИЗАЦИЯ: Сборка без энергии вообще.
        if (Boolean.TRUE.equals(p.get("all_powered"))) {
            ctx.allPowered = true;
        }
        // ПОДРЯД НА СТРОЙКУ / ПЕРЕКОММУТАЦИЯ: надбавки за вторую и последующие
        // операции нет. Ключи в данных — те же, по которым движок ведёт счётчик.
        if (p.get("no_surcharge") instanceof java.util.List<?> keys) {
            for (Object k : keys) {
                ctx.noSurcharge.add(String.valueOf(k));
            }
        }
        var res = Actions.create(name, s).perform(s.player(seat), ctx, agent);
        if (s.journal instanceof TurnJournal tj && res != null && res.ok()) {
            tj.onAction(seat, name, res.telemetry());
        }
        got.put("ran", res != null && res.ok());
        if (res != null) {
            got.put("detail", res.detail());
        }
        return got;
    }

    static Map<String, Object> moveUnit(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        Agent agent = agentFor(s, seat);
        int steps = p.containsKey("hexes") ? asInt(p.get("hexes")) : 1;
        int moved = 0;
        for (int step = 0; step < steps; step++) {
            List<Choice> opts = new ArrayList<>();
            for (UnitToken u : pl.unitsOnField()) {
                // СКОРОСТЬ 0 — НЕ ДВИГАЕТСЯ НИЧЕМ. Вышка ЦУ — дот: она стоит
                // там, где поставлена. Раньше эффект карты («переброска») этого
                // не проверял и таскал вышки по полю (баг найден дизайнером
                // 12.08.2026); Движение и манёвр скорость учитывали, а карты нет.
                Integer airOverride = Passives.aircraftSpeedOverride(s, seat);
                int speed = airOverride != null && u.type == kelium.core.UnitType.AIRCRAFT
                    ? airOverride : kelium.engine.Speed.of(s, pl.seat, u);
                if (speed <= 0) {
                    continue;
                }
                for (String nb : s.field.neighbors(u.hexId)) {
                    // E3: эффект карты обязан соблюдать ПРАВИЛА ПРОХОДИМОСТИ
                    // (запретные гексы, грядки, чужие здания, 2 сектора технике).
                    if (!Actions.MovementAction.canEnterHex(s, u, nb, seat)) {
                        continue;
                    }
                    Map<String, Object> mp = new HashMap<>();
                    mp.put("uid", u.uid);
                    mp.put("to", nb);
                    opts.add(new Choice("move", mp, u.type.code + "->" + nb));
                }
            }
            if (opts.isEmpty()) {
                break;
            }
            opts.add(new Choice("pass", null, "остановиться"));
            Choice ch = agent != null
                ? agent.choose(s, opts, Map.of("kind", "move"))
                : opts.get(opts.size() - 1);
            if (ch.payload() == null) {
                break;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> mp = (Map<String, Object>) ch.payload();
            int uid = asInt(mp.get("uid"));
            for (UnitToken u : pl.units) {
                if (u.uid == uid) {
                    String wasAt = u.hexId;
                    boolean wasInside = u.inside();     // снять ДО хода
                    u.setHexId((String) mp.get("to"));   // выводит из здания
                    // E3: печатный контейнер срабатывает так же, как при Движении
                    PrintedContainers.onUnitMoved(s, pl, wasAt, u.hexId, u.type, wasInside);
                    break;
                }
            }
            moved++;
        }
        return Map.of("moved", moved);
    }

    // ==================================================================
    //  ЭФФЕКТЫ РЕВЬЮ ОДНОРАЗОВЫХ ЭФФЕКТОВ (17.08.2026)
    // ==================================================================

    /**
     * ЩИТ — положи жетон щита на строку ОДНОГО из двух названных на карте родов
     * войск. Щит снимает ПЕРВОЕ попадание по жетону этого рода и уходит.
     *
     * <p>Заменяет прежнее «снять 1 урон с пехоты»: у пехоты прочность 1, снимать
     * там нечего — жетон уже уничтожен. Защита обязана срабатывать ДО попадания.
     * Сам жетон щита — физический объект на планшете, поэтому правило «эффект
     * живёт, пока лежит объект» (СВОД §9.1) соблюдено.
     *
     * <p>Параметр {@code types} — два рода на выбор игрока.
     */
    static Map<String, Object> shield(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        List<UnitType> offer = new ArrayList<>();
        if (p.get("types") instanceof List<?> l) {
            for (Object o : l) {
                try {
                    offer.add(UnitType.fromCode(String.valueOf(o)));
                } catch (RuntimeException ignored) {
                    // род, которого нет в игре, просто не предлагается
                }
            }
        }
        if (offer.isEmpty()) {
            offer = List.of(UnitType.INFANTRY, UnitType.VEHICLE);
        }
        UnitType pick = offer.get(0);
        Agent ag = agentFor(s, seat);
        if (ag != null && offer.size() > 1) {
            List<Choice> opts = new ArrayList<>();
            for (UnitType t : offer) {
                opts.add(new Choice("shield_kind", t.code, "щит на " + t.code));
            }
            Choice ch = ag.choose(s, opts, Map.of("kind", "shield"));
            if (ch != null && ch.payload() != null) {
                pick = UnitType.fromCode(String.valueOf(ch.payload()));
            }
        }
        pl.shieldedKinds.add(pick);
        return Map.of("shield", pick.code);
    }

    /**
     * ДЕСАНТ — размести на поле до {@code count} жетонов РАЗНЫХ родов войск.
     *
     * <p>Отличие от Сборки: жетоны не производятся зданиями и не требуют ни
     * энергии, ни гекса со зданием — они высаживаются. Ограничение одно и оно
     * жёсткое: рода должны быть РАЗНЫЕ, иначе карта превращалась бы в две
     * бесплатные пехоты.
     */
    static Map<String, Object> landing(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        Agent agent = agentFor(s, seat);
        int count = p.containsKey("count") ? asInt(p.get("count")) : 2;
        java.util.Set<UnitType> used = java.util.EnumSet.noneOf(UnitType.class);
        int placed = 0;
        // ТОЛЬКО СВОЯ ЗОНА СТРОЙКИ (решение дизайнера 20.08.2026).
        //
        // БЫЛО: перебирались ВСЕ гексы поля, и десант ставил два жетона куда
        // угодно — включая гекс рядом с чужим ЦУ. На карте при этом написано
        // просто «размести», без места, поэтому за столом никто и не догадался
        // бы, что так можно. Это ломало всю пространственную логику игры: зона
        // стройки растёт от своих стенок, движение идёт шагами и стоит приказа,
        // добытчик достаёт только до примыкающей жилы — везде дотянуться надо
        // заслужить, и только здесь два жетона появлялись из воздуха в любой
        // точке стола, без приказа Движения.
        //
        // Зона стройки взята потому, что это УЖЕ существующее понятие «куда я
        // имею право что-то ставить»: игрок его знает, видит на поле и ему не
        // надо объяснять новое правило.
        java.util.Set<String> zone =
            new java.util.LinkedHashSet<>(Actions.buildableHexes(s, seat));
        while (placed < count) {
            List<Choice> opts = new ArrayList<>();
            for (UnitType ut : UnitType.values()) {
                if (used.contains(ut) || pl.unitsOfKind(ut) >= s.tokenStats.unitStock(ut)) {
                    continue;
                }
                for (String hid : zone) {
                    Hex h = s.field.hexes.get(hid);
                    if (h == null || !Movement.passable(s, h.id)
                            || !roomForLanding(s, h.id, ut)) {
                        continue;
                    }
                    Map<String, Object> mp = new HashMap<>();
                    mp.put("type", ut.code);
                    mp.put("hex", h.id);
                    opts.add(new Choice("landing", mp, "высадить " + ut.code + " @" + h.id));
                }
            }
            if (opts.isEmpty()) {
                break;
            }
            opts.add(new Choice("pass", null, "хватит высаживать"));
            Choice ch = agent != null ? agent.choose(s, opts, Map.of("kind", "landing"))
                : opts.get(0);
            if (ch.payload() == null) {
                break;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> mp = (Map<String, Object>) ch.payload();
            UnitType ut = UnitType.fromCode(String.valueOf(mp.get("type")));
            String hex = String.valueOf(mp.get("hex"));
            UnitToken u = s.tokenStats.makeUnit(ut, seat, Placement.nextUid(s), pl.unitsOfKind(ut));
            u.hexId = hex;
            pl.units.add(u);
            PrintedContainers.onUnitPlaced(s, pl, hex, ut);
            used.add(ut);
            placed++;
        }
        return Map.of("landed", placed);
    }

    /**
     * Есть ли на гексе свободная ячейка нужного размера. Правила размещения те
     * же, что у Движения: техника просит две смежные наземные ячейки, авиация —
     * свободную воздушную.
     */
    private static boolean roomForLanding(GameState s, String hexId, UnitType t) {
        Hex h = s.field.get(hexId);
        if (h == null) {
            return false;
        }
        if (t == UnitType.AIRCRAFT) {
            return h.airToken == null;
        }
        int busy = 0;
        for (int i = 0; i < h.sideOwner.length; i++) {
            if (h.sideOwner[i] != null) {
                busy++;
            }
        }
        return h.sideOwner.length - busy >= (t == UnitType.VEHICLE ? 2 : 1);
    }

    /**
     * +1 К СКОРОСТИ одного рода войск ДО КОНЦА ХОДА.
     *
     * <p>Хранится в журнале хода: «до конца хода» — это ровно срок жизни журнала,
     * и заводить ради этого новое состояние объекта правила запрещают.
     */
    static Map<String, Object> speedBoost(GameState s, int seat, Map<String, Object> p) {
        Agent ag = agentFor(s, seat);
        UnitType pick = UnitType.INFANTRY;
        List<Choice> opts = new ArrayList<>();
        for (UnitType t : UnitType.values()) {
            if (Speed.of(s, seat, t) > 0) {      // вышка со скоростью 0 не разгоняется
                opts.add(new Choice("speed_kind", t.code, "+1 скорости: " + t.code));
            }
        }
        if (opts.isEmpty()) {
            return Map.of("speed_boost", "нет подвижных родов");
        }
        if (ag != null) {
            Choice ch = ag.choose(s, opts, Map.of("kind", "speed_boost"));
            if (ch != null && ch.payload() != null) {
                pick = UnitType.fromCode(String.valueOf(ch.payload()));
            }
        } else {
            pick = UnitType.fromCode(String.valueOf(opts.get(0).payload()));
        }
        s.journal.of(seat).speedBoostKind = pick.code;
        return Map.of("speed_boost", pick.code);
    }

    /**
     * ВЫБОР НА ОДНОЙ КАРТЕ: сыграть Смену энергии ИЛИ Смену модулей.
     *
     * <p>Смена модулей — единственная перестановка на планшете, которая обычно
     * доступна только в свой этап; карта даёт её вне очереди, и это настоящая
     * альтернатива энергии, а не довесок.
     */
    static Map<String, Object> energyOrModules(GameState s, int seat, Map<String, Object> p) {
        Agent ag = agentFor(s, seat);
        String pick = "energy_swap";
        if (ag != null) {
            Choice ch = ag.choose(s, List.of(
                new Choice("energy_or_modules", "energy_swap", "Смена энергии"),
                new Choice("energy_or_modules", "modules", "Смена модулей на планшете")),
                Map.of("kind", "energy_or_modules"));
            if (ch != null && ch.payload() != null) {
                pick = String.valueOf(ch.payload());
            }
        }
        if ("modules".equals(pick)) {
            Modules.moduleSwap(s, seat, ag, ev -> { });
            return Map.of("chose", "modules");
        }
        Map<String, Object> got = new HashMap<>(freeAction(s, seat, Map.of("action", "energy_swap")));
        got.put("chose", "energy_swap");
        return got;
    }

    /**
     * КОНВЕРСИЯ — обменять {@code amount} одного ресурса на другой.
     *
     * <p>Слово наконец значит то, что значит: раньше «конверсией» назывался урон
     * соседу, что не имеет к слову никакого отношения.
     */
    static Map<String, Object> convert(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        int amount = p.containsKey("amount") ? asInt(p.get("amount")) : 1;
        Resource from;
        Resource to;
        try {
            from = Resource.fromCode(String.valueOf(p.getOrDefault("from", "kelium")));
            to = Resource.fromCode(String.valueOf(p.getOrDefault("to", "ammo")));
        } catch (RuntimeException e) {
            return Map.of("converted", 0);
        }
        if (!pl.resources.canPay(from, amount)) {
            return Map.of("converted", 0);
        }
        pl.resources.pay(from, amount);
        int got = switch (to) {
            case AMMO -> Storage.addAmmoCapped(s, pl, amount);
            case DEBRIS -> Storage.addDebrisCapped(s, pl, amount);
            case KELIUM -> Storage.addKeliumCapped(s, pl, amount);
            default -> {
                pl.resources.add(to, amount);
                yield amount;
            }
        };
        Map<String, Object> out = new HashMap<>();
        out.put("converted", got);
        out.put("from", from.code);
        out.put("to", to.code);
        return out;
    }

    /** СБРОСИТЬ 1 КАРТУ АРСЕНАЛА у другого игрока (у кого их больше всех). */
    static Map<String, Object> discardEnemyArsenal(GameState s, int seat, Map<String, Object> p) {
        PlayerState victim = null;
        for (PlayerState o : s.players) {
            if (o.seat == seat || o.arsenalHand.isEmpty()) {
                continue;
            }
            if (victim == null || o.arsenalHand.size() > victim.arsenalHand.size()) {
                victim = o;
            }
        }
        if (victim == null) {
            return Map.of("discarded", 0);
        }
        String card = victim.arsenalHand.remove(victim.arsenalHand.size() - 1);
        s.decks.get("arsenal").discard(card);
        return Map.of("discarded", 1, "from_seat", victim.seat, "card", card);
    }

    /**
     * РАЗЫГРАЙ ЛЮБОЕ ЧИСЛО СПЕЦ-ДЕЙСТВИЙ ДО КОНЦА ХОДА.
     *
     * <p>Лимит СПЕЦ живёт в контексте хода, а эффект карты до него не дотягивается,
     * поэтому снятие лимита отмечается в журнале хода — ход читает его перед
     * следующим предложением СПЕЦ.
     */
    static Map<String, Object> unlimitedSpec(GameState s, int seat, Map<String, Object> p) {
        s.journal.of(seat).unlimitedSpec = true;
        return Map.of("unlimited_spec", true);
    }

    /** ВЕРНУТЬ НА МАРКЕТ сброшенную карту сделок на рынке. */
    static Map<String, Object> marketCardFromDiscard(GameState s, int seat, Map<String, Object> p) {
        var deck = s.decks.get("market");
        if (deck == null) {
            return Map.of("returned", 0);
        }
        String card = deck.takeFromDiscard(s.rng);
        if (card == null) {
            return Map.of("returned", 0);
        }
        s.marketActive = card;
        return Map.of("returned", 1, "card", card);
    }

    /** ЗАМЕНИТЬ 1 КАРТУ ПРИКАЗА В РУКЕ на карту из своего сброса приказов. */
    static Map<String, Object> swapOrderCard(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        if (pl.orderHand.isEmpty() || pl.orderPlayed.isEmpty()) {
            return Map.of("swapped", 0);
        }
        String back = pl.orderPlayed.remove(pl.orderPlayed.size() - 1);
        String out = pl.orderHand.remove(pl.orderHand.size() - 1);
        pl.orderHand.add(back);
        pl.orderPlayed.add(out);
        return Map.of("swapped", 1, "took", back, "gave", out);
    }

    static Map<String, Object> deployUnits(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        int count = p.containsKey("count") ? asInt(p.get("count")) : 1;
        int placed = 0;
        for (BuildingToken b : pl.buildingsOnField()) {
            if (placed >= count) {
                break;
            }
            UnitType ut = Actions.ASSEMBLY_UNIT.get(b.type);
            if (ut == null || !b.powered()) {
                continue;
            }
            // Личный запас по роду (4 жетона), а не общий предел на цвет — см.
            // TokenStats.unitStock.
            if (pl.unitsOfKind(ut) < s.tokenStats.unitStock(ut)) {
                // Номер в запасе рода задаёт трофейный оборот жетона.
                UnitToken u = s.tokenStats.makeUnit(ut, seat, Placement.nextUid(s),
                    pl.unitsOfKind(ut));
                u.hexId = b.hexId;
                pl.units.add(u);
                // Разворот жетона — тоже НАКРЫТИЕ печатной ячейки.
                PrintedContainers.onUnitPlaced(s, pl, b.hexId, ut);
                placed++;
            }
        }
        return Map.of("deployed", placed);
    }

    static Map<String, Object> placeDamage(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        Agent agent = agentFor(s, seat);
        boolean finishOff = Boolean.TRUE.equals(p.get("finish_off"));
        java.util.Set<String> ownHexes = new java.util.HashSet<>();
        for (UnitToken u : pl.unitsOnField()) {
            ownHexes.add(u.hexId);
        }
        List<Token> victims = new ArrayList<>();
        for (PlayerState opp : s.players) {
            if (opp.seat == seat) {
                continue;
            }
            List<Token> toks = new ArrayList<>();
            toks.addAll(opp.unitsOnField());
            toks.addAll(opp.buildingsOnField());
            for (Token t : toks) {
                boolean adj = false;
                for (String h : ownHexes) {
                    if (t.hexId().equals(h) || s.field.neighbors(h).contains(t.hexId())) {
                        adj = true;
                        break;
                    }
                }
                if (!adj) {
                    continue;
                }
                if (finishOff) {
                    if (damageOf(t) >= Passives.effectiveHp(s, t) - 1) {
                        victims.add(t);
                    }
                } else {
                    victims.add(t);
                }
            }
        }
        if (victims.isEmpty()) {
            return Map.of("damaged", 0);
        }
        List<Choice> opts = new ArrayList<>();
        for (Token v : victims) {
            opts.add(new Choice("dmg", uidOf(v), "hit"));
        }
        Choice ch = agent != null ? agent.choose(s, opts, Map.of("kind", "place_damage")) : opts.get(0);
        int uid = (Integer) ch.payload();
        Token v = victims.get(0);
        for (Token t : victims) {
            if (uidOf(t) == uid) {
                v = t;
                break;
            }
        }
        setDamage(v, damageOf(v) + 1);
        boolean destroyed = damageOf(v) >= Passives.effectiveHp(s, v);
        if (destroyed && s.combat != null) {
            ((CombatResolver) s.combat).destroy(v, seat);
        }
        Map<String, Object> got = new HashMap<>();
        got.put("damaged", 1);
        got.put("destroyed", destroyed);
        return got;
    }

    static Map<String, Object> grabContainers(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        int count = p.containsKey("count") ? asInt(p.get("count")) : 1;
        // adjacent_only (пассив grab_adjacent_container): только гексы своего
        // присутствия и их соседи, не всё поле.
        java.util.Set<String> allowed = null;
        if (Boolean.TRUE.equals(p.get("adjacent_only"))) {
            allowed = new java.util.HashSet<>();
            for (BuildingToken b : pl.buildingsOnField()) {
                allowed.add(b.hexId);
                allowed.addAll(s.field.neighbors(b.hexId));
            }
            for (UnitToken u : pl.unitsOnField()) {
                allowed.add(u.hexId);
                allowed.addAll(s.field.neighbors(u.hexId));
            }
        }
        int got = 0;
        for (Hex h : s.field.hexes.values()) {
            if (got >= count) {
                break;
            }
            if (allowed != null && !allowed.contains(h.id)) {
                continue;
            }
            // «Собрать контейнеры с поля» — эффекта больше нет: жетонов на поле
            // не бывает. Оставляем выдачу ИЗ ЗАПАСА, чтобы карты с таким верхом
            // не превратились в пустышку.
            if (h.containerCell >= 0 && got < count) {
                got += Storage.addContainersCapped(s, pl, 1, "карта «собрать контейнеры»");
            }
        }
        return Map.of("grabbed", got);
    }

    // -- вспомогательные ------------------------------------------------------
    private static List<Token> damagedTokens(PlayerState pl) {
        List<Token> out = new ArrayList<>();
        for (UnitToken u : pl.units) {
            if (u.damage > 0) {
                out.add(u);
            }
        }
        for (BuildingToken b : pl.buildings) {
            if (b.damage > 0) {
                out.add(b);
            }
        }
        return out;
    }

    private static int damageOf(Token t) {
        return t instanceof UnitToken u ? u.damage : ((BuildingToken) t).damage;
    }

    private static void setDamage(Token t, int v) {
        if (t instanceof UnitToken u) {
            u.damage = v;
        } else {
            ((BuildingToken) t).damage = v;
        }
    }

    private static int uidOf(Token t) {
        return t instanceof UnitToken u ? u.uid : ((BuildingToken) t).uid;
    }

    // ==================================================================
    //  ЭФФЕКТЫ, ДОПИСАННЫЕ 15.08.2026 (были заглушкой noop)
    // ==================================================================

    /**
     * ЗАБРАТЬ ЖЕТОН ПЕРВОГО ИГРОКА на следующий раунд.
     *
     * <p>Карты: контейнер c28 «Интриган», рынок «Штаб корпуса» (левое).
     *
     * <p>Порядок хода в этой игре стоит дорого: первый вскрывает приказ раньше
     * и первым занимает гексы. Поэтому эффект и лежит на хороших картах.
     */
    static Map<String, Object> grabFirstPlayer(GameState s, int seat, Map<String, Object> p) {
        int was = s.firstPlayer;
        if (Boolean.TRUE.equals(p.get("now"))) {
            // ПРИОРИТЕТ (карта рынка «Штаб корпуса»): игрок ЗАБИРАЕТ жетон себе
            // сейчас же, и в ближайшее Обновление жетон не передаётся — карта
            // отменяет правило передачи на этот один раз, пока лежит на рынке.
            s.firstPlayer = seat;
            s.firstPlayerHeld = true;
        } else {
            // В конце раунда движок сдвигает жетон на следующего по кругу, поэтому
            // кладём его на ПРЕДЫДУЩЕГО: после сдвига он окажется у нас.
            s.firstPlayer = Math.floorMod(seat - 1, s.numPlayers());
        }
        Map<String, Object> got = new HashMap<>();
        got.put("first_player_was", was);
        got.put("first_player_next", seat);
        // steal_coin: жетон отбирается ВМЕСТЕ с монетой у прежнего владельца —
        // отдельная просьба дизайнера 17.08.2026. Если у него монет нет, берётся
        // сколько есть: долгов в игре не бывает.
        if (p.get("steal_coin") instanceof Number cn && was != seat && was < s.numPlayers()) {
            PlayerState victim = s.player(was);
            int take = Math.min(cn.intValue(), victim.resources.coin());
            if (take > 0) {
                victim.resources.pay(Resource.COIN, take);
                s.player(seat).resources.add(Resource.COIN, take);
            }
            got.put("stolen_coin", take);
        }
        // take_objectives: жетон отбирается ВМЕСТЕ СО ВСЕЙ РУКОЙ ЗАДАНИЙ прежнего
        // первого игрока (правило дизайнера 17.08.2026). Штаб корпуса забирает не
        // только очерёдность, но и бумаги — потому предложение и стоит взять: до
        // этого «Приоритет» давал чистую позицию и не выбирался ни разу за 511
        // раундов замера, проигрывая заданиям напротив со счётом 0:316.
        if (Boolean.TRUE.equals(p.get("take_objectives"))
                && was != seat && was < s.numPlayers()) {
            PlayerState victim = s.player(was);
            List<String> taken = new ArrayList<>(victim.objectiveHand);
            victim.objectiveHand.clear();
            s.player(seat).objectiveHand.addAll(taken);
            got.put("taken_objectives", taken.size());
        }
        return got;
    }

    /**
     * ЗДАНИЕ СЧИТАЕТСЯ ЗАПИТАННЫМ на одно действие.
     *
     * <p>Карта: контейнер c20 «Резерв».
     *
     * <p>Реализуется выдачей одного кубика энергии прямо на выбранное здание:
     * дефицит энергии в игре ровно три кубика на десять потребителей, поэтому
     * «запитать бесплатно» и «дать кубик» здесь одно и то же по действию, но
     * второе не требует нового состояния объекта — а плодить состояния ради
     * одного эффекта правила запрещают.
     */
    static Map<String, Object> powerBuildingFree(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        BuildingToken best = null;
        for (BuildingToken b : pl.buildingsOnField()) {
            if (b.energySlots <= 0 || b.energyPlaced >= b.energySlots) {
                continue;
            }
            // Ближе всего к работе — тому, кому не хватает меньше всего.
            if (best == null
                    || (b.energySlots - b.energyPlaced) < (best.energySlots - best.energyPlaced)) {
                best = b;
            }
        }
        if (best == null) {
            return Map.of("powered", 0);
        }
        best.addEnergyFrom(-1, 1);
        return Map.of("powered", 1, "building", best.uid());
    }

    /**
     * ПОСТОЯННЫЙ КУБИК ЭНЕРГИИ на здание.
     *
     * <p>Карта: контейнер c26 «Резервный генератор». Отличие от предыдущего —
     * кубик не разовый: он остаётся на здании и дальше работает как обычный.
     */
    static Map<String, Object> permanentEnergy(GameState s, int seat, Map<String, Object> p) {
        Map<String, Object> got = powerBuildingFree(s, seat, p);
        return Map.of("permanent", got.getOrDefault("powered", 0),
            "building", got.getOrDefault("building", -1));
    }

    /**
     * ОТМЕНИТЬ ОДНУ АТАКУ по своему жетону.
     *
     * <p>Карта: контейнер c14 «Перехват».
     *
     * <p>Настоящая отмена требовала бы реактивного окна в бою, а его в правилах
     * нет: карта контейнера не лежит открытой и «до конца раунда» на ней быть не
     * может. Поэтому эффект приведён к тому же результату другой стороной —
     * СНЯТЬ УЖЕ ПОЛУЧЕННЫЙ УРОН с одного своего жетона. С правилами 1.7.0, где
     * урон не снимается сам никогда, это ровно та же ценность: жетон переживает
     * попадание, которого иначе бы не пережил.
     */
    static Map<String, Object> cancelAttack(GameState s, int seat, Map<String, Object> p) {
        return healOne(s, seat, p);
    }

    /**
     * ПОСТРОИТЬ НЕЙТРАЛЬНОЕ ЗДАНИЕ.
     *
     * <p>Карта: рынок «Гражданский подряд» (левое предложение).
     *
     * <p>Нейтральное здание — это СТЕНА, и в этом весь смысл предложения: оно
     * закрывает сектор гекса для прохода и для выстрела. Поэтому и ставится оно
     * НА ЛЮБОЕ СВОБОДНОЕ МЕСТО поля — на любой гекс, где есть один или два
     * свободных сектора, а не только на пустой гекс. Заграждение нужно ровно
     * там, где сосед собирается пройти, то есть чаще всего на занятом гексе.
     *
     * <p>Размер здания — 1 или 2 сектора, по выбору игрока; больших нейтралов
     * это предложение не ставит.
     */
    static Map<String, Object> buildNeutral(GameState s, int seat, Map<String, Object> p) {
        Agent agent = agentFor(s, seat);
        List<Choice> opts = new ArrayList<>();
        for (var e : s.field.hexes.entrySet()) {
            kelium.core.Hex h = e.getValue();
            // ГДЕ УГОДНО, В ТОМ ЧИСЛЕ ПОВЕРХ ЧУЖОГО (правило дизайнера
            // 17.08.2026). Годятся и свободные секторы, и занятые ЧУЖИМИ
            // зданиями: подрядчик приходит и застраивает участок, а стоявшее
            // там чужое здание возвращается владельцу в запас. Свои секторы и
            // секторы под нейтралами не трогаются — своё не сносим, а нейтрал
            // поверх нейтрала не ставится.
            for (int i = 0; i < 6; i++) {
                if (!sectorTakeable(s, h, i, seat)) {
                    continue;
                }
                Map<String, Object> one = new HashMap<>();
                one.put("hex", e.getKey());
                one.put("sectors", List.of(i));
                opts.add(new Choice("neutral", one, "нейтрал 1 сектор @" + e.getKey() + "/" + i));
                int next = (i + 1) % 6;
                if (sectorTakeable(s, h, next, seat)) {
                    Map<String, Object> two = new HashMap<>();
                    two.put("hex", e.getKey());
                    two.put("sectors", List.of(i, next));
                    opts.add(new Choice("neutral", two,
                        "нейтрал 2 сектора @" + e.getKey() + "/" + i + "-" + next));
                }
            }
        }
        if (opts.isEmpty()) {
            return Map.of("built", 0);
        }
        Choice pick = agent != null
            ? agent.choose(s, opts, Map.of("kind", "build_neutral"))
            : opts.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>)
            (pick != null && pick.payload() != null ? pick.payload() : opts.get(0).payload());
        String hex = String.valueOf(spec.get("hex"));
        @SuppressWarnings("unchecked")
        List<Integer> sectors = (List<Integer>) spec.get("sectors");
        kelium.core.Hex h = s.field.get(hex);

        // ЧУЖОЕ ОСВОБОЖДАЕТ МЕСТО: здание уходит владельцу в ЗАПАС, а не в
        // трофеи и не в металлолом. Его можно отстроить заново, заплатив за
        // стройку — карта отбирает позицию, а не жетон.
        int ousted = 0;
        java.util.Set<Integer> toOust = new java.util.LinkedHashSet<>();
        for (Integer i : sectors) {
            Integer tokenUid = h.sideOwner[i];
            if (tokenUid != null && tokenUid >= 0) {
                toOust.add(tokenUid);   // в секторе стоит чьё-то здание
            }
        }
        for (int tokenUid : toOust) {
            for (PlayerState any : s.players) {
                if (any.seat == seat) {
                    continue;           // своё не сносим
                }
                for (kelium.core.BuildingToken b : new ArrayList<>(any.buildingsOnField())) {
                    if (b.uid == tokenUid) {
                        Actions.returnOwnBuildingToReserve(s, any, b, false);
                        ousted++;
                    }
                }
            }
        }
        // Отрицательные uid — соглашение движка для нейтралов (см. Scenario):
        // они не принадлежат никому и не пересекаются с жетонами игроков.
        int uid = -1000 - h.neutrals.size() - s.round;
        h.neutrals.add(new kelium.core.Hex.NeutralBuilding(uid, false, List.copyOf(sectors)));
        for (Integer i : sectors) {
            h.sideOwner[i] = -1;   // сектор занят нейтралом
        }
        return Map.of("built", 1, "hex", hex, "sectors", sectors.size(), "ousted", ousted);
    }

    /**
     * МОЖНО ЛИ ЗАСТРОИТЬ этот сектор нейтралом «Восстановления».
     *
     * <p>Да, если он свободен либо занят ЧУЖИМ зданием. Нет, если там своё
     * здание (себя не сносим) или уже стоит нейтрал (стенка поверх стенки
     * ничего не добавляет).
     */
    private static boolean sectorTakeable(GameState s, kelium.core.Hex h, int i, int seat) {
        Integer tokenUid = h.sideOwner[i];
        if (tokenUid == null) {
            return true;            // сектор пуст
        }
        if (tokenUid < 0) {
            return false;           // там нейтрал: стенка поверх стенки ничего не даёт
        }
        // В СЕКТОРЕ ЧЬЁ-ТО ЗДАНИЕ. Своё — нельзя, чужое — можно.
        for (kelium.core.BuildingToken b : s.player(seat).buildingsOnField()) {
            if (b.uid == tokenUid) {
                return false;
            }
        }
        return true;
    }

    /**
     * ЭВАКУАЦИЯ — карта рынка «Гражданский подряд», правое предложение.
     *
     * <p>ПОРЯДОК ТАКОЙ (правило дизайнера 17.08.2026). Игрок выбирает СВОЙ гекс —
     * тот, где стоят его жетоны, — и снимает с них весь урон. А затем ОБЯЗАН
     * увести с этого гекса ВСЕ свои жетоны на другой гекс, доступный ему для
     * стройки. Буквально эвакуация: подлечились и ушли, а место осталось пустым.
     *
     * <p>Выбора «кого забрать» нет: уходят все. Остаться может только тот, кому
     * на новом гексе физически не хватило места.
     *
     * <p>ЭТО НЕ СТРОЙКА И НЕ ДВИЖЕНИЕ, А ТЕЛЕПОРТ (правило дизайнера
     * 17.08.2026). Ни одно правило перемещения здесь не действует: не платится
     * монета за перенос здания, не спрашивается поворот, не проверяются
     * запретные гексы, гряды зарождения, чужие здания, требование двух смежных
     * секторов технике. Жетон снимается с одного гекса и ставится на другой.
     *
     * <p>Единственное, чего телепорт не отменяет, — ФИЗИКА ГЕКСА: секторов земли
     * шесть, сектор Неба один, и больше на гекс не влезет. Что не влезло,
     * остаётся на месте.
     *
     * <p>Зачем предложение существует: это единственный способ собрать
     * растянутую по полю группу в кулак и заодно вылечить её. Прежнее лечение
     * «всех своих жетонов» такой цены не имело — оно ничего не меняло на поле.
     */
    static Map<String, Object> evacuate(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        Agent agent = agentFor(s, seat);

        // 1. ОТКУДА УХОДИМ: свой гекс, где стоят свои жетоны. Гекс без своих
        // жетонов эвакуировать нечего — такого выбора в меню нет.
        java.util.Set<String> mine = new java.util.LinkedHashSet<>();
        for (kelium.core.BuildingToken b : pl.buildingsOnField()) {
            mine.add(b.hexId);
        }
        for (kelium.core.UnitToken u : pl.unitsOnField()) {
            mine.add(u.hexId);
        }
        if (mine.isEmpty()) {
            return Map.of("moved", 0);
        }
        // 2. КУДА УХОДИМ: любой гекс своей зоны стройки, кроме покидаемого.
        // Уходить некуда — эвакуации нет: карта требует именно УВЕСТИ.
        java.util.Set<String> zone = new java.util.LinkedHashSet<>(
            Actions.buildableHexes(s, seat));
        List<Choice> pairs = new ArrayList<>();
        for (String from : mine) {
            for (String to : zone) {
                if (to.equals(from)) {
                    continue;
                }
                Map<String, Object> spec = new HashMap<>();
                spec.put("from", from);
                spec.put("to", to);
                pairs.add(new Choice("evacuate", spec, "эвакуация " + from + " -> " + to));
            }
        }
        if (pairs.isEmpty()) {
            return Map.of("moved", 0);
        }
        Choice pick = agent != null
            ? agent.choose(s, pairs, Map.of("kind", "evacuate"))
            : pairs.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>)
            (pick != null && pick.payload() != null ? pick.payload() : pairs.get(0).payload());
        String from = String.valueOf(spec.get("from"));
        String target = String.valueOf(spec.get("to"));

        // 3. ЛЕЧЕНИЕ — на покидаемом гексе и только своим жетонам: они и есть те,
        // кого эвакуируют. Происходит ДО переноса.
        int healed = 0;
        for (kelium.core.Token t : damagedTokens(pl)) {
            if (from.equals(t.hexId())) {
                healed += damageOf(t);
                setDamage(t, 0);
            }
        }

        // 4. УХОДЯТ ВСЕ. Выбора «кого забрать» нет — карта требует увести гекс
        // целиком. Здания идут первыми, и это не мелочь: здание занимает жёсткие
        // секторы, войско — мягкие. Пусти вперёд пехоту — зданию уже не встать.
        int movedBuildings = 0;
        for (kelium.core.BuildingToken b : new ArrayList<>(pl.buildingsOnField())) {
            if (!from.equals(b.hexId)) {
                continue;
            }
            int fp = Actions.buildingFootprint(b.type);
            int[] ld = Actions.groundLoad(s, target, -1);
            List<Integer> sides = s.field.get(target).chooseFootprint(fp, ld[0], ld[1]);
            if (sides == null) {
                continue;               // физически не влезло — остаётся на месте
            }
            // Войско, стоявшее ВНУТРИ здания, с ним не телепортируется: остаётся
            // на прежнем гексе и теряет укрытие — как при обычном переносе.
            Actions.evictFromBuilding(pl, b);
            s.field.get(from).freeSidesByToken(b.uid);
            b.hexId = target;
            s.field.get(target).occupySides(b.uid, sides);
            // ЖЁЛТЫЙ СЕКТОР действует и после телепорта: это печатное свойство
            // самой станции, а не правило стройки.
            Actions.resettlePlant(s, pl, b);
            PrintedContainers.onBuildingPlaced(s, pl, b);
            movedBuildings++;
        }
        int movedUnits = 0;
        for (kelium.core.UnitToken u : new ArrayList<>(pl.unitsOnField())) {
            if (!from.equals(u.hexId)) {
                continue;
            }
            // ЕДИНСТВЕННАЯ ПРОВЕРКА — место. Правила проходимости не спрашиваются:
            // это телепорт, а не движение.
            if (!Actions.roomForUnit(s, target, u.type)) {
                continue;
            }
            boolean wasInside = u.inside();
            u.setHexId(target);
            PrintedContainers.onUnitMoved(s, pl, from, target, u.type, wasInside);
            movedUnits++;
        }
        Map<String, Object> got = new HashMap<>();
        got.put("moved", movedBuildings + movedUnits);
        got.put("moved_buildings", movedBuildings);
        got.put("moved_units", movedUnits);
        got.put("healed", healed);
        got.put("from", from);
        got.put("hex", target);
        return got;
    }
}
