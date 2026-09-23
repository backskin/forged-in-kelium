package kelium.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.GameConfig;
import kelium.core.TurnJournal;
import kelium.dataio.Ctx;

/**
 * Игра заданий — SPEC-действие, завершающее задание ради наград. Порт из
 * forge/engine/objectives.py.
 *
 * <p>Задание в руке играбельно, если выполнено его базовое требование (предикат
 * по состоянию + журналу). Задания-жертвы (Ж) сначала платят цену.
 *
 * <p><b>УСИЛЕННАЯ НАГРАДА — ВМЕСТО БАЗОВОЙ, А НЕ СВЕРХ НЕЁ</b> (правило
 * дизайнера 16.09.2026). Игрок, выполнивший усиленное требование, получает
 * ОДНУ награду НА ВЫБОР: базовую или усиленную. Прежде награды складывались, и
 * усиление было чистой прибавкой — теперь это развилка: усиленная награда
 * обычно очковая и дальняя, базовая — ресурсная и сейчас.
 *
 * <p>Выбор делается ДО оплаты: у заданий-жертв усиление стоит доплаты, и
 * платить её, не собираясь брать усиленную награду, незачем. Поэтому движок
 * предлагает две отдельные возможности — «выполнить» и «выполнить усиленно», —
 * а не спрашивает после.
 *
 * <p>Ключ свода {@code objectives.enhanced_reward_replaces_base}. Выключенный
 * возвращает прежнее сложение: это нужно, чтобы мерить правку отдельно.
 *
 * <p>У карт, где блока {@code enhanced} нет вовсе (начальные задания), развилки
 * нет: обе награды выдаются вместе, как и раньше.
 */
public final class Objectives {

    private Objectives() {
    }

    @SuppressWarnings("unchecked")
    private static boolean requirementMet(GameState s, int seat, TurnJournal j, Map<String, Object> spec) {
        if (spec == null) {
            return false;
        }
        Object pidObj = spec.get("predicate");
        if (pidObj == null) {
            return false;
        }
        String pid = pidObj.toString();
        if (!Predicates.isRegistered(pid)) {
            return false;
        }
        Map<String, Object> params = spec.get("params") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        try {
            return Predicates.check(pid, s, seat, j, params);
        } catch (Predicates.PredicateError e) {
            return false;
        }
    }

    /**
     * Выполнено ли требование карты, УСТРОЙСТВО КОТОРОЙ ЖИВЁТ В КОДЕ
     * ({@code checked_by: card}, модуль {@code cards} — заказ дизайнера
     * 15.08.2026, «карты как объекты»).
     *
     * <p>НАЙДЕНО 16.08.2026: до этой правки такая карта никогда не считалась
     * выполненной — {@link #requirementMet} проверяет id предиката против
     * СТАРОГО реестра {@link Predicates}, а условия карт с {@code checked_by:
     * card} в нём не регистрируются (они читаются кодом карты, не строкой).
     * Замер: 4 военные карты каталога 1.7.0 (o41–o44) — 0 выполнений и 584
     * сожжения за 150 партий. Молчит (false), если код карты ещё не привязан
     * ({@link CardRegistry#find} вернул null) — то же поведение, что раньше у
     * карты с незарегистрированным предикатом, ничего не падает.
     */
    private static boolean cardRequirementMet(GameState s, int seat, String cid, boolean enhanced) {
        kelium.engine.cards.ObjectiveCard oc = kelium.engine.cards.CardRegistry.objective(cid);
        if (oc == null) {
            return false;
        }
        kelium.engine.cards.CardContext ctx = new kelium.engine.cards.EngineCardContext(s, seat);
        return enhanced ? oc.satisfiedEnhanced(ctx) : oc.satisfied(ctx);
    }

