package kelium.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
 * по состоянию + журналу). При розыгрыше базовая награда даётся всегда
 * (расходники — правило цепочки); если выполнено и усиленное требование,
 * дополнительно даётся особая награда (очковая вещь + свежая карта задания) —
 * награды СКЛАДЫВАЮТСЯ. Задания-жертвы (Ж) сначала платят цену.
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

    /** Завершить задание: оплатить жертву, выдать базовую (+особую) награду. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> playObjective(GameState s, int seat, TurnJournal j,
                                                     String cid, Consumer<Map<String, Object>> emit) {
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
        grantBase(s, p, (Map<String, Object>) card.getOrDefault("base_reward", Map.of()), base);

        boolean enhancedOk = false;
        Object enh = card.get("enhanced");
        if (enh instanceof Map<?, ?> enhMap && "sacrifice_enhanced".equals(enhMap.get("predicate"))
                && sacRes != null) {
            // Усиленная жертва: ДОПЛАТА разницы до усиленной суммы (а не
            // фантомная проверка «можешь ли»). Бот доплачивает всегда, когда может.
            Object ep = enhMap.get("params");
            int enhAmt = ep instanceof Map<?, ?> em && em.get("amount") instanceof Number en
                ? en.intValue() : sacBase;
            int diff = enhAmt - sacBase;
            if (diff > 0 && sacrificeCapacity(s, p, sacRes) >= diff) {
                paySacrifice(s, p, sacRes, diff, cid);
                enhancedOk = true;
            }
        } else if (enh instanceof Map<?, ?> && ("card".equals(card.get("checked_by"))
                ? cardRequirementMet(s, seat, cid, true)
                : requirementMet(s, seat, j, (Map<String, Object>) enh))) {
            enhancedOk = true;
        } else if (enh == null) {
            // У НАЧАЛЬНЫХ карт усиления нет вовсе (каталог: «без усиления и
            // верха»), и их награда лежит в special_reward. Без этой ветки она
            // не выдавалась НИКОГДА — все восемь начальных заданий выполнялись
            // впустую. Нет блока enhanced => награда положена за выполнение.
            enhancedOk = true;
        }
        if (enhancedOk) {
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
        ev.put("enhanced", enhancedOk);
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
                default -> { }
            }
        }
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
                    if (p.storageTokens.size() < 2) {
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
