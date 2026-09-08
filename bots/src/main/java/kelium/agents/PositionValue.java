package kelium.agents;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.CombatResolver;
import kelium.engine.ObjectiveHints;
import kelium.engine.Passives;
import kelium.engine.Scoring;
import kelium.engine.Storage;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.EngineCardContext;

/**
 * ОЦЕНКА ПОЗИЦИИ ДЛЯ ПЛАНИРОВЩИКА — «насколько мне хорошо после этого хода».
 *
 * <p>Единица измерения — ПОБЕДНОЕ ОЧКО. Всё, что не очки, переводится в очки по
 * тому, во что оно превращается за столом: трофей — в шаг науки, монета — в
 * здание, войско — в удары и трофеи, урон на чужом жетоне — в его снос.
 *
 * <p>Читается только ОТКРЫТАЯ информация плюс собственная рука. Руки соперников
 * не читаются: оценка честная, как у игрока за столом.
 *
 * <p>Веса — по ключам {@code pl.*} генома, у каждого характера свои. Порядок
 * важности задан здесь, характер лишь наклоняет.
 */
public final class PositionValue {

    private PositionValue() {
    }

    /** Разложение оценки по статьям — для отладки и отчётов. */
    public static final class Breakdown {
        public final Map<String, Double> parts = new java.util.LinkedHashMap<>();
        public double total;

        void add(String name, double v) {
            if (v != 0) {
                parts.merge(name, v, Double::sum);
            }
            total += v;
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder(String.format(java.util.Locale.ROOT,
                "%.2f {", total));
            for (var e : parts.entrySet()) {
                sb.append(e.getKey()).append('=')
                    .append(String.format(java.util.Locale.ROOT, "%.2f ", e.getValue()));
            }
            return sb.append('}').toString();
        }
    }

    public static double value(GameState s, int seat, Genome w, Intents in) {
        return breakdown(s, seat, w, in).total;
    }