    /**
     * Сколько единиц данного вида жертвы игрок может оплатить прямо сейчас.
     * -1 = вид жертвы неизвестен (карта не играбельна, не бесплатна!).
     */
    private static int sacrificeCapacity(GameState s, PlayerState p, String res) {
        switch (res) {
            case "container":
                return p.containers;
            case "objective_cards":
                // сама разыгрываемая карта ещё в руке — её сдать нельзя
                return Math.max(0, p.objectiveHand.size() - 1);
            case "units_off_base":
                // o10 «Разоружение»: войска (не вышки) вне гексов своих зданий,
                // «с разных гексов» — считаем РАЗНЫЕ гексы с такими войсками
                return unitsOffBaseHexes(s, p).size();
            case "trophies":
                // o22 «Зачистка»: трофейные ЖЕТОНЫ возвращаются владельцам мимо
                // Науки — очков они не приносят, в этом и цена.
                return p.destroyedTokens.size();
            case "units_on_field":
                // ЛЮБЫЕ свои войска, стоящие на поле, включая вышки: карта
                // просит вернуть жетоны в запас, а не увести их с позиций.
                return p.unitsOnField().size();
            case "arsenal_cards":
                // Карты арсенала в руке. Установленные не считаются: они уже
                // работают, и снимать работающую способность ради задания —
                // другая сделка, чем расстаться с ещё не сыгранной картой.
                return p.arsenalHand.size();
            case "buildings_off_cu":
                // o47 «Демонтаж»: своё здание уходит в запас БЕЗ компенсации
                // (обычный снос даёт монету, здесь не даёт).
                return ownBuildingsOffCu(p).size();
            default:
                try {
                    Resource r = Resource.fromCode(res);
                    return p.resources.get(r);
                } catch (RuntimeException e) {
                    return -1;
                }
        }
    }

    /** Гексы, где стоят войска (не вышки) игрока ВНЕ его гексов со зданиями. */
    private static List<String> unitsOffBaseHexes(GameState s, PlayerState p) {
        java.util.Set<String> own = new java.util.HashSet<>();
        for (kelium.core.BuildingToken b : p.buildingsOnField()) {
            own.add(b.hexId);
        }
        java.util.Set<String> hexes = new java.util.LinkedHashSet<>();
        for (kelium.core.UnitToken u : p.unitsOnField()) {
            if (u.type != kelium.core.UnitType.TOWER && !own.contains(u.hexId)) {
                hexes.add(u.hexId);
            }
        }
        return new ArrayList<>(hexes);
    }

    /**
     * ДВА ЖЕТОНА ТЕХНИКИ И/ИЛИ АВИАЦИИ С ОДНОГО ГЕКСА — то, за что платит
     * усиление «Разоружения». Пусто, если такой пары на поле нет.
     */
    private static List<kelium.core.UnitToken> ударнаяПара(PlayerState p) {
        java.util.Map<String, List<kelium.core.UnitToken>> поГексам =
            new java.util.LinkedHashMap<>();
        for (kelium.core.UnitToken u : p.unitsOnField()) {
            if (u.type == kelium.core.UnitType.VEHICLE
                    || u.type == kelium.core.UnitType.AIRCRAFT) {
                поГексам.computeIfAbsent(u.hexId, k -> new ArrayList<>()).add(u);
            }
        }
        for (List<kelium.core.UnitToken> группа : поГексам.values()) {
            if (группа.size() >= 2) {
                return new ArrayList<>(группа);
            }
        }
        return new ArrayList<>();
    }

    /** Свои здания на поле, кроме ЦУ — их можно сдать в жертву (o47). */
    private static List<kelium.core.BuildingToken> ownBuildingsOffCu(PlayerState p) {
        List<kelium.core.BuildingToken> out = new ArrayList<>();
        for (kelium.core.BuildingToken b : p.buildingsOnField()) {
            if (b.type != kelium.core.BuildingType.COMMAND_CENTER) {
                out.add(b);
            }
        }
        return out;
    }