    public static Breakdown breakdown(GameState s, int seat, Genome w, Intents in) {
        Breakdown b = new Breakdown();
        PlayerState me = s.player(seat);
        double late = lateness(s);              // 0 в начале партии, 1 в конце
        double early = 1.0 - late;

        // ---- 1. Очки на столе (включая множитель супер-задания) -------------
        int vp = Scoring.scorePlayer(s, seat).getOrDefault("total", 0);
        b.add("vp", vp * w.get("pl.vp", 1.0));
        // Отрыв: игра многопользовательская — важно не «сколько у меня», а
        // «насколько я впереди сильнейшего».
        int rivalMax = Integer.MIN_VALUE;
        for (PlayerState p : s.players) {
            if (p.seat != seat) {
                rivalMax = Math.max(rivalMax, Scoring.scorePlayer(s, p.seat).getOrDefault("total", 0));
            }
        }
        if (rivalMax != Integer.MIN_VALUE) {
            b.add("margin", (vp - rivalMax) * w.get("pl.margin", 0.35) * (0.5 + late));
        }

        // ---- 2. Задания -----------------------------------------------------
        double objW = w.get("pl.objective", 1.0);
        EngineCardContext cardCtx = new EngineCardContext(s, seat);
        double objSum = 0;
        for (String cid : me.objectiveHand) {
            var card = CardRegistry.objective(cid);
            if (card == null) {
                objSum += 0.4;
                continue;
            }
            double reward = rewardValue(s, seat, cid);       // в «ресурсных единицах»
            double vpReward = reward / 4.0;                  // 4 единицы ≈ очко
            double focus = in != null && cid.equals(in.focusObjective) ? 1.35 : 1.0;
            // ПРОИСШЕСТВИЕ ЖИВЁТ ОДИН ХОД, И ЭТО МЕНЯЕТ ВСЮ ЦЕНУ КАРТЫ.
            //
            // Требование-происшествие («В ЭТОТ ХОД уничтожь…») проверяется по
            // журналу хода, а журнал обнуляется в начале следующего. Значит
            // «условие выполнено, но карта не сыграна» — это не отложенная
            // выгода, а НИЧТО: к своему следующему ходу от неё не останется
            // ничего. Оценка же считала такую карту почти состоявшейся наградой
            // и тем самым уговаривала планировщика не тратить на неё СПЕЦ —
            // очки он себе уже начислил. Поэтому у происшествий платит только
            // фактическое выполнение (ниже, objectives_done), а на руке они
            // стоят одну возможность.
            if (происшествие(s, cid)) {
                objSum += 0.2 * vpReward * focus;
                continue;
            }
            double prog = clamp(card.progress(cardCtx));
            boolean ready = card.satisfied(cardCtx);
            // Готовая карта стоит часть награды (её ещё надо разыграть — СПЕЦ
            // один на ход); полуготовая — долю: сжечь её значит выбросить
            // сделанное, поэтому прогресс платит почти линейно.
            double v = ready ? 0.75 * vpReward : vpReward * (0.15 + 0.85 * prog);
            objSum += v * focus;
        }
        b.add("objectives", objSum * objW);
        // ВЫПОЛНЕННОЕ ЗАДАНИЕ — событие партии, а не только ресурсы: за него
        // платят усилением (модуль, арсенал, трофеи), рука пополняется, а
        // сожжённая карта этого не даёт. Поэтому факт выполнения ценится сам —
        // выше стоимости среднего утиля, иначе жечь всегда выгоднее.
        b.add("objectives_done", me.objectivesCompleted * 1.4 * objW);

        // ---- 3. Арсенал -----------------------------------------------------
        double arsW = w.get("pl.arsenal", 1.0);
        double arsSum = 0;
        for (String cid : me.allInstalledArsenal()) {
            arsSum += 0.6 + 1.6 * usefulness(s, seat, cid, true);   // очко уже в vp
        }
        for (String cid : me.arsenalHand) {
            arsSum += 0.5 + 0.8 * Math.max(usefulness(s, seat, cid, true),
                usefulness(s, seat, cid, false));
        }
        b.add("arsenal", arsSum * arsW);
        b.add("containers", me.containers * 0.35 * arsW);

        // ---- 4. Хозяйство ---------------------------------------------------
        double ecoW = w.get("pl.economy", 1.0);
        int coin = me.resources.coin();
        int kel = me.resources.kelium();
        int ammo = me.resources.ammo();
        int trophy = me.resources.trophy() + me.destroyedTokens.size();
        b.add("coin", (Math.min(coin, 8) * 0.22 + Math.max(0, coin - 8) * 0.06) * ecoW * (0.5 + early));
        b.add("kelium", (Math.min(kel, 6) * 0.65 + Math.max(0, kel - 6) * 0.2) * ecoW);
        b.add("trophy", (Math.min(trophy, 10) * 0.8 + Math.max(0, trophy - 10) * 0.3)
            * w.get("pl.trophy", 1.0));

        Set<String> live = Plan.liveTileHexes(s);
        int minersWorking = 0;
        int hungry = 0;
        int milPowered = 0;
        int plants = 0;
        int buildings = 0;
        double milCap = 0;
        for (BuildingToken bt : me.buildingsOnField()) {
            buildings++;
            hungry += Math.max(0, bt.energySlots - bt.energyPlaced);
            switch (bt.type) {
                case MINER -> {
                    if (bt.powered() && Plan.touchesLiveTile(s, bt.hexId, live)) {
                        minersWorking += bt.level != null && bt.level >= 3 ? 2 : 1;
                    }
                }
                case POWER_PLANT -> plants++;
                case BARRACKS, FACTORY, AIRBASE -> {
                    if (bt.powered()) {
                        milPowered++;
                    }
                    milCap += bt.type == BuildingType.BARRACKS ? 0.8 : 1.0;
                }
                default -> { }
            }
            if (bt.damage > 0) {
                b.add("own_damage", -0.4 * bt.damage);
            }
        }
        b.add("miners", minersWorking * 1.1 * ecoW * (0.4 + early));
        b.add("plants", plants * 0.45 * ecoW * (0.4 + early));
        b.add("buildings", buildings * 0.3 * ecoW);
        b.add("energy_hungry", -hungry * 0.28 * ecoW);
        int room = Storage.roomFor(s, me, kelium.core.Resource.KELIUM);
        b.add("storage_room", Math.min(room, 4) * 0.12 * ecoW);
        if (!me.hasCommandCenter()) {
            b.add("cu_lost", -3.0);
        }

        // ---- 5. Армия -------------------------------------------------------
        double armyW = w.get("pl.army", 1.0);
        int units = 0;
        double armySum = 0;
        Map<UnitType, Integer> byType = new HashMap<>();
        for (UnitToken u : me.unitsOnField()) {
            units++;
            byType.merge(u.type, 1, Integer::sum);
            armySum += switch (u.type) {
                case INFANTRY -> 0.8;
                case VEHICLE -> 1.15;
                case AIRCRAFT -> 1.15;
                case TOWER -> 0.65;
            };
            if (u.damage > 0) {
                armySum -= 0.3;
            }
        }
        // Разнообразие родов: разные цели бьются разными родами дёшево.
        armySum += 0.35 * Math.max(0, byType.size() - 1);
        b.add("army", armySum * armyW);
        b.add("military_powered", milPowered * 0.7 * armyW * (0.3 + early));
        b.add("military_cap", milCap * 0.25 * armyW);
        // Боеприпасы: ценны ровно настолько, насколько есть кому стрелять.
        int useful = Math.min(ammo, 2 * units + 2);
        b.add("ammo", (useful * 0.45 + (ammo - useful) * 0.08) * w.get("pl.ammo", 1.0));

        // ---- 6. Война -------------------------------------------------------
        double warW = w.get("pl.war", 1.0);
        double tBias = w.get("pl.target_bias", 0.5);
        double warSum = 0;
        WorldView wv = new WorldView(s, seat);
        for (Token t : wv.enemyTokens) {
            int dmg = t instanceof UnitToken u ? u.damage : ((BuildingToken) t).damage;
            if (dmg <= 0) {
                continue;
            }
            int hp = Passives.effectiveHp(s, t);
            boolean cu = t instanceof BuildingToken bb && bb.type == BuildingType.COMMAND_CENTER;
            double per = cu ? 1.4 : 0.55;
            double mult = in != null && in.targetSeat == t.owner() ? 1.0 + tBias : 1.0;
            warSum += per * dmg * mult * (hp > 1 ? 1.0 : 0.5);
        }
        // Осада: мои жетоны рядом с чужим ЦУ; чужие войска рядом с моим ЦУ.
        String myCu = null;
        for (BuildingToken bt : me.buildingsOnField()) {
            if (bt.type == BuildingType.COMMAND_CENTER) {
                myCu = bt.hexId;
            }
        }
        Set<String> nearEnemyCu = new HashSet<>();
        for (PlayerState p : s.players) {
            if (p.seat == seat) {
                continue;
            }
            for (BuildingToken bt : p.buildingsOnField()) {
                if (bt.type == BuildingType.COMMAND_CENTER) {
                    nearEnemyCu.add(bt.hexId);
                    nearEnemyCu.addAll(s.field.neighbors(bt.hexId));
                }
            }
        }
        int siege = 0;
        int strikers = 0;
        for (UnitToken u : me.unitsOnField()) {
            if (nearEnemyCu.contains(u.hexId)) {
                siege++;
            }
            // Есть ли рядом чужой жетон, которого этот жетон может убить.
            for (Token t : wv.enemyTokens) {
                if (t.hexId() != null && (t.hexId().equals(u.hexId)
                        || s.field.neighbors(u.hexId).contains(t.hexId()))
                        && CombatResolver.canHit(s, seat, u, t)) {
                    strikers++;
                    break;
                }
            }
        }
        warSum += siege * 0.45 + strikers * 0.35;
        warSum += me.cuDestructionTokens * 1.5;          // сверх 3 ПО: путь к победе
        // СНЕСЁННЫЕ МНОЙ ЖЕТОНЫ — не только трофей. Владелец до Возврата без
        // них: добытчик не копает, станция не питает, казарма не нанимает, а
        // вернувшееся здание надо ставить заново за монеты. Это ущерб
        // противнику, и он тем больнее, чем раньше в раунде и чем ценнее жетон.
        double denial = 0;
        for (Token t : me.destroyedTokens) {
            double mult = in != null && in.targetSeat == t.owner() ? 1.0 + tBias : 1.0;
            if (t instanceof BuildingToken bt) {
                int lvl = bt.level == null ? 1 : bt.level;
                denial += mult * switch (bt.type) {
                    case BARRACKS -> 1.0;
                    case FACTORY -> 1.3;
                    case AIRBASE -> 1.5;
                    case MINER -> 0.9 + 0.25 * lvl;
                    case POWER_PLANT -> 0.8 + 0.25 * lvl;
                    default -> 0.6;
                };
            } else if (t instanceof UnitToken ut) {
                denial += mult * (ut.type == UnitType.INFANTRY || ut.type == UnitType.TOWER ? 0.5 : 0.7);
            }
        }
        warSum += denial;
        b.add("war", warSum * warW);

        // Угроза моему ЦУ и открытые фланги.
        double caution = w.get("pl.caution", 1.0);
        int threat = 0;
        Set<String> danger = new HashSet<>();
        for (PlayerState p : s.players) {
            if (p.seat == seat) {
                continue;
            }
            for (UnitToken u : p.unitsOnField()) {
                danger.add(u.hexId);
                danger.addAll(s.field.neighbors(u.hexId));
                if (myCu != null && (myCu.equals(u.hexId)
                        || s.field.neighbors(myCu).contains(u.hexId))) {
                    threat++;
                }
            }
        }
        int exposed = 0;
        for (UnitToken u : me.unitsOnField()) {
            if (danger.contains(u.hexId)) {
                exposed++;
            }
        }
        for (BuildingToken bt : me.buildingsOnField()) {
            if (danger.contains(bt.hexId) && bt.type != BuildingType.COMMAND_CENTER) {
                exposed++;
            }
        }
        b.add("threat_cu", -threat * 0.6 * caution);
        b.add("exposed", -exposed * 0.12 * caution);

        // ---- 7. Наука -------------------------------------------------------
        // Очки шагов уже в vp; здесь — стратегическая высота: занятые места.
        int steps = 0;
        for (int v : me.techSteps.values()) {
            steps += v;
        }
        b.add("tech", steps * 0.25 * w.get("pl.tech", 1.0));
        return b;
    }