    /** Оплатить amt единиц жертвы вида res (проверка ёмкости уже сделана). */
    private static void paySacrifice(GameState s, PlayerState p, String res, int amt,
                                     String playedCid) {
        switch (res) {
            case "container" -> p.containers = Math.max(0, p.containers - amt);
            case "objective_cards" -> {
                int left = amt;
                List<String> hand = new ArrayList<>(p.objectiveHand);
                for (String other : hand) {
                    if (left == 0) {
                        break;
                    }
                    if (other.equals(playedCid)) {
                        continue;
                    }
                    p.objectiveHand.remove(other);
                    s.decks.get("objectives").discard(other);
                    left--;
                }
            }
            case "units_off_base" -> {
                // снять по одному войску с amt РАЗНЫХ гексов; сданное войско
                // возвращается в резерв (не считается уничтоженным)
                int left = amt;
                for (String hid : unitsOffBaseHexes(s, p)) {
                    if (left == 0) {
                        break;
                    }
                    for (kelium.core.UnitToken u : p.unitsOnField()) {
                        if (hid.equals(u.hexId) && u.type != kelium.core.UnitType.TOWER) {
                            u.setHexId(null);   // сдан в жертву — и вышел из здания
                            u.resetDamage();
                            left--;
                            break;
                        }
                    }
                }
            }
            case "units_on_field" -> {
                // ПОРЯДОК СДАЧИ НЕ СЛУЧАЕН: сперва пара техники или авиации С
                // ОДНОГО ГЕКСА — ровно то, за что платит усиление карты. Тот же
                // приём, что у «трофеев», где первым уходит здание: жадный
                // «первый попавшийся» сдал бы пехоту и лишил игрока усиления,
                // которое он честно заслужил.
                List<kelium.core.UnitToken> порядок = ударнаяПара(p);
                boolean пара = порядок.size() >= amt;
                for (kelium.core.UnitToken u : p.unitsOnField()) {
                    if (!порядок.contains(u)) {
                        порядок.add(u);
                    }
                }
                int left = amt;
                for (kelium.core.UnitToken u : порядок) {
                    if (left == 0) {
                        break;
                    }
                    u.setHexId(null);
                    u.resetDamage();
                    left--;
                }
                if (пара && s.journal != null) {
                    s.journal.of(p.seat).sacrificedStrikeGroup = true;
                }
            }
            case "arsenal_cards" -> {
                int left = amt;
                List<String> рука = new ArrayList<>(p.arsenalHand);
                for (String карта : рука) {
                    if (left == 0) {
                        break;
                    }
                    p.arsenalHand.remove(карта);
                    s.decks.get("arsenal").discard(карта);
                    left--;
                }
            }
            case "trophies" -> {
                // o22 «Зачистка»: сдаём уничтоженные жетоны ВЛАДЕЛЬЦАМ. Первым уходит
                // ЗДАНИЕ — карта требует именно его, и жадный «самый дешёвый»
                // выбор здесь врал бы: он сдал бы пехоту, а здание осталось.
                int left = amt;
                List<kelium.core.Token> order = new ArrayList<>();
                for (kelium.core.Token t : p.destroyedTokens) {
                    if (t instanceof kelium.core.BuildingToken) {
                        order.add(t);
                    }
                }
                for (kelium.core.Token t : p.destroyedTokens) {
                    if (!(t instanceof kelium.core.BuildingToken)) {
                        order.add(t);
                    }
                }
                for (kelium.core.Token t : order) {
                    if (left == 0) {
                        break;
                    }
                    p.destroyedTokens.remove(t);
                    t.setCapturedBy(null);
                    t.resetDamage();
                    t.setHexId(null);
                    // ЖЕТОН ВЕРНУЛСЯ ВЛАДЕЛЬЦУ — И ЛЁГ НА ЕГО ПЛАНШЕТ ХРАНИЛИЩА,
                    // ЗАКРЫВ ЯЧЕЙКИ. Добытчик и энергостанция открывают ячейки
                    // склада, пока стоят на поле или лежат чужим трофеем; вернувшись
                    // в запас, они накрывают их собой, и то, что в них лежало, обязано
                    // сгореть — то же правило, что при обычном возврате здания.
                    //
                    // Здесь этого не делалось, и склад ВЛАДЕЛЬЦА (не игрока, сдающего
                    // жертву!) оставался переполненным: поймано сторожем
                    // StorageNeverOverflowsTest — «занято 5 при 4 ячейках» у соседа
                    // после того, как карта o22 вернула ему здание.
                    //
                    // ownTurnChoice=false: владелец в этот момент не действует, свой
                    // ход не его, — значит и выбирать, что сгорит, ему не дают.
                    if (t instanceof kelium.core.BuildingToken bt
                            && (bt.type == kelium.core.BuildingType.MINER
                                || bt.type == kelium.core.BuildingType.POWER_PLANT)) {
                        Storage.evictOnBuildingReturn(s, s.player(bt.owner()), false);
                    }
                    left--;
                }
            }
            case "buildings_off_cu" -> {
                // o47 «Демонтаж»: здание уходит в запас, компенсации НЕТ.
                int left = amt;
                for (kelium.core.BuildingToken b : ownBuildingsOffCu(p)) {
                    if (left == 0) {
                        break;
                    }
                    Actions.returnOwnBuildingToReserve(s, p, b, true);
                    left--;
                }
            }
            default -> p.resources.pay(Resource.fromCode(res), amt);
        }
    }

    /**
     * МОЖЕТ ЛИ ИГРОК ОПЛАТИТЬ ЖЕРТВУ этой карты прямо сейчас.
     *
     * <p>Публично, потому что это и есть УСЛОВИЕ карты-жертвы: предикат у неё
     * {@code sacrifice_paid}, который всегда истинен, а настоящая проверка —
     * «есть ли чем заплатить». Без доступа сюда карта-объект отвечала бы
     * «выполнено» на пустом столе.
     */
    public static boolean canPaySacrifice(GameState s, int seat, Map<String, Object> card) {
        return canPaySacrifice(s, s.player(seat), card);
    }

    private static boolean canPaySacrifice(GameState s, PlayerState p, Map<String, Object> card) {
        Object sacObj = card.get("sacrifice");
        if (!(sacObj instanceof Map<?, ?> sac)) {
            return true;
        }
        Object res = sac.get("resource");
        int amt = sac.get("amount") instanceof Number n ? n.intValue() : 0;
        if (res == null) {
            return false;
        }
        int cap = sacrificeCapacity(s, p, res.toString());
        return cap >= amt;
    }

    /** Идентификаторы заданий в руке, чьё БАЗОВОЕ требование сейчас выполнено. */
    @SuppressWarnings("unchecked")
    public static List<String> playableObjectives(GameState s, int seat, TurnJournal j) {
        PlayerState p = s.player(seat);
        var content = Ctx.cards(s, "objectives");
        List<String> out = new ArrayList<>();
        for (String cid : p.objectiveHand) {
            Map<String, Object> card;
            try {
                card = content.byId(cid);
            } catch (RuntimeException e) {
                continue;
            }
            Object req = card.get("requirement");
            if (!(req instanceof Map<?, ?>)) {
                continue;
            }
            if (!canPaySacrifice(s, p, card)) {
                continue;
            }
            boolean met = "card".equals(card.get("checked_by"))
                ? cardRequirementMet(s, seat, cid, false)
                : requirementMet(s, seat, j, (Map<String, Object>) req);
            if (met) {
                out.add(cid);
            }
        }
        return out;
    }