    /** Доля партии, которая уже прошла (0 — начало, 1 — конец). */
    public static double lateness(GameState s) {
        Rivalry riv = new Rivalry(s, 0);
        int left = Math.max(0, riv.roundsLeft());
        int total = Math.max(1, s.round + left);
        return clamp((double) (s.round - 1) / total);
    }

    static double clamp(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }

    private static final Map<String, Boolean> ПРИРОДА =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * ТРЕБОВАНИЕ ЭТОЙ КАРТЫ — ПРОИСШЕСТВИЕ (событие хода), а не состояние стола.
     *
     * <p>Читается из записи каталога ({@code type: incident}) — того же места,
     * откуда движок берёт всё остальное про карту.
     */
    public static boolean происшествие(GameState s, String cid) {
        return ПРИРОДА.computeIfAbsent(cid, k -> {
            try {
                return "incident".equals(String.valueOf(
                    kelium.dataio.Ctx.cards(s, "objectives").byId(k).get("type")));
            } catch (RuntimeException e) {
                return Boolean.FALSE;
            }
        });
    }

    /** Мнение карты арсенала о своей полезности (0..1); 0.5, если карта молчит. */
    public static double usefulness(GameState s, int seat, String cid, boolean install) {
        try {
            var card = CardRegistry.arsenal(cid);
            if (card == null) {
                return 0.5;
            }
            double u = card.usefulness(new EngineCardContext(s, seat), install);
            return Double.isNaN(u) ? 0.5 : clamp(u);
        } catch (RuntimeException e) {
            return 0.5;
        }
    }

    private static final Map<String, Double> REWARD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Забыть посчитанные цены наград.
     *
     * <p>Нужно стенду щедрости: он гоняет один и тот же каталог с РАЗНЫМИ
     * множителями наград в одной машине, и без сброса второй множитель считался
     * бы по ценам первого — замер соврал бы молча.
     */
    public static void forgetRewards() {
        REWARD_CACHE.clear();
    }

    /** Цена награды задания в «ресурсных единицах» (монета = 1, келемий = 5). */
    public static double rewardValue(GameState s, int seat, String cid) {
        Double cached = REWARD_CACHE.get(cid);
        if (cached != null) {
            return cached;
        }
        double v = 4.0;
        try {
            List<ObjectiveHints.Hint> hints = ObjectiveHints.forHand(s, seat, s.journal,
                new java.util.LinkedHashSet<>(kelium.engine.Actions.ALL_NAMES), 0);
            for (ObjectiveHints.Hint h : hints) {
                if (h.cardId().equals(cid)) {
                    v = Math.max(1.0, h.value());
                }
            }
        } catch (RuntimeException e) {
            v = 4.0;
        }
        REWARD_CACHE.put(cid, v);
        return v;
    }
}