    /**
     * ДОСТУПНО ЛИ УСИЛЕНИЕ этой карты прямо сейчас — то есть надо ли предлагать
     * игроку вторую возможность, «выполнить усиленно».
     *
     * <p>Для жертвы усиление доступно, когда игроку есть чем доплатить разницу;
     * для остальных — когда выполнено усиленное требование. У карты без блока
     * {@code enhanced} усиления нет: развилки не будет.
     */
    @SuppressWarnings("unchecked")
    public static boolean enhancedAvailable(GameState s, int seat, TurnJournal j, String cid) {
        Map<String, Object> card = Ctx.cards(s, "objectives").byId(cid);
        if (card == null) {
            return false;
        }
        Object enh = card.get("enhanced");
        if (!(enh instanceof Map<?, ?> enhMap)) {
            return false;
        }
        if ("sacrifice_enhanced".equals(enhMap.get("predicate"))) {
            Object sacObj = card.get("sacrifice");
            if (!(sacObj instanceof Map<?, ?> sac) || sac.get("resource") == null) {
                return false;
            }
            int sacBase = sac.get("amount") instanceof Number n ? n.intValue() : 0;
            Object ep = enhMap.get("params");
            int enhAmt = ep instanceof Map<?, ?> em && em.get("amount") instanceof Number en
                ? en.intValue() : sacBase;
            int diff = enhAmt - sacBase;
            return diff <= 0
                || sacrificeCapacity(s, s.player(seat), sac.get("resource").toString()) >= diff;
        }
        return "card".equals(card.get("checked_by"))
            ? cardRequirementMet(s, seat, cid, true)
            : requirementMet(s, seat, j, (Map<String, Object>) enh);
    }

    /** Завершить задание базовой наградой. */
    public static Map<String, Object> playObjective(GameState s, int seat, TurnJournal j,
                                                     String cid, Consumer<Map<String, Object>> emit) {
        return playObjective(s, seat, j, cid, emit, false);
    }

    /**
     * Завершить задание: оплатить жертву и выдать награду.
     *
     * @param усиленно взять УСИЛЕННУЮ награду вместо базовой (и доплатить, если
     *                 усиление карты — доплата жертвы)
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> playObjective(GameState s, int seat, TurnJournal j,
                                                     String cid, Consumer<Map<String, Object>> emit,
                                                     boolean усиленно) {
        PlayerState p = s.player(seat);
        var content = Ctx.cards(s, "objectives");
        Map<String, Object> card = content.byId(cid);

        // E6: жертва оплачивается ПО-НАСТОЯЩЕМУ (карта без оплаты не играется —
        // playableObjectives уже отфильтровал неоплатные).
        String sacRes = null;
        int sacBase = 0;
        Object sacObj = card.get("sacrifice");
        if (sacObj instanceof Map<?, ?> sac) {
            Object res = sac.get("resource");
            sacBase = sac.get("amount") instanceof Number n ? n.intValue() : 0;
            if (res != null) {
                sacRes = res.toString();
                paySacrifice(s, p, sacRes, sacBase, cid);
            }
        }

        Map<String, Object> base = new HashMap<>();
        Map<String, Object> special = new HashMap<>();

        // ВМЕСТО ИЛИ СВЕРХ. По правилу 16.09.2026 усиленная награда заменяет
        // базовую, и тогда выдаётся ровно одна из двух. Ключ свода оставлен,
        // чтобы прежнее сложение можно было померить отдельно.
        boolean вместо = Boolean.TRUE.equals(Ctx.rules(s)
                .get("objectives.enhanced_reward_replaces_base", Boolean.TRUE));

        boolean enhancedOk = false;
        Object enh = card.get("enhanced");
        if (enh == null) {
            // У НАЧАЛЬНЫХ карт усиления нет вовсе (каталог: «без усиления и
            // верха»), и их награда лежит в special_reward. Без этой ветки она
            // не выдавалась НИКОГДА — все восемь начальных заданий выполнялись
            // впустую. Нет блока enhanced => развилки нет, выдаётся всё.
            enhancedOk = true;
            усиленно = false;
        } else if (усиленно || !вместо) {
            if (enh instanceof Map<?, ?> enhMap && "sacrifice_enhanced".equals(enhMap.get("predicate"))
                    && sacRes != null) {
                // Усиленная жертва: ДОПЛАТА разницы до усиленной суммы (а не
                // фантомная проверка «можешь ли»).
                Object ep = enhMap.get("params");
                int enhAmt = ep instanceof Map<?, ?> em && em.get("amount") instanceof Number en
                    ? en.intValue() : sacBase;
                int diff = enhAmt - sacBase;
                if (diff <= 0) {
                    enhancedOk = true;
                } else if (sacrificeCapacity(s, p, sacRes) >= diff) {
                    paySacrifice(s, p, sacRes, diff, cid);
                    enhancedOk = true;
                }
            } else if (enh instanceof Map<?, ?> && ("card".equals(card.get("checked_by"))
                    ? cardRequirementMet(s, seat, cid, true)
                    : requirementMet(s, seat, j, (Map<String, Object>) enh))) {
                enhancedOk = true;
            }
        }
        // ЗАПРОСИЛИ УСИЛЕНИЕ, А ОНО НЕ ВЫШЛО — карта всё равно выполняется, но
        // по базовой награде: игрок не должен остаться ни с чем из-за того, что
        // между предложением и розыгрышем что-то изменилось.
        boolean толькоУсиленная = вместо && усиленно && enhancedOk;
        if (!толькоУсиленная) {
            grantBase(s, p, (Map<String, Object>) card.getOrDefault("base_reward", Map.of()), base);
        }
        if (enhancedOk && (!вместо || усиленно)) {
            grantSpecial(s, p, (Map<String, Object>) card.getOrDefault("special_reward", Map.of()), special);
        }

        p.objectiveHand.remove(cid);
        s.decks.get("objectives").discard(cid);
        p.objectivesCompleted += 1;   // накопитель «Архива штаба» (супер 5.0)

        Map<String, Object> granted = new HashMap<>();
        granted.put("base", base);
        granted.put("special", special);
        Map<String, Object> ev = new HashMap<>();
        ev.put("type", "objective");
        ev.put("seat", seat);
        ev.put("card", cid);
        ev.put("enhanced", enhancedOk && (!вместо || усиленно));
        ev.put("enhanced_instead", толькоУсиленная);
        ev.put("round", s.round);
        ev.put("granted", granted);
        emit.accept(ev);
        return granted;
    }

    private static void grantBase(GameState s, PlayerState p, Map<String, Object> reward,
                                  Map<String, Object> into) {
        for (var e : reward.entrySet()) {
            int n = e.getValue() instanceof Number num ? num.intValue() : 0;
            switch (e.getKey()) {
                case "coin" -> {
                    p.resources.add(Resource.COIN, n);
                    into.merge("coin", n, (a, b) -> ((Number) a).intValue() + ((Number) b).intValue());
                }
                case "ammo" -> {
                    int added = Storage.addAmmoCapped(s, p, n);
                    into.merge("ammo", added, (a, b) -> ((Number) a).intValue() + ((Number) b).intValue());
                }
                case "container" -> {
                    // Каталог 10.0 контейнеров в наградах не выдаёт вовсе (правило
                    // дизайнера 17.08.2026), но ветка остаётся: старые версии
                    // каталога должны продолжать работать без правки данных.
                    int addedC = Storage.addContainersCapped(s, p, n, "награда задания");
                    into.merge("container", addedC, (a, b) -> ((Number) a).intValue() + ((Number) b).intValue());
                }
                // НАЧАЛЬНЫЕ ЗАДАНИЯ 10.0 платят трофеем и картой задания, а
                // усиления у них нет вовсе — значит эта награда лежит в БАЗОВОЙ и
                // выдаваться должна отсюда.
                case "trophy" -> {
                    int addedD = Storage.addTrophyCapped(s, p, n);
                    into.merge("trophy", addedD, (a, b) -> ((Number) a).intValue() + ((Number) b).intValue());
                }
                case "objective_card", "objective_cards" -> {
                    int drawn = 0;
                    for (int i = 0; i < n; i++) {
                        String c = s.decks.get("objectives").draw(s.rng);
                        if (c == null) {
                            break;
                        }
                        p.objectiveHand.add(c);
                        drawn++;
                    }
                    into.merge("objective_card", drawn,
                        (a, b) -> ((Number) a).intValue() + ((Number) b).intValue());
                }
                // === НАГРАДА-ДЕЙСТВИЕ (ревизия заданий 18.09.2026) ===
                //
                // Дизайнер: «не ресурсы ебаные давать заданием, а именно
                // действия — те, которые ты не успел отыграть». Выполнил
                // задание — играешь полноценное действие прямо сейчас, сверх
                // двух своих приказов. Ресурс остаётся только в усиленной
                // награде, и она теперь берётся ВМЕСТЕ с базовой.
                //
                // Почему через freeAction, а не своей веткой: это ровно тот же
                // подарок, что раздаёт утиль и карты арсенала, — со своим
                // контекстом, своим журналом и своей телеметрией. Вторая правда
                // о бесплатном действии здесь никому не нужна.
                case "action" -> {
                    // ВЫБОР ОДНОГО ИЗ ДВУХ ДЕЙСТВИЙ (решение дизайнера 22.09.2026).
                    // Награда кодирует пару через «|» («combat|energy_swap»):
                    // игрок выбирает, какое сыграть. Одиночное действие (без «|»)
                    // играется как раньше.
                    String имя = String.valueOf(e.getValue());
                    if (имя.contains("|")) {
                        String[] пара = имя.split("\\|", 2);
                        имя = выбратьДействиеНаграды(s, p.seat, пара[0], пара[1]);
                    }
                    Map<String, Object> итог = Effects.freeAction(s, p.seat,
                        java.util.Map.of("action", имя));
                    into.put("action", имя);
                    into.put("action_ran", итог.get("ran"));
                    // ЧТО ДЕЙСТВИЕ НАСЧИТАЛО — наружу целиком. Награда может
                    // «состояться» и не дать ничего: Бой без залпа, Стройка без
                    // операции, Рынок без сделки. Замер пользы считается по этим
                    // числам, а не по признаку «сыграно».
                    if (итог.get("telemetry") instanceof Map<?, ?> тел) {
                        into.put("action_telemetry", тел);
                    }
                }
                // ПОЗОЛОЧЕНИЕ МОДУЛЯ — восьмая награда ревизии. Не действие, но и
                // не ресурс: это прогресс, который дизайнер просил в награды
                // отдельно. Золотить нечего — награда пропадает, как всякая
                // недоступная (то же делает вершина зелёного трека).
                // ДВА СПЕЦ-ДЕЙСТВИЯ (решение дизайнера 19.09.2026, вместо
                // позолоты). Спец-действие — самый дефицитный ресурс хода: оно
                // одно, и через него идут и задания, и карты арсенала. Замер
                // показал, что за ход выполняется максимум ОДНО задание, ходов
                // с двумя не бывает вовсе; эта награда снимает ровно то горлышко.
                //
                // Прибавка кладётся в журнал хода, как и у одноимённого утиля
                // арсенала: предел спец-действий считается на входе в ход, а
                // награда приходит в середине.
                case "spec_actions" -> {
                    int сколько = e.getValue() instanceof Number сп ? сп.intValue() : 2;
                    s.journal.of(p.seat).specBonus += Math.max(0, сколько);
                    into.put("spec_actions", сколько);
                }
                case "gild" -> {
                    Agent агент = s.agents == null || p.seat >= s.agents.size()
                        ? null : s.agents.get(p.seat);
                    boolean позолотил = Modules.gildOne(s, p, агент);
                    into.put("gild", позолотил ? 1 : 0);
                }
                default -> { }
            }
        }
    }

    /**
     * ВЫБОР ОДНОГО ИЗ ДВУХ ДЕЙСТВИЙ НАГРАДЫ. Спрашивает агента; если агента нет
     * (тест, авторасстановка) — берёт первое. Возвращает код выбранного действия.
     */
    private static String выбратьДействиеНаграды(GameState s, int seat, String a, String b) {
        Agent агент = s.agents == null || seat >= s.agents.size()
            ? null : s.agents.get(seat);
        if (агент == null) {
            return a;
        }
        java.util.List<kelium.core.Choice> opts = java.util.List.of(
            new kelium.core.Choice("reward_action", a, a),
            new kelium.core.Choice("reward_action", b, b));
        kelium.core.Choice ch = агент.choose(s, opts,
            java.util.Map.of("kind", "objective_reward_action"));
        return ch != null && ch.payload() instanceof String выбр ? выбр : a;
    }

    private static void grantSpecial(GameState s, PlayerState p, Map<String, Object> reward,
                                     Map<String, Object> into) {
        for (var e : reward.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            int n = v instanceof Number num ? num.intValue() : 0;
            switch (k) {
                case "kelium" -> {
                    int added = Storage.addKeliumCapped(s, p, n);
                    into.put("kelium", added);
                }
                case "trophy" -> {
                    int added = Storage.addTrophyCapped(s, p, n);
                    into.put("trophy", added);
                }
                case "module" -> {
                    // Жетоны-награды: attack -> красный (атака), остальное -> синий
                    // (сборка). Жетон ХРАНИЛИЩА наградой НЕ бывает (правило 2026-08-11:
                    // только зелёный трек) — старые записи storage считаем сборкой.
                    String kind = String.valueOf(v);
                    switch (kind) {
                        case "attack" -> p.redModules += 1;
                        default -> p.blueModules += 1;   // assembly и прочее
                    }
                    into.put("module", v);
                }
                case "storage_token" -> {
                    // o13 «Расчистка» (решение 8.0): жетон хранилища как особая
                    // награда; слотов на планшете два — лишний жетон пропадает.
                    int ячеек = Ctx.rules(s).get("storage.module_slots", null)
                        instanceof Number сколько ? сколько.intValue() : 3;
                    if (p.storageTokens.size() < ячеек) {
                        p.storageTokens.add("+1_universal_cell");
                        into.put("storage_token", 1);
                    }
                }
                case "arsenal" -> {
                    String c = s.decks.get("arsenal").draw(s.rng);
                    if (c != null) {
                        kelium.engine.Storage.takeArsenalCard(s, p, c);
                    }
                    into.put("arsenal", 1);
                }
                case "arsenal_from_display" -> {
                    // КАРТА С ВИТРИНЫ — ВЫБОР ИЗ ДВУХ ОТКРЫТЫХ, а не слепая тяга
                    // (правило дизайнера 21.08.2026). Дороже обычной карты
                    // арсенала именно этим, поэтому и стоит на самых трудных
                    // заданиях. Витрина сразу пополняется с верха колоды.
                    int taken = 0;
                    for (int i = 0; i < Math.max(1, n); i++) {
                        String c = kelium.engine.Actions.takeFromArsenalDisplay(s, p,
                            s.agents == null || p.seat >= s.agents.size()
                                ? null : s.agents.get(p.seat));
                        if (c == null) {
                            break;
                        }
                        taken++;
                    }
                    into.put("arsenal_from_display", taken);
                }
                case "objective_card" -> {
                    int drawn = 0;
                    for (int i = 0; i < Math.max(1, n); i++) {
                        String c = s.decks.get("objectives").draw(s.rng);
                        if (c == null) {
                            break;
                        }
                        p.objectiveHand.add(c);
                        drawn++;
                    }
                    into.put("objective_card", drawn);
                }
                // Каталог 10.0 разрешает расходники и в усиленной награде
                // (o34 «Научный отдел» платит монетами) — правило «особая награда
                // только очковая» снято дизайнером 17.08.2026.
                case "coin" -> {
                    p.resources.add(Resource.COIN, n);
                    into.put("coin", n);
                }
                case "ammo" -> {
                    into.put("ammo", Storage.addAmmoCapped(s, p, n));
                }
                default -> { }
            }
        }
    }
}
